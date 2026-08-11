# API Contract: api-response-i18n-standard

<!-- contract-version: 1.0 -->
<!-- backend-status: pending -->
<!-- frontend-status: pending -->
<!-- generated-by: wf_api_contract -->
<!-- generated-at: 2026-08-11T19:45:00+07:00 -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.
>
> **Cross-cutting change**: This contract does NOT define new endpoints. It defines the **i18n protocol layer** applied to ALL existing and future endpoints. It supplements the `auth-login-admin` API Contract.

---

## I18n Protocol

### Supported Locales

| Locale | Language | Default | Format |
|--------|----------|---------|--------|
| `en` | English | ✅ | BCP 47 (RFC 5646) |
| `vi` | Vietnamese | | BCP 47 (RFC 5646) |

### Locale Resolution Order (Server-side)

```
1. Accept-Language header → match against supported locales
2. "vi-VN" → normalize to "vi" → match ✅
3. "km" → no match → fallback to "en"
4. Missing header → "en" (default)
```

### Locale Detection Order (Client-side)

```
1. localStorage("user_language") → persisted user preference
2. navigator.languages → match first supported locale
3. "en" → fallback default
```

---

## Request Headers (ALL Endpoints)

> These headers MUST be sent with **every API request** from the frontend.

| Header | Required | Format | Default | Description |
|--------|----------|--------|---------|-------------|
| `Accept-Language` | Optional | BCP 47 | `en` | Requested locale for response messages |
| `X-App-Version` | Optional | semver `x.y.z` | `0.0.0` | Frontend app version for audit/logging |
| `X-Client-Platform` | Optional | string | `web` | Client platform identifier |

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

| Header | Always | Format | Description |
|--------|--------|--------|-------------|
| `Content-Language` | ✅ | BCP 47 | Actual locale used to render response messages |

---

## Response Envelope — Success (2xx)

> All success responses use `ApiResponse<T>` envelope.

```typescript
interface ApiResponse<T> {
  code: string;          // "00" for success, visibility: public
  msgCode: string;       // Machine-readable code (e.g., "SUCCESS"), visibility: public
  message: string;       // i18n-resolved human-readable message, visibility: public
  data?: T;              // Response payload (omitted when null), visibility: public
  timestamp: number;     // Unix epoch millis, visibility: public
}
```

### i18n Behavior

| Accept-Language | `message` field value |
|----------------|-----------------------|
| `en` (or missing) | `"Operation completed successfully"` |
| `vi` | `"Thao tác thành công"` |

### Message Resolution Chain

```
1. DatabaseMessageSource → i18n_messages table (cache: 5 min Caffeine)
2. File MessageSource → classpath:messages/messages_{locale}.properties
3. File MessageSource → classpath:messages/messages.properties (default en)
4. ErrorCodeBase.description → hardcoded fallback (last resort)
```

---

## Response Envelope — Error (4xx/5xx)

> All error responses use **RFC 9457 ProblemDetail** format.

```typescript
interface ProblemDetail {
  type: string;                   // URI reference identifying problem type, visibility: public
  title: string;                  // Error code (e.g., "AUTH_020"), visibility: public
  status: number;                 // HTTP status code, visibility: public
  detail: string;                 // i18n-resolved error description, visibility: public
  errorCode: string;              // App-level error code, visibility: public
  retryAfterSeconds?: number;     // Seconds until retry (rate limit), visibility: public
  dimension?: string;             // Rate limit dimension, visibility: public
  maxSessions?: number;           // Session policy limit, visibility: public
  activeCount?: number;           // Current active sessions, visibility: public
}
```

### i18n Behavior — Error Messages

| Accept-Language | Error Code | `detail` field value |
|----------------|------------|---------------------|
| `en` | `AUTH_001` | `"Invalid username or password"` |
| `vi` | `AUTH_001` | `"Sai tên đăng nhập hoặc mật khẩu"` |
| `en` | `AUTH_020` | `"Too many login attempts"` |
| `vi` | `AUTH_020` | `"Quá nhiều lần đăng nhập"` |
| `en` | `AUTH_021` (args=[3]) | `"Maximum active sessions (3) reached"` |
| `vi` | `AUTH_021` (args=[3]) | `"Đã đạt tối đa 3 phiên hoạt động"` |

---

## Error Code Registry

### Base-core Error Codes (api.*)

> Applied to ALL services using base-core. Message key = `api.*`.

| Message Key | HTTP Status | EN Message | VI Message |
|-------------|------------|------------|------------|
| `api.success` | 200 | Operation completed successfully | Thao tác thành công |
| `api.bad_request` | 400 | Bad request | Yêu cầu không hợp lệ |
| `api.not_found` | 404 | Resource not found | Không tìm thấy tài nguyên |
| `api.forbidden` | 403 | Access forbidden | Truy cập bị từ chối |
| `api.validation_error` | 400 | Validation error: {0} | Lỗi xác thực: {0} |
| `api.system_error` | 500 | An unexpected error occurred. Reference: {0} | Đã xảy ra lỗi không mong muốn. Mã tham chiếu: {0} |

### Auth-service Error Codes (auth.*)

> Applied to auth-service endpoints. Message key = `auth.*` (= `AuthErrorCode.msgCode`).

| Code | Message Key | HTTP Status | EN Message | VI Message | Frontend Action |
|------|-------------|------------|------------|------------|-----------------|
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
| AUTH_013 | `auth.mfa_max_attempts` | 403 | Maximum MFA verification attempts exceeded | Đã vượt quá số lần xác minh MFA | `modal` |
| AUTH_014 | `auth.sso_token_invalid` | 401 | SSO token exchange failed | Trao đổi token SSO thất bại | `toast` |
| AUTH_015 | `auth.sso_user_not_provisioned` | 403 | SSO user not provisioned | Người dùng SSO chưa được cung cấp | `toast` |
| AUTH_016 | `auth.sso_identity_conflict` | 409 | SSO identity already linked | Danh tính SSO đã được liên kết | `modal` |
| AUTH_017 | `auth.password_policy_violation` | 400 | Password does not meet requirements | Mật khẩu không đáp ứng yêu cầu | `field-error` |
| AUTH_018 | `auth.password_expired` | 403 | Password has expired | Mật khẩu đã hết hạn | `modal` + redirect |
| AUTH_019 | `auth.mfa_rate_limited` | 429 | MFA rate limit exceeded | Đã vượt quá giới hạn tốc độ MFA | `toast` + countdown |
| AUTH_020 | `auth.rate_limited` | 429 | Too many login attempts | Quá nhiều lần đăng nhập | `toast` + countdown |
| AUTH_021 | `auth.session_limit` | 409 | Maximum active sessions ({0}) reached | Đã đạt tối đa {0} phiên hoạt động | `modal` |

---

## Database Schema — i18n_messages

> Dynamic message source for runtime-manageable i18n entries.

```typescript
interface I18nMessage {
  id: number;              // BIGSERIAL PK, visibility: internal
  code: string;            // Message key (e.g., "auth.rate_limited"), visibility: internal
  locale: string;          // BCP 47 locale (e.g., "en", "vi"), visibility: internal
  message: string;         // Message template with {0}, {1} placeholders, visibility: internal
  module: string;          // Service grouping (e.g., "auth", "common"), visibility: internal
  isActive: boolean;       // Active flag, visibility: internal
  createdAt: number;       // Epoch millis, visibility: internal
  updatedAt: number;       // Epoch millis, visibility: internal
}
```

> **Note**: This table has NO public API in this change. Future admin API will manage CRUD operations (Phase 2).

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
/**
 * Detect user's preferred language via cascade:
 * 1. localStorage (persisted user choice)
 * 2. navigator.languages (browser/OS locale)
 * 3. "en" (fallback default)
 */
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
  'Accept-Language': languageId,      // "vi" or "en"
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
// When user explicitly changes language:
const changeLanguage = async (languageId: string) => {
  await i18n.changeLanguage(languageId);
  localStorage.setItem('user_language', languageId);
  setGlobalHeaders({ 'Accept-Language': languageId });
};
```

---

## Sequence Diagrams

### Normal Request Flow (with i18n)

```
Frontend                         Server                          DB
   │                                │                              │
   │ Accept-Language: vi            │                              │
   │ X-App-Version: 1.0.0          │                              │
   │ X-Client-Platform: web        │                              │
   │──────────────────────────────▶ │                              │
   │                                │ AcceptHeaderLocaleResolver   │
   │                                │ → resolve("vi")             │
   │                                │ → LocaleContextHolder(vi)   │
   │                                │                              │
   │                    [SUCCESS]   │                              │
   │                                │ getMessage("api.success",   │
   │                                │   null, vi)                 │
   │   ◀────── 200 ────────────── │                              │
   │   Content-Language: vi         │                              │
   │   { code: "00",               │                              │
   │     message: "Thao tác        │                              │
   │     thành công",              │                              │
   │     data: {...} }             │                              │
   │                                │                              │
   │                    [ERROR]     │                              │
   │                                │ getMessage("auth.rate_      │
   │                                │   limited", [5,"IP"], vi)   │
   │                                │ → DB check ────────────────▶│
   │                                │ ◀── cache/lookup ──────────│
   │   ◀────── 429 ────────────── │                              │
   │   Content-Language: vi         │                              │
   │   { detail: "Quá nhiều lần    │                              │
   │     đăng nhập",              │                              │
   │     errorCode: "AUTH_020" }   │                              │
   │                                │                              │
   │ show(problem.detail)          │                              │
```

### Fallback Chain (Missing Translation)

```
MessageSource Chain:
   ┌─────────────────────────────────────────────────┐
   │ 1. DatabaseMessageSource (i18n_messages table)  │
   │    → cache hit? → return                        │
   │    → cache miss? → DB query                     │
   │    → DB miss? → delegate to parent ↓            │
   │                                                 │
   │ 2. FileMessageSource (auth-messages_vi)         │
   │    → key found? → return                        │
   │    → miss? → try default locale ↓               │
   │                                                 │
   │ 3. FileMessageSource (auth-messages / messages)  │
   │    → key found? → return (English)              │
   │    → miss? → delegate ↓                         │
   │                                                 │
   │ 4. ErrorCodeBase.description                    │
   │    → hardcoded fallback (never fails)           │
   └─────────────────────────────────────────────────┘
```

---

## Contract Rules

1. **Cross-cutting**: This contract applies to ALL existing and future endpoints — not just auth
2. **Supplements**: This contract supplements `auth-login-admin` API Contract (endpoint definitions unchanged)
3. **Backward compatible**: Missing `Accept-Language` header → server defaults to English — no break
4. **Message ownership**: `api.*` keys owned by base-core, `auth.*` keys owned by auth-service
5. **DB overrides file**: If same key exists in both DB and file → DB wins (CompositeMessageSource priority)
6. **Cache invalidation**: DB message changes take effect within 5 minutes (Caffeine TTL)
7. **Frontend rule**: NEVER build error messages client-side — always use `problem.detail` from server
8. **Locale persistence**: Frontend MUST persist language choice in `localStorage("user_language")`

---

## Validation Checklist

- [x] All request headers documented (Accept-Language, X-App-Version, X-Client-Platform)
- [x] All response headers documented (Content-Language)
- [x] Success envelope format (ApiResponse<T>) with i18n message field
- [x] Error envelope format (ProblemDetail RFC 9457) with i18n detail field
- [x] All 6 base-core error codes with EN + VI translations
- [x] All 21 auth-service error codes with EN + VI translations and frontend actions
- [x] Database schema (i18n_messages) documented
- [x] Frontend integration guide (detection, headers, display, persistence)
- [x] Fallback chain documented (DB → file locale → file default → hardcoded)
- [x] Sequence diagrams for normal flow and fallback
- [x] Contract rules defined
