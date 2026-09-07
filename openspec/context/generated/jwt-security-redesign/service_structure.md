# Service Structure

_Generated: 2026-09-07_

## auth-service

### Detected Packages

- `auth.application` — Business logic services (JwtService, LoginSessionService, TokenBlacklistCacheService, MfaService, SessionCleanupScheduler, ClaimValidatorChain)
- `auth.application.command` — CQRS command handlers (LoginHandler, RefreshTokenHandler, LogoutHandler, RevokeSessionsHandler)
- `auth.application.query` — CQRS query handlers (BuildAuthResponseQuery)
- `auth.application.port.out` — Outbound ports (TokenStore, NotificationGateway, UserPort)
- `auth.adapter.in.web` — REST controllers (SessionController, AdminSessionController, CqrsAuthController, TokenController, MfaController, AnonymousAuthController, SsoController, CaptchaController, AccountLifecycleController, EventStoreController, RateLimitAdminController, KeyExchangeController, InternalApiController)
- `auth.adapter.in.web.dto` — Request/Response DTOs (RequestDtos, AuthResponse, TokenDtos, MfaDtos, AnonymousDtos, SsoDtos)
- `auth.adapter.out.persistence.entity` — JPA entities (LoginSessionEntity, EventOutboxEntity, EventStoreEntity, ProcessedEventEntity, MfaRecoveryCodeEntity)
- `auth.adapter.out.persistence.repository` — JPA repositories (LoginSessionRepository, EventOutboxJpaRepository)
- `auth.adapter.out.persistence` — Persistence adapters (OutboxPersistenceAdapter)
- `auth.adapter.out.event` — Event infrastructure (OutboxPoller)
- `auth.adapter.out.http` — HTTP clients (SsoProviderClient, CaptchaClient)
- `auth.adapter.out.notification` — Notification adapters (KafkaNotificationGateway, LoggingNotificationGateway)
- `auth.domain.event` — Domain events (NewDeviceLoginEvent, TokenValidationFailedEvent)
- `shared.security` — Security filters (JwtAuthFilter, ServiceAuthFilter, LoginRateLimitFilter)
- `shared.config` — Configuration (SecurityConfig, SecurityProperties)
- `shared.exception` — Exception handling (GlobalExceptionHandler, AuthErrorCode, AuthExceptions, AuthCoreExceptions, AnonymousExceptions, CipherExceptions)
- `shared.persistence` — Shared persistence (VersionedAuditableEntity)
- `shared.audit` — Audit (AuditLogEntity)

### Not Found

- `auth.application.factory` — No factory pattern detected
- `auth.domain.model` — No rich domain model detected (anemic entities)

### Naming Convention

- Entity: `<Name>Entity` (e.g., `LoginSessionEntity`)
- Repository: `<Name>Repository` or `<Name>JpaRepository` (e.g., `LoginSessionRepository`, `EventOutboxJpaRepository`)
- Controller: `<Name>Controller` (e.g., `SessionController`)
- Handler: `<Name>Handler` (e.g., `LoginHandler`)
- Service: `<Name>Service` (e.g., `LoginSessionService`)
- DTO: `<Name>RequestDto`, `<Name>Response`, `data class` (Kotlin)
- Properties: `<Name>Properties` nested in `SecurityProperties`
- Event: `<Name>Event` (e.g., `NewDeviceLoginEvent`)
