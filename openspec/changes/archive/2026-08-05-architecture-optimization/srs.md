# SRS: Architecture Optimization — auth-service

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu đặc tả yêu cầu phần mềm cho architecture refactoring auth-service, chuẩn hoá theo Clean/Hexagonal Architecture và tận dụng base-core platform.

### 1.2 Phạm vi
- **Primary**: auth-service (auth, rbac, pbac, shared bounded contexts)
- **Secondary**: base-core/base-model (minor update)
- **New**: services/shared/ (grpc-proto, domain-common, security-common)

### 1.3 Tham chiếu
- [pre_openspec.md](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/openspec/changes/architecture-optimization/pre_openspec.md)
- [brainstorm_notes.md](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/openspec/changes/architecture-optimization/brainstorm_notes.md)
- [impact_analysis.md](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/openspec/changes/architecture-optimization/impact_analysis.md)

---

## 2. Functional Requirements

### Phase 1 — Domain Layer Foundation

#### FR-001: Domain Models Pure Kotlin
- **Mô tả**: Tạo domain models (`User`, `Domain`, `Group`, `Role`, `Permission`, `Policy`) là pure Kotlin — không import Spring/JPA.
- **Input**: Existing JPA entity fields.
- **Output**: Domain classes trong `auth/domain/model/`, `rbac/domain/model/`.
- **Validation**: `grep -r "import org.springframework\|import jakarta.persistence" domain/` → 0 results.

#### FR-002: Value Objects Type-Safe
- **Mô tả**: Value Objects (`UserId`, `DomainCode`, `Email`, `PasswordHash`) thay primitive types.
- **Input**: `Long userId`, `String email`, etc.
- **Output**: `@JvmInline value class UserId(val value: Long)`, `@JvmInline value class Email(val value: String)`.
- **Validation**: Compile-time type safety — truyền `UserId` thay vì `Long`.

#### FR-003: Sealed Interface UserStatus
- **Mô tả**: Thay `user.status = "LOCKED"` (magic strings) bằng `sealed interface UserStatus`.
- **Input**: String constants "ACTIVE", "LOCKED", "SUSPENDED".
- **Output**: `sealed interface UserStatus { data object Active : UserStatus; data class LockedUntil(val until: Instant) : UserStatus }`.
- **Validation**: `grep -r '"LOCKED"\|"ACTIVE"' domain/` → 0 results.

#### FR-004: Outbound Port Interfaces
- **Mô tả**: Port interfaces (`UserRepository`, `DomainRepository`, `TokenStore`, `EventPublisher`, `SsoGateway`, `PermissionCache`).
- **Input**: Existing JPA repository methods.
- **Output**: Pure Kotlin interfaces trong `application/port/out/`, return domain models.
- **Validation**: No framework imports trong port interfaces.

#### FR-005: MapStruct Entity-Domain Mappers
- **Mô tả**: MapStruct mappers cho JPA Entity ↔ Domain Model conversion.
- **Input**: JPA entities, Domain models.
- **Output**: `@Mapper` interfaces: `UserEntityMapper`, `PermissionMapper`, `RoleMapper`.
- **Validation**: Mapper tests pass. Auto-generated implementation compiles.

#### FR-019: VersionedAuditableEntity (Optimistic Locking Opt-in)
- **Mô tả**: Tạo `VersionedAuditableEntity` extends `SnowflakePersistentAuditableEntity` + `@Version`. Chỉ entities cần OL kế thừa class này.
- **Input**: User feedback: "chỉ một vài bảng quan trọng mới cần version optimistic locking".
- **Output**: Class mới trong base-model. Flyway migration `ALTER TABLE ... ADD COLUMN version BIGINT DEFAULT 0` cho `users`, `domains` tables.
- **Validation**: Chỉ `UserEntity`, `DomainEntity` kế thừa. Các entity khác unchanged.

#### FR-020: AuthException → BusinessException (Bridge Pattern)
- **Mô tả**: Refactor exception hierarchy: `AuthException extends BusinessException(ErrorCodeBase)`. Tạo `AuthErrorCode` enum. `AuthControllerAdvice extends BaseControllerAdvice` + override giữ ProblemDetail + HTTP status.
- **Input**: 
  - Current: `AuthException(errorCode: String, message: String, httpStatus: HttpStatus) : RuntimeException`
  - Target: `AuthException(authError: AuthErrorCode) : BusinessException(authError)`
- **Output**: 
  - `AuthErrorCode` enum implementing `ErrorCodeBase` + `httpStatus` property
  - `AuthException` extends `BusinessException`
  - `AuthControllerAdvice` extends `BaseControllerAdvice` + overrides `handleAuthException()`
- **Error Codes**: `AUTH_001` (INVALID_CREDENTIALS), `AUTH_002` (ACCOUNT_LOCKED), `AUTH_003` (TOKEN_EXPIRED), `AUTH_004` (PERMISSION_DENIED), `AUTH_005` (RESOURCE_NOT_FOUND), `AUTH_006` (DUPLICATE_RESOURCE), `AUTH_007` (CAPTCHA_REQUIRED), `AUTH_008` (CAPTCHA_FAILED), `AUTH_009` (POLICY_EVALUATION_FAILED), `AUTH_010` (WRITE_NOT_ALLOWED)
- **Validation**: `AuthException is BusinessException` = true. ProblemDetail response unchanged. HTTP status codes unchanged.

### Phase 2 — CQRS Business Handlers

#### FR-006: Adopt base-cqrs-starter
- **Mô tả**: Thêm dependency `base-cqrs-starter` vào `build.gradle.kts`. Auto-discover `CommandHandler`, `QueryHandler` beans.
- **Input**: `build.gradle.kts` hiện tại.
- **Output**: Dependency added. `CommandBus`, `QueryBus` beans available in application context.
- **Validation**: `./gradlew dependencies | grep base-cqrs-starter` thành công.

#### FR-007: Split AuthService → CommandHandlers
- **Mô tả**: Tách `AuthService` (307 lines, 11 deps) thành 5 CommandHandlers + 1 QueryHandler.
- **Handler Mapping** (từ brainstorm):

| Handler | Source Method | Command/Query | Est. Lines | Deps |
|---------|-------------|:-------------:|:----------:|:----:|
| `RegisterUserHandler` | `register()` | `RegisterUserCommand → UserId` | ~40 | 4 |
| `LoginHandler` | `login()` | `LoginCommand → LoginResult` | ~55 | 4 |
| `RefreshTokenHandler` | `refreshToken()` | `RefreshTokenCommand → AuthResponse` | ~25 | 3 |
| `SwitchDomainHandler` | `switchDomain()` | `SwitchDomainCommand → AuthResponse` | ~15 | 3 |
| `RevokeSessionsHandler` | `revokeAllSessions()` | `RevokeSessionsCommand → Int` | ~10 | 2 |
| `BuildAuthResponseHandler` | `buildAuthResponseForUser()` | `BuildAuthResponseQuery → AuthResponse` | ~15 | 3 |

- **Domain Services** (extracted private methods):
  - `AuthResponseBuilder` — builds AuthResponse from domain models
  - `DomainLookupService` — resolves primary domain for user
  - `TokenHasher` — SHA-256 utility
- **Validation**: AuthService.kt DELETED. Each handler ≤ 50 lines, ≤ 4 deps (LoginHandler ~55 accepted).

#### FR-008: Split RbacEngine → QueryHandlers
- **Mô tả**: Tách `RbacEngine` (90 lines, 7 deps) thành 3 QueryHandlers.

| Handler | Source Method | Query | Est. Lines | Deps |
|---------|-------------|:-----:|:----------:|:----:|
| `CheckPermissionHandler` | `hasPermission()` | `CheckPermissionQuery → Boolean` | ~15 | 2 |
| `GetUserRolesHandler` | `getUserRoles()` | `GetUserRolesQuery → List<String>` | ~10 | 2 |
| `GetPermissionsHandler` | `getEffectivePermissions()` | `GetPermissionsQuery → List<String>` | ~20 | 2 |

- **Shared Service**: `RbacResolver` giữ `resolveUserRoleIds()` + `resolveUserPermissions()`.
- **N+1 Fix (FR-015)**: `GetPermissionsHandler` dùng batch JOIN query thay vì N loop `findById()`.
- **Validation**: RbacEngine.kt DELETED. N+1 eliminated.

#### FR-009: Wire Controllers → Bus
- **Mô tả**: Controllers dispatch Commands/Queries qua CommandBus/QueryBus.
- **Input**: Controllers inject `AuthService`/`RbacEngine`.
- **Output**: Controllers inject `CommandBus`/`QueryBus` only.
- **Feature Flag**: `@ConditionalOnProperty("app.cqrs.enabled")` cho gradual rollout.
- **Validation**: `grep -r "AuthService\|RbacEngine" adapter/in/web/` → 0 results.

### Phase 3 — Communication Layer

#### FR-010: gRPC Proto Shared Module
- **Mô tả**: Tạo `services/shared/grpc-proto/` Gradle module chứa `.proto` files.
- **Output**: `PermissionService.proto` với messages: `CheckPermissionRequest/Response`, `EffectivePermissionsRequest`, `PermissionSet`, `ValidateTokenRequest/Response`.
- **Validation**: `./gradlew :services:shared:grpc-proto:build` thành công.

#### FR-011: spring-boot-starter-grpc Dependency
- **Mô tả**: Thêm `spring-boot-starter-grpc` vào auth-service dependencies.
- **Validation**: gRPC server listen trên port 9090.

#### FR-012: Implement gRPC PermissionService
- **Mô tả**: gRPC server `PermissionGrpcService` trong `adapter/in/grpc/`. Dispatch via QueryBus.
- **RPC Methods**: `CheckPermission`, `GetEffectivePermissions`, `ValidateToken`, `CheckPermissions` (streaming).
- **Validation**: `grpcurl localhost:9090 list` → shows `PermissionService`.

#### FR-013: RestTemplate → @HttpExchange
- **Mô tả**: Migrate `SsoAdapter` inline `RestTemplate()` và `CaptchaVerifier` injected `RestTemplate` sang `@HttpExchange` declarative clients.
- **Output**: 
  - `SsoProviderClient` (@HttpExchange interface)
  - `CaptchaClient` (@HttpExchange interface)
  - `HttpSsoGateway` implements `SsoGateway` port
  - `HttpCaptchaGateway` implements `CaptchaGateway` port
- **Validation**: `grep -r "RestTemplate" src/main/kotlin/` → 0 results.

#### FR-014: HTTP Client Configuration
- **Mô tả**: Configure timeout/retry via `spring.http.client.service.<group>.*`.
- **Validation**: `application.yml` has connect-timeout, read-timeout.

#### FR-024: Shared Domain Common Module
- **Mô tả**: `services/shared/domain-common/` chứa shared VOs (UserId, DomainCode). Pure Kotlin.
- **Validation**: No Spring/JPA dependencies.

#### FR-025: Shared Security Common Module
- **Mô tả**: `services/shared/security-common/` chứa JWT validation + permission cache client.
- **Validation**: Business services có thể import.

### Phase 4 — Performance

#### FR-015: Fix N+1 Query Pattern
- **Mô tả**: Thay N+1 loop trong `getEffectivePermissions()` bằng single batch JOIN.
- **SQL**: 
```sql
SELECT dr.code, a.code
FROM role_permission rp
JOIN permission p ON rp.permission_id = p.id
JOIN domain_resource dr ON p.resource_id = dr.id
JOIN action a ON p.action_id = a.id
WHERE rp.role_id IN (:roleIds) AND rp.active = true
```
- **Validation**: EXPLAIN ANALYZE → 1 query, no nested loop.

#### FR-016: Caffeine L1 Cache
- **Mô tả**: In-process cache cho permission checks, TTL 30s.
- **Validation**: Caffeine stats via Actuator.

#### FR-017: Multi-Tier Cache Adapter
- **Mô tả**: `PermissionCache` adapter: Caffeine L1 (30s) → Redis L2 (5min) → DB fallback.
- **Validation**: Unit tests cho tất cả fallback scenarios.

#### FR-018: Kafka Cache Invalidation
- **Mô tả**: Publish `iam.permission.changed` event khi permission thay đổi. Consumers invalidate L1+L2.
- **Validation**: Integration test: permission update → Kafka → cache invalidated.

#### FR-022: Virtual Threads Enable
- **Mô tả**: `spring.threads.virtual.enabled=true`.
- **Validation**: Config present. VT metrics via Actuator.

#### FR-023: Fix PolicyEvaluator Thread Pool
- **Mô tả**: Replace `Executors.newCachedThreadPool()` → Virtual Thread executor.
- **Validation**: No unbounded thread pool.

### Phase 5 — Testing

#### FR-021: ArchUnit Boundary Tests
- **Mô tả**: ArchUnit rules enforce: domain ≠ depend Spring/JPA, application ≠ depend adapter, handlers chỉ access ports + domain.
- **Validation**: `./gradlew test --tests "*ArchitectureTest*"` pass.

---

## 3. Non-Functional Requirements

| NFR | Metric | Target |
|-----|--------|:------:|
| NFR-01 | Domain purity | 0 framework imports trong `domain/` |
| NFR-02 | Handler size | ≤50 lines (LoginHandler ~55 accepted) |
| NFR-03 | Handler dependencies | ≤4 per handler |
| NFR-04 | Test coverage | >80% (domain + handlers) |
| NFR-05 | gRPC latency (p99) | <10ms |
| NFR-06 | REST latency (p99) | <50ms |
| NFR-07 | Cache hit rate | >95% (L1+L2) |
| NFR-08 | Build time (incremental) | <60s |
| NFR-09 | base-core adoption | >80% (10/13 features) |
| NFR-10 | Zero API breaking changes | 0 |

---

## 4. Error Code Registry

| Code | Exception | HTTP Status | Description |
|------|-----------|:-----------:|-------------|
| `AUTH_001` | `InvalidCredentialsException` | 401 | Invalid username or password |
| `AUTH_002` | `AccountLockedException` | 403 | Account locked due to failed attempts |
| `AUTH_003` | `TokenExpiredException` | 401 | JWT token expired |
| `AUTH_004` | `PermissionDeniedException` | 403 | Insufficient permissions |
| `AUTH_005` | `ResourceNotFoundException` | 404 | Resource not found |
| `AUTH_006` | `DuplicateResourceException` | 409 | Duplicate resource |
| `AUTH_007` | `CaptchaRequiredException` | 428 | CAPTCHA required |
| `AUTH_008` | `CaptchaFailedException` | 400 | CAPTCHA verification failed |
| `AUTH_009` | `PolicyEvaluationException` | 403 | Policy evaluation failed |
| `AUTH_010` | `WriteNotAllowedException` | 403 | Read-only access |

---

## 5. Traceability Matrix

| FR | Phase | Handler/Component | Files |
|----|:-----:|-------------------|-------|
| FR-001 | P1 | Domain Models | NEW `domain/model/*.kt` |
| FR-002 | P1 | Value Objects | NEW `domain/model/vo/*.kt` |
| FR-003 | P1 | UserStatus | NEW `domain/model/UserStatus.kt` |
| FR-004 | P1 | Outbound Ports | NEW `application/port/out/*.kt` |
| FR-005 | P1 | MapStruct Mappers | NEW `adapter/out/persistence/mapper/*.kt` |
| FR-006 | P2 | base-cqrs-starter | MODIFY `build.gradle.kts` |
| FR-007 | P2 | CommandHandlers | NEW `application/handler/*.kt` |
| FR-008 | P2 | QueryHandlers | NEW `rbac/application/handler/*.kt` |
| FR-009 | P2 | Controller Rewire | MODIFY controllers |
| FR-010 | P3 | grpc-proto | NEW `services/shared/grpc-proto/` |
| FR-011 | P3 | gRPC dependency | MODIFY `build.gradle.kts` |
| FR-012 | P3 | PermissionGrpcService | NEW `adapter/in/grpc/` |
| FR-013 | P3 | @HttpExchange | NEW `adapter/out/http/` |
| FR-014 | P3 | HTTP config | MODIFY `application.yml` |
| FR-015 | P2 | Batch JOIN query | MODIFY query in handler |
| FR-016 | P4 | Caffeine L1 | NEW `adapter/out/cache/` |
| FR-017 | P4 | Multi-tier cache | NEW `adapter/out/cache/` |
| FR-018 | P4 | Kafka invalidation | NEW `adapter/in/kafka/` |
| FR-019 | P1 | VersionedAuditableEntity | NEW in base-model |
| FR-020 | P1 | Exception Bridge | MODIFY `shared/exception/` |
| FR-021 | P5 | ArchUnit tests | NEW `src/test/` |
| FR-022 | P4 | Virtual Threads | MODIFY `application.yml` |
| FR-023 | P4 | PolicyEvaluator fix | MODIFY `PolicyEvaluator.kt` |
| FR-024 | P3 | domain-common | NEW `services/shared/domain-common/` |
| FR-025 | P3 | security-common | NEW `services/shared/security-common/` |
