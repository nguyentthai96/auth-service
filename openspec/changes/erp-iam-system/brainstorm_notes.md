---
type: brainstorm_notes
change: erp-iam-system
date: 2025-07-15
selected_direction: "Capability-Priority Enhancement with Event-Driven Redis Backbone"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: ERP IAM System Enhancement

## Date
2025-07-15 (revised — supersedes archived brainstorm 2026-08-23)

## Context
Full ERP IAM system enhancement spanning 3 existing microservices: `auth-service` (authentication, MFA, SSO, JWT, RBAC, PBAC — strong foundation, 29+ application files), `account-service` (profile, device, preferences — 16 Kotlin files, 883 LOC), and `system-admin-service` (menu permission, organization, API partner, workflow, audit, config, feature flags — 27 Kotlin files, 1967 LOC). All 3 services have **substantial implementations** (NOT stubs). This is EXTEND work, not NEWBUILD.

**Source**: Research artifacts (`handoff_summary.md`, `comparison_analysis.md`, `business_analysis.md`, `technical_spec.md`) + `pre_openspec.md` (quality score 90/100) + archived brainstorm (2026-08-24).

**Key change from archived brainstorm**: Feature type definitively reclassified from NEWBUILD → EXTEND. Codebase scan confirms all modules have real implementations. Enhancement effort reduced ~40-50% from original estimates.

## Questions Asked & Answers

### Q1: What phasing strategy should we use?
**Q:** The archived brainstorm used a 4-phase rollout (Fill Gaps → Enhancements → Advanced Features → Integration). Should we keep that or restructure?

**A:** **Restructure to Capability-Priority approach** — delivers security-critical value first.

**Reasoning (internal debate):**

| Approach | Pros | Cons | Score |
|----------|------|------|:---:|
| **A: Strict 4-phase rollout** (archived) | Clear phases, proven | Based on old assumption of NEWBUILD; "Fill Gaps" phase is now minimal since modules already exist | 6/10 |
| **B: Service-boundary phases** | Reduces context switching per service | Forces team to work on low-priority items just because they're in the same service | 5/10 |
| **C: Capability-priority** ✅ | Security first, business value alignment, can stop after Phase 1 if needed | More context switching between services | 8/10 |

**Selected: Approach C** — Capability-priority. Since all services already have implementations, we can target the highest-value enhancements first regardless of service boundary.

### Q2: API Gateway Rate Limiting Architecture
**Q:** Archived brainstorm selected "Decentralized rate limiting via Bucket4j + Redis at Gateway". Is this still the right approach?

**A:** **Yes, confirmed.** The architecture is validated by the codebase:
- `ApiPartnerService.kt` already exists with IP whitelist management
- Technical spec defines `rate_limit:bucket4j:{keyHash}` Redis key pattern
- system-admin-service CRUD → push to Redis → Gateway reads from Redis (no sync call)

```text
┌──────────────┐         ┌──────────────────┐
│ API Gateway  │ ◄─────  │ Redis            │
│ (Bucket4j)   │  read   │ rate_limit:      │
│              │         │ bucket4j:{hash}  │
└──────────────┘         └──────────┬───────┘
                                    │ write
                         ┌──────────▼───────┐
                         │ system-admin-svc  │
                         │ ApiPartnerService │
                         │ (CRUD only)       │
                         └──────────────────┘
```

### Q3: JWT Strategy — Lean vs Permission-Embedded
**Q:** JWT should contain permissions or resolve at runtime?

**A:** **Lean JWT confirmed.** JWT contains only: `userId`, `tenantId`, `roleIds`. Permissions resolved at runtime via Redis `AbstractTwoTierCache` (Caffeine L1 30s + Redis L2 5-30min).

**Evidence from codebase:**
- `JwtService.kt` (8.7KB) already issues minimal JWT claims
- `MenuPermissionService.kt` resolves permissions via Redis L2 cache
- `AbstractTwoTierCache` pattern already operational in auth-service
- ERP systems commonly have 100+ permissions → JWT bloat → HTTP header overflow risk

### Q4: Menu Cache Invalidation — Redis Pub/Sub vs Kafka?
**Q:** When role permissions change at system-admin-service, how to invalidate menu cache?

**A:** **Kafka for consistency** (revised from archived brainstorm's "Hybrid Redis Pub/Sub + Kafka").

**Revised reasoning:**
| Factor | Redis Pub/Sub | Kafka |
|--------|:---:|:---:|
| Latency | <1ms ✅ | ~50-100ms ⚠️ |
| Durability | ❌ fire-and-forget | ✅ persistent |
| Already exists | ✅ (Redis running) | ✅ (`PermissionChangedConsumer.kt` exists) |
| Consistent with architecture | ❌ (adds 2nd messaging pattern) | ✅ (single event backbone) |
| Failure resilience | ❌ (missed = 5min stale) | ✅ (replay from offset) |

**Decision:** Use Kafka exclusively. The ~50-100ms latency is acceptable given the TTL safety net (max 5min staleness). Simplicity > speed for this use case. Redis Pub/Sub adds an unnecessary second event pattern.

**Fallback:** If Kafka is not available (compileOnly issue), Redis cache TTL (5 min) auto-expires → max staleness = 5 minutes.

### Q5: Inter-Service Communication Pattern
**Q:** REST sync vs Kafka async for inter-service calls?

**A:** **Hybrid — REST for queries, Kafka for state changes** (confirmed from archived brainstorm).

```text
SYNC (REST):
  system-admin ──GET /api/internal/permissions/{userId}──→ auth-service
  (needs roles for menu tree filtering)

ASYNC (Kafka):
  auth-service ──iam.user.registered──→ account-service (create profile)
  auth-service ──iam.user.sso_provisioned──→ account-service (create SSO profile)
  auth-service ──iam.permission.changed──→ system-admin (invalidate menu cache)
  account-service ──acct.lifecycle.deactivated──→ auth-service (invalidate tokens)
  system-admin ──system.audit.events──→ all (audit trail)
```

**Codebase evidence:**
- `SpringEventPublisher.kt` currently uses Spring ApplicationEvent (stub) → Phase 3 migrate to Kafka
- `PermissionChangedConsumer.kt` already has `@KafkaListener` on auth-service
- `ProfileKafkaListener.kt` exists in account-service as consumer

### Q6: Workflow Condition DSL — What Format?
**Q:** FR-013 (pre_openspec) noted workflow condition DSL is undefined.

**A:** **Reuse PBAC `PolicyCondition` JSONB pattern** (confirmed from archived brainstorm).

```json
{
  "operator": "AND",
  "conditions": [
    {"field": "amount", "op": "GT", "value": 10000},
    {"field": "department", "op": "EQ", "value": "FINANCE"}
  ]
}
```

**Codebase evidence:** `PolicyEvaluator.kt` in auth-service already implements this pattern. Same expression evaluator can be extracted to base-core `common-expression` module and reused by `WorkflowEngine.kt`.

### Q7: Account Lifecycle Service Boundary
**Q:** FR-005 (Profile) on account-service but FR-009 (Lifecycle) on auth-service — inconsistency?

**A:** **Split responsibility with Kafka event contract** (confirmed from archived brainstorm).

| Action | Owner | Event Flow |
|--------|-------|------------|
| Deactivate | account-service initiates | → Kafka `acct.lifecycle.deactivated` → auth-service invalidates tokens/sessions |
| Delete (GDPR) | account-service initiates | → collects profile data → Kafka → auth-service exports auth data → aggregates |
| Data Export | account-service orchestrates | → REST to auth-service for auth data → merge → return |

**Codebase evidence:** `AccountLifecycleService.kt` and `DataExportService.kt` already exist in account-service (confirmed, NOT auth-service as originally stated in some docs).

### Q8: Inter-Service Authentication
**Q:** How do the 3 services authenticate to each other?

**A:** **Service-level JWT with shared secret** (confirmed from archived brainstorm).
- Each service has a `SERVICE` role with internal-only permissions
- Internal endpoints use path prefix `/api/internal/` and require service JWT
- `ServiceTokenService.kt` already exists in auth-service
- Phase 1: shared secret. Future: service account auto-registration.

### Q9: Spring Security 7 MFA API Stability (OQ-001)
**Q:** `@EnableMultiFactorAuthentication` API stable?

**A:** **Use existing custom `MfaService.kt` flow.** The custom implementation is production-ready:
- `MfaService.kt` (13.4KB) — full MFA orchestrator
- `TotpService.kt` (4.6KB) — TOTP verification
- `OtpService.kt` (3.6KB) — OTP generation/verification
- `MfaRateLimitService.kt` (8.7KB) — rate limiting for MFA attempts
- `SessionPromotionService.kt` (7.2KB) — partial → full session promotion

The custom flow is more feature-rich than Spring Security 7's built-in MFA. Keep existing, don't migrate.

### Q10: TreeEntity — Use Existing or Refactor?
**Q:** Archived brainstorm proposed creating TreeEntity in base-core. What now?

**A:** **TreeEntity already exists at `system-admin-service/shared/persistence/TreeEntity.kt`** — confirmed in codebase scan. It extends `SnowflakePersistentAuditableEntity` with `parentId`, `name`, `code`, `sortOrder`, `treeLevel`, `treePath`. Used by DepartmentEntity. TreeBuilder utility also exists at `shared/util/TreeBuilder.kt` with `buildTree()`, `detectCycle()`, `calculateTreePath()`, `validateMaxDepth()`.

**Decision:** No action needed — TreeEntity is already implemented and functional. Do NOT move to base-core (it's specific to system-admin-service patterns).

## Approaches Considered

### Phasing Strategy

#### Approach A: Strict 4-Phase Rollout (Archived)
- Fill Gaps → Enhancements → Advanced → Integration
- **Pros:** Clear phases, proven
- **Cons:** Based on NEWBUILD assumption; "Fill Gaps" phase is now minimal

#### Approach B: Service-Boundary Phases
- auth-service → account-service + system-admin → cross-cutting
- **Pros:** Reduces context switching
- **Cons:** Forces low-priority items alongside high-priority ones

#### Approach C: Capability-Priority ✅ Selected
- Security-critical (MFA, rate limiting, inter-service auth) → Admin features (menu permissions, org, workflow) → Polish (audit, config, feature flags)
- **Pros:** Delivers highest business value first, can stop after Phase 1
- **Cons:** More context switching between services (acceptable tradeoff)

### Inter-Service Communication

#### Option 1: REST Only
- **Cons:** Tight coupling, cascading failures
- **Score:** 4/10

#### Option 2: Kafka Only
- **Cons:** Complex, eventual consistency for everything including queries
- **Score:** 5/10

#### Option 3: Hybrid REST + Kafka ✅ Selected
- **Pros:** Sync for queries, async for state changes
- **Score:** 9/10

### Caching Strategy

#### Single Tier (Redis Only)
- **Cons:** Network hop for every check (~1-5ms)

#### Two-Tier (Caffeine L1 + Redis L2) ✅ Selected (Already in use)
- `AbstractTwoTierCache.kt` already operational
- L1 (Caffeine 30s TTL) → L2 (Redis 5-30min TTL)
- Cache hit ratio > 80% target

## Selected Direction

**Capability-Priority Enhancement with Event-Driven Redis Backbone**

Core architecture principles (refined from archived brainstorm):
1. **EXTEND-first mindset** — enhance existing implementations, not rewrite
2. **Redis as shared backbone** — permissions, rate limits, sessions, OTP state, menu cache
3. **Kafka for state change events** — single event backbone (no Redis Pub/Sub duplication)
4. **Lean JWT** — identity + roles only, permissions resolved at runtime via two-tier cache
5. **Decentralized rate limiting** — Bucket4j + Redis at Gateway, system-admin-service CRUD only
6. **Hybrid communication** — REST sync for queries, Kafka async for state changes
7. **Capability-priority phasing** — security-critical → admin features → polish

### Revised 3-Phase Enhancement Plan

#### Phase 1: Security-Critical Enhancements (2-3 weeks)
| Task | Service | Type | Effort | FR |
|------|---------|------|--------|-----|
| MFA progressive flow enhancement (recovery codes) | auth-service | EXTEND | 3-4 days | FR-001, FR-002 |
| Bucket4j rate limiting integration | system-admin-service | EXTEND | 2-3 days | FR-012 |
| Inter-service auth (service JWT) | auth-service | EXTEND | 2 days | FR-021 |
| SSO auto-provision enhancement | auth-service | EXTEND | 2-3 days | FR-003 |
| Timeout + circuit breaker for external APIs | auth-service | EXTEND | 2 days | FR-019 |

#### Phase 2: Admin Feature Enhancements (2-3 weeks)
| Task | Service | Type | Effort | FR |
|------|---------|------|--------|-----|
| Menu button-level permission + user override | system-admin-service | EXTEND | 3-4 days | FR-010 |
| Organization: user transfer, org chart query | system-admin-service | EXTEND | 2-3 days | FR-011 |
| Workflow: conditional routing, delegation, escalation | system-admin-service | EXTEND | 3-4 days | FR-013 |
| Profile: verification flow, avatar | account-service | EXTEND | 2-3 days | FR-005 |
| Device: trusted device management | account-service | EXTEND | 2-3 days | FR-007 |
| Session: concurrent session management | auth-service | EXTEND | 1-2 days | FR-008 |

#### Phase 3: Integration & Polish (1-2 weeks)
| Task | Service | Type | Effort | FR |
|------|---------|------|--------|-----|
| Audit trail immutability + export | system-admin-service | EXTEND | 2-3 days | FR-015 |
| Config versioning + feature flags | system-admin-service | EXTEND | 2 days | FR-014 |
| Domain/tenant config enhancement | auth + system-admin | EXTEND | 1-2 days | FR-016 |
| Kafka event migration (SpringEventPublisher → Kafka) | auth-service | EXTEND | 2-3 days | FR-020 |
| Retry mechanism (exponential backoff) | auth-service | EXTEND | 1-2 days | FR-020 |
| Idempotency filter replication | account + system-admin | REUSE | 1-2 days | FR-017 |
| E2E integration tests + OpenAPI docs | all services | NEWBUILD | 3-4 days | — |

## Pre-classifications (preliminary)
- Feature type: EXTEND (all 3 services have existing implementations)
- Flow type: Non-Financial (IAM, profile, configuration management)
- Affected modules: auth-service, account-service, system-admin-service

## Codebase Findings

### Updated Implementation Status (2025-07-15)

| Module | Service | Files | LOC | Status | Enhancement Needed |
|--------|---------|-------|-----|--------|--------------------|
| Auth (MFA/SSO/JWT) | auth-service | 29+ | ~8000+ | ✅ Strong | MFA progressive flow, recovery codes |
| RBAC Engine | auth-service | 3+ | — | ✅ Complete | None |
| PBAC PolicyEvaluator | auth-service | 2+ | — | ✅ Complete | Extract expression evaluator for workflow |
| Password Policy | auth-service | 3+ | — | ✅ Complete | None |
| Session Management | auth-service | 5+ | — | ✅ Complete | Concurrent session enhancement |
| E2EE | auth-service | 4+ | — | ✅ Complete | None |
| Event Publisher | auth-service | 3+ | — | ⚠️ Stub (logs only) | Kafka adapter migration |
| Menu Permission | system-admin | 1+ | 103 | ✅ Implemented | Button-level permission, user override |
| Organization | system-admin | 2+ | 300+ | ✅ Implemented | User transfer, org chart query |
| API Partner | system-admin | 1+ | 104 | ✅ Implemented | Bucket4j rate limiting, usage dashboard |
| Workflow Engine | system-admin | 3 | 520+ | ✅ Implemented | Conditional routing, delegation |
| Audit | system-admin | 2+ | 300+ | ✅ Implemented | Immutability constraints, export |
| Config | system-admin | 2+ | 300+ | ✅ Implemented | Config versioning |
| Profile | account-service | 2+ | — | ✅ Implemented | Verification flow, avatar |
| Device | account-service | 2+ | — | ✅ Implemented | Trusted device management |
| Preference | account-service | 2+ | — | ✅ Implemented | Minor enhancements |

### Key Infrastructure Already Available

| Component | Location | Used By |
|-----------|----------|---------|
| `TreeEntity` | system-admin/shared/persistence/ | DepartmentEntity, MenuItemEntity |
| `TreeBuilder` | system-admin/shared/util/ | Menu tree, department tree (buildTree, detectCycle, calculateTreePath) |
| `WorkflowEngine` | system-admin/workflow/application/ | Approval workflow (state machine with VALID_TRANSITIONS) |
| `AbstractTwoTierCache` | auth-service/shared/cache/ | Permission caching (Caffeine L1 + Redis L2) |
| `IdempotencyFilter` | auth-service/shared/filter/ | POST endpoint idempotency |
| `MfaRateLimitService` | auth-service/auth/application/ | MFA brute-force protection |
| `SessionPromotionService` | auth-service/auth/application/ | Anonymous → authenticated session promotion |
| `SnowflakePersistentAuditableEntity` | base-core | All entities (Snowflake ID + audit fields) |
| `DatabaseMessageSource` | auth-service/shared/i18n/ | I18n message resolution |
| `ServiceTokenService` | auth-service/auth/application/ | Inter-service JWT generation |

### Architecture Insights
- Clean Architecture enforced: `adapter/in/web`, `adapter/out/persistence`, `application`
- CQRS pattern optional (`CqrsAuthController.kt` alongside regular controllers)
- Consistent entity pattern: `SnowflakePersistentAuditableEntity` (Snowflake ID, audit fields)
- DTO mapping via Kotlin extension functions `toResponse()`
- i18n via `DatabaseMessageSource` (api-response-i18n-standard feature)
- Error code patterns: auth-service (`AUTH_001..044`), account-service (`ACCT_001..008`), system-admin-service (`SYS_001..018`)

## Visualization: System Architecture

```text
                    ┌─────────────────────────────────────────────┐
                    │              CLIENT LAYER                    │
                    │  [Web SPA]    [Mobile]    [API Partner]      │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────────┐
                    │           API GATEWAY                        │
                    │  • JWT Validation                            │
                    │  • API Key Auth (SHA-256 lookup via Redis)   │
                    │  • Rate Limiting (Bucket4j ←│              │
              │ • Session│  │         │  │              │
              └─────────┘  └─────────┘  └──────────────┘

Legend: ✅ = Implemented (EXTEND)    ⚠️ = Partial (needs migration)
```

## Event Flow Diagram

```text
┌──────────────────── INTER-SERVICE COMMUNICATION ────────────────────┐
│                                                                      │
│  SYNC (REST — for queries that need immediate response):             │
│                                                                      │
│    system-admin ──GET /api/internal/permissions/{userId}──→ auth     │
│    (menu tree needs user roles for filtering)                        │
│                                                                      │
│    account ──GET /api/internal/users/{userId}──→ auth                │
│    (lifecycle needs auth status for validation)                      │
│                                                                      │
│  ASYNC (Kafka — for state changes):                                  │
│                                                                      │
│    auth ──iam.user.registered──→ account (create default profile)    │
│    auth ──iam.user.sso_provisioned──→ account (SSO profile)         │
│    auth ──iam.permission.changed──→ system-admin (invalidate cache) │
│    account ──acct.lifecycle.deactivated──→ auth (invalidate tokens) │
│    account ──acct.lifecycle.deleted──→ auth (purge user data)       │
│    system-admin ──system.audit.events──→ centralized audit          │
│    system-admin ──system.org.events──→ auth (org structure change)  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

## MFA Progressive Flow (Enhanced)

```text
┌───────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌───────┐
│ Login │────▶│ Validate │────▶│ MFA      │────▶│ Verify   │────▶│ Full  │
│ Form  │     │ Password │     │ Required │     │ 2FA Code │     │ JWT   │
│       │     │ (Argon2) │     │          │     │          │     │ Token │
└───────┘     └──────────┘     └────┬─────┘     └──────────┘     └───────┘
                                     │                              ▲
                                     │  Trusted    ┌──────────┐     │
                                     └──Device────▶│ Skip MFA │─────┘
                                        Check      └──────────┘
                                        (account-   ▲
                                         service)   │
                                                     │
                                     ┌──────────┐   │
                                     │ Recovery │───┘
                                     │ Code     │
                                     └──────────┘

States:
  • partial_token (5min TTL) — only allows /verify-2fa
  • full_token — access to all authorized APIs
  • recovery_code — single-use, bypasses TOTP/OTP
```

## Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| Scope creep (21 FRs, 47 UCs, 3 services) | MEDIUM | HIGH | 3-phase capability-priority rollout; each phase independently shippable |
| Kafka compileOnly → implementation migration | LOW | MEDIUM | `PermissionChangedConsumer.kt` already uses `@KafkaListener`; need build.gradle.kts change only |
| Cross-service data consistency | MEDIUM | MEDIUM | Eventual consistency via Kafka; idempotent consumers; Redis cache TTL as safety net |
| WorkflowEngine edge cases | MEDIUM | MEDIUM | State machine already has `VALID_TRANSITIONS` map; add explicit guard conditions per transition |
| Menu permission complexity (button-level) | LOW | MEDIUM | `MenuPermissionService.kt` already has tree validation; button-level is additive permission_code pattern |
| Inter-service auth security | MEDIUM | HIGH | `ServiceTokenService.kt` already exists; shared secret Phase 1, service accounts later |

## Decision Log

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| D-01 | Lean JWT (userId, tenantId, roleIds only) | Prevent JWT bloat in ERP with 100+ permissions | Confirmed |
| D-02 | Decentralized rate limiting (Bucket4j + Redis) | Eliminate Gateway → system-admin sync bottleneck | Confirmed |
| D-03 | Kafka for all state change events (no Redis Pub/Sub) | Single event backbone, durable, consistent | Revised (was Hybrid) |
| D-04 | SHA-256 hash for API keys (show-once) | Stripe pattern, already implemented in ApiKeyService | Confirmed |
| D-05 | Reuse PBAC PolicyCondition for workflow DSL | Consistency, existing evaluator reusable | Confirmed |
| D-06 | Service JWT for inter-service auth | Simpler than mTLS, uses existing JWT infra | Confirmed |
| D-07 | TreeEntity already in system-admin-service | Do NOT move to base-core, it's service-specific | Confirmed |
| D-08 | Feature type: EXTEND (all services) | Codebase scan confirms substantial implementations | Updated |
| D-09 | Recursive CTE for org hierarchy (not ltree) | Simpler, portable, bounded depth (10 levels) | Confirmed |
| D-10 | Capability-priority phasing over 4-phase rollout | Security-critical first, each phase shippable independently | New |
| D-11 | Use existing custom MfaService over Spring Security 7 MFA | Custom flow is more feature-rich, production-ready | New |
| D-12 | Account lifecycle owned by account-service | Events to auth-service for token invalidation | Confirmed |

## Open Questions for Design Phase
- [RESOLVED] Menu cache invalidation: Redis Pub/Sub vs Kafka → **Kafka only** (single event backbone)
- [RESOLVED] API key secret storage: Hash vs Encrypt → **SHA-256 hash** (already implemented)
- [RESOLVED] Account lifecycle service boundary → **account-service owns, events to auth-service**
- [RESOLVED] TreeEntity location → **Already exists in system-admin-service**
- [RESOLVED] Workflow condition DSL → **Reuse PBAC PolicyCondition JSONB pattern**
- [RESOLVED] Inter-service auth → **Service JWT with shared secret**
- [RESOLVED] Spring Security 7 MFA API → **Keep existing custom MfaService.kt**
- [RESOLVED] Phasing strategy → **Capability-priority (3 phases)**
- [OPEN] OQ-001: Kafka dependency scope in `build.gradle.kts` — currently `compileOnly` or `implementation`? Need to verify and potentially change to `implementation`.
- [OPEN] OQ-002: DPoP (Demonstrating Proof-of-Possession) support → Phase 3+ consideration, not blocking.

## Open Questions for URD Analysis
- None — all requirements covered by research + pre_openspec
