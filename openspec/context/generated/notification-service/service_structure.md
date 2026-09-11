# Service Structure

_Generated: 2026-09-11_

## auth-service

### Detected Packages

- `auth/adapter/in/web/` — REST controllers (AnonymousAuth, Mfa, Token, AccountLifecycle, Captcha)
- `auth/adapter/in/web/dto/` — Request/Response DTOs (data classes)
- `auth/adapter/in/web/filter/` — HTTP filters (ContentLanguage, ClientMetadata, ServiceAuth)
- `auth/adapter/in/kafka/` — Kafka consumer listeners
- `auth/adapter/out/cache/` — Redis cache adapters
- `auth/adapter/out/cipher/` — Encryption/decryption adapters (RedisCipherKeySession, AntiReplay)
- `auth/adapter/out/event/` — Domain event publishers (Kafka, Spring, OutboxPoller)
- `auth/adapter/out/gateway/` — External gateway adapters (Captcha)
- `auth/adapter/out/http/` — HTTP client interfaces (CaptchaClient)
- `auth/adapter/out/notification/` — Notification gateway adapters (Kafka, Logging)
- `auth/adapter/out/persistence/` — JPA persistence adapters
- `auth/adapter/out/persistence/entity/` — JPA entities (User, MailQueue, EventOutbox, MailTemplate...)
- `auth/adapter/out/persistence/mapper/` — Entity-domain mappers
- `auth/adapter/out/persistence/repository/` — JPA repositories
- `auth/adapter/out/sso/` — SSO/OAuth2 integration
- `auth/application/` — Application services (MailQueueService, MailJobScheduler, TokenService...)
- `auth/application/cipher/` — Cipher application services
- `auth/application/command/` — Command handlers (AnonymousSession, RenewAnonymousToken)
- `auth/application/event/` — Event handling services (EventService)
- `auth/application/port/out/` — Output port interfaces (OutboxPort, NotificationGateway, EventPublisher)
- `auth/application/query/` — Query handlers
- `auth/domain/event/` — Domain events (EventEnvelope, DomainEvent)
- `auth/domain/model/` — Domain models
- `auth/domain/model/vo/` — Value objects
- `auth/domain/service/` — Domain services
- `pbac/` — Policy-Based Access Control module
- `rbac/` — Role-Based Access Control module
- `shared/audit/` — Audit infrastructure
- `shared/cache/` — Cache configuration
- `shared/config/` — Configuration properties (SecurityProperties, OutboxProperties)
- `shared/exception/` — Exception classes (AuthException, AuthErrorCode, GlobalExceptionHandler)
- `shared/filter/` — Shared filters (IdempotencyFilter)
- `shared/i18n/` — Internationalization
- `shared/persistence/` — Shared persistence utilities
- `shared/security/` — Security configuration

### Not Found

- `auth/application/port/in/` — No explicit input port interfaces detected (controllers call services directly)

### Naming Convention

- Controllers: `{Feature}Controller.kt`
- DTOs: `{Feature}Dtos.kt` (multiple DTOs per file) or `{Feature}Request.kt` / `{Feature}Response.kt`
- Entities: `{Name}Entity.kt`
- Repositories: `{Name}Repository.kt` or `{Name}JpaRepository.kt`
- Services: `{Feature}Service.kt`
- Handlers: `{Feature}Handler.kt`
- Adapters: `{Feature}Adapter.kt` or `{Feature}PersistenceAdapter.kt`
- Configs: `{Feature}Config.kt` or `{Feature}Properties.kt`
- Enums: `{Name}.kt` (e.g., `MailStatus.kt`)

## account-service (reference)

### Build Convention
- Plugin: `ntt.spring-app-conventions`
- Platform BOM: `com.ntt:platform:0.0.1-SNAPSHOT`
- Starters: `base-web-starter`, `base-data-starter`, `common-log`
- Version catalog: `com.ntt:version-catalog:0.0.1-SNAPSHOT`
- Settings: `pluginManagement` → `resolutionStrategy` for `ntt.*` plugins
- AOT disabled: `tasks.named("processAot") { enabled = false }`
