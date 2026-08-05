# Error Handling Pattern

_Generated: 2026-08-05_

## Exception Classes

### Base Exception (auth-service)

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `RuntimeException` (⚠️ should extend BusinessException from base-core)
  - Properties: `status: HttpStatus`, `detail: String`, `errorCode: String`

### Auth Exceptions

- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `AccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `ResourceNotFoundException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `DuplicateResourceException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `PolicyEvaluationException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`

### Auth Core Exceptions (domain-specific)

- `MfaCodeInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `MfaTokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `MfaMaxAttemptsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `TotpNotSetupException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `CaptchaRequiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `CaptchaFailedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `SsoTokenInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `SsoUserNotProvisionedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `SsoIdentityConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `CannotUnlinkLastIdentityException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `SsoProviderTimeoutException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `PasswordRecentlyUsedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `PasswordExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
- `PasswordPolicyViolationException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`

All extend `AuthException` → should migrate to extend `BusinessException` (base-core)

### Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@ControllerAdvice` + `@ExceptionHandler`
  - Handles: `AuthException`, `ResourceNotFoundException`, `DuplicateResourceException`, `PolicyEvaluationException`, `AccountLockedException`, `MethodArgumentNotValidException`, `ConstraintViolationException`
  - Returns: RFC 7807 `ProblemDetail`
  - ⚠️ Does NOT extend `BaseControllerAdvice` from base-core

### Exception Handler (base-core)

- `BaseControllerAdvice` — `components/base-core/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
  - Handles: `BusinessException`, `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpRequestMethodNotSupportedException`, `MissingServletRequestParameterException`, `TypeMismatchException`
  - Returns: RFC 7807 `ProblemDetail`
  - 119 lines, production-ready
  - NOT USED by auth-service ⚠️

## Error Code Format

- Pattern: HTTP Status code-based (embedded in `AuthException.status`)
- Example: `HttpStatus.UNAUTHORIZED` (401), `HttpStatus.FORBIDDEN` (403), `HttpStatus.CONFLICT` (409)
- No separate error code constants file detected

## NOT DETECTED

- Error code enum/constants file
- Error code prefix pattern (e.g., `AUTH_001`, `RBAC_002`)
- `@ResponseStatus` annotations on exception classes
- Custom error response wrapper class
