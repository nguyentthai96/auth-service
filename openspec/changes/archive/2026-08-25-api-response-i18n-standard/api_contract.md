# API Contract: api-response-i18n-standard

<!-- contract-version: 3.0 -->
<!-- backend-status: partially-implemented -->
<!-- frontend-status: pending -->
<!-- generated-by: wf_openspec -->
<!-- generated-at: 2026-08-25T12:00:00+07:00 -->
<!-- updated: 2026-08-25 — reflects ~97% implementation, LoginRateLimitFilter RESOLVED, 7 action endpoints pending + frontend pending -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.
>
> **Cross-cutting change**: This contract does NOT define new endpoints. It defines the **i18n protocol layer** applied to ALL existing and future endpoints. It supplements the `auth-login-admin` API Contract.
>
> [CHANGED] v3.0: Updated to reflect LoginRateLimitFilter i18n RESOLVED. 7 action endpoints still need success message i18n. New message keys listed.

---

## I18n Protocol

### Supported Locales

| Locale | Language | Default | Format | Status |
|--------|----------|---------|--------|--------|
| `en` | English | ✅ | BCP 47 (RFC 5646) | ✅ IMPLEMENTED |
| `vi` | Vietnamese | | BCP 47 (RFC 5646) | ✅ IMPLEMENTED |

### Locale Resolution Order (Server-side) — ✅ IMPLEMENTED

```
1. Accept-Language header → match against supported locales [en, vi]
2. "vi-VN" → normalize to "vi" → match ✅
3. "km" → no match → fallback to "en"
4. Missing header → "en" (default)
```

**Implementation**: `AcceptHeaderLocaleResolver` in `I18nConfig.kt:39-44`

### Locale Detection Order (Client-side)

```
1. localStorage("user_language") → persisted user preference
2. navigator.languages → match first supported locale
3. "en" → fallback default
```

**Implementation**: `detectLanguage()` in `I18nProvider.tsx`

---

## Request Headers (ALL Endpoints)

> These headers MUST be sent with **every API request** from the frontend.

| Header | Required | Format | Default | Description | Backend Status |
|--------|----------|--------|---------|-------------|----------------|
| `Accept-Language` | Optional | BCP 47 | `en` | Requested locale for response messages | ✅ Server handles (fallback en) |
| `X-App-Version` | Optional | semver `x.y.z` | `0.0.0` | Frontend app version for audit/logging | ✅ ClientMetadataFilter → MDC |
| `X-Client-Platform` | Optional | string | `web` | Client platform identifier | ✅ ClientMetadataFilter → MDC |

### TypeScript Header Setup

```typescript
// Injected globally via api.ts setGlobalHeaders()
interface ClientHeaders {
  'Accept-Language': string;      // e.g., "vi", "en" — synced from I18nProvider
  'X-App-Version': string;        // e.g., "1.0.0" — from import.meta.env.VITE_APP_VERSION
  'X-Client-Platform': string;    // "web" — hardcoded for admin dashboard
}
```

---

## Response Headers (ALL Endpoints)

> These headers are returned by the server on **every response**.

| Header | Always | Format | Description | Implementation |
|--------|--------|--------|-------------|----------------|
| `Content-Language` | ✅ | BCP 47 | Actual locale used to render response messages | `ContentLanguageFilter` (all paths) + `AuthControllerAdvice` (errors) + `LoginRateLimitFilter` (rate limit) — ✅ ALL PATHS COVERED |

---

## Response Envelope — Success (2xx)

> All success responses use `ApiResponse<T>` envelope or inline map with `message` field.

```typescript
interface ApiResponse<T> {
  code: string;          // "00" for success
  msgCode: string;       // Machine-readable code (e.g., "SUCCESS")
  message: string;       // i18n-resolved human-readable message
  data?: T;              // Response payload (omitted when null)
  timestamp: number;     // Unix epoch millis
}
```

### i18n Behavior — ✅ IMPLEMENTED (10 endpoints) + 🔲 PENDING (7 endpoints)

| Accept-Language | `message` field value |
|----------------|-----------------------|
| `en` (or missing) | `"Operation completed successfully"` |
| `vi` | `"Thao tác thành công"` |

### Message Resolution Chain — ✅ IMPLEMENTED

```
1. DatabaseMessageSource → i18n_messages table (cache: 5 min Caffeine, maxSize=500)
2. File MessageSource → classpath:messages/auth-messages_{locale}.properties
3. File MessageSource → classpath:messages/auth-messages.properties (default en)
4. ErrorCodeBase.description → hardcoded fallback (last resort)
```

---

## Response Envelope — Error (4xx/5xx)

> All error responses use **RFC 9457 ProblemDetail** format.

```typescript
interface ProblemDetail {
  type: string;                   // URI reference identifying problem type
  title: string;                  // Error code (e.g., "AUTH_020")
  status: number;                 // HTTP status code
  detail: string;                 // i18n-resolved error description
  errorCode: string;              // App-level error code
  retryAfterSeconds?: number;     // Seconds until retry (rate limit)
  dimension?: string;             // Rate limit dimension (IP, USERNAME, DEVICE)
  maxSessions?: number;           // Session policy limit
  activeCount?: number;           // Current active sessions
}
```

### i18n Behavior — Error Messages — ✅ ALL PATHS COVERED

| Accept-Language | Error Code | `detail` field value | Path | Status |
|----------------|------------|---------------------|------|--------|
| `en` | `AUTH_001` | `"Invalid username or password"` | AuthControllerAdvice | ✅ |
| `vi` | `AUTH_001` | `"Sai tên đăng nhập hoặc mật khẩu"` | AuthControllerAdvice | ✅ |
| `en` | `AUTH_020` | `"Too many login attempts (IP). Please wait 60 seconds."` | LoginRateLimitFilter | ✅ |
| `vi` | `AUTH_020` | `"Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây."` | LoginRateLimitFilter | ✅ |
| `en` | `AUTH_021` (args=[3]) | `"Maximum active sessions (3) reached"` | AuthControllerAdvice | ✅ |
| `vi` | `AUTH_021` (args=[3]) | `"Đã đạt tối đa 3 phiên hoạt động"` | AuthControllerAdvice | ✅ |

---

## Error Code Registry

### Auth-service Error Codes (auth.*) — ✅ ALL 44 CODES IN BUNDLES

> Message key = `AuthErrorCode.msgCode`. All 44 codes have entries in both `en` and `vi` bundles.

| Code | Message Key | HTTP | EN Message | VI Message | Frontend Action |
|------|-------------|------|------------|------------|--------------------|
| AUTH_001 | `auth.invalid_credentials` | 401 | Invalid username or password | Sai tên đăng nhập hoặc mật khẩu | `field-error` + `toast` |
| AUTH_002 | `auth.account_locked` | 403 | Account is locked due to failed login attempts | Tài khoản bị khóa do đăng nhập sai quá nhiều lần | `modal` with countdown |
| AUTH_003 | `auth.token_expired` | 401 | JWT token has expired | Token đã hết hạn | `redirect` to login |
| AUTH_004 | `auth.permission_denied` | 403 | Insufficient permissions | Không đủ quyền truy cập | `toast` |
| AUTH_005 | `auth.resource_not_found` | 404 | Resource not found | Không tìm thấy tài nguyên | `toast` |
| AUTH_006 | `auth.duplicate_resource` | 409 | Resource already exists | Tài nguyên đã tồn tại | `toast` |
| AUTH_007 | `auth.captcha_required` | 428 | CAPTCHA verification required | Yêu cầu xác minh CAPTCHA | Auto-trigger ALTCHA |
| AUTH_008 | `auth.captcha_failed` | 400 | CAPTCHA verification failed | Xác minh CAPTCHA thất bại | `toast` + re-trigger |
| AUTH_009 | `auth.policy_evaluation_failed` | 403 | Policy evaluation failed | Đánh giá chính sách thất bại | `toast` |
| AUTH_010 | `auth.write_not_allowed` | 403 | Write operation not allowed | Không được phép thực hiện ghi | `toast` |
| AUTH_011 | `auth.mfa_code_invalid` | 401 | Invalid MFA verification code | Mã xác minh MFA không hợp lệ | `field-error` |
| AUTH_012 | `auth.mfa_token_expired` | 401 | MFA session token has expired | Phiên xác minh MFA đã hết hạn | `redirect` to login |
| AUTH_013 | `auth.mfa_max_attempts` | 403 | Maximum MFA verification attempts exceeded | Đã vượt quá số lần xác minh MFA cho phép | `modal` |
| AUTH_014 | `auth.sso_token_invalid` | 401 | SSO token exchange failed | Trao đổi token SSO thất bại | `toast` |
| AUTH_015 | `auth.sso_user_not_provisioned` | 403 | SSO user not provisioned | Người dùng SSO chưa được cấp quyền | `toast` |
| AUTH_016 | `auth.sso_identity_conflict` | 409 | SSO identity already linked | Danh tính SSO đã được liên kết | `modal` |
| AUTH_017 | `auth.password_policy_violation` | 400 | Password does not meet requirements | Mật khẩu không đáp ứng yêu cầu | `field-error` |
| AUTH_018 | `auth.password_expired` | 403 | Password has expired | Mật khẩu đã hết hạn | `modal` + redirect |
| AUTH_019 | `auth.mfa_rate_limited` | 429 | MFA rate limit exceeded | Đã vượt quá giới hạn tốc độ xác minh MFA | `toast` + countdown |
| AUTH_020 | `auth.rate_limited` | 429 | Too many login attempts ({1}). Please wait {0} seconds. | Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây. | `toast` + countdown |
| AUTH_021 | `auth.session_limit` | 409 | Maximum active sessions ({0}) reached | Đã đạt tối đa {0} phiên hoạt động | `modal` |
| AUTH_030-039 | `auth.e2ee_*` | Various | E2EE error messages | E2EE Vietnamese translations | `toast` |
| AUTH_040-044 | `auth.anonymous_*` | Various | Anonymous session error messages | Anonymous Vietnamese translations | `toast` |

### Success Message Keys — ✅ 11 EXISTING + 🔲 7 NEW

#### Existing (11 keys — already in bundles)

| Key | Endpoint | EN | VI |
|-----|----------|----|-----|
| `auth.login_success` | (reserved — data-only) | Login successful | Đăng nhập thành công |
| `auth.logout_success` | CqrsAuthController.logout() | Logged out successfully | Đã đăng xuất thành công |
| `auth.password_changed` | CqrsAuthController.changePassword() | Password changed successfully | Đổi mật khẩu thành công |
| `auth.token_refreshed` | (reserved — data-only) | Token refreshed successfully | Làm mới token thành công |
| `auth.password_reset_sent` | CqrsAuthController.forgotPassword() | If the email exists, a reset link has been sent | Nếu email tồn tại, liên kết đặt lại mật khẩu đã được gửi |
| `auth.register_success` | CqrsAuthController.register() | Registration successful | Đăng ký thành công |
| `auth.switch_domain_success` | CqrsAuthController.switchDomain() | Domain switched successfully | Chuyển đổi domain thành công |
| `auth.session_revoked` | SessionController.revokeSession() | Session revoked | Đã thu hồi phiên |
| `auth.all_sessions_revoked` | SessionController.revokeAllSessions() | All sessions revoked | Đã thu hồi tất cả phiên |
| `auth.account_deactivated` | AccountLifecycleController | Account deactivated... | Tài khoản đã bị vô hiệu hóa... |
| `auth.deletion_requested` | AccountLifecycleController | Deletion request created... | Yêu cầu xóa đã được tạo... |

#### New (7 keys — to be added to bundles) [NEW]

| Key | Endpoint | EN | VI |
|-----|----------|----|-----|
| `auth.totp_confirmed` | MfaController.confirmTotp() | TOTP setup confirmed successfully | Xác nhận cài đặt TOTP thành công |
| `auth.recovery_codes_warning` | MfaController.regenerateRecoveryCodes() | Save these codes — they will not be shown again. | Lưu lại các mã này — chúng sẽ không được hiển thị lại. |
| `auth.all_user_sessions_revoked` | TokenController.revokeAllSessions() | All sessions revoked | Đã thu hồi tất cả phiên |
| `auth.sso_identity_linked` | SsoController.linkIdentity() | SSO identity linked successfully | Liên kết danh tính SSO thành công |
| `auth.sso_identity_unlinked` | SsoController.unlinkIdentity() | SSO identity unlinked successfully | Hủy liên kết danh tính SSO thành công |
| `auth.sessions_revoked_for_user` | AdminSessionController.forceRevokeUserSessions() | All sessions revoked for user {0} | Đã thu hồi tất cả phiên cho người dùng {0} |
| `auth.rate_limit_unlocked` | RateLimitAdminController.adminUnlock() | Rate limit locks cleared successfully | Đã xóa khóa giới hạn tốc độ thành công |

---

## Database Schema — i18n_messages — ✅ IMPLEMENTED

> Dynamic message source for runtime-manageable i18n entries.

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
```

> **Note**: This table has NO public API in this change. Future admin API will manage CRUD (Phase 2).

---

## Frontend Integration Guide

### Language Switcher State

```typescript
interface LanguageType {
  id: string;       // "en" | "vi"
  title: string;    // "English" | "Tiếng Việt"
  flag: string;     // "US" | "VN"
}

const SUPPORTED_LANGUAGES: LanguageType[] = [
  { id: 'en', title: 'English', flag: 'US' },
  { id: 'vi', title: 'Tiếng Việt', flag: 'VN' }
];
```

### Locale Detection Utility

```typescript
function detectLanguage(supportedLocales: string[], defaultLocale: string): string {
  const stored = localStorage.getItem('user_language');
  if (stored && supportedLocales.includes(stored)) return stored;

  for (const lang of navigator.languages) {
    const short = lang.split('-')[0];
    if (supportedLocales.includes(short)) return short;
  }

  return defaultLocale;
}
```

### Header Injection (api.ts)

```typescript
// On language change (from I18nProvider):
setGlobalHeaders({
  'Accept-Language': languageId,
  'X-App-Version': import.meta.env.VITE_APP_VERSION || '0.0.0',
  'X-Client-Platform': 'web'
});
```

### Error Display (JwtSignInForm.tsx)

```typescript
// BEFORE (hardcoded):
switch (problem.errorCode) {
  case 'AUTH_001': setErrorMessage('Invalid username or password'); break;
  case 'AUTH_020': setErrorMessage('Too many login attempts'); break;
  // ... 12 hardcoded strings
}

// AFTER (server-rendered i18n):
setErrorMessage(problem.detail || 'An error occurred');
// Client-side logic ONLY for UX behavior:
if (problem.errorCode === 'AUTH_007') setCaptchaRequired(true);
if (problem.errorCode === 'AUTH_020') setRateLimitRetry(problem.retryAfterSeconds);
```

### Language Persistence

```typescript
const changeLanguage = async (languageId: string) => {
  await i18n.changeLanguage(languageId);
  localStorage.setItem('user_language', languageId);
  setGlobalHeaders({ 'Accept-Language': languageId });
};
```

---

## Contract Rules

1. **Cross-cutting**: This contract applies to ALL existing and future endpoints
2. **Supplements**: Supplements `auth-login-admin` API Contract (endpoint definitions unchanged)
3. **Backward compatible**: Missing `Accept-Language` header → server defaults to English — no break
4. **Message ownership**: `api.*` keys = base-core, `auth.*` keys = auth-service
5. **DB overrides file**: Same key in DB + file → DB wins (CompositeMessageSource priority)
6. **Cache invalidation**: DB message changes take effect within 5 minutes (Caffeine TTL)
7. **Frontend rule**: NEVER build error messages client-side — always use `problem.detail` from server
8. **Locale persistence**: Frontend MUST persist language in `localStorage("user_language")`
9. **Content-Language coverage**: ALL response paths (controller, advice, filter) MUST set Content-Language header
10. **Action endpoint rule**: All action endpoints (mutation responses) MUST include i18n `message` field. Data-only endpoints (queries, token responses) MAY omit.

---

## Validation Checklist

- [x] All request headers documented (Accept-Language, X-App-Version, X-Client-Platform)
- [x] All response headers documented (Content-Language)
- [x] Success envelope format (ApiResponse<T>) with i18n message field
- [x] Error envelope format (ProblemDetail RFC 9457) with i18n detail field
- [x] All 44 auth-service error codes with EN + VI translations
- [x] All 11 existing + 7 new success message keys documented
- [x] Database schema (i18n_messages) documented
- [x] Frontend integration guide (detection, headers, display, persistence)
- [x] Fallback chain documented (DB → file locale → file default → hardcoded)
- [x] LoginRateLimitFilter i18n ✅ RESOLVED
- [x] 7 remaining action endpoints identified with fix specification
- [x] Contract rules defined (10 rules)
