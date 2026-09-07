# Error Handling Pattern

_Generated: 2026-09-07_

## Exception Classes

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - Pattern: `@RestControllerAdvice` + `@ExceptionHandler`
  - Returns RFC 7807 ProblemDetail format

- `AuthErrorCode` (enum) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - Pattern: Centralized error code enum

- `AuthExceptions` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Contains: Multiple exception classes for auth domain

- `AuthCoreExceptions` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - Contains: Core auth exceptions (InvalidCredentialsException, etc.)

- `AnonymousExceptions` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - Contains: Anonymous session specific exceptions

- `CipherExceptions` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt`
  - Contains: Encryption/decryption exceptions

- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/` (referenced in JwtService)
  - Usage: Thrown when JWT token is expired

- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/` (referenced in SessionController)
  - Usage: Thrown when authentication fails or user ID cannot be extracted

## Error Code Format

- Pattern: `AuthErrorCode` enum with string constants
- Example: `AuthErrorCode.INVALID_CREDENTIALS`, `AuthErrorCode.TOKEN_EXPIRED`

## Error Response Format

- Pattern: RFC 7807 ProblemDetail (Spring 6.x native)
- Handler: `GlobalExceptionHandler` (`@RestControllerAdvice`)
- Response structure:
  ```json
  {
    "type": "about:blank",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Token has expired",
    "instance": "/api/auth/refresh"
  }
  ```

## NOT DETECTED

- Custom `ErrorResponse` wrapper class (uses Spring's ProblemDetail directly)
- Numeric error codes (uses string-based enum)
