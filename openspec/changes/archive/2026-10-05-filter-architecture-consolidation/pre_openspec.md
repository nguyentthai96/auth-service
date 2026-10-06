# Pre-OpenSpec: filter-architecture-consolidation

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea (Code Review Analysis)
> **Classification Evidence**: `BaseRequestContextFilter` → `base-web-starter` → `BaseRequestContextFilter.kt`; `ResourceJwtAuthFilter` → `account-service`/`system-admin-service` → duplicate 86 LOC × 2; `DefaultSessionValidationFilter` → `base-security-starter` → `DefaultSessionValidationFilter.kt` (existing, unused)
> **Archive**: `openspec/changes/archive/2026-09-30-fix-session-filter-autoconfig-conflict`
> **Quality Score**: 96/100

## 📋 Feature Summary

Tối ưu hóa kiến trúc filter cross-service qua 4 mảng: (1) Merge `ContentLanguageFilter` vào `BaseRequestContextFilter` trong base-core để tất cả service tự động có `Content-Language` header, (2) Xóa `ResourceJwtAuthFilter` duplicate ở account/sysadmin-service và migrate sang `DefaultSessionValidationFilter` của base-core, (3) Refactor `JwtAuthFilter` "God object" (236 LOC, 7 deps) trong auth-service thành pipeline kế thừa `AbstractSessionValidationFilter`, (4) Chuẩn hóa error response sang RFC 7807 ProblemDetail cho tất cả security filters.

| Metric | Giá trị |
|--------|---------|
| Số FR | 12 (Idea: 10, Enriched: 2) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 0 |
| **Quality Score** | **96/100** |

---

## 1. Actors

- **System / Base-Core Framework**: Cung cấp filter infrastructure chung (BaseRequestContextFilter, AbstractSessionValidationFilter, DefaultSessionValidationFilter)
- **auth-service**: Identity Provider — quản lý xác thực JWT với domain-specific logic (blacklist, fingerprint, claim chain, anonymous token)
- **account-service**: Resource Server — tiêu thụ JWT token do auth-service cấp phát
- **system-admin-service**: Resource Server — tiêu thụ JWT token do auth-service cấp phát
- **DevOps / CI-CD**: Deploy phân pha (base-core → services)

## 2. Functional Requirements

### Phase 1: Base-Core Enhancement

### FR-001: Thêm Content-Language Header vào BaseRequestContextFilter [IDEA]
- **Actor**: System / Base-Core Framework
- **Action**: Hệ thống phải set response header `Content-Language` trong `BaseRequestContextFilter.doFilterInternal()` sử dụng `LocaleContextHolder.getLocale().toLanguageTag()`.
- **Validation**:
  - Mọi response (200, 4xx, 5xx) đều có `Content-Language` header tự động
  - Header set TRƯỚC `filterChain.doFilter()` (idempotent — set lại trong ControllerAdvice không gây conflict)
  - Có thể disable qua property `app.request-context.content-language-enabled` (default: `true`)

### FR-002: Giữ setContentLanguageHeader trong BaseControllerAdvice [IDEA]
- **Actor**: System / Base-Core Framework
- **Action**: Hệ thống phải giữ `setContentLanguageHeader()` trong `BaseControllerAdvice` như cơ chế belt-and-suspenders, bổ sung comment giải thích lý do giữ song song với filter.
- **Validation**:
  - Comment rõ: "Belt-and-suspenders: filter sets this header first, advice re-sets on error paths as backup"
  - Không thay đổi behavior hiện tại của `BaseControllerAdvice`

### Phase 2: Account/SysAdmin Migration

### FR-003: Xóa ResourceJwtAuthFilter ở account-service [IDEA]
- **Actor**: account-service
- **Action**: Hệ thống phải xóa hoàn toàn file `ResourceJwtAuthFilter.kt` (86 LOC) khỏi `account-service` và sử dụng `DefaultSessionValidationFilter` từ base-core thay thế.
- **Validation**:
  - File `ResourceJwtAuthFilter.kt` không còn tồn tại trong source code
  - `SecurityConfig.kt` không còn reference `ResourceJwtAuthFilter` hoặc `addFilterBefore(jwtAuthFilter, ...)`
  - `DefaultSessionValidationFilter` tự đăng ký qua `SecurityAutoConfiguration` (`matchIfMissing=true`)

### FR-004: Xóa ResourceJwtAuthFilter ở system-admin-service [IDEA]
- **Actor**: system-admin-service
- **Action**: Hệ thống phải xóa hoàn toàn file `ResourceJwtAuthFilter.kt` (86 LOC) khỏi `system-admin-service` và sử dụng `DefaultSessionValidationFilter` từ base-core thay thế.
- **Validation**:
  - Tương tự FR-003, áp dụng cho `system-admin-service`

### FR-005: Cấu hình publicPaths cho Resource Servers [IDEA]
- **Actor**: account-service, system-admin-service
- **Action**: Hệ thống phải cấu hình `app.security.public-paths` trong `application.yml` của mỗi service để `DefaultSessionValidationFilter` bypass đúng endpoint.
- **Validation**:
  - Ít nhất: `/actuator/**`, `/health`, OPTIONS `/**`
  - `DefaultSessionValidationFilter` bypass đúng các path này (không trả 401)

### Phase 3: auth-service JwtAuthFilter Refactor

### FR-006: Tạo AuthSessionValidationFilter kế thừa AbstractSessionValidationFilter [IDEA]
- **Actor**: auth-service
- **Action**: Hệ thống phải tạo `AuthSessionValidationFilter` kế thừa `AbstractSessionValidationFilter`, inject domain-specific deps qua constructor: `TokenBlacklistCacheService`, `ClaimValidatorChain`, `FingerprintService`, `TokenEventRecorder`, `SecurityProperties`, `MeterRegistry`.
- **Validation**:
  - Kế thừa `AbstractSessionValidationFilter(publicPaths)`
  - Override: `validateToken()`, `isTokenRevoked()`, `extractTokenId()`, `onAuthenticationSuccess()`
  - Đăng ký qua `FilterRegistrationBean` với order `OrderConstants.SESSION_VALIDATION` (-1700)
  - `@ConditionalOnMissingBean(AbstractSessionValidationFilter::class)` trên DefaultSessionValidF tự disable

### FR-007: Override isTokenRevoked với BlacklistCacheService [IDEA]
- **Actor**: auth-service
- **Action**: `AuthSessionValidationFilter.isTokenRevoked()` phải delegate sang `TokenBlacklistCacheService.isBlacklisted(tokenId)` qua constructor injection.
- **Validation**:
  - Token đã blacklist → trả `false` cho `isTokenRevoked()` → filter reject 401
  - Token chưa blacklist → pass through

### FR-008: Hỗ trợ anonymous token branching [IDEA]
- **Actor**: auth-service
- **Action**: `AuthSessionValidationFilter.onAuthenticationSuccess()` phải phân biệt token type `anonymous` vs `authenticated` và set `ROLE_ANONYMOUS` hoặc roles/permissions từ claims tương ứng.
- **Validation**:
  - Token `type=anonymous` → `SecurityContext` có `ROLE_ANONYMOUS` + `authDetails = {type: "anonymous", sessionId: ...}`
  - Token authenticated → `SecurityContext` có roles/permissions + `authDetails = {username: ...}`

### FR-009: Xóa ContentLanguageFilter auth-service [IDEA]
- **Actor**: auth-service
- **Action**: Hệ thống phải xóa hoàn toàn `ContentLanguageFilter.kt` (48 LOC) khỏi auth-service vì base-core `BaseRequestContextFilter` đã handle (FR-001).
- **Validation**:
  - File `ContentLanguageFilter.kt` không còn tồn tại
  - Response vẫn có `Content-Language` header (từ BaseRequestContextFilter)

### FR-010: Giữ LoginRateLimitFilter và ServiceAuthFilter trong Security Chain [IDEA]
- **Actor**: auth-service
- **Action**: `LoginRateLimitFilter` và `ServiceAuthFilter` phải GIỮ NGUYÊN trong Spring Security filter chain (`addFilterBefore`) vì cần authorize context (`ROLE_SERVICE`).
- **Validation**:
  - `SecurityConfig` vẫn có `addFilterBefore(loginRateLimitFilter, ...)` và `addFilterBefore(serviceAuthFilter, ...)`
  - `JwtAuthFilter` (cũ) bị XÓA khỏi `addFilterBefore` — thay bằng `AuthSessionValidationFilter` ở servlet level

### Phase 4: Error Response Standardization

### FR-011: Chuẩn hóa error response trong AbstractSessionValidationFilter [ENRICHED]
- **Actor**: System / Base-Core Framework
- **Action**: `AbstractSessionValidationFilter.sendUnauthorized()` phải trả RFC 7807 ProblemDetail thay vì JSON string thủ công.
- **Validation**:
  - Response body: `{"type":"...", "title":"Unauthorized", "status":401, "detail":"..."}`
  - Content-Type: `application/problem+json`
  - Content-Language header có mặt (từ BaseRequestContextFilter)

### FR-012: Wrap AuthSessionValidationFilter với ObservableFilterWrapper [ENRICHED]
- **Actor**: auth-service
- **Action**: Hệ thống phải wrap `AuthSessionValidationFilter` qua `ObservableFilterWrapper.wrap()` để tự động có duration metric + OpenTelemetry tracing. Business metrics (counter `auth.token.validation`) giữ trong filter body.
- **Validation**:
  - Metric `http.filter.authSessionValidation.duration` được ghi nhận
  - Counter `auth.token.validation` (result=success/failure, reason=...) hoạt động như cũ

## 3. Non-functional Requirements

- **NFR-001 — Zero Performance Regression**: Giảm filter chain overhead (xóa duplicate filters, giảm 231 LOC net)
- **NFR-002 — Backward Compatibility**: 100% tương thích ngược cho downstream services không thay đổi config
- **NFR-003 — Phased Deployment**: Mỗi phase deployable independently (base-core → account/sysadmin → auth → error)
- **NFR-004 — Content-Language Coverage**: 100% response (200, 4xx, 5xx) đều có Content-Language header trên tất cả services

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR.

## 5. Enriched Domain Requirements

### Enriched FRs
- **FR-011**: Chuẩn hóa RFC 7807 cho security filter error responses. Lý do: Hiện tại `AbstractSessionValidationFilter.sendUnauthorized()` trả JSON string thủ công, không consistent với `BaseControllerAdvice` ProblemDetail.
- **FR-012**: Wrap filter với ObservableFilterWrapper. Lý do: Reuse cơ sở hạ tầng observability sẵn có, tránh tạo MetricsRecorder class riêng.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Không có | Internal architecture consolidation | Không phụ thuộc third-party API |

## 6. Assumptions

- auth-service tự chịu trách nhiệm toàn bộ xác thực/phân quyền qua `AuthSessionValidationFilter` + SecurityConfig
- Resource servers (account/sysadmin) dùng `DefaultSessionValidationFilter` mặc định (no revocation check)
- `BaseRequestContextFilter` chạy ở order `-2000`, TRƯỚC tất cả filter khác → `LocaleContextHolder.getLocale()` trả đúng locale (fallback = default)
- notification-service không nằm trong scope change này

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không |
| Đầy đủ (Completeness) | 23/25 | FR-008: Cần verify anonymous token claims structure khớp với JwtService.generateAnonymousToken() (-1); FR-006: Cần verify fingerprint validation behavior khi migrate (-1) |
| Nhất quán (Consistency) | 24/25 | FR-011: ProblemDetail format cần align với BaseControllerAdvice.handleException() (-1) |
| Kiểm thử được (Testability) | 24/25 | FR-003/FR-004: Cần verify SecurityConfig authorization rules vẫn work sau khi xóa addFilterBefore (-1) |
| **Tổng** | **96/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Completeness | -1 | FR-008 | Anonymous token claims structure cần verify khớp generateAnonymousToken() | Unit test anonymous flow trước và sau |
| 2 | Completeness | -1 | FR-006 | Fingerprint validation behavior khi migrate từ JwtAuthFilter | Verify FingerprintService.validateFingerprint() hoạt động đúng context |
| 3 | Consistency | -1 | FR-011 | ProblemDetail format từ filter vs ControllerAdvice cần align | Tạo shared ProblemDetail builder utility |
| 4 | Testability | -1 | FR-003 | Authorization rules verify cần integration test | Test SecurityConfig.securityFilterChain() với DefaultSessionValidF |

---

## 8. Issues & Risks

- 🟡 **Filter type transition risk** — FR-003, FR-004: `ResourceJwtAuthFilter` dùng security chain (`addFilterBefore`), `DefaultSessionValidationFilter` dùng servlet filter registration. Cần verify authorization rules.
- 🟡 **JwtAuthFilter → AuthSessionValidationFilter behavior change** — FR-006: Refactor God object (236 LOC) có risk thay đổi behavior, đặc biệt exception handling flow (catch-all vs fail-fast).
- 🟡 **Coordinated deployment** — FR-001: base-core phải deploy trước services. Nếu service upgrade base-core nhưng chưa xóa ResourceJwtAuthFilter → có thể chạy 2 filter cùng lúc.

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 Warning | Filter type transition (security chain → servlet) | FR-003, FR-004 | Integration test authorization rules sau migration |
| 2 | Risk | 🟡 Warning | Behavior change khi refactor God object | FR-006 | Unit test + integration test trước/sau, verify exception flow |
| 3 | Risk | 🟡 Warning | Coordinated deployment dependency | FR-001 | Phase-by-phase: base-core → services. Document deployment order |

## 9. Open Questions

Không có câu hỏi mở. (Tất cả 5 open questions đã resolved trong brainstorm session)

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Framework Starter & Security Infrastructure (`base-web-starter`, `base-security-starter`)
- Service Security Configuration (`auth-service`, `account-service`, `system-admin-service`)

### 10.2 Flow Type
- Command / Configuration Infrastructure

### 10.3 Candidate Services
- `components/base-core/starters/base-web-starter`: Chứa `BaseRequestContextFilter.kt`, `RequestContextProperties.kt`
- `components/base-core/starters/base-security-starter`: Chứa `AbstractSessionValidationFilter.kt`, `DefaultSessionValidationFilter.kt`, `SecurityAutoConfiguration.kt`
- `components/base-core/src`: Chứa `BaseControllerAdvice.kt`, `OrderConstants.kt`
- `services/auth-service`: Chứa `JwtAuthFilter.kt`, `ContentLanguageFilter.kt`, `SecurityConfig.kt`
- `services/account-service`: Chứa `ResourceJwtAuthFilter.kt`, `SecurityConfig.kt`
- `services/system-admin-service`: Chứa `ResourceJwtAuthFilter.kt`, `SecurityConfig.kt`

### Detection Evidence
- Keyword: `BaseRequestContextFilter` → Module: `base-web-starter` → File: `src/main/kotlin/com/ntt/basecore/web/filter/BaseRequestContextFilter.kt`
- Keyword: `ContentLanguageFilter` → Module: `auth-service` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt`
- Keyword: `ResourceJwtAuthFilter` → Module: `account-service` → File: `src/main/kotlin/com/ntt/accountservice/shared/security/ResourceJwtAuthFilter.kt`
- Keyword: `ResourceJwtAuthFilter` → Module: `system-admin-service` → File: `src/main/kotlin/com/ntt/systemadminservice/shared/security/ResourceJwtAuthFilter.kt`
- Keyword: `JwtAuthFilter` → Module: `auth-service` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
- Keyword: `DefaultSessionValidationFilter` → Module: `base-security-starter` → File: `src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/DefaultSessionValidationFilter.kt`
- Keyword: `AbstractSessionValidationFilter` → Module: `base-security-starter` → File: `src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/AbstractSessionValidationFilter.kt`
- Archive: `2026-09-30-fix-session-filter-autoconfig-conflict` → MAINTENANCE predecessor

### 10.4 External Integrations
- Không có (Internal architecture consolidation)

### 10.5 Required Modules
- `components/base-core/starters/base-web-starter`
- `components/base-core/starters/base-security-starter`
- `components/base-core/src` (BaseControllerAdvice)
- `services/auth-service`
- `services/account-service`
- `services/system-admin-service`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | DevOps | Deploy base-core v.next (Phase 1) | Maven Local |
| 2 | System | BaseRequestContextFilter set Content-Language header automatically | base-web-starter |
| 3 | DevOps | Deploy account-service + sysadmin-service (Phase 2) | CI/CD |
| 4 | System | DefaultSessionValidationFilter auto-registers, ResourceJwtAuthFilter removed | base-security-starter |
| 5 | DevOps | Deploy auth-service (Phase 3) | CI/CD |
| 6 | System | AuthSessionValidationFilter replaces JwtAuthFilter, ContentLanguageFilter removed | auth-service |
| 7 | Client | Request đến service → filter chain: BaseRequestCtxF(-2000) → AuthSessionValidF(-1700) → SecurityChain | All services |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Code Review | Phase 1 | `BaseRequestContextFilter` (`base-web-starter`) | Pending |
| FR-002 | Code Review | Phase 1 | `BaseControllerAdvice` (`base-core/src`) | Pending |
| FR-003 | Code Review | Phase 2 | `ResourceJwtAuthFilter`, `SecurityConfig` (`account-service`) | Pending |
| FR-004 | Code Review | Phase 2 | `ResourceJwtAuthFilter`, `SecurityConfig` (`system-admin-service`) | Pending |
| FR-005 | Code Review | Phase 2 | `application.yml` (`account-service`, `system-admin-service`) | Pending |
| FR-006 | Code Review | Phase 3 | `AuthSessionValidationFilter` (new), `SecurityConfig` (`auth-service`) | Pending |
| FR-007 | Code Review | Phase 3 | `AuthSessionValidationFilter` → `TokenBlacklistCacheService` | Pending |
| FR-008 | Code Review | Phase 3 | `AuthSessionValidationFilter.onAuthenticationSuccess()` | Pending |
| FR-009 | Code Review | Phase 3 | `ContentLanguageFilter.kt` (`auth-service`) — DELETE | Pending |
| FR-010 | Code Review | Phase 3 | `SecurityConfig.kt` (`auth-service`) — KEEP chain filters | Pending |
| FR-011 | Enriched | Phase 4 | `AbstractSessionValidationFilter.sendUnauthorized()` (`base-security-starter`) | Pending |
| FR-012 | Enriched | Phase 4 | `ObservableFilterWrapper.wrap()` → `AuthSessionValidationFilter` | Pending |

### Change Impact Map (MAINTENANCE)

```
FR-001 → [MODIFY] BaseRequestContextFilter (base-web-starter/src/.../filter/BaseRequestContextFilter.kt)
FR-001 → [MODIFY] RequestContextProperties (base-web-starter/src/.../config/RequestContextProperties.kt)
FR-002 → [MODIFY] BaseControllerAdvice (base-core/src/.../domain/web/BaseControllerAdvice.kt) — comment only
FR-003 → [DELETE] ResourceJwtAuthFilter (account-service/src/.../shared/security/ResourceJwtAuthFilter.kt)
FR-003 → [MODIFY] SecurityConfig (account-service/src/.../shared/config/SecurityConfig.kt)
FR-004 → [DELETE] ResourceJwtAuthFilter (system-admin-service/src/.../shared/security/ResourceJwtAuthFilter.kt)
FR-004 → [MODIFY] SecurityConfig (system-admin-service/src/.../shared/config/SecurityConfig.kt)
FR-005 → [MODIFY] application.yml (account-service, system-admin-service)
FR-006 → [ADD]    AuthSessionValidationFilter (auth-service/src/.../shared/security/AuthSessionValidationFilter.kt)
FR-007 → [REUSE]  TokenBlacklistCacheService (auth-service — no changes needed)
FR-008 → [ADD]    AuthSessionValidationFilter.onAuthenticationSuccess() — new logic
FR-009 → [DELETE] ContentLanguageFilter (auth-service/src/.../filter/ContentLanguageFilter.kt)
FR-010 → [MODIFY] SecurityConfig (auth-service/src/.../shared/config/SecurityConfig.kt) — remove jwtAuthFilter
FR-011 → [MODIFY] AbstractSessionValidationFilter (base-security-starter/src/.../session/AbstractSessionValidationFilter.kt)
FR-012 → [MODIFY] SecurityConfig or FilterRegistration (auth-service) — wrap with ObservableFilterWrapper
```

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Change này là **continuation** của archive `2026-09-30-fix-session-filter-autoconfig-conflict` — archive đã thêm `session-validation.enabled` toggle, change này hoàn thiện migration.
- Scope lớn (6 modules, 4 phases) nhưng mỗi phase independent → risk manageable.
- `DefaultSessionValidationFilter` đã sẵn sàng cho account/sysadmin — chỉ cần xóa `ResourceJwtAuthFilter` và verify.
- JwtAuthFilter refactor là phần phức tạp nhất (236 LOC → pipeline) — cần unit test coverage trước refactor.

### Related Features / Precedents
- `2026-09-30-fix-session-filter-autoconfig-conflict`: Predecessor trực tiếp. Đã add `@ConditionalOnProperty` cho DefaultSessionValidationFilter.
- `2026-09-30-fix-security-autoconfig-bean-wiring`: Sửa bean wiring cho DynamicAuthorizationManager.
- `2026-08-21-api-response-i18n-standard`: Đã define chuẩn i18n response headers.
- `2026-09-28-shared-request-context`: Đã consolidate BaseRequestContextFilter.

### Integration Notes
- Không có external integration.
- Internal dependency: base-core MUST deploy trước services.

### Suggested Approach
1. **Phase 1** (base-core): Thêm 1 line `response.setHeader("Content-Language", ...)` vào BaseRequestContextFilter + property toggle
2. **Phase 2** (account/sysadmin): Xóa file, sửa SecurityConfig, add yml config. Verify với integration test.
3. **Phase 3** (auth-service): Tạo AuthSessionValidationFilter, migrate logic từ JwtAuthFilter, xóa ContentLanguageFilter, sửa SecurityConfig.
4. **Phase 4** (base-core): Sửa sendUnauthorized() → ProblemDetail, verify format consistency.

### Context from Confluence Images
N/A
