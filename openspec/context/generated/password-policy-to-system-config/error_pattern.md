# Error Handling Pattern

_Generated: 2026-10-02_

## Exception Classes

- `PasswordPolicyViolationException` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - Extends: `AuthException` (HTTP Status: 400 Bad Request)
- `PasswordRecentlyUsedException` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthCoreExceptions.kt`
  - Extends: `AuthException` (HTTP Status: 400 Bad Request)
- `SysAdminException` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminException.kt`
  - Extends: `BusinessException`

## Error Code Format

- Pattern: Enum `AuthErrorCode` implements `ErrorCodeBase`
- Key Example: `AuthErrorCode.PASSWORD_POLICY_VIOLATION` (`AUTH_017`, `auth.password_policy_violation`, "Password does not meet requirements", HttpStatus.BAD_REQUEST)
- Key Example: `AuthErrorCode.PASSWORD_EXPIRED` (`AUTH_018`, `auth.password_expired`, "Password has expired", HttpStatus.FORBIDDEN)
- SysAdmin Pattern: `SysAdminErrorCode.CONFIG_NOT_FOUND`, `SysAdminErrorCode.INVALID_CONFIG_TYPE`

## NOT DETECTED

- Custom legacy error dictionaries (toàn bộ đã quy chuẩn về RFC 7807 ProblemDetail + ApiResponse)
