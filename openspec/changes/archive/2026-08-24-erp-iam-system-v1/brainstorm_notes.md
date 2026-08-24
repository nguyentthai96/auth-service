---
type: brainstorm_notes
change: erp-iam-system
date: 2026-08-23
selected_direction: "Event-Driven & Redis-Backed Decentralized Authorization with Phased Rollout"
pre_flow: "Non-Financial"
pre_feature_type: "NEWBUILD"
status: complete
---

# Brainstorm Notes: ERP IAM System Architecture

## Date
2026-08-23 (revised — original brainstorm 2026-08-05, updated with codebase grounding)

## Context
Full ERP IAM system spanning 3 microservices: `auth-service` (authentication, MFA, SSO, JWT, RBAC, PBAC — strong foundation already exists), `account-service` (profile, device, session, lifecycle — partial), `system-admin-service` (menu permission, organization, API partner, workflow, audit, config — partial). Research completed with 47 use cases, 57+ endpoints, 30+ entities across 3 ERDs.

**Critical Update from Codebase Scan (2026-08-23)**: The original research_brief.md (A4) assumed account-service and system-admin-service were "stub only". A deeper scan reveals:
- **system-admin-service** already has: `MenuEntities.kt` (4 entities), `MenuPermissionService.kt` (full CRUD + permission algorithm + Redis caching), `ApiPartnerEntities.kt` (5 entities including SubscriptionPlan), `ApiKeyService.kt` (full lifecycle: generate, rotate, revoke, validate, Redis sync), `DomainConfigService.kt` + entities + history tracking, `MenuPermissionCacheAdapter.kt`, `MenuController.kt`, `ApiPartnerController.kt`, `ApiUsageController.kt`, `DomainConfigController.kt`
- **account-service** already has: `AccountLifecycleService.kt` + `DataExportService.kt` + entities, `SessionService.kt` + `SessionRedisAdapter.kt` + `SessionController.kt` + `ActiveSessionEntity.kt`, `ProfileKafkaListener.kt` (listener only, no CRUD yet)

**Impact**: Feature type for several FRs shifts from NEWBUILD → EXTEND. Build effort reduced by ~30-40%.

## Questions Asked & Answers

### Q1: API Gateway Rate Limiting Bottleneck
**Q:** Làm sao để API Gateway kiểm tra API Key Rate Limiting mà không bị bottleneck khi gọi liên tục vào `system-admin-service`?

**A:** Decentralized approach. `system-admin-service` manages CRUD + pushes config to Redis. Gateway reads directly from Redis via Bucket4j `ProxyManager`. **Confirmed in code**: `ApiKeyService.kt#syncRateLimitToRedis()` already pushes rate limit config to Redis key `rate_limit:bucket4j:{keyHash}`. Pattern validated.

### Q2: JWT Size — Embed Permissions or Resolve Dynamically?
**Q:** Quyền hạn nên nhúng vào JWT hay kiểm tra động?

**A:** Lean JWT — chỉ chứa `userId`, `tenantId`, `roleIds`. Permission resolution at runtime via Redis cache. Lý do: ERP systems have 100+ permissions → JWT bloat → HTTP header overflow. **Confirmed in code**: `JwtService.kt` already issues tokens with minimal claims. `MenuPermissionService.kt` resolves permissions via Redis L2 cache.

### Q3: SSO Auto-Provisioning Sync
**Q:** Khi user Auto-provision qua SSO, làm sao đồng bộ Profile?

**A:** Event-driven via Kafka. `auth-service` publishes `iam.user.sso_provisioned`, `account-service` consumes to create default profile. **Confirmed in code**: `SsoProvisionedEvent` and `UserRegisteredEvent` already defined in `EventPublisher.kt`. `SpringEventPublisher.kt` has Phase 3 TODO for Kafka migration. `ProfileKafkaListener.kt` exists in account-service as the consumer stub.

### Q4: Menu Cache Invalidation — Redis Pub/Sub vs Kafka?
**Q:** Khi role permissions thay đổi ở `system-admin-service`, dùng cơ chế nào để invalidate menu cache?

**A:** **Hybrid approach** — Redis Pub/Sub for instant cache invalidation (operational signal), Kafka for permission change audit trail (business event).

**Reasoning:**
| Factor | Redis Pub/Sub | Kafka |
|--------|:---:|:---:|
| Latency | <1ms ✅ | ~50-100ms |
| Already available | ✅ (Redis running) | ❌ (compileOnly) |
| Precedent in codebase | ✅ (`channel:api_key_revoked`) | ❌ |
| Durability | ❌ fire-and-forget | ✅ persistent |
| Blocks on Phase 4? | ❌ | ✅ |

**Fallback**: Even if Pub/Sub message is missed, Redis cache TTL (5 min) auto-expires → max staleness = 5 minutes. SLA target ≤5s met via Pub/Sub; TTL provides safety net.

### Q5: API Partner Secret Storage — Hash or Encrypt?
**Q:** API Partner key secret — hash một chiều hay AES encrypt?

**A:** **SHA-256 one-way hash** (Stripe pattern). No need to show the key again after generation. **Confirmed in code**: `ApiKeyService.kt#hashApiKey()` already uses `MessageDigest.getInstance("SHA-256")`. Show-once pattern implemented via `generateApiKey()` returning `Pair<Entity, rawKey>`.

### Q6: Account Lifecycle — Which Service Owns It?
**Q:** FR-005 (Profile) trên account-service nhưng FR-009 (Lifecycle) có components trên cả hai services — inconsistency?

**A:** **Split responsibility with event contract:**
- `account-service` owns profile data CRUD, device management, session management
- `auth-service` owns authentication state (lock, MFA, credentials)
- Account lifecycle actions (deactivate, delete, export) involve both:
  - Deactivate: account-service initiates → Kafka event → auth-service invalidates tokens + sessions
  - Delete (GDPR): account-service initiates → collects profile data + sends Kafka → auth-service exports auth data → account-service aggregates
- **Confirmed in code**: `AccountLifecycleService.kt` and `DataExportService.kt` already exist in account-service (not auth-service as originally stated in pre_openspec FR-009)

### Q7: Organization Hierarchy — How Deep?
**Q:** Department tree max depth và storage strategy?

**A:** Max 10 levels (configurable). Use `parent_id` with recursive CTE for PostgreSQL queries. Don't use `ltree` — simpler, portable, and depth is bounded. Circular reference detection via DFS/BFS before save.

### Q8: Workflow Condition DSL — What Schema?
**Q:** pre_openspec OQ identified that workflow condition DSL is undefined. What format?

**A:** Reuse the existing PBAC `PolicyCondition` pattern — JSONB with operators:
```json
{
  "operator": "AND",
  "conditions": [
    {"field": "amount", "op": "GT", "value": 10000},
    {"field": "department", "op": "EQ", "value": "FINANCE"}
  ]
}
```
**Reasoning:** PolicyEvaluator pattern already exists in auth-service. Same expression evaluator can be extracted to base-core `common-expression` module and reused.

### Q9: Inter-Service Authentication
**Q:** How do the 3 services authenticate to each other for internal APIs?

**A:** **Service-level JWT** (not mTLS). Simpler to implement, works with existing JWT infrastructure.
- Each service has a `SERVICE` role with internal-only permissions
- Internal endpoints use path prefix `/api/internal/` and require service JWT
- Service JWT issued at startup from a shared secret or service account
- **Phase 1:** Shared secret pre-configured. **Phase 4:** Migrate to service account auto-registration.

### Q10: TreeEntity Base Class Design
**Q:** base-core lacks TreeEntity. What should it look like?

**A:** `@MappedSuperclass` extending `SnowflakePersistentAuditableEntity`:
```kotlin
@MappedSuperclass
abstract class TreeEntity<T : TreeEntity<T>> : SnowflakePersistentAuditableEntity() {
    abstract var parentId: Long?
    abstract var sortOrder: Int
    abstract var level: Int
    
    @Transient
    var children: MutableList<T> = mutableListOf()
    
    fun isRoot(): Boolean = parentId == null
}
```
Used by: `MenuItemEntity`, `DepartmentEntity`. Helper utility `TreeBuilder.kt` provides `buildTree()` and `detectCycle()`.

**However** — `MenuItemEntity` already exists without `TreeEntity` base. Decision: **Don't create TreeEntity for Phase 1.** Menu already works. Create `TreeEntity` when building `DepartmentEntity` (same phase), then optionally refactor Menu to extend it.

## Approaches Considered

### Architecture-Level Decision

#### Approach 1: Monolithic IAM Service
- **Pros:** Simpler deployment, no inter-service communication overhead
- **Cons:** Violates existing 3-service architecture, single DB bottleneck, cannot scale independently

#### Approach 2: 3 Microservices (Current Direction) 👈 *Selected*
- **Pros:** Bounded contexts (auth, account, admin), independent scaling, already started
- **Cons:** Inter-service communication complexity, eventual consistency

#### Approach 3: 2 Services (Merge account into auth)
- **Pros:** Reduces inter-service calls for user data
- **Cons:** auth-service becomes too large, mixing concerns

### Inter-Service Communication

#### Approach 1: REST Sync (Full)
- **Pros:** Simple, immediate response
- **Cons:** Tight coupling, cascading failures

#### Approach 2: Event-Driven (Full Kafka)
- **Pros:** Loose coupling, resilient
- **Cons:** Complex, Kafka is compileOnly, eventual consistency for everything

#### Approach 3: Hybrid REST + Kafka 👈 *Selected*
- **Pros:** Sync for queries (menu tree needs roles → REST call to auth-service), Async for state changes (user created → Kafka event)
- **Cons:** Two communication patterns to maintain

### Caching Strategy

#### Approach 1: Redis Only
- **Pros:** Shared across instances, durable
- **Cons:** Network hop for every check (~1-5ms)

#### Approach 2: Caffeine L1 + Redis L2 (Two-Tier) 👈 *Selected*  
- **Pros:** L1 eliminates network hop for hot data (<0.1ms), L2 provides cross-instance consistency
- **Cons:** Cache coherence between L1 instances (solved by short L1 TTL = 30s)
- **Already in use:** `RedisConfig.kt` configures both tiers

## Selected Direction

**Event-Driven & Redis-Backed Decentralized Authorization with Phased Rollout**

Core architecture principles:
1. **Redis as shared backbone** — permissions, rate limits, sessions, OTP state, menu cache
2. **Kafka for state change events** — user provisioning, audit, permission changes (Phase 3-4)
3. **Lean JWT** — identity + roles only, permissions resolved at runtime
4. **Decentralized rate limiting** — Bucket4j + Redis at Gateway, system-admin-service CRUD only
5. **Hybrid communication** — REST sync for queries, Kafka async for state changes
6. **Two-tier caching** — Caffeine L1 (30s) + Redis L2 (5-30min) for all permission-sensitive data

## Pre-classifications (preliminary)
- Feature type: NEWBUILD (org module, workflow module, profile CRUD) + EXTEND (menu, API partner, auth MFA/SSO, session, lifecycle)
- Flow type: Non-Financial (IAM, profile, configuration management)
- Affected modules: auth-service, account-service, system-admin-service, base-core (TreeEntity), gateway (Bucket4j filter)

## Codebase Findings

### Existing Implementation Status (Updated 2026-08-23)

| Module | Service | Status | Key Files | Remaining Work |
|--------|---------|--------|-----------|----------------|
| Auth (MFA/SSO/JWT) | auth-service | ✅ Strong | 28 application files | Minor enhancements |
| RBAC Engine | auth-service | ✅ Complete | `RbacEngine.kt`, entities | None |
| PBAC PolicyEvaluator | auth-service | ✅ Complete | `PolicyEvaluator.kt` | Reuse pattern for workflow |
| Password Policy | auth-service | ✅ Complete | `PasswordPolicyService.kt`, entities | None |
| Session Management | auth-service | ✅ Complete | `LoginSessionService.kt`, `SessionPolicyService.kt` | None |
| E2EE | auth-service | ✅ Complete | `DecryptionVaultService.kt` | None |
| Event Publisher | auth-service | ⚠️ Stub | `SpringEventPublisher.kt` (logs only) | Kafka adapter (Phase 3) |
| **Menu Permission** | system-admin | ✅ **Implemented** | `MenuPermissionService.kt`, 4 entities, cache adapter | Circular ref detection, validation |
| **API Partner** | system-admin | ✅ **Implemented** | `ApiKeyService.kt`, 5 entities, Redis sync | Usage dashboard, IP whitelist enforcement |
| **Domain Config** | system-admin | ✅ **Implemented** | `DomainConfigService.kt`, history tracking | Feature flags extension |
| **Session (Redis)** | account-service | ✅ **Implemented** | `SessionService.kt`, `SessionRedisAdapter.kt` | None |
| **Account Lifecycle** | account-service | ✅ **Implemented** | `AccountLifecycleService.kt`, `DataExportService.kt` | GDPR enhancements |
| Profile CRUD | account-service | ⚠️ Listener only | `ProfileKafkaListener.kt` | Full CRUD service + controller |
| Organization | system-admin | ❌ Not started | — | Full module: Department, Position |
| Approval Workflow | system-admin | ❌ Not started | — | Full module: Definition, Instance, Engine |
| Audit Trail (enhanced) | system-admin | ❌ Not started | — | Immutable log, search, export |
| Device Management | account-service | ❌ Not started | — | Full module: Device entity, trust |
| Preferences | account-service | ❌ Not started | — | Key-value preferences store |

### Related Processes & Symbols
- `AuthService.kt` (12.9KB) — core auth orchestrator, login/register flows
- `MfaService.kt` (9.5KB) — MFA verification, enable/disable
- `JwtService.kt` (8.7KB) — JWT generation/validation (RS256)
- `SsoAdapter.kt` (8.0KB) — OAuth2/SSO integration adapter
- `LoginSessionService.kt` (7.2KB) — session tracking + cleanup
- `MenuPermissionService.kt` — full menu tree algorithm with Redis caching
- `ApiKeyService.kt` — complete API key lifecycle + Redis rate limit sync
- `EventPublisher.kt` — domain event port with 3 event types defined
- `SpringEventPublisher.kt` — stub adapter (logs only, Phase 3 → Kafka)

### Architecture Insights
- Clean Architecture enforced: `adapter/in/web`, `adapter/out/persistence`, `application`
- CQRS pattern optional (`CqrsAuthController.kt` exists alongside regular controllers)
- Consistent entity pattern: `SnowflakePersistentAuditableEntity` (Snowflake ID, audit fields)
- DTO mapping via Kotlin extension functions `toResponse()`
- i18n via `DatabaseMessageSource` (api-response-i18n-standard feature)
- Kafka `compileOnly` in build.gradle.kts — needs `implementation` upgrade for Phase 3

## Revised Phased Rollout

Based on actual codebase state (many modules already implemented), revised phases:

### Phase 1: Fill Gaps (2-3 weeks)
| Task | Service | Status | Effort |
|------|---------|--------|--------|
| Profile CRUD + controller | account-service | NEWBUILD | 3-4 days |
| Device Management module | account-service | NEWBUILD | 3-4 days |
| Preferences module | account-service | NEWBUILD | 2-3 days |
| Organization module (dept + position) | system-admin | NEWBUILD | 4-5 days |
| Audit Trail (immutable, search, export) | system-admin | NEWBUILD | 3-4 days |

### Phase 2: Enhancements (2-3 weeks)
| Task | Service | Status | Effort |
|------|---------|--------|--------|
| Menu: circular ref detection, validation | system-admin | EXTEND | 1-2 days |
| API Partner: usage dashboard, IP whitelist | system-admin | EXTEND | 2-3 days |
| Domain Config: feature flags extension | system-admin | EXTEND | 1-2 days |
| Auth: MFA enhancements (recovery codes UI flow) | auth-service | EXTEND | 2-3 days |
| Auth: SSO auto-provision enhancements | auth-service | EXTEND | 2-3 days |
| Lifecycle: GDPR data export improvements | account-service | EXTEND | 1-2 days |

### Phase 3: Advanced Features (2-3 weeks)
| Task | Service | Status | Effort |
|------|---------|--------|--------|
| Approval Workflow engine | system-admin | NEWBUILD | 5-7 days |
| System Config + Feature Flags CRUD | system-admin | EXTEND | 2-3 days |
| Inter-service auth (service JWT) | all services | NEWBUILD | 2-3 days |

### Phase 4: Integration & Polish (1-2 weeks)
| Task | Service | Status | Effort |
|------|---------|--------|--------|
| Kafka upgrade (compileOnly → implementation) | all services | EXTEND | 2-3 days |
| Replace SpringEventPublisher with KafkaPublisher | auth-service | EXTEND | 1-2 days |
| Idempotency filter (FR-017) | all services | NEWBUILD | 1-2 days |
| Timeout + retry for external APIs (FR-019, FR-020) | auth-service | EXTEND | 1-2 days |
| E2E integration tests | all services | NEWBUILD | 3-4 days |

## Visualization: Updated Architecture

```text
                    ┌─────────────────────────────────────────────┐
                    │              CLIENT LAYER                    │
                    │  [Web SPA]    [Mobile]    [API Partner]      │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────────┐
                    │           API GATEWAY                        │
                    │  • JWT Validation                            │
                    │  • API Key Auth (SHA-256 lookup)             │
                    │  • Rate Limiting (Bucket4j ←── Redis)       │
                    │  • Route to target service                   │
                    └──┬────────────┬────────────┬───────────────┘
                       │            │            │
         ┌─────────────▼──┐  ┌─────▼──────┐  ┌──▼──────────────┐
         │ auth-service    │  │ account-   │  │ system-admin-   │
         │                 │  │ service    │  │ service         │
         │ ✅ Auth/JWT     │  │            │  │                 │
         │ ✅ MFA/TOTP     │  │ ⚠️ Profile │  │ ✅ Menu Perms   │
         │ ✅ SSO/OAuth2   │  │ ❌ Device  │  │ ✅ API Partner  │
         │ ✅ RBAC/PBAC    │  │ ✅ Session  │  │ ✅ Domain Cfg   │
         │ ✅ Password Pol │  │ ✅ Lifecycl │  │ ❌ Organization │
         │ ✅ E2EE         │  │ ❌ Prefs   │  │ ❌ Workflow     │
         │ ⚠️ Events      │  │            │  │ ❌ Audit Trail  │
         └───────┬────────┘  └─────┬──────┘  └───────┬─────────┘
                 │                  │                   │
                 └──────────┬───────┴──────────────────┘
                            │
              ┌─────────────▼──────────────────────────┐
              │          INFRASTRUCTURE                  │
              │                                          │
              │  ┌─────────┐  ┌────────┐  ┌──────────┐ │
              │  │ Redis   │  │ Kafka  │  │PostgreSQL│ │
              │  │ ────────│  │ ───────│  │ ─────────│ │
              │  │ • Cache │  │ • User │  │ auth_db  │ │
              │  │ • Rate  │  │   events│  │ acct_db  │ │
              │  │   Limit │  │ • Perm │  │ sadm_db  │ │
              │  │ • OTP   │  │   change│  │          │ │
              │  │ • Menu  │  │ • Audit│  │          │ │
              │  │ • Sess  │  │        │  │          │ │
              │  └─────────┘  └────────┘  └──────────┘ │
              └────────────────────────────────────────┘

Legend: ✅ = Implemented    ⚠️ = Partial    ❌ = Not Started
```

## Event Flow Diagram

```text
                     SYNC (REST)                    ASYNC (Kafka / Redis Pub/Sub)
                     ──────────                     ─────────────────────────────

  ┌─ system-admin ──────────────── GET /api/internal/permissions ──→ auth-service
  │  (needs user roles                                                    │
  │   for menu tree)                                                      │
  │                                                                       │
  │  auth-service ──── Kafka: iam.user.registered ───────────→ account-service
  │                ──── Kafka: iam.user.sso_provisioned ─────→ account-service
  │                                                                       │
  │  system-admin ──── Redis Pub/Sub: menu_cache_invalidated ──→ self (all instances)
  │                ──── Redis Pub/Sub: api_key_revoked ────────→ gateway
  │                ──── Kafka: iam.permission.changed ─────────→ auth-service (audit)
  │                                                                       │
  │  account-service ──── Kafka: acct.lifecycle.deactivated ────→ auth-service (invalidate tokens)
  │                  ──── Kafka: acct.lifecycle.deleted ───────→ auth-service (purge user data)
  └──────────────────────────────────────────────────────────────────────────────────────────────
```

## Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| Scope creep (21 FRs, 47 UCs) | HIGH | HIGH | 4-phase rollout with strict gates; defer Phase 3+ if needed |
| Workflow state machine edge cases | MEDIUM | MEDIUM | Immutable definitions; explicit guard conditions |
| Cross-service data consistency | MEDIUM | MEDIUM | Eventual consistency via Kafka; idempotent consumers |
| Inter-service auth (no mTLS/JWT) | MEDIUM | HIGH | Service JWT in Phase 3; shared secret Phase 1 |

## Open Questions for Design Phase
- [RESOLVED] Menu cache invalidation: Redis Pub/Sub vs Kafka → **Hybrid** (Pub/Sub for invalidation, Kafka for audit)
- [RESOLVED] API key secret storage: Hash vs Encrypt → **SHA-256 hash** (already implemented)
- [RESOLVED] Account lifecycle service boundary → **account-service owns, events to auth-service**
- [RESOLVED] TreeEntity for base-core → **Defer to Phase 1 when building DepartmentEntity**
- [RESOLVED] Workflow condition DSL → **Reuse PBAC PolicyCondition JSONB pattern**
- [RESOLVED] Inter-service auth → **Service-level JWT with shared secret (Phase 1), service accounts (Phase 4)**
- [OPEN] Spring Security 7 `@EnableMultiFactorAuthentication` stable? → If unstable, use existing custom `MfaService.kt` flow (already working). Low risk — custom fallback is production-ready.
- [OPEN] DPoP (Demonstrating Proof-of-Possession) support → Phase 4+ consideration, not blocking.

## Open Questions for URD Analysis
- None — all requirements covered by research + pre_openspec

## Decision Log

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| D-01 | Lean JWT (userId, tenantId, roleIds only) | Prevent JWT bloat in ERP with 100+ permissions | 2026-08-05 |
| D-02 | Decentralized rate limiting (Bucket4j + Redis) | Eliminate Gateway → system-admin sync bottleneck | 2026-08-05 |
| D-03 | Event-driven inter-service (Kafka async) | Loose coupling, resilience, retry via DLQ | 2026-08-05 |
| D-04 | Redis Pub/Sub for cache invalidation | Near-instant (<1ms), pattern exists in codebase | 2026-08-23 |
| D-05 | SHA-256 hash for API keys (show-once) | Stripe pattern, already implemented | 2026-08-23 |
| D-06 | Reuse PBAC PolicyCondition for workflow DSL | Consistency, existing evaluator reusable | 2026-08-23 |
| D-07 | Service JWT for inter-service auth | Simpler than mTLS, uses existing JWT infra | 2026-08-23 |
| D-08 | Defer TreeEntity to Phase 1 (DepartmentEntity) | MenuItemEntity already works without it | 2026-08-23 |
| D-09 | Feature type: mixed NEWBUILD + EXTEND | Many modules already partially implemented | 2026-08-23 |
| D-10 | Recursive CTE for org hierarchy (not ltree) | Simpler, portable, bounded depth (10 levels) | 2026-08-23 |
