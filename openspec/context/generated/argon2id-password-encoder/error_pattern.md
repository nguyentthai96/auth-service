# Error Handling Pattern

_Generated: 2026-09-08_

## Exception Classes (Password-related)

- AuthException — `shared/exception/AuthExceptions.kt:11` — base exception (open class)
- InvalidCredentialsException — `shared/exception/AuthExceptions.kt:61` — wrong password
- AccountLockedException — `shared/exception/AuthExceptions.kt:52` — max failed attempts exceeded
- PasswordRecentlyUsedException — `shared/exception/AuthCoreExceptions.kt:116` — password history violation
- PasswordExpiredException — `shared/exception/AuthCoreExceptions.kt:124` — password age exceeded
- PasswordPolicyViolationException — `shared/exception/AuthCoreExceptions.kt:132` — Passay validation failed

## Error Code Format

- Pattern: `AuthErrorCode` enum — `shared/exception/AuthErrorCode.kt`
- Example constants: `AUTH_001` (invalid credentials), `AUTH_007` (account locked)

## Global Exception Handler

- GlobalExceptionHandler — `shared/exception/GlobalExceptionHandler.kt` — @ControllerAdvice
- Returns RFC 7807 ProblemDetail format

## NOT DETECTED

- New exception classes needed for Argon2id migration (none needed — same PasswordEncoder interface)
