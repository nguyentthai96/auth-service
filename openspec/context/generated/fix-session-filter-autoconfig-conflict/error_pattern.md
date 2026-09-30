# Error Pattern

_Generated: 2026-09-30 | Services: base-security-starter, auth-service_

## Filter-Level Errors (Root Cause)

### AbstractSessionValidationFilter Unauthorized Error
- **Origin**: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/AbstractSessionValidationFilter.kt#L151-L155`
- **Trigger**: Missing `Authorization: Bearer` header on any request path not matching `publicPaths`.
- **Response Format**:
  ```json
  {
    "error": "Unauthorized",
    "message": "Missing or invalid Authorization header"
  }
  ```
- **Status**: HTTP 401 Unauthorized
- **Behavior**: Writes directly to `HttpServletResponse.writer` and terminates filter chain immediately.

## Application & Domain Errors (Expected Flow)

### AuthExceptions / ProblemDetail
- **Standard**: RFC 7807 `ProblemDetail` via `@ControllerAdvice` (`GlobalExceptionHandler`)
- **Key Error Codes**:
  - `AUTH_001`: `InvalidCredentialsException` — Sai tên đăng nhập hoặc mật khẩu.
  - `AUTH_002`: `UserLockedException` — Tài khoản bị khóa tạm thời do nhập sai quá số lần.
  - `AUTH_020`: `RateLimitExceededException` — Quá giới hạn request đăng nhập (HTTP 429).
  - `AUTH_060`: `ServiceTokenInvalidException` — Service token không hợp lệ.

### Error Handling Consistency
- Bằng cách vô hiệu hóa `DefaultSessionValidationFilter` trên `auth-service`, các lỗi xác thực người dùng được chuyển về `GlobalExceptionHandler` và chuẩn hóa theo `ProblemDetail` thay vì bị hardcoded JSON lỗi từ Servlet filter cấp thấp.
