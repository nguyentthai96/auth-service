# API Contract: api-response-i18n-standard

<!-- contract-version: 4.0 -->
<!-- backend-status: fully-implemented -->
<!-- frontend-status: pending -->
<!-- generated-by: wf_openspec -->
<!-- generated-at: 2026-08-27T12:00:00+07:00 -->
<!-- updated: 2026-08-27 — reflects 100% backend implementation, 18/18 action endpoints DONE, all issues RESOLVED -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.
>
> **Cross-cutting change**: This contract does NOT define new endpoints. It defines the **i18n protocol layer** applied to ALL existing and future endpoints.
>
> [CHANGED] v4.0: Backend 100% complete. ALL 18 action endpoints + 1 filter use i18n. All issues from previous archives RESOLVED. Frontend changes still pending (external repo).

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

---

## Response Headers (ALL Endpoints)

| Header | Always | Format | Description | Implementation |
|--------|--------|--------|-------------|----------------|
| `Content-Language` | ✅ | BCP 47 | Actual locale used to render response messages | `ContentLanguageFilter` (all paths) + `AuthControllerAdvice` (errors) + `LoginRateLimitFilter` (rate limit) — ✅ ALL PATHS COVERED |

---

## Response Envelope — Success (2xx)

> Action endpoints return map with `message` field containing i18n-resolved text.

```typescript
// Action endpoint response (map-based)
interface ActionResponse {
  message: string;       // i18n-resolved human-readable message
  [key: string]: any;    // Additional endpoint-specific fields
}
```

### i18n Behavior — ✅ IMPLEMENTED (ALL 18 action endpoints)

| Accept-Language | `message` field value |
|----------------|-----------------------|
| `en` (or missing) | English message (e.g., "Registration successful") |
| `vi` | Vietnamese message (e.g., "Đăng ký thành công") |

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

---

## Complete Success Message Keys — ✅ ALL 18 IN BUNDLES

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
| `auth.rate_limited` | LoginRateLimitFilter | Too many login attempts ({1}). Please wait {0} seconds. | Quá nhiều lần đăng nhập ({1}). Vui lòng đợi {0} giây. |

---

## Frontend Integration Guide

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
2. **Backward compatible**: Missing `Accept-Language` header → server defaults to English — no break
3. **Message ownership**: `api.*` keys = base-core, `auth.*` keys = auth-service
4. **DB overrides file**: Same key in DB + file → DB wins (CompositeMessageSource priority)
5. **Cache invalidation**: DB message changes take effect within 5 minutes (Caffeine TTL)
6. **Frontend rule**: NEVER build error messages client-side — always use `problem.detail` from server
7. **Locale persistence**: Frontend MUST persist language in `localStorage("user_language")`
8. **Content-Language coverage**: ALL response paths (controller, advice, filter) MUST set Content-Language header
9. **Action endpoint rule**: All action endpoints (mutation responses) MUST include i18n `message` field
10. **Data-only endpoints**: Query/data endpoints MAY omit `message` field
