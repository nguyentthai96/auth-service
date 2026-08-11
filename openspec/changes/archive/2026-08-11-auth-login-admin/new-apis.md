# New APIs — auth-login-admin

## Endpoints Added

| Path | Method | Purpose | Auth |
|------|--------|---------|------|
| `/api/captcha/challenge` | GET | Generate ALTCHA PoW challenge | Public |
| `/api/auth/logout` | POST | Logout — revoke sessions + clear cookie | Authenticated |
| `/api/auth/sessions` | GET | List active sessions for current user | Authenticated |
| `/api/auth/sessions/{sessionId}` | DELETE | Revoke specific session | Authenticated |
| `/api/auth/sessions` | DELETE | Revoke all sessions | Authenticated |

## Modified Endpoints

| Path | Method | Changes |
|------|--------|---------|
| `/api/auth/login` | POST | Added deviceFingerprint field, rate limiting, session recording, HttpOnly refresh cookie |
| `/api/auth/refresh` | POST | Changed from body to HttpOnly cookie extraction |

## Request/Response Examples

### GET /api/captcha/challenge
```json
// Response
{
  "algorithm": "SHA-256",
  "challenge": "abc123...",
  "salt": "uuid-salt",
  "signature": "hmac-sig",
  "maxnumber": 50000
}
```

### POST /api/auth/login
```json
// Request headers:
// X-Device-Fingerprint: sha256-hash
// Request body:
{
  "username": "admin",
  "password": "***",
  "domainCode": "default",
  "captchaToken": "base64-altcha-payload"
}

// Response (success):
{
  "accessToken": "jwt...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": 123,
  "username": "admin",
  "activeDomain": "default",
  "roles": ["ADMIN"],
  "permissions": ["user:read", "user:write"]
}
// Note: refreshToken in HttpOnly cookie, NOT in response body
```

### GET /api/auth/sessions
```json
// Response
[
  {
    "id": 123456,
    "ipAddress": "192.168.1.1",
    "deviceType": "DESKTOP",
    "browserName": "Chrome",
    "osName": "macOS",
    "loginAt": "2026-08-11T10:00:00Z",
    "lastActivityAt": "2026-08-11T10:15:00Z",
    "isNewDevice": false
  }
]
```
