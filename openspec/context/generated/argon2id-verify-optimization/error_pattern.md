# Error Handling Pattern

_Generated: 2026-09-08_

## Exception Classes (Existing — No Changes)

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt:11`
  - Base exception class for all auth-service errors
- `InvalidCredentialsException` — `AuthExceptions.kt:61`
  - Thrown when password verify fails in LoginHandler
- `AccountLockedException` — `AuthExceptions.kt:52`
  - Thrown when account locked after max failed attempts

## Error Code Format

- Pattern: `AuthErrorCode` enum — `AUTH_XXX` format
- Example: `AuthErrorCode.INVALID_CREDENTIALS`, `AuthErrorCode.ACCOUNT_LOCKED`

## NOT DETECTED (Feature-Specific)

- No new exception classes needed
- No new error codes needed
- Feature change (iterations config) does not introduce new error paths
- Existing error handling for failed password verification remains unchanged
