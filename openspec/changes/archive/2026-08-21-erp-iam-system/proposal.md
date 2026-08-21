# Proposal: erp-iam-system

> **Change**: erp-iam-system | **Type**: NEWBUILD | **Flow**: Non-Financial
> **Direction**: Event-Driven & Redis-Backed Decentralized Authorization with Phased Rollout (from brainstorm)
> **_Generated**: 2026-08-24_

## Changes

- **account-service — Profile CRUD module [NEW]**: `ProfileService.kt`, `ProfileController.kt`, `UserProfileEntity.kt`, `UserContactEntity.kt`, `ProfileDtos.kt` — full profile management (FR-005). `ProfileKafkaListener.kt` [MODIFY] — consume `iam.user.registered`, `iam.user.sso_provisioned` events to auto-create default profile.
- **account-service — Device Management module [NEW]**: `DeviceService.kt`, `DeviceController.kt`, `UserDeviceEntity.kt`, `DeviceDtos.kt` — device fingerprint tracking, trust/untrust, remote logout (FR-007). Max 5 trusted devices, 30-day TTL.
- **account-service — Preferences module [NEW]**: `PreferenceService.kt`, `PreferenceController.kt`, `UserPreferenceEntity.kt` — key-value settings grouped by category, merge-update semantics (FR-006).
- **account-service — Error handling [NEW]**: `AccountErrorCode.kt` (ACCT_001–ACCT_008), `AccountExceptions.kt`, `AccountControllerAdvice.kt` extending `BaseControllerAdvice`.
- **account-service — Flyway migrations [NEW]**: V1 (profile tables), V2 (device tables), V3 (preference tables).
- **system-admin-service — Organization module [NEW]**: `OrganizationService.kt`, `DepartmentController.kt`, `DepartmentEntity.kt`, `PositionEntity.kt`, `UserPositionEntity.kt`, `TreeEntity.kt`, `TreeBuilder.kt` — department hierarchy (recursive CTE, max 10 levels, DFS cycle detection), position management, user assignment (FR-011).
- **system-admin-service — Immutable Audit Trail module [NEW]**: `AuditService.kt`, `AuditAspect.kt` (AOP `@Around`), `AuditController.kt`, `AuditLogEntity.kt` — JSONB diff `{field: {old: X, new: Y}}`, DB rules prevent UPDATE/DELETE, search + CSV/JSON export (FR-015).
- **system-admin-service — Error handling [NEW]**: `SysAdminErrorCode.kt` (SYS_001–SYS_018), `SysAdminExceptions.kt`, `SysAdminControllerAdvice.kt`.
- **system-admin-service — Flyway migrations [NEW]**: V1 (organization tables), V2 (audit tables), V3 (workflow tables), V4 (feature flag tables).
- **system-admin-service — Menu enhancements [MODIFY]**: `MenuPermissionService.kt` — add circular reference detection via `TreeBuilder.detectCycle()`, max depth validation (FR-010).
- **system-admin-service — API Partner enhancements [MODIFY]**: `ApiPartnerService.kt`, `ApiUsageController.kt` — usage dashboard aggregation, IP whitelist enforcement (FR-012).
- **system-admin-service — Feature Flags [NEW]**: `FeatureFlagService.kt`, `FeatureFlagEntity.kt` — boolean + percentage rollout + segment targeting (FR-014).
- **system-admin-service — Approval Workflow engine [NEW]**: `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowController.kt`, `WorkflowEntities.kt`, `WorkflowEscalationScheduler.kt` — state machine (PENDING→IN_PROGRESS→APPROVED/REJECTED/ESCALATED/CANCELLED), condition DSL reusing PBAC PolicyCondition pattern, auto-escalation (FR-013).
- **system-admin-service — System Config enhancement [MODIFY]**: `DomainConfigService.kt` — type validation (STRING/JSON/NUMBER), history tracking (FR-014).
- **auth-service — MFA recovery codes [MODIFY]**: `MfaService.kt`, `MfaController.kt` — generate 10 single-use codes on setup, regenerate endpoint (FR-001).
- **auth-service — SSO auto-provision enhancement [MODIFY]**: `SsoAdapter.kt` — publish `SsoProvisionedEvent` via Kafka for account-service profile creation (FR-003).
- **auth-service — Account Lifecycle GDPR [MODIFY]**: `AccountLifecycleService.kt` — enhanced data export, `AccountDeactivatedEvent` cross-service event (FR-009).
- **auth-service — Domain config extension [MODIFY]**: `DomainLookupService.kt`, `RbacEntities.kt` — branding fields (logo_url, primary_color, login_page_config) on DomainEntity (FR-016).
- **auth-service — Audit persistence [MODIFY]**: `AuditLogService.kt` — replace TODO with actual DB insert via `AuditLogEntity`/`AuditLogRepository` (FR-015).
- **auth-service — Inter-service auth [NEW]**: `ServiceTokenService.kt`, `ServiceAuthFilter.kt`, `InternalApiController.kt` — service JWT for `/api/internal/**` paths (FR-021). Error codes AUTH_050–AUTH_052.
- **auth-service — Kafka migration [MODIFY]**: `build.gradle.kts` — `compileOnly` → `implementation` for spring-kafka (FR-020). `KafkaEventPublisher.kt` [NEW] — implements `EventPublisher` port with retry (max 3, exponential backoff). `KafkaConfig.kt` [NEW] — DLQ with `.DLT` topic suffix.
- **auth-service — Infrastructure [NEW]**: `IdempotencyFilter.kt` — `X-Idempotency-Key` header, Redis cached response (TTL 24h) (FR-017). `HttpClientConfig.kt` [MODIFY] — configurable timeout + circuit breaker (FR-019).
- **auth-service — Event publisher [MODIFY]**: `EventPublisher.kt` — add `AccountDeactivatedEvent`, `AccountDeletedEvent`, `AuditEvent` types (FR-020). `SecurityConfig.kt` [MODIFY] — internal endpoint filter chain (FR-021).
- **auth-service — Cache refactoring [EXTRACT]**: `AbstractTwoTierCache.kt` [NEW] — extracted from `MultiTierPermissionCache.kt` L1+L2 pattern. `MultiTierPermissionCache.kt` [MODIFY] — extends abstract base.

**Total**: ~55 new files, ~18 modified files across 3 services. 32 tasks in 4 phases + 1 refactoring layer. 57+ API endpoints. 30+ entities. ~8-12 weeks effort.

---

## 1. Executive Summary

Triển khai hệ thống Enterprise Identity & Access Management (IAM) hoàn chỉnh cho ERP, phân tách thành 3 microservices theo bounded context: **auth-service** (xác thực, MFA, SSO, JWT, RBAC/PBAC — foundation mạnh đã có), **account-service** (profile, device, session, preferences, lifecycle — partial), và **system-admin-service** (menu permission, API partner, organization, approval workflow, audit, config — partial). Kiến trúc event-driven với Redis làm backbone cho decentralized authorization, rate limiting, và caching.

### Business Value
- **Tổ chức quản trị tập trung**: Hệ thống menu động + phân quyền role-based cho phép admin cấu hình UI/quyền mà không cần deploy.
- **Bảo mật enterprise-grade**: Progressive MFA, SSO/OIDC, password policy, API key management, immutable audit trail.
- **Scale cho multi-tenant ERP**: Decentralized rate limiting, lean JWT, two-tier caching đảm bảo performance với 1000+ concurrent users per service.
- **GDPR compliance**: Account lifecycle (deactivate, delete, export) với grace period và immutable audit.

### Key Metrics
| Metric | Value |
|--------|-------|
| Functional Requirements | 21 (URD: 16, Enriched: 5) |
| Services | 3 (auth-service, account-service, system-admin-service) |
| Estimated Entities | 30+ |
| Estimated Endpoints | 57+ |
| Phases | 4 (8-12 weeks total) |
| Reuse from existing code | ~60-70% patterns, ~30-40% direct code |

---

## 2. Problem Statement

Hệ thống ERP hiện tại có auth-service với foundation mạnh (authentication, MFA, SSO, RBAC, PBAC, E2EE) nhưng thiếu:
1. **System Administration UI**: Không có menu động, cấu hình tổ chức, API partner management.
2. **Account Management**: Profile CRUD tách biệt, device tracking, user preferences chưa có.
3. **Workflow Engine**: Không có approval workflow cho các thao tác admin cần phê duyệt.
4. **Cross-service Communication**: Event publishing chỉ là stub (log only), Kafka chưa active runtime.
5. **Comprehensive Audit**: Audit log chưa persist to DB, chưa có search/export.

---

## 3. Technical Approach

### 3.1 Architecture

- **Pattern**: Clean Architecture (Hexagonal) — consistent across 3 services.
- **Communication**: Hybrid REST + Kafka (sync for queries, async for state changes).
- **Caching**: Two-tier Caffeine L1 (30s) + Redis L2 (5-30min).
- **Authorization**: Lean JWT (userId, tenantId, roleIds) + runtime permission resolution via Redis.
- **Rate Limiting**: Decentralized via Bucket4j + Redis at API Gateway.
- **Event Backbone**: Redis Pub/Sub for cache invalidation + Kafka for business events.

### 3.2 Service Boundaries

| Service | Responsibility | State |
|---------|---------------|-------|
| auth-service | Authentication, MFA, SSO, JWT, RBAC/PBAC, Password Policy, E2EE | Existing (EXTEND) |
| account-service | Profile, Device, Session (Redis), Preferences, Lifecycle | Partial (NEWBUILD + EXTEND) |
| system-admin-service | Menu Permission, Organization, API Partner, Workflow, Audit, Config | Partial (NEWBUILD + EXTEND) |

### 3.3 Inter-Service Events

| Event | Producer | Consumer | Protocol |
|-------|----------|----------|----------|
| `iam.user.registered` | auth-service | account-service | Kafka |
| `iam.user.sso_provisioned` | auth-service | account-service | Kafka |
| `iam.permission.changed` | system-admin | auth-service | Kafka |
| `menu_cache_invalidated` | system-admin | system-admin (all instances) | Redis Pub/Sub |
| `api_key_revoked` | system-admin | API Gateway | Redis Pub/Sub |
| `acct.lifecycle.deactivated` | account-service | auth-service | Kafka |

### 3.4 Key Technology Decisions

| Decision | Rationale |
|----------|-----------|
| Lean JWT (no permissions embedded) | ERP has 100+ permissions → JWT bloat risk |
| Decentralized rate limiting (Bucket4j + Redis) | Eliminate Gateway → system-admin sync bottleneck |
| SHA-256 API key hash (show-once) | Stripe pattern, already implemented |
| Recursive CTE for org hierarchy (not ltree) | Simpler, portable, bounded depth (10 levels) |
| PBAC PolicyCondition reuse for workflow DSL | Consistency, evaluator already exists |
| Service JWT for inter-service auth | Simpler than mTLS, uses existing JWT infra |

---

## 4. Expected Outcomes

- Hệ thống ERP IAM mạnh mẽ hỗ trợ SSO, MFA, RBAC/PBAC cho multi-tenant.
- Dynamic menu tree filtered per user role/permission với Redis caching (load < 100ms).
- API Partner onboarding với rate limiting decentralized (check < 10ms).
- Approval workflow engine cho admin operations (multi-step, conditional routing).
- Organization hierarchy management (department tree, positions, user assignments).
- Complete audit trail — immutable, searchable, exportable.
- GDPR compliance — account deactivation, deletion with grace period, data export.
- Inter-service communication via Kafka events (eventual consistency).

---

## 5. Phased Rollout

### Phase 1: Fill Gaps (2-3 weeks)
- account-service: Profile CRUD, Device Management, Preferences
- system-admin-service: Organization module (department + position), Audit Trail

### Phase 2: Enhancements (2-3 weeks)
- system-admin-service: Menu circular ref detection, API Partner usage dashboard/IP whitelist
- auth-service: MFA enhancements (recovery codes), SSO auto-provision improvements

### Phase 3: Advanced Features (2-3 weeks)
- system-admin-service: Approval Workflow engine, System Config + Feature Flags
- All services: Inter-service auth (service JWT)

### Phase 4: Integration & Polish (1-2 weeks)
- Kafka upgrade (compileOnly → implementation) + KafkaEventPublisher
- Idempotency filter, timeout/retry for external APIs, E2E integration tests

---

## 6. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Scope creep (21 FRs, 47 UCs, 57+ endpoints) | HIGH | HIGH | 4-phase rollout with strict gates |
| Cross-service data consistency | MEDIUM | MEDIUM | Eventual consistency via Kafka + idempotent consumers |
| Workflow state machine edge cases | MEDIUM | MEDIUM | Immutable definitions + explicit guard conditions |
| Inter-service auth gap | MEDIUM | HIGH | Service JWT Phase 3, shared secret Phase 1 |

---

## 7. FR Coverage

| FR Range | Count | Coverage |
|----------|-------|----------|
| FR-001 — FR-016 (URD) | 16 | ✅ All covered |
| FR-017 — FR-021 (Enriched) | 5 | ✅ All covered |
| **Total** | **21** | **21/21** |
