# API Contract: jwt-security-redesign

<!-- contract-version: 1.0 -->
<!-- backend-status: pending -->
<!-- frontend-status: implemented -->
<!-- generated-by: wf_api_contract -->
<!-- generated-at: 2026-09-07T11:18:00+07:00 -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.

---

## Response Convention

> **Auth-service does NOT use a wrapper envelope.** Controllers return `ResponseEntity<T>` directly.
> Errors use RFC 7807 `ProblemDetail` format via `AuthControllerAdvice`.

### Success Response

```typescript
// Direct body — no wrapper
// Frontend receives the DTO directly from ResponseEntity<T>
```

### Error Response (RFC 7807 ProblemDetail)

```typescript
interface ProblemDetail {
  type: string;        // URI: "https://auth-service/errors/{error_name}"
  title: string;       // AuthErrorCode code (e.g., "AUTH_070")
  status: number;      // HTTP status code
  detail: string;      // I18n error message
  instance?: string;   // Request URI
  errorCode: string;   // AuthErrorCode code
  // Extra properties per exception type (e.g., retryAfterSeconds)
}
```

---

## Endpoints

### GET /api/auth/devices

> **FR**: FR-006 — List active devices for the authenticated user.

- **Auth**: `required` (Bearer JWT)
- **Request**: No request body. Headers: `Authorization: Bearer <token>`
- **Request Headers**:
  ```typescript
  // Standard auth header only
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ResponseEntity<List<DeviceResponse>>
  interface DeviceResponse {
    sessionId: number;          // visibility: public — snowflake ID
    deviceType: string | null;  // visibility: public — "DESKTOP" | "MOBILE" | "TABLET"
    browserName: string | null; // visibility: public — e.g., "Chrome", "Safari"
    osName: string | null;      // visibility: public — e.g., "Windows", "macOS"
    ipAddress: string;          // visibility: public — masked last octet for privacy
    loginAt: string;            // visibility: public — ISO 8601 UTC
    lastActivityAt: string | null; // visibility: public — ISO 8601 UTC
    deviceName: string | null;  // visibility: public — e.g., "Chrome on Windows"
    isCurrent: boolean;         // visibility: public — true if matches current JWT JTI
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_003` | 401 | JWT token expired | `redirect` → login |
  | - | 401 | No/invalid Bearer token | `redirect` → login |

---

### DELETE /api/auth/devices/{sessionId}

> **FR**: FR-007 — Kick (revoke) a specific device session.

- **Auth**: `required` (Bearer JWT)
- **Request**:
  ```typescript
  // Path parameter
  interface KickDeviceRequest {
    sessionId: number;  // visibility: public — path param, snowflake ID
  }
  ```
- **Response (200)**:
  ```typescript
  // ResponseEntity<Map<String, Any?>>
  interface KickDeviceResponse {
    success: boolean;     // visibility: public — always true on 200
    sessionId: number;    // visibility: public — echoed back
    message: string;      // visibility: public — e.g., "Device session revoked"
  }
  ```
- **Revocation Side Effects** (not in response, backend-only):
  - Session record: `revokedAt` set, `revokeReason = "KICKED_BY_USER"` — visibility: internal
  - Access token JTI: blacklisted in 3-tier cache (L1+L2+DB) — visibility: internal
  - Refresh token: revoked — visibility: internal
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_070` | 404 | Device session not found | `toast` |
  | `AUTH_071` | 403 | Device not owned by requesting user | `toast` |
  | `AUTH_003` | 401 | JWT token expired | `redirect` → login |

---

### DELETE /api/auth/devices

> **FR**: FR-008 — Kick all other device sessions except current.

- **Auth**: `required` (Bearer JWT)
- **Request**: No request body.
- **Response (200)**:
  ```typescript
  // ResponseEntity<Map<String, Any?>>
  interface KickAllDevicesResponse {
    success: boolean;       // visibility: public — always true on 200
    revokedCount: number;   // visibility: public — number of sessions revoked (0 if only current)
    message: string;        // visibility: public — e.g., "3 other device sessions revoked"
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_003` | 401 | JWT token expired | `redirect` → login |

---

## Modified Flows (No New Endpoints)

> These flows modify existing endpoints — no new REST paths, but contract changes to existing request/response.

### Login Flow Enhancement

> **Existing endpoint**: `POST /api/auth/login` (handled by `LoginHandler`)
> **FR**: FR-002, FR-005 — Fingerprint embedded in JWT.

- **New Request Header** (optional):
  ```typescript
  // X-Device-Fingerprint: <hex-string-64-chars>
  // If not provided or invalid → server computes fallback fingerprint
  ```
- **Response Change**: **None** — `AuthResponse` unchanged. Fingerprint embedded in JWT `device_fingerprint` claim (internal to token, not in response body).
- **Side Effects** (backend-only):
  - JWT access token now contains `device_fingerprint` claim — visibility: internal
  - `LoginSessionEntity` records `accessTokenJti` and `deviceName` — visibility: internal
  - New device detection → publishes `NewDeviceLoginEvent` → enqueues email — visibility: internal

### Refresh Token Flow Enhancement

> **Existing endpoint**: `POST /api/auth/refresh` (handled by `RefreshTokenHandler`)
> **FR**: FR-011 — Old JTI blacklisted on refresh.

- **New Request Header** (optional):
  ```typescript
  // X-Device-Fingerprint: <hex-string-64-chars>
  // New token inherits fingerprint from current request
  ```
- **Response Change**: **None** — same token pair response format.
- **Side Effects** (backend-only):
  - Old access token JTI blacklisted in 3-tier cache — visibility: internal
  - Session `accessTokenJti` updated to new JTI — visibility: internal
  - Old refresh token revoked — visibility: internal

### JwtAuthFilter Enhancement

> **Not an endpoint** — filter layer validation.
> **FR**: FR-003 — Per-request fingerprint validation.

- **New Request Header** (required when fingerprint enabled):
  ```typescript
  // X-Device-Fingerprint: <hex-string-64-chars>
  // Must match fingerprint claim in JWT, or server-computed fallback is used
  ```
- **New Error Response** (when strict mode enabled):
  | Code | HTTP Status | Description | Frontend Action |
  |------|------------|-------------|-----------------|
  | `AUTH_072` | 401 | Device fingerprint mismatch — possible token replay | `redirect` → login |

---

## Shared Types

### DeviceResponse

```typescript
interface DeviceResponse {
  sessionId: number;          // visibility: public
  deviceType: string | null;  // visibility: public
  browserName: string | null; // visibility: public
  osName: string | null;      // visibility: public
  ipAddress: string;          // visibility: public
  loginAt: string;            // visibility: public — ISO 8601
  lastActivityAt: string | null; // visibility: public — ISO 8601
  deviceName: string | null;  // visibility: public
  isCurrent: boolean;         // visibility: public
}
```

### AuthResponse (Existing — Unchanged)

```typescript
interface AuthResponse {
  accessToken: string;          // visibility: public
  refreshToken: string | null;  // visibility: public
  tokenType: string;            // visibility: public — always "Bearer"
  expiresIn: number;            // visibility: public — seconds
  userId: number;               // visibility: public
  username: string;             // visibility: public
  activeDomain: string;         // visibility: public
  roles: string[];              // visibility: public
  permissions: string[];        // visibility: public
  promotedFromAnonymous: boolean; // visibility: public
  dataTransferred: DataTransferredInfo | null; // visibility: public
  message: string | null;       // visibility: public — optional
}
```

---

## Error Codes (New — JWT Security Redesign)

> These error codes extend `AuthErrorCode` enum range AUTH_070~079.

| Code | HTTP Status | Error Key | Description | Frontend Action |
|------|------------|-----------|-------------|-----------------|
| `AUTH_070` | 404 | `auth.device_not_found` | Device session not found | `toast` — "Phiên đăng nhập không tìm thấy" |
| `AUTH_071` | 403 | `auth.device_not_owned` | Device session not owned by user | `toast` — "Không có quyền đăng xuất thiết bị này" |
| `AUTH_072` | 401 | `auth.fingerprint_mismatch` | Device fingerprint mismatch (strict mode) | `redirect` → login |

---

## State Contracts

### DeviceManagement State (Frontend)

```typescript
interface DeviceManagementState {
  // Data
  devices: DeviceResponse[];
  
  // Loading states
  isLoadingDevices: boolean;
  isKickingDevice: Record<number, boolean>;  // sessionId → loading
  isKickingAll: boolean;
  
  // Error states
  error: string | null;
  
  // Computed
  currentDevice: DeviceResponse | null;      // isCurrent === true
  otherDevices: DeviceResponse[];            // isCurrent === false
  deviceCount: number;
}
```

### DeviceManagement Actions (Frontend)

```typescript
interface DeviceManagementActions {
  fetchDevices: () => Promise<void>;
  kickDevice: (sessionId: number) => Promise<void>;
  kickAllOtherDevices: () => Promise<void>;
}
```

---

## Request Headers Contract

> New headers introduced by JWT Security Redesign.

| Header | Required | Format | Endpoints | Fallback |
|--------|----------|--------|-----------|----------|
| `X-Device-Fingerprint` | Optional | Hex string, 64 chars | ALL authenticated requests | Server-computed SHA-256 |

> **Frontend implementation note**: Client SHOULD compute a device fingerprint (e.g., using FingerprintJS or similar library) and send via `X-Device-Fingerprint` header on every authenticated request. If not sent, server will compute a fallback from User-Agent + Accept-Language + IP-Prefix/24 + SEC-CH-UA.

---

## Contract Rules

1. **Bi-directional sync**: If frontend needs additional fields → update this contract → notify backend
2. **Version bump**: Any breaking change MUST increment `contract-version`
3. **Status tracking**: Each track updates its status header after implementation
4. **Visibility enforcement**: Frontend generators MUST filter out `visibility: internal` fields
5. **Error handling**: Every error code MUST have a corresponding frontend action
6. **No wrapper**: Auth-service returns `ResponseEntity<T>` directly — frontend reads body as-is

---

## Validation Checklist

- [x] Every endpoint in `design.md` has a contract entry (3/3 Device endpoints)
- [x] Modified flows documented (Login, Refresh, JwtAuthFilter)
- [x] All fields have visibility annotation
- [x] Response convention matches backend implementation (no wrapper, direct ResponseEntity)
- [x] Error codes are unique (AUTH_070, AUTH_071, AUTH_072) — no overlap with existing AUTH_001~066
- [x] State contract matches endpoint response shape
- [x] Request headers documented (X-Device-Fingerprint)
- [x] Frontend actions specified for all error codes
