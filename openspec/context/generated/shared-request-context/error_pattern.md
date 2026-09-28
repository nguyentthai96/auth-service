# Error Pattern

_Generated: 2026-09-28 | Candidate Services: base-core, auth-service, system-admin-service, account-service_

## Global Exception Handling Architecture

- `BaseControllerAdvice` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
- `DefaultControllerAdvice` — `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/DefaultControllerAdvice.kt`
- `ProblemDetailControllerAdvice` (RFC 7807) — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/ProblemDetailControllerAdvice.kt`

## Error Response Format

### Standard Error Response (`BaseResponse<Nothing>`)
```json
{
  "code": 401001,
  "message": "Authentication required",
  "data": null,
  "timestamp": 1727500000000
}
```
HTTP Headers:
- `X-Correlation-ID: 8f123abc-4567-89de-f012-3456789abcde`

### RFC 7807 ProblemDetail Format (khi `app.web.rfc7807.enabled=true`)
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid or expired token",
  "instance": "/api/v1/accounts/me",
  "correlationId": "8f123abc-4567-89de-f012-3456789abcde"
}
```

## Exception Hierarchy Liên Quan Đến Context

1. **`UnauthenticatedException`**: Ném ra khi code gọi `RequestContext.requireUserId()` mà request không có thông tin xác thực (`RequestContext.user == null`).
   - HTTP Status: `401 Unauthorized`.
   - Xử lý tại: `BaseControllerAdvice.handleUnauthenticatedException()`.
2. **`ScopeNotActiveException` (Tránh)**: Thay vì ném ngoại lệ này khi gọi ngoài web context, `RequestContextHolder.require()` sẽ tự động trả về `RequestContext.fallback(source)` an toàn.
3. **`AccessDeniedException`**: Ném ra khi user không có role hoặc permission yêu cầu (`requireRole()`).
   - HTTP Status: `403 Forbidden`.
