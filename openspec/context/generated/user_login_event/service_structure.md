# Service Structure

_Generated: 2025-08-21_

## auth-service (auth module)

### Detected Packages

- `auth.adapter.in.web` — REST controllers (CqrsAuthController, MfaController)
- `auth.adapter.in.web.dto` — Web DTOs (AuthResponse, etc.)
- `auth.adapter.in.web.filter` — Servlet filters (LoginRateLimitFilter)
- `auth.adapter.in.kafka` — Kafka consumers (PermissionChangedConsumer)
- `auth.adapter.out.event` — Event adapters (SpringEventPublisher, KafkaEventPublisher, OutboxPoller)
- `auth.adapter.out.persistence` — JPA persistence adapters
- `auth.adapter.out.persistence.entity` — JPA entities (EventStoreEntity, EventOutboxEntity)
- `auth.adapter.out.persistence.mapper` — Entity-domain mappers
- `auth.adapter.out.persistence.repository` — Spring Data repositories
- `auth.adapter.out.cache` — Cache adapters (PermissionCache)
- `auth.adapter.out.cipher` — E2EE cipher adapters
- `auth.adapter.out.gateway` — External gateway adapters
- `auth.adapter.out.http` — HTTP client adapters
- `auth.adapter.out.sso` — SSO provider adapters
- `auth.application` — Application services (AuthService, LoginSessionService, MfaService, SessionPromotionService, etc.)
- `auth.application.command` — CQRS commands + handlers (LoginCommand, LoginHandler, RegisterCommand, RegisterHandler, etc.)
- `auth.application.event` — Event services (EventService, NewDeviceLoginEvent)
- `auth.application.port.out` — Outbound port interfaces (UserPort, DomainPort, EventStorePort, OutboxPort, DomainEvent, etc.)
- `auth.application.query` — CQRS queries (BuildAuthResponseQuery)
- `auth.application.cipher` — Cipher services
- `auth.domain.event` — Domain events (UserRegisteredEvent, EventEnvelope)
- `auth.domain.model` — Domain models (User, AuthToken, UserStatus)
- `auth.domain.model.vo` — Value objects
- `auth.domain.service` — Domain services (TokenHasher)

### Not Found

- `auth.application.factory` — NOT FOUND (no factory pattern used in auth module)
- `auth.application.saga` — NOT FOUND (no saga pattern)

### Naming Convention

- Commands: `{Action}Command.kt` (e.g., LoginCommand, RegisterCommand, RenewAnonymousTokenCommand)
- Handlers: `{Action}Handler.kt` (e.g., LoginHandler, RegisterHandler)
- Domain Events: `{Entity}{Action}Event.kt` (e.g., UserRegisteredEvent, UserLoggedInEvent)
- Ports: `{Entity}Port.kt` or `{Capability}Port.kt` (e.g., UserPort, EventStorePort, OutboxPort)
- DTOs: `{Action}Request.kt`, `{Action}Response.kt`
- Exceptions: `{Condition}Exception.kt` (e.g., InvalidCredentialsException, AccountLockedException)
- Config: `{Feature}Properties.kt` or `{Feature}Config.kt`

## auth-service (shared module)

### Detected Packages

- `shared.config` — Configuration classes (SecurityProperties, RedisConfig, KafkaConfig, OutboxProperties, RedisLuaScriptConfig, JacksonConfig)
- `shared.exception` — Exception hierarchy (AuthException, AuthErrorCode, GlobalExceptionHandler)
- `shared.cache` — Cache infrastructure (AbstractTwoTierCache)
- `shared.filter` — Shared filters (IdempotencyFilter)
- `shared.audit` — Audit infrastructure

### Not Found

- `shared.util` — NOT FOUND
- `shared.i18n` — NOT FOUND (message keys in AuthErrorCode.msgCode used by GlobalExceptionHandler)

## auth-service (pbac module)

### Detected Packages

- `pbac.adapter.in.web` — Policy management controllers
- `pbac.adapter.out.persistence` — Policy persistence
- `pbac.application` — Policy evaluation services

## auth-service (rbac module)

### Detected Packages

- `rbac.adapter.in.web` — Role management controllers
- `rbac.adapter.out.persistence` — Role persistence
- `rbac.application` — Role assignment services
- `rbac.application.query` — Role query handlers
- `rbac.domain.model` — Role domain models
- `rbac.domain.service` — Role domain services
