# Comparison Analysis — Architecture Optimization (v2 — Enriched)

## 1. Architecture Pattern Compliance (Updated with Business Handler)

| Feature | Clean Arch. | Hexagonal | Onion | Current auth-service | base-core Available? | Gap |
|---------|:-----------:|:---------:|:-----:|:-------------------:|:-------------------:|:---:|
| **Domain layer pure** | ✅ | ✅ | ✅ | ❌ No domain layer | — | 🔴 |
| **Inbound ports** | ✅ | ✅ | ⚠️ | ❌ Missing | CommandHandler/QueryHandler | 🔴 |
| **Outbound ports** | ✅ | ✅ | ✅ | ❌ Missing | — | 🔴 |
| **Business Handler layer** | ✅ (Use Case) | ✅ (Service) | ✅ | ❌ God-class services | CommandBus/QueryBus ✅ | 🔴 |
| **Adapter layer** | ✅ | ✅ | ✅ | ⚠️ Partial naming | — | 🟡 |
| **Dependency direction** | ✅ | ✅ | ✅ | ❌ Violated | — | 🔴 |
| **Model separation** | ✅ | ✅ | ✅ | ❌ Single JPA model | BaseMapper/CrudMapper ✅ | 🔴 |
| **gRPC inter-service** | ⚠️ | ⚠️ | ⚠️ | ❌ Only REST+Kafka | spring-boot-starter-grpc needed | 🔴 |
| **CQRS pattern** | ⚠️ | ⚠️ | ⚠️ | ❌ Not implemented | base-cqrs-starter ✅ | 🔴 |
| **Exception hierarchy** | ⚠️ | ⚠️ | ⚠️ | ⚠️ Custom (not base-core) | BusinessException ✅ | 🟡 |
| **HTTP client (SOTA)** | — | — | — | ❌ RestTemplate inline | @HttpExchange ✅ | 🔴 |
| **Base @Version** | — | — | — | ⚠️ Only UserEntity | ❌ Not in base-model | 🟡 |
| **Package by feature** | ✅ | ✅ | ✅ | ✅ Present | — | ✅ |
| **Config properties** | — | — | — | ✅ Good | — | ✅ |
| **Snowflake IDs** | — | — | — | ✅ Good | SnowflakeBaseEntity ✅ | ✅ |

## 2. base-core Utilization Score

### auth-service — base-core Feature Usage

| base-core Feature | Available? | Used by auth-service? | Impact of Adoption |
|-------------------|:---------:|:--------------------:|-------------------|
| `CommandBus` + `CommandHandler` | ✅ | ❌ | Replaces god-class AuthService, enables Business Handler pattern |
| `QueryBus` + `QueryHandler` | ✅ | ❌ | Separates read concerns, enables CQRS |
| `EventBus` | ✅ | ❌ | Replaces manual Kafka event publishing |
| `BaseControllerAdvice` | ✅ | ⚠️ Partial | auth has own GlobalExceptionHandler, should extend base |
| `BaseMapper` / `CrudMapper` | ✅ | ❌ | Enables Entity ↔ Domain mapping |
| `CommandService` / `QueryService` | ✅ | ❌ | Base service interfaces for CQRS |
| `ApiResponse` wrapper | ✅ | ❌ | Standardized response format |
| `CursorPagination` | ✅ | ❌ | High-performance pagination |
| `BaseController` | ✅ | ❌ | Base REST controller utilities |
| `SessionManagement` | ✅ | ✅ | DefaultSessionManagement used |
| `PluginLifecycleHook` | ✅ | ❌ | Plugin system for extensibility |
| `base-resilience-starter` | ✅ | ❌ | Circuit breaker, retry, rate limiter |
| `base-observability-starter` | ✅ | ❌ | Metrics, tracing auto-config |

**Utilization Score**: auth-service uses **2 out of 13** available base-core features (15%)

> [!IMPORTANT]
> Đây là phát hiện quan trọng nhất: base-core đã cung cấp hầu hết infrastructure cần thiết cho Clean Architecture + CQRS + Business Handler pattern. auth-service chỉ cần **adopt** chứ không cần **build from scratch**.

## 3. Communication Pattern Comparison (Updated with gRPC)

### Current vs Target

| Communication | Current | Target | Reason |
|--------------|---------|--------|--------|
| **auth ↔ account** | Kafka (SSO event only) | **gRPC** (sync query) + **Kafka** (async events) | Permission check needs sync response |
| **auth ↔ system-admin** | None | **gRPC** (menu permission check) | Fast permission verification |
| **business services → auth** | REST API | **gRPC** (permission check) + **JWT** (self-verify) | 100M TPS requires gRPC + local JWT |
| **auth → SSO providers** | `RestTemplate` inline | **`@HttpExchange`** declarative client | SOTA, type-safe, auto-configured |
| **auth → Redis** | Spring Data Redis | Keep + add **Caffeine L1** | Multi-tier caching |
| **auth → PostgreSQL** | JPA sync | JPA + **read replicas** | Read-heavy optimization |

### gRPC vs REST Performance at Scale

| Metric | REST (JSON) | gRPC (Protobuf) | Improvement |
|--------|:-----------:|:---------------:|:-----------:|
| Serialization size | 100% | ~30-40% | 2.5-3x smaller |
| Latency (p50) | 5-10ms | 1-3ms | 3-5x faster |
| Throughput | Baseline | 2-5x | HTTP/2 multiplexing |
| Connection reuse | Per-request | Long-lived | No handshake overhead |
| Type safety | Runtime (JSON parsing) | Compile-time (Protobuf) | Safer |

## 4. Gap Analysis — base-model Enhancement

### @Version Addition Impact

| Entity Base Class | Current Inheritors | Impact of Adding @Version |
|---|---|---|
| `SnowflakeBaseEntity` | ActionEntity, PermissionEntity, RefreshTokenEntity, TokenBlacklistEntity | ⚠️ May not need — lookup/immutable tables |
| `SnowflakeAuditableEntity` | — (not used currently) | ✅ Good — mutable entities need @Version |
| `SnowflakePersistentAuditableEntity` | UserEntity, DomainEntity, GroupEntity, DomainRoleEntity, DomainResourceEntity, RolePermissionEntity, PolicyEntity | ✅ Must have — all are mutable |

**Decision**: Add `@Version` to `AuditableEntity` (not `BaseEntity`) — only mutable entities need optimistic locking.

## 5. Service-by-Service Gap Score (Updated)

### auth-service — Gap Score: 25/100 (decreased from 35 due to unused base-core)

```
Category Scores:
  Domain Purity:        0/15  ← No domain layer
  Port Interfaces:      0/10  ← No ports
  Business Handlers:    0/10  ← No CQRS handlers (despite base-core having them!)
  Model Separation:     0/10  ← Single JPA model (despite MapStruct available)
  Test Coverage:        0/10  ← ~0%
  base-core Adoption:   3/10  ← Uses 2/13 features (15%)
  gRPC Integration:     0/10  ← REST only
  HTTP Client Modern:   0/5   ← RestTemplate inline
  Exception Base:       3/5   ← Custom, not extending base-core
  Performance:          5/5   ← Snowflake IDs, @Version (partial)
  Scalability Infra:    4/10  ← Kafka, Redis present but underutilized
  ─────────────────────────
  TOTAL:               15/100 → WITH base-core adoption potential: 25/100
```

## 6. Recommendation (Updated)

### Verdict: **ADOPT + ENHANCE** (Adopt base-core → Restructure)

**Critical insight**: The heavy lifting is already done in base-core. The gap is not "build infrastructure" but "adopt what exists."

### Priority Matrix

| Priority | Action | Effort | Value |
|:--------:|--------|:------:|:-----:|
| **P0** | Adopt `base-cqrs-starter` — refactor AuthService → CommandHandlers/QueryHandlers | M | 🔴 Critical |
| **P1** | Create domain models (pure Kotlin) + MapStruct mappers | L | 🔴 Critical |
| **P2** | Create outbound port interfaces | M | 🔴 Critical |
| **P3** | Add `@Version` to `AuditableEntity` in base-model | S | 🟡 High |
| **P4** | Migrate `RestTemplate` → `@HttpExchange` | S | 🟡 High |
| **P5** | Extend `BaseControllerAdvice` (not duplicate) | S | 🟡 High |
| **P6** | Add `spring-boot-starter-grpc` to platform BOM | S | 🟡 High |
| **P7** | Create `grpc-proto` shared module | M | 🟡 High |
| **P8** | Implement gRPC PermissionService | L | 🔴 Critical |
| **P9** | Multi-tier caching (Caffeine L1 + Redis L2) | M | 🟡 High |
| **P10** | Fix N+1 in RbacEngine | S | 🟡 High |
| **P11** | Write ArchUnit tests | M | 🟡 High |
| **P12** | Standardize account-service + system-admin-service | L | ⚠️ Medium |

## 7. Risk Assessment (Updated)

| Risk | Probability | Impact | Mitigation |
|------|:-----------:|:------:|------------|
| base-core `@Version` addition breaks existing DB migrations | Low | High | Create Flyway migration: `ALTER TABLE x ADD COLUMN version BIGINT DEFAULT 0` |
| gRPC proto schema evolution breaks backward compatibility | Medium | High | Use Protobuf field numbering best practices, never reuse numbers |
| CQRS handler proliferation (too many small classes) | Medium | Low | Group related handlers, use sealed command hierarchies |
| Caffeine L1 cache stale data | Medium | Medium | Short TTL (30s) + Kafka-driven invalidation |
| RestTemplate → @HttpExchange migration risk | Low | Low | Same HTTP semantics, different API surface |
| Multiple Gradle submodules → build complexity | Medium | Medium | Start minimal (grpc-proto, domain-common), add when needed |
