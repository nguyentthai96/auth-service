# Proposal: filter-architecture-consolidation

## Why

Kiến trúc filter hiện tại có 3 vấn đề chính:

1. **Code duplicate**: `ResourceJwtAuthFilter` ở account-service và system-admin-service (86 LOC × 2) gần **identical** với `DefaultSessionValidationFilter` trong base-core — cùng logic extract Bearer token, validate JWT, set SecurityContext.

2. **Content-Language coverage gap**: `ContentLanguageFilter` chỉ tồn tại ở auth-service. account-service và system-admin-service **thiếu 100%** Content-Language header trên response — vi phạm NFR-004 (i18n header coverage).

3. **God object**: `JwtAuthFilter` (auth-service, 236 LOC, 7 constructor deps) — monolith filter xử lý cả JWT validation, blacklist check, anonymous branching, device fingerprint, metrics recording — vi phạm Single Responsibility Principle.

4. **Error format inconsistency**: Security filters trả error bằng JSON string thủ công `{"error":"Unauthorized","message":"..."}`, trong khi ControllerAdvice trả RFC 7807 ProblemDetail — client phải handle 2 format khác nhau.

## What Changes

### Phase 1: Base-Core Enhancement
- **MODIFY** `BaseRequestContextFilter.kt` (base-web-starter): Thêm `response.setHeader("Content-Language", ...)` vào `doFilterInternal()` — 100% service coverage tự động.
- **MODIFY** `RequestContextProperties.kt` (base-web-starter): Thêm property `contentLanguageEnabled` (default: `true`).
- **MODIFY** `BaseControllerAdvice.kt` (base-core/src): Thêm comment giải thích belt-and-suspenders pattern.

### Phase 2: Service Migration
- **DELETE** `ResourceJwtAuthFilter.kt` (account-service): Xóa hoàn toàn, dùng `DefaultSessionValidationFilter` từ base-core.
- **DELETE** `ResourceJwtAuthFilter.kt` (system-admin-service): Tương tự.
- **MODIFY** `SecurityConfig.kt` (account/sysadmin): Xóa `addFilterBefore(jwtAuthFilter, ...)`.
- **MODIFY** `application.yml` (account/sysadmin): Thêm `app.security.public-paths`.

### Phase 3: auth-service Refactor
- **ADD** `AuthSessionValidationFilter.kt` (auth-service): Kế thừa `AbstractSessionValidationFilter`, inject `TokenBlacklistCacheService`, `FingerprintService`, etc. via constructor. Đăng ký qua `FilterRegistrationBean` (order -1700).
- **DELETE** `ContentLanguageFilter.kt` (auth-service): Đã merge vào base-core (Phase 1).
- **DELETE** `JwtAuthFilter.kt` (auth-service): Replaced by `AuthSessionValidationFilter`.
- **MODIFY** `SecurityConfig.kt` (auth-service): Xóa `addFilterBefore(jwtAuthFilter, ...)`, giữ `LoginRateLimitFilter` + `ServiceAuthFilter`.

### Phase 4: Error Standardization
- **MODIFY** `AbstractSessionValidationFilter.sendUnauthorized()` (base-security-starter): JSON string → RFC 7807 ProblemDetail (`application/problem+json`).
- **MODIFY** auth-service filter registration: Wrap `AuthSessionValidationFilter` với `ObservableFilterWrapper`.

## Capabilities

### New Capabilities
- `content-language-auto`: Content-Language header tự động set trên 100% response cho tất cả services qua `BaseRequestContextFilter` trong base-web-starter.
- `auth-session-pipeline`: auth-service sử dụng modular `AuthSessionValidationFilter` kế thừa base-core abstract class, dễ extend và test.

### Modified Capabilities
- `session-validation-error-format`: Error response từ filter chuyển sang RFC 7807 ProblemDetail — consistent với ControllerAdvice.
- `observable-filter`: `AuthSessionValidationFilter` wrapped với `ObservableFilterWrapper` cho duration metric + tracing.

## Impact

- **Components Affected**:
  - `components/base-core/starters/base-web-starter`: Thêm Content-Language vào BaseRequestContextFilter.
  - `components/base-core/starters/base-security-starter`: Chuẩn hóa sendUnauthorized() → ProblemDetail.
  - `components/base-core/src`: Comment update trong BaseControllerAdvice.
- **Services Affected**:
  - `services/auth-service`: Refactor JwtAuthFilter → AuthSessionValidationFilter, xóa ContentLanguageFilter.
  - `services/account-service`: Xóa ResourceJwtAuthFilter, dùng DefaultSessionValidationFilter.
  - `services/system-admin-service`: Tương tự account-service.
- **Downstream Services**:
  - `notification-service`: Không bị ảnh hưởng (ngoài scope). Tự động nhận Content-Language khi upgrade base-core.
- **APIs**:
  - Tất cả API response có `Content-Language` header (trước đây chỉ auth-service).
  - Error response từ filter thay đổi format (JSON → ProblemDetail) — breaking change cho client parsing error responses.
- **LOC Net Impact**: -231 lines (xóa 466, thêm 235).
