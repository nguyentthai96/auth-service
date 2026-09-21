# Pre-OpenSpec: controller-request-context

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: `getCurrentUserId` → `auth/adapter/in/web/` → `CqrsAuthController.kt`, `SessionController.kt`, `MfaController.kt`, `DeviceController.kt`, `SsoController.kt`, `AccountLifecycleController.kt`, `AdminUnlockController.kt` (7 duplicate private functions)
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Chuẩn hóa cấu trúc Controller layer trong auth-service để loại bỏ code lặp, quản lý request context tập trung, và truyền data giữa các tầng Clean Architecture dễ dàng hơn. Feature bao gồm: tạo `RequestContext` (request-scoped bean), base controller hierarchy (`BaseController` → `AuthenticatedController` → `AdminController`), response helpers (ok/created/okMessage/pagedResponse), và convention cho DTO→Command mapping.

| Metric | Giá trị |
|--------|---------|
| Số FR | 12 (Idea: 9, Enriched: 3) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Developer**: Sử dụng base controller và request context khi viết controller mới hoặc refactor controller cũ
- **System (Spring IoC)**: Tự động populate RequestContext qua Filter trước khi request đến controller

## 2. Functional Requirements

### FR-001: Tạo RequestContext bean [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải cung cấp một `@RequestScope` bean `RequestContext` chứa thông tin: userId, username, jti, roles, clientIp, userAgent, deviceFingerprint, correlationId, requestId, locale, anonymousSessionId, anonymousTokenJti
- **Validation**: Bean phải thread-safe (request-scoped), không null khi inject

### FR-002: Tự động populate RequestContext [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tự động populate `RequestContext` từ `SecurityContextHolder` (authentication data) và `HttpServletRequest` (headers) thông qua `RequestContextFilter` chạy SAU Spring Security filter chain
- **Validation**: Filter order phải sau SecurityFilterChain, trước DispatcherServlet

### FR-003: MDC correlation propagation [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải propagate `correlationId`, `userId`, `clientIp` vào MDC trong `RequestContextFilter`, cleanup trong `finally` block
- **Validation**: MDC keys phải consistent: `correlationId`, `userId`, `clientIp`

### FR-004: Tạo BaseController với response helpers [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp abstract `BaseController` class với response helpers: `ok(body)`, `ok(body, messageKey)`, `okMessage(messageKey)`, `created(body)`, `noContent()`, `message(key, args)`, `pagedResponse(page)`
- **Validation**: Tất cả helpers phải return `ResponseEntity<*>`, i18n phải dùng `requestContext.locale`

### FR-005: Tạo AuthenticatedController [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `AuthenticatedController` extends `BaseController` với helper methods: `currentUserId()`, `currentJti()`, `currentRoles()`, `requireRole(role)`
- **Validation**: `currentUserId()` phải throw exception nếu chưa authenticated

### FR-006: Tạo AdminController [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `AdminController` extends `AuthenticatedController` với `requireAdmin()` method kiểm tra role `ROLE_ADMIN` hoặc `ROLE_SUPER_ADMIN`
- **Validation**: Throw `AccessDeniedException` nếu không có admin role

### FR-007: Convention DTO→Command mapping [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp convention dùng Kotlin extension function `fun DtoClass.toCommand(ctx: RequestContext): CommandClass` để map DTO sang Command
- **Validation**: Extension function phải nằm cùng file DTO hoặc file `DtoMappers.kt` riêng

### FR-008: Correlationid extraction chuẩn [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải extract correlation ID theo thứ tự: `X-Correlation-ID` → `X-Request-ID` → auto-generated UUID — nhất quán cho TẤT CẢ endpoints
- **Validation**: Không còn controller nào tự extract correlation ID

### FR-009: Client IP extraction chuẩn [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải extract client IP theo thứ tự: `X-Forwarded-For` (first value) → `request.remoteAddr` — nhất quán cho TẤT CẢ endpoints
- **Validation**: Không còn controller/filter nào duplicate `extractClientIp()`

### FR-010: Backward compatibility [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo KHÔNG thay đổi API contract (request/response format) của bất kỳ endpoint nào trong quá trình migration
- **Validation**: Tất cả existing tests phải pass sau migration

### FR-011: Incremental migration support [ENRICHED]
- **Actor**: Developer
- **Action**: Migration phải có thể thực hiện controller-by-controller — cũ và mới cùng tồn tại song song
- **Validation**: Mỗi phase migration phải compilable và test-pass

### FR-012: RequestContext test support [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp test helper/factory để tạo mock `RequestContext` trong unit tests
- **Validation**: Controller unit test phải dễ dàng setup `RequestContext` không cần full Spring context

## 3. Non-functional Requirements

- **Performance**: RequestContextFilter không được thêm > 2ms latency per request
- **Thread Safety**: RequestContext phải thread-safe (guaranteed bởi `@RequestScope`)
- **Maintainability**: Giảm ≥50% duplicate code trong controller layer

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp — tất cả FR đều có scope riêng biệt.

## 5. Enriched Domain Requirements

Bổ sung 3 enriched FRs:

### Enriched FRs
- **FR-010** [ENRICHED]: Backward compatibility — domain requirement bắt buộc cho refactoring
- **FR-011** [ENRICHED]: Incremental migration — best practice cho safe refactoring
- **FR-012** [ENRICHED]: Test support — đảm bảo testability sau refactoring

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Không có | Đây là internal refactoring | Không tích hợp hệ thống ngoài |

## 6. Assumptions

- `@RequestScope` hoạt động đúng với virtual threads (Spring Boot 3.x default)
- `BaseControllerAdvice` từ base-core không conflict với `BaseController` mới
- CGLIB proxy cho request-scoped bean không gây performance issue đáng kể

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-001: Nhiều fields trong RequestContext — cần verify đủ fields chưa |
| Đầy đủ (Completeness) | 22/25 | FR-007: Chưa specify rõ convention cho Query object (chỉ nói Command) |
| Nhất quán (Consistency) | 23/25 | FR-004: cần xác nhận `PublicController` có cần thiết hay merge vào `BaseController` |
| Kiểm thử được (Testability) | 20/25 | FR-012: Test helper mô tả chung, cần thiết kế cụ thể hơn |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích Idea) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-001 | User nói "lấy header đa số sẽ lấy chung mẫu" — chưa liệt kê hết headers cần extract | Liệt kê đầy đủ headers trong technical spec |
| 2 | Completeness | -3 | FR-007 | User nói "mapping request dto" nhưng chưa đề cập Query pattern | Bổ sung convention cho Query DTO mapping |
| 3 | Consistency | -2 | FR-004 | Idea đề cập "PublicController" nhưng research spec không rõ khi nào dùng Base vs Public | Clarify hierarchy rule |
| 4 | Testability | -5 | FR-012 | Test helper chung chung, chưa có concrete API | Thiết kế TestRequestContextFactory |

---

## 8. Issues & Risks

- 🟡 `@RequestScope` và Virtual Threads — Spring Boot 3.x hỗ trợ nhưng cần verify khi enable `spring.threads.virtual.enabled=true` — FR-001, FR-002
- 🟡 Existing `ClientMetadataFilter` overlap — đã extract một số headers vào MDC → cần consolidate hoặc coordinate với `RequestContextFilter` — FR-002, FR-003

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Virtual Thread + @RequestScope compatibility | FR-001 | Test với `spring.threads.virtual.enabled=true` |
| 2 | Conflict | 🟡 | ClientMetadataFilter đã extract X-App-Version, X-Client-Platform vào MDC | FR-002 | Consolidate: RequestContextFilter delegate/coordinate với ClientMetadataFilter |

## 9. Open Questions

- **Q1**: `ClientMetadataFilter` hiện tại extract `X-App-Version`, `X-Client-Platform` vào MDC. `RequestContextFilter` mới cũng cần populate MDC. Nên merge 2 filter hay coordinate order?
- **Q2**: `SelfServiceUnlockController` dùng Thymeleaf (SSR) — có nên migrate sang base controller pattern không, hay exclude?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- auth (authentication, session management, MFA, SSO, password, device, anonymous auth)
- rbac (role-based access control — domain, role, group, resource, permission)
- pbac (policy-based access control)
- shared (exception, filter, security, i18n, config, cache, audit, persistence)

### 10.2 Flow Type
Command (infrastructure refactoring — no transaction flow)

### 10.3 Candidate Services
- **auth-service**: Primary — tất cả controllers nằm trong service này
  - Evidence: `getCurrentUserId()` duplicate 7 lần trong `auth/adapter/in/web/*.kt`

### Detection Evidence
- Keyword: `getCurrentUserId` → Module: `auth/adapter/in/web` → File: `CqrsAuthController.kt` (line 258)
- Keyword: `extractClientIp` → Module: `auth/adapter/in/web` → File: `CqrsAuthController.kt` (line 269)
- Keyword: `SecurityContextHolder.getContext().authentication` → Module: `auth/adapter/in/web` → File: 7 controllers
- Keyword: `LocaleContextHolder.getLocale()` → Module: `auth/adapter/in/web` → File: 15+ locations
- Keyword: `messageSource.getMessage` → Module: `auth/adapter/in/web` → File: all controllers with i18n

### 10.4 External Integrations
Không có — internal refactoring

### 10.5 Required Modules
- `auth/adapter/in/web/` — 16 controllers cần refactor
- `rbac/adapter/in/web/` — 5 controllers cần chuẩn hóa
- `pbac/adapter/in/web/` — 1 controller
- `shared/` — nơi đặt infrastructure mới (RequestContext, BaseController)
- `shared/filter/` — nơi đặt RequestContextFilter
- `auth/adapter/in/web/filter/` — ClientMetadataFilter cần coordinate

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | HTTP Request | Gửi request với headers (Authorization, X-Correlation-ID, User-Agent...) | Servlet Container |
| 2 | Spring Security | Authenticate JWT → set SecurityContext | SecurityFilterChain |
| 3 | System | Auto-populate RequestContext từ SecurityContext + Headers | RequestContextFilter |
| 4 | System | Propagate correlationId, userId, clientIp vào MDC | RequestContextFilter |
| 5 | Controller | Inject RequestContext, dùng helper methods | BaseController/AuthenticatedController |
| 6 | Controller | Map DTO → Command/Query using extension functions | DtoMapper extensions |
| 7 | Controller | Dispatch command/query to Handler | Handler.handle() |
| 8 | Controller | Return response using response helpers | BaseController.ok/created/okMessage |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | User Idea | RequestContext.kt | [ADD] RequestContext | Mapped |
| FR-002 | User Idea | RequestContextFilter.kt | [ADD] RequestContextFilter | Mapped |
| FR-003 | User Idea | RequestContextFilter.kt | [ADD] RequestContextFilter (MDC section) | Mapped |
| FR-004 | User Idea | BaseController.kt | [ADD] BaseController | Mapped |
| FR-005 | User Idea | AuthenticatedController.kt | [ADD] AuthenticatedController | Mapped |
| FR-006 | User Idea | AdminController.kt | [ADD] AdminController | Mapped |
| FR-007 | User Idea | DtoMappers.kt | [ADD] DtoMappers + [MODIFY] RequestDtos.kt | Mapped |
| FR-008 | User Idea | RequestContextFilter.kt | [MODIFY] CqrsAuthController, AnonymousAuthController | Mapped |
| FR-009 | User Idea | RequestContextFilter.kt | [MODIFY] CqrsAuthController, LoginRateLimitFilter | Mapped |
| FR-010 | Enriched | N/A | [REUSE] All existing controllers — API không đổi | Mapped |
| FR-011 | Enriched | N/A | [REUSE] Migration strategy — parallel existence | Mapped |
| FR-012 | Enriched | TestRequestContextFactory.kt | [ADD] TestRequestContextFactory | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này có complexity MEDIUM — chủ yếu là structural refactoring, không đổi business logic
- Rủi ro thấp vì API contract không thay đổi
- Code hiện tại rất fragmented — 7 copy-paste `getCurrentUserId()` là red flag rõ ràng
- `BaseControllerAdvice` (base-core) đã tồn tại cho exception handling → pattern tương tự có thể áp dụng cho controller base

### Related Features / Precedents
- `2026-08-21-api-response-i18n-standard` (archive) — đã chuẩn hóa i18n response pattern, liên quan đến FR-004 (response helpers)
- `2026-08-05-architecture-optimization` (archive) — có thể chứa context về kiến trúc tối ưu hóa trước đó

### Integration Notes
- Không tích hợp hệ thống ngoài
- Phụ thuộc base-core: `BaseControllerAdvice`, `ErrorCodeBase` (đã dùng)
- Cần verify compatibility với `spring-boot-starter-web` request scope

### Suggested Approach
1. **Phase 1**: Tạo infrastructure trong `shared/web/` — `RequestContext`, `RequestContextFilter`, `BaseController`, `AuthenticatedController`, `AdminController`
2. **Phase 2**: Migrate từ đơn giản → phức tạp: `SessionController` → `DeviceController` → `AccountLifecycleController` → `MfaController` → `CqrsAuthController`
3. **Phase 3**: Migrate RBAC + PBAC controllers
4. **Phase 4**: Cleanup + xóa duplicate code

### Context from Confluence Images
N/A
