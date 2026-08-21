# Error Handling Pattern

_Generated: 2026-08-25_

## Exception Classes

- AuthException (base) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Properties: authError (AuthErrorCode), httpStatus (HttpStatus)
  - Extends: BusinessException (base-core)
- ResourceNotFoundException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_005, 404
- DuplicateResourceException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_006, 409
- PermissionDeniedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_004, 403
- WriteNotAllowedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_010, 403
- AccountLockedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_002, 403
- InvalidCredentialsException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_001, 401
- TokenExpiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_003, 401
- PolicyEvaluationException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_009, 403
- MfaCodeInvalidException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_011, 401
- MfaTokenExpiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_012, 401
- MfaMaxAttemptsException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_013, 403
- MfaAccountLockedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_019, 429
- CaptchaRequiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_007, 428
- CaptchaFailedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_008, 400
- SsoTokenInvalidException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_014, 401
- SsoUserNotProvisionedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_015, 403
- SsoIdentityConflictException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_016, 409
- PasswordPolicyViolationException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_017, 400
- PasswordExpiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_018, 403
- RateLimitExceededException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_020, 429
- SessionLimitExceededException — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_021, 409
- CipherTimeSkewException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_030, 400
- CipherReplayDetectedException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_033, 409
- CipherContextMismatchException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_032, 403
- CipherVersionUnknownException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_031, 400
- CipherVersionSunsetException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_034, 426
- CipherDecryptFailedException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_035, 500
- CipherKmsUnavailableException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_036, 503
- CipherKeyExpiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_037, 401
- CipherDeviceUnregisteredException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_038, 403
- CipherMaxDevicesException — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_039, 429
- AnonymousSessionExpiredException — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_040, 404
- AnonymousDataLimitExceededException — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_041, 413
- AnonymousPromotionConflictException — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_042, 409
- AnonymousRateLimitedException — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_043, 429
- AnonymousMaxRenewalsException — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_044, 429

## Error Code Format

- Pattern: `AUTH_XXX` (3-digit numeric, enum-based) — `AuthErrorCode.kt`
- Example: `AUTH_001` (Invalid credentials), `AUTH_020` (Rate limited), `AUTH_044` (Anonymous max renewals)
- Total codes: 44 (AUTH_001-AUTH_021, AUTH_030-AUTH_039, AUTH_040-AUTH_044)
- msgCode convention: `auth.{snake_case_name}` (e.g., `auth.invalid_credentials`, `auth.rate_limited`)
- Each AuthErrorCode maps to: errorCode (String), msgCode (String), description (String), httpStatus (HttpStatus)
- Bridge to base-core: `toErrorCodeBase()` returns delegation wrapper

## Error Response Format

- Format: RFC 9457 ProblemDetail
- Handler: AuthControllerAdvice — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
- Fields: `type` (URI), `title` (errorCode), `status` (HTTP), `detail` (i18n message), `errorCode` (machine-readable)
- Extra properties per exception type: `retryAfterSeconds`, `dimension`, `maxSessions`, `lockedUntilAt`, etc.
- Headers: `Content-Language` (locale), `Retry-After` (for rate-limit/lock exceptions)
- i18n: MessageSource.getMessage(msgCode, args, defaultMessage, locale)
- Content-Type: `application/problem+json`

## Message Bundle (i18n)

- File: `src/main/resources/messages/auth-messages.properties` (70 lines, English default)
- File: `src/main/resources/messages/auth-messages_vi.properties` (70 lines, Vietnamese)
- 44 error message keys + 11 success message keys = 55 total keys
- All AuthErrorCode.msgCode values have corresponding entries in both bundles

## NOT DETECTED

- Custom error response wrapper (uses Spring ProblemDetail directly)
- Error code registry service (enum-based, no dynamic registry)
