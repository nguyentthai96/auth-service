# Technical Design: filter-architecture-consolidation

## Context

Xem chi tiết tại `proposal.md`, `impact_analysis.md`, và `brainstorm_notes.md`.

Hiện tại kiến trúc filter cross-service có 4 vấn đề: code duplicate (`ResourceJwtAuthFilter`), Content-Language coverage gap, God object (`JwtAuthFilter` 236 LOC), và error format inconsistency. Change này consolidate toàn bộ qua 4 phase sequential.

## Goals / Non-Goals

**Goals:**
- Centralize Content-Language header management vào `BaseRequestContextFilter` (100% coverage)
- Eliminate `ResourceJwtAuthFilter` duplicate (172 LOC removed)
- Refactor `JwtAuthFilter` thành modular `AuthSessionValidationFilter` kế thừa `AbstractSessionValidationFilter`
- Standardize filter error response sang RFC 7807 ProblemDetail
- Giữ `LoginRateLimitFilter` + `ServiceAuthFilter` nguyên vị trí trong security chain

**Non-Goals:**
- Thay đổi logic validate JWT (signature, claims parsing) — reuse `JwtTokenExtractor`
- Thay đổi blacklist mechanism — reuse `TokenBlacklistCacheService`
- Refactor `LoginRateLimitFilter` hoặc `ServiceAuthFilter`
- Apply changes cho notification-service

## Decisions

### Decision 1: Content-Language vào BaseRequestContextFilter (not separate filter)

- **Lý do**: Zero filter mới. `BaseRequestContextFilter` đã chạy ở order `-2000` (HIGHEST_PRECEDENCE), trước tất cả filter khác. Thêm 1 line `response.setHeader()` — chi phí gần zero.
- **Trade-off**: Filter set Content-Language TRƯỚC controller xử lý → locale từ `LocaleContextHolder.getLocale()` có thể chưa reflect request-specific locale nếu `I18nAutoConfiguration` chưa chạy.
- **Mitigation**: `I18nAutoConfiguration` (LocaleChangeInterceptor) chạy ở interceptor level → `BaseControllerAdvice.setContentLanguageHeader()` GIỮ LẠI như belt-and-suspenders backup.
- **Phương án loại bỏ**: Tạo filter riêng (`ContentLanguageBaseFilter`) → rejected vì thêm filter thừa.

### Decision 2: ResourceJwtAuthFilter → DELETE + DefaultSessionValidationFilter auto

- **Lý do**: `DefaultSessionValidationFilter` đã tồn tại trong base-core, tested, `@ConditionalOnMissingBean` + `@ConditionalOnProperty`. ResourceJwtAuthFilter là copy-paste 86% giống.
- **Trade-off**: Chuyển từ security chain registration (`addFilterBefore`) sang servlet filter registration (`FilterRegistrationBean` order -1700).
- **Mitigation**: Integration test verify authorization rules vẫn đúng sau migration.

### Decision 3: JwtAuthFilter refactor → AuthSessionValidationFilter extends AbstractSessionValidationFilter

- **Lý do**: God object (236 LOC, 7 deps) → pipeline kế thừa abstract class. Auth-service cần domain-specific logic (blacklist, fingerprint, anonymous branching) → override 4 abstract methods.
- **Trade-off**: Chuyển từ security chain sang servlet filter registration — `LoginRateLimitFilter` + `ServiceAuthFilter` giữ trong security chain.
- **Mitigation**: Comprehensive unit test + integration test trước/sau refactor.

### Decision 4: Servlet filter registration (NOT security chain) cho SessionValidation

- **Lý do**: Consistency với `DefaultSessionValidationFilter` (đã dùng `FilterRegistrationBean` order -1700). `OrderConstants` quản lý tập trung. `@ConditionalOnMissingBean` auto-disable default khi custom bean present.
- **Trade-off**: Filter chạy trước Spring Security → phải set SecurityContext manually → SecurityFilterChain chỉ authorize (không validate).
- **Phương án loại bỏ**: `addFilterBefore` trong security chain → rejected vì inconsistent với base-core convention.

### Decision 5: sendUnauthorized → RFC 7807 ProblemDetail

- **Lý do**: Consistency với `BaseControllerAdvice` + `GlobalExceptionHandler` (đã dùng ProblemDetail). Client chỉ cần parse 1 format.
- **Trade-off**: Breaking change cho client parsing `{"error":"Unauthorized","message":"..."}` → `{"type":"...","title":"...","status":401,"detail":"..."}`.
- **Mitigation**: Phase 4 (cuối cùng) → client có thời gian adapt. Document trong API changelog.

## Component Architecture (After)

```
┌─────────────────────────────────────────────────────┐
│                  Servlet Container                   │
│                                                      │
│  BaseRequestContextFilter     (order: -2000)         │
│  ├── MDC lifecycle                                   │
│  ├── RequestContext                                  │
│  └── Content-Language header ← NEW (FR-001)          │
│                                                      │
│  ContentCachingFilter         (order: -1950)         │
│                                                      │
│  *SessionValidationFilter     (order: -1700)         │
│  ├── account/sysadmin: DefaultSessionValidF (auto)   │
│  └── auth-service: AuthSessionValidF (custom)        │
│      ├── validateToken() → JwtTokenExtractor.extract │
│      ├── isTokenRevoked() → TokenBlacklistCacheService│
│      ├── extractTokenId() → Claims.jti              │
│      └── onAuthenticationSuccess()                   │
│          ├── anonymous → ROLE_ANONYMOUS              │
│          └── authenticated → roles/permissions       │
│                                                      │
│  SecurityContextBridgeFilter  (order: -50)           │
│                                                      │
│  DelegatingFilterProxy → SecurityFilterChain         │
│  ├── LoginRateLimitFilter (auth only)                │
│  ├── ServiceAuthFilter (auth only)                   │
│  └── AuthorizationFilter → DynamicAuthorizationMgr   │
└─────────────────────────────────────────────────────┘
```

## Risks / Trade-offs

| # | Risk | Mitigation |
|---|------|------------|
| 1 | Filter type transition (security chain → servlet) cho account/sysadmin | Integration test authorization rules |
| 2 | JwtAuthFilter refactor behavior change | Unit test + integration test before/after |
| 3 | Coordinated deployment dependency | Phase-by-phase: base-core → services |
| 4 | ProblemDetail breaking change cho client | Phase 4 last → client adapt time |

## Migration Plan

1. **Phase 1** (base-core): Modify BaseRequestContextFilter + RequestContextProperties → `publishToMavenLocal`
2. **Phase 2** (account/sysadmin): Delete ResourceJwtAuthFilter, modify SecurityConfig + yml → test
3. **Phase 3** (auth-service): Create AuthSessionValidationFilter, delete JwtAuthFilter + ContentLanguageFilter, modify SecurityConfig → test
4. **Phase 4** (base-core): Modify AbstractSessionValidationFilter.sendUnauthorized() → `publishToMavenLocal` → all services re-test
