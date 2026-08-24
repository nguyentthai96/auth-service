# Proposal: erp-iam-system

> **Change**: erp-iam-system | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Capability-Priority Enhancement with Event-Driven Redis Backbone (from brainstorm)
> **_Generated**: 2025-07-15_
> **Previous Version**: 2026-08-24 (archived, NEWBUILD) — [CHANGED] reclassified to EXTEND

## Changes

- **auth-service — MFA Enhancement [MODIFY]**: `MfaService.kt`, `MfaController.kt`, `MfaDtos.kt` — add recovery codes management (generate 10 single-use, regenerate endpoint), enhance progressive flow (partial JWT → full JWT), trusted device MFA skip integration with account-service (FR-001, FR-002).
- **auth-service — SSO Enhancement [MODIFY]**: `SsoAdapter.kt`, `SsoController.kt`, `SsoDtos.kt` — enhance auto-provision flow, publish `SsoProvisionedEvent` via Kafka for account-service profile creation (FR-003).
- **auth-service — Session Management Enhancement [MODIFY]**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt` — add max concurrent session enforcement, terminate oldest on exceed, session list query (FR-008).
- **auth-service — Account Lifecycle GDPR Enhancement [MODIFY]**: `AccountLifecycleService.kt`, `AccountLifecycleController.kt` — enhanced data export (JSON format), `AccountDeactivatedEvent` cross-service event, grace period enforcement (FR-009).
- **auth-service — Domain Config Extension [MODIFY]**: `DomainLookupService.kt`, `DomainEntity.kt` — branding fields (logo_url, primary_color, login_page_config JSONB) (FR-016).
- **auth-service — Audit Persistence [MODIFY]**: `AuditLogService.kt` — implement actual DB persistence (replace log-only TODO), add `AuditLogEntity.kt`, `AuditLogRepository.kt`, sensitive data masking (FR-015).
- **auth-service — Inter-service Auth Enhancement [MODIFY]**: `ServiceTokenService.kt` — enhance for cross-service JWT validation, add `ServiceAuthFilter.kt` [NEW] for `/api/internal/**` paths, error codes AUTH_050–AUTH_052 (FR-021).
- **auth-service — Kafka Migration [MODIFY]**: `build.gradle.kts` — `compileOnly` → `implementation` for spring-kafka. `KafkaEventPublisher.kt` [NEW] — implements `EventPublisher` port with retry (max 3, exponential backoff). `KafkaConfig.kt` [NEW] — DLQ with `.DLT` topic suffix (FR-020).
- **auth-service — Event Publisher Enhancement [MODIFY]**: `EventPublisher.kt` — add new event types (`AccountDeactivatedEvent`, `AccountDeletedEvent`, `OrgStructureChangedEvent`). `SpringEventPublisher.kt` [MODIFY] — delegate to KafkaEventPublisher (FR-020).
- **auth-service — Infrastructure Enhancement [MODIFY]**: `HttpClientConfig.kt` — configurable timeout + circuit breaker (Resilience4j) for external APIs (FR-019). `IdempotencyFilter.kt` — already exists, document pattern for replication to other services (FR-017).
- **auth-service — Security Config Enhancement [MODIFY]**: `SecurityConfig.kt` — add internal endpoint filter chain for `/api/internal/**` (FR-021).
- **account-service — Profile Enhancement [MODIFY]**: `ProfileService.kt`, `ProfileController.kt`, `ProfileDtos.kt` — add email/phone verification flow, avatar upload, profile completeness score (FR-005).
- **account-service — Device Enhancement [MODIFY]**: `DeviceService.kt`, `DeviceController.kt`, `DeviceDtos.kt` — add trusted device management, 30-day TTL, max 5 devices, integrate with auth-service MFA skip (FR-007).
- **account-service — Preference Enhancement [MODIFY]**: `PreferenceService.kt`, `PreferenceController.kt` — enhance merge-update semantics, add category-based grouping (FR-006).
- **account-service — Idempotency [NEW]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017).
- **system-admin-service — Menu Permission Enhancement [MODIFY]**: `MenuPermissionService.kt` — add button-level permission (BUTTON type `permission_code`), role-menu assignment CRUD, user override (user_menu_overrides table), Redis cache invalidation via Kafka (FR-010).
- **system-admin-service — Organization Enhancement [MODIFY]**: `OrganizationService.kt`, `PositionService.kt`, `DepartmentController.kt`, `PositionController.kt` — enhance user transfer between departments, org chart query (recursive CTE), department head assignment (FR-011).
- **system-admin-service — API Partner Enhancement [MODIFY]**: `ApiPartnerService.kt`, `ApiUsageController.kt` — add Bucket4j rate limiting integration (Redis ProxyManager), API key lifecycle (generate/rotate/revoke with show-once pattern `ntt_pk_`/`ntt_sk_`), IP whitelist enforcement, usage dashboard (FR-012).
- **system-admin-service — Workflow Enhancement [MODIFY]**: `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowController.kt` — enhance conditional routing (PBAC PolicyCondition DSL reuse), delegation support, auto-escalation timeout tuning, guard conditions per state transition (FR-013).
- **system-admin-service — Config Enhancement [MODIFY]**: `DomainConfigService.kt`, `FeatureFlagService.kt` — add config versioning with history tracking, type validation (STRING/JSON/NUMBER) (FR-014).
- **system-admin-service — Audit Enhancement [MODIFY]**: `AuditService.kt`, `AuditAspect.kt`, `AuditController.kt` — add immutability DB constraints (no UPDATE/DELETE), sensitive data masking, search + CSV/JSON export (FR-015).
- **system-admin-service — Idempotency [NEW]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017).

**Total**: ~12 new files, ~35 modified files across 3 services. 21 FRs in 3 capability-priority phases. 57+ API endpoints. ~6-9 weeks effort (reduced from 8-12 weeks due to EXTEND classification).

---

## 1. Executive Summary

[CHANGED] Enhancement hệ thống Enterprise Identity & Access Management (IAM) hiện có cho ERP, với 3 microservices đã có implementations: **auth-service** (29+ application files — MFA, SSO, JWT, RBAC/PBAC, session, E2EE), **account-service** (16 Kotlin files, 883 LOC — profile, device, preferences), và **system-admin-service** (27 Kotlin files, 1967 LOC — menu permission, organization, API partner, workflow, audit, config). Kiến trúc event-driven với Redis làm backbone cho decentralized authorization, rate limiting, và caching.

### Business Value
- **Bảo mật enterprise-grade**: Progressive MFA with recovery codes, inter-service JWT auth, API key rate limiting (Bucket4j + Redis).
- **Quản trị tập trung**: Menu động + button-level permission, organization hierarchy, approval workflow, feature flags.
- **GDPR compliance**: Account lifecycle (deactivate, delete, export) với immutable audit trail.
- **Scale cho multi-tenant ERP**: Decentralized rate limiting, lean JWT, two-tier caching (Caffeine L1 + Redis L2).

### Key Metrics
| Metric | Value |
|--------|-------|
| Total FRs | 21 (16 URD + 5 Enriched) |
| Services affected | 3 (auth-service, account-service, system-admin-service) |
| Modified files | ~35 |
| New files | ~12 |
| API endpoints | 57+ |
| Estimated effort | 6-9 weeks |
| Risk level | MEDIUM (reduced from HIGH — code exists) |

---

## 2. Architecture Overview

### 2.1 Service Boundaries

```
┌────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                            │
│     [Web SPA]         [Mobile App]       [API Partner]         │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│                       API GATEWAY        cycle.deactivated` | Invalidate tokens/sessions |
| ASYNC | system-admin → all | Kafka `system.audit.events` | Centralized audit |

### 2.3 Key Design Decisions (from brainstorm)

| # | Decision | Rationale |
|---|----------|-----------|
| D-01 | Lean JWT (userId, tenantId, roleIds only) | Prevent JWT bloat — ERP has 100+ permissions |
| D-02 | Decentralized rate limiting (Bucket4j + Redis at Gateway) | Eliminate Gateway → system-admin sync bottleneck |
| D-03 | Kafka for all state change events (no Redis Pub/Sub) | Single event backbone, durable, consistent |
| D-04 | SHA-256 hash for API keys (show-once Stripe pattern) | Security best practice |
| D-05 | Reuse PBAC PolicyCondition for workflow DSL | Consistency, existing evaluator reusable |
| D-06 | Service JWT for inter-service auth | Simpler than mTLS, reuses JWT infra |
| D-07 | Keep TreeEntity in system-admin-service | Service-specific, not base-core |
| D-08 | Use existing custom MfaService over Spring Security 7 | More feature-rich, production-ready |

---

## 3. Phasing Strategy (Capability-Priority)

### Phase 1: Security-Critical Enhancements (2-3 weeks)
| Task | Service | FR | Effort |
|------|---------|-----|--------|
| MFA progressive flow + recovery codes | auth-service | FR-001, FR-002 | 3-4 days |
| Bucket4j rate limiting integration | system-admin-service | FR-012 | 2-3 days |
| Inter-service auth (service JWT) | auth-service | FR-021 | 2 days |
| SSO auto-provision enhancement | auth-service | FR-003 | 2-3 days |
| Timeout + circuit breaker for external APIs | auth-service | FR-019 | 2 days |

### Phase 2: Admin Feature Enhancements (2-3 weeks)
| Task | Service | FR | Effort |
|------|---------|-----|--------|
| Menu button-level permission + user override | system-admin-service | FR-010 | 3-4 days |
| Organization user transfer + org chart | system-admin-service | FR-011 | 2-3 days |
| Workflow conditional routing + delegation | system-admin-service | FR-013 | 3-4 days |
| Profile verification flow + avatar | account-service | FR-005 | 2-3 days |
| Device trusted management | account-service | FR-007 | 2-3 days |
| Session concurrent management | auth-service | FR-008 | 1-2 days |

### Phase 3: Integration & Polish (1-2 weeks)
| Task | Service | FR | Effort |
|------|---------|-----|--------|
| Audit trail immutability + export | system-admin-service | FR-015 | 2-3 days |
| Config versioning + feature flags | system-admin-service | FR-014 | 2 days |
| Domain/tenant config enhancement | auth + system-admin | FR-016 | 1-2 days |
| Kafka event migration | auth-service | FR-020 | 2-3 days |
| Retry mechanism (exponential backoff) | auth-service | FR-020 | 1-2 days |
| Idempotency filter replication | account + system-admin | FR-017 | 1-2 days |
| Account lifecycle GDPR | auth-service | FR-009 | 1-2 days |
| Preference enhancement | account-service | FR-006 | 1 day |

---

## 4. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| Scope creep (21 FRs, 3 services) | MEDIUM | HIGH | 3-phase capability-priority; each phase independently shippable |
| Kafka compileOnly → implementation | LOW | MEDIUM | `@KafkaListener` already works; build.gradle.kts change only |
| Cross-service data consistency | MEDIUM | MEDIUM | Eventual consistency via Kafka; idempotent consumers; Redis TTL |
| WorkflowEngine edge cases | MEDIUM | MEDIUM | State machine has `VALID_TRANSITIONS`; add explicit guards |
| Inter-service auth security | MEDIUM | HIGH | `ServiceTokenService.kt` exists; shared secret Phase 1 |

---

## 5. FR Traceability

| FR | Title | Service | Action | Phase |
|----|-------|---------|--------|-------|
| FR-001 | MFA Management | auth-service | MODIFY | 1 |
| FR-002 | Progressive MFA Login | auth-service | MODIFY | 1 |
| FR-003 | SSO Enhancement | auth-service | MODIFY | 1 |
| FR-004 | Password Policy | auth-service | REUSE | — |
| FR-005 | Profile Enhancement | account-service | MODIFY | 2 |
| FR-006 | Preferences | account-service | MODIFY | 3 |
| FR-007 | Device Management | account-service | MODIFY | 2 |
| FR-008 | Session Management | auth-service | MODIFY | 2 |
| FR-009 | Account Lifecycle GDPR | auth-service | MODIFY | 3 |
| FR-010 | Menu Permission | system-admin-service | MODIFY | 2 |
| FR-011 | Organization | system-admin-service | MODIFY | 2 |
| FR-012 | API Partner | system-admin-service | MODIFY | 1 |
| FR-013 | Approval Workflow | system-admin-service | MODIFY | 2 |
| FR-014 | System Config | system-admin-service | MODIFY | 3 |
| FR-015 | Audit Trail | system-admin + auth | MODIFY | 3 |
| FR-016 | Domain/Tenant Config | auth + system-admin | MODIFY | 3 |
| FR-017 | Idempotency | account + system-admin | REUSE | 3 |
| FR-018 | Transaction Logging | all | REUSE | — |
| FR-019 | Timeout Handling | auth-service | MODIFY | 1 |
| FR-020 | Retry Mechanism | auth-service | MODIFY | 3 |
| FR-021 | Inter-service Auth | auth-service | MODIFY | 1 |

**Coverage**: 21/21 FRs mapped. FR-004 and FR-018 are REUSE (no code changes needed — already implemented).

---

## 6. Open Items

- ⚠️ OPEN QUESTION: OQ-001 (from brainstorm) — Kafka dependency scope in `build.gradle.kts` — verify `compileOnly` vs `implementation`.
- ⚠️ OPEN QUESTION: OQ-002 (from brainstorm) — DPoP support → Phase 3+ consideration, not blocking.
