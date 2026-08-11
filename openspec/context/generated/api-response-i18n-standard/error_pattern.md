# Error Handling Pattern

_Generated: 2026-08-11_

## Exception Classes

- `AuthException` — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - base class for all auth exceptions
  - carries: `authError: AuthErrorCode`, `httpStatus: HttpStatus`
- `InvalidCredentialsException` — same file
- `AccountLockedException` — same file (extra: `lockedUntilAt`)
- `RateLimitExceededException` — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extra: `retryAfterSeconds`, `dimension`
- `SessionLimitExceededException` — same file
  - extra: `maxSessions`, `activeCount`
- `CaptchaRequiredException` — same file
- `CaptchaFailedException` — same file
- `MfaAccountLockedException` — same file
  - extra: `retryAfterSeconds`, `lockType`
- `BusinessException` — `base-core/src/main/kotlin/com/ntt/basecore/exception/BusinessException.kt`
  - base class for business errors in base-core
- `NotFoundException` — `base-core/src/main/kotlin/com/ntt/basecore/exception/NotFoundException.kt`

## Error Code Format

### base-core

- Pattern: `ErrorCodeBase(code, msgCode, description)`
- `ErrorService` interface: `getCode()`, `getMsgCode()`, `getDesc()`, `withArgs()`
- `ApiErrorCode` enum — `base-core/src/main/kotlin/com/ntt/basecore/exception/error/ApiErrorCode.kt`
  - examples: `BAD_REQUEST`, `NOT_FOUND`, `VALIDATION_ERROR`, `SYSTEM_ERROR`, `FORBIDDEN`

### auth-service

- Pattern: `AuthErrorCode(errorCode, msgCode, description, httpStatus)`
- `AuthErrorCode` enum — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - 21 error codes: `AUTH_001` → `AUTH_021`
  - `msgCode` convention: `auth.<snake_case_name>` (e.g., `auth.rate_limited`, `auth.session_limit`)
  - `description`: English hardcoded string (current — to be replaced by MessageSource lookup)

## Error Response Format

### base-core (ApiResponse)
```json
{
  "code": "400",
  "msgCode": "VALIDATION_ERROR",
  "message": "English hardcoded message",
  "timestamp": 1723372800000
}
```

### auth-service (ProblemDetail — RFC 9457)
```json
{
  "type": "https://auth-service/errors/rate_limited",
  "title": "AUTH_020",
  "status": 429,
  "detail": "Too many login attempts",
  "errorCode": "AUTH_020",
  "retryAfterSeconds": 60,
  "dimension": "IP"
}
```

## Error Handler Chain

1. `AuthException` → `AuthControllerAdvice.handleAuthException()` → `ProblemDetail`
2. `BusinessException` → `BaseControllerAdvice.handleBusinessException()` → `ApiResponse<Unit>`
3. `NotFoundException` → `BaseControllerAdvice.handleNotFound()` → `ApiResponse<Unit>`
4. `Throwable` → `BaseControllerAdvice.handleException()` → `ApiResponse<Unit>`

## NOT DETECTED

- i18n message resolution in error handlers — NOT DETECTED (to be added)
- `Content-Language` response header — NOT DETECTED (to be added)
