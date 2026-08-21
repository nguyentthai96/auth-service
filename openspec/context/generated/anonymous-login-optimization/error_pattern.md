# Error Handling Pattern

_Generated: 2025-07-15_

## Exception Hierarchy

```
AuthException (abstract, extends BusinessException from base-core)
├── AnonymousSessionExpiredException  (AUTH_040, 404 NOT_FOUND)
├── AnonymousDataLimitExceededException  (AUTH_041, 413 PAYLOAD_TOO_LARGE)
├── AnonymousPromotionConflictException  (AUTH_042, 409 CONFLICT)
├── AnonymousRateLimitedException  (AUTH_043, 429 TOO_MANY_REQUESTS)
├── AnonymousMaxRenewalsException  (AUTH_044, 429 TOO_MANY_REQUESTS)
├── MfaCodeInvalidException  (AUTH_020)
├── MfaTokenExpiredException  (AUTH_021)
├── MfaMaxAttemptsException  (AUTH_022)
├── MfaAccountLockedException  (AUTH_025)
├── RateLimitExceededException  (AUTH_012)
├── AccountLockedException  (AUTH_013)
├── SessionLimitExceededException  (AUTH_011)
├── TokenExpiredException  (AUTH_003)
└── ...other AuthException subclasses
```

## Anonymous-Specific Exception Classes

- `AnonymousSessionExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` line 11 — extends `AuthException`, code AUTH_040, HTTP 404. Params: `sessionId: String`.
- `AnonymousDataLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` line 19 — extends `AuthException`, code AUTH_041, HTTP 413. Params: `currentSize: Long`, `maxSize: Long`.
- `AnonymousPromotionConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` line 28 — extends `AuthException`, code AUTH_042, HTTP 409. Params: `sessionId: String`.
- `AnonymousRateLimitedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` line 36 — extends `AuthException`, code AUTH_043, HTTP 429. Params: `retryAfterSeconds: Long`.
- `AnonymousMaxRenewalsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` line 44 — extends `AuthException`, code AUTH_044, HTTP 429. Params: `maxRenewals: Int`.

## Error Code Format

- Pattern: `AUTH_XXX` — String enum values in `AuthErrorCode`
- Example constants:
  - `AUTH_040` — `ANONYMOUS_SESSION_EXPIRED` — "Anonymous session expired or not found"
  - `AUTH_041` — `ANONYMOUS_DATA_LIMIT_EXCEEDED` — "Anonymous session data limit exceeded"
  - `AUTH_042` — `ANONYMOUS_PROMOTION_CONFLICT` — "Anonymous session promotion conflict"
  - `AUTH_043` — `ANONYMOUS_RATE_LIMITED` — "Anonymous token creation rate limited"
  - `AUTH_044` — `ANONYMOUS_MAX_RENEWALS` — "Anonymous token maximum renewals exceeded"
- File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` lines 50-55

## Error Code Enum Structure

- `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` — enum with fields: `code: String`, `messageKey: String`, `defaultMessage: String`, `httpStatus: HttpStatus`
- i18n message keys follow pattern: `auth.anonymous_*` (e.g., `auth.anonymous_session_expired`)
- Each error code maps to a specific HTTP status code

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` — `@ControllerAdvice`, extends `BaseControllerAdvice` (base-core)
  - Handles `AuthException` → RFC 7807 ProblemDetail with correct HTTP status
  - Special handling for anonymous exceptions at lines 76-82:
    - `AnonymousRateLimitedException` → HTTP 429 + Retry-After header
    - `AnonymousMaxRenewalsException` → HTTP 429
    - `AnonymousDataLimitExceededException` → HTTP 413
  - `extractMessageArgs()` at line 112 extracts params for i18n message formatting:
    - `AnonymousRateLimitedException` → `[retryAfterSeconds]`
    - `AnonymousMaxRenewalsException` → `[maxRenewals]`
    - `AnonymousDataLimitExceededException` → `[currentSize, maxSize]`

## Error Handling Strategy in Anonymous Services

| Service | Error | Handling |
|---------|-------|----------|
| `AnonymousRateLimitService` | Redis unavailable | **Fail-open**: catch `RedisConnectionFailureException`, log error, allow request through |
| `AnonymousRateLimitService` | Rate limit exceeded | Throw `AnonymousRateLimitedException` with `retryAfterSeconds` |
| `AnonymousSessionDataService` | Session not found | Throw `AnonymousSessionExpiredException` |
| `AnonymousSessionDataService` | Data size exceeded | Throw `AnonymousDataLimitExceededException` |
| `AnonymousSessionDataService` | Transfer error | Catch exception, log, return `DataTransferResult(partial=true)` |
| `SessionPromotionService` | Lock acquisition failed | Return `PromotionResult(CONFLICT)` |
| `SessionPromotionService` | Session not found | Return `PromotionResult(FAILED)` |
| `SessionPromotionService` | Blacklist/cleanup error | Catch, log warning, continue — best-effort |
| `RenewAnonymousTokenHandler` | Session expired | Throw `AnonymousSessionExpiredException` |
| `RenewAnonymousTokenHandler` | Max renewals reached | Throw `AnonymousMaxRenewalsException` |

## NOT DETECTED

- Custom error response DTOs (uses RFC 7807 ProblemDetail from base-core)
- Error code ranges beyond AUTH_040-044 for anonymous features
