## Why

[CHANGED] API response i18n infrastructure đã implement ~97%. Hệ thống hiện tại có đầy đủ: `I18nConfig` (AcceptHeaderLocaleResolver, CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached, 5-min TTL), `AuthControllerAdvice` (MessageSource i18n cho ProblemDetail), `ContentLanguageFilter` (100% Content-Language header coverage), `ClientMetadataFilter` (MDC logging), message bundles (55 keys × 2 locales = 110 entries), và controller i18n cho 10 action endpoints (CqrsAuthController 5, SessionController 2, AccountLifecycleController 3).

Tuy nhiên, `LoginRateLimitFilter.writeRateLimitResponse()` tạo ProblemDetail trực tiếp với `ex.message` (hardcoded English) — bypass hoàn toàn `AuthControllerAdvice` và `MessageSource` chain. Đây là i18n compliance gap duy nhất trên backend — khi user bị rate limit, họ luôn nhận English message bất kể `Accept-Language` header. Filter cũng không set `Content-Language` response header, vi phạm NFR-004 (100% coverage).

Frontend chưa inject `Accept-Language` / metadata headers (`X-App-Version`, `X-Client-Platform`) vào API requests, và forms vẫn hardcode ~12 error message strings thay vì hiển thị `problem.detail` từ server. Language switcher vẫn có dead links và thiếu VN flag asset.

## Changes

- **LoginRateLimitFilter i18n fix**: Inject `MessageSource` vào constructor, thêm private `resolveLocale(request)` helper parse `Accept-Language` header directly (filter runs pre-DispatcherServlet — không thể dùng `LocaleContextHolder`), resolve `auth.rate_limited` message with locale, set `Content-Language` response header. Sử dụng `Locale.LanguageRange.parse()` + `Locale.lookup()` với cùng whitelist `[en, vi]` như `I18nConfig`.
- **Message template enhancement** (recommended): Update `auth.rate_limited` trong cả en + vi bundles để include `{0}` (retryAfterSeconds) và `{1}` (dimension) interpolation placeholders — hiện template ignore args, `MessageFormat` silently drops extra args. Updated template: EN: `"Too many login attempts ({1}). Please wait {0} seconds."` / VI: `"Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây."`
- **Frontend header injection**: `api.ts` → inject `Accept-Language`, `X-App-Version` (from `import.meta.env.VITE_APP_VERSION`), `X-Client-Platform: web` vào global headers via `setGlobalHeaders()`
- **Frontend i18n sync**: `I18nProvider.tsx` → sync language change event with `Accept-Language` header update; `i18n.ts` → language detection cascade (localStorage `user_language` → `navigator.languages` → `en` fallback), `supportedLngs: ['en', 'vi']`
- **Frontend cleanup**: Remove hardcoded error messages in `JwtSignInForm.tsx` (~12 strings), replace switch/case on errorCode with `setErrorMessage(problem.detail || 'An error occurred')`. Keep errorCode usage for UX-only logic (CAPTCHA trigger, rate limit countdown).
- **Frontend assets**: Add `VN.svg` flag asset for language switcher, cleanup `LanguageSwitcher.tsx` dead links pointing to non-existent documentation route

## Capabilities

### New Capabilities
- `rate-limit-i18n`: LoginRateLimitFilter resolves ProblemDetail.detail via MessageSource with locale-aware Accept-Language parsing — ensures ALL error responses (including pre-auth filter responses) are i18n compliant
- `frontend-locale-detection`: Hybrid cascade locale detection (localStorage → navigator.languages → fallback) determines initial language without external service

### Modified Capabilities
- `rate-limit-response`: LoginRateLimitFilter ProblemDetail now includes i18n `detail` field (was hardcoded English) + `Content-Language` header (was missing). Interpolation args `[retryAfterSeconds, dimension]` used in template for informative user message.
- `frontend-api-headers`: api.ts injects `Accept-Language` (synced from i18n language state), `X-App-Version` (from build env), `X-Client-Platform: web` on every HTTP request via ky client beforeRequest hook
- `frontend-i18n-sync`: I18nProvider syncs language selection with Accept-Language header — language change triggers `setGlobalHeaders()` update + `localStorage.setItem('user_language', languageId)` persistence
- `frontend-error-display`: Forms show server-rendered `problem.detail` directly instead of building error messages client-side from error codes. Client retains errorCode for UX behavior only (CAPTCHA widget, countdown timer).
- `language-switcher`: Updated languages list `[en, vi]` (removed tr, ar boilerplate defaults), VN flag asset added, dead documentation links removed

## Impact

### Backend (auth-service)
- **MODIFY**: `LoginRateLimitFilter.kt` — inject `MessageSource`, add `resolveLocale()` private helper, modify `writeRateLimitResponse()` to resolve i18n message + set Content-Language header. 1 file, ~15 lines changed.
- **MODIFY** (recommended): `auth-messages.properties` L33, `auth-messages_vi.properties` L33 — update `auth.rate_limited` template with `{0}`, `{1}` placeholders. 2 files, 1 line each.

### Backend (already implemented — no changes needed)
- `GlobalExceptionHandler.kt` — ✅ AuthControllerAdvice with MessageSource i18n, extractMessageArgs(), resolveMessage()
- `I18nConfig.kt` — ✅ AcceptHeaderLocaleResolver [en, vi], CompositeMessageSource chain
- `DatabaseMessageSource.kt` — ✅ Caffeine cache, resolveCode(), parent chain delegation
- `ContentLanguageFilter.kt` — ✅ 100% Content-Language coverage on /api/**
- `ClientMetadataFilter.kt` — ✅ MDC logging with cleanup
- `CqrsAuthController.kt` — ✅ 5 endpoints with messageSource.getMessage()
- `SessionController.kt` — ✅ 2 endpoints with messageSource.getMessage()
- `AccountLifecycleController.kt` — ✅ 3 endpoints with messageSource.getMessage()
- `auth-messages*.properties` — ✅ 55 keys × 2 locales = 110 entries

### Frontend (admindashboard)
- **MODIFY**: `api.ts` (inject global headers), `I18nProvider.tsx` (language sync + detection + header injection), `i18n.ts` (vi resources + detection utility + supportedLngs)
- **MODIFY**: `JwtSignInForm.tsx` (remove ~12 hardcoded error strings — show problem.detail)
- **MODIFY**: `LanguageSwitcher.tsx` (remove dead "Learn More" link, remove unused flag references)
- **NEW**: `public/assets/images/flags/VN.svg` (Vietnamese flag asset)

### Database
- No changes. `i18n_messages` table already exists (V5 migration). Entity + Repository already implemented.
