## Context

API responses hiện tại dùng hardcoded English messages. `ApiResponse.errorLang()` + `ErrorCodeBase.msgCode` convention đã có sẵn trong base-core nhưng chưa wire `MessageSource`. Frontend hardcode 12 error strings trong `JwtSignInForm.tsx`. Cần chuẩn hóa i18n end-to-end: server render → client display.

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (CommandHandler)
- base-core: Shared library dùng bởi tất cả services — changes phải backward compatible
- Frontend: React 19, Ky HTTP client, i18next (minimal setup, chưa có `vi`)
- Existing: `ApiResponse.errorLang()` factory, `ErrorCodeBase.msgCode` convention, `setGlobalHeaders()` in api.ts

## Goals / Non-Goals

**Goals:**
- Server render message i18n hoàn chỉnh — client chỉ show
- Dual message source: file (static, common) + database (dynamic, runtime-manageable)
- Backward compatible — existing services không break
- Zero external dependency cho locale detection
- Chuẩn quốc tế BCP 47

**Non-Goals:**
- Admin UI quản lý i18n messages (Phase 2)
- Locale `km` (Khmer) hoặc thêm ngôn ngữ (mở rộng sau)
- IP geolocation cho locale detection (rejected)
- Frontend SSR i18n (admin dashboard là SPA)
- Translation management platform (Crowdin, Lokalise)

## Decisions

### D1: Dual MessageSource — File + Database

**Choice**: `CompositeMessageSource` chain: DatabaseMessageSource → FileMessageSource
**Why**: User yêu cầu "mã đa ngôn ngữ common có thể load từ file, các mã động nên load từ 1 bảng message ở database". File cho static/common codes (deploy-time), DB cho dynamic codes (runtime, admin-manageable).
**Alternative**: File-only (simple, no DB) — nhưng không flexible, phải redeploy để thay đổi message
**Trade-off**: DB adds complexity (entity, repo, cache) nhưng cho phép thay đổi message mà không cần redeploy

**Implementation**:
```
CompositeMessageSource (ordered chain):
  1. DatabaseMessageSource (priority — check DB first)
     └── CaffeineCache (5 min TTL)
     └── I18nMessageRepository → i18n_messages table
  2. ReloadableResourceBundleMessageSource (fallback)
     └── classpath:messages/messages
     └── classpath:messages/auth-messages
```

### D2: BaseControllerAdvice — Nullable MessageSource Injection

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
    // Utility method for all handlers
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

### D3: Frontend Locale Detection — Hybrid Cascade

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

### D4: Message Key Convention

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

# auth-service (auth.*)
auth.invalid_credentials=Invalid username or password
auth.rate_limited=Too many login attempts
auth.session_limit=Maximum active sessions ({0}) reached
# ... (21 total)
```

### D5: Content-Language Response Header

**Choice**: Set via `HttpServletResponse` in each handler
**Why**: Simple, explicit, no filter overhead
**Alternative**: Servlet filter — adds filter to chain for all requests
**Trade-off**: Repetitive code in handlers → extract to `resolveMessage()` utility method

### D6: Frontend Languages List Update

**Choice**: Replace `['en', 'tr', 'ar']` → `['en', 'vi']`
**Why**: Current languages list has Turkish/Arabic (boilerplate defaults) — project needs English + Vietnamese
**No alternative**: Must match backend supported locales

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
| `AuthControllerAdvice` | `shared/exception/GlobalExceptionHandler.kt` | [MODIFY] | FR-001, FR-004 |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | [MODIFY] | FR-005 |
| `DatabaseMessageSource` | `shared/i18n/DatabaseMessageSource.kt` | [NEW] | FR-003B |
| `I18nMessageEntity` | `shared/i18n/I18nMessageEntity.kt` | [NEW] | FR-003B |
| `I18nMessageRepository` | `shared/i18n/I18nMessageRepository.kt` | [NEW] | FR-003B |
| `CompositeI18nConfig` | `shared/config/I18nConfig.kt` | [NEW] | FR-003, FR-003B |
| `auth-messages.properties` | `resources/messages/auth-messages.properties` | [NEW] | FR-003 |
| `auth-messages_vi.properties` | `resources/messages/auth-messages_vi.properties` | [NEW] | FR-003 |
| `V5__create_i18n_messages.sql` | `resources/db/migration/V5__create_i18n_messages.sql` | [NEW] | FR-003B |

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
   │                              → resolve("vi")                       │
   │                              → LocaleContextHolder.set(vi)         │
   │                                    │                                │
   │                              [SUCCESS CASE]                        │
   │                              messageSource.getMessage(             │
   │                                "api.success", null, vi)            │
   │◀─── 200 ApiResponse ──────────────│                                │
   │  Content-Language: vi              │                                │
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
   │  Content-Language: vi              │                                │
   │  { detail: "Quá nhiều lần         │                                │
   │    đăng nhập từ IP",              │                                │
   │    errorCode: "AUTH_020" }         │                                │
   │                                    │                                │
   │ show(problem.detail)              │                                │
   │ "Quá nhiều lần đăng nhập từ IP"   │                                │
```
