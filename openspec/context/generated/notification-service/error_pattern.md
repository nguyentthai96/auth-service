# Error Handling Pattern

_Generated: 2026-09-11_

## Exception Classes

- `AuthException` — `shared/exception/AuthExceptions.kt` — extends `BusinessException` (base-core)
- `ResourceNotFoundException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `DuplicateResourceException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `PermissionDeniedException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `WriteNotAllowedException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `AccountLockedException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `InvalidCredentialsException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `TokenExpiredException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `PolicyEvaluationException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `EventStorePersistException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `EventNotFoundException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `ServiceTokenInvalidException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `ServiceInsufficientScopeException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`
- `ServiceNotRegisteredException` — `shared/exception/AuthExceptions.kt` — extends `AuthException`

## Error Code Format

- Enum: `AuthErrorCode` — `shared/exception/AuthErrorCode.kt`
- Pattern: `enum class AuthErrorCode(val errorCode: String, val msgCode: String, val description: String)`
- Bridge: `toErrorCodeBase()` → converts to base-core `ErrorCodeBase`
- Examples:
  - `RESOURCE_NOT_FOUND` → errorCode + msgCode + description
  - `DUPLICATE_RESOURCE`
  - `PERMISSION_DENIED`
  - `INVALID_CREDENTIALS`
  - `TOKEN_EXPIRED`
  - `ACCOUNT_LOCKED`
  - `EVENT_STORE_PERSIST_FAILED`
  - `EVENT_NOT_FOUND`
  - `INVALID_SERVICE_TOKEN`
  - `INSUFFICIENT_SCOPE`
  - `SERVICE_NOT_REGISTERED`

## Exception Handler

- `GlobalExceptionHandler` — `shared/exception/GlobalExceptionHandler.kt` — extends `BaseControllerAdvice` (base-core)
- Pattern: `@ControllerAdvice` + `@ExceptionHandler`
- Response format: RFC 7807 ProblemDetail (via base-core)

## Error Pattern Summary

- **Hierarchy**: `BusinessException` (base-core) → `AuthException` → specific exceptions
- **Error codes**: Enum-based (`AuthErrorCode`) with bridge to `ErrorCodeBase`
- **Convention for new service**: Create `NotificationException` → extends `BusinessException`, `NotificationErrorCode` enum

## NOT DETECTED

- Custom error response wrapper (uses base-core standard)
