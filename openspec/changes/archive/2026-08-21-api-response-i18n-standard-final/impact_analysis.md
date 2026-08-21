# Impact Analysis: api-response-i18n-standard

_Generated: 2026-08-27 | Mode: DELTA (archive: 2026-08-25)_

> **[CHANGED] vs 2026-08-25**: ALL 7 remaining backend action endpoints NOW IMPLEMENTED. Backend is 100% complete (18/18 action endpoints + 1 filter). The remaining work is **frontend-only** (admindashboard — external repo) and **backend integration tests**.
> **Classification**: EXTEND | **FR count**: 13

---

## 1. Core Files — NƠI SỬA

> **Backend: No more code changes needed.** All controller + filter + bundle changes from archive are DONE.
> Files below are for **verification/testing only** — NOT for code modification.

### 1a. Backend Files — ĐÃ IMPLEMENT (VERIFY ONLY)

| # | File | Status | Chức năng |
|---|------|--------|-----------|
| 1 | [MfaController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | ✅ DONE | `messageSource.getMessage()` at L56 (confirmTotp) + L118 (regenerateRecoveryCodes) |
| 2 | [TokenController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | ✅ DONE | `messageSource.getMessage()` at L63 (revokeAllSessions) |
| 3 | [SsoController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt) | ✅ DONE | `messageSource.getMessage()` at L48 (linkIdentity) + L57 (unlinkIdentity) |
| 4 | [AdminSessionController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt) | ✅ DONE | `messageSource.getMessage()` at L87 (forceRevokeUserSessions) |
| 5 | [RateLimitAdminController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt) | ✅ DONE | `messageSource.getMessage()` at L58 (adminUnlock) |
| 6 | [auth-messages.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | ✅ DONE | 77 lines, 55+ keys, all new success keys present |
| 7 | [auth-messages_vi.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | ✅ DONE | 77 lines, 55+ keys, matching Vietnamese translations |

### 1b. Frontend Files — CẦN MODIFY (admindashboard — external repo)

| # | File | Action | Chức năng |
|---|------|--------|-----------|
| 1 | `admindashboard/src/utils/api.ts` | [MODIFY] | Inject `Accept-Language`, `X-App-Version`, `X-Client-Platform` headers |
| 2 | `admindashboard/src/@i18n/I18nProvider.tsx` | [MODIFY] | Sync language change → `Accept-Language` header |
| 3 | `admindashboard/src/@i18n/i18n.ts` | [MODIFY] | `supportedLngs: ['en', 'vi']`, `fallbackLng: 'en'` |
| 4 | `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | [MODIFY] | Remove hardcoded error messages, use `problem.detail` |

### 1c. Backend Test Files — CẦN TẠO MỚI

| # | File | Action | Chức năng |
|---|------|--------|-----------|
| 1 | `src/test/kotlin/.../web/I18nIntegrationTest.kt` | [NEW] | Integration tests for i18n behavior: locale resolution, Content-Language header, success/error message i18n |

---

## 2. Call Tree — LOGIC CẦN SỬA

> **No new code changes needed in backend.** All call trees from archive (2026-08-25) are IMPLEMENTED.
> Call trees below represent the CURRENT IMPLEMENTED state — for reference only.

#### Success i18n Pattern (ALL 18 endpoints follow this pattern — VERIFIED)

```
⟶ POST /api/auth/{action} (Accept-Language: vi)
├── ClientMetadataFilter (HIGHEST_PRECEDENCE + 10)
│   └── MDC.put(appVersion, clientPlatform)
├── AcceptHeaderLocaleResolver
│   └── resolve("vi") → LocaleContextHolder.setLocale(vi)
├── Controller.{action}(request)
│   ├── {businessService}.{operation}(params)
│   ├── val locale = LocaleContextHolder.getLocale()  // ← vi
│   ├── val message = messageSource.getMessage("{key}", args, "{default}", locale)  // ← CompositeMessageSource chain
│   └── ResponseEntity.ok(mapOf("field" to value, "message" to message))
└── ContentLanguageFilter (LOWEST_PRECEDENCE - 10)
    └── response.setHeader("Content-Language", "vi")
```

#### Error i18n Pattern (ALL exceptions follow this pattern — VERIFIED)

```
⟶ POST /api/auth/{action} (Accept-Language: vi)
├── Controller throws AuthException(AuthErrorCode.{CODE})
├── AuthControllerAdvice.handleAuthException(ex)
│   ├── resolveMessage(ex.authError, request)
│   │   ├── extractMessageArgs(ex) → arrayOf({arg1}, {arg2})
│   │   └── messageSource.getMessage(ex.authError.msgCode, args, ex.authError.description, locale)
│   ├── ProblemDetail.forStatusAndDetail(httpStatus, resolvedMessage)
│   │   ├── setType(URI("https://auth-service/errors/{errorCode}"))
│   │   ├── setTitle(errorCode.toString())
│   │   └── setProperty("errorCode", errorCode)
│   └── setContentLanguageHeader(response, locale)
└── ContentLanguageFilter
    └── response.setHeader("Content-Language", "vi")  // ← defense-in-depth
```

#### Rate Limit Filter i18n (PRE-DispatcherServlet — VERIFIED)

```
⟶ POST /api/auth/login (Accept-Language: vi) — rate limited
├── LoginRateLimitFilter.doFilterInternal()
│   ├── rateLimitService.checkRateLimit(key, dimension)  // ← BLOCKED
│   ├── resolveLocale(request)  // ← own implementation (LocaleContextHolder NOT available)
│   │   └── Locale.LanguageRange.parse(acceptLanguage) → Locale.lookup(supported=[en,vi])
│   ├── messageSource.getMessage("auth.rate_limited", arrayOf(retryAfter, dimension), default, locale)
│   └── writeRateLimitResponse(response, locale, detail, retryAfter, dimension)
│       ├── response.setHeader("Content-Language", locale.language)
│       └── objectMapper.writeValue(response.writer, ProblemDetail)
└── [STOPS HERE — does NOT reach DispatcherServlet]
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (0 files — ALL DONE)

**No remaining direct impact.** All 7 controller files from archive (2026-08-25) have been modified and verified.

### 🟡 Indirect Impact — auth-service (1 file — NEW)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `I18nIntegrationTest.kt` | [NEW TEST] | Integration test to verify i18n behavior across representative endpoints |

### 🟠 Cross-service Impact (0 files — backend)

No cross-service impact. All changes are auth-service controller-layer modifications. `MessageSource` bean already configured project-wide via `I18nConfig`.

> ⚠️ Frontend (admindashboard) impacts listed separately in `fe_tasks.md`.

### 🟢 Shared Utilities (0 files)

No shared utility extractions needed. The `messageSource.getMessage()` pattern is a direct Spring API call — no custom abstraction.

---

## 4. Reuse Map

> Follows `reuse_rules_compact.md` Step 2 Decision Framework.

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| MessageSource injection + getMessage() | ALL 9 controllers + 1 filter (18 endpoints) | 100% | **REUSE** (pattern — DONE) | 🟢 (0 callers affected) | All controllers already use constructor-injected `MessageSource` + `getMessage(key, args, defaultMsg, locale)` |
| `LocaleContextHolder.getLocale()` locale resolution | ALL 9 controllers | 100% | **REUSE** (pattern — DONE) | 🟢 (0 callers affected) | Spring standard — already used consistently |
| Map-based response with "message" field | ALL action endpoints | 100% | **REUSE** (pattern — DONE) | 🟢 (0 callers affected) | `ResponseEntity.ok(mapOf("field" to value, "message" to resolvedMessage))` |
| Frontend header injection pattern | `api.ts` `setGlobalHeaders()` utility | 100% | **REUSE** (utility exists) | 🟢 (0 callers affected) | Wire `Accept-Language`, `X-App-Version`, `X-Client-Platform` into existing utility |

> No EXTRACT operations needed. All backend patterns are proven and consistently applied.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

> Agent đọc section này → đủ info bắt tay code, KHÔNG cần search thêm.

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `MessageSource` | Interface (Spring, injected via constructor) | `getMessage(code, args, defaultMessage, locale)` | CompositeMessageSource from I18nConfig: DB → file chain. Already injected in ALL 9 controllers + 1 filter |
| `LocaleContextHolder` | Static utility (Spring) | `getLocale()` | Thread-local locale from AcceptHeaderLocaleResolver. Used by all controllers |
| `ContentLanguageFilter` | Servlet filter (OncePerRequestFilter) | `doFilterInternal()` | LOWEST_PRECEDENCE - 10. Sets `Content-Language` on ALL `/api/**` responses |
| `ClientMetadataFilter` | Servlet filter (OncePerRequestFilter) | `doFilterInternal()` | HIGHEST_PRECEDENCE + 10. Extracts `X-App-Version`, `X-Client-Platform` → MDC |
| `AuthControllerAdvice` | @ControllerAdvice | `handleAuthException()`, `resolveMessage()` | Renders ProblemDetail with i18n `detail` field for ALL AuthException subtypes |

### Config Keys

| Key | Source | Example Value | Nơi dùng |
|-----|--------|--------------|---------|
| Supported locales | `I18nConfig.kt:42-43` | `[en, vi]` | `AcceptHeaderLocaleResolver` → `LocaleContextHolder` |
| Default locale | `I18nConfig.kt:43` | `Locale.ENGLISH` | Fallback when no `Accept-Language` match |
| Caffeine cache TTL | `DatabaseMessageSource.kt` | `5 minutes, maxSize=500` | In-process cache for DB-backed messages |

### Error Codes Thrown

No new error codes. All 62+ existing error codes (AUTH_001-AUTH_062) already have i18n support via `AuthControllerAdvice` + message bundles.

### DTO / Response Type Changes — ALL DONE

| Endpoint | Previous Return | Current Return | Status |
|---|---|---|---|
| MfaController.confirmTotp() | `Map<String, Boolean>` | `Map<String, Any>` with `message` | ✅ DONE |
| MfaController.regenerateRecoveryCodes() | `Map<String, Any>` (hardcoded warning) | `Map<String, Any>` (i18n warning) | ✅ DONE |
| TokenController.revokeAllSessions() | Map with `message` field | Map with i18n `message` field | ✅ DONE |
| SsoController.linkIdentity() | `Map<String, Boolean>` | `Map<String, Any>` with `message` | ✅ DONE |
| SsoController.unlinkIdentity() | `Map<String, Boolean>` | `Map<String, Any>` with `message` | ✅ DONE |
| AdminSessionController.forceRevokeUserSessions() | `Map<String, Any>` (hardcoded EN) | `Map<String, Any>` (i18n message) | ✅ DONE |
| RateLimitAdminController.adminUnlock() | Map with `message` field | Map with i18n `message` field | ✅ DONE |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `messageSource.getMessage()` | `MessageSource.getMessage(String, Array<Any>?, String, Locale)` | Spring Framework — 19 occurrences in codebase | ✅ Verified |
| `LocaleContextHolder.getLocale()` | `LocaleContextHolder.getLocale(): Locale` | Spring Framework — all i18n controllers | ✅ Verified |
| `ContentLanguageFilter` | `OncePerRequestFilter.doFilterInternal()` | auth-service — `ContentLanguageFilter.kt` | ✅ Verified |
| `AuthControllerAdvice.resolveMessage()` | `resolveMessage(AuthErrorCode, HttpServletRequest): String` | `GlobalExceptionHandler.kt:125-131` | ✅ Verified |
