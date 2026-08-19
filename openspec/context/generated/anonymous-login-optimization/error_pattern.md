# Error Handling Pattern: anonymous-login-optimization

> _Generated: 2025-01-20 (regenerated Phase B completion)_
> Candidate Service: auth-service (`src/main/kotlin/com/ntt/authservice/`)

---

## 1. Error Code Enum

| Class | File Path | Pattern | Notes |
|-------|-----------|---------|-------|
| `AuthErrorCode` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` | `enum class AuthErrorCode(errorCode: String, msgCode: String, description: String, httpStatus: HttpStatus)` | Implements `ErrorCodeBase` (base-core) via delegation. Each entry carries HTTP status for RFC 7807 mapping. |

### Error Code Format

- **Pattern**: `AUTH_{NNN}` — 3-digit numeric, grouped by feature domain
- **Example constants**:
  - `AUTH_001` — `INVALID_CREDENTIALS` (401)
  - `AUTH_020` — `RATE_LIMITED` (429)
  - `AUTH_021` — `SESSION_LIMIT_EXCEEDED` (409)
  - `AUTH_030`~`AUTH_039` — E2EE error codes
  - `AUTH_040`~`AUTH_044` — Anonymous session error codes (already added)

### Error Code Ranges

| Range | Domain | Status |
|-------|--------|--------|
| `AUTH_001`~`AUTH_010` | Core auth (credentials, tokens, permissions, CAPTCHA) | Existing |
| `AUTH_011`~`AUTH_019` | MFA, SSO, Password Policy | Existing |
| `AUTH_020`~`AUTH_021` | Rate limiting, Session management | Existing |
| `AUTH_030`~`AUTH_039` | E2EE (End-to-End Encryption) | Existing |
| `AUTH_040`~`AUTH_044` | Anonymous session | Existing (already implemented) |

---

## 2. Base Exception Class

| Class | File Path | Extends | Notes |
|-------|-----------|---------|-------|
| `AuthException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `BusinessException` (from `com.ntt.basecore.exception`) | Base for all auth errors. Bridge pattern: extends `BusinessException` while carrying `AuthErrorCode` + per-exception `HttpStatus`. Constructor: `(authError: AuthErrorCode, message: String, httpStatus: HttpStatus)` |

---

## 3. Exception Classes (by domain)

### 3a. Core Auth Exceptions

| Class | File Path | Error Code | HTTP Status |
|-------|-----------|------------|-------------|
| `InvalidCredentialsException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_001` | 401 |
| `AccountLockedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_002` | 403 |
| `TokenExpiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_003` | 401 |
| `PermissionDeniedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_004` | 403 |
| `ResourceNotFoundException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_005` | 404 |
| `DuplicateResourceException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_006` | 409 |
| `WriteNotAllowedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_010` | 403 |
| `PolicyEvaluationException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | `AUTH_009` | 403 |

### 3b. Auth Core Feature Exceptions (MFA, SSO, CAPTCHA, Password, Rate Limit)

| Class | File Path | Error Code | HTTP Status |
|-------|-----------|------------|-------------|
| `MfaCodeInvalidException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_011` | 401 |
| `MfaTokenExpiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_012` | 401 |
| `MfaMaxAttemptsException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_013` | 403 |
| `MfaAccountLockedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_019` | 429 |
| `TotpNotSetupException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_011` | 400 |
| `CaptchaRequiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_007` | 428 |
| `CaptchaFailedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_008` | 400 |
| `SsoTokenInvalidException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_014` | 401 |
| `SsoUserNotProvisionedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_015` | 403 |
| `SsoIdentityConflictException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_016` | 409 |
| `CannotUnlinkLastIdentityException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_014` | 400 |
| `SsoProviderTimeoutException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_014` | 504 |
| `PasswordRecentlyUsedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_017` | 400 |
| `PasswordExpiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_018` | 403 |
| `PasswordPolicyViolationException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_017` | 400 |
| `RateLimitExceededException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_020` | 429 |
| `SessionLimitExceededException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt` | `AUTH_021` | 409 |

### 3c. E2EE (Cipher) Exceptions

| Class | File Path | Error Code | HTTP Status |
|-------|-----------|------------|-------------|
| `CipherTimeSkewException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_030` | 400 |
| `CipherReplayDetectedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_033` | 409 |
| `CipherContextMismatchException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_032` | 403 |
| `CipherVersionUnknownException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_031` | 400 |
| `CipherVersionSunsetException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_034` | 426 |
| `CipherDecryptFailedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_035` | 500 |
| `CipherKmsUnavailableException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_036` | 503 |
| `CipherKeyExpiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_037` | 401 |
| `CipherDeviceUnregisteredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_038` | 403 |
| `CipherMaxDevicesException` | `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | `AUTH_039` | 429 |

### 3d. Anonymous Session Exceptions (relevant to this feature)

| Class | File Path | Error Code | HTTP Status | Notes |
|-------|-----------|------------|-------------|-------|
| `AnonymousSessionExpiredException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | `AUTH_040` | 404 | Constructor: `(sessionId: String)` |
| `AnonymousDataLimitExceededException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | `AUTH_041` | 413 | Constructor: `(currentSize: Long, maxSize: Long)` |
| `AnonymousPromotionConflictException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | `AUTH_042` | 409 | Constructor: `(sessionId: String)` |
| `AnonymousRateLimitedException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | `AUTH_043` | 429 | Constructor: `(retryAfterSeconds: Long)` |
| `AnonymousMaxRenewalsException` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | `AUTH_044` | 429 | Constructor: `(maxRenewals: Int)` |

---

## 4. Exception Handler

| Class | File Path | Extends | Notes |
|-------|-----------|---------|-------|
| `AuthControllerAdvice` | `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | `BaseControllerAdvice` (from `com.ntt.basecore.domain.web`) | `@RestControllerAdvice`. Handles `AuthException` hierarchy with RFC 7807 ProblemDetail. Uses `MessageSource` for i18n message resolution. Sets `Content-Language` header. Adds `Retry-After` header for rate-limited responses. |

### Exception Handling Pattern

```kotlin
// Priority chain (most specific → least specific):
// 1. AuthException → handleAuthException()     → ProblemDetail with correct HTTP status
// 2. BusinessException → handleBusinessException() → ApiResponse with 422 (base-core)
// 3. Throwable → handleException()              → fallback (base-core)

@ExceptionHandler(AuthException::class)
fun handleAuthException(ex: AuthException, response: HttpServletResponse): ResponseEntity<ProblemDetail> {
    val args = extractMessageArgs(ex)
    val detail = resolveMessage(ex.authError.toErrorCodeBase().getMsgCode(), args, ex.message)
    
    val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, detail)
    problem.title = ex.authError.getErrorCode()
    problem.type = URI.create("https://auth-service/errors/${ex.authError.name.lowercase()}")
    problem.setProperty("errorCode", ex.authError.getErrorCode())
    
    // Extra properties for specific exception types (e.g., retryAfterSeconds, maxRenewals)
    // Retry-After header for rate-limited responses
    return ResponseEntity.status(ex.httpStatus).body(problem)
}
```

### Message Args Extraction Pattern

```kotlin
// Each exception type maps to MessageFormat placeholders:
private fun extractMessageArgs(ex: AuthException): Array<Any>? = when (ex) {
    is RateLimitExceededException -> arrayOf(ex.retryAfterSeconds, ex.dimension)
    is SessionLimitExceededException -> arrayOf(ex.maxSessions)
    is MfaAccountLockedException -> arrayOf(ex.retryAfterSeconds)
    is AnonymousRateLimitedException -> arrayOf(ex.retryAfterSeconds)
    is AnonymousMaxRenewalsException -> arrayOf(ex.maxRenewals)
    is AnonymousDataLimitExceededException -> arrayOf(ex.currentSize, ex.maxSize)
    else -> null
}
```

---

## 5. Exception Class Creation Convention

### Pattern for new exception class:

```kotlin
class {Feature}{ErrorType}Exception(
    val contextField: SomeType,                    // Exception-specific context
    message: String = "Default error message: $contextField"
) : AuthException(
    authError = AuthErrorCode.{SCREAMING_SNAKE_CODE},
    message = message,
    httpStatus = HttpStatus.{STATUS}
)
```

### Checklist for adding new exceptions:

1. Add `AuthErrorCode` enum entry with unique `AUTH_NNN` code, `msgCode`, `description`, and `HttpStatus`
2. Create exception class extending `AuthException` in appropriate file:
   - Core auth → `AuthExceptions.kt`
   - Feature-specific → `{Feature}Exceptions.kt` (e.g., `AnonymousExceptions.kt`, `CipherExceptions.kt`)
3. Add `extractMessageArgs()` case in `GlobalExceptionHandler` if exception has interpolation fields
4. Add extra properties handling in `handleAuthException()` if ProblemDetail needs custom fields
5. Add `Retry-After` header handling if exception is rate-limit-related
6. Add i18n message key in `messages.properties` matching the `msgCode`

---

## 6. I18n Integration

| Component | File Path | Notes |
|-----------|-----------|-------|
| `DatabaseMessageSource` | `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt` | Custom MessageSource backed by database |
| `I18nMessageEntity` | `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt` | JPA entity for i18n messages |
| `I18nMessageRepository` | `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt` | JPA repository for i18n messages |
| `I18nConfig` | `src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt` | I18n configuration |

**Message key pattern**: `auth.{feature}_{error_type}` — e.g., `auth.anonymous_session_expired`, `auth.anonymous_rate_limited`

---

## NOT DETECTED

- No custom exception interceptors (AOP-based)
- No exception-to-event mapping (exceptions don't automatically publish events)
