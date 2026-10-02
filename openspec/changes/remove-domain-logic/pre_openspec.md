# Pre-OpenSpec: remove-domain-logic

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: `domainId` → auth.application/rbac.adapter → `DomainEntity`, `SwitchDomainHandler`, `DomainLookupService`, `TokenGenerator`
> **Archive**: N/A
> **Quality Score**: 90/100

## 📋 Feature Summary

Xóa hoàn toàn concept multi-domain khỏi auth-service. Chuyển hệ thống từ domain-scoped RBAC sang flat/global RBAC. Mục tiêu: giảm độ phức tạp hệ thống, tối ưu login flow, loại bỏ code không cần thiết.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (Idea: 11, Enriched: 3) |
| Issues | 3 (🔴: 1, 🟡: 2) |
| Open Questions | 0 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **Developer / Admin**: Người thực hiện migration và verify kết quả
- **System (auth-service)**: Hệ thống cần được refactor bỏ domain concept
- **End User**: Người dùng cuối — login flow đơn giản hơn sau change
- **Frontend (admindashboard)**: Cần cập nhật types/services/pages bỏ domain references

## 2. Functional Requirements

### FR-001: Xóa entity domain khỏi hệ thống [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải xóa hoàn toàn `DomainEntity`, `UserDomainEntity` khỏi codebase
- **Validation**: Không còn reference tới `DomainEntity` hoặc `UserDomainEntity` trong bất kỳ file nào

### FR-002: Rename entity domain-scoped thành global [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải rename `DomainResourceEntity` → `ResourceEntity` và `DomainRoleEntity` → `RoleEntity`, bỏ column `domain_id`
- **Validation**: Entity mới không chứa field `domainId`, table name đổi thành `resources`/`roles`

### FR-003: Xóa domain scope khỏi GroupEntity [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải remove column `domain_id` khỏi `GroupEntity`
- **Validation**: `GroupEntity` không còn field `domainId`, group là global scope

### FR-004: Xóa SwitchDomain handler và command [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải xóa hoàn toàn `SwitchDomainHandler`, `SwitchDomainCommand`, endpoint `/auth/switch-domain`
- **Validation**: Không còn switch-domain endpoint, handler, command

### FR-005: Loại bỏ domain khỏi token generation [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải bỏ claims `domains`, `active_domain` khỏi JWT token. `TokenGenerator` không còn dependency `DomainPort`/`DomainLookupService`
- **Validation**: JWT token không chứa domain-related claims. `AuthToken` model không có `activeDomain`

### FR-006: Chuyển RBAC sang global scope [IDEA]
- **Actor**: Developer
- **Action**: `RbacEngine.hasPermission()`, `getUserRoles()`, `getEffectivePermissions()` phải bỏ param `domainId`, query trực tiếp không filter domain
- **Validation**: Tất cả RBAC methods hoạt động mà không cần `domainId`

### FR-007: Chuyển PasswordPolicy sang global [IDEA]
- **Actor**: Developer
- **Action**: `PasswordPolicyService` phải bỏ `domainId` khỏi tất cả methods. `PasswordPolicyEntity` chỉ cho phép 1 row global policy
- **Validation**: `getPolicy()` không có tham số, `validatePasswordStrength()` không cần `domainId`

### FR-008: Xóa DomainPort và adapter [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải xóa `DomainPort`, `DomainPersistenceAdapter`, `DomainLookupService`, và test files tương ứng
- **Validation**: Không còn file nào reference `DomainPort`

### FR-009: Bỏ domain khỏi events [IDEA]
- **Actor**: Developer
- **Action**: Remove `domainId`/`domainCode` khỏi `TokenIssuedEvent`, `UserRegisteredEvent`, `UserLoggedInEvent` và event publisher
- **Validation**: Downstream consumers (notification, account) không bị ảnh hưởng (đã verify: 0 references)

### FR-010: Xóa DomainController khỏi RBAC [IDEA]
- **Actor**: Developer
- **Action**: Remove `DomainController` hoàn toàn. Controllers RBAC khác bỏ `{domainId}` path variable
- **Validation**: Không còn admin API path chứa `{domainId}`

### FR-011: Cập nhật frontend bỏ domain references [IDEA]
- **Actor**: Developer
- **Action**: Remove `domainCode`/`activeDomain` khỏi frontend types, services, pages (authService, auth.types, account.types, ProfilePage, etc.)
- **Validation**: Frontend compile thành công, không reference domain fields

### FR-012: Database migration loại bỏ domain tables [ENRICHED]
- **Actor**: System
- **Action**: Tạo Flyway migration script: DROP `domains`, `user_domains`, ALTER `domain_resources`→`resources`, `domain_roles`→`roles`, DROP column `domain_id` khỏi `groups` và `password_policies`
- **Validation**: Migration chạy thành công, schema không còn domain-related objects

### FR-013: Xóa domain repositories [ENRICHED]
- **Actor**: Developer
- **Action**: Remove `DomainRepository`, `UserDomainRepository`. Update các repository khác bỏ methods chứa `domainId` (e.g., `findByDomainIdAndCodeAndActiveTrue`)
- **Validation**: Không còn repository method nào nhận `domainId` param

### FR-014: Cập nhật PolicyController bỏ domain scope [ENRICHED]
- **Actor**: Developer
- **Action**: `PolicyController` bỏ `{domainId}` path variable, policies trở thành global
- **Validation**: PBAC policies không còn scope theo domain

## 3. Non-functional Requirements

- **NFR-001**: Login flow giảm ít nhất 1 DB query (bỏ domain lookup)
- **NFR-002**: JWT token size giảm (bỏ domain claims)
- **NFR-003**: Backward compatibility: Cut-over strategy (không cần transitional phase — hệ thống chưa production)

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Tất cả FRs distinct và scoped rõ ràng.

## 5. Enriched Domain Requirements

### Enriched FRs
- **FR-012**: Database migration — cần thiết cho mọi schema change, user không mention explicit nhưng bắt buộc
- **FR-013**: Repository cleanup — hệ quả trực tiếp của entity removal
- **FR-014**: PBAC policy controller — phát hiện qua code scan, PolicyController cũng dùng domainId

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Cache RBAC permissions, rate limit, sessions | Cache key có thể chứa domainId → cần check |
| Kafka | Event publishing (auth events) | Events chứa domainId → cần remove |
| PostgreSQL | Primary database | Migration required |

## 6. Assumptions

- Hệ thống chưa production → cut-over không gây downtime cho end users
- Config versioning domain (`/api/v1/configs/domains/{domainName}`) là config grouping, không liên quan domain RBAC
- Notification-service và account-service không consume `domainId` từ events (đã verify qua grep)

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-011: "Frontend bỏ domain references" — scope rộng, cần list cụ thể files |
| Đầy đủ (Completeness) | 24/25 | FR-012: Seed data migration chưa nêu rõ (data cleanup cho existing rows) |
| Nhất quán (Consistency) | 22/25 | FR-006 & FR-007: Cả 2 đều liên quan RBAC engine refactor, có overlap scope |
| Kiểm thử được (Testability) | 20/25 | FR-001~004: Validation chỉ là "không còn reference" — cần define rõ hơn cách verify |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích idea) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -1 | FR-011 | "Cập nhật frontend bỏ domain references" — scope quá chung | List exact files trong tasks.md |
| 2 | Completeness | -1 | FR-012 | Data migration chưa rõ: existing domain/role data chuyển thế nào? | Specify migration strategy cho existing data |
| 3 | Consistency | -3 | FR-006, FR-007 | Overlap RBAC + password policy, cùng bỏ domainId nhưng scope khác | Tasks.md sẽ phân tách rõ order |
| 4 | Testability | -5 | FR-001~004 | "Không còn reference" khó verify tự động | Thêm grep verification step trong CI |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | JWT tokens cũ sẽ có claims `domains`/`active_domain` — server mới không validate | FR-005 | Cut-over: invalidate all existing tokens khi deploy |
| 2 | Risk | 🟡 | Redis cache keys có thể chứa `domainId` → cache mismatch sau migration | FR-006 | Flush RBAC cache khi deploy migration |
| 3 | Risk | 🟡 | Seed data trong DB có domain references → cần cleanup migration | FR-012 | Migration script xử lý data cleanup |

> Risk #1 giảm severity vì hệ thống chưa production (user confirmed cut-over).

## 9. Open Questions

Không có câu hỏi mở — tất cả 4 open questions đã resolved trong brainstorm phase.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
auth-service (Kotlin/Spring Boot), admindashboard (TypeScript/React)

### 10.2 Flow Type
Command — modifies entities, auth flow, DB schema

### 10.3 Candidate Services
- **auth-service**: Primary target — entities, handlers, ports, services, controllers, events, repositories, migrations
- **admindashboard**: Secondary — types, services, pages bỏ domain references

### Detection Evidence
- Keyword: `domainId`, `domain_id`, `DomainEntity` → Module: `rbac.adapter.out.persistence.entity`, `auth.application` → File: `RbacEntities.kt`, `TokenGenerator.kt`, `RegisterHandler.kt`

### 10.4 External Integrations
- Redis (StringRedisTemplate) — cache keys
- Kafka (KafkaTemplate, @KafkaListener) — events
- PostgreSQL (JPA) — schema migration

### 10.5 Required Modules
- `auth.application.command` — SwitchDomainHandler, RegisterHandler, TokenGenerator, LoginHandler
- `auth.application` — DomainLookupService, PasswordPolicyService
- `auth.application.port.out` — DomainPort
- `auth.adapter.out.persistence` — DomainPersistenceAdapter
- `auth.adapter.in.web.dto` — RequestDtos (SwitchDomainRequestDto), ResponseDtos
- `auth.adapter.in.web` — CqrsAuthController, InternalApiController
- `auth.adapter.in.kafka` — PermissionChangedConsumer
- `auth.domain.event` — TokenIssuedEvent, UserRegisteredEvent, UserLoggedInEvent
- `rbac.adapter.out.persistence.entity` — RbacEntities, PermissionEntities, PasswordPolicyEntity
- `rbac.adapter.out.persistence.repository` — Repositories (DomainRepository, etc.)
- `rbac.adapter.in.web` — RbacControllers (DomainController, etc.)
- `rbac.application` — RbacEngine
- `pbac.adapter.in.web` — PolicyController
- `shared.web` — BaseController, AdminController, AuthenticatedController

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Tạo Flyway migration script | PostgreSQL: DROP/ALTER tables |
| 2 | Developer | Remove domain entities + repositories | auth-service: compile check |
| 3 | Developer | Refactor RBAC chain (RbacEngine, handlers) | auth-service: bỏ domainId param |
| 4 | Developer | Refactor auth flow (TokenGenerator, RegisterHandler) | auth-service: login flow simplified |
| 5 | Developer | Update events + controllers | auth-service: events clean, APIs flat |
| 6 | Developer | Update frontend types/services/pages | admindashboard: compile check |
| 7 | Developer | Run tests + verify | Both: all tests green |


## 12. Traceability Matrix

| FR-ID | Source | Affected Class | Status |
|-------|--------|---------------|--------|
| FR-001 | Idea: "xóa entity domain" | `DomainEntity`, `UserDomainEntity` (RbacEntities.kt) | Mapped |
| FR-002 | Idea: "rename entity" | `DomainResourceEntity`, `DomainRoleEntity` (RbacEntities.kt, PermissionEntities.kt) | Mapped |
| FR-003 | Idea: "bỏ domain_id group" | `GroupEntity` (RbacEntities.kt) | Mapped |
| FR-004 | Idea: "xóa SwitchDomain" | `SwitchDomainHandler.kt`, `SwitchDomainCommand.kt` | Mapped |
| FR-005 | Idea: "bỏ domain khỏi token" | `TokenGenerator.kt`, `JwtService.kt`, `AuthToken.kt` | Mapped |
| FR-006 | Idea: "RBAC global scope" | `RbacEngine.kt` | Mapped |
| FR-007 | Idea: "password policy global" | `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt` | Mapped |
| FR-008 | Idea: "xóa DomainPort" | `DomainPort.kt`, `DomainPersistenceAdapter.kt`, `DomainLookupService.kt` | Mapped |
| FR-009 | Idea: "bỏ domain events" | `TokenIssuedEvent`, `UserRegisteredEvent`, `UserLoggedInEvent` | Mapped |
| FR-010 | Idea: "xóa DomainController" | `RbacControllers.kt` (DomainController) | Mapped |
| FR-011 | Idea: "frontend cleanup" | `authService.ts`, `auth.types.ts`, `account.types.ts`, `ProfilePage.tsx` | Mapped |
| FR-012 | Enriched | Flyway migration SQL | Pending |
| FR-013 | Enriched | `Repositories.kt` (DomainRepository, etc.) | Mapped |
| FR-014 | Enriched | `PolicyController.kt` | Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Đây là MAINTENANCE change có blast radius lớn (~50 files) nhưng low complexity per file — chủ yếu là XÓA code, không phải viết logic mới
- Domain concept nằm rất sâu trong architecture (entity → repo → port → service → JWT → frontend) → cần phải xử lý bottom-up (DB first, then entities, then services)
- Hệ thống đã có clean architecture pattern tốt (ports, adapters, CQRS) → sau khi remove sẽ gọn hơn đáng kể

### Related Features / Precedents
- Archive `2026-08-24-erp-iam-system`: Hệ thống RBAC gốc, có thể tham khảo design decisions ban đầu
- Archive `2026-08-21-auth-core-features`: Auth core features liên quan login/register flow

### Integration Notes
- **Redis**: Cache RBAC permissions có thể dùng key pattern `rbac:{userId}:{domainId}:perms` → cần flush khi deploy
- **Kafka**: Events `iam.auth.token-issued`, `iam.auth.user-registered` chứa `domainId` → remove field, downstream OK (verified)
- **PostgreSQL**: Flyway migration cần chạy trước application deploy

### Suggested Approach
1. **Phase 1 — DB Migration**: Tạo Flyway script, xử lý data cleanup
2. **Phase 2 — Bottom-up Entity Removal**: Entities → Repositories → Ports → Services → Handlers → Controllers
3. **Phase 3 — Auth Flow**: TokenGenerator, JwtService, LoginHandler, RegisterHandler
4. **Phase 4 — Frontend**: Types → Services → Pages
5. **Phase 5 — Test & Verify**: Run all tests, verify login flow

### Context from Confluence Images
N/A — không có Confluence source
