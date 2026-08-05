# Pre-OpenSpec: architecture-optimization

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (Research Documents — `openspec/research/architecture-optimization/`)
> **Classification Evidence**: `AuthService.kt` 419 lines god-class → `auth/application/` → `auth-service`; `base-cqrs-starter` exists nhưng không dùng → EXTEND (mở rộng adoption base-core)
> **Archive**: N/A
> **Quality Score**: 82/100

## 📋 Feature Summary

Chuẩn hoá kiến trúc auth-service theo Clean/Hexagonal/Onion Architecture, tối ưu cho 1B users / 100M TPS. Refactoring tập trung vào 5 mục tiêu chính: (1) Tách Domain Layer pure Kotlin, (2) Split god-class services → CQRS Business Handlers, (3) Thêm gRPC inter-service communication, (4) Migrate RestTemplate → @HttpExchange SOTA client, (5) Multi-tier caching (Caffeine L1 + Redis L2).

User feedback quan trọng:
- **@Version**: KHÔNG thêm vào `AuditableEntity` (ảnh hưởng tất cả entities). Chỉ vài bảng quan trọng cần optimistic locking → tạo class riêng để kế thừa.
- **Proto files**: Đặt ở `services/shared/grpc-proto/`, KHÔNG ở `components/base-core/`.
- **AuthException → BusinessException**: Đồng ý, tận dụng base-core code đã có.

| Metric | Giá trị |
|--------|---------|
| Số FR | 25 (URD: 20, Enriched: 5) |
| Issues | 3 (🔴: 1, 🟡: 2) |
| Open Questions | 0 |
| **Quality Score** | **82/100** |

---

## 1. Actors

- **Development Team**: Thực hiện refactoring, viết tests
- **Auth Service**: Service chính cần refactor (bounded contexts: auth, rbac, pbac)
- **Business Services** (downstream): Consume gRPC PermissionService, use auth cache
- **base-core Platform**: Cung cấp infrastructure (CQRS, starters, BOM)
- **CI/CD Pipeline**: Build verification, ArchUnit boundary tests

## 2. Functional Requirements

### FR-001: Tạo Domain Models Pure Kotlin [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải có domain models (`User`, `Domain`, `Group`, `Role`, `Permission`, `Policy`) là pure Kotlin data classes/sealed interfaces, KHÔNG import framework classes.
- **Validation**: `grep -r "import org.springframework" domain/` trả về 0 kết quả. `grep -r "import jakarta.persistence" domain/` trả về 0 kết quả.

### FR-002: Tạo Value Objects Type-Safe [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải dùng Value Objects (`UserId`, `DomainCode`, `Email`, `PasswordHash`) thay vì primitive types cho domain identifiers.
- **Validation**: Mỗi VO là `@JvmInline value class` hoặc `data class`. Compile-time type safety verified.

### FR-003: Sealed Interface Thay Magic Strings [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải dùng `sealed interface UserStatus` thay vì `user.status = "LOCKED"` (magic strings).
- **Validation**: `grep -r '"LOCKED"\|"ACTIVE"\|"SUSPENDED"' domain/` trả về 0 kết quả.

### FR-004: Tạo Outbound Port Interfaces [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải có outbound port interfaces (`UserRepository`, `DomainRepository`, `TokenStore`, `EventPublisher`, `SsoGateway`, `PermissionCache`) trong `application/port/out/`.
- **Validation**: Mỗi port interface là pure Kotlin interface, KHÔNG import framework types. Repository returns domain models, KHÔNG JPA entities.

### FR-005: Tạo MapStruct Entity-Domain Mappers [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải có MapStruct mappers để convert JPA Entity ↔ Domain Model.
- **Validation**: `UserEntityMapper`, `PermissionMapper`, `RoleMapper` compile thành công. Mapping tests pass.

### FR-006: Adopt base-cqrs-starter [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải thêm `base-cqrs-starter` dependency và sử dụng CommandBus/QueryBus từ `eventsourcing-utils`.
- **Validation**: `CommandBus` và `QueryBus` beans auto-discovered. `./gradlew dependencies | grep base-cqrs-starter` trả về kết quả.

### FR-007: Split AuthService → CommandHandlers [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải tách AuthService (419 lines, 12 deps) thành 7+ CommandHandlers: `RegisterUserHandler`, `LoginHandler`, `RefreshTokenHandler`, `LogoutHandler`, `SwitchDomainHandler`, `MfaVerifyHandler`, `SsoLoginHandler`.
- **Validation**: Mỗi handler ≤ 50 lines, ≤ 4 dependencies. AuthService god-class KHÔNG còn tồn tại.

### FR-008: Split RbacEngine → QueryHandlers [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải tách RbacEngine (216 lines) thành QueryHandlers: `GetEffectivePermissionsHandler`, `CheckPermissionHandler`, `GetUserRolesHandler`.
- **Validation**: Mỗi handler ≤ 50 lines. N+1 query pattern KHÔNG còn tồn tại.

### FR-009: Wire Controllers Qua Bus [URD]
- **Actor**: Development Team
- **Action**: Controllers phải dispatch Commands/Queries qua CommandBus/QueryBus thay vì gọi Service trực tiếp.
- **Validation**: `grep -r "AuthService\|RbacEngine" adapter/in/web/` trả về 0 kết quả. Controllers chỉ inject `CommandBus`/`QueryBus`.

### FR-010: Tạo gRPC Proto Shared Module [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải tạo Gradle submodule `services/shared/grpc-proto/` chứa proto definitions cho PermissionService.
- **Validation**: `./gradlew :services:shared:grpc-proto:build` thành công. Generated Java/Kotlin classes từ proto.

### FR-011: Thêm spring-boot-starter-grpc [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải thêm `spring-boot-starter-grpc` dependency vào platform BOM.
- **Validation**: gRPC server start trên port 9090. Health check endpoint response OK.

### FR-012: Implement gRPC PermissionService [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải expose gRPC service `PermissionService` với methods: `CheckPermission`, `GetEffectivePermissions`, `ValidateToken`.
- **Validation**: `grpcurl localhost:9090 list` hiển thị `PermissionService`. Response time < 10ms (p99).

### FR-013: Migrate RestTemplate → @HttpExchange [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải migrate `SsoAdapter.restTemplate` (inline `RestTemplate()`) và `CaptchaVerifier.restTemplate` sang `@HttpExchange` declarative client.
- **Validation**: `grep -r "RestTemplate" src/main/kotlin/` trả về 0 kết quả. `@HttpExchange` interfaces compile thành công.

### FR-014: Configure HTTP Client Groups [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải configure timeout/retry cho HTTP clients qua `spring.http.client.service.<group>.*`.
- **Validation**: `application.yml` có `spring.http.client.service.sso-provider.connect-timeout`, `read-timeout` config.

### FR-015: Fix N+1 Query Pattern [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải thay N+1 loop queries trong RbacEngine bằng single batch JOIN query.
- **Validation**: `EXPLAIN ANALYZE` cho permission query chỉ có 1 query thay vì N*3. Query execution plan không có nested loop scan.

### FR-016: Implement Caffeine L1 Cache [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải có Caffeine in-process cache cho RBAC permission checks với TTL 30s.
- **Validation**: Cache hit rate metrics available via Actuator. Caffeine stats hiển thị hit/miss ratio.

### FR-017: Multi-Tier Cache Adapter [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải implement `PermissionCache` adapter với fallback: Caffeine L1 (30s) → Redis L2 (5min) → gRPC L3 (source).
- **Validation**: Cache adapter unit test pass cho tất cả fallback scenarios.

### FR-018: Kafka Cache Invalidation [URD]
- **Actor**: Auth Service
- **Action**: Khi permission thay đổi, hệ thống phải publish `iam.permission.changed` event qua Kafka. Tất cả services consume event → invalidate L1+L2 cache.
- **Validation**: Permission update → Kafka message published → downstream cache invalidated (integration test).

### FR-019: Optimistic Locking Riêng [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải tạo class riêng (ví dụ `VersionedAuditableEntity`) extends `AuditableEntity` + `@Version` cho các bảng cần optimistic locking (UserEntity, DomainEntity). KHÔNG thêm @Version vào `AuditableEntity` base class.
- **Validation**: Chỉ entities kế thừa `VersionedAuditableEntity` mới có `@Version` column. Flyway migration chỉ thêm `version` column cho các bảng cụ thể.

### FR-020: AuthException → BusinessException [URD]
- **Actor**: Development Team
- **Action**: Hệ thống phải refactor `AuthException` hierarchy để extends `BusinessException` từ base-core thay vì `RuntimeException` trực tiếp.
- **Validation**: `AuthException : BusinessException()`. `GlobalExceptionHandler` tận dụng `BaseControllerAdvice` pattern từ base-core.

### FR-021: ArchUnit Boundary Tests [ENRICHED]
- **Actor**: Development Team
- **Action**: Hệ thống phải có ArchUnit tests enforce: domain không depend Spring/JPA, application không depend adapter, handlers chỉ access ports + domain.
- **Validation**: `./gradlew test --tests "*ArchitectureTest*"` pass.

### FR-022: Virtual Threads Enable [ENRICHED]
- **Actor**: Development Team
- **Action**: Hệ thống phải enable Virtual Threads cho Spring Boot.
- **Validation**: `spring.threads.virtual.enabled=true` trong config. PolicyEvaluator replace `newCachedThreadPool()` → Virtual Thread executor.

### FR-023: Fix PolicyEvaluator Thread Pool [ENRICHED]
- **Actor**: Development Team
- **Action**: Hệ thống phải fix unbounded `Executors.newCachedThreadPool()` trong PolicyEvaluator → Virtual Thread executor hoặc bounded thread pool.
- **Validation**: No unbounded thread pool. Thread naming convention enforced.

### FR-024: Shared Domain Common Module [ENRICHED]
- **Actor**: Development Team
- **Action**: Hệ thống phải tạo `services/shared/domain-common/` module chứa shared Value Objects (UserId, DomainCode) — pure Kotlin, không dependency.
- **Validation**: Module compile thành công. Không có Spring/JPA dependency.

### FR-025: Shared Security Common Module [ENRICHED]
- **Actor**: Development Team
- **Action**: Hệ thống phải tạo `services/shared/security-common/` module chứa JWT validation logic và permission cache client cho business services consume.
- **Validation**: Module compile thành công. Business services có thể import và sử dụng.

## 3. Non-functional Requirements

| NFR | Target |
|-----|--------|
| **Domain Purity** | 0 framework imports trong `domain/` package |
| **Handler Size** | ≤ 50 lines, ≤ 4 dependencies per handler |
| **Test Coverage** | > 80% cho domain + handlers |
| **gRPC Latency (p99)** | < 10ms |
| **REST Latency (p99)** | < 50ms |
| **Cache Hit Rate** | > 95% (L1 + L2) |
| **Build Time** | < 60s incremental |
| **base-core Adoption** | > 80% (từ 15% hiện tại) |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 → FR-020 đều address unique concerns.

## 5. Enriched Domain Requirements

Bổ sung 5 FRs tagged `[ENRICHED]` (FR-021 → FR-025):

| FR | Justification |
|----|---------------|
| FR-021 | ArchUnit boundary tests — ensure architecture compliance CI-enforced |
| FR-022 | Virtual Threads — Spring Boot 4 SOTA performance, đơn giản enable |
| FR-023 | PolicyEvaluator fix — memory leak risk từ unbounded thread pool |
| FR-024 | Shared domain-common — reuse Value Objects across services |
| FR-025 | Shared security-common — JWT validation + cache client cho business services |

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| **PostgreSQL** | Primary database (pda_user:pda_secret@localhost:5432/pda_db) | Podman container |
| **Redis** | OTP storage, token blacklist, L2 cache (planned) | StringRedisTemplate |
| **Kafka** | SSO event publishing (existing), cache invalidation (planned) | KafkaTemplate |
| **SSO Providers** | Google, GitHub, Microsoft OAuth2 | RestTemplate → @HttpExchange |
| **reCAPTCHA** | Bot protection | RestTemplate → @HttpExchange |
| **gRPC** (planned) | Inter-service permission check | spring-boot-starter-grpc |

## 6. Assumptions

- base-core platform BOM sẽ được update để thêm `spring-boot-starter-grpc` dependency
- Flyway migration cho `version` column chỉ áp dụng cho UserEntity, DomainEntity (theo user feedback)
- PostgreSQL trên Podman đã running và accessible
- Kotlin 2.4.10 Context Parameters stable — sử dụng nếu phù hợp
- Business services (downstream) chưa cần migrate trong phase này — chỉ auth-service

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-017: Multi-tier cache fallback logic chưa detail edge cases (timeout, partial failure) |
| Đầy đủ (Completeness) | 21/25 | FR-012: gRPC proto schema chưa define cụ thể message fields; FR-018: Kafka topic schema chưa specify |
| Nhất quán (Consistency) | 23/25 | FR-019: @Version approach khác với research docs ban đầu (đã update theo user feedback) |
| Kiểm thử được (Testability) | 16/25 | FR-012, FR-017, FR-018: Integration test yêu cầu Testcontainers setup chưa describe chi tiết |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -3 | FR-017 | "Multi-tier cache adapter" — chưa specify behavior khi partial failure (L1 ok, L2 down) | Define explicit fallback matrix |
| 2 | Completeness | -2 | FR-012 | "PermissionService.proto" — chưa list message fields cụ thể | Define proto schema trong technical spec |
| 3 | Completeness | -2 | FR-018 | "iam.permission.changed" — chưa define event payload schema | Define Avro/Protobuf event schema |
| 4 | Consistency | -2 | FR-019 | Research docs gốc nói "@Version vào AuditableEntity" → user feedback sửa thành class riêng | ✅ Đã update trong pre_openspec |
| 5 | Testability | -9 | FR-012, FR-017, FR-018 | Integration tests cần Testcontainers cho PostgreSQL, Redis, Kafka, gRPC — setup chưa describe | Add Testcontainers config trong technical spec |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | **AuthException → BusinessException breaking change**: Error response format có thể thay đổi nếu `BusinessException` có different fields. Cần verify `BaseControllerAdvice` handling. | FR-020 | Compare `AuthException` fields vs `BusinessException` fields trước khi migrate |
| 2 | Risk | 🟡 | **gRPC proto backward compatibility**: Nếu proto schema thay đổi sau release, downstream services cần rebuild. | FR-010, FR-012 | Versioned proto packages, CI proto lint |
| 3 | Missing | 🟡 | **Rollback strategy**: Nếu CQRS migration gây regression, không có documented rollback plan. | FR-007, FR-008 | Feature flags cho CommandBus dispatch vs direct service call |

> Nếu không có issues → ghi "Không phát hiện vấn đề."

## 9. Open Questions

Không có câu hỏi mở — tất cả đã được user confirm qua feedback.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
IAM (Identity & Access Management) — Authentication, Authorization, RBAC, PBAC, MFA

### 10.2 Flow Type
Command (architecture refactoring — no user-facing transaction flow)

### 10.3 Candidate Services
- **auth-service**: Primary target — toàn bộ refactoring tập trung ở đây. Evidence: `AuthService.kt` (419 lines), `RbacEngine.kt` (216 lines), `PolicyEvaluator.kt` — tất cả cần refactor.
- **base-core** (components): Cần update: thêm `VersionedAuditableEntity`, thêm `spring-boot-starter-grpc` vào platform BOM. Evidence: `platform/build.gradle.kts`, `base-model/src/`.
- **shared** (new modules): Tạo mới: `grpc-proto`, `domain-common`, `security-common`. Evidence: User requirement — tách submodules cho phần dùng chung.

### Detection Evidence
- Keyword: `AuthService` god-class → Module: `auth/application/` → File: `AuthService.kt`
- Keyword: `RestTemplate` inline → Module: `auth/application/` → File: `SsoAdapter.kt`, `CaptchaVerifier.kt`
- Keyword: `newCachedThreadPool` → Module: `pbac/application/` → File: `PolicyEvaluator.kt`
- Keyword: `findById` N+1 → Module: `rbac/application/` → File: `RbacEngine.kt`
- Keyword: `CommandBus` → Module: `eventsourcing-utils/lib/cqrs/` → File: `CommandBus.kt` (base-core — EXISTS but NOT USED)
- Keyword: `SnowflakePersistentAuditableEntity` → Module: `base-model` → File: `SnowflakeVariants.kt` (base-core — USED)

### 10.4 External Integrations
- PostgreSQL (JPA/Hibernate — `base-data-starter`)
- Redis (StringRedisTemplate — OTP, token blacklist)
- Kafka (KafkaTemplate — SSO events)
- SSO Providers (Google, GitHub, Microsoft — REST/OAuth2)
- reCAPTCHA (REST API verification)

### 10.5 Required Modules
- `base-cqrs-starter` (từ base-core, chưa dùng)
- `base-messaging-starter` (từ base-core, chưa dùng)
- `base-resilience-starter` (từ base-core, chưa dùng)
- `spring-boot-starter-grpc` (cần thêm vào platform BOM)
- MapStruct 1.6.3 (trong version catalog, chưa dùng)
- ArchUnit 1.4.2 (trong version catalog, chưa dùng)
- Caffeine (cần thêm dependency)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Create domain models (pure Kotlin) | auth-service `domain/model/` |
| 2 | Developer | Create outbound port interfaces | auth-service `application/port/out/` |
| 3 | Developer | Create MapStruct mappers | auth-service `adapter/out/persistence/mapper/` |
| 4 | Developer | Add base-cqrs-starter dependency | auth-service `build.gradle.kts` |
| 5 | Developer | Create Command/Query data classes | auth-service `application/dto/` |
| 6 | Developer | Create CommandHandler/QueryHandler | auth-service `application/handler/` |
| 7 | Developer | Wire controllers → CommandBus/QueryBus | auth-service `adapter/in/web/` |
| 8 | Developer | Create proto definitions | `services/shared/grpc-proto/` |
| 9 | Developer | Implement gRPC PermissionService | auth-service `adapter/in/grpc/` |
| 10 | Developer | Migrate RestTemplate → @HttpExchange | auth-service `adapter/out/http/` |
| 11 | Developer | Implement multi-tier cache | auth-service `adapter/out/cache/` |
| 12 | Developer | Write ArchUnit + unit + integration tests | auth-service `src/test/` |


## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-01 Basic Flow §2 | Phase 1.2 | NEW `domain/model/*.kt` | Pending |
| FR-002 | UC-01 BR-03 | Phase 1.2 | NEW `domain/model/vo/*.kt` | Pending |
| FR-003 | UC-01 BR-04 | Phase 1.2 | NEW `domain/model/UserStatus.kt` | Pending |
| FR-004 | UC-01 Basic Flow §5 | Phase 1.3 | NEW `application/port/out/*.kt` | Pending |
| FR-005 | UC-01 Basic Flow §3 | Phase 1.4 | NEW `adapter/out/persistence/mapper/*.kt` | Pending |
| FR-006 | UC-02 Basic Flow §1 | Phase 2.1 | [MODIFY] `build.gradle.kts` | Pending |
| FR-007 | UC-02 Basic Flow §4 | Phase 2.2 | [MODIFY] `AuthService.kt` → NEW handlers | Pending |
| FR-008 | UC-02 §RbacEngine | Phase 2.3 | [MODIFY] `RbacEngine.kt` → NEW handlers | Pending |
| FR-009 | UC-02 Basic Flow §5 | Phase 2.4 | [MODIFY] `AuthController.kt`, `RbacControllers.kt` | Pending |
| FR-010 | UC-03 Basic Flow §1 | Phase 3.1 | NEW `services/shared/grpc-proto/` | Pending |
| FR-011 | UC-03 Basic Flow §2 | Phase 3.2 | [MODIFY] `platform/build.gradle.kts` | Pending |
| FR-012 | UC-03 Basic Flow §4 | Phase 3.2 | NEW `adapter/in/grpc/PermissionGrpcService.kt` | Pending |
| FR-013 | UC-04 Basic Flow §1-5 | Phase 3.3 | [MODIFY] `SsoAdapter.kt`, `CaptchaVerifier.kt` → NEW @HttpExchange | Pending |
| FR-014 | UC-04 Basic Flow §3 | Phase 3.3 | [MODIFY] `application.yml` | Pending |
| FR-015 | UC-05 NF | Phase 4.1 | [MODIFY] `RbacEngine.kt` queries | Pending |
| FR-016 | UC-05 Basic Flow §1 | Phase 4.2 | NEW `adapter/out/cache/CaffeinePermissionCache.kt` | Pending |
| FR-017 | UC-05 Basic Flow §1-5 | Phase 4.2 | NEW `adapter/out/cache/MultiTierPermissionCache.kt` | Pending |
| FR-018 | UC-05 Exception Flow §E1 | Phase 4.2 | NEW `adapter/in/kafka/PermissionChangedConsumer.kt` | Pending |
| FR-019 | User Feedback | Phase 1.1 | NEW `VersionedAuditableEntity.kt` in base-model | Pending |
| FR-020 | User Feedback | Phase 1.5 | [MODIFY] `AuthExceptions.kt`, `AuthCoreExceptions.kt` | Pending |
| FR-021 | Enriched | Phase 5 | NEW `src/test/kotlin/**/ArchitectureTest.kt` | Pending |
| FR-022 | Enriched | Phase 4.3 | [MODIFY] `application.yml` | Pending |
| FR-023 | Enriched | Phase 4.3 | [MODIFY] `PolicyEvaluator.kt` | Pending |
| FR-024 | Enriched | Phase 3.1 | NEW `services/shared/domain-common/` | Pending |
| FR-025 | Enriched | Phase 3.1 | NEW `services/shared/security-common/` | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Đây là **architecture refactoring** — KHÔNG phải feature mới. Phạm vi rộng nhưng có thể triển khai incremental per phase.
- Rủi ro lớn nhất: **AuthException → BusinessException** breaking change (FR-020). Cần verify response format compatibility trước.
- base-core adoption score (15%) cho thấy team chưa leverage infrastructure đã build sẵn. CQRS bus, Event Sourcing, MapStruct, ArchUnit đều có trong version catalog nhưng không dùng.
- God-class `AuthService` (419 lines, 12 deps) là bottleneck chính — mỗi thay đổi có blast radius lớn.

### Related Features / Precedents
- `openspec/changes/auth-core-features/` — có thể reference existing auth patterns
- `openspec/changes/erp-iam-system/` — IAM system reference
- `openspec/research/architecture-optimization/` — 8 research docs đầy đủ

### Integration Notes
- **PostgreSQL**: Flyway migration cần cho `version` column (chỉ UserEntity, DomainEntity tables)
- **Redis**: Hiện dùng cho OTP + token blacklist. Sẽ thêm L2 cache layer — cần evaluate Redis connection pool sizing.
- **Kafka**: Hiện dùng cho SSO events. Sẽ thêm `iam.permission.changed` topic — cần define event schema.
- **gRPC**: New addition — port 9090. Cần configure riêng trong Docker/Podman compose.

### Suggested Approach
1. **Phase 1 first** — domain layer extraction là foundation cho tất cả phases sau
2. Dùng **MapStruct** (đã có trong catalog) cho Entity ↔ Domain mapping
3. Dùng **ArchUnit** (đã có trong catalog) enforce boundaries từ đầu
4. CQRS handlers kế thừa `CommandHandler<C, R>` / `QueryHandler<Q, R>` từ `eventsourcing-utils` — KHÔNG tạo interface mới
5. gRPC service đặt trong `adapter/in/grpc/` — dispatch qua QueryBus → reuse existing handlers
6. @HttpExchange dùng `@ImportHttpServices` (Spring Boot 4.1 feature) — centralized config
7. **Feature flag** cho CommandBus dispatch — cho phép rollback nếu cần

### Context from Confluence Images
N/A — source là research documents, không có Confluence images.
