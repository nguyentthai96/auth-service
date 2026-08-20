# Delta Spec: api-response-i18n-standard

_Generated: 2026-08-22 | Classification: EXTEND_

## Summary

This change closes the **last backend i18n gap**: `LoginRateLimitFilter.writeRateLimitResponse()` now resolves error messages via `MessageSource` using the request's `Accept-Language` header, matching the pattern already established in `AuthControllerAdvice`.

## Changes vs Previous Behavior

### LoginRateLimitFilter (`LoginRateLimitFilter.kt`)

| Aspect | Before | After |
|--------|--------|-------|
| Constructor params | `loginRateLimitService`, `objectMapper` | `loginRateLimitService`, `objectMapper`, **`messageSource`** |
| ProblemDetail.detail | `ex.message` (hardcoded English) | `messageSource.getMessage("auth.rate_limited", args, ex.message, locale)` |
| Locale resolution | None (always English) | `resolveLocale(request)` — parses `Accept-Language` header via `Locale.LanguageRange` |
| Content-Language header | Not set | `response.setHeader("Content-Language", locale.toLanguageTag())` |
| `writeRateLimitResponse()` signature | `(response, ex)` | `(request, response, ex)` — `request` needed for Accept-Language parsing |

### Message Bundles

| Key | Before (EN) | After (EN) |
|-----|-------------|------------|
| `auth.rate_limited` | `Too many login attempts` | `Too many login attempts ({1}). Please wait {0} seconds.` |

| Key | Before (VI) | After (VI) |
|-----|-------------|------------|
| `auth.rate_limited` | `Quá nhiều lần đăng nhập` | `Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây.` |

Placeholders: `{0}` = retryAfterSeconds (Long), `{1}` = dimension (String: "IP", "USERNAME", "DEVICE")

## Behavioral Impact

| Scenario | Before | After |
|----------|--------|-------|
| `Accept-Language: vi` + rate limited | `ProblemDetail.detail = "Too many login attempts from IP — retry after 60 seconds"` | `ProblemDetail.detail = "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây."` + `Content-Language: vi` |
| `Accept-Language: en` + rate limited | Same hardcoded English message | `"Too many login attempts (IP). Please wait 60 seconds."` + `Content-Language: en` |
| No `Accept-Language` + rate limited | Same hardcoded English message | Fallback English + `Content-Language: en` |
| Unsupported locale (e.g., `ja`) | Same hardcoded English message | Fallback English + `Content-Language: en` |
| MessageSource unavailable | N/A | Falls back to `ex.message` (defaultMessage param) |
| Non-rate-limited requests | Unaffected | Unaffected |

## Backward Compatibility

✅ **Fully backward compatible**:
- Existing clients not sending `Accept-Language` get English (same as before) + new `Content-Language` header (additive)
- ProblemDetail structure unchanged: same `type`, `title`, `status`, `errorCode`, `retryAfterSeconds`, `dimension` properties
- `Retry-After` header still set
- Only `detail` field content changes (now localized + interpolated)

## Files NOT Changed (Confirmed Unaffected)

- `GlobalExceptionHandler.kt` — unchanged, still handles non-filter exceptions
- `ContentLanguageFilter.kt` — unchanged, still sets Content-Language for DispatcherServlet responses
- `ClientMetadataFilter.kt` — unchanged
- All controllers — unchanged
- All other message bundle keys — unchanged
