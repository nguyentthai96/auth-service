# Error Pattern — auth-core-features

> Generated: 2026-08-26
> Scope: `auth-service` (primary candidate service)
> Source: Code scan — grep for Exception, ErrorCode, error patterns

---

## 1. Exception Files

| File | Path | Notes |
|------|------|-------|
| `AuthExceptions.kt` | `shared/exception/AuthExceptions.kt` | Core auth exceptions (InvalidCredentials, TokenExpired, AccountLocked, etc.) |
| `AuthCoreExceptions.kt` | `shared/exception/AuthCoreExceptions.kt` | MFA/SSO/Password exceptions (MfaCodeInvalid, SsoTokenInvalid, PasswordPolicyViolation, etc.) |
| `AnonymousExceptions.kt` | `shared/exception/AnonymousExceptions.kt` | Anonymous session exceptions |
| `CipherExceptions.kt` | `shared/exception/CipherExceptions.kt` | E2EE/Cipher exceptions |
| `GlobalExceptionHandler.kt` | `shared/exception/GlobalExceptionHandler.kt` | `@RestControllerAdvice` — centralized exception → HTTP response mapping |

## 2. Error Code Enum

**File**: `shared/exception/AuthErrorCode.kt`
**Total error codes**: 52 (AUTH_001 — AUTH_062, with gaps)

### Auth Core Errors (AUTH_001 — AUTH_021)

| Code | Name | HTTP Status | Description |
|------|------|-------------|-------------|
| AUTH_001 | `INVALID_CREDENTIALS` | 401 | Invalid username or password |
| AUTH_002 | `ACCOUNT_LOCKED` | 403 | Account locked due to failed attempts |
| AUTH_003 | `TOKEN_EXPIRED` | 401 | JWT token has expired |
| AUTH_004 | `PERMISSION_DENIED` | 403 | Insufficient permissions |
| AUTH_005 | `RESOURCE_NOT_FOUND` | 404 | Resource not found |
| AUTH_006 | `DUPLICATE_RESOURCE` | 409 | Resource already exists |
| AUTH_007 | `CAPTCHA_REQUIRED` | 428 | CAPTCHA verification required |
| AUTH_008 | `CAPTCHA_FAILED` | 400 | CAPTCHA verification failed |
| AUTH_009 | `POLICY_EVALUATION_FAILED` | 403 | Policy evaluation failed |
| AUTH_010 | `WRITE_NOT_ALLOWED` | 403 | Write operation not allowed |
| AUTH_011 | `MFA_CODE_INVALID` | 401 | Invalid MFA verification code |
| AUTH_012 | `MFA_TOKEN_EXPIRED` | 401 | MFA session token has expired |
| AUTH_013 | `MFA_MAX_ATTEMPTS` | 403 | Maximum MFA attempts exceeded |
| AUTH_014 | `SSO_TOKEN_INVALID` | 401 | SSO token exchange failed |
| AUTH_015 | `SSO_USER_NOT_PROVISIONED` | 403 | SSO user not provisioned |
| AUTH_016 | `SSO_IDENTITY_CONFLICT` | 409 | SSO identity already linked |
| AUTH_017 | `PASSWORD_POLICY_VIOLATION` | 400 | Password does not meet requirements |
| AUTH_018 | `PASSWORD_EXPIRED` | 403 | Password has expired |
| AUTH_019 | `MFA_RATE_LIMITED` | 429 | MFA rate limit exceeded |
| AUTH_020 | `RATE_LIMITED` | 429 | Too many login attempts |
| AUTH_021 | `SESSION_LIMIT_EXCEEDED` | 409 | Maximum active sessions exceeded |

### E2EE Errors (AUTH_030 — AUTH_039)

| Code | Name | HTTP Status | Description |
|------|------|-------------|-------------|
| AUTH_030 | `E2EE_TIME_SKEW` | 400 | Request timestamp out of tolerance |
| AUTH_031 | `E2EE_VERSION_UNKNOWN` | 400 | Unknown cipher version |
| AUTH_032 | `E2EE_CONTEXT_MISMATCH` | 403 | AAD context mismatch |
| AUTH_033 | `E2EE_REPLAY_DETECTED` | 409 | Duplicate nonce — replay attack |
| AUTH_034 | `E2EE_VERSION_SUNSET` | 426 | Cipher version past sunset |
| AUTH_035 | `E2EE_DECRYPT_FAILED` | 500 | Decryption failed |
| AUTH_036 | `E2EE_KMS_UNAVAILABLE` | 503 | KMS unavailable and no cached DEK |
| AUTH_037 | `E2EE_KEY_EXPIRED` | 401 | Key session expired |
| AUTH_038 | `E2EE_DEVICE_UNREGISTERED` | 403 | Device not registered for E2EE |
| AUTH_039 | `E2EE_MAX_DEVICES` | 429 | Maximum devices per user exceeded |

### Anonymous Session Errors (AUTH_040 — AUTH_044)

| Code | Name | HTTP Status | Description |
|------|------|-------------|-------------|
| AUTH_040 | `ANONYMOUS_SESSION_EXPIRED` | 404 | Anonymous session expired or not found |
| AUTH_041 | `ANONYMOUS_DATA_LIMIT_EXCEEDED` | 413 | Anonymous session data limit exceeded |
| AUTH_042 | `ANONYMOUS_PROMOTION_CONFLICT` | 409 | Anonymous session promotion conflict |
| AUTH_043 | `ANONYMOUS_RATE_LIMITED` | 429 | Anonymous token creation rate limited |
| AUTH_044 | `ANONYMOUS_MAX_RENEWALS` | 429 | Anonymous token max renewals exceeded |

### Event Sourcing Errors (AUTH_050 — AUTH_053) [NEW]

| Code | Name | HTTP Status | Description |
|------|------|-------------|-------------|
| AUTH_050 | `EVENT_STORE_PERSIST_FAILED` | 500 | Event store write failure |
| AUTH_051 | `OUTBOX_PUBLISH_FAILED` | 500 | Outbox Kafka publish failure |
| AUTH_052 | `EVENT_NOT_FOUND` | 404 | Event not found |
| AUTH_053 | `OUTBOX_MAX_RETRIES_EXCEEDED` | 500 | Outbox max retries exceeded |

### Inter-service Auth Errors (AUTH_060 — AUTH_062) [NEW]

| Code | Name | HTTP Status | Description |
|------|------|-------------|-------------|
| AUTH_060 | `INVALID_SERVICE_TOKEN` | 401 | Invalid or expired service auth token |
| AUTH_061 | `INSUFFICIENT_SCOPE` | 403 | Service insufficient scope |
| AUTH_062 | `SERVICE_NOT_REGISTERED` | 403 | Service not registered |

## 3. Error Response Format

**Handler**: `GlobalExceptionHandler.kt` (`@RestControllerAdvice`)

The error response follows a standardized JSON format with i18n support:

```json
{
  "code": "AUTH_011",
  "message": "Invalid MFA verification code",
  "details": {},
  "timestamp": "2026-08-26T10:00sources, identity conflicts |
| Precondition errors | HTTP 428 | CAPTCHA required |
| Event sourcing errors | HTTP 500 | [NEW] Event store/outbox failures (non-blocking for auth flow) |
