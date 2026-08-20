## Context

[CHANGED] Updated 2026-08-22 — reflects current codebase state where ~97% i18n infrastructure is implemented.

API response i18n infrastructure đã implement gần hoàn chỉnh: `I18nConfig` (AcceptHeaderLocaleResolver + CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached), `AuthControllerAdvice` (ProblemDetail i18n), `ContentLanguageFilter` (100% Content-Language coverage), `ClientMetadataFilter` (MDC logging), message bundles (55 keys × 2 locales), controller i18n (10 action endpoints across 3 controllers).

**Only remaining gap**: `LoginRateLimitFilter.writeRateLimitResponse()` bypasses MessageSource chain — writes ProblemDetail with hardcoded English `ex.message`. Frontend chưa inject headers và chưa cleanup hardcoded error messages.

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (CommandHandler), Spring Boot + Kotlin
- auth-service: All i18n infrastructure EXISTING (`I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice`, filters, bundles)
- Frontend: React 19, Ky HTTP client, i18next (đã setup en + vi)
- Existing: `setGlobalHeaders()` in api.ts, `I18nProvider` with language list, `ContentLanguageFilter`, `ClientMetadataFilter`

### Brainstorm Selected Direction
> Targeted Backend Fix + Frontend Header Injection — LoginRateLimitFilter i18n + Frontend Accept-Language/Metadata Headers + Hardcoded Message Cleanup

## Goals / Non-Goals

**Goals:**
- Fix LoginRateLimitFilter i18n gap — rate limit errors render in user's language
- Frontend injects Accept-Language + metadata headers on every API request
- Frontend displays server-rendered messages directly (no client-side message building)
- 100% Content-Language header coverage including filter bypass paths (NFR-004)

**Non-Goals:**
- Modifying any other backend controllers or handlers (already done)
- Adding new message bundle keys (already complete — 55 keys × 2 locales)
- Creating new filters or infrastructure classes (already done)
- Admin UI for i18n message management (Phase 2)
- Adding locale `km` (Khmer) or other languages (future)
- IP geolocation for locale detection (rejected)

## Decisions

### D1: Dual MessageSource — File + Database [EXISTING — NO CHANGE]

**Choice**: `CompositeMessageSource` chain: DatabaseMessageSource → FileMessageSource
**Status**: ✅ IMPLEMENTED in `I18nConfig.kt:49-63`

```
CompositeMessageSource (ordered chain):
  1. DatabaseMessageSource (priority — check DB first)
     └── CaffeineCache (5 min TTL, maxSize=500)
     └── I18nMessageRepository → i18n_messages table
  2. ReloadableResourceBundleMessageSource (fallback)
     └── classpath:messages/auth-messages
     └── classpath:messages/messages
```

### D2: BaseControllerAdvice — Nullable MessageSource Injection [EXISTING — NO CHANGE]

**Choice**: Constructor parameter `messageSource: MessageSource? = null`
**Status**: ✅ IMPLEMENTED — backward compatible, auth-service's `AuthControllerAdvice` injects MessageSource

### D3: Frontend Locale Detection — Hybrid Cascade [EXISTING — NO CHANGE]

**Choice**: `localStorage("user_language")` → `navigator.languages` → `"en"` fallback
**Status**: ✅ IMPLEMENTED in `I18nProvider.tsx` and `i18n.ts`

```typescript
function detectLanguage(supportedLocales: string[], defaultLocale: string): string {
  const stored = localStorage.getItem('user_language');
  if (stored && supportedLocales.includes(stored)) return stored;
  
  for (const lang of navigator.languages) {
    const short = lang.split('-')[0];
    if (supportedLocales.includes(short)) return short;
  }
  
  return defaultLocale;
}
```

### D4: Message Key Convention [EXISTING — NO CHANGE]

**Choice**: `AuthErrorCode.msgCode` = message bundle key (1:1 mapping)
**Status**: ✅ IMPLEMENTED — 44 error codes + 11 success keys in both locales

### D5: Content-Language Response Header — Servlet Filter [EXISTING — NO CHANGE]

**Choice**: `ContentLanguageFilter` (OncePerRequestFilter) sets `Content-Language` on ALL `/api/**` responses
**Status**: ✅ IMPLEMENTED in `ContentLanguageFilter.kt` — @Order(LOWEST_PRECEDENCE - 10), `/api/**`

### D6: Frontend Languages List [EXISTING — NO CHANGE]

**Choice**: `['en', 'vi']` (replaced `['en', 'tr', 'ar']` boilerplate defaults)
**Status**: ✅ IMPLEMENTED in `I18nProvider.tsx`

### D7: ClientMetadataFilter — MDC Logging [EXISTING — NO CHANGE]

**Choice**: Separate `ClientMetadataFilter` extracts `X-App-Version`, `X-Client-Platform` → MDC
**Status**: ✅ IMPLEMENTED in `ClientMetadataFilter.kt` — @Order(HIGHEST_PRECEDENCE + 10)

### D8: AcceptHeaderLocaleResolver with Locale Whitelist [EXISTING — NO CHANGE]

**Choice**: `AcceptHeaderLocaleResolver` bean with supported locales `[en, vi]`, default `en`
**Status**: ✅ IMPLEMENTED in `I18nConfig.kt:39-44`

### D9: Data-only Endpoints Skip i18n Messages [EXISTING — NO CHANGE]

**Choice**: Query/data endpoints (login, refresh, introspect, get sessions) do not include i18n `message` field
**Status**: ✅ CONFIRMED — login/refresh return `AuthResponse` with tokens (data), no forced message

### D10: Defense-in-depth Content-Language [EXISTING — NO CHANGE]

**Choice**: Both `ContentLanguageFilter` and `AuthControllerAdvice.setContentLanguageHeader()` set Content-Language
**Status**: ✅ IMPLEMENTED — redundant but harmless (same value from LocaleContextHolder)

### D11: AcceptHeaderLocaleResolver with supported locale whitelist [EXISTING — NO CHANGE]

**Status**: ✅ Merged into D8

### D12: All error codes MUST have message bundle entries [EXISTING — NO CHANGE]

**Status**: ✅ IMPLEMENTED — 44 error codes (AUTH_001-044) all have entries in both en + vi bundles

### D13: LoginRateLimitFilter parses Accept-Language directly [NEW — THE FIX]

**Choice**: Parse `Accept-Language` header inline in filter using `Locale.LanguageRange.parse()` + `Locale.lookup()` with supported locales `[en, vi]`
**Why**: Filter runs in Spring Security filter chain BEFORE DispatcherServlet. `AcceptHeaderLocaleResolver` is invoked BY DispatcherServlet. Therefore `LocaleContextHolder.getLocale()` returns JVM default (not request locale) in filter context. Must parse header directly.
**Alternative 1**: Custom `LocaleContextFilter` before `LoginRateLimitFilter` — adds complexity, another filter in chain
**Alternative 2**: Accept that rate limit responses always use default locale — violates i18n requirement
**Trade-off**: Duplicates locale whitelist `[en, vi]` between `I18nConfig` and `LoginRateLimitFilter` — acceptable for 2 values

**Implementation**:
```kotlin
private fun resolveLocale(request: HttpServletRequest): Locale {
    val acceptLanguage = request.getHeader("Accept-Language") ?: return Locale.ENGLISH
    return try {
        val ranges = Locale.LanguageRange.parse(acceptLanguage)
        val supported = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))
        Locale.lookup(ranges, supported) ?: Locale.ENGLISH
    } catch (e: Exception) {
        Locale.ENGLISH
    }
}
```

### D14: Update `auth.rate_limited` template with interpolation args [NEW — RECOMMENDED]

**Choice**: Update template to include `{0}` (retryAfterSeconds) and `{1}` (dimension) placeholders
**Why**: Currently template "Too many login attempts" ignores args `[retryAfterSeconds, dimension]` extracted by `extractMessageArgs()`. `MessageFormat` silently drops extra args — not a bug but suboptimal UX. Updated template gives user actionable information (how long to wait, what dimension triggered it).
**Alternative**: Keep current template — rate limit message is generic but correct
**Trade-off**: Longer message, exposes internal dimension name ("IP", "USERNAME") to user — acceptable for admin dashboard

**Updated templates**:
```properties
# EN
auth.rate_limited=Too many login attempts ({1}). Please wait {0} seconds.
# VI
auth.rate_limited=Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây.
```

### D15: Login/refresh do NOT need i18n message in AuthResponse [EXISTING — NO CHANGE]

**Choice**: Data endpoints — tokens are the payload. Message keys exist in bundles if ever needed.
**Status**: ✅ CONFIRMED by brainstorm analysis (Q9)

## Component Mapping

### auth-service — Changes Needed

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `LoginRateLimitFilter` | `auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | [MODIFY] | FR-004B |
| `auth-messages.properties` | `resources/messages/auth-messages.properties` | [MODIFY] | D14 |
| `auth-messages_vi.properties` | `resources/messages/auth-messages_vi.properties` | [MODIFY] | D14 |

### auth-service — Already Implemented (VERIFY ONLY)

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `AuthControllerAdvice` | `shared/exception/GlobalExceptionHandler.kt` | [EXISTING] | FR-001, FR-004 |
| `I18nConfig` | `shared/config/I18nConfig.kt` | [EXISTING] | FR-002, FR-003, FR-012 |
| `DatabaseMessageSource` | `shared/i18n/DatabaseMessageSource.kt` | [EXISTING] | FR-003, FR-010 |
| `I18nMessageEntity` | `shared/i18n/I18nMessageEntity.kt` | [EXISTING] | FR-003 |
| `I18nMessageRepository` | `shared/i18n/I18nMessageRepository.kt` | [EXISTING] | FR-003 |
| `ContentLanguageFilter` | `auth/adapter/in/web/filter/ContentLanguageFilter.kt` | [EXISTING] | FR-006 |
| `ClientMetadataFilter` | `auth/adapter/in/web/filter/ClientMetadataFilter.kt` | [EXISTING] | FR-011 |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | [EXISTING] | FR-005 |
| `SessionController` | `auth/adapter/in/web/SessionController.kt` | [EXISTING] | FR-005 |
| `AccountLifecycleController` | `auth/adapter/in/web/AccountLifecycleController.kt` | [EXISTING] | FR-005 |

### admindashboard — Changes Needed

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `api.ts` | `src/utils/api.ts` | [MODIFY] | FR-007, FR-008 |
| `I18nProvider.tsx` | `src/@i18n/I18nProvider.tsx` | [MODIFY] | FR-007, D3 |
| `i18n.ts` | `src/@i18n/i18n.ts` | [MODIFY] | FR-007, D3 |
| `JwtSignInForm.tsx` | `src/@auth/services/jwt/components/JwtSignInForm.tsx` | [MODIFY] | FR-009 |
| `LanguageSwitcher.tsx` | `src/components/theme-layouts/components/LanguageSwitcher.tsx` | [MODIFY] | D6 |
| `VN.svg` | `public/assets/images/flags/VN.svg` | [NEW] | D6 |

## Sequence Diagram

### LoginRateLimitFilter i18n Flow (THE FIX)

```
Frontend                          LoginRateLimitFilter              MessageSource
   │                                    │                                │
   │──POST /api/auth/login──────────────▶                                │
   │  Accept-Language: vi               │                                │
   │                                    │                                │
   │                              shouldNotFilter() → false              │
   │                              (POST + /api/auth/login)              │
   │                                    │                                │
   │                              loginRateLimitService                  │
   │                              .checkMultiDimensional()               │
   │                              → throws RateLimitExceededException    │
   │                              (retryAfterSeconds=60, dimension="IP") │
   │                                    │                                │
   │                              writeRateLimitResponse(response, ex)  │
   │                              ├── resolveLocale(request)             │
   │                              │   ├── getHeader("Accept-Language")   │
   │                              │   │   → "vi"                        │
   │                              │   ├── LanguageRange.parse("vi")     │
   │                              │   └── Locale.lookup([vi], [en,vi])  │
   │                              │       → Locale("vi")                │
   │                              │                                      │
   │                              ├── messageSource.getMessage(──────────▶│
   │                              │     "auth.rate_limited",             │
   │                              │     [60, "IP"],                      │
   │                              │     ex.message,  // fallback         │
   │                              │     Locale("vi"))                    │
   │                              │◀─────────────────────────────────────│
   │                              │   "Quá nhiều lần đăng nhập (IP).    │
   │                              │    Vui lòng đợi 60 giây."           │
   │                              │                                      │
   │                              ├── ProblemDetail.forStatusAndDetail(  │
   │                              │     429, resolvedMessage)            │
   │                              ├── response.setHeader(                │
   │                              │     "Content-Language", "vi")        │
   │                              └── response.writer.write(json)        │
   │                                    │                                │
   │◀─── 429 ProblemDetail ─────────────│                                │
   │  Content-Language: vi              │                                │
   │  { detail: "Quá nhiều lần         │                                │
   │    đăng nhập (IP). Vui lòng       │                                │
   │    đợi 60 giây.",                  │                                │
   │    errorCode: "AUTH_020",          │                                │
   │    retryAfterSeconds: 60,          │                                │
   │    dimension: "IP" }               │                                │
   │                                    │                                │
   │ show(problem.detail)              │                                │
   │ "Quá nhiều lần đăng nhập (IP)..." │                                │
```

### Full Request Flow (Existing — for reference)

```
Frontend                          Server (Spring)                    Database
   │                                    │                                │
   │──API Request────────────────────────▶                                │
   │  Accept-Language: vi               │                                │
   │  X-App-Version: 1.0.0             │                                │
   │  X-Client-Platform: web           │                                │
   │                                    │                                │
   │                              ClientMetadataFilter (HIGHEST+10)     │
   │                              → MDC.put(appVersion, clientPlatform) │
   │                                    │                                │
   │                              AcceptHeaderLocaleResolver             │
   │                              → resolve("vi") → LocaleContextHolder │
   │                                    │                                │
   │                              Controller dispatch                   │
   │                              ├── [SUCCESS] messageSource.getMessage │
   │                              └── [ERROR] → AuthControllerAdvice    │
   │                                    │     → resolveMessage(i18n)    │
   │                                    │                                │
   │                              ContentLanguageFilter (LOWEST-10)     │
   │                              → setHeader("Content-Language", "vi") │
   │                                    │                                │
   │◀─── Response ──────────────────────│                                │
   │  Content-Language: vi              │                                │
```
