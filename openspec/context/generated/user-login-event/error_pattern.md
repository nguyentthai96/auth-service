# Error Handling Pattern

_Generated: 2025-08-22_

## Exception Classes (Login Flow)

- `AuthException` (base) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (from `com.ntt.basecore.exception.BusinessException`)
  - properties: authError (AuthErrorCode), message, httpStatus
  - note: Base exception for all auth service errors. Bridge to base-core.

- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.INVALID_CREDENTIALS` (AUTH_001)
  - httpStatus: 401 UNAUTHORIZED
  - note: **LoginHandler failure point** — thrown when user not found OR password mismatch. → LoginFailureReason.INVALID_CREDENTIALS

- `AccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.ACCOUNT_LOCKED` (AUTH_002)
  - httpStatus: 403 FORBIDDEN
  - properties: lockedUntilAt (Instant), reason (String)
  - note: **LoginHandler failure point** — thrown when account locked. → LoginFailureReason.ACCOUNT_LOCKED

- `CaptchaRequiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.CAPTCHA_REQUIRED` (AUTH_007)
  - httpStatus: 428 PRECONDITION_REQUIRED
  - note: **LoginHandler failure point** — thrown when CAPTCHA needed but not provided. → LoginFailureReason.CAPTCHA_REQUIRED

- `CaptchaFailedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.CAPTCHA_FAILED` (AUTH_008)
  - httpStatus: 400 BAD_REQUEST
  - note: **LoginHandler failure point** — thrown when CAPTCHA verification fails. → LoginFailureReason.CAPTCHA_FAILED

- `PasswordExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.PASSWORD_EXPIRED` (AUTH_018)
  - httpStatus: 403 FORBIDDEN
  - note: **LoginHandler failure point** — thrown when password expired. → LoginFailureReason.PASSWORD_EXPIRED

- `RateLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.RATE_LIMITED` (AUTH_020)
  - httpStatus: 429 TOO_MANY_REQUESTS
  - properties: retryAfterSeconds (Long), dimension (String)
  - note: Login rate limit exceeded. Not thrown directly in LoginHandler but by LoginRateLimitService. → LoginFailureReason.RATE_LIMITED

## Error Code Format

- Pattern: `AuthErrorCode` enum — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Format: `AUTH_XXX` (e.g., AUTH_001, AUTH_002, AUTH_007)
- Bridge: `toErrorCodeBase()` converts to base-core `ErrorCodeBase` for RFC 7807 ProblemDetail
- Login-related codes: AUTH_001 (INVALID_CREDENTIALS), AUTH_002 (ACCOUNT_LOCKED), AUTH_007 (CAPTCHA_REQUIRED), AUTH_008 (CAPTCHA_FAILED), AUTH_018 (PASSWORD_EXPIRED), AUTH_020 (RATE_LIMITED)
- Event store codes: AUTH_050 (EVENT_STORE_PERSIST_FAILED), AUTH_051 (OUTBOX_PUBLISH_FAILED)

## Error Response Format

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - note: Maps AuthException subclasses to RFC 7807 ProblemDetail responses
  - Special handling: AccountLockedException → lockedUntil header, MfaAccountLockedException → retryAfterSeconds header

## Enum Discriminator Pattern (for LoginFailureReason)

- `ValidationFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`
  - values: BLACKLISTED, SIGNATURE_INVALID, ISSUER_MISMATCH, AUDIENCE_MISMATCH, TYPE_REJECTED, CLAIM_VALIDATION_FAILED
  - note: **Pattern reference for LoginFailureReason enum** — enum classifying validation failures, used in TokenValidationFailedEvent

- `IssuanceContext` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/IssuanceContext.kt`
  - note: Enum discriminator for TokenIssuedEvent — LOGIN, REGISTRATION, TOKEN_REFRESH, MFA_COMPLETION, SSO

- `RevocationType` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/RevocationType.kt`
  - note: Enum discriminator for TokenRevokedEvent — LOGOUT, ROTATION, BULK_REVOKE, etc.

## NOT DETECTED

- Custom error response DTO (uses base-core ProblemDetail directly)
