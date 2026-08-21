<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- updated: 2026-08-25 — re-scanned against current codebase, ~97% infrastructure already implemented, LoginRateLimitFilter RESOLVED -->

# Tasks: api-response-i18n-standard

> **Type**: EXTEND | **Flow**: Command | **FRs**: 13 (9 URD + 4 ENRICHED)
> **Direction**: Per-Controller MessageSource Injection — extend proven pattern from CqrsAuthController/SessionController/AccountLifecycleController to 5 remaining controllers (7 action endpoints)

## Changes

[CHANGED] Feature EXTENDS existing i18n infrastructure (**~97% already implemented**). Previous tasks.md (2026-08-22) covered LoginRateLimitFilter i18n fix — NOW RESOLVED. This updated tasks.md reflects the ACTUAL remaining work:

**Backend (5 controller files modify)**: MfaController, TokenController, SsoController, AdminSessionController, RateLimitAdminController — inject MessageSource, add i18n messages to action endpoints
**Message bundles (2 files modify)**: Add 7 new success message keys to auth-messages.properties + auth-messages_vi.properties
**Frontend (4 files modify, 1 file new)**: api.ts headers, I18nProvider sync, i18n.ts detection, JwtSignInForm cleanup, VN flag asset, LanguageSwitcher cleanup

**Total remaining**: 7 backend files (5 controllers + 2 bundles), 5 frontend files (4 modify + 1 new)

---

## Phase 1: Message Bundle — Add New Keys (PREREQUISITE)

- [x] **Task 1: Add 7 new success message keys — English**
  - File: `src/main/resources/messages/auth-messages.properties` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n for remaining action endpoints
  - Details:
    - **[APPEND after L48]** Add after existing success messages section:
    ```properties
    # Extended success messages (api-response-i18n-standard v3)
    auth.totp_confirmed=TOTP setup confirmed successfully
    auth.recovery_codes_warning=Save these codes — they will not be shown again.
    auth.all_user_sessions_revoked=All sessions revoked
    auth.sso_identity_linked=SSO identity linked successfully
    auth.sso_identity_unlinked=SSO identity unlinked successfully
    auth.sessions_revoked_for_user=All sessions revoked for user {0}
    auth.rate_limit_unlocked=Rate limit locks cleared successfully
    ```
  - Validation: `getMessage("auth.totp_confirmed", null, Locale.ENGLISH)` → "TOTP setup confirmed successfully"

- [x] **Task 2: Add 7 new success message keys — Vietnamese**
  - File: `src/main/resources/messages/auth-messages_vi.properties` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n for remaining action endpoints
  - Details:
    - **[APPEND after L48]** Add after existing success messages section:
    ```properties
    # Extended success messages (api-response-i18n-standard v3)
    auth.totp_confirmed=Xác nhận cài đặt TOTP thành công
    auth.recovery_codes_warning=Lưu lại các mã này — chúng sẽ không được hiển thị lại.
    auth.all_user_sessions_revoked=Đã thu hồi tất cả phiên
    auth.sso_identity_linked=Liên kết danh tính SSO thành công
    auth.sso_identity_unlinked=Hủy liên kết danh tính SSO thành công
    auth.sessions_revoked_for_user=Đã thu hồi tất cả phiên cho người dùng {0}
    auth.rate_limit_unlocked=Đã xóa khóa giới hạn tốc độ thành công
    ```
  - Validation: `getMessage("auth.totp_confirmed", null, Locale("vi"))` → "Xác nhận cài đặt TOTP thành công"

## Phase 2: Backend Controllers — Extend Success i18n (7 endpoints, 5 files)

- [x] **Task 3: Modify MfaController — inject MessageSource + i18n for confirmTotp() + regenerateRecoveryCodes()**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` | Action: [MODIFY]
  - Base: Standalone `@RestController` (no base class) ← (from base_class_map.md)
  - FR: FR-005 — Success response i18n for action endpoints
  - Pattern: Constructor injection + `messageSource.getMessage(key, args, defaultMsg, locale)` ← (from CqrsAuthController proven pattern)
  - Dependencies: `MessageSource` (Spring, from `I18nConfig` CompositeMessageSource bean), `LocaleContextHolder` (Spring)
  - Source: Pattern REUSE from `CqrsAuthController.kt:89` ← (from impact_analysis.md)
  - Details:
    - **[MODIFY L16-18]** Add `private val messageSource: MessageSource` to constructor params:
      ```kotlin
      class MfaController(
          private val mfaService: MfaService,
          private val authService: AuthService,
          private val messageSource: MessageSource  // ← NEW
      ) {
      ```
    - **[ADD import]** `import org.springframework.context.MessageSource` and `import org.springframework.context.i18n.LocaleContextHolder`
    - **[MODIFY L49-53]** `confirmTotp()` — add i18n message to response map:
      ```kotlin
      @PostMapping("/totp/confirm")
      fun confirmTotp(@Valid @RequestBody request: TotpConfirmRequest): ResponseEntity<Map<String, Any>> {
          val userId = getCurrentUserId()
          mfaService.confirmTotp(userId, request.code)
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.totp_confirmed", null, "TOTP setup confirmed successfully", locale)
          return ResponseEntity.ok(mapOf("success" to true, "message" to message))
      }
      ```
    - **[MODIFY L94-103]** `regenerateRecoveryCodes()` — replace hardcoded English warning:
      ```kotlin
      @PostMapping("/recovery-codes/regenerate")
      fun regenerateRecoveryCodes(): ResponseEntity<Map<String, Any>> {
          val userId = getCurrentUserId()
          val codes = mfaService.generateRecoveryCodes(userId)
          val locale = LocaleContextHolder.getLocale()
          val warning = messageSource.getMessage("auth.recovery_codes_warning", null, "Save these codes — they will not be shown again.", locale)
          return ResponseEntity.ok(mapOf(
              "codes" to codes,
              "count" to codes.size,
              "warning" to warning
          ))
      }
      ```
  - Validation:
    - `Accept-Language: vi` + confirmTotp → `{"success": true, "message": "Xác nhận cài đặt TOTP thành công"}`
    - `Accept-Language: en` + regenerateRecoveryCodes → `{"codes": [...], "count": 10, "warning": "Save these codes — they will not be shown again."}`
    - `Accept-Language: vi` + regenerateRecoveryCodes → `{"codes": [...], "count": 10, "warning": "Lưu lại các mã này — chúng sẽ không được hiển thị lại."}`
    - No `Accept-Language` → fallback English

- [x] **Task 4: Modify TokenController — inject MessageSource + i18n for revokeAllSessions()**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` | Action: [MODIFY]
  - Base: Standalone `@RestController` (no base class)
  - FR: FR-005 — Success response i18n for action endpoints
  - Pattern: Constructor injection + getMessage() + map-based response
  - Dependencies: `MessageSource`, `LocaleContextHolder`
  - Details:
    - **[MODIFY L17-21]** Add `private val messageSource: MessageSource` to constructor:
      ```kotlin
      class TokenController(
          private val jwtService: JwtService,
          private val authService: AuthService,
          private val tokenBlacklistRepository: TokenBlacklistRepository,
          private val messageSource: MessageSource  // ← NEW
      ) {
      ```
    - **[ADD imports]** `import org.springframework.context.MessageSource` and `import org.springframework.context.i18n.LocaleContextHolder`
    - **[MODIFY L56-60]** `revokeAllSessions()` — wrap DTO fields in map + add i18n message:
      ```kotlin
      @PostMapping("/api/auth/sessions/{userId}/revoke-all")
      fun revokeAllSessions(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
          val count = authService.revokeAllSessions(userId)
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.all_user_sessions_revoked", null, "All sessions revoked", locale)
          return ResponseEntity.ok(mapOf(
              "revokedCount" to count,
              "userId" to userId,
              "message" to message
          ))
      }
      ```
  - Validation:
    - `Accept-Language: vi` + revokeAllSessions → `{"revokedCount": 3, "userId": 42, "message": "Đã thu hồi tất cả phiên"}`
    - Response has same data fields as before + new `message` field

- [x] **Task 5: Modify SsoController — inject MessageSource + i18n for linkIdentity() + unlinkIdentity()**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt` | Action: [MODIFY]
  - Base: Standalone `@RestController` (no base class)
  - FR: FR-005 — Success response i18n for action endpoints
  - Pattern: Constructor injection + getMessage() + add "message" to existing map
  - Dependencies: `MessageSource`, `LocaleContextHolder`
  - Details:
    - **[MODIFY L16-19]** Add `private val messageSource: MessageSource` to constructor:
      ```kotlin
      class SsoController(
          private val ssoAdapter: SsoAdapter,
          private val authService: AuthService,
          private val messageSource: MessageSource  // ← NEW
      ) {
      ```
    - **[ADD imports]** `import org.springframework.context.MessageSource` and `import org.springframework.context.i18n.LocaleContextHolder`
    - **[MODIFY L43-46]** `linkIdentity()` — add i18n message to response map:
      ```kotlin
      @PostMapping("/link")
      fun linkIdentity(@Valid @RequestBody request: SsoLinkRequest): ResponseEntity<Map<String, Any>> {
          val userId = getCurrentUserId()
          ssoAdapter.linkIdentity(userId, request.code, request.provider, request.redirectUri)
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.sso_identity_linked", null, "SSO identity linked successfully", locale)
          return ResponseEntity.ok(mapOf("linked" to true, "message" to message))
      }
      ```
    - **[MODIFY L49-53]** `unlinkIdentity()` — add i18n message to response map:
      ```kotlin
      @DeleteMapping("/unlink/{provider}")
      fun unlinkIdentity(@PathVariable provider: String): ResponseEntity<Map<String, Any>> {
          val userId = getCurrentUserId()
          ssoAdapter.unlinkIdentity(userId, provider)
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.sso_identity_unlinked", null, "SSO identity unlinked successfully", locale)
          return ResponseEntity.ok(mapOf("linked" to false, "message" to message))
      }
      ```
  - Validation:
    - `Accept-Language: vi` + linkIdentity → `{"linked": true, "message": "Liên kết danh tính SSO thành công"}`
    - `Accept-Language: vi` + unlinkIdentity → `{"linked": false, "message": "Hủy liên kết danh tính SSO thành công"}`

- [x] **Task 6: Modify AdminSessionController — inject MessageSource + i18n for forceRevokeUserSessions()**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt` | Action: [MODIFY]
  - Base: Standalone `@RestController` (no base class)
  - FR: FR-005 — Success response i18n for action endpoints
  - Pattern: Constructor injection + getMessage() + replace hardcoded English "message" value
  - Dependencies: `MessageSource`, `LocaleContextHolder`
  - Details:
    - **[MODIFY L22-24]** Add `private val messageSource: MessageSource` to constructor:
      ```kotlin
      class AdminSessionController(
          private val loginSessionService: LoginSessionService,
          private val loginSessionRepository: LoginSessionRepository,
          private val messageSource: MessageSource  // ← NEW
      ) {
      ```
    - **[ADD imports]** `import org.springframework.context.MessageSource` and `import org.springframework.context.i18n.LocaleContextHolder`
    - **[MODIFY L80-86]** `forceRevokeUserSessions()` — replace hardcoded English message:
      ```kotlin
      @DeleteMapping("/user/{userId}")
      fun forceRevokeUserSessions(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
          val sessions = loginSessionService.getActiveSessions(userId)
          loginSessionService.revokeAllSessions(userId, "ADMIN_FORCE_REVOKE")
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.sessions_revoked_for_user", arrayOf(userId), "All sessions revoked for user $userId", locale)
          return ResponseEntity.ok(mapOf(
              "message" to message,
              "revokedCount" to sessions.size
          ))
      }
      ```
  - Validation:
    - `Accept-Language: vi` + forceRevokeUserSessions(42) → `{"message": "Đã thu hồi tất cả phiên cho người dùng 42", "revokedCount": 5}`
    - `Accept-Language: en` → `{"message": "All sessions revoked for user 42", "revokedCount": 5}`
    - Note: `{0}` interpolation with userId arg

- [x] **Task 7: Modify RateLimitAdminController — inject MessageSource + i18n for adminUnlock()**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` | Action: [MODIFY]
  - Base: Standalone `@RestController` (no base class)
  - FR: FR-005 — Success response i18n for action endpoints
  - Pattern: Constructor injection + getMessage() + map-based response (replacing typed DTO)
  - Dependencies: `MessageSource`, `LocaleContextHolder`
  - Details:
    - **[MODIFY L18-20]** Add `private val messageSource: MessageSource` to constructor:
      ```kotlin
      class RateLimitAdminController(
          private val rateLimitService: MfaRateLimitService,
          private val messageSource: MessageSource  // ← NEW
      ) {
      ```
    - **[ADD imports]** `import org.springframework.context.MessageSource` and `import org.springframework.context.i18n.LocaleContextHolder`
    - **[MODIFY L56-58]** `adminUnlock()` — change return type + add i18n message:
      ```kotlin
      @DeleteMapping("/locks/{userId}")
      fun adminUnlock(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
          rateLimitService.adminUnlock(userId)
          val locale = LocaleContextHolder.getLocale()
          val message = messageSource.getMessage("auth.rate_limit_unlocked", null, "Rate limit locks cleared successfully", locale)
          return ResponseEntity.ok(mapOf(
              "unlocked" to true,
              "userId" to userId,
              "message" to message
          ))
      }
      ```
  - Validation:
    - `Accept-Language: vi` + adminUnlock → `{"unlocked": true, "userId": 42, "message": "Đã xóa khóa giới hạn tốc độ thành công"}`
    - Same `unlocked` and `userId` fields as before + new `message` field

## Phase 3: Frontend Changes (admindashboard — separate workspace)

> ⚠️ These tasks target `admindashboard` project — outside auth-service workspace.
> Frontend tasks previously partially implemented in prior `wf_openspec_apply` runs should be VERIFIED.

- [x] **Task 8: Verify/Update I18nProvider — Accept-Language header sync**
  - File: `admindashboard/src/@i18n/I18nProvider.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
    - `useEffect` on language change → `setGlobalHeaders({ 'Accept-Language': languageId })`
    - `detectLanguage()` on mount → localStorage → navigator.languages → "en"
    - `localStorage.setItem('user_language', languageId)` on change
  - Validation: Change language → next API request has correct Accept-Language header

- [x] **Task 9: Verify/Update i18n.ts — language detection + vi resources**
  - File: `admindashboard/src/@i18n/i18n.ts` | Action: [VERIFY/MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - `supportedLngs: ['en', 'vi']`
    - `fallbackLng: 'en'`
    - `vi` translation namespace (basic structure)
    - `detectInitialLanguage()` utility: localStorage → navigator → "en"
  - Validation: App init detects browser locale correctly

- [x] **Task 10: Verify/Update api.ts — inject metadata headers**
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

- [x] **Task 11: Verify/Update JwtSignInForm.tsx — server message display**
  - File: `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY/MODIFY]
  - FR: FR-009 — Client hiển thị message trực tiếp
  - Details:
    - Replace switch/case on errorCode → `setErrorMessage(problem.detail || 'An error occurred')`
    - Keep errorCode for UX-only: `AUTH_007` → `setCaptchaRequired(true)`, `AUTH_020` → `setRateLimitRetry(problem.retryAfterSeconds)`
    - Remove ~12 hardcoded error message strings
  - Validation: Error messages display in user's selected language

- [x] **Task 12: Add VN flag asset**
  - File: `admindashboard/public/assets/images/flags/VN.svg` | Action: [NEW]
  - FR: D6 — Frontend languages update
  - Details: Vietnamese flag SVG (red background, yellow star)
  - Validation: LanguageSwitcher renders VN flag without broken image

- [x] **Task 13: Cleanup LanguageSwitcher — remove dead links**
  - File: `admindashboard/src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
  - FR: D6 — Frontend languages update
  - Details:
    - Remove "Learn More" MenuItem block pointing to non-existent `/documentation/configuration/multi-language`
    - Component already reads `languages` from `useI18n()` — no language list change needed
  - Validation: LanguageSwitcher shows only en/vi, no dead links

## Phase 4: Verification

- [x] **Task 14: Backend integration verification**
  - Verify ALL 17 action endpoints have i18n success messages:
    - **Existing (10 — verify no regression)**: register, logout, changePassword, forgotPassword, switchDomain, revokeSession, revokeAllSessions (SessionController), deactivateAccount, requestDeletion, cancelDeletion
    - **New (7 — verify functionality)**: confirmTotp, regenerateRecoveryCodes, revokeAllSessions (TokenController), linkIdentity, unlinkIdentity, forceRevokeUserSessions, adminUnlock
  - For each new endpoint:
    - POST/DELETE with `Accept-Language: vi` → Vietnamese message
    - POST/DELETE with `Accept-Language: en` → English message
    - POST/DELETE without Accept-Language → English (fallback)
    - Response has `Content-Language` header (via ContentLanguageFilter)
  - Verify data-only endpoints NOT affected:
    - introspect, JWKS, listAllActiveSessions, getSessionStats, getLockInfo, verifyMfa, setupTotp, resendOtp, getRecoveryCodeCount, updateSettings, ssoCallback, getProviders, createAnonymousSession, renewAnonymousToken — all unchanged
  - Verify no regression in error i18n:
    - AuthControllerAdvice error handling unchanged (all 44 error codes)
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
| Message bundle keys (55 keys × 2 locales) | 44 error + 11 success, en + vi | ✅ VERIFIED EXISTING |
| ContentLanguageFilter | OncePerRequestFilter, Content-Language header | ✅ VERIFIED EXISTING |
| ClientMetadataFilter | OncePerRequestFilter, MDC logging | ✅ VERIFIED EXISTING |
| AuthControllerAdvice i18n | resolveMessage(), extractMessageArgs(), setContentLanguageHeader() | ✅ VERIFIED EXISTING |
| CqrsAuthController i18n | 5 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| SessionController i18n | 2 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| AccountLifecycleController i18n | 3 endpoints with messageSource.getMessage() | ✅ VERIFIED EXISTING |
| LoginRateLimitFilter i18n | messageSource + resolveLocale() + Content-Language | ✅ VERIFIED EXISTING (resolved 2026-08-22) |
| auth.rate_limited template update | EN + VI with {0} {1} interpolation | ✅ VERIFIED EXISTING |

---

## FR Traceability Matrix

| FR-ID | Tasks | Status |
|-------|-------|--------|
| FR-001 | Previously verified (AuthControllerAdvice) | ✅ Covered |
| FR-002 | Previously verified (I18nConfig) | ✅ Covered |
| FR-003 | Previously verified (I18nConfig, DatabaseMessageSource, bundles) + T1, T2 (new keys) | ✅ Covered |
| FR-004 | Previously verified (AuthControllerAdvice + LoginRateLimitFilter) | ✅ Covered |
| FR-005 | Previously verified (3 controllers, 10 endpoints) + T3, T4, T5, T6, T7 (7 new endpoints) | ✅ Covered |
| FR-006 | Previously verified (ContentLanguageFilter + LoginRateLimitFilter + AuthControllerAdvice) | ✅ Covered |
| FR-007 | T8, T9, T10 | ✅ Verified (already implemented) |
| FR-008 | T10 | ✅ Verified (already implemented) |
| FR-009 | T11 | ✅ Verified (already implemented) |
| FR-010 | Previously verified (DatabaseMessageSource fallback) | ✅ Covered |
| FR-011 | Previously verified (ClientMetadataFilter) | ✅ Covered |
| FR-012 | Previously verified (I18nConfig whitelist) | ✅ Covered |
| FR-013 | Previously verified (AcceptHeaderLocaleResolver stateless) | ✅ Covered |
