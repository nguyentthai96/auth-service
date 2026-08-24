# Error Handling Pattern

_Generated: 2026-08-25_

## Exception Classes

### Base Exception Hierarchy
- `AuthException` (base) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - role: Base exception for all auth-service specific errors

### Auth Core Exceptions
- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `AccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `PermissionDeniedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `RateLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
- `SessionLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`

### MFA Exceptions
- `MfaCodeInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
- `MfaTokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
- `MfaAccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
- `MfaRateLimitedException` (inferred from handler) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`

### Anonymous Session Exceptions
- `AnonymousSessionExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - extends: `AuthException`
- `AnonymousDataLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - extends: `AuthException`
- `AnonymousPromotionConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - extends: `AuthException`
- `AnonymousRateLimitedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - extends: `AuthException`
- `AnonymousMaxRenewalsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt`
  - extends: `AuthException`

### E2EE / Cipher Exceptions
- `CipherExceptions` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt`
  - Multiple exception classes extending `AuthException` (time skew, version unknown, replay, decrypt failed, KMS unavailable, etc.)

### Exception Handler
- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - annotation: `@RestControllerAdvice`
  - handles: `AuthException` → ProblemDetail (RFC 7807) with correct HTTP status
  - i18n: Resolves error messages via `MessageSource` using `AuthErrorCode.msgCode`
  - message args extraction for RateLimitExceededException, SessionLimitExceededException, MfaAccountLockedException, AnonymousRateLimitedException, AnonymousMaxRenewalsException, AnonymousDataLimitExceededException

## Error Code Format

- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - implements: `ErrorCodeBase` (base-core) via delegation
  - Pattern: `AUTH_XXX` where XXX is 3-digit number
  - Examples:
    - `AUTH_001` — Invalid credentials
    - `AUTH_002` — Account locked
    - `AUTH_003` — Token expired
    - `AUTH_011` — MFA code invalid
    - `AUTH_020` — Rate limited
    - `AUTH_030~039` — E2EE errors
    - `AUTH_040~044` — Anonymous session errors
    - `AUTH_050~053` — Event sourcing errors
    - `AUTH_060~062` — Inter-service auth errors
  - Each code carries: errorCode (String), msgCode (i18n key), description, httpStatus
  - Total: 30+ error codes defined

## Error Response Format

- RFC 7807 `ProblemDetail` — via Spring Framework built-in
- Bridge: `AuthException` → `BusinessException` (base-core) for cross-service consistency
- Priority chain: AuthException handler (most specific) → BusinessException handler (base-core) → Throwable handler (base-core fallback)
- Content-Language header set on all error responses

## NOT DETECTED

- Custom error response wrapper — uses standard Spring `ProblemDetail` and base-core `ApiResponse`
- Error code constants file separate from enum — all in `AuthErrorCode` enum
