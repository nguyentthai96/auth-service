# New APIs: anonymous-login-optimization

_Generated: 2025-01-20_

## Anonymous Session Endpoints

| # | Path | Method | Auth | Request | Response | Purpose |
|---|------|--------|------|---------|----------|---------|
| 1 | `/api/v1/auth/anonymous` | POST | None (public) | `{ deviceFingerprint?: string }` | `201 { token, sessionId, expiresIn, tokenType }` | Create anonymous session with JWT token |
| 2 | `/api/v1/auth/anonymous/renew` | POST | Bearer (anonymous) | None (token from header) | `200 { token, sessionId, expiresIn, tokenType }` | Renew anonymous token, blacklist old JTI |
| 3 | `/api/v1/auth/anonymous/session/data` | PUT | Bearer (anonymous) | `{ namespace, key, value }` | `204 No Content` | Store key-value data in session namespace |
| 4 | `/api/v1/auth/anonymous/session/data` | GET | Bearer (anonymous) | `?namespace={ns}&key={key}` | `200 { namespace, key, value }` | Retrieve session data by namespace/key |
| 5 | `/api/v1/auth/anonymous/session/data` | DELETE | Bearer (anonymous) | `?namespace={ns}&key={key}` | `204 No Content` | Delete session data by namespace/key |

## Modified Endpoints

| # | Path | Method | Change | Purpose |
|---|------|--------|--------|---------|
| 6 | `/api/auth/login` | POST | Added `anonymousSessionId` optional field in request body | Support session promotion on login |
| 7 | `/api/auth/register` | POST | Added `anonymousSessionId` optional field in request body | Support session promotion on register |
| 8 | `/api/auth/login` | POST | Added `promotedFromAnonymous`, `dataTransferred` in success response | Return promotion metadata |
| 9 | `/api/auth/register` | POST | Added `promotedFromAnonymous`, `dataTransferred` in success response | Return promotion metadata |

## Error Responses

| Code | Error | HTTP Status | When |
|------|-------|-------------|------|
| AUTH_040 | ANONYMOUS_SESSION_EXPIRED | 404 | Session not found in Redis |
| AUTH_041 | ANONYMOUS_DATA_LIMIT_EXCEEDED | 413 | Data exceeds 64KB per session |
| AUTH_042 | ANONYMOUS_PROMOTION_CONFLICT | 409 | Concurrent promotion attempt |
| AUTH_043 | ANONYMOUS_RATE_LIMITED | 429 | IP exceeds 5 tokens/hour |
| AUTH_044 | ANONYMOUS_MAX_RENEWALS | 429 | Token renewed > 24 times |
