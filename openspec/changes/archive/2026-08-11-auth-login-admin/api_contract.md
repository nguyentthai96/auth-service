# API Contract: auth-login-admin

<!-- contract-version: 1.0 -->
<!-- backend-status: implemented -->
<!-- frontend-status: implemented -->
<!-- generated-by: wf_api_contract -->
<!-- generated-at: 2026-08-11T10:32:00+07:00 -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.

---

## Response Wrapper

> Backend auth endpoints return **direct JSON responses** (NOT wrapped in ApiResponse envelope).
> Error responses use **RFC 7807 ProblemDetail** format.
> Frontend service layer returns typed objects directly.

### Success Response

Direct JSON body — varies by endpoint (see individual endpoint specs below).

### Error Response (RFC 7807)

```typescript
interface ProblemDetail {
  type: string;           // URI reference identifying problem type
  title: string;          // Human-readable summary, visibility: public
  status: number;         // HTTP status code, visibility: public
  detail: string;         // Explanation specific to occurrence, visibility: public
  errorCode: string;      // App-level error code (e.g., "AUTH_001"), visibility: public
  retryAfterSeconds?: number;  // Seconds until retry (rate limit), visibility: public
  dimension?: string;     // Rate limit dimension ("IP"/"USER"/"DEVICE"), visibility: public
  maxSessions?: number;   // Session policy limit, visibility: public
  activeCount?: number;   // Current active sessions count, visibility: public
}
```

---

## Endpoints

### POST /api/auth/login

- **Auth**: `none`
- **Headers**:
  | Header | Required | Description |
  |--------|----------|-------------|
  | `X-Device-Fingerprint` | optional | SHA-256 browser fingerprint for device tracking |
  | `User-Agent` | auto | Extracted server-side for login history |
  | `X-Forwarded-For` | auto | Real IP extraction behind proxy |

- **Request**:
  ```typescript
  interface LoginRequest {
    username: string;             // @NotBlank, visibility: public
    password: string;             // @NotBlank, visibility: public
    domainCode?: string;          // Default: null (fallback "default"), visibility: public
    captchaToken?: string;        // ALTCHA verification token, visibility: public
    trustedDeviceHash?: string;   // Trusted device skip, visibility: public
    deviceFingerprint?: string;   // Browser fingerprint, visibility: public
    captchaPayload?: string;      // ALTCHA Base64 PoW payload, visibility: public
  }
  ```

- **Response (200 — Login Success)**:
  ```typescript
  interface LoginResponse {
    accessToken: string;          // JWT RS256, visibility: public
    refreshToken?: string | null; // null — transported via HttpOnly cookie, visibility: public
    tokenType: string;            // "Bearer", visibility: public
    expiresIn: number;            // Seconds until access token expires, visibility: public
    userId: number;               // Snowflake ID, visibility: public
    username: string;             // visibility: public
    activeDomain: string;         // Current domain code, visibility: public
    roles: string[];              // e.g. ["ADMIN"], visibility: public
    permissions: string[];        // e.g. ["user:read", "user:write"], visibility: public
  }
  ```

  > **Cookie Side-Effect**: On success, backend sets:
  > `Set-Cookie: refresh_token={jwt}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age={refreshTtl}`

- **Response (200 — MFA Required)**:
  ```typescript
  interface MfaRequiredResponse {
    mfaRequired: true;            // Discriminator field, visibility: public
    mfaToken: string;             // Temporary MFA session token, visibility: public
    method: string;               // "TOTP" | "EMAIL" | "SMS", visibility: public
    expiresIn: number;            // Seconds until mfaToken expires, visibility: public
  }
  ```

- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_001` | 401 | Invalid username or password | `field-error` on username + toast |
  | `AUTH_002` | 403 | Account locked (too many failed attempts) | `modal` with countdown |
  | `AUTH_007` | 428 | CAPTCHA required (suspicious login) | Auto-trigger ALTCHA solve + retry |
  | `AUTH_008` | 400 | CAPTCHA verification failed | `toast` + re-trigger ALTCHA |
  | `AUTH_020` | 429 | Rate limited (IP/User/Device) | `toast` + countdown from `retryAfterSeconds` |
  | `AUTH_021` | 409 | Session limit exceeded (REJECT_NEW policy) | `modal` with session management link |

---

### POST /api/auth/refresh

- **Auth**: `cookie` (HttpOnly refresh_token cookie auto-sent)
- **Request**: No body — refresh token extracted from `Cookie: refresh_token=...`
- **Response (200)**:
  ```typescript
  interface RefreshResponse {
    accessToken: string;          // New JWT, visibility: public
    refreshToken?: string | null; // null — rotated via new HttpOnly cookie, visibility: public
    tokenType: string;            // "Bearer", visibility: public
    expiresIn: number;            // Seconds, visibility: public
    userId: number;               // visibility: public
    username: string;             // visibility: public
    activeDomain: string;         // visibility: public
    roles: string[];              // visibility: public
    permissions: string[];        // visibility: public
  }
  ```

  > **Cookie Side-Effect**: New `Set-Cookie: refresh_token=...` with rotated token.
  > **Absolute Ceiling**: If `iat + 36000s` exceeded → returns 401.

- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_003` | 401 | Refresh token expired or invalid | `redirect` to login page |
  | — | 401 | Absolute session ceiling (10h) exceeded | `redirect` to login + toast "Phiên đăng nhập đã hết hạn" |

---

### POST /api/auth/logout

- **Auth**: `required` (Bearer token) + HttpOnly cookie
- **Request**: No body — refresh token from cookie
- **Response (204)**: No content

  > **Cookie Side-Effect**: Clears cookie: `Set-Cookie: refresh_token=; Max-Age=0; Path=/api/auth`
  > **Backend Side-Effect**: Revokes refresh token in DB, deactivates login session.

- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | — | 401 | Not authenticated | `redirect` to login |

---

### GET /api/captcha/challenge

- **Auth**: `none`
- **Request**: No parameters
- **Response (200)**:
  ```typescript
  interface AltchaChallenge {
    algorithm: string;      // "SHA-256", visibility: public
    challenge: string;      // Hex-encoded hash target, visibility: public
    salt: string;           // UUID-based unique salt, visibility: public
    signature: string;      // HMAC-SHA256(salt, serverKey), visibility: public
    maxnumber: number;      // Difficulty (e.g., 50000), visibility: public
  }
  ```

- **Errors**: None — always returns 200

---

### GET /api/auth/sessions

- **Auth**: `required` (Bearer token)
- **Request**: No parameters
- **Response (200)**:
  ```typescript
  // Array response
  type ActiveSessionsResponse = SessionInfo[];

  interface SessionInfo {
    id: number;                    // Session snowflake ID, visibility: public
    ipAddress: string;             // Masked IP for display, visibility: public
    deviceType: string | null;     // "DESKTOP" | "MOBILE" | "TABLET" | null, visibility: public
    browserName: string | null;    // "Chrome" | "Firefox" | etc, visibility: public
    osName: string | null;         // "macOS" | "Windows" | etc, visibility: public
    loginAt: string;               // ISO 8601 UTC, visibility: public
    lastActivityAt: string | null; // ISO 8601 UTC, visibility: public
    isNewDevice: boolean;          // First login from this device, visibility: public
    // userId: number;             // visibility: internal — filtered by JWT subject
    // userAgent: string;          // visibility: internal — raw UA string
    // deviceFingerprint: string;  // visibility: internal — privacy-sensitive
    // refreshTokenId: number;     // visibility: internal
    // revokedAt: string;          // visibility: internal — only active shown
    // revokeReason: string;       // visibility: internal
    // geoCountry: string;         // visibility: internal — Phase 2
  }
  ```

- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | — | 401 | Not authenticated | `redirect` to login |

---

### DELETE /api/auth/sessions/{sessionId}

- **Auth**: `required` (Bearer token)
- **Path Parameters**:
  | Param | Type | Description |
  |-------|------|-------------|
  | `sessionId` | number | Snowflake ID of session to revoke |

- **Response (204)**: No content
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | — | 401 | Not authenticated | `redirect` to login |
  | — | 403 | Session belongs to different user | `toast` "Không có quyền" |
  | `AUTH_005` | 404 | Session not found | `toast` |

---

### DELETE /api/auth/sessions

- **Auth**: `required` (Bearer token)
- **Request**: No body
- **Response (204)**: No content — all sessions for current user deactivated
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | — | 401 | Not authenticated | `redirect` to login |

---

## Shared Types

### User (from JWT claims)

```typescript
interface JwtPayload {
  sub: string;               // User ID (string), visibility: public
  username: string;          // visibility: public
  roles: string[];           // visibility: public
  permissions: string[];     // visibility: public
  domain: string;            // Active domain code, visibility: public
  iat: number;               // Issued at (Unix epoch), visibility: public
  exp: number;               // Expiration (Unix epoch), visibility: public
  // jti: string;            // visibility: internal — JWT ID for revocation
  // tokenType: string;      // visibility: internal — "ACCESS" | "REFRESH"
}
```

### Device Fingerprint

```typescript
/**
 * Client-generated SHA-256 hash of browser properties.
 * Sent via X-Device-Fingerprint header.
 * NOT a shared type — generated entirely on frontend.
 */
type DeviceFingerprint = string; // 64-char hex SHA-256
```

---

## State Contracts

### Auth State (Frontend)

```typescript
interface AuthState {
  authStatus: 'configuring' | 'authenticated' | 'unauthenticated';
  isAuthenticated: boolean;
  user: AuthUser | null;
}

interface AuthUser {
  id: string;               // From LoginResponse.userId.toString()
  displayName: string;      // From LoginResponse.username
  email: string;            // Empty string (admin login uses username)
  role: string;             // First item from LoginResponse.roles
}
```

### Login Form State (Frontend)

```typescript
interface LoginFormState {
  errorMessage: string | null;        // Global error alert
  captchaRequired: boolean;           // ALTCHA widget visibility
  solvingCaptcha: boolean;            // PoW in progress
  rateLimitRetry: number | null;      // Countdown seconds (null = not limited)
  isSubmitting: boolean;              // Form submission in progress
}
```

### Session Management State (Frontend — Phase 2 UI)

```typescript
interface SessionManagementState {
  sessions: SessionInfo[];            // Active sessions list
  isLoading: boolean;
  currentSessionId: number | null;    // From JWT claim or session match
}
```

---

## Security Contracts

### Token Lifecycle

| Token | Storage | TTL | Renewal |
|-------|---------|-----|---------|
| Access Token | In-memory (React ref) | 15 min | Sliding — auto-refresh at exp - 120s |
| Refresh Token | HttpOnly cookie | Configurable | Rotated on each refresh |
| Absolute Ceiling | — | 10h from `iat` | Force logout when exceeded |

### CORS Requirements

| Header | Value |
|--------|-------|
| `Access-Control-Allow-Origin` | Frontend domain (exact match) |
| `Access-Control-Allow-Credentials` | `true` |
| `Access-Control-Allow-Headers` | `*` |
| `Access-Control-Allow-Methods` | `GET, POST, PUT, DELETE, OPTIONS` |

### Security Headers

| Header | Value |
|--------|-------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |

---

## Contract Rules

1. **Bi-directional sync**: If frontend needs additional fields → update this contract → notify backend
2. **Version bump**: Any breaking change MUST increment `contract-version`
3. **Status tracking**: Each track updates its status header after implementation
4. **Visibility enforcement**: Frontend generators MUST filter out `visibility: internal` fields
5. **Error handling**: Every error code MUST have a corresponding frontend action
6. **Cookie transport**: Refresh tokens MUST NEVER appear in response body in production — HttpOnly cookie only

---

## Validation Checklist

- [x] Every endpoint has Request + Response + Errors defined
- [x] All fields have visibility annotation
- [x] Response wrapper format matches backend implementation (direct JSON + ProblemDetail errors)
- [x] Error codes are unique and documented
- [x] State contract matches endpoint response shape
- [ ] OpenAPI spec link is valid (not yet generated — backend build blocked by Gradle plugin)
