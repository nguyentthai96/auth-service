# Error Handling Pattern

_Generated: 2026-08-26_

## Exception Classes

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - properties: `authError: AuthErrorCode`, `message: String`, `httpStatus: HttpStatus`
  - role: Base exception for all auth service errors

- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - errorCode: AUTH_003
  - httpStatus: 401 UNAUTHORIZED
  - message: "JWT token has expired"

- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - errorCode: AUTH_001
  - httpStatus: 401 UNAUTHORIZED

- `PermissionDeniedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - errorCode: AUTH_004
  - httpStatus: 403 FORBIDDEN

- `ServiceTokenInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - errorCode: AUTH_060
  - httpStatus: 401 UNAUTHORIZED
  - message: "Invalid or expired service authentication token"

- `EventStorePersistException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - errorCode: AUTH_050
  - httpStatus: 500 INTERNAL_SERVER_ERROR

- Additional exceptions in `AnonymousExceptions.kt`, `AuthCoreExceptions.kt`, `CipherExceptions.kt`

## Error Code Format

- Pattern: `AUTH_XXX` — 3-digit numeric suffix
- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Bridge: Implements `ErrorCodeBase` (base-core) via delegation
- Properties per code: `errorCode` (String), `msgCode` (String, i18n key), `description` (String), `httpStatus` (HttpStatus)

### Error Code Ranges

| Range | Domain | Examples |
|-------|--------|---------|
| AUTH_001-009 | Core Authentication | INVALID_CREDENTIALS, ACCOUNT_LOCKED, TOKEN_EXPIRED, PERMISSION_DENIED |
| AUTH_010-019 | MFA & CAPTCHA | MFA_CODE_INVALID, MFA_TOKEN_EXPIRED, MFA_MAX_ATTEMPTS |
| AUTH_014-016 | SSO | SSO_TOKEN_INVALID, SSO_USER_NOT_PROVISIONED, SSO_IDENTITY_CONFLICT |
| AUTH_017-018 | Password Policy | PASSWORD_POLICY_VIOLATION, PASSWORD_EXPIRED |
| AUTH_019-021 | Rate Limiting & Sessions | MFA_RATE_LIMITED, RATE_LIMITED, SESSION_LIMIT_EXCEEDED |
| AUTH_030-039 | E2EE | E2EE_TIME_SKEW, E2EE_VERSION_UNKNOWN, etc. |
| AUTH_040-044 | Anonymous Sessions | ANONYMOUS_SESSION_EXPIRED, ANONYMOUS_DATA_LIMIT_EXCEEDED |
| AUTH_050-053 | Event Sourcing | EVENT_STORE_PERSIST_FAILED, OUTBOX_PUBLISH_FAILED |
| AUTH_060-062 | Inter-service Auth | INVALID_SERVICE_TOKEN, INSUFFICIENT_SCOPE, SERVICE_NOT_REGISTERED |

## Error Response Format

- Format: RFC 7807 ProblemDetail
- Handler: `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - Priority: AuthException handler overrides base BusinessException handler
  - Properties: `type` (URI), `title` (errorCode), `detail` (i18n message), `errorCode`, plus type-specific extras
  - I18n: Uses `MessageSource` with `AuthErrorCode.msgCode` as key
  - Extra properties: `lockedUntilAt` (AccountLockedException), `retryAfterSeconds` (RateLimited), `maxSessions` (SessionLimit)
  - Retry-After header: Set for rate-limited responses (RFC 6585)

## JWT Validation Error Handling (JwtAuthFilter)

- **JwtAuthFilter** error handling pattern (L86-91):
  - Catch `Exception` → log debug → continue filter chain without authentication
  - DOES NOT throw — fail-open for filter, endpoint security decides (401/403)
  - Blacklisted token → return 401 directly via `response.status = SC_UNAUTHORIZED` (L49-51)
  - JwtService.parseToken() throws: `TokenExpiredException` (expired), `IllegalStateException` (no key), `JwtException` (invalid)

## NOT DETECTED

- Custom error response wrapper (uses RFC 7807 ProblemDetail directly)
- Error code reservation for JWT validation-specific failures (e.g., blacklisted, audience mismatch) — ⚠️ may need new codes
