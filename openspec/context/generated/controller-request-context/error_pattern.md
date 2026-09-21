# Error Handling Pattern

_Generated: 2026-09-21_

## Exception Classes

- `AuthControllerAdvice` extends `BaseControllerAdvice` (base-core) — `shared/exception/GlobalExceptionHandler.kt`
  - Handles: `AuthException` hierarchy → RFC 7807 ProblemDetail with correct HTTP status
  - Priority: AuthException (specific) > BusinessException (base-core) > Throwable (fallback)
  - I18n: Resolves error messages via `MessageSource` using `AuthErrorCode.msgCode` as key

- `AuthExceptions.kt` — `shared/exception/AuthExceptions.kt`
  - Contains auth-specific exception classes

- `AuthCoreExceptions.kt` — `shared/exception/AuthCoreExceptions.kt`
  - Core auth exceptions (credentials, token, etc.)

- `AnonymousExceptions.kt` — `shared/exception/AnonymousExceptions.kt`
  - Anonymous session exceptions

- `CipherExceptions.kt` — `shared/exception/CipherExceptions.kt`
  - E2EE encryption/decryption exceptions

## Error Code Format

- Pattern: `AUTH_XXX` — Example: `AUTH_001` (INVALID_CREDENTIALS)
- Enum class: `AuthErrorCode` — `shared/exception/AuthErrorCode.kt`
- Implements: `ErrorCodeBase` (base-core interface)
- Structure per code:
  - `errorCode`: `"AUTH_XXX"` (string)
  - `msgCode`: `"auth.xxx_yyy"` (i18n message key)
  - `description`: English description
  - `httpStatus`: `HttpStatus` enum value

## Detected Error Codes (sample)

| Code | MsgCode | HTTP Status | Description |
|------|---------|-------------|-------------|
| AUTH_001 | auth.invalid_credentials | 401 UNAUTHORIZED | Invalid username or password |
| AUTH_002 | auth.account_locked | 403 FORBIDDEN | Account locked |
| AUTH_003 | auth.token_expired | 401 UNAUTHORIZED | JWT expired |
| AUTH_007 | auth.captcha_required | 428 PRECONDITION_REQUIRED | CAPTCHA required |
| AUTH_011 | auth.mfa_code_invalid | 401 UNAUTHORIZED | Invalid MFA code |
| AUTH_020 | auth.rate_limited | 429 TOO_MANY_REQUESTS | Too many login attempts |
| AUTH_030 | auth.e2ee_time_skew | 400 BAD_REQUEST | E2EE timestamp out of tolerance |

## NOT DETECTED

- Custom error response DTO (uses base-core `ProblemDetail` directly)
- Error code range allocation per module (all use AUTH_XXX)
