# Error Handling Pattern

_Generated: 2025-08-22_

## Exception Classes (Relevant to Registration Flow)

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Base exception class for all auth exceptions
  - Constructor: `(errorCode: AuthErrorCode, message: String, httpStatus: HttpStatus)`

- `DuplicateResourceException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - Used when: username or email already exists
  - Constructor: `(resourceType: String, fieldName: String, fieldValue: String)`
  - Thrown by: `RegisterHandler` → `userPort.existsByUsername()`, `userPort.existsByEmail()`
  - Maps to: `RegistrationFailureReason.DUPLICATE_USERNAME` or `DUPLICATE_EMAIL`

- `ResourceNotFoundException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - Used when: domain code not found
  - Constructor: `(resourceType: String, identifier: String)`
  - Thrown by: `RegisterHandler` → `domainPort.findByCodeAndActive()` returns null
  - Maps to: `RegistrationFailureReason.INVALID_DOMAIN`

- `EventStorePersistException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - Used when: event store persistence fails
  - Relevant for: EventService.record() failure handling

- `EventNotFoundException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - Used when: event not found in store

- `PasswordPolicyViolationException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
  - Used when: password does not meet policy requirements
  - Maps to: `RegistrationFailureReason.WEAK_PASSWORD`

## Error Code Format

- Pattern: `AuthErrorCode` enum — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Format: `AUTH_XXX` (3-digit numeric code)
- Enum properties: `code: String`, `defaultMessage: String`, `httpStatus: HttpStatus`
- Examples:
  - `AUTH_001` — Invalid credentials
  - `AUTH_002` — Account locked
  - `AUTH_003` — Token expired
  - `AUTH_010` — Resource not found
  - `AUTH_011` — Duplicate resource
  - `AUTH_020` — Permission denied

## Exception Handler

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@ControllerAdvice`
  - Handles `AuthException` → returns `ProblemDetail` (RFC 7807)
  - Maps `AuthErrorCode.httpStatus` → HTTP response status

## Fire-and-Forget Error Handling Pattern (Event Recorders)

- **Pattern**: All event recorders wrap `EventService.record()` in try/catch
- **On success**: `log.debug("Event recorded: ...")`
- **On failure**: `log.warn("Failed to record event: ...", e.message, e)` — exception swallowed
- **Examples**:
  - `LoginEventRecorder.recordLoginSuccess()` — try/catch, log.debug/log.warn
  - `LoginEventRecorder.recordLoginFailure()` — try/catch, log.debug/log.warn
  - `TokenEventRecorder.recordIssuance()` — try/catch, log.debug/log.warn
  - `TokenEventRecorder.recordRevocation()` — try/catch, log.debug/log.warn
  - `TokenEventRecorder.recordValidationFailure()` — try/catch, log.debug/log.warn

## Failure Reason Enum Pattern

- `LoginFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt`
  - 9 values with KDoc per value
  - Used in: `UserLoginFailedEvent.failureReason`
  - Mapped by: `LoginHandler.mapToFailureReason(exception: AuthException)` — `when (exception)` pattern

- `ValidationFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`
  - Used in: `TokenValidationFailedEvent`

## NOT DETECTED

- Custom error response DTOs (uses ProblemDetail from Spring Framework)
- Error code prefix patterns beyond AUTH_XXX
