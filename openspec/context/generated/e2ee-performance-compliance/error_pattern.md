# Error Handling Pattern

_Generated: 2026-08-15_

## Exception Classes

### Base Exception
- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Extends: `BusinessException` (from `com.ntt.basecore.exception.BusinessException`)
  - Properties: `authError: AuthErrorCode`, `message: String`, `httpStatus: HttpStatus`
  - Pattern: Bridge to base-core `BusinessException` while preserving per-exception HTTP status

### Auth Exceptions (from `AuthExceptions.kt`)
- `ResourceNotFoundException` — extends `AuthException` — HTTP 404
- `DuplicateResourceException` — extends `AuthException` — HTTP 409
- `PermissionDeniedException` — extends `AuthException` — HTTP 403
- `WriteNotAllowedException` — extends `AuthException` — HTTP 403
- `AccountLockedException` — extends `AuthException` — HTTP 403 — has `lockedUntilAt: Instant`
- `InvalidCredentialsException` — extends `AuthException` — HTTP 401
- `TokenExpiredException` — extends `AuthException` — HTTP 401
- `PolicyEvaluationException` — extends `AuthException` — HTTP 403

### Auth Core Feature Exceptions (from `AuthCoreExceptions.kt`)
- `MfaCodeInvalidException` — extends `AuthException` — HTTP 401
- `MfaTokenExpiredException` — extends `AuthException` — HTTP 401
- `MfaMaxAttemptsException` — extends `AuthException` — HTTP 403
- `MfaAccountLockedException` — extends `AuthException` — HTTP 429 — has `retryAfterSeconds: Long`
- `TotpNotSetupException` — extends `AuthException` — HTTP 400
- `CaptchaRequiredException` — extends `AuthException` — HTTP 428
- `CaptchaFailedException` — extends `AuthException` — HTTP 400
- `SsoTokenInvalidException` — extends `AuthException` — HTTP 401
- `SsoUserNotProvisionedException` — extends `AuthException` — HTTP 403
- `SsoIdentityConflictException` — extends `AuthException` — HTTP 409
- `CannotUnlinkLastIdentityException` — extends `AuthException` — HTTP 400
- `SsoProviderTimeoutException` — extends `AuthException` — HTTP 504
- `PasswordRecentlyUsedException` — extends `AuthException` — HTTP 400
- `PasswordExpiredException` — extends `AuthException` — HTTP 403
- `PasswordPolicyViolationException` — extends `AuthException` — HTTP 400
- `RateLimitExceededException` — extends `AuthException` — HTTP 429 — has `retryAfterSeconds: Long`, `dimension: String`
- `SessionLimitExceededException` — extends `AuthException` — HTTP 409 — has `maxSessions: Int`, `activeCount: Int`

## Error Code Format

- Pattern: `AuthErrorCode` enum — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Format: `AUTH_XXX` (3-digit numeric suffix)
- Structure: `errorCode: String, msgCode: String, description: String, httpStatus: HttpStatus`
- Bridge: implements `ErrorCodeBase` from `com.ntt.basecore.exception.base.ErrorCodeBase`
- i18n: `msgCode` field (e.g., `auth.invalid_credentials`) for message resolution
- Range: AUTH_001 → AUTH_021 (21 codes defined)

### Error Codes List
| Code | msgCode | Description | HTTP Status |
|------|---------|-------------|-------------|
| AUTH_001 | auth.invalid_credentials | Invalid username or password | 401 |
| AUTH_002 | auth.account_locked | Account is locked | 403 |
| AUTH_003 | auth.token_expired | JWT token has expired | 401 |
| AUTH_004 | auth.permission_denied | Insufficient permissions | 403 |
| AUTH_005 | auth.resource_not_found | Resource not found | 404 |
| AUTH_006 | auth.duplicate_resource | Resource already exists | 409 |
| AUTH_007 | auth.captcha_required | CAPTCHA verification required | 428 |
| AUTH_008 | auth.captcha_failed | CAPTCHA verification failed | 400 |
| AUTH_009 | auth.policy_evaluation_failed | Policy evaluation failed | 403 |
| AUTH_010 | auth.write_not_allowed | Write not allowed | 403 |
| AUTH_011 | auth.mfa_code_invalid | Invalid MFA code | 401 |
| AUTH_012 | auth.mfa_token_expired | MFA token expired | 401 |
| AUTH_013 | auth.mfa_max_attempts | Max MFA attempts | 403 |
| AUTH_014 | auth.sso_token_invalid | SSO token invalid | 401 |
| AUTH_015 | auth.sso_user_not_provisioned | SSO user not provisioned | 403 |
| AUTH_016 | auth.sso_identity_conflict | SSO identity conflict | 409 |
| AUTH_017 | auth.password_policy_violation | Password policy violation | 400 |
| AUTH_018 | auth.password_expired | Password expired | 403 |
| AUTH_019 | auth.mfa_rate_limited | MFA rate limited | 429 |
| AUTH_020 | auth.rate_limited | Rate limited | 429 |
| AUTH_021 | auth.session_limit | Session limit exceeded | 409 |

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
- Annotation: `@RestControllerAdvice`
- Pattern: RFC 7807 ProblemDetail responses
- Handles: `BusinessException`, `AuthException`, `MethodArgumentNotValidException`, generic `Exception`

## E2EE Error Codes (Proposed — NOT YET IMPLEMENTED)

To be added in `AuthErrorCode`:
- `AUTH_030` → `E2EE_TIME_SKEW` — HTTP 400
- `AUTH_031` → `E2EE_VERSION_UNKNOWN` — HTTP 400
- `AUTH_032` → `E2EE_CONTEXT_MISMATCH` — HTTP 403
- `AUTH_033` → `E2EE_REPLAY_DETECTED` — HTTP 409
- `AUTH_034` → `E2EE_VERSION_SUNSET` — HTTP 426
- `AUTH_035` → `E2EE_DECRYPT_FAILED` — HTTP 500
- `AUTH_036` → `E2EE_KMS_UNAVAILABLE` — HTTP 503

## NOT DETECTED

- Custom error response body format (uses base-core ProblemDetail)
- Error code ranges per module (all share AUTH_xxx namespace)
