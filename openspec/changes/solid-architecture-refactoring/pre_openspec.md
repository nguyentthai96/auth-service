# Pre-OpenSpec: solid-architecture-refactoring

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: URD (File — research output từ `/wf_feature_research`)
> **Classification Evidence**: `LoginHandler` → `auth/application/command/LoginHandler.kt` (17 deps, SRP violation), `PolicyEvaluator` → `pbac/application/PolicyEvaluator.kt` (OCP violation), `RolePermissionController` → `rbac/adapter/in/web/RolePermissionController.kt` (DIP violation)
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Refactoring kiến trúc `auth-service` để tuân thủ nguyên lý SOLID và áp dụng Design Patterns phù hợp. Bao gồm: tách God Class LoginHandler (Chain of Responsibility), thêm CommandBus/Mediator, chuẩn hóa Strategy Pattern cho MFA/Captcha, tạo Application Layer cho RBAC/PBAC, và Enum Strategy cho PolicyEvaluator.

| Metric | Giá trị |
|--------|---------|
| Số FR | 20 (URD: 15, Enriched: 5) |
| Issues | 4 (🔴: 1, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Developer**: Người refactor code, thay đổi cấu trúc nội bộ
- **System (auth-service)**: Hệ thống chạy các design patterns mới
- **Client (API consumer)**: KHÔNG bị ảnh hưởng — API contracts giữ nguyên
- **Admin**: Sử dụng RBAC/PBAC endpoints (contracts không đổi)

## 2. Functional Requirements

### FR-001: Tách LoginHandler thành Pipeline [URD]
- **Actor**: System
- **Action**: Hệ thống phải phân tách `LoginHandler` (17 dependencies, 254 LOC) thành 5 pipeline steps tuần tự sử dụng Chain of Responsibility pattern
- **Validation**: Mỗi step chỉ có 3-5 dependencies, tổng behavior giữ nguyên

### FR-002: Tạo AuthenticationStep interface [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp interface `AuthenticationStep` với contract `execute(context): StepResult`
- **Validation**: Interface phải hỗ trợ `Continue` và `ShortCircuit` results

### FR-003: Tạo AuthenticationContext [URD]
- **Actor**: System
- **Action**: Hệ thống phải tạo data class `AuthenticationContext` chứa accumulated state qua pipeline
- **Validation**: Context phải thread-safe cho single-request scope

### FR-004: Tạo SecurityPreCheckStep [URD]
- **Actor**: System
- **Action**: Hệ thống phải kiểm tra RateLimit, Lockout, và Captcha trong step đầu tiên
- **Validation**: Step short-circuit nếu account locked hoặc captcha invalid

### FR-005: Tạo CredentialVerificationStep [URD]
- **Actor**: System
- **Action**: Hệ thống phải verify user credentials và upgrade password hash nếu cần
- **Validation**: Return `Continue` nếu valid, throw nếu invalid

### FR-006: Tạo PolicyEnforcementStep [URD]
- **Actor**: System
- **Action**: Hệ thống phải enforce password expiry, MFA check, và session limit
- **Validation**: Short-circuit `MfaRequired` nếu MFA triggered

### FR-007: Tạo SessionEstablishmentStep [URD]
- **Actor**: System
- **Action**: Hệ thống phải tạo login session, xử lý device fingerprint, và promote anonymous session
- **Validation**: Session được persist vào DB

### FR-008: Tạo TokenIssuanceStep [URD]
- **Actor**: System
- **Action**: Hệ thống phải generate JWT tokens và record login events
- **Validation**: JWT chứa đầy đủ claims

### FR-009: Implement CommandBus [URD]
- **Actor**: System
- **Action**: Hệ thống phải cung cấp `CommandBus` interface dispatch commands tới handler dựa trên type matching
- **Validation**: Handler auto-discovery via Spring `List<CommandHandler>` injection

### FR-010: Giảm dependencies CqrsAuthController [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải giảm constructor dependencies của `CqrsAuthController` từ 13 xuống ≤ 5 thông qua CommandBus
- **Validation**: Controller chỉ inject `CommandBus`, `QueryBus`, và 3 auxiliary services

### FR-011: Tạo MfaProvider interface [URD]
- **Actor**: System
- **Action**: Hệ thống phải cung cấp `MfaProvider` interface với `initiate()`, `verify()`, `isConfigured()` methods
- **Validation**: Mỗi MFA method (SMS, EMAIL, TOTP) là 1 implementation

### FR-012: Tạo MfaProviderRegistry [URD]
- **Actor**: System
- **Action**: Hệ thống phải auto-register tất cả `MfaProvider` beans và dispatch dựa trên `method` string
- **Validation**: Spring auto-inject `List<MfaProvider>`, registry resolve O(1)

### FR-013: Fix CaptchaController OCP [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải delegate captcha challenge generation cho `CaptchaStrategyRegistry` thay vì hardcode `when` clause trong controller
- **Validation**: Controller chỉ gọi `registry.getStrategy(type).generateChallenge()`

### FR-014: Tạo RBAC Application Use Cases [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải tạo `RolePermissionUseCase` và `DomainManagementUseCase` để RBAC controllers gọi thay vì JPA repositories trực tiếp
- **Validation**: Controllers không inject `*Repository`, chỉ inject Use Cases

### FR-015: Tạo PBAC Application Use Cases [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải tạo `PolicyManagementUseCase` để `PolicyController` gọi thay vì `PolicyRepository` trực tiếp
- **Validation**: PolicyController không import persistence package

### FR-016: Tạo ConditionOperator enum [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải thay `when` clause trong `PolicyEvaluator.evaluateCondition()` bằng enum class `ConditionOperator` chứa evaluate lambda
- **Validation**: Thêm operator mới chỉ cần thêm enum entry

### FR-017: Tối ưu PolicyEvaluator iteration [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải giảm từ 2 lần duyệt policies (DENY + ALLOW) xuống 1 lần duyệt duy nhất
- **Validation**: Performance benchmark ≤ current

### FR-018: Tạo Port interfaces cho RBAC/PBAC [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải tạo `RolePermissionPort`, `PolicyPort` interfaces tại application layer để decouple từ JPA repositories
- **Validation**: Use Cases chỉ reference Port, không reference Entity/Repository

### FR-019: Fix N+1 query trong RolePermissionController [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải thay loop query từng permission bằng batch JOIN query trong `RolePermissionUseCase`
- **Validation**: Chỉ 1 SQL query cho `listPermissions(roleId)`

### FR-020: Tạo LockoutPort abstraction [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải thay trực tiếp `RedisLockoutAdapter` trong `AccountLockoutService` bằng `LockoutPort` interface
- **Validation**: AccountLockoutService không import adapter package

## 3. Non-functional Requirements

- **NFR-001**: API contracts (HTTP endpoints, request/response DTOs) KHÔNG được thay đổi
- **NFR-002**: Performance không được degradation (< 1μs overhead cho CommandBus dispatch)
- **NFR-003**: Test coverage cho mỗi component mới ≥ 80%
- **NFR-004**: Không yêu cầu database migration
- **NFR-005**: Backward compatibility — existing integration tests phải pass

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-016**: ConditionOperator enum — OCP compliance cho policy evaluation
- **FR-017**: Single-pass policy evaluation — performance optimization
- **FR-018**: Port interfaces cho RBAC/PBAC — DIP compliance
- **FR-019**: N+1 query fix — database performance
- **FR-020**: LockoutPort abstraction — DIP compliance

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Cache, rate limit, lockout counter | Không thay đổi integration — chỉ thêm Port abstraction |
| Kafka | Event publishing | Không thay đổi |
| Notification Service | Email notifications | Không thay đổi |
| PostgreSQL | Primary data store | Không thay đổi — chỉ optimize queries |

## 6. Assumptions

- A-001: `eventsourcing-utils` đã cung cấp `CommandHandler<C,R>` interface — reuse thay vì tạo mới
- A-002: Tất cả existing tests sẽ được update để mock CommandBus thay vì individual handlers
- A-003: BaseController inheritance pattern sẽ giữ nguyên (ResponseBodyAdvice deferred)
- A-004: RbacControllers.kt (13KB mega file) sẽ được split thành 3+ files nhỏ hơn

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-003: "thread-safe cho single-request scope" cần clarify scope chi tiết |
| Đầy đủ (Completeness) | 22/25 | FR-020: LockoutPort chưa specify đầy đủ method signatures |
| Nhất quán (Consistency) | 22/25 | FR-009 vs FR-001: CommandBus dispatch vs Pipeline — cần clarify interaction |
| Kiểm thử được (Testability) | 21/25 | FR-017: "Performance benchmark ≤ current" chưa có baseline number |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|----------------|
| 1 | Clarity | -2 | FR-003 | AuthenticationContext "thread-safe" mơ hồ — request-scoped by default | Ghi rõ "request-scoped, created per pipeline execution" |
| 2 | Completeness | -3 | FR-020 | LockoutPort method signatures chưa define | Specify `recordFailedAttempt()`, `isLocked()`, `unlock()` |
| 3 | Consistency | -3 | FR-009/FR-001 | Interaction giữa CommandBus dispatch và Pipeline execution chưa rõ | Ghi rõ "CommandBus → LoginHandler → Pipeline" flow |
| 4 | Testability | -4 | FR-017 | "Performance ≤ current" không có baseline metric | Chạy JMH benchmark trước refactoring |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | LoginHandler refactoring có thể break 5+ integration tests | FR-001 | Run tests sau mỗi step extraction |
| 2 | Risk | 🟡 | CommandBus thêm layer indirection — debugging khó hơn | FR-009 | Logging decorator cho CommandBus |
| 3 | Risk | 🟡 | RbacControllers.kt split có thể miss endpoint registration | FR-014 | Verify tất cả endpoints sau split |
| 4 | Missing | 🟡 | Chưa define rollback strategy nếu refactoring fail giữa chừng | ALL | Git branch strategy, commit per step |

## 9. Open Questions

- **OQ-001**: `CommandHandler<C,R>` từ `eventsourcing-utils` có sẵn `commandType()` method không? Hay cần extend?
- **OQ-002**: `AuthenticationContext` nên là `data class` (immutable copy) hay `class` (mutable)? Trade-off: immutable an toàn hơn nhưng performance kém hơn cho pipeline.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication, Authorization (RBAC + PBAC)

### 10.2 Flow Type
Command (internal refactoring — no user-facing transaction flow)

### 10.3 Candidate Services
- **auth-service**: Keyword match "LoginHandler", "CommandHandler", "MfaService", "CaptchaController", "PolicyEvaluator", "RolePermissionController" — toàn bộ refactoring scope nằm trong service này

### Detection Evidence
- Keyword: `LoginHandler` → Module: `auth/application/command` → File: `LoginHandler.kt`
- Keyword: `PolicyEvaluator` → Module: `pbac/application` → File: `PolicyEvaluator.kt`
- Keyword: `CommandHandler` → Module: `auth/application/command` → File: 9 handler files
- Keyword: `RolePermissionController` → Module: `rbac/adapter/in/web` → File: `RolePermissionController.kt`

### 10.4 External Integrations
- Redis (cache/lockout) — existing, thêm Port abstraction
- Notification Service (email) — existing, không thay đổi
- Kafka (events) — existing, không thay đổi
- PostgreSQL — existing, optimize queries

### 10.5 Required Modules
- `auth` — Pipeline, CommandBus, MFA Strategy
- `rbac` — Application Layer + Ports
- `pbac` — Application Layer + Ports + ConditionOperator
- `shared` — CommandBus/QueryBus infrastructure

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Refactor code | auth-service |
| 2 | System | Apply design patterns | Internal restructuring |
| 3 | System | Run tests | Verify behavior unchanged |

> Note: Đây là refactoring task, không có user-facing transaction flow.

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Research/business_analysis UC-001 | Pipeline | `LoginHandler.kt` | [MODIFY] |
| FR-002 | Research/technical_spec §2.1 | Pipeline | NEW `AuthenticationStep.kt` | [ADD] |
| FR-003 | Research/technical_spec §2.1 | Pipeline | NEW `AuthenticationContext.kt` | [ADD] |
| FR-004 | Research/technical_spec §2.1 | Pipeline | NEW `SecurityPreCheckStep.kt` | [ADD] |
| FR-005 | Research/technical_spec §2.1 | Pipeline | NEW `CredentialVerificationStep.kt` | [ADD] |
| FR-006 | Research/technical_spec §2.1 | Pipeline | NEW `PolicyEnforcementStep.kt` | [ADD] |
| FR-007 | Research/technical_spec §2.1 | Pipeline | NEW `SessionEstablishmentStep.kt` | [ADD] |
| FR-008 | Research/technical_spec §2.1 | Pipeline | NEW `TokenIssuanceStep.kt` | [ADD] |
| FR-009 | Research/technical_spec §2.2 | CommandBus | NEW `CommandBus.kt`, `SpringCommandBus.kt` | [ADD] |
| FR-010 | Research/technical_spec §2.2 | CommandBus | `CqrsAuthController.kt` | [MODIFY] |
| FR-011 | Research/technical_spec §2.3 | MFA | NEW `MfaProvider.kt` | [ADD] |
| FR-012 | Research/technical_spec §2.3 | MFA | NEW `MfaProviderRegistry.kt` | [ADD] |
| FR-013 | Research/comparison_analysis GAP-04 | Captcha | `CaptchaController.kt` | [MODIFY] |
| FR-014 | Research/comparison_analysis GAP-06 | RBAC | NEW `RolePermissionUseCase.kt` | [ADD] |
| FR-015 | Research/comparison_analysis GAP-07 | PBAC | NEW `PolicyManagementUseCase.kt` | [ADD] |
| FR-016 | Research/technical_spec §2.5 | PBAC | NEW `ConditionOperator.kt` | [ADD] |
| FR-017 | Research/technical_spec §2.5 | PBAC | `PolicyEvaluator.kt` | [MODIFY] |
| FR-018 | Research/comparison_analysis GAP-06,07 | Ports | NEW `RolePermissionPort.kt`, `PolicyPort.kt` | [ADD] |
| FR-019 | Research/comparison_analysis GAP-08 | RBAC | `RolePermissionController.kt` | [MODIFY] |
| FR-020 | Research/comparison_analysis GAP-09 | Auth | `AccountLockoutService.kt` | [MODIFY] |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này là **pure refactoring** — không có business logic thay đổi, chỉ restructuring code
- Rủi ro cao nhất tại FR-001 (LoginHandler) vì có 5+ integration tests phụ thuộc trực tiếp
- Quick Wins (FR-013, FR-016) có thể deliver trước để build confidence
- Tổng effort ước tính: 37-52 giờ (6 phases)

### Related Features / Precedents
- Archive `2026-08-05-architecture-optimization` — đã có lần optimize trước, nhưng scope nhỏ hơn
- Archive `2026-08-21-api-response-i18n-standard` — đã chuẩn hóa ApiResponse, baseline tốt cho refactoring

### Integration Notes
- Redis: Thêm `LockoutPort` interface nhưng `RedisLockoutAdapter` vẫn là implementation duy nhất
- Kafka: `LoginEventRecorder` sẽ nằm trong `TokenIssuanceStep` — không thay đổi event format
- Notification Service: `NotificationPort` (đã có) — `NewDeviceMailHandler` không thay đổi

### Suggested Approach
1. **Phase 1a** (Quick Wins): Fix `CaptchaController` OCP + `ConditionOperator` enum → 2-4h, risk thấp
2. **Phase 1b**: RBAC/PBAC Application Layer → tạo Use Cases + Ports → 8-12h
3. **Phase 2a**: CommandBus infrastructure → `shared/cqrs/` package → 4-6h
4. **Phase 2b**: Authentication Pipeline → tách `LoginHandler` → 12-16h (highest risk)
5. **Phase 2c**: MFA Provider Strategy → tách `MfaService` → 6-8h
6. Mỗi phase commit riêng, run tests sau mỗi phase

### Context from Confluence Images
N/A — Source là local research files, không có Confluence images.
