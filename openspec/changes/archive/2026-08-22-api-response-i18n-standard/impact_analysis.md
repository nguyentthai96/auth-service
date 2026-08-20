# Impact Analysis: api-response-i18n-standard

_Generated: 2026-08-22 | Updated: 2026-08-22 (wf_openspec merge — re-scan against current codebase)_

> **[CHANGED] vs 2026-08-11**: Infrastructure is now ~97% implemented. All previously-planned filters, controller i18n, message bundles are EXISTING. Only LoginRateLimitFilter i18n gap remains on backend. Frontend changes still pending.

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [LoginRateLimitFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt) | L24-28, L94-107 | Constructor — inject `MessageSource`. `writeRateLimitResponse()` — resolve i18n message via MessageSource, parse Accept-Language from request, set Content-Language header |
| 2 | [auth-messages.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | L33 | `auth.rate_limited` — OPTIONAL: update template to include `{0}` (retryAfterSeconds) and `{1}` (dimension) interpolation |
| 3 | [auth-messages_vi.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | L33 | `auth.rate_limited` — matching Vietnamese template update |

## [REMOVED] Files No Longer Needing Changes (vs 2026-08-11)

| # | File | Previous Action | Current Status |
|---|------|----------------|----------------|
| 1 | `GlobalExceptionHandler.kt` | MODIFY — add MessageSource i18n | ✅ ALREADY DONE — resolveMessage(), extractMessageArgs(), setContentLanguageHeader() |
| 2 | `I18nConfig.kt` | MODIFY — add AcceptHeaderLocaleResolver | ✅ ALREADY DONE — localeResolver() bean with [en, vi] |
| 3 | `CqrsAuthController.kt` | MODIFY — extend success i18n | ✅ ALREADY DONE — 5 endpoints use messageSource |
| 4 | `SessionController.kt` | MODIFY — inject MessageSource | ✅ ALREADY DONE — session_revoked, all_sessions_revoked |
| 5 | `AccountLifecycleController.kt` | MODIFY — inject MessageSource | ✅ ALREADY DONE — account_deactivated, deletion_requested, deletion_cancelled |
| 6 | `ContentLanguageFilter.kt` | NEW | ✅ ALREADY DONE — OncePerRequestFilter, `/api/**` |
| 7 | `ClientMetadataFilter.kt` | NEW | ✅ ALREADY DONE — OncePerRequestFilter, MDC |
| 8 | `auth-messages*.properties` (55 keys) | MODIFY — add 22 missing keys | ✅ ALREADY DONE — 44 error + 11 success keys |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### `LoginRateLimitFilter.writeRateLimitResponse()` (L94-107) — THE ONLY BACKEND FIX

```
⟶ HTTP Request POST /api/auth/login (Accept-Language: vi)
├── shouldNotFilter(request) → false (POST + /api/auth/login)
├── doFilterInternal(request, response, filterChain)
│   ├── extractClientIp(request) → ip
│   ├── loginRateLimitService.checkMultiDimensional(ip, username, fingerprint)
│   │   └── throws RateLimitExceededException(retryAfterSeconds=60, dimension="IP")
│   └── writeRateLimitResponse(response, ex)                    // ← FIX TARGET
│       ├── [CURRENT — BROKEN i18n]
│       │   └── ProblemDetail.forStatusAndDetail(429, ex.message)   // ← English hardcoded
│       │
│       └── [TARGET — i18n enabled]
│           ├── resolveLocale(request)                              // ← NEW private method
│           │   ├── request.getHeader("Accept-Language") → "vi"
│           │   ├── Locale.LanguageRange.parse("vi")
│           │   ├── Locale.lookup(ranges, [en, vi]) → Locale("vi")
│           │   └── return Locale("vi")
│           ├── messageSource.getMessage(                           // ← NEW injection
│           │     "auth.rate_limited",
│           │     arrayOf(ex.retryAfterSeconds, ex.dimension),
│           │     ex.message,                                       // ← fallback to ex.message
│           │     locale
│           │   ) → "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây."
│           ├── ProblemDetail.forStatusAndDetail(429, resolvedMessage) // ← i18n message
│           ├── response.setHeader("Content-Language", locale.toLanguageTag()) // ← NEW
│           └── response.writer.write(json)
```

#### Filter Chain Position (context)

```
Spring Security Filter Chain:
  1. SecurityFilterChain (JWT authentication)
  2. LoginRateLimitFilter ← THIS FILTER (catches exception BEFORE DispatcherServlet)
     → writeRateLimitResponse() writes directly to response
     → BYPASSES: AcceptHeaderLocaleResolver, ContentLanguageFilter, AuthControllerAdvice
     → Therefore MUST: parse Accept-Language + set Content-Language + resolve i18n INLINE

Servlet Filter Chain (only reached if NOT rate-limited):
  3. ClientMetadataFilter @Order(HIGHEST_PRECEDENCE + 10)
  4. DispatcherServlet → AcceptHeaderLocaleResolver
  5. Controller dispatch → MessageSource for success messages
  6. AuthControllerAdvice → MessageSource for error messages
  7. ContentLanguageFilter @Order(LOWEST_PRECEDENCE - 10) → Content-Language header
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (1 file)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `LoginRateLimitFilter.kt` | [LoginRateLimitFilter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt) | Inject `MessageSource` into constructor, modify `writeRateLimitResponse()` to resolve i18n message + set Content-Language. Add private `resolveLocale()` helper. |

### 🟡 Indirect Impact — auth-service (2 files — message bundles only)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `auth-messages.properties` | [en bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | Optional: update `auth.rate_limited` template to include `{0}`, `{1}` placeholders |
| 2 | `auth-messages_vi.properties` | [vi bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | Optional: matching Vietnamese template update |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. `LoginRateLimitFilter` is auth-service internal. `MessageSource` is already injected project-wide.

### 🟢 Shared Utilities (0 files)

No shared utility extractions needed. `resolveLocale()` is a 4-line private method — no extraction value.

---

## 4. Reuse Map

> Follows `reuse_rules_compact.md` Step 2 Decision Framework.

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Locale resolution | `AcceptHeaderLocaleResolver` in `I18nConfig.kt:39-44` | 80% | **REUSE** (concept) | 🟢 (0 callers affected) | Inline same whitelist `[en, vi]` in filter using `Locale.lookup()`. Cannot reuse bean — filter runs pre-DispatcherServlet. |
| ProblemDetail creation | `AuthControllerAdvice.handleAuthException()` in `GlobalExceptionHandler.kt:48-99` | 70% | **REUSE** (pattern) | 🟢 (0 callers affected) | Same structure but filter writes directly to response. Keep direct write pattern. |
| MessageSource injection | `CqrsAuthController`, `SessionController`, `AccountLifecycleController` | 100% | **REUSE** (pattern) | 🟢 (0 callers affected) | Constructor-inject `MessageSource` into `LoginRateLimitFilter` — proven pattern |
| Content-Language header | `ContentLanguageFilter` + `AuthControllerAdvice.setContentLanguageHeader()` | 100% | **REUSE** (pattern) | 🟢 (0 callers affected) | Set `Content-Language` in `writeRateLimitResponse()` since filter bypasses both |

> No EXTRACT operations needed. All logic blocks are REUSE of existing patterns or small inline additions.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

> Agent đọc section này → đủ info bắt tay code, KHÔNG cần search thêm.

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `MessageSource` | Interface (Spring, inject into constructor) | `getMessage(code, args, defaultMessage, locale)` | CompositeMessageSource from I18nConfig: DB → file chain |
| `ObjectMapper` | Jackson (already injected) | `writeValueAsString(problem)` | Serialize ProblemDetail to JSON |
| `LoginRateLimitService` | Service (already injected) | `checkMultiDimensional(ip, username, fingerprint)` | Throws `RateLimitExceededException` |
| `Locale` | JDK static utility | `Locale.LanguageRange.parse(header)`, `Locale.lookup(ranges, supported)` | Parse Accept-Language header |

### Config Keys

| Key | Source | Example Value | Nơi dùng |
|-----|--------|--------------|---------|
| Supported locales | Hardcoded in `resolveLocale()` helper | `[en, vi]` | Must match `I18nConfig.localeResolver()` config |
| Default locale | Hardcoded in `resolveLocale()` helper | `Locale.ENGLISH` | Fallback when no match |

### Error Codes Thrown

| Error Code | Message Key | Bundle Status |
|-----------|-------------|--------------|
| `AUTH_020` | `auth.rate_limited` | ✅ Present in both en + vi bundles (70 lines each) |

### Message Template Status

| Key | Current EN | Current VI | Args Used? |
|-----|-----------|-----------|-----------|
| `auth.rate_limited` | "Too many login attempts" | "Quá nhiều lần đăng nhập" | ⚠️ Args `[retryAfterSeconds, dimension]` extracted by `extractMessageArgs()` but **silently ignored** (no `{0}`, `{1}` in template). MessageFormat ignores extra args — not a bug but suboptimal. |

> ⚠️ OPEN QUESTION from brainstorm (D14): Recommend updating template to include args for more informative rate limit errors.

### Design Decision (from brainstorm D13)

LoginRateLimitFilter **MUST parse Accept-Language directly** because:
1. Filter runs in Spring Security filter chain — BEFORE DispatcherServlet
2. `AcceptHeaderLocaleResolver` invoked BY DispatcherServlet → not available in filter
3. `LocaleContextHolder.getLocale()` returns JVM default in filter context

Implementation:
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

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `messageSource.getMessage()` | `MessageSource.getMessage(String, Array<Any>?, String, Locale)` | Spring Framework | ✅ |
| `Locale.LanguageRange.parse()` | `Locale.LanguageRange.parse(String): List<LanguageRange>` | JDK | ✅ |
| `Locale.lookup()` | `Locale.lookup(List<LanguageRange>, Collection<Locale>): Locale?` | JDK | ✅ |
| `response.setHeader()` | `HttpServletResponse.setHeader(String, String)` | Jakarta Servlet | ✅ |
