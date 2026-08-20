<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- updated: 2026-08-22 — re-scanned against current codebase, ~97% infrastructure already implemented -->

# Tasks: api-response-i18n-standard

> **Type**: EXTEND | **Flow**: Command | **FRs**: 14 (9 URD + 4 ENRICHED + 1 NEW from brainstorm)
> **Direction**: Targeted Backend Fix + Frontend Header Injection — LoginRateLimitFilter i18n + Frontend Accept-Language/Metadata Headers + Hardcoded Message Cleanup

## Changes

[CHANGED] Feature EXTENDS existing i18n infrastructure (**~97% already implemented**). Previous tasks.md (2026-08-11) listed 20 tasks — most are now verified DONE. This updated tasks.md reflects the ACTUAL remaining work:

**Backend (1 file modify)**: LoginRateLimitFilter — inject MessageSource, parse Accept-Language, resolve i18n, set Content-Language
**Message bundles (2 files modify, optional)**: Update `auth.rate_limited` template with interpolation placeholders
**Frontend (4 files modify, 1 file new)**: api.ts headers, I18nProvider sync, i18n.ts detection, JwtSignInForm cleanup, VN flag asset, LanguageSwitcher cleanup

**Total remaining**: 3 backend files (1 mandatory + 2 optional), 5 frontend files (4 modify + 1 new)

---

## Phase 1: Backend — LoginRateLimitFilter i18n Fix (MANDATORY)

- [x] **Task 1: Modify LoginRateLimitFilter — inject MessageSource + i18n ProblemDetail**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | Action: [MODIFY]
  - FR: FR-004B — LoginRateLimitFilter ProblemDetail i18n
  - Pattern: Constructor injection, private helper method, same ProblemDetail structure
  - Dependencies: `MessageSource` (Spring, from `I18nConfig` CompositeMessageSource bean), `Locale.LanguageRange`, `Locale.lookup()`
  - Source: Pattern from `AuthControllerAdvice.resolveMessage()` (`GlobalExceptionHandler.kt:125-131`) — REUSE concept
  - Details:
    - **[MODIFY L24-28]** Add `private val messageSource: MessageSource` to constructor params (alongside existing `loginRateLimitService` and `objectMapper`)
    - **[NEW]** Add private `resolveLocale(request: HttpServletRequest): Locale` helper:
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
    - **[MODIFY L94-107]** Update `writeRateLimitResponse()`:
      1. Call `val locale = resolveLocale(request)` — NOTE: must pass `request` param (currently method signature is `(response, ex)` — add `request` param)
      2. Resolve message: `val detail = messageSource.getMessage("auth.rate_limited", arrayOf(ex.retryAfterSeconds, ex.dimension), ex.message, locale)`
      3. Replace `ProblemDetail.forStatusAndDetail(429, ex.message)` → `ProblemDetail.forStatusAndDetail(429, detail)`
      4. Add `response.setHeader("Content-Language", locale.toLanguageTag())` before `response.writer.write()`
    - **[MODIFY L61]** Update call site in `doFilterInternal()`: `writeRateLimitResponse(request, response, ex)` (add `request` param)
    - **Imports to add**: `org.springframework.context.MessageSource`, `java.util.Locale`
  - Validation:
    - `Accept-Language: vi` + rate limited → ProblemDetail.detail in Vietnamese + `Content-Language: vi`
    - `Accept-Language: en` + rate limited → ProblemDetail.detail in English + `Content-Language: en`
    - No `Accept-Language` header → fallback English + `Content-Language: en`
    - `Accept-Language: ja` → fallback English (unsupported locale)
    - MessageSource unavailable → fallback to `ex.message` (defaultMessage param)

## Phase 2: Message Bundle Enhancement (RECOMMENDED)

- [x] **Task 2: Update `auth.rate_limited` message template — English**
  - File: `src/main/resources/messages/auth-messages.properties` | Action: [MODIFY]
  - FR: D14 — Update template with interpolation args
  - Details:
    - **[MODIFY L33]** Change: `auth.rate_limited=Too many login attempts`
    - **[MODIFY L33]** To: `auth.rate_limited=Too many login attempts ({1}). Please wait {0} seconds.`
    - `{0}` = retryAfterSeconds (integer), `{1}` = dimension (string: "IP", "USERNAME", "DEVICE")
  - Validation: `getMessage("auth.rate_limited", [60, "IP"], locale)` → "Too many login attempts (IP). Please wait 60 seconds."

- [x] **Task 3: Update `auth.rate_limited` message template — Vietnamese**
  - File: `src/main/resources/messages/auth-messages_vi.properties` | Action: [MODIFY]
  - FR: D14 — Update template with interpolation args
  - Details:
    - **[MODIFY L33]** Change: `auth.rate_limited=Quá nhiều lần đăng nhập`
    - **[MODIFY L33]** To: `auth.rate_limited=Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây.`
  - Validation: `getMessage("auth.rate_limited", [60, "IP"], Locale("vi"))` → "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây."

## Phase 3: Frontend Changes (admindashboard — separate workspace)

> ⚠️ These tasks target `admindashboard` project — outside auth-service workspace.
> Frontend tasks previously implemented in `wf_openspec_apply` (2026-08-11) should be VERIFIED.

- [x] **Task 4: Verify/Update I18nProvider — Accept-Language header sync**
  - File: `admindashboard/src/@i18n/I18nProvider.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
    - `useEffect` on language change → `setGlobalHeaders({ 'Accept-Language': languageId })`
    - `detectLanguage()` on mount → localStorage → navigator.languages → "en"
    - `localStorage.setItem('user_language', languageId)` on change
  - Validation: Change language → next API request has correct Accept-Language header

- [x] **Task 5: Verify/Update i18n.ts — language detection + vi resources**
  - File: `admindashboard/src/@i18n/i18n.ts` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - `supportedLngs: ['en', 'vi']`
    - `fallbackLng: 'en'`
    - `vi` translation namespace (basic structure)
    - `detectInitialLanguage()` utility: localStorage → navigator.languages → "en"
  - Validation: App init detects browser locale correctly

- [x] **Task 6: Verify/Update api.ts — inject metadata headers**
  - File: `admindashboard/src/utils/api.ts` | Action: [VERIFY/MODIFY]
  - FR: FR-008 — X-App-Version, X-Client-Platform headers
  - Details:
    - Default headers in ky client:
      ```
      X-App-Version: import.meta.env.VITE_APP_VERSION || '0.0.0'
      X-Client-Platform: web
      ```
    - `Accept-Language` → managed by I18nProvider via `setGlobalHeaders()`
  - Validation: Every API request includes all 3 headers

- [x] **Task 7: Verify/Update JwtSignInForm.tsx — server message display**
  - File: `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-009 — Client hiển thị message trực tiếp
  - Details:
    - Replace switch/case on errorCode → `setErrorMessage(problem.detail || 'An error occurred')`
    - Keep errorCode for UX-only: `AUTH_007` → `setCaptchaRequired(true)`, `AUTH_020` → `setRateLimitRetry(problem.retryAfterSeconds)`
    - Remove ~12 hardcoded error message strings
  - Validation: Error messages display in user's selected language

- [x] **Task 8: Add VN flag asset**
  - File: `admindashboard/public/assets/images/flags/VN.svg` | Action: [NEW]
  - FR: D6 — Frontend languages update
  - Details: Vietnamese flag SVG (red background, yellow star)
  - Validation: LanguageSwitcher renders VN flag without broken image

- [x] **Task 9: Cleanup LanguageSwitcher — remove dead links**
  - File: `admindashboard/src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
  - FR: D6 — Frontend languages update
  - Details:
    - Remove "Learn More" MenuItem block pointing to non-existent `/documentation/configuration/multi-language`
    - Component already reads `languages` from `useI18n()` — no language list change needed
  - Validation: LanguageSwitcher shows only en/vi, no dead links

## Phase 4: Verification

- [x] **Task 10: Backend integration verification**
  - Verify LoginRateLimitFilter i18n:
    - POST /api/auth/login with `Accept-Language: vi` + trigger rate limit → Vietnamese ProblemDetail
    - POST /api/auth/login with `Accept-Language: en` + trigger rate limit → English ProblemDetail
    - POST /api/auth/login without Accept-Language + trigger rate limit → English ProblemDetail (fallback)
    - Response has `Content-Language` header matching resolved locale
    - Response has `Retry-After` header (existing behavior preserved)
  - Verify no regression:
    - Normal login flow (no rate limit) unchanged
    - AuthControllerAdvice error handling unchanged
    - ContentLanguageFilter still works for non-rate-limited responses
    - All existing message bundle keys resolve correctly

---

## Previously Verified Tasks (DONE — from 2026-08-11 run)

> These tasks were completed and verified in previous `wf_openspec_apply` runs.
> Listed here for traceability — DO NOT re-implement.

| Task | Description | Status |
|------|-------------|--------|
| I18nAutoConfiguration (base-core) | localeResolver() + messageSource() beans | ✅ VERIFIED EXISTING |
| base-core message bundles | messages.properties + messages_vi.properties (6 keys × 2) | ✅ VERIFIED EXISTING |
| BaseControllerAdvice MessageSource | nullable injection + resolveMessage() | ✅ VERIFIED EXISTING |
| ApiResponse.success() i18n | message param documented for i18n | ✅ VERIFIED |
| i18n_messages DB migration | V5__create_i18n_messages.sql | ✅ VERIFIED EXISTING |
| I18nMessageEntity + Repository | JPA entity + findByCodeAndLocaleAndIsActiveTrue | ✅ VERIFIED EXISTING |
| DatabaseMessageSource | Caffeine cache, resolveCode(), parent chain | ✅ VERIFIED EXISTING |
| I18nConfig locale resolver + message source | AcceptHeaderLocaleResolver [en, vi] + CompositeMessageSource | ✅ VERIFIED EXISTING |
| Message bundle keys (55 keys × 2 locales) | 44 error + 11 success, en + vi | ✅ VERIFIED EXISTING |
| ContentLanguageFilter | OncePerRequestFilter, Content-Language header | ✅ VERIFIED EXISTING |
| ClientMetadataFilter | OncePerRequestFilter, MDC logging | ✅ VERIFIED EXISTING |
| AuthControllerAdvice i18n | resolveMessage(), extractMessageArgs(), setContentLanguageHeader() | ✅ VERIFIED EXISTING |
| CqrsAuthController i18n | 5 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| SessionController i18n | 2 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| AccountLifecycleController i18n | 3 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |

---

## FR Traceability Matrix

| FR-ID | Tasks | Status |
|-------|-------|--------|
| FR-001 | Previously verified (AuthControllerAdvice) | ✅ Covered |
| FR-002 | Previously verified (I18nConfig) | ✅ Covered |
| FR-003 | Previously verified (I18nConfig, DatabaseMessageSource, bundles) | ✅ Covered |
| FR-004 | Previously verified (AuthControllerAdvice) | ✅ Covered |
| FR-004B | T1 (LoginRateLimitFilter) | 🔲 Pending |
| FR-005 | Previously verified (3 controllers) | ✅ Covered |
| FR-006 | Previously verified (ContentLanguageFilter) + T1 (rate limit path) | ✅/🔲 |
| FR-007 | T4, T5, T6 | 🔲 Pending (frontend) |
| FR-008 | T6 | 🔲 Pending (frontend) |
| FR-009 | T7 | 🔲 Pending (frontend) |
| FR-010 | Previously verified (DatabaseMessageSource fallback) | ✅ Covered |
| FR-011 | Previously verified (ClientMetadataFilter) | ✅ Covered |
| FR-012 | Previously verified (I18nConfig whitelist) | ✅ Covered |
| FR-013 | Previously verified (AcceptHeaderLocaleResolver stateless) | ✅ Covered |
| D14 | T2, T3 (message template update) | 🔲 Pending (recommended) |
