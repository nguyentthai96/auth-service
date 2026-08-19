# Delta Spec: anonymous-login-optimization

_Generated: 2025-01-20_

## Summary

This EXTEND feature adds anonymous/guest session support to the auth-service. All changes are backward compatible — existing APIs continue to work identically without `anonymousSessionId`.

## Behavioral Changes

### Login Flow (POST /api/auth/login)
| Aspect | Before | After |
|--------|--------|-------|
| Request body | No `anonymousSessionId` field | Optional `anonymousSessionId: String?` field accepted |
| Response body | `{ accessToken, tokenType, expiresIn, userId, ... }` | Same + optional `promotedFromAnonymous: Boolean`, `dataTransferred: { itemCount, namespaces, status }` |
| Session promotion | N/A | If `anonymousSessionId` provided, best-effort data transfer from Redis anonymous session to user namespace |
| Login failure | No change | No change — promotion only runs on successful login |

### Register Flow (POST /api/auth/register)
| Aspect | Before | After |
|--------|--------|-------|
| Request body | No `anonymousSessionId` field | Optional `anonymousSessionId: String?` field accepted |
| Response body | `{ accessToken, tokenType, ... }` | Same + optional promotion metadata |

### JwtAuthFilter
| Aspect | Before | After |
|--------|--------|-------|
| Token type handling | Only authenticated tokens (roles/permissions) | Anonymous tokens (`type=anonymous`) → `ROLE_ANONYMOUS` authority |
| SecurityContext details | `activeDomain`, `domains`, `username` | Same for auth tokens + `type`, `sessionId` for anonymous tokens |

### SecurityConfig
| Aspect | Before | After |
|--------|--------|-------|
| Public paths | `/api/auth/login`, `/api/auth/register`, etc. | Same + `/api/v1/auth/anonymous` (create session) |
| Anonymous-auth paths | N/A | `/api/v1/auth/anonymous/**` requires `ROLE_ANONYMOUS` |

### JwtService
| Aspect | Before | After |
|--------|--------|-------|
| Token types | access, refresh, mfa | Same + anonymous (type=anonymous, sub=sessionId) |
| Methods | `generateAccessToken()`, `generateRefreshToken()`, `generateMfaToken()`, `parseMfaToken()` | Same + `generateAnonymousToken()`, `parseAnonymousToken()` |

## Redis Key Additions

| Namespace | Pattern | TTL | Purpose |
|-----------|---------|-----|---------|
| `anon:session:` | `anon:session:{sessionId}` | 24h (configurable) | Session metadata (Hash) |
| `anon:data:` | `anon:data:{sessionId}:{namespace}:{key}` | Matches session TTL | Session data (String) |
| `anon:lock:` | `anon:lock:{sessionId}` | 30s | Promotion distributed lock |
| `anon:rate:` | `anon:rate:{ipAddress}` | 1h (configurable) | IP rate limit counter |
| `user:session_data:` | `user:session_data:{userId}:{namespace}:{key}` | 7d (configurable) | Promoted data destination |

## Database Impact

No new tables or migrations. Reuses existing `token_blacklist` table with new `reason` values: `PROMOTION`, `RENEWAL`.

## Backward Compatibility

✅ All changes are **fully backward compatible**:
- New fields in DTOs have default values (`null`, `false`)
- Login/register work identically without `anonymousSessionId`
- Existing JWT tokens parse unchanged (no `type` claim → authenticated flow)
- No database migrations required
