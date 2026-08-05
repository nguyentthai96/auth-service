# Research Brief — Architecture Optimization: Clean / Hexagonal / Onion (v2 — Enriched)

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature Name** | Architecture Optimization — Clean / Hexagonal / Onion Architecture Standardization |
| **Input Mode** | Name + Description (user request) + User Feedback (14 comments) |
| **Target Scale** | 1 billion users, 100 million TPS (upgraded from 10M) |
| **Services in Scope** | auth-service → account-service → system-admin-service (sequential) |
| **Date** | 2026-08-05 (v2) |

### ⚡ User Requirements Update (from feedback comments)

| # | Requirement | Impact |
|---|-------------|--------|
| U-01 | **Spring Boot 4.x** (base-core platform đã dùng 4.1.0) + **Kotlin 2.4.10** + **JDK 25 LTS** | ✅ Đã có sẵn trong version catalog |
| U-02 | **gRPC** cho inter-service communication, load balancer, Kafka downstream | Cần thêm `spring-boot-starter-grpc` |
| U-03 | **Library common modules** cho reusable components | Tách shared code thành Gradle submodules |
| U-04 | **Business Handler layer** giữa Application layer và Service → Repository/Remote API | Thêm tầng orchestration giữa controller và persistence |
| U-05 | **RFC 7807 ProblemDetail base module** cho tất cả services | ✅ Đã có `BaseControllerAdvice` trong base-core |
| U-06 | **Base Entity @Version** trong core-base module | ❌ Chưa có — cần thêm vào `base-model` |
| U-07 | **SOTA HTTP client** (Spring Boot 4.x) | `@HttpExchange` + `RestClient` + `@ImportHttpServices` |
| U-08 | **RBAC tối ưu 100M TPS** — payment, stock, product, shipping, booking, CRM tất cả cần access | Multi-tier cache + JWT embedded permissions + gRPC policy distribution |
| U-09 | **Sequential deployment** — auth first, ensure quality | auth → account → system-admin |
| U-10 | **Immutability per model** — analyze case by case | Sealed class cho status, data class cho VO, mutable cho entities |
| U-11 | **Gradle submodules** cho shared reusable parts | domain-common, security-common, grpc-proto |
| U-12 | **Deploy: bare metal first → K8s later** | Có Spring Config + Spring Gateway riêng |
| U-13 | **DB: PostgreSQL on Podman** | `postgresql://pda_user:pda_secret@localhost:5432/pda_db` |
| U-14 | **Traffic: balanced, user wants read-heavy optimization** | CQRS read-heavy, L1+L2 cache strategy |

---

## 2. Keywords & Search Queries

### Primary Keywords (Updated v2)
1. Spring Boot 4.1 Kotlin 2.4 JDK 25 Clean Architecture
2. Hexagonal Architecture Ports and Adapters Business Handler
3. gRPC Spring Boot 4 inter-service communication load balancing
4. `@HttpExchange` `RestClient` `@ImportHttpServices` Spring Boot 4
5. RBAC permissions cache 100 million TPS microservices gRPC
6. CQRS Event Sourcing CommandBus QueryBus Spring Boot
7. Spring Modulith modular monolith to microservice
8. Proxyless gRPC service mesh authorization
9. JWT embedded permissions multi-tier caching strategy
10. Gradle multi-module hexagonal architecture Kotlin
11. Base entity `@Version` optimistic locking shared module
12. Virtual Threads Project Loom ZGC Spring Boot 4

### Search Queries Executed
1. `"Spring Boot 4.0 release features 2025 2026 new HTTP client RestClient HttpInterface Spring Framework 7 JDK 25"`
2. `"Spring Boot gRPC starter inter-service communication load balancing high performance 2025 2026"`
3. `"RBAC permissions cache distribution gRPC microservices 100 million TPS authorization service mesh sidecar pattern 2025"`
4. `"Spring Boot 4 RestClient HttpExchangeInterface declarative HTTP client best practice SOTA replacement RestTemplate WebClient 2026"`
5. `"Kotlin 2.4 new features 2026 JDK 25 LTS support Spring Boot 4 compatibility"`
6. `"Clean Architecture Hexagonal Architecture Spring Boot Kotlin best practices billion users scale"`
7. `"hexagonal architecture ports adapters domain model Spring Boot Kotlin production large scale microservices"`
8. `"Spring Boot 10 million TPS high throughput architecture CQRS event sourcing Spring Modulith virtual threads Loom"`

---

## 3. Research Scope

### In Scope
- ✅ Current architecture assessment (all 3 services)
- ✅ **base-core library deep audit** (accessed via disk: `/components/base-core/`)
- ✅ Package structure analysis with **Business Handler layer**
- ✅ Dependency direction audit
- ✅ Domain layer purity check
- ✅ **gRPC inter-service communication** design
- ✅ **SOTA HTTP client** migration plan
- ✅ **RBAC distribution at 100M TPS** strategy
- ✅ **Gradle multi-module** shared library design
- ✅ Spring Boot 4.x / Kotlin 2.4 / JDK 25 compatibility check

### Out of Scope
- Frontend/mobile architecture
- Infrastructure as Code (Terraform, K8s manifests)
- CI/CD pipeline optimization
- Cost analysis

---

## 4. Current System Analysis (ENRICHED with base-core)

### 4.1 Tech Stack (Actual — from version catalog)

| Component | Version | Status |
|-----------|---------|:------:|
| **JDK** | 25 LTS | ✅ Latest |
| **Kotlin** | 2.4.10 | ✅ Latest (Jun 2026) |
| **Spring Boot** | 4.1.0 (via platform BOM) | ✅ Latest (Jun 2026) |
| **Spring Framework** | 7.x (via SB 4.1.0) | ✅ Latest |
| **Spring Cloud** | 2025.1.2 | ✅ Latest |
| **Spring Modulith** | 2.1.0 | ✅ Latest |
| **Resilience4j** | 2.4.0 (`resilience4j-spring-boot4`) | ✅ Ready |
| **MapStruct** | 1.6.3 | ✅ Ready |
| **ArchUnit** | 1.4.2 | ✅ Ready |
| **JJWT** | 0.13.0 | ✅ |
| **Kotlinx Coroutines** | 1.11.0 | ✅ |
| **OpenTelemetry** | 1.64.0 | ✅ |
| **gRPC** | ❌ Missing | 🔴 Need to add `spring-boot-starter-grpc` |

### 4.2 base-core Ecosystem Map

```
components/
├── base-core/                     # Root project
│   ├── base-model/                # Entity hierarchy (Snowflake, Audit, SoftDelete)
│   ├── base-core/                 # AuditLogAspect
│   ├── common-log/                # Logging utilities
│   ├── platform/                  # BOM — Spring Boot 4.1.0 + all starters
│   ├── version-catalog/           # Shared libs.versions.toml
│   ├── build-logic/               # Convention plugins
│   ├── starters/
│   │   ├── base-web-starter/      # ControllerAdvice, OpenAPI, Virtual Threads, CORS
│   │   ├── base-data-starter/     # JPA, Flyway, connection pooling
│   │   ├── base-security-starter/ # OAuth2, JWT Resource Server
│   │   ├── base-cqrs-starter/     # CommandBus/QueryBus auto-discovery
│   │   ├── base-messaging-starter/ # EventBus (Kafka/RabbitMQ/InMemory)
│   │   ├── base-resilience-starter/ # CircuitBreaker, Retry, RateLimiter
│   │   ├── base-observability-starter/ # Metrics, Tracing
│   │   ├── base-testing-starter/  # Test utilities
│   │   └── base-file-starter/     # File import/export
│   └── src/main/kotlin/           # Core domain code
│       └── com.ntt.basecore/
│           ├── domain/
│           │   ├── web/           # BaseControllerAdvice, BaseController, ApiResponse
│           │   ├── service/       # CommandService, QueryService, BaseCrudService
│           │   ├── mapper/        # BaseMapper, CrudMapper, DtoConvertible
│           │   ├── pagination/    # CursorPagination (encrypted)
│           │   ├── file/          # ExportStrategy, ImportStrategy
│           │   └── session/       # SessionManagement
│           ├── exception/         # BusinessException, NotFoundException, ErrorCode system
│           ├── hook/              # PluginLifecycleHook (plugin system)
│           └── utils/             # ResponseHttpUtil, CollectionValidator
├── common-utils/                  # Shared utility functions
└── eventsourcing-utils/           # CQRS + Event Sourcing
    └── lib/
        ├── cqrs/                  # Command, CommandBus, CommandHandler, Query, QueryBus, QueryHandler
        └── es/                    # AggregateRoot, EventBus, Projection, Snapshot, EventProcessor
```

### 4.3 base-model Entity Hierarchy

```mermaid
classDiagram
    class BaseEntity~ID~ {
        <<abstract>>
        +id: ID?
    }
    
    class AuditableEntity~ID~ {
        <<abstract>>
        +createdAt: Instant?
        +updatedAt: Instant?
        +createdBy: String?
        +updatedBy: String?
    }
    
    class PersistentAuditableEntity~ID~ {
        <<abstract>>
        +active: Boolean = true
    }
    
    class SnowflakeBaseEntity {
        <<abstract>>
        @SnowflakeId
        +id: Long?
    }
    
    class SnowflakeAuditableEntity {
        <<abstract>>
        @SnowflakeId
        +id: Long?
    }
    
    class SnowflakePersistentAuditableEntity {
        <<abstract>>
        @SnowflakeId
        +id: Long?
    }
    
    BaseEntity <|-- AuditableEntity
    AuditableEntity <|-- PersistentAuditableEntity
    BaseEntity <|-- SnowflakeBaseEntity
    AuditableEntity <|-- SnowflakeAuditableEntity
    PersistentAuditableEntity <|-- SnowflakePersistentAuditableEntity
    
    note for BaseEntity "⚠️ Missing @Version for optimistic locking!"
    note for SnowflakePersistentAuditableEntity "Most used by services"
```

> [!WARNING]
> **Gap phát hiện**: `@Version` cho optimistic locking CHƯA có trong base-model hierarchy. Hiện tại chỉ `UserEntity` trong auth-service tự thêm `@Version` riêng. User yêu cầu thêm vào base entity.

### 4.4 CQRS Infrastructure (Already Available!)

```
eventsourcing-utils/lib/cqrs/
├── Command<R>              # Marker interface — intent to change state
├── CommandBus              # dispatch(command) → R
├── CommandHandler<C, R>    # handle(command): R + commandType()
├── Query<R>                # Marker interface — intent to read
├── QueryBus                # dispatch(query) → R  
├── QueryHandler<Q, R>      # handle(query): R + queryType()
└── SpringCommandBus/SpringQueryBus  # Auto-discovery implementations

eventsourcing-utils/lib/es/
├── AggregateRoot           # DDD aggregate with event sourcing
├── EventBus                # Publish/subscribe domain events
├── Projection              # Read model projector
├── Snapshot                # Aggregate snapshot for performance
├── EventProcessor          # Process events from event store
└── TokenStore/TrackingToken # Event stream position tracking
```

### 4.5 base-core Services Available

| Service | Location | Purpose | Used by auth-service? |
|---------|----------|---------|----|
| `CommandService` | `domain/service/` | Write operations interface | ❌ No |
| `QueryService` | `domain/service/` | Read operations interface | ❌ No |
| `BaseCrudService` | `domain/service/` | Combined CRUD | ❌ No |
| `BaseControllerAdvice` | `domain/web/` | Exception handling | ✅ Partially (auth has own) |
| `BaseController` | `domain/web/` | Base REST controller | ❌ No |
| `BaseMapper` | `domain/mapper/` | Entity ↔ DTO mapping | ❌ No |
| `CrudMapper` | `domain/mapper/` | Full CRUD mapping | ❌ No |
| `CursorPagination` | `domain/pagination/` | Encrypted cursor paging | ❌ No |
| `ApiResponse` | `domain/web/payload/` | Standard response wrapper | ❌ No |
| `SessionManagement` | `domain/session/` | Session interface | ✅ Yes (DefaultSessionManagement) |

> [!IMPORTANT]
> **Key Finding**: base-core đã có rất nhiều infrastructure sẵn (CQRS, Event Sourcing, Mappers, Exception Handling) nhưng auth-service **KHÔNG sử dụng bất kỳ thứ nào** ngoại trừ entity base classes và SessionManagement. Đây là gap lớn nhất — cần migrate auth-service sang dùng base-core infrastructure.

### 4.6 Integration Points (Updated)

| From | To | Mechanism | Purpose | Future |
|------|----|-----------|---------|--------|
| auth-service → account-service | Kafka | Event | SSO provisioned | **gRPC** for sync queries |
| account-service → auth-service | REST | HTTP | Token validation | **gRPC** for sync queries |
| system-admin-service → Redis | Direct | Cache | Rate limiting | Keep |
| All services → PostgreSQL | JPA/JDBC | Persistence | Primary store | Add read replicas |
| auth-service → External IdPs | REST (OAuth2) | HTTP | SSO callback | **@HttpExchange** |
| **NEW**: All services → auth-service | — | — | — | **gRPC** permission check |
| **NEW**: business services → auth-service | — | — | — | **JWT embedded permissions** + L1/L2 cache |

---

## 5. Spring Boot 4.x / Kotlin 2.4 / JDK 25 New Capabilities

### 5.1 Spring Boot 4.1.0 (Current in Platform BOM)

| Feature | Status | Benefit |
|---------|:------:|---------|
| `@HttpExchange` + `@ImportHttpServices` | ✅ GA | Declarative HTTP clients — replace RestTemplate |
| `spring-boot-starter-grpc` | ✅ GA | Native gRPC auto-configuration |
| Virtual Threads | ✅ GA | `spring.threads.virtual.enabled=true` |
| API Versioning (MVC + WebFlux) | ✅ GA | Native API versioning support |
| JSpecify null-safety | ✅ GA | Compile-time null checks |
| Jakarta EE 11 | ✅ GA | Servlet 6.1, JPA 3.2, Bean Validation 3.1 |
| Built-in Resilience (`@Retryable`) | ✅ GA | Native retry without external lib |
| Enhanced Observability | ✅ GA | OpenTelemetry native integration |

### 5.2 Kotlin 2.4.10

| Feature | Status | Benefit |
|---------|:------:|---------|
| Context Parameters | ✅ Stable | `context(Logger) fun process()` — no explicit passing |
| Explicit Backing Fields | ✅ Stable | Cleaner property encapsulation |
| Java 25/26 support | ✅ Full | JVM bytecode targeting |
| UUID standard library | ✅ Stable | `kotlin.uuid.Uuid` |
| Sorted order checking | ✅ Stable | Collection utilities |

### 5.3 JDK 25 LTS

| Feature | Status | Benefit |
|---------|:------:|---------|
| Virtual Threads | ✅ Stable | Millions of concurrent tasks |
| Structured Concurrency | ✅ Preview | Better thread lifecycle |
| Scoped Values | ✅ Preview | Replace ThreadLocal |
| ZGC Generational | ✅ Stable | Low-latency GC |
| Foreign Function & Memory | ✅ Stable | Native interop |

---

## 6. gRPC Architecture Design (NEW)

### 6.1 Spring Boot 4.1 gRPC Integration

```yaml
# application.yml
grpc:
  server:
    port: 9090
  client:
    auth-service:
      address: 'dns:///auth-service.default.svc.cluster.local:9090'
      default-load-balancing-policy: round_robin
      negotiation-type: tls
```

### 6.2 Proto File Organization

```
components/
└── grpc-proto/                    # NEW shared module
    └── src/main/proto/
        ├── auth/
        │   ├── auth_service.proto       # Login, Register, MFA
        │   └── permission_service.proto # RBAC/PBAC check
        ├── account/
        │   └── account_service.proto    # Profile, Session
        └── common/
            ├── pagination.proto         # Cursor pagination messages
            └── error.proto              # Standard error messages
```

### 6.3 gRPC cho RBAC Distribution (100M TPS)

```mermaid
graph TB
    subgraph "Business Services (payment, stock, product, etc.)"
        BS1["payment-svc"]
        BS2["product-svc"]
        BS3["booking-svc"]
    end
    
    subgraph "Authorization Layer"
        subgraph "L1: Local Cache (In-Process)"
            LC1["Caffeine Cache<br/>TTL: 30s"]
        end
        subgraph "L2: Distributed Cache"
            RC["Redis Cluster<br/>TTL: 5min"]
        end
        subgraph "L3: Source of Truth"
            AUTH["auth-service gRPC<br/>Permission Service"]
        end
    end
    
    BS1 -->|"1. Check L1"| LC1
    LC1 -->|"2. Miss → Check L2"| RC
    RC -->|"3. Miss → gRPC call"| AUTH
    AUTH -->|"4. Cache in L2"| RC
    RC -->|"5. Cache in L1"| LC1
    
    subgraph "Invalidation"
        KAFKA["Kafka: permission.changed"]
    end
    
    AUTH -->|"Publish on change"| KAFKA
    KAFKA -->|"Invalidate L1+L2"| BS1
    KAFKA -->|"Invalidate L1+L2"| BS2
    KAFKA -->|"Invalidate L1+L2"| BS3
    
    style LC1 fill:#4CAF50,color:#fff
    style RC fill:#FF9800,color:#fff
    style AUTH fill:#2196F3,color:#fff
```

**Strategy**: JWT tokens embed role codes. L1 (Caffeine, 30s TTL) → L2 (Redis, 5min TTL) → L3 (auth-service gRPC). Kafka event invalidation on permission change.

---

## 7. SOTA HTTP Client — `@HttpExchange` (NEW)

### 7.1 Migration từ RestTemplate

| Before (RestTemplate) | After (@HttpExchange) |
|---|---|
| `private val restTemplate = RestTemplate()` | `@HttpExchange` interface + `@ImportHttpServices` |
| Manual URL building | Declarative annotations |
| No connection pooling | Auto-configured connection pool |
| No timeout config | `spring.http.client.service.*` properties |
| No retry | Built-in `@Retryable` support |

### 7.2 Example — SSO Provider Client

```kotlin
// Define contract
@HttpExchange
interface SsoProviderClient {
    @PostExchange("/oauth2/token")
    fun exchangeToken(@RequestBody request: TokenExchangeRequest): TokenResponse
    
    @GetExchange("/oauth2/userinfo")
    fun getUserInfo(@RequestHeader("Authorization") bearer: String): UserInfoResponse
}

// Configure in application.yml
spring:
  http:
    client:
      service:
        sso-provider:
          base-url: "https://sso.example.com"
          connect-timeout: 2s
          read-timeout: 5s

// Auto-register
@Configuration
@ImportHttpServices(group = "sso-provider", types = [SsoProviderClient::class])
class SsoClientConfig
```

---

## 8. Business Handler Layer Design (NEW)

### 8.1 Layered Architecture với Business Handler

User yêu cầu thêm tầng **Business Handler** giữa Application Layer và Service:

```
Controller (adapter/in/web)
    ↓ calls
Business Handler (application/handler)   ← NEW LAYER
    ↓ orchestrates
Service (application/service)            ← Domain service logic
    ↓ calls
Repository / Remote API (adapter/out)    ← gRPC, REST, JPA, Redis
```

### 8.2 Mapping to CQRS

Business Handler layer maps perfectly to CQRS CommandHandler/QueryHandler:

```kotlin
// Command Handler = Business Handler for write operations
class RegisterUserHandler(
    private val userService: UserDomainService,         // Domain logic
    private val userRepository: UserRepository,         // Outbound port
    private val eventPublisher: EventPublisher,         // Outbound port
    private val permissionClient: PermissionGrpcClient  // Remote API
) : CommandHandler<RegisterUserCommand, UserId> {
    
    override fun handle(command: RegisterUserCommand): UserId {
        // 1. Validate via domain service
        userService.validateRegistration(command.toDomain())
        // 2. Persist via repository port
        val user = userRepository.save(command.toDomain())
        // 3. Publish domain event
        eventPublisher.publish(UserRegisteredEvent(user.id))
        return user.id
    }
}

// Query Handler = Business Handler for read operations
class GetUserPermissionsHandler(
    private val permissionCache: PermissionCache,      // L1/L2 cache
    private val rbacRepository: RbacRepository         // Outbound port
) : QueryHandler<GetUserPermissionsQuery, PermissionSet> {
    
    override fun handle(query: GetUserPermissionsQuery): PermissionSet {
        return permissionCache.getOrLoad(query.userId, query.domainId) {
            rbacRepository.findEffectivePermissions(query.userId, query.domainId)
        }
    }
}
```

---

## 9. Updated Package Structure (with Business Handler + gRPC)

```text
com.ntt.<service-name>/
├── domain/                          # 🔵 CORE — Pure Kotlin, NO framework deps
│   ├── model/                       # Domain entities, Value Objects, Aggregates
│   ├── event/                       # Domain events (implement BaseEvent)
│   ├── exception/                   # Domain exceptions (extend BusinessException)
│   └── service/                     # Domain services (pure logic)
│
├── application/                     # 🟢 USE CASES — Orchestration
│   ├── port/
│   │   ├── in/                      # Inbound ports (UseCase/Command/Query interfaces)
│   │   └── out/                     # Outbound ports (Repository, Gateway, EventPublisher)
│   ├── handler/                     # 🆕 BUSINESS HANDLERS (CommandHandler/QueryHandler)
│   │   ├── command/                 # Write handlers (implements CommandHandler)
│   │   └── query/                   # Read handlers (implements QueryHandler)
│   ├── service/                     # Application services (thin orchestrators)
│   └── dto/                         # Commands, Queries, application DTOs
│
├── adapter/                         # 🟠 INFRASTRUCTURE — Framework-dependent
│   ├── in/                          # Driving adapters
│   │   ├── web/                     # REST controllers (@RestController)
│   │   │   └── dto/                 # Request/Response DTOs
│   │   ├── grpc/                    # 🆕 gRPC service implementations
│   │   └── kafka/                   # Kafka consumers
│   └── out/                         # Driven adapters
│       ├── persistence/             # JPA repositories
│       │   ├── entity/              # JPA entities (extend base-model)
│       │   ├── repository/          # Spring Data repos
│       │   └── mapper/              # Entity ↔ Domain (MapStruct)
│       ├── cache/                   # Redis adapter + Caffeine L1
│       ├── grpc/                    # 🆕 gRPC client stubs
│       ├── http/                    # 🆕 @HttpExchange clients
│       ├── kafka/                   # Kafka producers
│       └── external/                # External API clients
│
└── config/                          # Spring configuration
```

---

## 10. Gradle Multi-Module Design (NEW)

### 10.1 Shared Library Modules (user requirement U-03)

```
services/
├── shared/                          # 🆕 Shared library modules
│   ├── grpc-proto/                  # Proto definitions (all services consume)
│   ├── domain-common/               # Shared domain models (UserId, DomainCode, etc.)
│   ├── security-common/             # JWT validation, permission cache client
│   └── exception-common/            # Shared exception hierarchy
│
├── auth-service/
│   ├── domain/                      # auth-specific domain
│   ├── application/
│   ├── adapter/
│   └── build.gradle.kts             # depends on shared/*
│
├── account-service/
└── system-admin-service/
```

### 10.2 Dependency Graph

```mermaid
graph TB
    subgraph "base-core (components)"
        BM["base-model"]
        BC["base-core"]
        ES["eventsourcing-utils"]
        BW["base-web-starter"]
        BD["base-data-starter"]
        BS["base-security-starter"]
        BCQRS["base-cqrs-starter"]
        BMSG["base-messaging-starter"]
        BR["base-resilience-starter"]
    end
    
    subgraph "shared (services)"
        GP["grpc-proto"]
        DC["domain-common"]
        SC["security-common"]
    end
    
    subgraph "services"
        AUTH["auth-service"]
        ACC["account-service"]
        SYS["system-admin-service"]
    end
    
    DC --> BM
    SC --> BS
    SC --> GP
    
    AUTH --> DC
    AUTH --> SC
    AUTH --> BW
    AUTH --> BD
    AUTH --> BCQRS
    AUTH --> BMSG
    AUTH --> BR
    
    ACC --> DC
    ACC --> SC
    ACC --> GP
    
    SYS --> DC
    SYS --> SC
    
    style BM fill:#2196F3,color:#fff
    style BCQRS fill:#4CAF50,color:#fff
    style GP fill:#FF9800,color:#fff
    style DC fill:#FF9800,color:#fff
    style SC fill:#FF9800,color:#fff
```
