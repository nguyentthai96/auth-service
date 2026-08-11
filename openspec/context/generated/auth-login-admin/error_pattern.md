# Error Handling Pattern

_Generated: 2026-08-11_

## Exception Classes

- AuthException (base) — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: BusinessException (com.ntt.basecore.exception)
  - uses: AuthErrorCode enum

- InvalidCredentialsException — same file
  - error: INVALID_CREDENTIALS
  - HTTP: 401 Unauthorized

- AccountLockedException — same file
  - error: ACCOUNT_LOCKED
  - HTTP: 423 Locked
  - extra fields: lockedUntil (Instant), reason (String)

- TokenExpiredException — same file
  - error: TOKEN_EXPIRED
  - HTTP: 401 Unauthorized

- ResourceNotFoundException — same file
  - error: RESOURCE_NOT_FOUND
  - HTTP: 404

- PermissionDeniedException — same file
  - error: PERMISSION_DENIED
  - HTTP: 403

- PolicyEvaluationException — same file
  - error: POLICY_EVALUATION_FAILED
  - HTTP: 403

## Auth Core Exceptions (Extended)

- CaptchaRequiredException — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - error: CAPTCHA_REQUIRED
  - HTTP: 428 Precondition Required

- MfaAccountLockedException — same file
  - error: MFA_ACCOUNT_LOCKED
  - HTTP: 423 Locked

- Additional exceptions in same file (MFA, SSO, Password Policy related)

## Error Code Format

- Pattern: `AuthErrorCode` enum with `(errorCode, msgCode, description)`
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - Example: `INVALID_CREDENTIALS("AUTH_001", "auth.error.invalid.credentials", "Invalid username or password")`
  - Example: `ACCOUNT_LOCKED("AUTH_002", "auth.error.account.locked", "Account is locked")`
  - Example: `TOKEN_EXPIRED("AUTH_003", "auth.error.token.expired", "Token has expired")`
  - Bridge: `toErrorCodeBase(): ErrorCodeBase` — delegates to base-core

## Exception Handler

- GlobalExceptionHandler — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - Annotation: @RestControllerAdvice
  - Pattern: RFC 7807 ProblemDetail
  - Handler chain:
    1. `handleAuthException(AuthException)` → ProblemDetail with correct HTTP status
    2. `handleBusinessException(BusinessException)` → ApiResponse with 422 (base-core)
    3. `handleException(Throwable)` → fallback (base-core)
  - Special handling:
    - AccountLockedException → adds `retryAfter` property to ProblemDetail
    - MfaAccountLockedException → adds `retryAfter` property

## Frontend Error Handling

- Currently: Generic catch in JwtAuthProvider → `console.error('Sign in failed:', error.response.status)`
- Needs: Specific error code parsing + UI state mapping (locked, captcha, rate limit)

## NOT DETECTED

- Rate limit exception class — NOT DETECTED (will need NEW `RateLimitExceededException` or use HTTP 429 directly)
- Frontend error boundary — NOT DETECTED (generic React error boundaries not specific to auth)
