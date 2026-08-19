# Impact Analysis: api-response-i18n-standard

_Generated: 2026-08-11 | Updated: 2026-08-11 (wf_openspec merge)_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [GlobalExceptionHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt) | L31-140 | `AuthControllerAdvice` — error ProblemDetail i18n, Content-Language header (EXISTING — verify i18n coverage) |
| 2 | [I18nConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt) | L1-41 | CompositeMessageSource chain config — add `AcceptHeaderLocaleResolver` bean with locale whitelist |
| 3 | [CqrsAuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | L37-282 | Extend success i18n to remaining endpoints (login, register, refresh, switch-domain) |
| 4 | [auth-messages.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | L1-44 | Add 17 missing message keys (E2EE AUTH_030-039, Anonymous AUTH_040-044, success messages) |
| 5 | [auth-messages_vi.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | L1-44 | Add 17 missing Vietnamese translations matching en bundle |

## New Files

| # | File | Type | Purpose |
|---|------|------|---------|
| 1 | `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt` | Filter | `OncePerRequestFilter` — sets `Content-Language` on ALL `/api/**` responses (NFR-004 100% coverage) |
| 2 | `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt` | Filter | `OncePerRequestFilter` — extracts `X-App-Version`, `X-Client-Platform` → MDC for audit logging |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### Request Processing Chain (Full i18n flow)

```
⟶ HTTP Request (Accept-Language: vi, X-App-Version: 1.0.0, X-Client-Platform: web)
├── AcceptHeaderLocaleResolver.resolveLocale(request)  // ← Spring built-in, configured in I18nConfig
│   ├── supportedLocales = [en, vi]
│   ├── match("vi") → Locale("vi")
│   └── → LocaleContextHolder.setLocale(vi)
│
├── ClientMetadataFilter.doFilterInternal(request, response, chain)  // ← NEW filter
│   ├── MDC.put("appVersion", request.getHeader("X-App-Version"))
│   ├── MDC.put("clientPlatform", request.getHeader("X-Client-Platform"))
│   └── chain.doFilter() → cleanup MDC in finally
│
├── ContentLanguageFilter.doFilterInternal(request, response, chain)  // ← NEW filter
│   ├── chain.doFilter()  // ← controller processes request
│   └── response.setHeader("Content-Language", LocaleContextHolder.getLocale().toLanguageTag())
│
├── [SUCCESS PATH]
│   └── CqrsAuthController.logout(request, response)  // ← example endpoint
│       └── messageSource.getMessage("auth.logout_success", null, locale)
│           ├── DatabaseMessageSource.resolveCode("auth.logout_success", vi)  // ← cache check
│           │   ├── cache HIT → return MessageFormat
│           │   └── cache MISS → repository.findByCodeAndLocaleAndIsActiveTrue()
│           │       ├── found → cache.put() + return MessageFormat
│           │       └── null → delegate to parent ↓
│           └── ReloadableResourceBundleMessageSource  // ← file bundle fallback
│               └── auth-messages_vi.properties → "Đã đăng xuất thành công"
│
└── [ERROR PATH]
    └── AuthException thrown → AuthControllerAdvice.handleAuthException(ex, response)
        ├── extractMessageArgs(ex)  // ← RateLimitExceededException → [retryAfterSeconds, dimension]
        ├── resolveMessage(authError.msgCode, args, fallback)
        │   └── messageSource.getMessage("auth.rate_limited", [5, "IP"], "Too many...", vi)
        │       └── → "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 5 giây."
        ├── ProblemDetail.forStatusAndDetail(429, resolvedMessage)
        └── setContentLanguageHeader(response)  // ← redundant with filter, defense-in-depth
```

#### Filter Ordering

```
Spring Filter Chain:
  1. SecurityFilterChain (Spring Security — authentication)
  2. LoginRateLimitFilter (existing — pre-auth rate limiting)
  3. ClientMetadataFilter (NEW — request-phase: headers → MDC)
  4. ContentLanguageFilter (NEW — response-phase: wraps doFilter, sets Content-Language AFTER)
  5. Controller dispatch
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (5 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `GlobalExceptionHandler.kt` | [AuthControllerAdvice](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt) | Already uses MessageSource for ProblemDetail.detail — verify E2EE/Anonymous coverage |
| 2 | `CqrsAuthController.kt` | [CqrsAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | Inject MessageSource (already done for 3/7 endpoints), extend to remaining |
| 3 | `I18nConfig.kt` | [I18nConfig](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt) | Add AcceptHeaderLocaleResolver bean with locale whitelist [en, vi] |
| 4 | `auth-messages.properties` | [en bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | Add 17 missing keys (E2EE, Anonymous, success) |
| 5 | `auth-messages_vi.properties` | [vi bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | Add 17 missing Vietnamese translations |

### 🟡 Indirect Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `SessionController.kt` | (in auth/adapter/in/web/) | Has 2 hardcoded English strings ("Session revoked", "All sessions revoked") — needs messageSource |
| 2 | `AccountLifecycleController.kt` | (in auth/adapter/in/web/) | Has 3 hardcoded English strings — needs messageSource |
| 3 | `DatabaseMessageSource.kt` | [DatabaseMessageSource](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt) | EXISTING — no changes needed, works as-is |

### 🟠 Cross-service Impact (2 files — base-core)

| # | File | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | `BaseControllerAdvice.kt` | base-core shared library | In-process | Inject `MessageSource?` nullable — backward compatible for all services |
| 2 | `ApiResponse.kt` | base-core shared library | In-process | Optional: document `message` field for i18n usage — no code change needed |

### 🟢 Shared Utilities (0 files)

No shared utility extractions needed. All patterns reuse existing Spring framework components.

---

## 4. Reuse Map

> Follows `reuse_rules_compact.md` Step 2 Decision Framework.

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Error message i18n | `AuthControllerAdvice.resolveMessage()` L125-130 | 100% | **REUSE** | 🟢 (1 caller) | Already in place — extend coverage to E2EE/Anonymous |
| CompositeMessageSource chain | `I18nConfig.messageSource()` L24-41 | 100% | **REUSE** | 🟢 (1 caller) | Already configured — add locale resolver bean |
| DatabaseMessageSource cache | `DatabaseMessageSource.resolveCode()` L28-52 | 100% | **REUSE** | 🟢 (0 direct callers) | Transparent via MessageSource chain — no changes |
| Success message pattern | `CqrsAuthController.logout()` L172+ | 100% | **REUSE** | 🟢 (1 caller) | Proven pattern — apply to remaining endpoints |
| Content-Language header | `AuthControllerAdvice.setContentLanguageHeader()` L134-140 | 80% | **EXTRACT** | 🟢 Low (1 caller) | Extract to `ContentLanguageFilter` for 100% coverage (NFR-004) |
| `setGlobalHeaders()` | `admindashboard/src/utils/api.ts` | 100% | **REUSE** | 🟢 (1 caller) | Wire Accept-Language via existing utility |
| Spring AcceptHeaderLocaleResolver | Spring Framework built-in | 100% | **REUSE** | 🟢 (0 custom code) | Configure bean — zero custom code |
| Spring AbstractMessageSource | Spring Framework built-in | 100% | **REUSE** | 🟢 (1 subclass) | DatabaseMessageSource already extends this |

### EXTRACT Details

#### E1: Content-Language Header → ContentLanguageFilter

**Source:** `AuthControllerAdvice.setContentLanguageHeader()` — L134-140, sets `Content-Language` for error responses only
**Target:** `ContentLanguageFilter` — `auth/adapter/in/web/filter/ContentLanguageFilter.kt`
**Callers found:** 1 file (`AuthControllerAdvice`)
- AuthControllerAdvice → keep existing call (defense-in-depth, same value)

**Impact level:** 🟢 Low
**Breaking changes:** None — filter is additive
**Migration plan:** Add filter, keep AuthControllerAdvice logic (redundant but harmless — both set same value from LocaleContextHolder)

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

> Agent đọc section này → đủ info bắt tay code, KHÔNG cần search thêm.

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `MessageSource` | Interface (Spring, injected) | `getMessage(code, args, locale)`, `getMessage(code, args, defaultMessage, locale)` | CompositeMessageSource chain from I18nConfig |
| `LocaleContextHolder` | Static utility | `getLocale()` | Thread-bound locale from AcceptHeaderLocaleResolver |
| `I18nMessageRepository` | JPA Repository (injected) | `findByCodeAndLocaleAndIsActiveTrue(code, locale)` | Used by DatabaseMessageSource |
| `Caffeine` | Cache library | `cache.getIfPresent(key)`, `cache.put(key, value)` | 5-min TTL, maxSize=500 |

### Config Keys

| Key | Source | Example Value | Nơi dùng |
|-----|--------|--------------|---------|
| `app.security.cqrs.enabled` | `application.yml` | `true` | CqrsAuthController conditional |
| Supported locales | `AcceptHeaderLocaleResolver` bean | `[en, vi]` | I18nConfig |
| Default locale | `AcceptHeaderLocaleResolver` bean | `ENGLISH` | I18nConfig |

### Error Codes Thrown

| Error Code | Message Key | Missing in Bundle? |
|-----------|-------------|-------------------|
| AUTH_001-021 | `auth.invalid_credentials` ... `auth.session_limit` | ✅ All present (21 keys) |
| AUTH_030-039 | `auth.e2ee_time_skew` ... `auth.e2ee_max_devices` | ❌ All 10 MISSING — need to add |
| AUTH_040-044 | `auth.anonymous_session_expired` ... `auth.anonymous_max_renewals` | ❌ All 5 MISSING — need to add |

### Success Message Keys (Missing)

| Key | Endpoint | Status |
|-----|----------|--------|
| `auth.login_success` | CqrsAuthController.login() | ✅ Present |
| `auth.logout_success` | CqrsAuthController.logout() | ✅ Present |
| `auth.password_changed` | CqrsAuthController.changePassword() | ✅ Present |
| `auth.token_refreshed` | CqrsAuthController.refresh() | ✅ Present |
| `auth.password_reset_sent` | CqrsAuthController.forgotPassword() | ✅ Present |
| `auth.register_success` | CqrsAuthController.register() | ❌ MISSING |
| `auth.switch_domain_success` | CqrsAuthController.switchDomain() | ❌ MISSING |
| `auth.session_revoked` | SessionController.revokeSession() | ❌ MISSING |
| `auth.all_sessions_revoked` | SessionController.revokeAllSessions() | ❌ MISSING |
| `auth.account_deactivated` | AccountLifecycleController | ❌ MISSING |
| `auth.deletion_requested` | AccountLifecycleController | ❌ MISSING |
| `auth.deletion_cancelled` | AccountLifecycleController | ❌ MISSING |

### Design Decision (from brainstorm Selected Direction)

- **Content-Language**: Servlet Filter (`ContentLanguageFilter`) for 100% coverage — NOT per-controller
- **Client metadata**: Separate filter (`ClientMetadataFilter`) for MDC logging
- **Success i18n**: Direct MessageSource injection in controllers (~5 controllers need it)
- **Locale detection (frontend)**: Hybrid Cascade (localStorage → navigator.languages → "en")
- **Supported locales**: `[en, vi]` — extensible later

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `messageSource.getMessage()` | `MessageSource.getMessage(String, Object[], String, Locale)` | Spring Framework | ✅ |
| `LocaleContextHolder.getLocale()` | `LocaleContextHolder.getLocale(): Locale` | Spring Framework | ✅ |
| `response.setHeader("Content-Language", ...)` | `HttpServletResponse.setHeader(String, String)` | Jakarta Servlet | ✅ |
| `MDC.put(key, value)` | `MDC.put(String, String)` | SLF4J | ✅ |
