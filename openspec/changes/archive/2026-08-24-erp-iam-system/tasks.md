# Tasks: erp-iam-system

<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "NEWBUILD", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

_Generated: 2026-08-24_
_Profile: Non-Financial | N/A (no factory) | NEWBUILD_
_Brainstorm Direction: Event-Driven & Redis-Backed Decentralized Authorization_

> **Type**: NEWBUILD | **Flow**: Non-Financial | **FRs**: 21 (URD: 16, Enriched: 5)
> **Direction**: Event-Driven & Redis-Backed Decentralized Authorization with Phased Rollout (from brainstorm)

## Changes

[NEW] Full ERP IAM system build across 3 microservices. This tasks.md covers the complete NEWBUILD + EXTEND scope identified in `pre_openspec.md` and refined by `brainstorm_notes.md` codebase scan (2026-08-23). Brainstorm discovered that system-admin-service and account-service already have partial implementations — reducing build effort by ~30-40%.

**account-service (Phase 1 — 6 tasks)**: Profile CRUD (FR-005), Device Management (FR-007), Preferences (FR-006), Error handling, Flyway migrations, ProfileKafkaListener enhancement.
**system-admin-service (Phase 1 — 5 tasks)**: Organization department tree + positions (FR-011), Immutable Audit Trail (FR-015), Error handling, Flyway migrations.
**system-admin-service (Phase 2 — 3 tasks)**: Menu circular ref detection (FR-010), API Partner usage dashboard + IP whitelist (FR-012), Feature Flags extension (FR-014).
**auth-service (Phase 2 — 5 tasks)**: MFA recovery codes (FR-001), SSO auto-provision Kafka event (FR-003), Account Lifecycle GDPR (FR-009), Domain config branding (FR-016), AuditLogService persistence (FR-015).
**system-admin-service (Phase 3 — 3 tasks)**: Approval Workflow engine + escalation scheduler (FR-013), System Config enhancement (FR-014).
**All services (Phase 3 — 1 task)**: Inter-service auth via Service JWT (FR-021).
**auth-service (Phase 4 — 7 tasks)**: Kafka dependency upgrade + KafkaEventPublisher (FR-020), Idempotency filter (FR-017), Timeout + circuit breaker (FR-019), DLQ config, EventPublisher new events, SecurityConfig internal endpoints (FR-021).
**Refactoring (Layer R — 2 tasks)**: AbstractTwoTierCache extraction from MultiTierPermissionCache (🟡 Medium impact, 3 callers).

**Total**: 32 tasks, ~55 new files, ~18 modified files, 57+ endpoints, 30+ entities, 21/21 FR covered, ~8-12 weeks.

---

## Phase 1: Fill Gaps — NEWBUILD Core Modules (2-3 weeks)

### account-service

- [x] **Task 1.1: Profile CRUD Service + Controller**
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/application/ProfileService.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/web/ProfileController.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/out/persistence/entity/UserProfileEntity.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/out/persistence/entity/UserContactEntity.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/web/dto/ProfileDtos.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-005 — Quản lý User Profile
  - Pattern: Clean Architecture — controller → service → entity. DTO via `data class` + companion `from()` factory.
  - Endpoints: `GET /api/account/profile`, `PUT /api/account/profile`

- [x] **Task 1.2: ProfileKafkaListener Enhancement**
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/kafka/ProfileKafkaListener.kt` | Action: [MODIFY]
  - FR: FR-005 — Auto-create profile on user registration/SSO provision
  - Dependencies: `ProfileService`, Kafka topics: `iam.user.registered`, `iam.user.sso_provisioned`
  - Pattern: `@KafkaListener` consumer → call `profileService.createDefaultProfile(event)`

- [x] **Task 1.3: Device Management Module**
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/device/application/DeviceService.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/device/adapter/in/web/DeviceController.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/device/adapter/out/persistence/entity/UserDeviceEntity.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/device/adapter/in/web/dto/DeviceDtos.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity`
  - FR: FR-007 — Quản lý Device
  - Error: `ACCT_005` (device not found), `ACCT_006` (max devices reached)
  - Pattern: Device fingerprint unique per user, trust TTL 30 days, remote logout → revoke session
  - Endpoints: `GET /api/account/devices`, `POST /api/account/devices/{id}/trust`, `DELETE /api/account/devices/{id}`

- [x] **Task 1.4: Preferences Module**
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/preference/application/PreferenceService.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/preference/adapter/in/web/PreferenceController.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/preference/adapter/out/persistence/entity/UserPreferenceEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity`
  - FR: FR-006 — Quản lý Preferences & Settings
  - Error: `ACCT_004` (invalid preference format)
  - Pattern: Key-value grouped by category, merge-update semantics (PATCH = partial)
  - Endpoints: `GET /api/account/preferences`, `GET /api/account/preferences/{category}`, `PATCH /api/account/preferences/{category}`

- [x] **Task 1.5: Account Service Error Codes & Exception Handler**
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountErrorCode.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountExceptions.kt` | Action: [NEW]
  - File: `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountControllerAdvice.kt` | Action: [NEW]
  - Base: `ErrorCodeBase` from `com.ntt.basecore.exception.base`, `BaseControllerAdvice` from `com.ntt.basecore.domain.web`
  - Pattern: Same as `AuthErrorCode` — enum with `errorCode`, `msgCode`, `httpStatus`
  - Error: `ACCT_001` — `ACCT_008` range

- [x] **Task 1.6: Account Service Flyway Migrations**
  - File: `account-service/src/main/resources/db/migration/V1__create_profile_tables.sql` | Action: [NEW]
  - File: `account-service/src/main/resources/db/migration/V2__create_device_tables.sql` | Action: [NEW]
  - File: `account-service/src/main/resources/db/migration/V3__create_preference_tables.sql` | Action: [NEW]
  - Pattern: Flyway versioned migrations, Snowflake BIGINT PK, audit columns

### system-admin-service

- [x] **Task 1.7: Organization Module — Department Tree**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/application/OrganizationService.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/DepartmentController.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/out/persistence/entity/DepartmentEntity.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/persistence/TreeEntity.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/util/TreeBuilder.kt` | Action: [NEW]
  - Base: `TreeEntity` extends `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-011 — Quản lý Cơ Cấu Tổ Chức
  - Error: `SYS_004` (not found), `SYS_005` (circular hierarchy), `SYS_018` (max depth)
  - Pattern: Recursive CTE for tree queries, DFS cycle detection, max 10 levels
  - Endpoints: `GET /api/admin/departments/tree`, `POST/PUT/DELETE /api/admin/departments`, `PUT /api/admin/departments/{id}/move`

- [x] **Task 1.8: Organization Module — Position & Assignment**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/application/PositionService.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/PositionController.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/out/persistence/entity/PositionEntity.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/out/persistence/entity/UserPositionEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity`
  - FR: FR-011 — Position management
  - Error: `SYS_006` (position duplicate)
  - Endpoints: `POST/PUT/DELETE /api/admin/positions`, `POST /api/admin/positions/{id}/assign`

- [x] **Task 1.9: Immutable Audit Trail Module**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditService.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/adapter/in/web/AuditController.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/adapter/out/persistence/entity/AuditLogEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity` (override — no updated_at)
  - FR: FR-015 — Immutable Audit Trail
  - Error: `SYS_017` (query failed)
  - Pattern: AOP `@Around` aspect on admin controllers, JSONB diff `{field: {old: X, new: Y}}`, DB rules prevent UPDATE/DELETE
  - Endpoints: `GET /api/admin/audit-logs`, `GET /api/admin/audit-logs/export`
  - Dependencies: `DataSource` (direct JDBC for immutable insert), Jackson for JSON diff

- [x] **Task 1.10: System Admin Error Codes & Exceptions**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminControllerAdvice.kt` | Action: [NEW]
  - Base: `ErrorCodeBase`, `BaseControllerAdvice`
  - Error: `SYS_001` — `SYS_018` range

- [x] **Task 1.11: System Admin Flyway Migrations**
  - File: `system-admin-service/src/main/resources/db/migration/V1__create_organization_tables.sql` | Action: [NEW]
  - File: `system-admin-service/src/main/resources/db/migration/V2__create_audit_tables.sql` | Action: [NEW]
  - File: `system-admin-service/src/main/resources/db/migration/V3__create_workflow_tables.sql` | Action: [NEW]
  - File: `system-admin-service/src/main/resources/db/migration/V4__create_feature_flag_tables.sql` | Action: [NEW]
  - Pattern: Flyway versioned, immutability rules for audit_logs

---

## Phase 2: Enhancements — EXTEND Existing Modules (2-3 weeks)

### system-admin-service

- [x] **Task 2.1: Menu Circular Reference Detection & Validation**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/menu/application/MenuPermissionService.kt` | Action: [MODIFY]
  - FR: FR-010 — Menu tree validation
  - Error: `SYS_002` (circular reference), `SYS_018` (max depth)
  - Pattern: DFS cycle detection via `TreeBuilder.detectCycle()`, max depth check before save

- [x] **Task 2.2: API Partner — Usage Dashboard & IP Whitelist**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/apipartner/application/ApiPartnerService.kt` | Action: [MODIFY]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/apipartner/adapter/in/web/ApiUsageController.kt` | Action: [MODIFY]
  - FR: FR-012 — API Partner enhancements
  - Error: `SYS_010` (IP not whitelisted)
  - Pattern: Aggregate API usage logs, IP whitelist enforcement filter
  - Endpoints: `GET /api/admin/api-usage`, `PUT /api/admin/api-partners/{id}/ip-whitelist`

- [x] **Task 2.3: Domain Config — Feature Flags Extension**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/application/FeatureFlagService.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/adapter/out/persistence/entity/FeatureFlagEntity.kt` | Action: [NEW]
  - FR: FR-014 — Feature flags per domain
  - Pattern: Boolean + percentage rollout + user segment targeting
  - Endpoints: `GET /api/admin/feature-flags`, `PUT /api/admin/feature-flags/{key}`

### auth-service

- [x] **Task 2.4: MFA Recovery Codes UI Flow**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` | Action: [MODIFY]
  - FR: FR-001 — Recovery codes management
  - Pattern: Generate 10 single-use codes on MFA setup, regenerate on demand
  - Endpoints: `GET /api/auth/mfa/recovery-codes`, `POST /api/auth/mfa/recovery-codes/regenerate`

- [x] **Task 2.5: SSO Auto-Provision Enhancement**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` | Action: [MODIFY]
  - FR: FR-003 — Enhanced auto-provision with Kafka event
  - Dependencies: `EventPublisher` (publish `SsoProvisionedEvent`)
  - Pattern: On first SSO login → create user → publish event → account-service creates profile

- [x] **Task 2.6: Account Lifecycle GDPR Enhancements**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt` | Action: [MODIFY]
  - FR: FR-009 — Enhanced data export, cross-service deactivation events
  - Dependencies: `EventPublisher` (new `AccountDeactivatedEvent`)
  - Pattern: Deactivate → publish Kafka event → account-service handles profile deactivation

- [x] **Task 2.7: Domain Config Extension (Branding, Login Page)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt` | Action: [MODIFY]
  - FR: FR-016 — Domain/tenant extended config
  - Pattern: Add branding fields (logo_url, primary_color, login_page_config) to DomainEntity

- [x] **Task 2.8: AuditLogService Persistence Enhancement**
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` | Action: [MODIFY]
  - FR: FR-015 — Persist audit to DB
  - Pattern: Replace `// TODO: persist to audit_log table` with actual JPA insert
  - Dependencies: New `AuditLogEntity`, `AuditLogRepository`

---

## Phase 3: Advanced Features — NEWBUILD Complex Modules (2-3 weeks)

### system-admin-service

- [x] **Task 3.1: Approval Workflow Engine**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/application/WorkflowEngine.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/application/WorkflowService.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/adapter/in/web/WorkflowController.kt` | Action: [NEW]
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/adapter/out/persistence/entity/WorkflowEntities.kt` | Action: [NEW]
  - FR: FR-013 — Dynamic Approval Workflow
  - Error: `SYS_011` (workflow not found), `SYS_012` (invalid transition), `SYS_013` (already processed), `SYS_014` (escalation timeout)
  - Pattern: State machine (PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED)
  - Dependencies: PBAC PolicyCondition pattern for condition DSL evaluation
  - Source: Condition evaluator extracted from `PolicyEvaluator.kt` pattern

- [x] **Task 3.2: Workflow Escalation Scheduler**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/application/WorkflowEscalationScheduler.kt` | Action: [NEW]
  - FR: FR-013 — Auto-escalation on timeout
  - Pattern: `@Scheduled(fixedRate = 300000)` checks pending steps, escalates if timeout exceeded
  - Dependencies: `WorkflowEngine`, Kafka event notification

- [x] **Task 3.3: System Config CRUD Enhancement**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/application/DomainConfigService.kt` | Action: [MODIFY]
  - FR: FR-014 — Config versioning, type validation
  - Pattern: STRING, JSON, NUMBER type validation, history tracking via existing `DomainConfigHistoryEntity`

### All Services

- [x] **Task 3.4: Inter-Service Authentication — Service JWT**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ServiceTokenService.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ServiceAuthFilter.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt` | Action: [NEW]
  - FR: FR-021 — Inter-service auth
  - Error: `AUTH_050` (service token invalid), `AUTH_051` (expired), `AUTH_052` (access denied)
  - Pattern: Service JWT issued at startup, validates `/api/internal/**` paths
  - Dependencies: `JwtService` (extend for service token generation), `SecurityConfig` (add filter)
  - Endpoints: `POST /api/internal/service-token`, `GET /api/internal/users/{id}/roles`

---

## Phase 4: Integration & Polish (1-2 weeks)

### Kafka Migration

- [x] **Task 4.1: Kafka Dependency Upgrade**
  - File: `build.gradle.kts` | Action: [MODIFY]
  - FR: FR-020 — Kafka runtime activation
  - Pattern: Change `compileOnly("org.springframework.kafka:spring-kafka")` to `implementation(...)`
  - Dependencies: Kafka broker must be available in runtime environment

- [x] **Task 4.2: KafkaEventPublisher Adapter**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt` | Action: [NEW]
  - FR: FR-020 — Replace SpringEventPublisher with Kafka
  - Pattern: Implements `EventPublisher` port, publishes to Kafka topics with retry (max 3, exponential backoff)
  - Dependencies: `KafkaTemplate`, `EventPublisher` interface

### Infrastructure

- [x] **Task 4.3: Idempotency Filter**
  - File: `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt` | Action: [NEW]
  - FR: FR-017 — Idempotency key handling
  - Pattern: Servlet filter, reads `X-Idempotency-Key` header, checks Redis `idempotency:{service}:{key}`, caches response (TTL 24h)
  - Dependencies: `StringRedisTemplate`

- [x] **Task 4.4: Timeout & Circuit Breaker Config**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt` | Action: [MODIFY]
  - FR: FR-019 — Timeout handling for external APIs
  - Pattern: Configurable timeout per client (default 10s), circuit breaker (open after 5 failures, half-open 30s)
  - Dependencies: Resilience4j or Spring Retry

- [x] **Task 4.5: Kafka Dead Letter Queue Config**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt` | Action: [NEW]
  - FR: FR-020 — Dead-letter queue for failed messages
  - Pattern: `.DLT` topic suffix, max retries 3, backoff multiplier 2
  - Dependencies: Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`

- [x] **Task 4.6: Event Publisher New Event Types**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt` | Action: [MODIFY]
  - FR: FR-020 — Add new domain event types
  - Pattern: Add `AccountDeactivatedEvent`, `AccountDeletedEvent`, `AuditEvent` to port interface

- [x] **Task 4.7: SecurityConfig Internal Endpoints**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-021 — Configure internal endpoint security
  - Pattern: `/api/internal/**` → requires `ServiceAuthFilter`, not standard JWT auth
  - Dependencies: `ServiceAuthFilter` from Task 3.4

---

## Layer R: Refactoring — Cache Abstraction `[EXTRACT]`

> **Ref:** `impact_analysis.md` — 🟡 Medium impact, 3 callers

- [x] **R01: Create AbstractTwoTierCache** `[NEW][EXTRACT]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - Methods: `get(key)`, `put(key, value)`, `invalidate(key)`, `invalidateAll()`
  - Source: Extracted from `MultiTierPermissionCache.kt` L1+L2 orchestration pattern

- [x] **R02: Refactor MultiTierPermissionCache** `[MODIFY][REUSE]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
  - Remove: Inline L1+L2 orchestration
  - Add dependency: `AbstractTwoTierCache<String, List<Permission>>`
  - Delegate: Override `loadFromSource()`, `serializeForRedis()`, `deserializeFromRedis()`

#### Post-extract Checklist (BẮT BUỘC sau khi hoàn thành EXTRACT)
- [x] Tất cả callers cũ đã được update → inject interface, delegate
- [x] Không còn duplicate logic ở source gốc (grep confirm)
- [x] Build/compile thành công — không breaking change
- [x] tasks.md đã sync: dependencies, method names, signatures khớp code thực tế

---

## Summary

| Phase | Tasks | Status | FR Coverage |
|-------|-------|--------|-------------|
| Phase 1 | 11 tasks | ✅ Complete | FR-005, 006, 007, 011, 015 |
| Phase 2 | 8 tasks | ✅ Complete | FR-001, 003, 009, 010, 012, 014, 015, 016 |
| Phase 3 | 4 tasks | ✅ Complete | FR-013, 014, 021 |
| Phase 4 | 7 tasks | ✅ Complete | FR-017, 019, 020, 021 |
| Layer R | 2 tasks | ✅ Complete | (Refactoring — cache extraction) |
| **Total** | **32 tasks** | **✅ All complete** | **21/21 FR covered** |

### FR → Task Mapping

| FR | Task(s) | Action |
|----|---------|--------|
| FR-001 | 2.4 | MODIFY |
| FR-002 | (existing — no task needed) | — |
| FR-003 | 2.5 | MODIFY |
| FR-004 | (existing — no task needed) | — |
| FR-005 | 1.1, 1.2 | NEW, MODIFY |
| FR-006 | 1.4 | NEW |
| FR-007 | 1.3 | NEW |
| FR-008 | (existing — no task needed) | — |
| FR-009 | 2.6 | MODIFY |
| FR-010 | 2.1 | MODIFY |
| FR-011 | 1.7, 1.8 | NEW |
| FR-012 | 2.2 | MODIFY |
| FR-013 | 3.1, 3.2 | NEW |
| FR-014 | 2.3, 3.3 | NEW, MODIFY |
| FR-015 | 1.9, 2.8 | NEW, MODIFY |
| FR-016 | 2.7 | MODIFY |
| FR-017 | 4.3 | NEW |
| FR-018 | (base-core reuse — no task) | — |
| FR-019 | 4.4 | MODIFY |
| FR-020 | 4.1, 4.2, 4.5, 4.6 | NEW, MODIFY |
| FR-021 | 3.4, 4.7 | NEW, MODIFY |
