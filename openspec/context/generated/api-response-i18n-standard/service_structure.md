# Service Structure

_Generated: 2026-08-27_

## auth-service

### Detected Packages

- `auth/adapter/in/web` — REST controllers (CqrsAuthController, SessionController, MfaController, SsoController, etc.)
- `auth/adapter/in/web/dto` — Request/Response DTOs (AnonymousDtos, AuthResponse, MfaDtos, RequestDtos, SsoDtos, TokenDtos)
- `auth/adapter/in/web/filter` — Servlet filters (LoginRateLimitFilter, ClientMetadataFilter, ContentLanguageFilter)
- `auth/adapter/in/kafka` — Kafka consumers
- `auth/adapter/out/persistence` — JPA repositories and entities
- `auth/adapter/out/persistence/entity` — JPA entities
- `auth/adapter/out/persistence/mapper` — Entity-to-domain mappers
- `auth/adapter/out/persistence/repository` — Spring Data JPA repositories
- `auth/adapter/out/event` — Event publishing adapters
- `auth/adapter/out/cipher` — Encryption adapters
- `auth/adapter/out/http` — HTTP client adapters
- `auth/adapter/out/gateway` — External gateway adapters
- `auth/adapter/out/cache` — Cache adapters
- `auth/adapter/out/sso` — SSO provider adapters
- `auth/application` — Application services (AuthService, MfaService, JwtService, LoginSessionService, etc.)
- `auth/application/command` — CQRS command handlers
- `auth/application/event` — Domain event handlers
- `auth/application/cipher` — Cipher application services
- `auth/application/query` — CQRS query handlers
- `auth/application/port/out` — Output ports (interfaces)
- `auth/domain/event` — Domain events
- `auth/domain/model` — Domain model classes
- `auth/domain/model/vo` — Value objects
- `auth/domain/service` — Domain services
- `shared/config` — Configuration classes (I18nConfig, SecurityConfig, etc.)
- `shared/exception` — Exception classes and error codes (AuthControllerAdvice, AuthErrorCode, AuthException hierarchy)
- `shared/i18n` — i18n infrastructure (DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository)
- `shared/security` — Security infrastructure
- `shared/cache` — Cache configuration
- `shared/persistence` — Shared persistence utilities
- `shared/filter` — Shared servlet filters
- `shared/audit` — Audit logging infrastructure
- `rbac/domain/model` — RBAC domain models
- `rbac/domain/service` — RBAC domain services
- `rbac/application` — RBAC application services
- `rbac/application/query` — RBAC query handlers
- `rbac/adapter/out/persistence` — RBAC persistence layer
- `rbac/adapter/in/web` — RBAC REST controllers (DomainController, RoleController, GroupController, etc.)
- `pbac/application` — PBAC application services
- `pbac/adapter/out/persistence` — PBAC persistence layer
- `pbac/adapter/in/web` — PBAC REST controllers (PolicyController)

### Not Found

- `controller/` (standard) — controllers are at `adapter/in/web/` (Clean Architecture convention)
- `handler/` (standard) — handlers embedded in `application/command/`
- `factory/` — no factory layer detected
- `model/` (standard) — models at `domain/model/`
- `entity/` (standard) — entities at `adapter/out/persistence/entity/`
- `repository/` (standard) — repositories at `adapter/out/persistence/repository/`

### Naming Convention

- Controllers: `*Controller.kt` (e.g., `CqrsAuthController`, `MfaController`, `SessionController`)
- Services: `*Service.kt` (e.g., `AuthService`, `MfaService`, `JwtService`)
- Handlers: `*Handler.kt` (e.g., `AnonymousSessionHandler`)
- Filters: `*Filter.kt` (e.g., `LoginRateLimitFilter`, `ContentLanguageFilter`)
- Entities: `*Entity.kt` (e.g., `I18nMessageEntity`)
- Repositories: `*Repository.kt` (e.g., `I18nMessageRepository`)
- DTOs: grouped in `*Dtos.kt` files (e.g., `MfaDtos.kt`, `SsoDtos.kt`)
- Exception: `*Exception.kt` or grouped `*Exceptions.kt` (e.g., `AuthExceptions.kt`, `CipherExceptions.kt`)
- Config: `*Config.kt` (e.g., `I18nConfig`)
- Architecture: Clean Architecture / Hexagonal (adapter/in, adapter/out, application, domain)
