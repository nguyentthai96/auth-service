# Error Handling Pattern

_Generated: 2026-08-05 (refreshed)_

## Exception Handler

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (from `com.ntt.basecore.domain.web.BaseControllerAdvice`)
  - Annotation: `@RestControllerAdvice`
  - Pattern: RFC 7807 ProblemDetail + i18n via `MessageSource`
  - Priority chain: AuthException → BusinessException (base-core) → Throwable (fallback)
  - Bridge: AuthException → ProblemDetail with correct HTTP status (overrides base-core default 422)

## Error Code Format

- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - Implements: `ErrorCodeBase` from `com.ntt.basecore.exception.base.ErrorCodeBase`
  - Pattern: `AUTH_XXX` (3-digit numeric, grouped by feature)
  - Fields per enum: `errorCode`, `msgCode`, `description`, `httpStatus`
  - Bridge: `toErrorCodeBase()` method for base-core compatibility
  - I18n key format: `auth.<feature>_<detail>` (e.g., `auth.invalid_credentials`, `auth.mfa_code_invalid`)

### Error Code Ranges

| Range | Feature | Count |
|-------|---------|-------|
| AUTH_001..010 | Core Auth (login, token, permission, captcha, policy) | 10 |
| AUTH_011..021 | Auth Core Features (MFA, SSO, password policy, rate limit, session) | 11 |
| AUTH_030..039 | E2EE (cipher, replay, version, KMS) | 10 |
| AUTH_040..044 | Anonymous Session | 5 |

### Error Code Examples

| Code | Message Key | HTTP Status | Description |
|------|------------|-------------|-------------|
| `AUTH_001` | `auth.invalid_credentials` | 401 UNAUTHORIZED | Invalid username or password |
| `AUTH_002` | `auth.account_locked` | 403 FORBIDDEN | Account locked due to failed attempts |
| `AUTH_003` | `auth.token_expired` | 401 UNAUTHORIZED | JWT token expired |
| `AUTH_004` | `auth.permission_denied` | 403 FORBIDDEN | Insufficient permissions |
| `AUTH_005` | `auth.resource_not_found` | 404 NOT_FOUND | Resource not found |
| `AUTH_006` | `auth.duplicate_resource` | 409 CONFLICT | Resource already exists |
| `AUTH_011` | `auth.mfa_code_invalid` | 401 UNAUTHORIZED | Invalid MFA code |
| `AUTH_012` | `auth.mfa_token_expired` | 401 UNAUTHORIZED | MFA session expired |
| `AUTH_013` | `auth.mfa_max_attempts` | 403 FORBIDDEN | Max MFA attempts exceeded |
| `AUTH_014` | `auth.sso_token_invalid` | 401 UNAUTHORIZED | SSO token exchange failed |
| `AUTH_017` | `auth.password_policy_violation` | 400 BAD_REQUEST | Password doesn't meet requirements |
| `AUTH_019` | `auth.mfa_rate_limited` | 429 TOO_MANY_REQUESTS | MFA rate limit exceeded |
| `AUTH_020` | `auth.rate_limited` | 429 TOO_MANY_REQUESTS | Login rate limit exceeded |
| `AUTH_030` | `auth.e2ee_time_skew` | 400 BAD_REQUEST | Request timestamp out of tolerance |
| `AUTH_040` | `auth.anonymous_session_expired` | 404 NOT_FOUND | Anonymous session expired |

## Exception Class Hierarchy

### Base Exception

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Pattern: `open class AuthException(val errorCode: AuthErrorCode, message: String, cause: Throwable?)`
  - All auth exceptions extend this

### Core Auth Exceptions — `AuthExceptions.kt`

| Class | Error Code | HTTP Status |
|-------|-----------|-------------|
| `ResourceNotFoundException` | AUTH_005 | 404 |
| `DuplicateResourceException` | AUTH_006 | 409 |
| `PermissionDeniedException` | AUTH_004 | 403 |
| `WriteNotAllowedException` | AUTH_010 | 403 |
| `AccountLockedException` | AUTH_002 | 403 |
| `InvalidCredentialsException` | AUTH_001 | 401 |
| `TokenExpiredException` | AUTH_003 | 401 |
| `PolicyEvaluationException` | AUTH_009 | 403 |

### Auth Core Feature Exceptions — `AuthCoreExceptions.kt`

| Class | Error Code | HTTP Status |
|-------|-----------|-------------|
| `MfaCodeInvalidException` | AUTH_011 | 401 |
| `MfaTokenExpiredException` | AUTH_012 | 401 |
| `MfaMaxAttemptsException` | AUTH_013 | 403 |
| `MfaAccountLockedException` | AUTH_019 | 429 |
| `TotpNotSetupException` | AUTH_011 | 401 |
| `CaptchaRequiredException` | AUTH_007 | 428 |
| `CaptchaFailedException` | AUTH_008 | 400 |
| `SsoTokenInvalidException` | AUTH_014 | 401 |
| `SsoUserNotProvisionedException` | AUTH_015 | 403 |
| `SsoIdentityConflictException` | AUTH_016 | 409 |
| `CannotUnlinkLastIdentityException` | AUTH_016 | 409 |
| `SsoProviderTimeoutException` | AUTH_014 | 401 |
| `PasswordRecentlyUsedException` | AUTH_017 | 400 |
| `PasswordExpiredException` | AUTH_018 | 403 |
| `PasswordPolicyViolationException` | AUTH_017 | 400 |
| `RateLimitExceededException` | AUTH_020 | 429 |
| `SessionLimitExceededException` | AUTH_021 | 409 |

### Cipher Exceptions — `CipherExceptions.kt`

| Class | Error Code | HTTP Status |
|-------|-----------|-------------|
| `CipherTimeSkewException` | AUTH_030 | 400 |
| `CipherReplayDetectedException` | AUTH_033 | 409 |
| `CipherContextMismatchException` | AUTH_032 | 403 |
| `CipherVersionUnknownException` | AUTH_031 | 400 |
| `CipherVersionSunsetException` | AUTH_034 | 426 |
| `CipherDecryptFailedException` | AUTH_035 | 500 |
| `CipherKmsUnavailableException` | AUTH_036 | 503 |
| `CipherKeyExpiredException` | AUTH_037 | 401 |
| `CipherDeviceUnregisteredException` | AUTH_038 | 403 |
| `CipherMaxDevicesException` | AUTH_039 | 429 |

### Anonymous Session Exceptions — `AnonymousExceptions.kt`

| Class | Error Code | HTTP Status |
|-------|-----------|-------------|
| `AnonymousSessionExpiredException` | AUTH_040 | 404 |
| `AnonymousDataLimitExceededException` | AUTH_041 | 413 |
| `AnonymousPromotionConflictException` | AUTH_042 | 409 |
| `AnonymousRateLimitedException` | AUTH_043 | 429 |
| `AnonymousMaxRenewalsException` | AUTH_044 | 429 |

## NOT DETECTED

- Custom error response wrapper (uses base-core `ApiResponse<T>` + RFC 7807 `ProblemDetail`)
- Retry-specific exceptions
- Circuit breaker exceptions
- External service timeout exceptions (beyond `SsoProviderTimeoutException`)
