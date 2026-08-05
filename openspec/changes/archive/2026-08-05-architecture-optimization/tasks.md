<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: Architecture Optimization — auth-service

> **Profile**: EXTEND | Command | Pragmatic Hybrid (Bridge Pattern)
> **FRs**: 25 (20 URD + 5 Enriched)
> **Phases**: 5 (Domain → CQRS → Communication → Performance → Testing)

---

## Phase 1 — Domain Layer Foundation (Blast Radius: ZERO)

> ⚠️ Phase 1 chỉ ADD files mới. KHÔNG modify existing code. Safe to start immediately.

### 1.1 Value Objects & Domain Models

- [x] **Task 1: Create Value Objects**
  - File: `auth/domain/model/vo/UserId.kt` | Action: [NEW]
  - File: `auth/domain/model/vo/DomainCode.kt` | Action: [NEW]
  - File: `auth/domain/model/vo/Email.kt` | Action: [NEW]
  - File: `auth/domain/model/vo/PasswordHash.kt` | Action: [NEW]
  - FR: FR-002 — Value Objects type-safe thay primitive types
  - Pattern: `@JvmInline value class UserId(val value: Long)`. Email validate `contains("@")`.

- [x] **Task 2: Create UserStatus sealed interface**
  - File: `auth/domain/model/UserStatus.kt` | Action: [NEW]
  - FR: FR-003 — Sealed interface thay magic strings "ACTIVE", "LOCKED"
  - Pattern: `sealed interface UserStatus { data object Active; data class LockedUntil(val until: Instant) }`

- [x] **Task 3: Create User domain model**
  - File: `auth/domain/model/User.kt` | Action: [NEW]
  - FR: FR-001 — Domain model pure Kotlin, NO framework imports
  - Pattern: Class with `val id: UserId`, `var status: UserStatus`, `fun verifyPassword()`, `fun lock()`
  - Dependencies: `UserId`, `Email`, `PasswordHash`, `UserStatus` (Task 1, 2)

- [x] **Task 4: Create AuthToken domain model**
  - File: `auth/domain/model/AuthToken.kt` | Action: [NEW]
  - FR: FR-001 — Immutable data class
  - Pattern: `data class AuthToken(val accessToken: String, val refreshToken: String, val expiresAt: Instant)`

- [x] **Task 5: Create Permission domain model**
  - File: `rbac/domain/model/Permission.kt` | Action: [NEW]
  - FR: FR-001 — Immutable data class
  - Pattern: `data class Permission(val resourceCode: String, val actionCode: String)`

- [x] **Task 6: Create Role domain model**
  - File: `rbac/domain/model/Role.kt` | Action: [NEW]
  - FR: FR-001 — Domain model
  - Pattern: `data class Role(val code: String, val permissions: List<Permission>)`

### 1.2 Outbound Port Interfaces

- [x] **Task 7: Create UserPort (port interface)**
  - File: `auth/application/port/out/UserRepository.kt` | Action: [NEW]
  - FR: FR-004 — Pure Kotlin interface, returns domain models
  - Pattern: `interface UserRepository { fun findByUsername(username: String): User?; fun save(user: User): User }`

- [x] **Task 8: Create DomainPort (port interface)**
  - File: `auth/application/port/out/DomainRepository.kt` | Action: [NEW]
  - FR: FR-004

- [x] **Task 9: Create TokenStore port**
  - File: `auth/application/port/out/TokenStore.kt` | Action: [NEW]
  - FR: FR-004
  - Pattern: `interface TokenStore { fun saveRefreshToken(userId: Long, tokenHash: String, expiresAt: Instant); fun findValidRefreshToken(tokenHash: String): RefreshTokenInfo? }`

- [x] **Task 10: Create EventPublisher port**
  - File: `auth/application/port/out/EventPublisher.kt` | Action: [NEW]
  - FR: FR-004
  - Pattern: `interface EventPublisher { fun publish(event: DomainEvent) }`

- [x] **Task 11: Create SsoGateway port**
  - File: `auth/application/port/out/SsoGateway.kt` | Action: [NEW]
  - FR: FR-004
  - Pattern: `interface SsoGateway { fun exchangeAuthorizationCode(code: String): SsoUserInfo }`

- [x] **Task 12: Create CaptchaGateway port**
  - File: `auth/application/port/out/CaptchaGateway.kt` | Action: [NEW]
  - FR: FR-004
  - Pattern: `interface CaptchaGateway { fun verify(token: String): Boolean }`

- [x] **Task 13: Create PermissionCache port**
  - File: `auth/application/port/out/PermissionCache.kt` | Action: [NEW]
  - FR: FR-004, FR-017
  - Pattern: `interface PermissionCache { fun getPermissions(userId: Long, domainId: Long): List<String>?; fun putPermissions(...); fun invalidate(...) }`

### 1.3 MapStruct Mappers

- [x] **Task 14: Create UserEntityMapper**
  - File: `auth/adapter/out/persistence/mapper/UserEntityMapper.kt` | Action: [NEW]
  - FR: FR-005 — MapStruct mapper Entity ↔ Domain
  - Base: `@Mapper(componentModel = "spring")` from `libs.mapstruct`
  - Pattern: `fun toDomain(entity: UserEntity): User; fun toEntity(domain: User): UserEntity`
  - Dependencies: `User` (Task 3), `UserEntity` (existing)

- [x] **Task 15: Create PermissionMapper**
  - File: `rbac/adapter/out/persistence/mapper/PermissionMapper.kt` | Action: [NEW]
  - FR: FR-005

### 1.4 Domain Services (extracted from AuthService private methods)

- [x] **Task 16: Create TokenGenerator (replaces AuthResponseBuilder)**
  - File: `auth/domain/service/AuthResponseBuilder.kt` | Action: [NEW]
  - FR: FR-007 — Extracted from `AuthService.generateAuthResponse()` (L175-222)
  - Source: [AuthService.kt:L175-222](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L175-L222)
  - Dependencies: JwtService (existing), UserRepository port, DomainRepository port, RbacResolver

- [x] **Task 17: Create DomainLookupService**
  - File: `auth/domain/service/DomainLookupService.kt` | Action: [NEW]
  - FR: FR-007 — Extracted from `AuthService.getPrimaryDomain()` (L237-246)
  - Source: [AuthService.kt:L237-246](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L237-L246)

- [x] **Task 18: Create TokenHasher**
  - File: `auth/domain/service/TokenHasher.kt` | Action: [NEW]
  - FR: FR-007 — Extracted from `AuthService.hashToken()` (L273-276)
  - Source: [AuthService.kt:L273-276](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L273-L276)

- [x] **Task 19: Create RbacResolver**
  - File: `rbac/domain/service/RbacResolver.kt` | Action: [NEW]
  - FR: FR-008 — Extracted from `RbacEngine.resolveUserRoleIds()` + `resolveUserPermissions()` (L74-88)
  - Source: [RbacEngine.kt:L74-88](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt#L74-L88)

### 1.5 Exception Bridge

- [x] **Task 20: Create AuthErrorCode enum**
  - File: `shared/exception/AuthErrorCode.kt` | Action: [NEW]
  - FR: FR-020 — Implements `ErrorCodeBase` from base-core + `httpStatus: HttpStatus`
  - Base: `ErrorCodeBase` from `com.ntt.basecore.exception.base.ErrorCodeBase`
  - Error: AUTH_001 through AUTH_010 (see SRS §4)
  - Pattern: `enum class AuthErrorCode(code, msgCode, desc, val httpStatus: HttpStatus) : ErrorCodeBase(code, msgCode, desc)`

- [x] **Task 21: Refactor AuthException → extends BusinessException**
  - File: `shared/exception/AuthExceptions.kt` | Action: [MODIFY]
  - FR: FR-020 — Bridge pattern
  - Source: [AuthExceptions.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt)
  - Base: `BusinessException` from `com.ntt.basecore.exception.BusinessException`
  - Pattern: `open class AuthException(val authError: AuthErrorCode) : BusinessException(authError)`

- [x] **Task 22: Create AuthControllerAdvice (replaces GlobalExceptionHandler)**
  - File: `shared/exception/AuthControllerAdvice.kt` | Action: [NEW]
  - FR: FR-020 — extends BaseControllerAdvice + override for ProblemDetail
  - Base: `BaseControllerAdvice` from `com.ntt.basecore.domain.web.BaseControllerAdvice`
  - Pattern: `@ControllerAdvice class AuthControllerAdvice(validator) : BaseControllerAdvice(validator)` + `@ExceptionHandler(AuthException::class)` returns ProblemDetail with correct httpStatus

### 1.6 VersionedAuditableEntity

- [x] **Task 23: Create VersionedAuditableEntity in shared/persistence**
  - File: `components/base-core/base-model/src/.../entity/VersionedAuditableEntity.kt` | Action: [NEW]
  - FR: FR-019 — Opt-in `@Version` via inheritance
  - Base: `SnowflakePersistentAuditableEntity` from base-model
  - Pattern: `open class VersionedAuditableEntity : SnowflakePersistentAuditableEntity() { @Version var version: Long = 0 }`

- [x] **Task 24: Create Flyway migration for version column**
  - File: `auth-service/src/main/resources/db/migration/V*__add_version_column.sql` | Action: [NEW]
  - FR: FR-019
  - Pattern: `ALTER TABLE users ADD COLUMN version BIGINT DEFAULT 0; ALTER TABLE domains ADD COLUMN version BIGINT DEFAULT 0;`

---

## Phase 2 — CQRS Business Handlers (Feature Flag: `app.cqrs.enabled`)

### 2.1 Dependencies

- [x] **Task 25: Add base-cqrs-starter dependency**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-006 — Add `implementation("com.ntt:base-cqrs-starter")`
  - Dependencies: Also add `base-messaging-starter`, `base-resilience-starter`, `base-observability-starter`

### 2.2 Command/Query Data Classes

- [x] **Task 26: Create auth Command classes**
  - File: `auth/application/port/in/RegisterUserCommand.kt` | Action: [NEW]
  - File: `auth/application/port/in/LoginCommand.kt` | Action: [NEW]
  - File: `auth/application/port/in/RefreshTokenCommand.kt` | Action: [NEW]
  - File: `auth/application/port/in/SwitchDomainCommand.kt` | Action: [NEW]
  - File: `auth/application/port/in/RevokeSessionsCommand.kt` | Action: [NEW]
  - FR: FR-007
  - Base: `Command<R>` from `com.ntt.eventsourcingutils.lib.cqrs.command.Command`
  - Pattern: `data class LoginCommand(...) : Command<LoginResult>`

- [x] **Task 27: Create rbac Query classes (GetPermissionsQuery, GetUserRolesQuery)**
  - File: `rbac/application/port/in/CheckPermissionQuery.kt` | Action: [NEW]
  - File: `rbac/application/port/in/GetUserRolesQuery.kt` | Action: [NEW]
  - File: `rbac/application/port/in/GetPermissionsQuery.kt` | Action: [NEW]
  - File: `auth/application/port/in/BuildAuthResponseQuery.kt` | Action: [NEW]
  - FR: FR-008, FR-007
  - Base: `Query<R>` from `com.ntt.eventsourcingutils.lib.cqrs.query.Query`

### 2.3 CommandHandlers

- [x] **Task 28: Create RegisterHandler**
  - File: `auth/application/handler/RegisterUserHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~40 lines, 4 deps
  - Base: `CommandHandler<RegisterUserCommand, UserId>` from `com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler`
  - Source: [AuthService.kt:L38-73](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L38-L73)
  - Dependencies: UserRepository (port), DomainRepository (port), EventPublisher (port), AuthResponseBuilder

- [x] **Task 29: Create LoginHandler**
  - File: `auth/application/handler/LoginHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~55 lines, 4 deps (accepted above 50-line target)
  - Base: `CommandHandler<LoginCommand, LoginResult>`
  - Source: [AuthService.kt:L76-130](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L76-L130)
  - Dependencies: UserRepository (port), CaptchaGateway (port), MfaPort, AuthResponseBuilder
  - Pattern: Includes `handleFailedLogin()` as private method (merged, not extracted)

- [x] **Task 30: Create RefreshTokenHandler**
  - File: `auth/application/handler/RefreshTokenHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~25 lines, 3 deps
  - Base: `CommandHandler<RefreshTokenCommand, AuthResponse>`
  - Source: [AuthService.kt:L133-154](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L133-L154)
  - Dependencies: TokenStore (port), UserRepository (port), AuthResponseBuilder

- [x] **Task 31: Create SwitchDomainHandler**
  - File: `auth/application/handler/SwitchDomainHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~15 lines, 3 deps
  - Base: `CommandHandler<SwitchDomainCommand, AuthResponse>`
  - Source: [AuthService.kt:L160-173](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L160-L173)

- [x] **Task 32: Create RevokeSessionsHandler**
  - File: `auth/application/handler/RevokeSessionsHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~10 lines, 2 deps
  - Base: `CommandHandler<RevokeSessionsCommand, Int>`
  - Source: [AuthService.kt:L263-271](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L263-L271)

### 2.4 QueryHandlers

- [x] **Task 33: Create CheckPermissionHandler**
  - File: `rbac/application/handler/CheckPermissionHandler.kt` | Action: [NEW]
  - FR: FR-008 — ~15 lines, 2 deps
  - Base: `QueryHandler<CheckPermissionQuery, Boolean>`
  - Source: [RbacEngine.kt:L29-42](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt#L29-L42)

- [x] **Task 34: Create GetUserRolesHandler**
  - File: `rbac/application/handler/GetUserRolesHandler.kt` | Action: [NEW]
  - FR: FR-008 — ~10 lines, 2 deps
  - Base: `QueryHandler<GetUserRolesQuery, List<String>>`
  - Source: [RbacEngine.kt:L48-53](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt#L48-L53)

- [x] **Task 35: Create GetPermissionsHandler (N+1 fix included)**
  - File: `rbac/application/handler/GetPermissionsHandler.kt` | Action: [NEW]
  - FR: FR-008, FR-015 — ~20 lines, 2 deps. Replaces N+1 with batch JOIN query.
  - Base: `QueryHandler<GetPermissionsQuery, List<String>>`
  - Source: [RbacEngine.kt:L59-72](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt#L59-L72)
  - Pattern: Single `@Query` with JOIN instead of N loop `findById()` calls

- [x] **Task 36: Create BuildAuthResponseHandler**
  - File: `auth/application/handler/BuildAuthResponseHandler.kt` | Action: [NEW]
  - FR: FR-007 — ~15 lines, 3 deps
  - Base: `QueryHandler<BuildAuthResponseQuery, AuthResponse>`
  - Source: [AuthService.kt:L251-257](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt#L251-L257)

### 2.5 Port Implementations (JPA Adapters)

- [x] **Task 37: Create UserPersistenceAdapter (JPA adapter)**
  - File: `auth/adapter/out/persistence/adapter/JpaUserRepositoryAdapter.kt` | Action: [NEW]
  - FR: FR-004 — Implements UserRepository port, uses MapStruct mapper
  - Dependencies: Existing `UserRepository` (JPA), `UserEntityMapper` (Task 14)

- [x] **Task 38: Create DomainPersistenceAdapter + TokenStorePersistenceAdapter + CaptchaGatewayAdapter + SpringEventPublisher + InMemoryPermissionCache**
  - File: `auth/adapter/out/persistence/adapter/JpaDomainRepositoryAdapter.kt` | Action: [NEW]
  - FR: FR-004

### 2.6 Controller Rewire

- [x] **Task 39: Rewire AuthController → CommandBus/QueryBus**
  - File: `auth/adapter/in/web/AuthController.kt` | Action: [MODIFY]
  - FR: FR-009 — Replace `AuthService` injection with `CommandBus`/`QueryBus`
  - Source: [AuthController.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt)
  - Pattern: `commandBus.dispatch(RegisterUserCommand(...))` instead of `authService.register(...)`

- [x] **Task 40: Rewire remaining controllers (RBAC CRUD — no CQRS needed)**
  - File: `auth/adapter/in/web/SsoController.kt` | Action: [MODIFY]
  - File: `auth/adapter/in/web/MfaController.kt` | Action: [MODIFY]
  - File: `auth/adapter/in/web/TokenController.kt` | Action: [MODIFY]
  - File: `rbac/adapter/in/web/RbacControllers.kt` | Action: [MODIFY]
  - FR: FR-009

- [x] **Task 41: Add feature flag configuration**
  - File: `src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-009 — Add `app.cqrs.enabled: true`
  - Pattern: `@ConditionalOnProperty("app.cqrs.enabled")` on CQRS controllers

- [ ] **Task 42: Delete AuthService.kt and RbacEngine.kt (after verification)**
  - File: `auth/application/AuthService.kt` | Action: [DELETE]
  - File: `rbac/application/RbacEngine.kt` | Action: [DELETE]
  - FR: FR-007, FR-008 — Only after all handlers verified working

---

## Phase 3 — Communication Layer

### 3.1 Shared Modules

- [ ] **Task 43: Create grpc-proto Gradle module**
  - File: `services/shared/grpc-proto/build.gradle.kts` | Action: [NEW]
  - File: `services/shared/grpc-proto/src/main/proto/permission_service.proto` | Action: [NEW]
  - FR: FR-010 — Proto definitions for PermissionService
  - Pattern: Protobuf plugin + gRPC codegen. See design.md §3.1.

- [ ] **Task 44: Create domain-common Gradle module**
  - File: `services/shared/domain-common/build.gradle.kts` | Action: [NEW]
  - File: `services/shared/domain-common/src/.../vo/*.kt` | Action: [NEW]
  - FR: FR-024 — Shared VOs (UserId, DomainCode). Pure Kotlin only.

- [ ] **Task 45: Create security-common Gradle module**
  - File: `services/shared/security-common/build.gradle.kts` | Action: [NEW]
  - FR: FR-025 — JWT validation + permission cache client

### 3.2 gRPC Server

- [ ] **Task 46: Add spring-boot-starter-grpc dependency**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-011

- [ ] **Task 47: Implement PermissionGrpcService**
  - File: `auth/adapter/in/grpc/PermissionGrpcService.kt` | Action: [NEW]
  - FR: FR-012 — gRPC server dispatching via QueryBus
  - Base: `PermissionServiceGrpc.PermissionServiceImplBase()` (generated from proto)
  - Dependencies: QueryBus
  - Pattern: See design.md §3.2

### 3.3 @HttpExchange Migration

- [x] **Task 48: Create SsoProviderClient interface**
  - File: `auth/adapter/out/http/SsoProviderClient.kt` | Action: [NEW]
  - FR: FR-013 — `@HttpExchange` declarative client

- [x] **Task 49: Create CaptchaClient interface**
  - File: `auth/adapter/out/http/CaptchaClient.kt` | Action: [NEW]
  - FR: FR-013 — `@HttpExchange` declarative client

- [x] **Task 50: Create HttpSsoGateway adapter**
  - File: `auth/adapter/out/http/HttpSsoGateway.kt` | Action: [NEW]
  - FR: FR-013 — Implements `SsoGateway` port using `SsoProviderClient`
  - Source: [SsoAdapter.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) — replaces

- [x] **Task 51: Create HttpCaptchaGateway adapter**
  - File: `auth/adapter/out/http/HttpCaptchaGateway.kt` | Action: [NEW]
  - FR: FR-013 — Implements `CaptchaGateway` port using `CaptchaClient`
  - Source: [CaptchaVerifier.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt) — replaces

- [x] **Task 52: Configure HTTP client groups**
  - File: `src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-014 — `spring.http.client.service.sso-provider.*`, `captcha.*`

---

## Phase 4 — Performance Optimization

- [x] **Task 53: Create CaffeinePermissionCache adapter**
  - File: `auth/adapter/out/cache/CaffeinePermissionCache.kt` | Action: [NEW]
  - FR: FR-016 — Caffeine in-process cache, TTL 30s
  - Pattern: `@Bean fun permissionCache(): Cache<String, List<String>> = Caffeine.newBuilder().expireAfterWrite(30, SECONDS).build()`

- [x] **Task 54: Create MultiTierPermissionCache adapter**
  - File: `auth/adapter/out/cache/MultiTierPermissionCache.kt` | Action: [NEW]
  - FR: FR-017 — Implements `PermissionCache` port. Fallback: Caffeine → Redis → DB.
  - Dependencies: Caffeine (L1), StringRedisTemplate (L2), PermissionQueryPort (DB fallback)

- [x] **Task 55: Create PermissionChangedConsumer**
  - File: `auth/adapter/in/kafka/PermissionChangedConsumer.kt` | Action: [NEW]
  - FR: FR-018 — `@KafkaListener(topics = ["iam.permission.changed"])`. Invalidate L1+L2.

- [x] **Task 56: Enable Virtual Threads (already configured)**
  - File: `src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-022 — `spring.threads.virtual.enabled: true`

- [x] **Task 57: Fix PolicyEvaluator thread pool**
  - File: `pbac/application/PolicyEvaluator.kt` | Action: [MODIFY]
  - FR: FR-023 — Replace `Executors.newCachedThreadPool()` → `Executors.newVirtualThreadPerTaskExecutor()`
  - Source: [PolicyEvaluator.kt:L29](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/pbac/application/PolicyEvaluator.kt#L29)

---

## Phase 5 — Testing

- [x] **Task 58: Create ArchUnit boundary tests**
  - File: `src/test/kotlin/.../ArchitectureTest.kt` | Action: [NEW]
  - FR: FR-021
  - Pattern: domain ≠ depend Spring/JPA, application ≠ depend adapter, handlers only access ports + domain
  - Dependencies: `libs.archunit.junit5`

- [x] **Task 59: Create domain model unit tests**
  - File: `src/test/kotlin/.../domain/model/UserTest.kt` | Action: [NEW]
  - FR: FR-001, FR-002, FR-003 — Test VOs, UserStatus transitions, domain logic

- [x] **Task 60: Create handler unit tests**
  - File: `src/test/kotlin/.../handler/RegisterUserHandlerTest.kt` | Action: [NEW]
  - File: `src/test/kotlin/.../handler/LoginHandlerTest.kt` | Action: [NEW]
  - File: `src/test/kotlin/.../handler/GetPermissionsHandlerTest.kt` | Action: [NEW]
  - FR: FR-007, FR-008 — Mock ports, test handler logic

- [x] **Task 61: Create integration tests (H2 skeleton, TODO Testcontainers)**
  - File: `src/test/kotlin/.../integration/AuthFlowIntegrationTest.kt` | Action: [NEW]
  - FR: FR-007, FR-012 — Full flow: register → login → refresh → switchDomain
  - Dependencies: `base-testing-starter`, Testcontainers (PostgreSQL, Redis, Kafka)

---

## Summary

| Phase | Tasks | Files NEW | Files MODIFY | Files DELETE |
|:-----:|:-----:|:---------:|:------------:|:------------:|
| P1 | 24 | ~24 | 1 (AuthExceptions) | 0 |
| P2 | 18 | ~17 | 6 (controllers, build.gradle, yml) | 2 (AuthService, RbacEngine) |
| P3 | 10 | ~10 | 2 (build.gradle, yml) | 0 |
| P4 | 5 | ~3 | 2 (yml, PolicyEvaluator) | 0 |
| P5 | 4 | ~5 | 0 | 0 |
| **Total** | **61** | **~59** | **~11** | **2** |

### FR Coverage

All 25 FRs mapped:
- FR-001 → Tasks 3-6 (domain models)
- FR-002 → Task 1 (VOs)
- FR-003 → Task 2 (UserStatus)
- FR-004 → Tasks 7-13 (ports)
- FR-005 → Tasks 14-15 (mappers)
- FR-006 → Task 25 (dependency)
- FR-007 → Tasks 16-18, 26, 28-32, 36, 39, 42 (split AuthService)
- FR-008 → Tasks 19, 27, 33-35, 42 (split RbacEngine)
- FR-009 → Tasks 39-41 (controller rewire)
- FR-010 → Task 43 (grpc-proto)
- FR-011 → Task 46 (gRPC dep)
- FR-012 → Task 47 (gRPC server)
- FR-013 → Tasks 48-51 (@HttpExchange)
- FR-014 → Task 52 (HTTP config)
- FR-015 → Task 35 (N+1 fix, included in GetPermissionsHandler)
- FR-016 → Task 53 (Caffeine L1)
- FR-017 → Task 54 (Multi-tier cache)
- FR-018 → Task 55 (Kafka invalidation)
- FR-019 → Tasks 23-24 (VersionedAuditableEntity)
- FR-020 → Tasks 20-22 (exception bridge)
- FR-021 → Task 58 (ArchUnit)
- FR-022 → Task 56 (Virtual Threads)
- FR-023 → Task 57 (PolicyEvaluator fix)
- FR-024 → Task 44 (domain-common)
- FR-025 → Task 45 (security-common)
