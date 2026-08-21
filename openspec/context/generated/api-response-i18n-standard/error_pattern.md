# Error Handling Pattern

_Generated: 2026-08-27_

## Exception Classes

### Base Exception
- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - field: `authError: AuthErrorCode`, `httpStatus: HttpStatus`

### Auth Core Exceptions (`AuthCoreExceptions.kt`)
- `MfaCodeInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` — AuthErrorCode.MFA_CODE_INVALID
- `MfaTokenExpiredException` — AuthErrorCode.MFA_TOKEN_EXPIRED
- `MfaMaxAttemptsException` — AuthErrorCode.MFA_MAX_ATTEMPTS
- `MfaRateLimitedException` — AuthErrorCode.MFA_RATE_LIMITED
- `RecoveryCodeInvalidException` — AuthErrorCode.MFA_CODE_INVALID
- `CaptchaRequiredException` — AuthErrorCode.CAPTCHA_REQUIRED
- `CaptchaFailedException` — AuthErrorCode.CAPTCHA_FAILED
- `SsoTokenInvalidException` — AuthErrorCode.SSO_TOKEN_INVALID
- `SsoUserNotProvisionedException` — AuthErrorCode.SSO_USER_NOT_PROVISIONED
- `SsoIdentityConflictException` — AuthErrorCode.SSO_IDENTITY_CONFLICT
- `SsoProviderDisabledException` — AuthErrorCode.SSO_TOKEN_INVALID
- `SsoInvalidStateException` — AuthErrorCode.SSO_TOKEN_INVALID
- `PasswordPolicyViolationException` — AuthErrorCode.PASSWORD_POLICY_VIOLATION
- `PasswordExpiredException` — AuthErrorCode.PASSWORD_EXPIRED
- `PasswordReusedException` — AuthErrorCode.PASSWORD_POLICY_VIOLATION
- `RateLimitExceededException` — AuthErrorCode.RATE_LIMITED
- `SessionLimitExceededException` — AuthErrorCode.SESSION_LIMIT_EXCEEDED

### Base Exceptions (`AuthExceptions.kt`)
- `ResourceNotFoundException` — AuthErrorCode.RESOURCE_NOT_FOUND
- `DuplicateResourceException` — AuthErrorCode.DUPLICATE_RESOURCE
- `PermissionDeniedException` — AuthErrorCode.PERMISSION_DENIED
- `WriteNotAllowedException` — AuthErrorCode.WRITE_NOT_ALLOWED
- `AccountLockedException` — AuthErrorCode.ACCOUNT_LOCKED
- `InvalidCredentialsException` — AuthErrorCode.INVALID_CREDENTIALS
- `TokenExpiredException` — AuthErrorCode.TOKEN_EXPIRED
- `PolicyEvaluationFailedException` — AuthErrorCode.POLICY_EVALUATION_FAILED
- `EventStorePersistException` — AuthErrorCode.EVENT_STORE_PERSIST_FAILED
- `EventNotFoundException` — AuthErrorCode.EVENT_NOT_FOUND

### Cipher Exceptions (`CipherExceptions.kt`)
- `TimeSkewException` — AuthErrorCode.E2EE_TIME_SKEW
- `ReplayDetectedException` — AuthErrorCode.E2EE_REPLAY_DETECTED
- `ContextMismatchException` — AuthErrorCode.E2EE_CONTEXT_MISMATCH
- `CipherVersionUnknownException` — AuthErrorCode.E2EE_VERSION_UNKNOWN
- `CipherVersionSunsetException` — AuthErrorCode.E2EE_VERSION_SUNSET
- `DecryptionFailedException` — AuthErrorCode.E2EE_DECRYPT_FAILED
- `KmsUnavailableException` — AuthErrorCode.E2EE_KMS_UNAVAILABLE
- `KeyExpiredException` — AuthErrorCode.E2EE_KEY_EXPIRED
- `DeviceUnregisteredException` — AuthErrorCode.E2EE_DEVICE_UNREGISTERED
- `MaxDevicesExceededException` — AuthErrorCode.E2EE_MAX_DEVICES

### Anonymous Exceptions (`AnonymousExceptions.kt`)
- `AnonymousSessionExpiredException` — AuthErrorCode.ANONYMOUS_SESSION_EXPIRED
- `AnonymousDataLimitExceededException` — AuthErrorCode.ANONYMOUS_DATA_LIMIT_EXCEEDED
- `AnonymousPromotionConflictException` — AuthErrorCode.ANONYMOUS_PROMOTION_CONFLICT
- `AnonymousRateLimitedException` — AuthErrorCode.ANONYMOUS_RATE_LIMITED
- `AnonymousMaxRenewalsException` — AuthErrorCode.ANONYMOUS_MAX_RENEWALS

## Error Code Format

- Pattern: `AUTH_XXX` (3-digit zero-padded)
- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Fields per code: `errorCode` (AUTH_001), `msgCode` (auth.invalid_credentials), `description`, `httpStatus`
- Range: AUTH_001-AUTH_021 (core), AUTH_030-AUTH_039 (E2EE), AUTH_040-AUTH_044 (anonymous), AUTH_050-AUTH_053 (event sourcing), AUTH_060-AUTH_062 (inter-service)
- Total: 62+ error codes
- Bridge: `toErrorCodeBase()` delegates to base-core `ErrorCodeBase`

## Error Response Format

- **Error**: `ProblemDetail` (RFC 9457) — via `AuthControllerAdvice.handleAuthException()`
  - Fields: `type`, `title`, `status`, `detail` (i18n), `errorCode` (machine-readable), extra properties per exception type
  - Content-Type: `application/problem+json`
  - `detail` field: localized via `MessageSource.getMessage(authError.msgCode, args, locale)`
- **Success**: `Map<String, Any?>` or typed DTOs with `message` field (i18n)
  - `message` field: localized via `messageSource.getMessage(key, args, defaultMsg, locale)`

## i18n Error Message Resolution

- `AuthControllerAdvice.resolveMessage()` — `GlobalExceptionHandler.kt:125`
- `extractMessageArgs()` — maps exception properties to `MessageFormat {0}, {1}` placeholders — `GlobalExceptionHandler.kt:107-117`
- Fallback: `ErrorCodeBase.description` as default message

## NOT DETECTED

- Custom error response wrapper (errors use ProblemDetail directly)
- Error aggregation / multi-error response pattern
