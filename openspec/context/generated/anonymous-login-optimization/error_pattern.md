# Error Handling Pattern

_Generated: 2025-01-20_

## Exception Base Class

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - `open class AuthException(authError: AuthErrorCode, message: String, httpStatus: HttpStatus)`
  - All feature exceptions extend `AuthException`
  - Handled by `GlobalExceptionHandler` via `@RestControllerAdvice`

## Anonymous Feature Exception Classes

- `AnonymousSessionExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Error code: `AUTH_040` (`ANONYMOUS_SESSION_EXPIRED`)
  - HTTP status: `404 NOT_FOUND`
  - Message: `"Anonymous session expired or not found: {sessionId}"`
  - Thrown by: `AnonymousSessionDataService.storeData/getData/deleteData`, `RenewAnonymousTokenHandler.handle`

- `AnonymousDataLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Error code: `AUTH_041` (`ANONYMOUS_DATA_LIMIT_EXCEEDED`)
  - HTTP status: `413 PAYLOAD_TOO_LARGE`
  - Fields: `currentSize: Long`, `maxSize: Long`
  - Message: `"Anonymous session data limit exceeded: current={currentSize} bytes, max={maxSize} bytes"`
  - Thrown by: `AnonymousSessionDataService.storeData`

- `AnonymousPromotionConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Error code: `AUTH_042` (`ANONYMOUS_PROMOTION_CONFLICT`)
  - HTTP status: `409 CONFLICT`
  - Message: `"Anonymous session promotion conflict — session {sessionId} is being promoted by another request"`
  - Note: Currently `SessionPromotionService` returns `PromotionResult.Status.CONFLICT` instead of throwing this exception — exception available for direct API callers if needed

- `AnonymousRateLimitedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Error code: `AUTH_043` (`ANONYMOUS_RATE_LIMITED`)
  - HTTP status: `429 TOO_MANY_REQUESTS`
  - Fields: `retryAfterSeconds: Long`
  - Message: `"Anonymous token creation rate limited — retry after {retryAfterSeconds} seconds"`
  - Thrown by: `AnonymousRateLimitService.checkRateLimit`

- `AnonymousMaxRenewalsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Error code: `AUTH_044` (`ANONYMOUS_MAX_RENEWALS`)
  - HTTP status: `429 TOO_MANY_REQUESTS`
  - Fields: `maxRenewals: Int`
  - Message: `"Anonymous token maximum renewals exceeded: max={maxRenewals}"`
  - Thrown by: `RenewAnonymousTokenHandler.handle`

## Error Code Format

- Pattern: `AUTH_XXX` — string enum
- Implementation: `AuthErrorCode` enum — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Bridge: `toErrorCodeBase(): ErrorCodeBase` — bridges to base-core `ErrorCodeBase` via delegation
- Fields per code: `errorCode: String`, `msgCode: String`, `description: String`, `httpStatus: HttpStatus`

### Anonymous Error Code Range

| Code | Name | HTTP Status | i18n Key |
|------|------|-------------|----------|
| `AUTH_040` | `ANONYMOUS_SESSION_EXPIRED` | 404 | `auth.anonymous_session_expired` |
| `AUTH_041` | `ANONYMOUS_DATA_LIMIT_EXCEEDED` | 413 | `auth.anonymous_data_limit_exceeded` |
| `AUTH_042` | `ANONYMOUS_PROMOTION_CONFLICT` | 409 | `auth.anonymous_promotion_conflict` |
| `AUTH_043` | `ANONYMOUS_RATE_LIMITED` | 429 | `auth.anonymous_rate_limited` |
| `AUTH_044` | `ANONYMOUS_MAX_RENEWALS` | 429 | `auth.anonymous_max_renewals` |

### Other Error Code Ranges (context)

| Range | Domain |
|-------|--------|
| `AUTH_001~021` | Core auth errors (credentials, locks, tokens, permissions, MFA, SSO, rate limits, sessions) |
| `AUTH_030~039` | E2EE cipher errors |
| `AUTH_040~044` | Anonymous session errors |

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@RestControllerAdvice` — catches all `AuthException` subtypes
  - Anonymous-specific handling:
    - `AnonymousRateLimitedException` → adds `Retry-After` header (line 76)
    - `AnonymousMaxRenewalsException` → adds `Retry-After` header (line 79)
    - `AnonymousDataLimitExceededException` → payload too large response (line 82)
  - Response format: RFC 7807 ProblemDetail (via base-core)
  - i18n message args:
    - `AnonymousRateLimitedException` → `[retryAfterSeconds]` (line 117)
    - `AnonymousMaxRenewalsException` → `[maxRenewals]` (line 118)
    - `AnonymousDataLimitExceededException` → `[currentSize, maxSize]` (line 119)

## Error Handling Strategy

- **Fail-open for rate limiting**: `AnonymousRateLimitService` catches `RedisConnectionFailureException` and allows request through when Redis is unavailable (line 64-67)
- **Best-effort for promotion**: `LoginHandler` and `RegisterHandler` catch all exceptions during promotion and return `PromotionResult(Status.FAILED)` — login/register succeeds regardless (DD-007)
- **Best-effort for blacklisting**: `RenewAnonymousTokenHandler` catches exceptions during token blacklisting and logs warning — renewal succeeds regardless (line 74-77)
- **Best-effort for cleanup**: `SessionPromotionService` catches exceptions during session deletion and logs warning — promotion succeeds regardless (line 94-97)

## NOT DETECTED

- No custom error response DTO (uses base-core RFC 7807 ProblemDetail)
- No error code constants outside `AuthErrorCode` enum
