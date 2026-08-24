# Error Handling Pattern

_Generated: 2025-07-15 (refreshed)_

## Exception Handlers (3 services)

### auth-service
- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (from `com.ntt.basecore.domain.web.BaseControllerAdvice`)
  - Annotation: `@RestControllerAdvice`
  - Pattern: RFC 7807 ProblemDetail + i18n via `MessageSource`
  - Priority chain: AuthException → BusinessException (base-core) → Throwable (fallback)
  - Bridge: AuthException → ProblemDetail with correct HTTP status (overrides base-core default 422)

### account-service
- `AccountControllerAdvice` — `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountControllerAdvice.kt`
  - extends: `BaseControllerAdvice` (from base-core)
  - Pattern: Same RFC 7807 ProblemDetail pattern as auth-service

### system-admin-service
- `SysAdminControllerAdvice` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminControllerAdvice.kt`
  - extends: `BaseControllerAdvice` (from base-core)
  - Pattern: Same RFC 7807 ProblemDetail pattern as auth-service

## Error Code Format

### auth-service — `AuthErrorCode`
- File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Implements: `ErrorCodeBase` from `com.ntt.basecore.exception.base.ErrorCodeBase` (via delegation)
- Pattern: `AUTH_XXX` (3-digit numeric, grouped by feature)
- Fields per enum: `errorCode`, `msgCode`, `description`, `httpStatus`
- Bridge: `toErrorCodeBase()` method for base-core compatibility
- I18n key format: `auth.<feature>_<detail>` (e.g., `auth.invalid_credentials`, `auth.mfa_code_invalid`)

#### Error Code Ranges (auth-service)

| Range | Feature | Count |
|-------|---------|-------|
| AUTH_001..010 | Core Auth (login, token, permission, captcha, policy) | 10 |
| AUTH_011..021 | Auth Core Features (MFA, SSO, password policy, rate limit, session) | 11 |
| AUTH_030..039 | E2EE (cipher, replay, version, KMS) | 10 |
| AUTH_040..044 | Anonymous Session | 5 |

#### Key Error Codes (auth-service)

| Code | Message Key | HTTP Status | Description |
|------|------------|-------------|-------------|
| `AUTH_001` | `auth.invalid_credentials` | 401 UNAUTHORIZED | Invalid username or password |
| `AUTH_002` | `auth.account_locked` | 403 FORBIDDEN | Account locked due to failed attempts |
| `AUTH_003` | `auth.token_expired` | 401 UNAUTHORIZED | JWT token expired |
| `AUTH_004` | `auth.permission_denied` | 403 FORBIDDEN | Insufficient permissions |
| `AUTH_005` | `auth.resource_not_found` | 404 NOT_FOUND | Resource not found |
| `AUTH_006` | `auth.duplicate_resource` | 409 CONFLICT | Resource already exists |
| `AUTH_007` | `auth.captcha_required` | 428 PRECONDITION_REQUIRED | CAPTCHA verification required |
| `AUTH_008` | `auth.captcha_failed` | 400 BAD_REQUEST | CAPTCHA verification failed |
| `AUTH_011` | `auth.mfa_code_invalid` | 401 UNAUTHORIZED | Invalid MFA code |
| `AUTH_012` | `auth.mfa_token_expired` | 401 UNAUTHORIZED | MFA session expired |
| `AUTH_013` | `auth.mfa_max_attempts` | 403 FORBIDDEN | Max MFA attempts exceeded |
| `AUTH_014` | `auth.sso_token_invalid` | 401 UNAUTHORIZED | SSO token exchange failed |
| `AUTH_015` | `auth.sso_user_not_provisioned` | 403 FORBIDDEN | SSO user not provisioned |
| `AUTH_017` | `auth.password_policy_violation` | 400 BAD_REQUEST | Password doesn't meet requirements |
| `AUTH_019` | `auth.mfa_rate_limited` | 429 TOO_MANY_REQUESTS | MFA rate limit exceeded |
| `AUTH_020` | `auth.rate_limited` | 429 TOO_MANY_REQUESTS | Login rate limit exceeded |
| `AUTH_021` | `auth.session_limit_exceeded` | 409 CONFLICT | Session limit exceeded |
| `AUTH_030` | `auth.e2ee_time_skew` | 400 BAD_REQUEST | Request timestamp out of tolerance |
| `AUTH_040` | `auth.anonymous_session_expired` | 404 NOT_FOUND | Anonymous session expired |

### account-service — `AccountErrorCode`
- File: `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountErrorCode.kt`
- Implements: `ErrorCodeBase` from base-core (same delegation pattern)
- Pattern: `ACCT_XXX` (3-digit numeric)

| Code | Message Key | HTTP Status | Description |
|------|------------|-------------|-------------|
| `ACCT_001` | `account.profile_not_found` | 404 NOT_FOUND | User profile not found |
| `ACCT_002` | `account.profile_already_exists` | 409 CONFLICT | User profile already exists |
| `ACCT_003` | `account.contact_verification_required` | 428 PRECONDITION_REQUIRED | Contact change requires verification |
| `ACCT_004` | `account.invalid_preference_format` | 400 BAD_REQUEST | Invalid preference format |
| `ACCT_005` | `account.device_not_found` | 404 NOT_FOUND | Device not found |
| `ACCT_006` | `account.max_devices_reached` | 429 TOO_MANY_REQUESTS | Maximum devices per user reached |
| `ACCT_007` | `account.invalid_contact_type` | 400 BAD_REQUEST | Invalid contact type |
| `ACCT_008` | `account.general_error` | 500 INTERNAL_SERVER_ERROR | General error |

### system-admin-service — `SysAdminErrorCode`
- File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt`
- Implements: `ErrorCodeBase` from base-core (same delegation pattern)
- Pattern: `SYS_XXX` (3-digit numeric)

| Code | Message Key | HTTP Status | Description |
|------|------------|-------------|-------------|
| `SYS_001` | `sysadmin.general_error` | 500 INTERNAL_SERVER_ERROR | General system admin error |
| `SYS_002` | `sysadmin.circular_reference` | 400 BAD_REQUEST | Circular reference in menu tree |
| `SYS_003` | `sysadmin.permission_denied` | 403 FORBIDDEN | Insufficient permissions |
| `SYS_004` | `sysadmin.not_found` | 404 NOT_FOUND | Resource not found |
| `SYS_005` | `sysadmin.circular_hierarchy` | 400 BAD_REQUEST | Circular hierarchy in department tree |
| `SYS_006` | `sysadmin.position_duplicate` | 409 CONFLICT | Position code already exists |
| `SYS_007` | `sysadmin.partner_not_found` | 404 NOT_FOUND | API partner not found |
| `SYS_008` | `sysadmin.api_key_expired` | 401 UNAUTHORIZED | API key expired |
| `SYS_009` | `sysadmin.rate_limit_exceeded` | 429 TOO_MANY_REQUESTS | API rate limit exceeded |
| `SYS_010` | `sysadmin.ip_not_whitelisted` | 403 FORBIDDEN | IP address not whitelisted |
| `SYS_011` | `sysadmin.workflow_not_found` | 404 NOT_FOUND | Workflow not found |
| `SYS_012` | `sysadmin.invalid_transition` | 400 BAD_REQUEST | Invalid workflow state transition |
| `SYS_013` | `sysadmin.already_processed` | 409 CONFLICT | Workflow step already processed |
| `SYS_014` | `sysadmin.escalation_timeout` | 408 REQUEST_TIMEOUT | Escalation timeout exceeded |
| `SYS_015` | `sysadmin.config_not_found` | 404 NOT_FOUND | System config not found |

## Exception Class Hierarchy (auth-service)

### Base Exception

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Pattern: `open class AuthException(val authError: AuthErrorCode, message: String, cause: Throwable?)`
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

## Exception Hierarchy (account-service)

- `AccountException` — `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountExceptions.kt`
  - Base exception class for account-service

## Exception Hierarchy (system-admin-service)

- `SysAdminException` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
  - Base exception class for system-admin-service

## Cross-Service Error Pattern Summary

| Service | Error Code Prefix | Range | Exception Base | ControllerAdvice |
|---------|-------------------|-------|---------------|-----------------|
| auth-service | `AUTH_` | 001-044 | `AuthException` | `AuthControllerAdvice` (GlobalExceptionHandler.kt) |
| account-service | `ACCT_` | 001-008 | `AccountException` | `AccountControllerAdvice` |
| system-admin-service | `SYS_` | 001-015+ | `SysAdminException` | `SysAdminControllerAdvice` |

## NOT DETECTED

- Custom error response wrapper (uses base-core `ApiResponse<T>` + RFC 7807 `ProblemDetail`)
- Retry-specific exceptions
- Circuit breaker exceptions
- External service timeout exceptions (beyond `SsoProviderTimeoutException`)
