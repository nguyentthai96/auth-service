# Business Analysis — Architecture Optimization

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature** | Clean/Hexagonal/Onion Architecture Standardization |
| **Type** | Non-functional (Architecture Refactoring) |
| **Priority** | P0 — Foundation for all future development |
| **Stakeholders** | Development Team, DevOps, Technical Lead |
| **Target Scale** | 1B users, 100M TPS |

### Semantic Description

Hiện tại auth-service (và các services khác) có cấu trúc code **không tuân thủ** Clean/Hexagonal Architecture tiêu chuẩn:
- Domain logic bị dính chặt với JPA framework
- Không có port interfaces → không thể thay đổi infrastructure
- God-class services → khó maintain và test
- base-core đã cung cấp sẵn 90% infrastructure nhưng chỉ dùng 15%

Refactoring sẽ cho phép:
- **Testability**: Domain logic test được mà không cần Spring context
- **Swappability**: Thay đổi DB, cache, messaging mà không ảnh hưởng business logic
- **Scalability**: CQRS cho phép optimize read/write paths riêng biệt
- **Maintainability**: Mỗi handler có 1 responsibility rõ ràng

---

## 2. Use Cases

### UC-01: Refactor Domain Layer Extraction

**Mô tả**: Tách domain models ra khỏi JPA entities, tạo pure Kotlin domain models.

**Tại sao cần**: Domain model hiện tại bị lock vào JPA annotations. Không thể test business logic mà không khởi động cả database. Ở scale 100M TPS, cần swap database sang read replicas, caching layers — domain không được biết infrastructure.

**Basic Flow**:
1. Phân tích JPA entity → xác định business logic vs persistence concerns
2. Tạo pure Kotlin domain model (data class / sealed interface)
3. Tạo MapStruct mapper: JPA Entity ↔ Domain Model
4. Refactor Service → sử dụng Domain Model thay vì JPA Entity
5. Tạo Outbound Port interface cho Repository
6. JPA Repository implements Outbound Port
7. Verify: domain package không import `org.springframework.*` hay `jakarta.persistence.*`

**Exception Flow**:
- E1: Entity có custom JPA callback (`@PrePersist`, `@PostLoad`) → tách callback logic vào domain service
- E2: Entity có lazy loading relationship → preload trong adapter, pass plain data vào domain

**Business Rules**:
- BR-01: Domain models KHÔNG import bất kỳ framework class nào
- BR-02: Domain models có thể là `data class` (immutable) hoặc regular class (mutable) — case by case
- BR-03: Value Objects PHẢI là `@JvmInline value class` hoặc `data class`
- BR-04: Status fields PHẢI dùng `sealed interface` thay vì magic strings

---

### UC-02: Split God-Class Services → CQRS Handlers

**Mô tả**: Tách `AuthService` (419 lines, 12 dependencies) thành nhiều CQRS CommandHandlers và QueryHandlers.

**Tại sao cần**: God-class vi phạm SRP. Mỗi thay đổi (vd: sửa login logic) có risk ảnh hưởng register, token refresh, SSO. 12 dependencies nghĩa là 12 điểm coupling. Ở scale 100M TPS, cần optimize từng handler riêng biệt (vd: cache permission queries, batch DB queries).

**Basic Flow**:
1. Liệt kê tất cả public methods của AuthService
2. Classify: Command (write) vs Query (read)
3. Tạo Command/Query data class cho mỗi operation
4. Tạo CommandHandler/QueryHandler implement CQRS interfaces từ `eventsourcing-utils`
5. Wire Controller → CommandBus/QueryBus → Handler
6. Remove AuthService god-class
7. Verify: mỗi handler ≤ 50 lines, ≤ 4 dependencies

**Exception Flow**:
- E1: Cross-cutting concerns (logging, audit, metrics) → AOP aspect hoặc CommandBus middleware
- E2: Handler cần access nhiều repositories → inject outbound ports, không inject repositories trực tiếp

**Business Rules**:
- BR-05: Mỗi Command có đúng 1 CommandHandler
- BR-06: CommandHandler chỉ inject Outbound Ports (interfaces), KHÔNG inject concrete classes
- BR-07: Controller chỉ tạo Command/Query và dispatch qua Bus

---

### UC-03: gRPC Inter-Service Communication

**Mô tả**: Thêm gRPC server cho auth-service để business services (payment, stock, booking, etc.) có thể check permissions với low latency.

**Tại sao cần**: REST API có overhead lớn (JSON serialization, HTTP/1.1) không phù hợp cho 100M TPS permission checks. gRPC + Protobuf giảm 3-5x latency, support streaming, multiplexing. Business services cần check quyền trước mỗi operation → đây là critical path.

**Basic Flow**:
1. Tạo `grpc-proto` shared Gradle module với proto definitions
2. Thêm `spring-boot-starter-grpc` vào platform BOM
3. Define `PermissionService.proto` (CheckPermission, GetEffectivePermissions, ValidateToken)
4. Implement gRPC service adapter trong auth-service (`adapter/in/grpc/`)
5. gRPC adapter dispatch qua QueryBus → QueryHandler
6. Business services consume proto + generate client stubs
7. Verify: gRPC call latency < 10ms (p99)

**Exception Flow**:
- E1: gRPC server down → business service fallback to JWT self-validation (roles embedded in token)
- E2: Proto schema evolution → backward compatible (never reuse field numbers)

**Business Rules**:
- BR-08: gRPC server port riêng (9090), không chung REST port (8080)
- BR-09: Proto files đặt ở shared module, không duplicate
- BR-10: gRPC clients sử dụng client-side load balancing (`round_robin`)

---

### UC-04: SOTA HTTP Client Migration

**Mô tả**: Migrate `SsoAdapter` từ inline `RestTemplate()` sang `@HttpExchange` declarative client.

**Tại sao cần**: RestTemplate deprecated trong Spring Boot 4. Inline creation không có connection pooling, timeout, retry. `@HttpExchange` là SOTA — type-safe, auto-configured, centralized config.

**Basic Flow**:
1. Define `SsoProviderClient` interface với `@HttpExchange` annotations
2. Register via `@ImportHttpServices(group = "sso-provider")`
3. Configure timeout/retry via `spring.http.client.service.sso-provider.*`
4. Create `HttpSsoGateway` implements `SsoGateway` outbound port
5. Inject `SsoProviderClient` vào `HttpSsoGateway`
6. Remove `SsoAdapter` with inline `RestTemplate`

**Exception Flow**:
- E1: SSO provider timeout → circuit breaker (Resilience4j) → return error
- E2: SSO provider returns non-standard error → custom error decoder

**Business Rules**:
- BR-11: HTTP clients PHẢI có timeout config (connect: 2s, read: 5s)
- BR-12: HTTP clients PHẢI qua outbound port, controller KHÔNG gọi trực tiếp

---

### UC-05: Multi-Tier Caching for RBAC

**Mô tả**: Implement L1 (Caffeine) + L2 (Redis) cache cho permission checks, với Kafka-driven invalidation.

**Tại sao cần**: Ở 100M TPS, mỗi request cần check permission. Network call to auth-service cho mỗi request = impossible. Multi-tier cache giảm 95% network calls. L1 (in-process) < 1μs, L2 (Redis) < 2ms, L3 (gRPC) < 10ms.

**Basic Flow**:
1. Check Caffeine L1 cache (key: `userId:domainId`)
2. If miss → check Redis L2 cache
3. If miss → gRPC call to auth-service
4. Store result in L2 (TTL 5min) and L1 (TTL 30s)
5. Return PermissionSet

**Exception Flow**:
- E1: Permission changed → auth-service publishes `iam.permission.changed` to Kafka → all services consume → invalidate L1 + L2
- E2: Redis down → fallback to L1 only + gRPC direct calls (degraded mode)
- E3: Cache stampede → distributed lock trên Redis khi rebuild cache

**Business Rules**:
- BR-13: L1 TTL ≤ 30 seconds (eventual consistency window)
- BR-14: L2 TTL ≤ 5 minutes
- BR-15: Cache invalidation PHẢI qua Kafka (không polling)

---

## 3. Traceability Matrix

| Use Case | Findings Addressed | Phase |
|----------|-------------------|:-----:|
| UC-01 | F-01 (No domain), F-02 (App imports persistence), F-06 (Magic strings) | Phase 1 |
| UC-02 | F-03 (No ports), F-04 (Co-located DTOs), F-05 (God class) | Phase 2 |
| UC-03 | U-02 (gRPC requirement), U-08 (100M TPS RBAC) | Phase 3 |
| UC-04 | F-08 (Inline RestTemplate), U-07 (SOTA HTTP) | Phase 3 |
| UC-05 | F-09 (N+1 queries), U-08 (100M TPS RBAC), U-14 (Read-heavy) | Phase 4 |

## 4. Non-Functional Requirements

| NFR | Target | Current | Gap |
|-----|--------|---------|-----|
| **Response time (p99)** | < 50ms (REST), < 10ms (gRPC) | Unknown | 🔴 No measurement |
| **Throughput** | 100M TPS (cached), 1M TPS (uncached) | Unknown | 🔴 No baseline |
| **Test coverage** | > 80% | ~0% | 🔴 Critical |
| **Domain purity** | 0 framework imports | 100+ | 🔴 Critical |
| **Handler size** | ≤ 50 lines, ≤ 4 deps | 419 lines, 12 deps | 🔴 Critical |
| **Cache hit rate** | > 95% (L1+L2) | 0% (no cache) | 🔴 Critical |
| **Build time** | < 60s (incremental) | Unknown | — |

## 5. Risks & Dependencies

| Risk | Impact | Probability | Mitigation |
|------|:------:|:-----------:|------------|
| @Version migration breaks existing data | HIGH | LOW | Flyway adds column with DEFAULT 0 |
| CQRS handler proliferation | LOW | MEDIUM | Group related, use sealed command hierarchies |
| gRPC proto schema drift | HIGH | MEDIUM | Backward compatible changes only, CI validation |
| Caffeine cache stale data | MEDIUM | MEDIUM | Short TTL + Kafka invalidation |
| Team learning curve (CQRS, Hexagonal) | MEDIUM | HIGH | Documentation, pair programming, examples |
