## Context

API responses hiện tại dùng hardcoded English messages. `ApiResponse.errorLang()` + `ErrorCodeBase.msgCode` convention đã có sẵn trong base-core nhưng chưa wire `MessageSource`. Frontend hardcode 12 error strings trong `JwtSignInForm.tsx`. Cần chuẩn hóa i18n end-to-end: server render → client display.

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (CommandHandler)
- base-core: Shared library dùng bởi tất cả services — changes phải backward compatible
- Frontend: React 19, Ky HTTP client, i18next (minimal setup, chưa có `vi`)
- Existing: `ApiResponse.errorLang()` factory, `ErrorCodeBase.msgCode` convention, `setGlobalHeaders()` in api.ts
- Existing i18n infrastructure (~90%): `I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice` (MessageSource-integrated), message bundles (en + vi, 44 keys)

### Brainstorm Selected Direction
> Servlet Filter Content-Language + Controller-level Success i18n + Hybrid Cascade Locale Detection

## Goals / Non-Goals

**Goals:**
- Server render message i18n hoàn chỉnh — client chỉ show
- Dual message source: file (static, common) + database (dynamic, runtime-manageable)
- Backward compatible — existing services không break
- Zero external dependency cho locale detection
- Chuẩn quốc tế BCP 47
- 100% `Content-Language` header coverage (NFR-004)

**Non-Goals:**
- Admin UI quản lý i18n messages (Phase 2)
- Locale `km` (Khmer) hoặc thêm ngôn ngữ (mở rộng sau)
- IP geolocation cho locale detection (rejected)
- Frontend SSR i18n (admin dashboard là SPA)
- Translation management platform (Crowdin, Lokalise)

## Decisions

### D1: Dual MessageSource — File + Database [UNCHANGED]

**Choice**: `CompositeMessageSource` chain: DatabaseMessageSource → FileMessageSource
**Why**: User yêu cầu "mã đa ngôn ngữ common có thể load từ file, các mã động nên load từ 1 bảng message ở database". File cho static/common codes (deploy-time), DB cho dynamic codes (runtime, admin-manageable).
**Alternative**: File-only (simple, no DB) — nhưng không flexible, phải redeploy để thay đổi message
**Trade-off**: DB adds complexity (entity, repo, cache) nhưng cho phép thay đổi message mà không cần redeploy

**Implementation** (EXISTING — `I18nConfig.kt`):
```
CompositeMessageSource (ordered chain):
  1. DatabaseMessageSource (priority — check DB first)
     └── CaffeineCache (5 min TTL, maxSize=500)
     └── I18nMessageRepository → i18n_messages table
  2. ReloadableResourceBundleMessageSource (fallback)
     └── classpath:messages/auth-messages
     └── classpath:messages/messages
```

### D2: BaseControllerAdvice — Nullable MessageSource Injection [UNCHANGED]

**Choice**: Constructor parameter `messageSource: MessageSource? = null`
**Why**: Backward compatible — existing services (không dùng i18n) truyền null hoặc không truyền → fallback `ErrorCodeBase.description`. Services opt-in bằng cách inject `MessageSource`.
**Alternative**: Required injection — breaks all existing services
**Trade-off**: Nullable check mỗi handler call — acceptable cost cho backward compatibility

**Implementation**:
```kotlin
abstract class BaseControllerAdvice(
    private val validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource? = null  // NEW — opt-in
) {
    protected fun resolveMessage(
        msgCode: String,
        args: Array<Any>? = null,
        fallback: String
    ): String {
        val ms = messageSource ?: return fallback
        val locale = LocaleContextHolder.getLocale()
        return try {
            ms.getMessage(msgCode, args, locale)
        } catch (e: NoSuchMessageException) {
            fallback
        }
    }
}
```

### D3: Frontend Locale Detection — Hybrid Cascade [UNCHANGED]

**Choice**: `localStorage` → `navigator.languages` → `"en"` fallback
**Why**: User yêu cầu "detection location" — `navigator.languages` reflect OS locale setting (proxy for location). Zero external dependency, privacy-friendly.
**Alternative**: IP geolocation API — external cost, privacy concern, inaccurate (VPN)
**Trade-off**: Browser setting might not match user intent (shared computer) — but user switches once → persisted forever

**Implementation**:
```typescript
function detectLanguage(supportedLocales: string[], defaultLocale: string): string {
  // 1. Check persisted preference
  const stored = localStorage.getItem('user_language');
  if (stored && supportedLocales.includes(stored)) return stored;
  
  // 2. Check browser/OS locale (navigator.languages)
  for (const lang of navigator.languages) {
    const short = lang.split('-')[0]; // "vi-VN" → "vi"
    if (supportedLocales.includes(short)) return short;
  }
  
  // 3. Fallback
  return defaultLocale;
}
```

### D4: Message Key Convention [UNCHANGED]

**Choice**: `ErrorCodeBase.msgCode` = message bundle key (1:1 mapping)
**Why**: Convention đã có sẵn — `AuthErrorCode.RATE_LIMITED.msgCode = "auth.rate_limited"` → message key = `auth.rate_limited`. Zero mapping effort.
**Trade-off**: Key format tied to error code naming — acceptable, already consistent

**Key groups**:
```properties
# base-core (api.*)
api.success=Operation completed successfully
api.bad_request=Bad request
api.not_found=Resource not found
api.forbidden=Access forbidden
api.validation_error=Validation error: {0}
api.system_error=An unexpected error occurred. Reference: {0}

# auth-service (auth.*) — existing 21 + 15 new E2EE/Anonymous + 7 new success
auth.invalid_credentials=Invalid username or password
auth.rate_limited=Too many login attempts
auth.session_limit=Maximum active sessions ({0}) reached
auth.e2ee_time_skew=Request timestamp out of tolerance window   # NEW
auth.anonymous_session_expired=Anonymous session expired         # NEW
auth.register_success=Registration successful                   # NEW
# ... (total 44 existing + ~17 new keys)
```

### D5: Content-Language Response Header — Servlet Filter [CHANGED]

**Choice**: `ContentLanguageFilter` (OncePerRequestFilter) sets `Content-Language` on ALL `/api/**` responses
**Why**: NFR-004 requires 100% Content-Language coverage on ALL responses. With 13+ controllers and growing, per-controller approach is fragile — new endpoints would miss the header. A single filter guarantees coverage with zero maintenance burden.
**Alternative**: Per-controller via HttpServletResponse parameter — explicit but fragile at scale (13+ controllers, 40+ endpoints)
**Alternative**: ResponseBodyAdvice — framework-level but doesn't cover raw servlet responses (LoginRateLimitFilter writes ProblemDetail directly)
**Trade-off**: Filter runs on all /api/** paths (minor overhead). AuthControllerAdvice already sets Content-Language for errors — filter will set same value (defense-in-depth, last-write wins but identical value).

**Implementation**:
```kotlin
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class ContentLanguageFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        filterChain.doFilter(request, response)
        // Set AFTER controller processing — LocaleContextHolder available
        response.setHeader(
            "Content-Language",
            LocaleContextHolder.getLocale().toLanguageTag()
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }
}
```

> **Brainstorm evidence**: Selected "Servlet Filter for Content-Language" approach over per-controller (Approach 1 vs Approach 2). See `brainstorm_notes.md` → Approaches Considered.

### D6: Frontend Languages List Update [UNCHANGED]

**Choice**: Replace `['en', 'tr', 'ar']` → `['en', 'vi']`
**Why**: Current languages list has Turkish/Arabic (boilerplate defaults) — project needs English + Vietnamese
**No alternative**: Must match backend supported locales

### D7: ClientMetadataFilter — MDC Logging [NEW]

**Choice**: Separate `ClientMetadataFilter` (OncePerRequestFilter) extracts `X-App-Version`, `X-Client-Platform` from request headers → MDC
**Why**: FR-011 requires metadata in MDC for audit logging. Separate from ContentLanguageFilter — different concerns (request-phase vs response-phase).
**Alternative**: Combine with ContentLanguageFilter — violates single responsibility
**Trade-off**: Extra filter in chain — minimal overhead (string operations only)

**Implementation**:
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ClientMetadataFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            MDC.put("appVersion", request.getHeader("X-App-Version") ?: "unknown")
            MDC.put("clientPlatform", request.getHeader("X-Client-Platform") ?: "unknown")
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("appVersion")
            MDC.remove("clientPlatform")
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }
}
```

### D8: AcceptHeaderLocaleResolver with Locale Whitelist [NEW]

**Choice**: Configure `AcceptHeaderLocaleResolver` bean in `I18nConfig` with supported locales `[en, vi]` and default `en`
**Why**: FR-012 requires locale whitelist. Without explicit config, Spring accepts any locale from `Accept-Language` header.

> ⚠️ OPEN QUESTION: Should `AcceptHeaderLocaleResolver` be configured in `I18nConfig` or in a separate `WebMvcConfigurer`?
> → Decision: Configure in `I18nConfig` — keeps all i18n-related beans in one place.

**Implementation**:
```kotlin
@Bean
fun localeResolver(): AcceptHeaderLocaleResolver {
    val resolver = AcceptHeaderLocaleResolver()
    resolver.defaultLocale = Locale.ENGLISH
    resolver.supportedLocales = listOf(Locale.ENGLISH, Locale("vi"))
    return resolver
}
```

### D9: Data-only Endpoints Skip i18n Messages [UNCHANGED]

**Choice**: Query endpoints returning structured data do not include i18n `message` field
**Why**: Data endpoints (get sessions, introspect token, get policies) return factual data — no user-facing action confirmation needed.

> ⚠️ OPEN QUESTION: Should login/register success responses include i18n `message` field in AuthResponse DTO, or keep as data-only?
> → Recommendation: Login/register return `AuthResponse` with tokens — success is implicit. Add optional `message` field only for action endpoints (logout, change-password, etc.).

## Component Mapping

### base-core

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `I18nAutoConfiguration` | `configuration/I18nAutoConfiguration.kt` | [NEW] | FR-002, FR-003, FR-012 |
| `BaseControllerAdvice` | `domain/web/BaseControllerAdvice.kt` | [MODIFY] | FR-001, FR-006, FR-010 |
| `ApiResponse` | `domain/web/payload/ApiResponse.kt` | [MODIFY] | FR-005 |
| `messages.properties` | `resources/messages/messages.properties` | [NEW] | FR-003 |
| `messages_vi.properties` | `resources/messages/messages_vi.properties` | [NEW] | FR-003 |

### auth-service

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `AuthControllerAdvice` | `shared/exception/GlobalExceptionHandler.kt` | [VERIFY] | FR-001, FR-004 |
| `I18nConfig` | `shared/config/I18nConfig.kt` | [MODIFY] | FR-003, FR-012 |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | [MODIFY] | FR-005 |
| `SessionController` | `auth/adapter/in/web/SessionController.kt` | [MODIFY] | FR-005 |
| `AccountLifecycleController` | `auth/adapter/in/web/AccountLifecycleController.kt` | [MODIFY] | FR-005 |
| `ContentLanguageFilter` | `auth/adapter/in/web/filter/ContentLanguageFilter.kt` | [NEW] | FR-006, NFR-004 |
| `ClientMetadataFilter` | `auth/adapter/in/web/filter/ClientMetadataFilter.kt` | [NEW] | FR-011 |
| `DatabaseMessageSource` | `shared/i18n/DatabaseMessageSource.kt` | [EXISTING] | FR-003B |
| `I18nMessageEntity` | `shared/i18n/I18nMessageEntity.kt` | [EXISTING] | FR-003B |
| `I18nMessageRepository` | `shared/i18n/I18nMessageRepository.kt` | [EXISTING] | FR-003B |
| `auth-messages.properties` | `resources/messages/auth-messages.properties` | [MODIFY] | FR-003 |
| `auth-messages_vi.properties` | `resources/messages/auth-messages_vi.properties` | [MODIFY] | FR-003 |

### admindashboard

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `api.ts` | `src/utils/api.ts` | [MODIFY] | FR-007, FR-008, FR-011 |
| `I18nProvider.tsx` | `src/@i18n/I18nProvider.tsx` | [MODIFY] | FR-007, D3, D6 |
| `i18n.ts` | `src/@i18n/i18n.ts` | [MODIFY] | FR-007, D3 |
| `JwtSignInForm.tsx` | `src/@auth/services/jwt/components/JwtSignInForm.tsx` | [MODIFY] | FR-009 |

## Sequence Diagram

```
Frontend                          Server (Spring)                    Database
   │                                    │                                │
   │ ┌──────────────────────┐           │                                │
   │ │ detectLanguage()     │           │                                │
   │ │ localStorage → nav   │           │                                │
   │ │ → "vi"               │           │                                │
   │ └──────────────────────┘           │                                │
   │                                    │                                │
   │──POST /api/auth/login──────────────▶                                │
   │  Accept-Language: vi               │                                │
   │  X-App-Version: 1.0.0             │                                │
   │  X-Client-Platform: web           │                                │
   │                                    │                                │
   │                              AcceptHeaderLocaleResolver             │
   │                              → resolve("vi") — whitelist OK        │
   │                              → LocaleContextHolder.set(vi)         │
   │                                    │                                │
   │                              ClientMetadataFilter                  │
   │                              → MDC.put("appVersion", "1.0.0")     │
   │                              → MDC.put("clientPlatform", "web")   │
   │                                    │                                │
   │                              ContentLanguageFilter (wraps)         │
   │                                    │                                │
   │                              [SUCCESS CASE]                        │
   │                              messageSource.getMessage(             │
   │                                "api.success", null, vi)            │
   │◀─── 200 ApiResponse ──────────────│                                │
   │  Content-Language: vi              │ (set by filter AFTER ctrl)    │
   │  { message: "Đăng nhập thành      │                                │
   │    công", data: {...} }            │                                │
   │                                    │                                │
   │                              [ERROR CASE]                          │
   │                              AuthException thrown                  │
   │                              → AuthControllerAdvice                │
   │                              → messageSource.getMessage(           │
   │                                "auth.rate_limited", [5,"IP"], vi)  │
   │                              → DB check (CompositeMessageSource)───▶│
   │                              ← cache hit or DB lookup ─────────────│
   │◀─── 429 ProblemDetail ─────────────│                                │
   │  Content-Language: vi              │ (set by advice + filter)      │
   │  { detail: "Quá nhiều lần         │                                │
   │    đăng nhập từ IP",              │                                │
   │    errorCode: "AUTH_020" }         │                                │
   │                                    │                                │
   │ show(problem.detail)              │                                │
   │ "Quá nhiều lần đăng nhập từ IP"   │                                │
```
