## Why

[CHANGED] API response i18n infrastructure đã implement **100% backend**. Hệ thống hiện tại có đầy đủ: `I18nConfig` (AcceptHeaderLocaleResolver, CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached, 5-min TTL), `AuthControllerAdvice` (MessageSource i18n cho ProblemDetail), `ContentLanguageFilter` (100% Content-Language header coverage), `ClientMetadataFilter` (MDC logging), `LoginRateLimitFilter` (i18n-enabled with own `resolveLocale()`), message bundles (55+ keys × 2 locales), và **18/18 action endpoints** across 9 controllers dùng `messageSource.getMessage()` — tất cả ĐÃ IMPLEMENT.

[CHANGED] vs archive (2026-08-25): Tất cả 7 action endpoints còn lại (MfaController 2, SsoController 2, AdminSessionController 1, RateLimitAdminController 1, TokenController 1) ĐÃ IMPLEMENT. Backend hoàn chỉnh.

**Remaining (~0.5 developer-day)**:
- Frontend: inject `Accept-Language` + metadata headers vào ky client (`api.ts`)
- Frontend: sync `i18n.language` (react-i18next) → `Accept-Language` header (`I18nProvider.tsx`)
- Frontend: cleanup hardcoded error messages trong forms → show server message directly
- Backend: integration tests to verify i18n behavior end-to-end

## Changes

- **Backend integration tests**: Add `@WebMvcTest`/`MockMvc` tests verifying:
  - Error response returns localized `ProblemDetail.detail` based on `Accept-Language`
  - Success response returns localized `message` field based on `Accept-Language`
  - Missing `Accept-Language` → fallback English
  - Unsupported locale (e.g., `ja`) → fallback English
  - `Content-Language` header present on all responses
- **Frontend header injection**: `api.ts` → inject `Accept-Language`, `X-App-Version` (from `import.meta.env.VITE_APP_VERSION`), `X-Client-Platform: web` vào global headers via `setGlobalHeaders()`
- **Frontend i18n sync**: `I18nProvider.tsx` → sync language change event with `Accept-Language` header update
- **Frontend cleanup**: Remove hardcoded error messages in `JwtSignInForm.tsx`, replace with `setErrorMessage(problem.detail || 'An error occurred')`. Keep errorCode usage for UX-only logic (CAPTCHA trigger, rate limit countdown)

## Capabilities

### Verified Capabilities (already implemented — no changes)
- `server-i18n-error`: All error responses render localized `ProblemDetail.detail` via `AuthControllerAdvice` + `MessageSource`
- `server-i18n-success`: All 18 action endpoints return localized `message` field via per-controller `MessageSource` injection
- `locale-resolution`: `AcceptHeaderLocaleResolver` with whitelist `[en, vi]`, fallback `en`
- `content-language-header`: 100% coverage via `ContentLanguageFilter` + defense-in-depth in `AuthControllerAdvice` + `LoginRateLimitFilter`
- `db-message-source`: `DatabaseMessageSource` with Caffeine cache (5-min TTL, maxSize=500) → file bundle fallback
- `client-metadata-logging`: `ClientMetadataFilter` extracts `X-App-Version`, `X-Client-Platform` → MDC

### New Capabilities (this iteration)
- `frontend-locale-sync`: Frontend `Accept-Language` header synced with react-i18next language state via ky client `beforeRequest` hook
- `frontend-server-message-display`: Frontend displays `problem.detail` (error) and `response.message` (success) directly from server — no client-side message building
- `backend-i18n-tests`: Integration test suite verifying i18n behavior across representative endpoints

## Impact

### Backend (auth-service) — TESTS ONLY
- **NEW**: `I18nIntegrationTest.kt` — integration tests for i18n behavior (locale resolution, Content-Language header, success/error message i18n). Estimated: ~200 lines.
- **NO MODIFY**: All existing source files unchanged — 100% backend implementation already done.

### Frontend (admindashboard) — EXTERNAL REPO
- **MODIFY**: `api.ts` (inject global headers)
- **MODIFY**: `I18nProvider.tsx` (language sync + header injection)
- **MODIFY**: `i18n.ts` (supportedLngs, fallbackLng)
- **MODIFY**: `JwtSignInForm.tsx` (remove hardcoded error strings → show `problem.detail`)
- **MODIFY**: `LanguageSwitcher.tsx` (remove dead links)
- **NEW**: `VN.svg` (Vietnamese flag asset)

### Database
- No changes. `i18n_messages` table already exists. Entity + Repository already implemented.
