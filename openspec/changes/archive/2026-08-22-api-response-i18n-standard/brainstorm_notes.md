---
type: brainstorm_notes
change: api-response-i18n-standard
date: 2026-08-22
selected_direction: "Targeted Backend Fix + Frontend Header Injection — LoginRateLimitFilter i18n + Frontend Accept-Language/Metadata Headers + Hardcoded Message Cleanup"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: API Response I18n Standard

## Date
2026-08-22

## Context
Chuẩn hóa format API response (success + error) với server-side i18n message rendering, unified response contract, client metadata headers. Feature này đã được brainstorm lần đầu (2026-08-11) nhưng cần re-evaluate vì **codebase đã tiến hóa đáng kể** — hầu hết infrastructure đã implement. Re-brainstorm dựa trên pre_openspec.md (2026-08-22) với codebase scan mới nhất.

**Research handoff** (openspec/research/api-response-i18n-standard/) confirms:
- Build from scratch (Spring native) — score 8.60/10
- 90%+ infrastructure already implemented → **NOW ~97% implemented after codebase evolution**
- Remaining work ≈ **1-2 developer-days** (down from original 3-3.5 estimate)

**Key evolution since first brainstorm (2026-08-11 → 2026-08-22):**
- [RESOLVED] ContentLanguageFilter — NOW EXISTS at `auth/adapter/in/web/filter/ContentLanguageFilter.kt` (was planned, now implemented)
- [RESOLVED] ClientMetadataFilter — NOW EXISTS at `auth/adapter/in/web/filter/ClientMetadataFilter.kt` (was planned, now implemented)
- [RESOLVED] Missing E2EE/Anonymous message keys — ALL 44 error codes + 11 success keys now in both en + vi bundles (70 lines each)
- [RESOLVED] SessionController hardcoded messages — NOW uses `messageSource.getMessage()` for `session_revoked`, `all_sessions_revoked`
- [RESOLVED] AccountLifecycleController hardcoded messages — NOW uses `messageSource.getMessage()` for `account_deactivated`, `deletion_requested`, `deletion_cancelled`
- [RESOLVED] CqrsAuthController partial i18n — NOW 7 endpoints use messageSource (register, logout, change-password, forgot-password, switch-domain, plus existing login_success/token_refreshed keys in bundles)
- [STILL OPEN] LoginRateLimitFilter.writeRateLimitResponse() — creates ProblemDetail directly, bypasses MessageSource
- [STILL OPEN] Frontend header injection — api.ts needs Accept-Language, X-App-Version, X-Client-Platform
- [STILL OPEN] Frontend i18n sync — I18nProvider.tsx language change → Accept-Language header
- [STILL OPEN] Frontend hardcoded error messages — forms showing client-built error strings

## Questions Asked & Answers

- Q1: Có cần thêm locale `km` (Khmer) ngay từ phase đầu?
  → A: Không. Hiện tại chỉ `en` (default) + `vi`. BCP 47 format. Mở rộng sau. (Carried from first brainstorm — still valid)

- Q2: Locale detection mechanism?
  → A: Hybrid cascade: (1) localStorage("user_language") → (2) navigator.languages → (3) default "en". KHÔNG dùng IP geolocation. (Carried from first brainstorm — still valid)

- Q3: Content-Language header coverage?
  → A: **RESOLVED by implementation.** `ContentLanguageFilter` (OncePerRequestFilter, @Order LOWEST_PRECEDENCE - 10) sets `Content-Language` on all `/api/**` responses. `AuthControllerAdvice.setContentLanguageHeader()` provides defense-in-depth for error responses. 100% coverage achieved.

- Q4: How to handle success message i18n for controllers that return data without user-facing messages?
  → A: **Two categories confirmed by implementation:**
    - (1) Action endpoints → have i18n messages (logout, change-password, session-revoke, etc.) — ✅ ALL DONE
    - (2) Data query endpoints → return structured data only (get sessions, export status, introspect) — ✅ CORRECT, no message needed

- Q5: Should all controllers inject MessageSource directly?
  → A: **Confirmed by implementation.** Direct injection in controllers that need success messages. CqrsAuthController, SessionController, AccountLifecycleController all inject `MessageSource` directly. Pattern proven.

- Q6: Missing message keys?
  → A: **RESOLVED.** All 44 error codes (AUTH_001-044) + 11 success messages now in both en + vi bundles. Total: 55 keys × 2 locales = 110 entries.

- Q7: ClientMetadataFilter vs ContentLanguageFilter?
  → A: **RESOLVED by implementation.** Separate filters, separate responsibilities. `ClientMetadataFilter` @Order(HIGHEST_PRECEDENCE + 10) = request phase (MDC). `ContentLanguageFilter` @Order(LOWEST_PRECEDENCE - 10) = response phase (Content-Language header).

- Q8 (NEW): Should LoginRateLimitFilter.writeRateLimitResponse() be refactored to use MessageSource?
  → A: **YES.** This is the only remaining i18n gap on the backend. Currently creates `ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message)` which uses the hardcoded English message from the exception. Analysis:
    - `RateLimitExceededException.message` = hardcoded English string
    - `AuthControllerAdvice.handleAuthException()` already handles `RateLimitExceededException` with proper i18n
    - But `LoginRateLimitFilter` catches the exception *before* it reaches the controller → AuthControllerAdvice never sees it
    - **Fix**: Inject `MessageSource` into `LoginRateLimitFilter`, resolve `auth.rate_limited` message with args `[retryAfterSeconds, dimension]`
    - **Alternative**: Let the exception propagate to AuthControllerAdvice — BUT this won't work because the filter runs pre-authentication, and AuthControllerAdvice operates in the DispatcherServlet context
    - **Verdict**: Inject MessageSource + LocaleContextHolder into the filter directly

- Q9 (NEW): Login and refresh endpoints return AuthResponse (data objects) without i18n `message` field — is this a gap?
  → A: **DECISION: NOT A GAP.** Analysis:
    - Login returns `AuthResponse` with `accessToken`, `refreshToken`, `userId`, `roles` — this is data, not a user-facing action confirmation
    - Refresh returns `AuthResponse` with new tokens — same rationale
    - Login already has `auth.login_success` and refresh has `auth.token_refreshed` keys in message bundles
    - If a `message` field is desired in AuthResponse DTO, it would add complexity for zero UX gain — client already shows toast from the HTTP status (200 = success)
    - The register endpoint sets `message` in AuthResponse because it's an action with a meaningful user-facing confirmation ("Registration successful")
    - Login is typically a silent action — showing a "Login successful" toast is a UX decision, not an i18n requirement
    - **If needed later**: Simply add `val locale = LocaleContextHolder.getLocale()` + `messageSource.getMessage("auth.login_success", null, "Login successful", locale)` and pass to AuthResponse.copy(message = ...)
    - **Verdict**: Skip for now. Pattern exists if needed. Not a compliance gap.

- Q10 (NEW): What is the exact remaining scope after codebase evolution?
  → A: Re-evaluated and narrowed:

    **Backend (0.5 developer-day):**
    - LoginRateLimitFilter i18n fix — inject MessageSource, resolve rate limit message with locale

    **Frontend (1 developer-day):**
    - api.ts — inject `Accept-Language`, `X-App-Version`, `X-Client-Platform` global headers
    - I18nProvider/i18n.ts — locale detection (navigator.languages) + persist localStorage + sync Accept-Language
    - Form cleanup — remove hardcoded error messages, show `problem.detail` directly

    **Testing (0.5 developer-day):**
    - Integration tests for locale-based response verification
    - LoginRateLimitFilter i18n test

    **TOTAL: ~2 developer-days** (down from 3-3.5 in first brainstorm)

## Approaches Considered

### Approach A: Full Coverage — fix all gaps including login/refresh success messages (REJECTED ❌)
- **Mô tả**: Fix LoginRateLimitFilter + add i18n message to login/refresh AuthResponse + frontend headers + form cleanup
- **Pros**: 100% consistency — every endpoint has i18n message
- **Cons**: Login/refresh returning `message` in AuthResponse is low-value (data endpoints, not action confirmations), adds code complexity for marginal UX benefit
- **Verdict**: ❌ Over-engineering. Login/refresh are data endpoints that return tokens. Forcing a success message adds noise.

### Approach B: Targeted Backend Fix + Frontend Headers (SELECTED ✅)
- **Mô tả**: Fix LoginRateLimitFilter i18n (real bug), keep login/refresh as-is (data-only), implement frontend header injection + form cleanup
- **Pros**: Fixes the only real i18n compliance gap (LoginRateLimitFilter bypasses MessageSource), completes frontend contract (Accept-Language + metadata headers), removes hardcoded frontend error messages
- **Cons**: Login/refresh don't have i18n message field — acceptable because they're data endpoints
- **Trade-off**: Consistency sacrifice (10/12 action endpoints have messages) vs pragmatism (login/refresh are data endpoints)
- **Verdict**: ✅ Best balance of effort vs value. Real bugs fixed, no over-engineering.

### Approach C: Minimal — Only LoginRateLimitFilter + Frontend Headers (CONSIDERED)
- **Mô tả**: Fix LoginRateLimitFilter, add frontend headers, skip form cleanup
- **Pros**: Minimum effort (~1 day)
- **Cons**: Leaves hardcoded error messages in frontend forms — users still see English-only errors from client-side logic even when server sends Vietnamese
- **Verdict**: ⚠️ Too minimal. Form cleanup is part of the original requirement (FR-009).

## Selected Direction

**Targeted Backend Fix + Frontend Header Injection + Form Cleanup**

Three work streams:

### 1. Backend: LoginRateLimitFilter i18n Fix (0.5 day)

```
CURRENT:
    LoginRateLimitFilter catches RateLimitExceededException
    → writeRateLimitResponse(response, ex)
    → ProblemDetail.forStatusAndDetail(429, ex.message)  ← HARDCODED ENGLISH
    → response.writer.write(json)

TARGET:
    LoginRateLimitFilter catches RateLimitExceededException
    → writeRateLimitResponse(response, ex)
    → resolve locale from LocaleContextHolder
    → messageSource.getMessage("auth.rate_limited", [retryAfterSeconds, dimension], locale)
    → ProblemDetail.forStatusAndDetail(429, resolvedMessage)  ← I18N
    → response.setHeader("Content-Language", locale.toLanguageTag())
    → response.writer.write(json)
```

```
MODIFIED CLASS:
┌──────────────────────────────────────────────────────────────┐
│ LoginRateLimitFilter                                          │
│ ─────────────────────                                         │
│ + loginRateLimitService: LoginRateLimitService                │
│ + objectMapper: ObjectMapper                                  │
│ + messageSource: MessageSource          ← NEW INJECTION       │
│ ─────────────────────                                         │
│ + writeRateLimitResponse(response, ex)  ← MODIFIED           │
│   - resolve locale from LocaleContextHolder                   │
│   - messageSource.getMessage(...)                             │
│   - set Content-Language header                               │
└──────────────────────────────────────────────────────────────┘
```

**Key consideration**: `LocaleContextHolder` is available in the filter because `AcceptHeaderLocaleResolver` is a `LocaleResolver` registered with Spring MVC. However, in the filter chain (before DispatcherServlet), `LocaleContextHolder` may not be populated yet. 

**Analysis of filter ordering:**
- `LoginRateLimitFilter` extends `OncePerRequestFilter` (no explicit @Order) → runs in Spring Security filter chain
- `AcceptHeaderLocaleResolver` is invoked by DispatcherServlet → AFTER security filter chain
- Therefore `LocaleContextHolder.getLocale()` may return default `en` in LoginRateLimitFilter

**Mitigation options:**
1. Parse `Accept-Language` header directly in LoginRateLimitFilter (recommended — simple, no dependency on DispatcherServlet)
2. Use a custom LocaleContextFilter before LoginRateLimitFilter
3. Accept that rate limit responses are always in the default locale

**Selected**: Option 1 — Parse `Accept-Language` directly. The filter already has access to `HttpServletRequest`. Use Spring's `AcceptHeaderLocaleResolver.resolveLocale(request)` or manually parse the header with the supported locales whitelist [en, vi].

```kotlin
// Approach: create a private resolveLocale helper in LoginRateLimitFilter
private fun resolveLocale(request: HttpServletRequest): Locale {
    val acceptLanguage = request.getHeader("Accept-Language") ?: return Locale.ENGLISH
    val ranges = Locale.LanguageRange.parse(acceptLanguage)
    val supported = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))
    return Locale.lookup(ranges, supported) ?: Locale.ENGLISH
}
```

### 2. Frontend: Header Injection + i18n Sync (0.5-1 day)

```
┌─────────────────────────────────────────────────────────────┐
│ FRONTEND ARCHITECTURE                                        │
│                                                              │
│   App Init                                                   │
│   ├─ Check localStorage("user_language")                     │
│   │   ├─ Found: "vi" → use                                  │
│   │   └─ Not found: navigator.languages → detect → persist  │
│   ├─ i18n.changeLanguage(detected)                           │
│   └─ api.setGlobalHeaders({                                  │
│        "Accept-Language": detected,                          │
│        "X-App-Version": APP_VERSION,                         │
│        "X-Client-Platform": "web"                            │
│      })                                                      │
│                                                              │
│   Language Switch (user action)                              │
│   ├─ i18n.changeLanguage("en")                               │
│   ├─ localStorage.set("user_language", "en")                 │
│   └─ api.setGlobalHeaders({"Accept-Language": "en"})         │
│                                                              │
│   API Response Handling                                      │
│   ├─ Success: show response.message as toast (if present)    │
│   └─ Error: show problem.detail directly (NO client compose) │
└─────────────────────────────────────────────────────────────┘
```

### 3. Frontend: Form Cleanup (0.5 day)

Remove hardcoded error message templates in frontend forms. Replace with direct display of `problem.detail` from server response. Need frontend codebase scan to identify all instances — scope determined during implementation.

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Command (cross-cutting concern affecting all API endpoints)
- Affected modules:
  - **auth-service/auth/adapter/in/web/filter**: `LoginRateLimitFilter.kt` [MODIFY — inject MessageSource, resolve i18n]
  - **admindashboard/src/utils**: `api.ts` [MODIFY — header injection]
  - **admindashboard/src/@i18n**: `I18nProvider.tsx`, `i18n.ts` [MODIFY — language detection + sync]
  - **admindashboard/src/.../forms**: Frontend forms [MODIFY — remove hardcoded error messages]

## Codebase Findings (2026-08-22 Re-scan)

### Verified Infrastructure — ALL EXISTING AND WORKING

| Component | File | Status | Key Details |
|-----------|------|--------|-------------|
| I18nConfig | `shared/config/I18nConfig.kt` | ✅ DONE | AcceptHeaderLocaleResolver (en, vi), CompositeMessageSource (DB → file), 66 lines |
| DatabaseMessageSource | `shared/i18n/DatabaseMessageSource.kt` | ✅ DONE | Caffeine cache, 5-min TTL, maxSize=500 |
| I18nMessageEntity | `shared/i18n/I18nMessageEntity.kt` | ✅ DONE | JPA entity for i18n_messages table |
| I18nMessageRepository | `shared/i18n/I18nMessageRepository.kt` | ✅ DONE | `findByCodeAndLocaleAndIsActiveTrue()` |
| AuthControllerAdvice | `shared/exception/GlobalExceptionHandler.kt` | ✅ DONE | MessageSource i18n, extractMessageArgs(), resolveMessage(), 140 lines |
| ContentLanguageFilter | `auth/adapter/in/web/filter/ContentLanguageFilter.kt` | ✅ DONE | OncePerRequestFilter, @Order(LOWEST_PRECEDENCE - 10), `/api/**`, 44 lines |
| ClientMetadataFilter | `auth/adapter/in/web/filter/ClientMetadataFilter.kt` | ✅ DONE | OncePerRequestFilter, @Order(HIGHEST_PRECEDENCE + 10), MDC(appVersion, clientPlatform), 57 lines |
| auth-messages.properties | `resources/messages/auth-messages.properties` | ✅ DONE | 55 keys (en): 44 error + 11 success, 70 lines |
| auth-messages_vi.properties | `resources/messages/auth-messages_vi.properties` | ✅ DONE | 55 keys (vi): 44 error + 11 success, 70 lines |
| CqrsAuthController | `auth/adapter/in/web/CqrsAuthController.kt` | ✅ DONE | 7/7 action endpoints use messageSource |
| SessionController | `auth/adapter/in/web/SessionController.kt` | ✅ DONE | 2/2 action endpoints use messageSource |
| AccountLifecycleController | `auth/adapter/in/web/AccountLifecycleController.kt` | ✅ DONE | 3/3 action endpoints use messageSource |

### Remaining Gap: LoginRateLimitFilter

```kotlin
// FILE: src/main/kotlin/.../filter/LoginRateLimitFilter.kt
// LINES 96-107 — writeRateLimitResponse()

private fun writeRateLimitResponse(response: HttpServletResponse, ex: RateLimitExceededException) {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message)
    //                                                                           ^^^^^^^^^^
    //                                                              ISSUE: ex.message is English-only
    //                                                              Should be: messageSource.getMessage(...)
    problem.title = "AUTH_020"
    problem.type = URI.create("https://auth-service/errors/rate_limited")
    problem.setProperty("errorCode", "AUTH_020")
    problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds)
    problem.setProperty("dimension", ex.dimension)

    response.status = HttpStatus.TOO_MANY_REQUESTS.value()
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.setHeader("Retry-After", ex.retryAfterSeconds.toString())
    // MISSING: response.setHeader("Content-Language", locale.toLanguageTag())
    response.writer.write(objectMapper.writeValueAsString(problem))
}
```

### Controllers NOT Needing i18n Messages (data-only endpoints — confirmed)

```
AnonymousAuthController   → Returns token/session data only (POST /api/auth/anonymous/*)
TokenController           → Returns introspection/revoke data (no user-facing messages)
KeyExchangeController     → Returns key exchange data
CaptchaController         → Returns captcha challenge
SsoController             → Returns auth response/status
AdminSessionController    → Returns session stats
RateLimitAdminController  → Returns rate limit status
PolicyController (pbac)   → Returns policy CRUD data
RbacControllers (rbac)    → Returns RBAC CRUD data
```

### Message Interpolation Cross-check

All exception types with message args are properly handled in AuthControllerAdvice.extractMessageArgs():

| Exception | Args | Bundle Key | EN Template | VI Template |
|-----------|------|-----------|-------------|-------------|
| RateLimitExceededException | [retryAfterSeconds, dimension] | auth.rate_limited | "Too many login attempts" | "Quá nhiều lần đăng nhập" |
| SessionLimitExceededException | [maxSessions] | auth.session_limit | "Maximum active sessions ({0}) reached" | "Đã đạt tối đa {0} phiên hoạt động" |
| MfaAccountLockedException | [retryAfterSeconds] | auth.mfa_rate_limited | "MFA rate limit exceeded" | "Đã vượt quá giới hạn tốc độ xác minh MFA" |
| AnonymousRateLimitedException | [retryAfterSeconds] | auth.anonymous_rate_limited | "Too many requests. Please try again in {0} seconds." | "Quá nhiều yêu cầu. Vui lòng thử lại sau {0} giây." |
| AnonymousMaxRenewalsException | [maxRenewals] | auth.anonymous_max_renewals | "Maximum session renewals ({0}) reached" | "Đã đạt số lần gia hạn phiên tối đa ({0})" |
| AnonymousDataLimitExceededException | [currentSize, maxSize] | auth.anonymous_data_limit_exceeded | "Data size ({0} bytes) exceeds maximum ({1} bytes)" | "Kích thước dữ liệu ({0} bytes) vượt quá giới hạn ({1} bytes)" |

⚠️ **Note**: `auth.rate_limited` template does NOT have {0}, {1} placeholders despite `RateLimitExceededException` providing `[retryAfterSeconds, dimension]` args. The current template is just "Too many login attempts" / "Quá nhiều lần đăng nhập". This means the args are extracted but **silently ignored** during interpolation. This is a minor inconsistency but not a bug — MessageFormat ignores extra args.

**Recommendation for design phase**: Consider updating `auth.rate_limited` template to use args: `"Too many login attempts ({1}). Please wait {0} seconds."` / `"Quá nhiều lần đăng nhập ({1}). Vui lòng thử lại sau {0} giây."`. This would make the rate limit error more informative.

## Design Decisions (carried forward + updated)

| # | Decision | Rationale | Status |
|---|----------|-----------|--------|
| D1 | `navigator.languages` for auto-detect, NOT IP geolocation | Privacy, zero cost, no external dependency | Carried ✅ |
| D2 | Persist language in `localStorage` | Survive page refresh, no server roundtrip | Carried ✅ |
| D3 | Supported locales: `["en", "vi"]` only | Current requirement — extensible | Carried ✅ |
| D4 | `en` default locale | International standard, safe fallback | Carried ✅ |
| D5 | BCP 47 locale format | IETF standard, Spring native | Carried ✅ |
| D6 | `ContentLanguageFilter` for 100% Content-Language coverage | NFR-004 compliance | Carried ✅ — IMPLEMENTED |
| D7 | `ClientMetadataFilter` for MDC logging | Single responsibility | Carried ✅ — IMPLEMENTED |
| D8 | Direct MessageSource injection in controllers | Pattern proven in 3 controllers | Carried ✅ — IMPLEMENTED |
| D9 | Data-only endpoints skip i18n messages | Login/refresh are data endpoints | Carried ✅ — CONFIRMED |
| D10 | Keep AuthControllerAdvice Content-Language (redundant with filter) | Defense-in-depth | Carried ✅ — CONFIRMED |
| D11 | AcceptHeaderLocaleResolver with supported locale whitelist | I18nConfig configures [en, vi] | Carried ✅ — IMPLEMENTED |
| D12 | All error codes MUST have message bundle entries | AuthControllerAdvice needs them | Carried ✅ — IMPLEMENTED |
| D13 | **NEW**: LoginRateLimitFilter parses Accept-Language directly (not via LocaleContextHolder) | Filter runs before DispatcherServlet → LocaleContextHolder may not be populated. Use `Locale.lookup()` with supported locales. | NEW |
| D14 | **NEW**: Update `auth.rate_limited` template to include interpolation args | Currently template ignores [retryAfterSeconds, dimension] args — make message more informative | NEW — RECOMMENDED |
| D15 | **NEW**: Login/refresh do NOT need i18n `message` in AuthResponse | Data endpoints — tokens are the payload. Message keys exist in bundles if ever needed. | NEW |

## Filter Chain Architecture (verified)

```
Spring Security Filter Chain:
  1. [EXISTING] SecurityFilterChain (JWT authentication)
  2. [EXISTING] LoginRateLimitFilter (pre-auth rate limiting)
          ↓ (if rate limited)
          → writeRateLimitResponse() ← THIS IS THE FIX TARGET
          ↓ (if not rate limited)

Servlet Filter Chain:
  3. [EXISTING] ClientMetadataFilter @Order(HIGHEST_PRECEDENCE + 10)
     → Extract X-App-Version, X-Client-Platform → MDC
  4. DispatcherServlet
     → AcceptHeaderLocaleResolver sets LocaleContextHolder
  5. Controller dispatch
     → Success: messageSource.getMessage() for action endpoints
     → Error: AuthException → AuthControllerAdvice → MessageSource
  6. [EXISTING] ContentLanguageFilter @Order(LOWEST_PRECEDENCE - 10)
     → response.setHeader("Content-Language", locale)
```

## Open Questions for Design Phase
- [OPEN] Should `auth.rate_limited` message template be updated to include `{0}` (retryAfterSeconds) and `{1}` (dimension) placeholders? → Recommended YES, makes rate limit errors more user-friendly
- [OPEN] Frontend cleanup scope — how many forms have hardcoded error messages? → Need frontend codebase scan during implementation (outside auth-service repo)
- [OPEN] Should LoginRateLimitFilter use a shared locale resolver utility or inline the Accept-Language parsing? → Design phase decision. Inline is simpler but creates duplication with I18nConfig's locale resolver config.

## Open Questions for URD Analysis
- Không có — đã đủ thông tin từ research + pre_openspec + codebase scan

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----|
| LoginRateLimitFilter locale resolution differs from AcceptHeaderLocaleResolver | LOW | LOW | Use same supported locales whitelist [en, vi] + same default (en). Test both paths. |
| Frontend not sending Accept-Language correctly | LOW | LOW | Server defaults to en — backward compatible. ky beforeRequest hook is proven pattern. |
| Hardcoded frontend error messages not fully identified | MEDIUM | LOW | Grep-based scan for error message patterns. Can be done iteratively — server always sends correct i18n regardless. |
| ContentLanguageFilter + AuthControllerAdvice redundancy | NONE | NONE | Already confirmed: both set same value. Defense-in-depth. No conflict. |
| Login/refresh missing `message` field requested later | LOW | LOW | Pattern exists in register endpoint. Keys exist in bundles. 5-minute code change if ever needed. |

## Implementation Estimate (Updated)

| Phase | Work Item | Effort | Status |
|-------|-----------|--------|--------|
| **Phase 1: Backend filter fix** | LoginRateLimitFilter — inject MessageSource, parse Accept-Language, resolve i18n message, set Content-Language header | 0.5 day | 🔲 TODO |
| **Phase 2: Message bundle update** | Update `auth.rate_limited` template to include `{0}`, `{1}` args (en + vi) | 0.25 day | 🔲 TODO |
| **Phase 3: Frontend headers** | api.ts — inject Accept-Language, X-App-Version, X-Client-Platform global headers | 0.25 day | 🔲 TODO |
| **Phase 4: Frontend i18n sync** | I18nProvider/i18n.ts — locale detection (navigator.languages), persist localStorage, sync Accept-Language | 0.25 day | 🔲 TODO |
| **Phase 5: Frontend cleanup** | Remove hardcoded error messages in forms, show problem.detail directly | 0.5 day | 🔲 TODO |
| **Phase 6: Testing** | Integration tests for LoginRateLimitFilter i18n, frontend header injection | 0.25 day | 🔲 TODO |
| **TOTAL** | | **2 developer-days** | |

## Comparison with Previous Brainstorm

| Aspect | Previous (2026-08-11) | Current (2026-08-22) | Delta |
|--------|----------------------|---------------------|-------|
| Infrastructure done | ~90% | ~97% | +7% (filters, message keys, controller i18n implemented) |
| Missing message keys | ~17 keys × 2 locales | 0 keys (maybe 1 update for rate_limited) | -17 keys |
| Missing filters | 2 (ContentLanguageFilter, ClientMetadataFilter) | 0 | -2 filters |
| Missing controller i18n | ~5 controllers, ~10 endpoints | 0 controllers (all action endpoints done) | Fully resolved |
| Remaining backend work | 2 developer-days | 0.5 developer-days | -75% |
| Remaining frontend work | 1 developer-day | 1 developer-day | Same |
| Total estimate | 3.5 developer-days | 2 developer-days | -43% |
| Open questions | 7 (3 open) | 3 (3 open — all minor) | Reduced |
| Risks | 5 (2 medium) | 5 (1 medium — frontend scan scope) | Reduced |
