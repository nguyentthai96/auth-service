# Error Handling Pattern

_Generated: 2025-08-21 | Feature: user-registration-event_

## Exception Classes

### Base Exception Hierarchy
- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Extends `BusinessException` (base-core library: `com.ntt.basecore.exception.BusinessException`)
  - Properties: `authError: AuthErrorCode`, `message: String`, `httpStatus: HttpStatus`
  - Bridge: `AuthException` → `BusinessException(authError.toErrorCodeBase())` for cross-service consistency

### Auth Core Exceptions (AUTH_001-021) — `AuthExceptions.kt`
- `InvalidCredentialsException` — AUTH_001 (401 UNAUTHORIZED)
- `AccountLockedException` — AUTH_002 (403 FORBIDDEN) — extra: `lockedUntilAt: Instant`, `reason: String`
- `TokenExpiredException` — AUTH_003 (401 UNAUTHORIZED)
- `PermissionDeniedException` — AUTH_004 (403 FORBIDDEN)
- `ResourceNotFoundException` — AUTH_005 (404 NOT_FOUND) — params: `resource: String`, `identifier: Any`
- `DuplicateResourceException` — AUTH_006 (409 CONFLICT) — params: `resource: String`, `field: String`, `value: Any`
- `PolicyEvaluationException` — AUTH_009 (403 FORBIDDEN)
- `WriteNotAllowedException` — AUTH_010 (403 FORBIDDEN)

### Auth Core Feature Exceptions (AUTH_007-021) — `AuthCoreExceptions.kt`
- `CaptchaRequiredException` — AUTH_007 (428 PRECONDITION_REQUIRED)
- `CaptchaFailedException` — AUTH_008 (400 BAD_REQUEST)
- `MfaCodeInvalidException` — AUTH_011 (401 UNAUTHORIZED)
- `MfaTokenExpiredException` — AUTH_012 (401 UNAUTHORIZED)
- `MfaMaxAttemptsException` — AUTH_013 (403 FORBIDDEN)
- `SsoTokenInvalidException` — AUTH_014 (401 UNAUTHORIZED)
- `SsoUserNotProvisionedException` — AUTH_015 (403 FORBIDDEN)
- `SsoIdentityConflictException` — AUTH_016 (409 CONFLICT)
- `PasswordPolicyViolationException` — AUTH_017 (400 BAD_REQUEST)
- `PasswordExpiredException` — AUTH_018 (403 FORBIDDEN)
- `MfaAccountLockedException` — AUTH_019 (429 TOO_MANY_REQUESTS) — extra: `retryAfterSeconds: Long`, `lockType: String`
- `RateLimitExceededException` — AUTH_020 (429 TOO_MANY_REQUESTS) — extra: `retryAfterSeconds: Long`, `dimension: String`
- `SessionLimitExceededException` — AUTH_021 (409 CONFLICT) — extra: `maxSessions: Int`, `activeCount: Int`

### Special Auth Core Exceptions — `AuthCoreExceptions.kt`
- `CannotUnlinkLastIdentityException` — AUTH_014 (400 BAD_REQUEST)
- `SsoProviderTimeoutException` — AUTH_014 (504 GATEWAY_TIMEOUT)
- `TotpNotSetupException` — AUTH_011 (400 BAD_REQUEST)
- `PasswordRecentlyUsedException` — AUTH_017 (400 BAD_REQUEST)

### E2EE Exceptions (AUTH_030-039) — `CipherExceptions.kt`
- `CipherTimeSkewException` — AUTH_030 (400 BAD_REQUEST)
- `CipherVersionUnknownException` — AUTH_031 (400 BAD_REQUEST) — extra: `version: String`
- `CipherContextMismatchException` — AUTH_032 (403 FORBIDDEN)
- `CipherReplayDetectedException` — AUTH_033 (409 CONFLICT) — extra: `nonce: String`
- `CipherVersionSunsetException` — AUTH_034 (426 UPGRADE_REQUIRED) — extra: `version: String`, `sunsetDate: String`
- `CipherDecryptFailedException` — AUTH_035 (500 INTERNAL_SERVER_ERROR)
- `CipherKmsUnavailableException` — AUTH_036 (503 SERVICE_UNAVAILABLE)
- `CipherKeyExpiredException` — AUTH_037 (401 UNAUTHORIZED) — extra: `keyId: String`
- `CipherDeviceUnregisteredException` — AUTH_038 (403 FORBIDDEN) — extra: `deviceId: String`
- `CipherMaxDevicesException` — AUTH_039 (429 TOO_MANY_REQUESTS) — extra: `maxDevices: Int`

### Anonymous Exceptions (AUTH_040-044) — `AnonymousExceptions.kt`
- `AnonymousSessionExpiredException` — AUTH_040 (404 NOT_FOUND) — params: `sessionId: String`
- `AnonymousDataLimitExceededException` — AUTH_041 (413 PAYLOAD_TOO_LARGE) — extra: `currentSize: Long`, `maxSize: Long`
- `AnonymousPromotionConflictException` — AUTH_042 (409 CONFLICT) — params: `sessionId: String`
- `AnonymousRateLimitedException` — AUTH_043 (429 TOO_MANY_REQUESTS) — extra: `retryAfterSeconds: Long`
- `AnonymousMaxRenewalsException` — AUTH_044 (429 TOO_MANY_REQUESTS) — extra: `maxRenewals: Int`

## Error Code Format

- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Pattern: `AUTH_XXX` (3-digit numeric, zero-padded)
- Total: 44 error codes (AUTH_001 through AUTH_044, with grouping gaps)
- Ranges:
  - AUTH_001-021: Core authentication & authorization
  - AUTH_030-039: E2EE (End-to-End Encryption)
  - AUTH_040-044: Anonymous sessions
- Properties per code:
  - `errorCode: String` — e.g., `"AUTH_001"`
  - `msgCode: String` — i18n message key, e.g., `"auth.invalid_credentials"`
  - `description: String` — human-readable description
  - `httpStatus: HttpStatus` — HTTP response status
- Bridge: `toErrorCodeBase(): ErrorCodeBase` — delegates to `com.ntt.basecore.exception.base.ErrorCodeBase`
- I18n: `msgCode` resolved via `MessageSource` (backed by `DatabaseMessageSource`)
- Example: `AuthErrorCode.DUPLICATE_RESOURCE` → errorCode=`AUTH_006`, msgCode=`auth.duplicate_resource`, httpStatus=`409 CONFLICT`

### Relevant Error Codes for This Feature
- `AUTH_006` (`DUPLICATE_RESOURCE`) — duplicate registration (username/email already exists)
- `AUTH_005` (`RESOURCE_NOT_FOUND`) — domain not found during registration
- Next available range for event sourcing errors: AUTH_050+ (⚠️ Assumption: event store errors will need new codes)

## Error Response Format

- Standard: RFC 7807 ProblemDetail
- Handler: `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@RestControllerAdvice`
  - Extends `BaseControllerAdvice` (base-core: `com.ntt.basecore.domain.web.BaseControllerAdvice`)
  - `@ExceptionHandler(AuthException::class)` → ProblemDetail with correct HTTP status
- Response fields:
  - `status` — HTTP status code
  - `title` — AuthErrorCode string (e.g., `"AUTH_006"`)
  - `detail` — i18n resolved message
  - `type` — URI `https://auth-service/errors/<error_name_lowercase>`
  - `errorCode` — custom property with error code string
- Special properties per exception type:
  - `AccountLockedException` → `lockedUntilAt`
  - `MfaAccountLockedException` → `retryAfterSeconds`, `lockType`
  - `RateLimitExceededException` → `retryAfterSeconds`, `dimension` + `Retry-After` header
  - `AnonymousRateLimitedException` → `retryAfterSeconds` + `Retry-After` header
  - `AnonymousMaxRenewalsException` → `maxRenewals`
  - `AnonymousDataLimitExceededException` → `currentSize`, `maxSize`
  - `SessionLimitExceededException` → `maxSessions`, `activeCount`
- I18n: Uses `MessageSource` with `AuthErrorCode.msgCode` as key
  - `extractMessageArgs(ex)` provides interpolation arguments per exception type
  - Fallback: `ex.message` → `authError.description` → `"Authentication error"`
- Content-Language: Set on all error responses via `setContentLanguageHeader(response)`

## Handler Priority

1. `AuthException` → `handleAuthException()` (this class — ProblemDetail with correct status)
2. `BusinessException` → `handleBusinessException()` (BaseControllerAdvice — ApiResponse with 422)
3. `Throwable` → `handleException()` (BaseControllerAdvice — fallback 500)

## NOT DETECTED

- Custom `@ControllerAdvice` beyond `AuthControllerAdvice` — NOT DETECTED (single centralized handler)
- Error code ranges beyond AUTH_044 — NOT DETECTED (available for extension)
- Event store specific error codes — NOT DETECTED (to be added for this feature)
