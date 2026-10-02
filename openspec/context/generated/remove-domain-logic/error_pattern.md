# Error Handling Pattern

_Generated: 2026-10-02_

## Exception Classes

### Base Exception
- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: RuntimeException (implied)
  - pattern: All auth-service exceptions inherit from this

### Auth Exceptions (AuthExceptions.kt)
- `ResourceNotFoundException` — 404 — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
- `DuplicateResourceException` — 409 — same file
- `InvalidCredentialsException` — 401 — same file
- `TokenExpiredException` — 401 — same file
- `PasswordPolicyViolationException` — 400 — same file
- `PasswordRecentlyUsedException` — 400 — same file
- `UnlockTokenExpiredException` — 401 — same file
- `UnlockTokenUsedException` — 409 — same file
- `UnlockNotAllowedException` — 403 — same file

### Cipher Exceptions (CipherExceptions.kt)
- Multiple cipher-related exceptions — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt`
  - All extend `AuthException`

### Auth Core Exceptions (AuthCoreExceptions.kt)
- Multiple core auth exceptions — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - All extend `AuthException`

### Anonymous Exceptions (AnonymousExceptions.kt)
- Anonymous session exceptions — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - All extend `AuthException`

## Error Response Format

- Handler: `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - pattern: Bridge `AuthException` → `BusinessException` for cross-service consistency
  - format: RFC 7807 ProblemDetail (via base-core)

## Error Code Format

- Pattern: Exception-class-based (not numeric error codes)
- Message resolution: `MessageSource` (i18n) with `extractMessageArgs()`
- Response: `ApiResponse<T>` error format from base-core

## NOT DETECTED

- Numeric error code constants (uses exception class names instead)
- Domain-specific error codes for domain operations (will be removed)
