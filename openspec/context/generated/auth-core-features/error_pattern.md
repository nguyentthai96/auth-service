# Error Handling Pattern

_Generated: 2026-08-25_

## Exception Classes

### Base Exception Hierarchy
- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Extends `BusinessException` (base-core library)
  - Properties: `authError: AuthErrorCode`, `httpStatus: HttpStatus`
  - Bridge: `AuthException` → `BusinessException` for cross-service consistency

### Auth Core Exceptions (AUTH_001-021)
- `InvalidCredentialsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_001 (401)
- `AccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_002 (403)
- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_003 (401)
- `AuthPermissionDeniedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_004 (403)
- `AuthResourceNotFoundException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_005 (404)
- `DuplicateResourceException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_006 (409)
- `CaptchaRequiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_007 (428)
- `CaptchaFailedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_008 (400)
- `PolicyEvaluationFailedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_009 (403)
- `WriteNotAllowedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` — AUTH_010 (403)
- `MfaCodeInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_011 (401)
- `MfaTokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_012 (401)
- `MfaMaxAttemptsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_013 (403)
- `SsoTokenInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_014 (401)
- `SsoUserNotProvisionedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_015 (403)
- `SsoIdentityConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_016 (409)
- `PasswordPolicyViolationException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_017 (400)
- `PasswordExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_018 (403)
- `MfaAccountLockedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_019 (429)
- `RateLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_020 (429)
- `SessionLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_021 (409)

### Special Exception Types
- `CannotUnlinkLastIdentityException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_014 (401)
- `SsoProviderTimeoutException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_014 (401)
- `TotpNotSetupException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_011 (401)
- `PasswordRecentlyUsedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AUTH_017 (400)

### E2EE Exceptions (AUTH_030-039)
- `E2eeTimeSkewException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_030 (400)
- `E2eeVersionUnknownException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_031 (400)
- `E2eeContextMismatchException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_032 (403)
- `E2eeReplayDetectedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_033 (409)
- `E2eeVersionSunsetException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_034 (426)
- `E2eeDecryptFailedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_035 (500)
- `E2eeKmsUnavailableException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_036 (503)
- `E2eeKeyExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_037 (401)
- `E2eeDeviceUnregisteredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_038 (403)
- `E2eeMaxDevicesException` — `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` — AUTH_039 (429)

### Anonymous Exceptions (AUTH_040-044)
- `AnonymousSessionExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_040 (404)
- `AnonymousDataLimitExceededException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_041 (413)
- `AnonymousPromotionConflictException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_042 (409)
- `AnonymousRateLimitedException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_043 (429)
- `AnonymousMaxRenewalsException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` — AUTH_044 (429)

## Error Code Format

- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Pattern: `AUTH_XXX` (3-digit numeric)
- Total: 45 error codes (AUTH_001 through AUTH_044, with gaps for grouping)
- Properties per code: `errorCode` (String), `msgCode` (String for i18n), `description` (String), `httpStatus` (HttpStatus)
- Bridge: `toErrorCodeBase()` — delegates to `com.ntt.basecore.exception.base.ErrorCodeBase`
- I18n: `msgCode` used as key in `MessageSource` (via `DatabaseMessageSource`)
- Example: `AuthErrorCode.INVALID_CREDENTIALS` → errorCode=`AUTH_001`, msgCode=`auth.invalid_credentials`, httpStatus=`401 UNAUTHORIZED`

## Error Response Format

- RFC 7807 ProblemDetail
- Handler: `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@RestControllerAdvice`
  - Extends `BaseControllerAdvice` (base-core)
  - `@ExceptionHandler(AuthException::class)` → ProblemDetail with correct HTTP status
  - I18n resolution via `MessageSource` using `AuthErrorCode.msgCode` as key
  - Special handling: `AccountLockedException` (lock duration), `MfaAccountLockedException` (retry-after), `RateLimitExceededException` (retry-after + dimension), `SessionLimitExceededException` (max sessions), `AnonymousRateLimitedException`, `AnonymousMaxRenewalsException`, `AnonymousDataLimitExceededException`

## NOT DETECTED

- Custom `@ControllerAdvice` beyond `GlobalExceptionHandler` — NOT DETECTED (single centralized handler)
- Error code ranges beyond AUTH_044 — NOT DETECTED
