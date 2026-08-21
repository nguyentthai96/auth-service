## Why

[CHANGED] API response i18n infrastructure đã implement ~97%. Hệ thống hiện tại có đầy đủ: `I18nConfig` (AcceptHeaderLocaleResolver, CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached, 5-min TTL), `AuthControllerAdvice` (MessageSource i18n cho ProblemDetail), `ContentLanguageFilter` (100% Content-Language header coverage), `ClientMetadataFilter` (MDC logging), `LoginRateLimitFilter` (i18n-enabled with own `resolveLocale()`), message bundles (55 keys × 2 locales = 110 entries), và controller i18n cho 10 action endpoints (CqrsAuthController 5, SessionController 2, AccountLifecycleController 3).

Tuy nhiên, còn **7 action endpoints** trong 5 controllers chưa có success i18n message:
- `MfaController.confirmTotp()` — trả `mapOf("success" to true)` không có message
- `MfaController.regenerateRecoveryCodes()` — hardcoded English `"Save these codes — they will not be shown again."` (Issue #1 từ pre_openspec)
- `TokenController.revokeAllSessions()` — trả `RevokeSessionsResponse` DTO không có message
- `SsoController.linkIdentity()` — trả `mapOf("linked" to true)` không có message
- `SsoController.unlinkIdentity()` — trả `mapOf("linked" to false)` không có message
- `AdminSessionController.forceRevokeUserSessions()` — hardcoded English `"All sessions revoked for user $userId"`
- `RateLimitAdminController.adminUnlock()` — trả `UnlockResponse` DTO không có message

Frontend chưa inject `Accept-Language` / metadata headers (`X-App-Version`, `X-Client-Platform`) vào API requests, và forms vẫn hardcode error message strings thay vì hiển thị `problem.detail` từ server.

## Changes

- **Extend success i18n cho 5 controllers (7 endpoints)**: Inject `MessageSource` vào constructor của MfaController, TokenController, SsoController, AdminSessionController, RateLimitAdminController. Call `messageSource.getMessage(key, args, defaultMsg, locale)` inline — proven pattern từ CqrsAuthController/SessionController/AccountLifecycleController.
- **Add 7 new message keys to bundles**: `auth.totp_confirmed`, `auth.recovery_codes_warning`, `auth.all_user_sessions_revoked`, `auth.sso_identity_linked`, `auth.sso_identity_unlinked`, `auth.sessions_revoked_for_user`, `auth.rate_limit_unlocked` — both en + vi.
- **Frontend header injection**: `api.ts` → inject `Accept-Language`, `X-App-Version` (from `import.meta.env.VITE_APP_VERSION`), `X-Client-Platform: web` vào global headers via `setGlobalHeaders()`
- **Frontend i18n sync**: `I18nProvider.tsx` → sync language change event with `Accept-Language` header update
- **Frontend cleanup**: Remove hardcoded error messages in `JwtSignInForm.tsx` (~12 strings), replace with `setErrorMessage(problem.detail || 'An error occurred')`. Keep errorCode usage for UX-only logic (CAPTCHA trigger, rate limit countdown).

## Capabilities

### New Capabilities
- `extended-success-i18n`: All action endpoints (17 total: 10 existing + 7 new) return i18n `message` field in success responses via per-controller MessageSource injection pattern
- `frontend-locale-sync`: Frontend Accept-Language header synced with react-i18next language state via ky client beforeRequest hook

### Modified Capabilities
- `mfa-totp-confirm`: MfaController.confirmTotp() response now includes i18n `message` field alongside `success` boolean
- `mfa-recovery-codes`: MfaController.regenerateRecoveryCodes() `warning` field now i18n-resolved (was hardcoded English)
- `token-revoke-all`: TokenController.revokeAllSessions() response now includes i18n `message` field alongside `revokedCount` and `userId`
- `sso-link-unlink`: SsoController.linkIdentity()/unlinkIdentity() responses now include i18n `message` field alongside `linked` boolean
- `admin-session-revoke`: AdminSessionController.forceRevokeUserSessions() `message` field now i18n-resolved (was hardcoded English with string interpolation)
- `admin-rate-limit-unlock`: RateLimitAdminController.adminUnlock() response now includes i18n `message` field alongside `unlocked` and `userId`
- `frontend-error-display`: Forms show server-rendered `problem.detail` directly instead of building error messages client-side from error codes

## Impact

### Backend (auth-service)
- **MODIFY**: `MfaController.kt` — inject `MessageSource`, add i18n to `confirmTotp()` + `regenerateRecoveryCodes()`. 1 file, ~10 lines changed.
- **MODIFY**: `TokenController.kt` — inject `MessageSource`, add i18n to `revokeAllSessions()`. 1 file, ~8 lines changed.
- **MODIFY**: `SsoController.kt` — inject `MessageSource`, add i18n to `linkIdentity()` + `unlinkIdentity()`. 1 file, ~10 lines changed.
- **MODIFY**: `AdminSessionController.kt` — inject `MessageSource`, replace hardcoded message in `forceRevokeUserSessions()`. 1 file, ~6 lines changed.
- **MODIFY**: `RateLimitAdminController.kt` — inject `MessageSource`, add i18n to `adminUnlock()`. 1 file, ~8 lines changed.
- **MODIFY**: `auth-messages.properties` + `auth-messages_vi.properties` — add 7 new success message keys. 2 files, 7 lines each.

### Backend (already implemented — no changes needed)
- `GlobalExceptionHandler.kt` — ✅ AuthControllerAdvice with MessageSource i18n
- `I18nConfig.kt` — ✅ AcceptHeaderLocaleResolver [en, vi] + CompositeMessageSource chain
- `DatabaseMessageSource.kt` — ✅ Caffeine cache, resolveCode(), parent chain delegation
- `ContentLanguageFilter.kt` — ✅ 100% Content-Language coverage on /api/**
- `ClientMetadataFilter.kt` — ✅ MDC logging with cleanup
- `LoginRateLimitFilter.kt` — ✅ i18n-enabled with own resolveLocale()
- `CqrsAuthController.kt` — ✅ 5 endpoints with messageSource.getMessage()
- `SessionController.kt` — ✅ 2 endpoints with messageSource.getMessage()
- `AccountLifecycleController.kt` — ✅ 3 endpoints with messageSource.getMessage()
- `auth-messages*.properties` — ✅ 55 keys × 2 locales (existing keys unchanged)

### Frontend (admindashboard)
- **MODIFY**: `api.ts` (inject global headers), `I18nProvider.tsx` (language sync + header injection), `i18n.ts` (vi resources + supportedLngs)
- **MODIFY**: `JwtSignInForm.tsx` (remove ~12 hardcoded error strings — show problem.detail)
- **MODIFY**: `LanguageSwitcher.tsx` (remove dead links)
- **NEW**: `public/assets/images/flags/VN.svg` (Vietnamese flag asset)

### Database
- No changes. `i18n_messages` table already exists. Entity + Repository already implemented.
