# Service Structure

_Generated: 2025-08-22_

## auth-service (auth module)

### Detected Packages

- `auth.adapter.in.web` — REST controllers (CqrsAuthController, EventStoreController, etc.)
- `auth.adapter.in.web.dto` — Request/Response DTOs (RegisterRequestDto, AuthResponse, etc.)
- `auth.adapter.in.kafka` — Kafka consumers (PermissionChangedConsumer)
- `auth.adapter.out.event` — Event publishers (KafkaEventPublisher, SpringEventPublisher, OutboxPoller)
- `auth.adapter.out.persistence` — JPA adapters (UserPersistenceAdapter, EventStorePersistenceAdapter, OutboxPersistenceAdapter)
- `auth.adapter.out.persistence.entity` — JPA entities (EventStoreEntity, EventOutboxEntity, etc.)
- `auth.adapter.out.persistence.repository` — Spring Data JPA repositories
- `auth.adapter.out.http` — HTTP clients (CaptchaClient, SsoProviderClient)
- `auth.adapter.out.notification` — Notification gateway adapters
- `auth.adapter.out.cipher` — Encryption adapters
- `auth.adapter.out.sso` — SSO adapters
- `auth.adapter.out.gateway` — External gateway adapters
- `auth.application.command` — CQRS command handlers (RegisterHandler, LoginHandler, etc.)
- `auth.application.event` — Event recording services (EventService, LoginEventRecorder, TokenEventRecorder)
- `auth.application.port.out` — Outbound port interfaces (UserPort, EventStorePort, OutboxPort, EventPublisher, DomainEvent)
- `auth.application.query` — CQRS query handlers
- `auth.application.cipher` — Cipher/encryption services
- `auth.domain.event` — Domain event classes (UserRegisteredEvent, EventEnvelope, etc.)
- `auth.domain.model` — Domain models (User, AuthToken, UserStatus, etc.)
- `auth.domain.model.vo` — Value objects (UserId, Email, PasswordHash)
- `auth.domain.service` — Domain services

### Relevant Sub-modules

- `shared.config` — Configuration classes (KafkaConfig, RedisConfig, etc.)
- `shared.exception` — Exception classes (AuthException, DuplicateResourceException, ResourceNotFoundException, AuthErrorCode)
- `shared.audit` — Audit logging
- `shared.filter` — Servlet filters
- `shared.i18n` — Internationalization
- `shared.persistence` — Shared persistence utils
- `shared.security` — Security config

### Not Found

- `auth.application.factory` — no factory pattern detected in event recording flow

### Naming Convention

- Controllers: `*Controller.kt` (e.g., CqrsAuthController)
- CQRS Handlers: `*Handler.kt` (e.g., RegisterHandler, LoginHandler)
- Event Recorders: `*EventRecorder.kt` (e.g., LoginEventRecorder, TokenEventRecorder)
- Domain Events: `*Event.kt` (e.g., UserRegisteredEvent, UserLoggedInEvent)
- Failure Reasons: `*FailureReason.kt` or `*Reason.kt` (e.g., LoginFailureReason)
- Ports: `*Port.kt` (e.g., EventStorePort, OutboxPort, UserPort)
- Adapters: `*PersistenceAdapter.kt`, `*Publisher.kt` (e.g., EventStorePersistenceAdapter, KafkaEventPublisher)
- DTOs: `*RequestDto.kt`, `*Response.kt` (e.g., RegisterRequestDto, AuthResponse)
- Commands: `*Command.kt` (e.g., RegisterCommand, LoginCommand)
- Entities: `*Entity.kt` (e.g., EventStoreEntity, EventOutboxEntity)
- Enums: descriptive name (e.g., LoginFailureReason, IssuanceContext, RevocationType)

### Architecture Pattern

- **Hexagonal Architecture** (Ports & Adapters)
  - `application.port.out` — outbound ports (interfaces)
  - `adapter.out.*` — infrastructure adapters (implementations)
  - `adapter.in.*` — driving adapters (controllers, consumers)
  - `domain.*` — domain model + events
  - `application.*` — application services + CQRS handlers
- **CQRS** via eventsourcing-utils library (Command/CommandHandler interfaces)
- **Event Sourcing** infrastructure (EventService, event_store, outbox pattern)
- **Fire-and-forget** event recording via helper services (LoginEventRecorder, TokenEventRecorder)
