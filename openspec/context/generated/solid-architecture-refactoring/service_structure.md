# Service Structure

_Generated: 2026-09-22_

## auth-service

### Detected Packages (Clean Architecture + CQRS)

#### Module: `auth`
- `auth/domain/model/` — Domain models (User, UserStatus, etc.)
- `auth/domain/model/vo/` — Value Objects
- `auth/domain/event/` — Domain events (LoginFailureReason, etc.)
- `auth/domain/service/` — Domain services
- `auth/application/` — Application services (MfaService, AccountLockoutService, etc.)
- `auth/application/command/` — CQRS command handlers (LoginHandler, RegisterHandler, etc.)
- `auth/application/query/` — CQRS query handlers (BuildAuthResponseHandler)
- `auth/application/event/` — Event handlers (LoginEventRecorder)
- `auth/application/cipher/` — Cipher/encryption services
- `auth/application/port/out/` — Output port interfaces (UserPort, DomainPort, etc.)
- `auth/adapter/in/web/` — REST controllers
- `auth/adapter/in/web/dto/` — Request/Response DTOs
- `auth/adapter/in/web/filter/` — Servlet filters (LoginRateLimitFilter)
- `auth/adapter/in/kafka/` — Kafka consumer
- `auth/adapter/out/persistence/entity/` — JPA entities
- `auth/adapter/out/persistence/mapper/` — Entity-Domain mappers
- `auth/adapter/out/persistence/repository/` — JPA repositories
- `auth/adapter/out/event/` — Event publishing adapters
- `auth/adapter/out/notification/` — Notification adapters
- `auth/adapter/out/cipher/` — Cipher adapters (Redis-based)
- `auth/adapter/out/http/` — HTTP client adapters
- `auth/adapter/out/gateway/` — Gateway adapters
- `auth/adapter/out/cache/` — Cache adapters (RedisLockoutAdapter)
- `auth/adapter/out/sso/` — SSO adapters

#### Module: `rbac`
- `rbac/domain/model/` — RBAC domain models
- `rbac/domain/service/` — RBAC domain services
- `rbac/application/` — Application layer (currently sparse)
- `rbac/application/query/` — Query handlers
- `rbac/adapter/in/web/` — Controllers (RbacControllers.kt, RolePermissionController.kt, InternalUserController.kt)
- `rbac/adapter/out/persistence/entity/` — JPA entities
- `rbac/adapter/out/persistence/mapper/` — Entity mappers
- `rbac/adapter/out/persistence/repository/` — JPA repositories

#### Module: `pbac`
- `pbac/application/` — Application layer (PolicyEvaluator)
- `pbac/adapter/in/web/` — PolicyController
- `pbac/adapter/out/persistence/entity/` — JPA entities
- `pbac/adapter/out/persistence/repository/` — JPA repositories

#### Module: `shared`
- `shared/web/` — BaseController
- `shared/config/` — Configuration (SecurityProperties, RedisConfig)
- `shared/cache/` — Cache infrastructure
- `shared/exception/` — Exception classes (AuthExceptions, GlobalExceptionHandler)
- `shared/security/` — Security rules (SecurityRuleCacheConfig)
- `shared/security/entity/` — Security entities
- `shared/security/repository/` — Security repositories
- `shared/i18n/` — Internationalization
- `shared/persistence/` — Shared persistence utilities
- `shared/filter/` — Shared servlet filters
- `shared/audit/` — Audit logging (AuditLogService)

### Not Found
- `shared/cqrs/` — CommandBus/QueryBus infrastructure (to be created)
- `auth/application/pipeline/` — Authentication pipeline (to be created)
- `auth/application/mfa/` — MFA providers (to be created)
- `rbac/application/port/` — RBAC port interfaces (to be created)
- `pbac/application/port/` — PBAC port interfaces (to be created)

### Naming Convention
- Controllers: `*Controller.kt` (suffix)
- Handlers: `*Handler.kt` (suffix)
- Services: `*Service.kt` (suffix)
- Repositories: `*Repository.kt` (suffix, JPA interface)
- Entities: `*Entity.kt` (suffix)
- DTOs: `*RequestDto.kt` / `*Response.kt`
- Ports: `*Port.kt` (suffix)
- Events: `*Event.kt` / `*Reason.kt`
- Config: `*Config.kt` / `*Properties.kt`
