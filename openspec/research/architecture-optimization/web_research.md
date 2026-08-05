# Web Research — Architecture Optimization (v2 — Enriched)

## Search Strategy (Updated)

| # | Query | Sources | Key Insight |
|---|-------|:-------:|------------|
| 1 | Clean Arch Hexagonal Spring Boot Kotlin billion users | 8+ | Domain must be pure Kotlin, no framework deps |
| 2 | Hexagonal ports adapters domain model production microservices | 18+ | Strict model separation (Domain ≠ JPA ≠ DTO) |
| 3 | Spring Boot 10M TPS CQRS event sourcing virtual threads | 6+ | CQRS essential, DB always bottleneck |
| 4 | **Spring Boot 4.0 features RestClient HttpInterface JDK 25** | 10+ | `@HttpExchange` + `@ImportHttpServices` = SOTA |
| 5 | **Spring Boot gRPC starter inter-service load balancing** | 12+ | Native `spring-boot-starter-grpc` in 4.1 |
| 6 | **RBAC permissions cache 100M TPS gRPC authorization** | 8+ | Multi-tier cache + JWT embedded + proxyless gRPC |
| 7 | **Kotlin 2.4 JDK 25 LTS Spring Boot 4 compatibility** | 6+ | Context parameters stable, Java 25 full support |
| 8 | **Business Handler pattern CQRS CommandHandler QueryHandler** | 5+ | Maps to CQRS handler pattern naturally |

---

## Finding 1: Spring Boot 4.1.0 — New Capabilities

**Source**: Spring.io release notes, InfoQ, Dev.to

### Key Features Available NOW (in base-core 4.1.0)

| Feature | Description | Impact on Architecture |
|---------|-------------|----------------------|
| **`@HttpExchange` + `@ImportHttpServices`** | Declarative HTTP clients — define interface, Spring generates implementation | Replace ALL `RestTemplate` usage (F-08: SsoAdapter) |
| **`spring-boot-starter-grpc`** | Native gRPC auto-configuration with client/server support | Enable gRPC inter-service communication (U-02) |
| **API Versioning** | Native MVC + WebFlux API versioning | Future-proof API evolution |
| **JSpecify null-safety** | Compile-time null checks across framework | Better Kotlin interop |
| **Built-in `@Retryable`** | Framework 7 native resilience without external libs | Simplify resilience patterns |
| **HTTP Client Groups** | Configure multiple clients with shared RestClient infrastructure | Organize SSO providers, internal services |

### RestTemplate Migration Path

```
RestTemplate (DEPRECATED in SB4)
    ↓
RestClient (Synchronous, fluent API)
    ↓
@HttpExchange interface (SOTA — declarative, type-safe, auto-configured)
```

**Applicability**: SsoAdapter.kt currently creates `RestTemplate()` inline. Must migrate to `@HttpExchange` interface.

---

## Finding 2: gRPC in Spring Boot 4.1 — Native Support

**Source**: Spring.io, Dev.to, InfoQ

### Architecture Decision

| Communication | When to Use | Protocol |
|--------------|-------------|----------|
| **gRPC** | Sync queries between services, low-latency permission checks | HTTP/2 + Protobuf |
| **Kafka** | Async events, state changes, data replication | Event streaming |
| **REST** | External API, public endpoints, browser clients | HTTP/1.1 + JSON |

### gRPC Load Balancing (Critical)

gRPC uses long-lived HTTP/2 connections → standard L4 load balancers FAIL.

| Strategy | When | Pros | Cons |
|----------|------|------|------|
| **Client-side LB** (round_robin) | Bare metal / simple K8s | No extra infra, low latency | Client needs service discovery |
| **Service Mesh** (Linkerd/Istio) | K8s at scale | Full L7 LB, mTLS | Sidecar overhead |
| **Proxyless gRPC** | Extreme performance | No sidecar, lowest latency | Complex setup |

**Recommendation for user**: Start with **client-side round_robin** (bare metal) → evolve to **service mesh** on K8s.

### Proto Organization

```protobuf
// auth/permission_service.proto
service PermissionService {
  rpc CheckPermission(PermissionRequest) returns (PermissionResponse);
  rpc GetEffectivePermissions(EffectivePermRequest) returns (PermissionSet);
  rpc ValidateToken(TokenRequest) returns (TokenValidation);
}

message PermissionRequest {
  int64 user_id = 1;
  int64 domain_id = 2;
  string resource_code = 3;
  string action_code = 4;
}
```

---

## Finding 3: RBAC Distribution at 100M TPS

**Source**: OPA docs, Envoy docs, CNCF security patterns

### Multi-Tier Caching Strategy

| Tier | Technology | TTL | Hit Rate | Latency |
|------|-----------|:----|:--------:|:-------:|
| **L1: In-Process** | Caffeine | 30s | ~85% | <1μs |
| **L2: Distributed** | Redis Cluster | 5min | ~10% | <2ms |
| **L3: Source** | auth-service gRPC | — | ~5% | <10ms |

### Invalidation Strategy

```
Permission Change Event Flow:
admin-ui → auth-service → PostgreSQL (write)
                        → Kafka topic "iam.permission.changed"
                        → All services consume → invalidate L1 + L2
```

### JWT Embedded Permissions (Reduce Network Calls)

```kotlin
// JWT payload structure for cross-service authorization
{
  "sub": "12345",
  "roles": ["ADMIN", "FINANCE_MANAGER"],
  "permissions": ["booking:read", "booking:create", "payment:approve"],
  "active_domain": "hotel-chain-A",
  "domains": ["hotel-chain-A", "hotel-chain-B"],
  "exp": 1722873600
}
```

**Benefits**:
- 95%+ of permission checks resolved locally from JWT claims
- No network call needed for standard RBAC checks
- gRPC call only for complex PBAC policy evaluation

**Applicability**: auth-service already embeds `roles` and `permissions` in JWT. Need to optimize the format and ensure all business services can parse them independently.

---

## Finding 4: Business Handler Pattern → CQRS Handlers

**Source**: Architecture patterns, CQRS literature, base-core source analysis

### Mapping User's "Business Handler" to base-core CQRS

User wants: `Controller → Business Handler → Service → Repository/Remote API`

base-core already provides:
- `Command<R>` — marker interface for write intents
- `CommandBus` — dispatches commands to handlers
- `CommandHandler<C, R>` — processes commands
- `Query<R>` — marker interface for read intents
- `QueryBus` — dispatches queries to handlers
- `QueryHandler<Q, R>` — processes queries
- `SpringCommandBus` / `SpringQueryBus` — auto-discovery implementations

**This is exactly the Business Handler pattern**, just named differently:

| User's Term | CQRS Term | base-core Interface |
|-------------|-----------|-------------------|
| Business Handler (Write) | Command Handler | `CommandHandler<C, R>` |
| Business Handler (Read) | Query Handler | `QueryHandler<Q, R>` |
| Business Request | Command / Query | `Command<R>` / `Query<R>` |
| Handler Registry | Bus | `CommandBus` / `QueryBus` |

**Applicability**: auth-service should adopt `base-cqrs-starter` and implement handlers instead of god-class services.

---

## Finding 5: Base Entity @Version for Optimistic Locking

**Source**: base-core source code analysis, JPA best practices

### Current State
- `base-model` entity hierarchy: `BaseEntity → AuditableEntity → PersistentAuditableEntity`
- **NO `@Version` field** in any base class
- Only `UserEntity` in auth-service manually adds `@Version`

### Recommendation

Add `@Version` to `AuditableEntity` (the first entity that has mutable fields):

```kotlin
// base-model/src/.../auditing/Auditable.kt
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity<ID> : BaseEntity<ID>() {
    
    @jakarta.persistence.Version
    var version: Long = 0  // ← ADD THIS
    
    @CreatedDate
    @Column(updatable = false)
    var createdAt: Instant? = null
    // ... other fields
}
```

**Why AuditableEntity** (not BaseEntity):
- `BaseEntity` is for lookup/immutable tables → no concurrent updates
- `AuditableEntity` onwards has mutable fields → needs optimistic locking
- All `SnowflakeAuditableEntity` and `SnowflakePersistentAuditableEntity` inheritors automatically get `@Version`

---

## Finding 6: Kotlin 2.4 Features for Architecture

**Source**: Kotlin blog, JetBrains release notes

### Context Parameters (Stable) — For Clean Architecture

```kotlin
// Before: pass Logger everywhere
class LoginHandler(private val logger: Logger) {
    fun handle(cmd: LoginCommand) {
        logger.info("Login attempt: ${cmd.username}")
    }
}

// After: context parameters (Kotlin 2.4)
context(logger: Logger)
class LoginHandler {
    fun handle(cmd: LoginCommand) {
        logger.info("Login attempt: ${cmd.username}")
    }
}
```

### Sealed Interface for Domain Status (User feedback U-10)

```kotlin
// Domain model — type-safe status
sealed interface UserStatus {
    data object Active : UserStatus
    data object Locked : UserStatus  
    data object Disabled : UserStatus
    data class LockedUntil(val until: Instant) : UserStatus
}

// JPA entity — stored as String
@Entity
class UserEntity : SnowflakePersistentAuditableEntity() {
    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"  // Mapped from/to UserStatus
}
```

---

## Finding 7: RFC 7807 ProblemDetail — Base Module Design

**Source**: base-core source code, Spring Framework 7 docs

### Current State
- ✅ `BaseControllerAdvice` exists in base-core → handles common exceptions
- ✅ `DefaultControllerAdvice` in `base-web-starter` → auto-registered if no custom exists
- ❌ auth-service has its own `GlobalExceptionHandler` with `AuthException` hierarchy
- ❌ auth-service's `AuthException` extends `RuntimeException` instead of base-core `BusinessException`

### Recommended Integration

```
base-core:
  BusinessException ← base exception
  NotFoundException ← 404
  BaseControllerAdvice ← handles above + validation + generic

auth-service:
  AuthException extends BusinessException ← domain-specific
  GlobalExceptionHandler extends BaseControllerAdvice ← inherits base handling
    + adds AuthException, MfaException, SsoException handlers
```

---

## Products / Tools Evaluated (Updated)

| Tool | Category | Version | Status in base-core | Fit |
|------|----------|---------|:-------------------:|:---:|
| **spring-boot-starter-grpc** | Inter-service comm | 4.1.0 | ❌ Not included | 🔴 Add |
| **@HttpExchange** | HTTP client | 4.1.0 | ✅ Available | ✅ Use |
| **MapStruct** | Object mapping | 1.6.3 | ✅ In version catalog | ✅ Use |
| **ArchUnit** | Architecture test | 1.4.2 | ✅ In version catalog | ✅ Use |
| **Resilience4j** | Circuit breaker | 2.4.0 | ✅ Starter exists | ✅ Use |
| **Spring Modulith** | Module boundaries | 2.1.0 | ✅ In version catalog | ✅ Use |
| **CommandBus/QueryBus** | CQRS | — | ✅ eventsourcing-utils | ✅ Use |
| **EventBus** | Event publishing | — | ✅ eventsourcing-utils | ✅ Use |
| **Caffeine** | L1 cache | — | ❌ Not included | 🟡 Add |
| **Protobuf/gRPC codegen** | Proto compilation | — | ❌ Not included | 🔴 Add |

---

## Source Verification

| # | Source | Verified | Method |
|---|--------|:--------:|--------|
| 1 | Spring Boot 4.1.0 in platform BOM | ✅ | `platform/build.gradle.kts:L20` |
| 2 | Kotlin 2.4.10 in version catalog | ✅ | `libs.versions.toml:L3` |
| 3 | JDK 25 in version catalog | ✅ | `libs.versions.toml:L2` |
| 4 | CQRS interfaces in eventsourcing-utils | ✅ | Source code files scanned |
| 5 | BaseControllerAdvice in base-core | ✅ | `src/main/kotlin/.../BaseControllerAdvice.kt` |
| 6 | No @Version in base-model | ✅ | `grep -r "@Version" base-core/` → 0 results |
| 7 | gRPC starter missing | ✅ | Not in platform constraints |
| 8 | MapStruct 1.6.3 in catalog | ✅ | `libs.versions.toml` |
| 9 | auth-service doesn't use base-core services | ✅ | No imports of CommandService, BaseMapper |
