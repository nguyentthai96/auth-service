# SRS: API Response I18n Standard

> [CHANGED] Updated 2026-08-27 — reflects current codebase state where **100% backend i18n infrastructure is implemented** (18/18 action endpoints). All issues from archives RESOLVED. Remaining: backend integration tests + frontend header injection + form cleanup (external repo).

## 1. Feature Overview

Chuẩn hóa API request/response format với i18n support: Server render message hoàn chỉnh (interpolated) theo `Accept-Language` header → client chỉ hiển thị. Dual response: `ApiResponse<T>` (2xx), `ProblemDetail` (4xx/5xx). Dual message source: file (static) + database (dynamic). Content-Language header trên mọi response via Servlet Filter (100% coverage).

**Current implementation status**: **100% backend complete**. Infrastructure (`I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice`, `ContentLanguageFilter`, `ClientMetadataFilter`, `LoginRateLimitFilter` i18n, message bundles 55+ keys en+vi) and ALL 18 action endpoints across 9 controllers use `messageSource.getMessage()`. Remaining: backend integration tests + frontend header injection + form cleanup.

## 2. Functional Requirements

### FR-001: Server render message đa ngôn ngữ cho error response [URD] — ✅ ĐÃ IMPLEMENT
- Server phải resolve message hoàn chỉnh (đã interpolate params) bằng ngôn ngữ client request
- Message key = `AuthErrorCode.msgCode` (e.g., `auth.rate_limited`)
- Args interpolation: `MessageSource.getMessage(msgCode, args, locale)`
- Ví dụ: key=`auth.session_limit`, args=[3] → EN: "Maximum active sessions (3) reached" / VI: "Đã đạt tối đa 3 phiên hoạt động"
- **Evidence**: `AuthControllerAdvice.resolveMessage()` — `GlobalExceptionHandler.kt:125-131`; `extractMessageArgs()` maps exception properties — `GlobalExceptionHandler.kt:107-117`
- **Error**: N/A — internal infrastructure

### FR-002: Locale resolution từ Accept-Language [URD] — ✅ ĐÃ IMPLEMENT
- Spring `AcceptHeaderLocaleResolver` resolve locale từ `Accept-Language` header
- Supported locales: `en` (default), `vi`
- Missing/unsupported header → fallback `en`
- Format: IETF BCP 47 (RFC 5646)
- **Evidence**: `I18nConfig.kt:39-44` — `AcceptHeaderLocaleResolver` with supportedLocales = [en, vi], defaultLocale = ENGLISH
- **Error**: Không phát sinh error — fallback mechanism

### FR-003: Message bundle infrastructure (DB + file) [URD] — ✅ ĐÃ IMPLEMENT
- **Static bundles**: `auth-messages.properties` (en, 77 lines, 55+ keys), `auth-messages_vi.properties` (vi, 77 lines, 55+ keys)
- **Dynamic bundles**: `DatabaseMessageSource` extends `AbstractMessageSource`, Caffeine cache (5-min TTL, maxSize=500)
- **Composite chain**: `I18nConfig.kt:49-63` → DatabaseMessageSource → ReloadableResourceBundleMessageSource
- **Evidence**: `I18nConfig.kt`, `DatabaseMessageSource.kt`, `I18nMessageEntity.kt`, `I18nMessageRepository.kt` — all EXISTING
- **Error**: Missing key → fallback chain: DB → file locale → file default → `ErrorCodeBase.description`

### FR-004: Error response dùng ProblemDetail (RFC 9457) với i18n detail [URD] — ✅ ĐÃ IMPLEMENT
- `AuthControllerAdvice.handleAuthException()` → `ProblemDetail.setDetail(resolvedMessage)`
- Response `Content-Type: application/problem+json`
- Covers ALL AuthException subtypes including E2EE (AUTH_030-039), Anonymous (AUTH_040-044), and rate limit filter path
- **Evidence**: `GlobalExceptionHandler.kt:48-99` — sets type, title, status, detail (i18n), errorCode, extra properties
- `LoginRateLimitFilter.kt:127-132` — resolves `auth.rate_limited` via messageSource with own `resolveLocale()` for pre-DispatcherServlet context
- **Error**: `AUTH_001`→`AUTH_062` (62+ codes) — all resolved via MessageSource

### FR-005: Success response với i18n message [URD] — ✅ ĐÃ IMPLEMENT [CHANGED]
- Action endpoints include i18n `message` field via `messageSource.getMessage(key, args, defaultMsg, locale)`
- Data-only endpoints (login, refresh, introspect, get sessions, CRUD) → no forced i18n message
- **[CHANGED] ALL 18 action endpoints IMPLEMENTED**:
  - `CqrsAuthController.kt` (5 endpoints: register, logout, change-password, forgot-password, switch-domain)
  - `SessionController.kt` (2: session_revoked, all_sessions_revoked)
  - `AccountLifecycleController.kt` (3: account_deactivated, deletion_requested, deletion_cancelled)
  - `MfaController.kt` (2: totp_confirmed, recovery_codes_warning) [CHANGED from archive — was pending]
  - `SsoController.kt` (2: sso_identity_linked, sso_identity_unlinked) [CHANGED from archive — was pending]
  - `AdminSessionController.kt` (1: admin_sessions_revoked) [CHANGED from archive — was pending]
  - `RateLimitAdminController.kt` (1: rate_limit_unlocked) [CHANGED from archive — was pending]
  - `TokenController.kt` (1: sessions_revoked_all) [CHANGED from archive — was pending]
  - `LoginRateLimitFilter.kt` (1: rate limit ProblemDetail) [EXISTING]
- **Skip (data-only)**: All GET endpoints, login/refresh (return AuthResponse tokens), introspect (RFC 7662), JWKS, anonymous endpoints (data/204), internal endpoints (machine consumers), RBAC/PBAC controllers (entity CRUD), MfaController.updateSettings/verifyMfa/setupTotp/resendOtp
- **Error**: N/A

### FR-006: Content-Language response header [URD] — ✅ ĐÃ IMPLEMENT (100% coverage)
- `ContentLanguageFilter` (OncePerRequestFilter, @Order LOWEST_PRECEDENCE - 10) sets `Content-Language` on ALL `/api/**` responses
- Defense-in-depth: `AuthControllerAdvice.setContentLanguageHeader()` also sets for error responses
- `LoginRateLimitFilter.writeRateLimitResponse()` sets `Content-Language` directly for rate limit bypass path
- **Evidence**: `ContentLanguageFilter.kt`, `GlobalExceptionHandler.kt:134-136`, `LoginRateLimitFilter.kt:141`
- **Error**: N/A

### FR-007: Client gửi Accept-Language header [URD] — ⬜ CHƯA IMPLEMENT (Frontend — external repo)
- Frontend inject `Accept-Language` header vào mọi API request
- Sync với `i18n.language` khi change language
- Format: BCP 47 (e.g., `vi`, `en`)
- Hook vào `api.ts` `setGlobalHeaders()`
- **Error**: Missing header → server fallback `en`

### FR-008: Client gửi metadata headers (X-App-Version, X-Client-Platform) [URD] — ⬜ CHƯA IMPLEMENT (Frontend — external repo)
- Frontend inject `X-App-Version: {version}` (semver `^\d+\.\d+\.\d+$`) và `X-Client-Platform: web`
- Version từ `import.meta.env.VITE_APP_VERSION` hoặc `package.json` version
- **Error**: Missing header → server ignores (audit only), MDC value = "unknown"

### FR-009: Client hiển thị message trực tiếp [URD] — ⬜ CHƯA IMPLEMENT (Frontend — external repo)
- Error: `setErrorMessage(problem.detail)` — no switch/case on errorCode
- Success: show `response.message`
- Frontend KHÔNG tự build error message string
- Remove hardcoded strings trong forms
- **Error**: `problem.detail` undefined → fallback "An error occurred"

### FR-010: Fallback chain khi thiếu translation [ENRICHED] — ✅ ĐÃ IMPLEMENT
- Chain: DB `i18n_messages` → file `messages_{locale}` → file `messages` (default) → `ErrorCodeBase.description`
- Missing key KHÔNG gây exception — trả fallback message
- **Evidence**: `DatabaseMessageSource.resolveCode()` returns `null` → delegate to parent. `resolveMessage()` uses `defaultMessage` param.
- **Error**: Log warning `"Missing i18n key: {key}, locale: {locale}"`

### FR-011: Client metadata filter cho logging [ENRICHED] — ✅ ĐÃ IMPLEMENT
- `ClientMetadataFilter` (OncePerRequestFilter, @Order HIGHEST_PRECEDENCE + 10) extracts headers → MDC
- MDC keys: `appVersion`, `clientPlatform`; cleanup in finally block
- **Evidence**: `ClientMetadataFilter.kt` (57 lines)
- **Error**: Missing header → MDC value = "unknown"

### FR-012: Supported locales whitelist [ENRICHED] — ✅ ĐÃ IMPLEMENT
- `AcceptHeaderLocaleResolver` configured with `supportedLocales = [en, vi]`, `defaultLocale = ENGLISH`
- `LoginRateLimitFilter.resolveLocale()` mirrors same whitelist
- **Evidence**: `I18nConfig.kt:42-43`, `LoginRateLimitFilter.kt:115-124`
- **Error**: N/A

### FR-013: Idempotent locale resolution [ENRICHED] — ✅ ĐÃ IMPLEMENT
- `AcceptHeaderLocaleResolver` stateless → inherently idempotent
- Same `Accept-Language` → same resolved locale → same `Content-Language`
- `LoginRateLimitFilter.resolveLocale()` is pure function (no state) → deterministic
- **Evidence**: Spring built-in, `ContentLanguageFilter` reads from `LocaleContextHolder`
- **Error**: N/A

## 3. Non-functional Requirements

- NFR-001: MessageSource loading < 100ms startup overhead
- NFR-002: `getMessage()` latency < 1ms (in-memory Caffeine cache hit), < 50ms (DB cold hit)
- NFR-003: Backward compatible — existing API consumers không break nếu không gửi `Accept-Language`
- NFR-004: ALL responses MUST have `Content-Language` header — 100% coverage via ContentLanguageFilter + LoginRateLimitFilter + AuthControllerAdvice
- NFR-005: DB MessageSource cache TTL = 5 min (Caffeine, maxSize=500), hit ratio > 95%
- NFR-006: DB unavailable → degrade gracefully to file bundles (no error to client, log warning)

## 4. Database Schema

### Table: `i18n_messages` — ✅ ĐÃ IMPLEMENT

```sql
CREATE TABLE i18n_messages (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(128) NOT NULL,
    locale        VARCHAR(10)  NOT NULL,
    message       TEXT         NOT NULL,
    module        VARCHAR(64)  NOT NULL DEFAULT 'common',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL,
    CONSTRAINT uq_i18n_code_locale UNIQUE (code, locale)
);

CREATE INDEX idx_i18n_messages_code ON i18n_messages (code);
CREATE INDEX idx_i18n_messages_module ON i18n_messages (module);
```

## 5. API Contract Changes

### Headers (all endpoints)

| Header | Direction | Required | Example |
|--------|-----------|----------|---------|
| `Accept-Language` | Request | Optional (fallback en) | `vi`, `en` |
| `X-App-Version` | Request | Optional | `1.0.0` |
| `X-Client-Platform` | Request | Optional | `web` |
| `Content-Language` | Response | Always (NFR-004) | `vi`, `en` |

### Response Format

#### Success (2xx) — Action Endpoints
```json
{
  "field1": "value1",
  "message": "Đăng ký thành công"
}
```

#### Error (4xx/5xx) — ProblemDetail RFC 9457
```json
{
  "type": "https://auth-service/errors/rate_limited",
  "title": "AUTH_020",
  "status": 429,
  "detail": "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây.",
  "errorCode": "AUTH_020",
  "retryAfterSeconds": 60,
  "dimension": "IP"
}
```

### Complete Success Message Keys (18 endpoints — ALL IN BUNDLES)

| Key | Endpoint | EN | VI |
|-----|----------|----|-----|
| `auth.register_success` | CqrsAuthController.register() | Registration successful | Đăng ký thành công |
| `auth.logout_success` | CqrsAuthController.logout() | Logged out successfully | Đã đăng xuất thành công |
| `auth.password_changed` | CqrsAuthController.changePassword() | Password changed successfully | Đổi mật khẩu thành công |
| `auth.password_reset_sent` | CqrsAuthController.forgotPassword() | If the email exists, a reset link has been sent | Nếu email tồn tại, liên kết đặt lại mật khẩu đã được gửi |
| `auth.switch_domain_success` | CqrsAuthController.switchDomain() | Domain switched successfully | Chuyển đổi domain thành công |
| `auth.session_revoked` | SessionController.revokeSession() | Session revoked | Đã thu hồi phiên |
| `auth.all_sessions_revoked` | SessionController.revokeAllSessions() | All sessions revoked | Đã thu hồi tất cả phiên |
| `auth.account_deactivated` | AccountLifecycleController.deactivateAccount() | Account deactivated | Tài khoản đã bị vô hiệu hóa |
| `auth.deletion_requested` | AccountLifecycleController.requestDeletion() | Deletion request created | Yêu cầu xóa đã được tạo |
| `auth.deletion_cancelled` | AccountLifecycleController.cancelDeletion() | Deletion request cancelled | Yêu cầu xóa đã bị hủy |
| `auth.totp_confirmed` | MfaController.confirmTotp() | TOTP setup confirmed successfully | Xác nhận cài đặt TOTP thành công |
| `auth.recovery_codes_warning` | MfaController.regenerateRecoveryCodes() | Save these codes — they will not be shown again. | Lưu lại các mã này — chúng sẽ không được hiển thị lại. |
| `auth.sso_identity_linked` | SsoController.linkIdentity() | SSO identity linked successfully | Liên kết danh tính SSO thành công |
| `auth.sso_identity_unlinked` | SsoController.unlinkIdentity() | SSO identity unlinked successfully | Hủy liên kết danh tính SSO thành công |
| `auth.admin_sessions_revoked` | AdminSessionController.forceRevokeUserSessions() | All sessions revoked for user {0} | Đã thu hồi tất cả phiên cho người dùng {0} |
| `auth.rate_limit_unlocked` | RateLimitAdminController.adminUnlock() | Rate limit locks cleared successfully | Đã xóa khóa giới hạn tốc độ thành công |
| `auth.sessions_revoked_all` | TokenController.revokeAllSessions() | All sessions revoked | Đã thu hồi tất cả phiên |
| `auth.rate_limited` | LoginRateLimitFilter.writeRateLimitResponse() | Too many login attempts ({1}). Please wait {0} seconds. | Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây. |

### Message Interpolation Examples

| Error Code | Args | EN Message | VI Message |
|-----------|------|-----------|------------|
| AUTH_020 | [60, "IP"] | Too many login attempts (IP). Please wait 60 seconds | Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây |
| AUTH_021 | [3] | Maximum active sessions (3) reached | Đã đạt tối đa 3 phiên hoạt động |
| (success) admin_sessions_revoked | [42] | All sessions revoked for user 42 | Đã thu hồi tất cả phiên cho người dùng 42 |
