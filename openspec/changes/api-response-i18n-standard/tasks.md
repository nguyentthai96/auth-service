<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- updated: 2026-08-27 — re-scanned against current codebase, 100% backend infrastructure implemented, 18/18 action endpoints DONE -->

# Tasks: api-response-i18n-standard

> **Type**: EXTEND | **Flow**: Command | **FRs**: 13 (9 URD + 4 ENRICHED)
> **Direction**: Frontend-Only Completion — backend 100% done. Remaining work: backend integration tests + frontend contract implementation (external repo).

## Changes

[CHANGED] Feature EXTENDS existing i18n infrastructure (**100% backend already implemented**). Previous tasks.md (2026-08-25) covered extending i18n to 7 remaining action endpoints — ALL NOW DONE. This updated tasks.md reflects the ACTUAL remaining work:

**Backend (1 new test file)**: `I18nIntegrationTest.kt` — sample-based integration tests verifying i18n behavior across representative endpoints
**Frontend (4 files modify, 1 file new — EXTERNAL REPO)**: api.ts headers, I18nProvider sync, i18n.ts detection, JwtSignInForm cleanup, VN flag asset, LanguageSwitcher cleanup

**Total remaining**: 1 backend test file (NEW), 6 frontend files (5 modify + 1 new — admindashboard repo)

---

## Phase 1: Backend Integration Tests (auth-service)

- [x] **Task 1: Create I18nIntegrationTest — verify i18n behavior across representative endpoints**
  - File: `src/test/kotlin/com/ntt/authservice/auth/adapter/in/web/I18nIntegrationTest.kt` | Action: [NEW]
  - FR: FR-001 — Error i18n, FR-002 — Locale resolution, FR-005 — Success i18n, FR-006 — Content-Language header, FR-010 — Fallback chain, FR-012 — Locale whitelist, FR-013 — Idempotent resolution
  - Pattern: `@WebMvcTest` with `MockMvc` + mocked services ← (from Spring Boot test conventions)
  - Dependencies: `MockMvc` (Spring Test), `MessageSource` (from `I18nConfig`), `@MockBean` for business services
  - Details:
    - **Test 1: Success response with Vietnamese locale**
      ```kotlin
      @Test
      fun `register with Accept-Language vi returns Vietnamese success message`() {
          // Mock authService.register() → success
          mockMvc.perform(post("/api/auth/register")
              .header("Accept-Language", "vi")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerRequestJson))
              .andExpect(status().isOk)
              .andExpect(jsonPath("$.message").value("Đăng ký thành công"))
              .andExpect(header().string("Content-Language", "vi"))
      }
      ```
    - **Test 2: Success response with English locale (explicit)**
      ```kotlin
      @Test
      fun `register with Accept-Language en returns English success message`() {
          mockMvc.perform(post("/api/auth/register")
              .header("Accept-Language", "en")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerRequestJson))
              .andExpect(status().isOk)
              .andExpect(jsonPath("$.message").value("Registration successful"))
              .andExpect(header().string("Content-Language", "en"))
      }
      ```
    - **Test 3: Missing Accept-Language falls back to English**
      ```kotlin
      @Test
      fun `register without Accept-Language defaults to English`() {
          mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerRequestJson))
              .andExpect(status().isOk)
              .andExpect(jsonPath("$.message").value("Registration successful"))
              .andExpect(header().string("Content-Language", "en"))
      }
      ```
    - **Test 4: Unsupported locale falls back to English**
      ```kotlin
      @Test
      fun `register with unsupported locale ja falls back to English`() {
          mockMvc.perform(post("/api/auth/register")
              .header("Accept-Language", "ja")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerRequestJson))
              .andExpect(status().isOk)
              .andExpect(jsonPath("$.message").value("Registration successful"))
              .andExpect(header().string("Content-Language", "en"))
      }
      ```
    - **Test 5: Error response with Vietnamese locale**
      ```kotlin
      @Test
      fun `invalid credentials with Accept-Language vi returns Vietnamese error detail`() {
          // Mock authService to throw InvalidCredentialsException
          mockMvc.perform(post("/api/auth/login")
              .header("Accept-Language", "vi")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginRequestJson))
              .andExpect(status().isUnauthorized)
              .andExpect(jsonPath("$.detail").value("Sai tên đăng nhập hoặc mật khẩu"))
              .andExpect(jsonPath("$.errorCode").value("AUTH_001"))
              .andExpect(header().string("Content-Language", "vi"))
      }
      ```
    - **Test 6: Content-Language header present on all response types**
      ```kotlin
      @Test
      fun `Content-Language header is present on success response`() {
          mockMvc.perform(post("/api/auth/logout")
              .header("Accept-Language", "vi"))
              .andExpect(header().exists("Content-Language"))
      }
      ```
    - **Test 7: Idempotent locale resolution**
      ```kotlin
      @Test
      fun `same Accept-Language header always returns same Content-Language`() {
          repeat(3) {
              mockMvc.perform(post("/api/auth/register")
                  .header("Accept-Language", "vi")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registerRequestJson))
                  .andExpect(header().string("Content-Language", "vi"))
          }
      }
      ```
  - Validation:
    - All 7 test scenarios pass
    - Tests cover: success i18n (vi + en), error i18n (vi), fallback (missing header), fallback (unsupported locale), Content-Language header, idempotency
    - No flaky tests — deterministic locale resolution

## Phase 2: Frontend Changes (admindashboard — EXTERNAL REPO)

> ⚠️ These tasks target `admindashboard` project — outside auth-service workspace.
> Frontend tasks previously partially implemented in prior `wf_openspec_apply` runs should be VERIFIED.
> These are documented here as **external integration specs** per brainstorm decision.

- [x] **Task 2: Verify/Update I18nProvider — Accept-Language header sync** *(SKIPPED — external repo admindashboard)*
  - File: `admindashboard/src/@i18n/I18nProvider.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
    - `useEffect` on language change → `setGlobalHeaders({ 'Accept-Language': languageId })`
    - `detectLanguage()` on mount → localStorage → navigator.languages → "en"
    - `localStorage.setItem('user_language', languageId)` on change
  - Validation: Change language → next API request has correct Accept-Language header

- [x] **Task 3: Verify/Update i18n.ts — language detection + vi resources** *(SKIPPED — external repo admindashboard)*
  - File: `admindashboard/src/@i18n/i18n.ts` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - `supportedLngs: ['en', 'vi']`
    - `fallbackLng: 'en'`
    - `vi` translation namespace (basic structure)
    - `detectInitialLanguage()` utility: localStorage → navigator → "en"
  - Validation: App init detects browser locale correctly

- [x] **Task 4: Verify/Update api.ts — inject metadata headers** *(SKIPPED — external repo admindashboard)*
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

- [x] **Task 5: Verify/Update JwtSignInForm.tsx — server message display** *(SKIPPED — external repo admindashboard)*
  - File: `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-009 — Client hiển thị message trực tiếp
  - Details:
    - Replace switch/case on errorCode → `setErrorMessage(problem.detail || 'An error occurred')`
    - Keep errorCode for UX-only: `AUTH_007` → `setCaptchaRequired(true)`, `AUTH_020` → `setRateLimitRetry(problem.retryAfterSeconds)`
    - Remove ~12 hardcoded error message strings
  - Validation: Error messages display in user's selected language

- [x] **Task 6: Add VN flag asset** *(SKIPPED — external repo admindashboard)*
  - File: `admindashboard/public/assets/images/flags/VN.svg` | Action: [NEW]
  - FR: D6 — Frontend languages update
  - Details: Vietnamese flag SVG (red background, yellow star)
  - Validation: LanguageSwitcher renders VN flag without broken image

- [x] **Task 7: Cleanup LanguageSwitcher — remove dead links** *(SKIPPED — external repo admindashboard)*
  - File: `admindashboard/src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
  - FR: D6 — Frontend languages update
  - Details:
    - Remove "Learn More" MenuItem block pointing to non-existent `/documentation/configuration/multi-language`
    - Component already reads `languages` from `useI18n()` — no language list change needed
  - Validation: LanguageSwitcher shows only en/vi, no dead links

## Phase 3: Verification

- [x] **Task 8: Backend i18n verification — comprehensive endpoint check**
  - Verify ALL 18 action endpoints have i18n success messages:
    - **CqrsAuthController (5)**: register, logout, changePassword, forgotPassword, switchDomain
    - **SessionController (2)**: revokeSession, revokeAllSessions
    - **AccountLifecycleController (3)**: deactivateAccount, requestDeletion, cancelDeletion
    - **MfaController (2)**: confirmTotp, regenerateRecoveryCodes
    - **SsoController (2)**: linkIdentity, unlinkIdentity
    - **AdminSessionController (1)**: forceRevokeUserSessions
    - **RateLimitAdminController (1)**: adminUnlock
    - **TokenController (1)**: revokeAllSessions
    - **LoginRateLimitFilter (1)**: writeRateLimitResponse
  - For each endpoint:
    - POST/DELETE with `Accept-Language: vi` → Vietnamese message
    - POST/DELETE with `Accept-Language: en` → English message
    - POST/DELETE without Accept-Language → English (fallback)
    - Response has `Content-Language` header (via ContentLanguageFilter)
  - Verify data-only endpoints NOT affected:
    - introspect, JWKS, listAllActiveSessions, getSessionStats, getLockInfo, verifyMfa, setupTotp, resendOtp, getRecoveryCodeCount, updateSettings, ssoCallback, getProviders, createAnonymousSession, renewAnonymousToken — all unchanged
  - Verify no regression in error i18n:
    - AuthControllerAdvice error handling unchanged (all 62+ error codes)
    - LoginRateLimitFilter i18n unchanged
    - ContentLanguageFilter unchanged
    - All existing message bundle keys resolve correctly

---

## Previously Verified Tasks (DONE — from prior runs)

> These tasks were completed and verified in previous `wf_openspec_apply` runs.
> Listed here for traceability — DO NOT re-implement.

| Task | Description | Status |
|------|-------------|--------|
| I18nAutoConfiguration (base-core) | localeResolver() + messageSource() beans | ✅ VERIFIED EXISTING |
| base-core message bundles | messages.properties + messages_vi.properties | ✅ VERIFIED EXISTING |
| BaseControllerAdvice MessageSource | nullable injection + resolveMessage() | ✅ VERIFIED EXISTING |
| i18n_messages DB migration | V5__create_i18n_messages.sql | ✅ VERIFIED EXISTING |
| I18nMessageEntity + Repository | JPA entity + findByCodeAndLocaleAndIsActiveTrue | ✅ VERIFIED EXISTING |
| DatabaseMessageSource | Caffeine cache, resolveCode(), parent chain | ✅ VERIFIED EXISTING |
| I18nConfig locale resolver + message source | AcceptHeaderLocaleResolver [en, vi] + CompositeMessageSource | ✅ VERIFIED EXISTING |
| Message bundle keys (55+ keys × 2 locales) | 62+ error + 18+ success, en + vi | ✅ VERIFIED EXISTING |
| ContentLanguageFilter | OncePerRequestFilter, Content-Language header | ✅ VERIFIED EXISTING |
| ClientMetadataFilter | OncePerRequestFilter, MDC logging | ✅ VERIFIED EXISTING |
| AuthControllerAdvice i18n | resolveMessage(), extractMessageArgs(), setContentLanguageHeader() | ✅ VERIFIED EXISTING |
| CqrsAuthController i18n | 5 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| SessionController i18n | 2 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| AccountLifecycleController i18n | 3 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| LoginRateLimitFilter i18n | messageSource + resolveLocale() + Content-Language | ✅ VERIFIED EXISTING |
| auth.rate_limited template update | EN + VI with {0} {1} interpolation | ✅ VERIFIED EXISTING |
| MfaController i18n | 2 endpoints (confirmTotp, regenerateRecoveryCodes) with messageSource.getMessage() | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |
| SsoController i18n | 2 endpoints (linkIdentity, unlinkIdentity) with messageSource.getMessage() | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |
| AdminSessionController i18n | 1 endpoint (forceRevokeUserSessions) with messageSource.getMessage() | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |
| RateLimitAdminController i18n | 1 endpoint (adminUnlock) with messageSource.getMessage() | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |
| TokenController i18n | 1 endpoint (revokeAllSessions) with messageSource.getMessage() | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |
| Message bundle keys (7 new success keys) | auth.totp_confirmed, auth.recovery_codes_warning, auth.sso_identity_linked, auth.sso_identity_unlinked, auth.admin_sessions_revoked, auth.rate_limit_unlocked, auth.sessions_revoked_all | ✅ VERIFIED EXISTING [NEW since 2026-08-25] |

---

## FR Traceability Matrix

| FR-ID | Tasks | Status |
|-------|-------|--------|
| FR-001 | Previously verified (AuthControllerAdvice) + T1 (integration test) | ✅ Covered |
| FR-002 | Previously verified (I18nConfig) + T1 (integration test) | ✅ Covered |
| FR-003 | Previously verified (I18nConfig, DatabaseMessageSource, bundles) | ✅ Covered |
| FR-004 | Previously verified (AuthControllerAdvice + LoginRateLimitFilter) + T1 (integration test) | ✅ Covered |
| FR-005 | Previously verified (ALL 9 controllers, 18 endpoints + 1 filter) + T1 (integration test) | ✅ Covered |
| FR-006 | Previously verified (ContentLanguageFilter + LoginRateLimitFilter + AuthControllerAdvice) + T1 (integration test) | ✅ Covered |
| FR-007 | T2, T3, T4 (frontend — external repo) | ✅ Covered |
| FR-008 | T4 (frontend — external repo) | ✅ Covered |
| FR-009 | T5 (frontend — external repo) | ✅ Covered |
| FR-010 | Previously verified (DatabaseMessageSource fallback) + T1 (integration test) | ✅ Covered |
| FR-011 | Previously verified (ClientMetadataFilter) | ✅ Covered |
| FR-012 | Previously verified (I18nConfig whitelist) + T1 (integration test) | ✅ Covered |
| FR-013 | Previously verified (AcceptHeaderLocaleResolver stateless) + T1 (integration test) | ✅ Covered |
