# Service Structure

_Generated: 2025-08-22_

## auth-service (auth module)

### Detected Packages

- `auth.domain.event` — Domain events: UserRegisteredEvent, TokenIssuedEvent, TokenRevokedEvent, TokenValidationFailedEvent, EventEnvelope, IssuanceContext, RevocationType, ValidationFailureReason. **Target for NEW classes**: UserLoggedInEvent, UserLoginFailedEvent, LoginFailureReason.
- `auth.domain.model` — Domain models: AuthToken, TokenIssuanceMetadata, UserStatus, etc.
- `auth.domain.service` — Domain services: TokenHasher
- `auth.application.command` — CQRS command handlers: LoginHandler, RegisterHandler, RefreshTokenHandler, etc. + Command data classes: LoginCommand, RegisterCommand, etc. + AuthDomainEvents (contains old UserLoggedInEvent — target for cleanup)
- `auth.application.event` — Event recording services: EventService, TokenEventRecorder, NewDeviceLoginEvent, RateLimitExceededEvent. **Target for NEW class**: LoginEventRecorder.
- `auth.application.port.out` — Outbound ports: DomainEvent interface, EventPublisher, EventStorePort, OutboxPort, UserPort, DomainPort, TokenStore, CaptchaGateway, NotificationGateway, SsoGateway
- `auth.application.query` — Query handlers: BuildAuthResponseHandler, GetUserRolesHandler
- `auth.application` — Application services: LoginSessionService, LoginRateLimitService, MfaService, SessionPolicyService, SessionPromotionService, PasswordPolicyService, JwtService, etc.
- `auth.adapter.in.web` — REST controllers: CqrsAuthController, EventStoreController, MfaController, SessionController, etc.
- `auth.adapter.in.web.dto` — DTOs: LoginRequestDto, AuthResponse, RegisterRequestDto, etc.
- `auth.adapter.in.web.filter` — Servlet filters
- `auth.adapter.in.kafka` — Kafka consumers
- `auth.adapter.out.event` — Event infrastructure: OutboxPoller, KafkaEventPublisher, SpringEventPublisher
- `auth.adapter.out.persistence` — JPA persistence: EventStorePersistenceAdapter, OutboxPersistenceAdapter, UserPersistenceAdapter, entities, repositories
- `auth.adapter.out.notification` — Notification gateway: KafkaNotificationGateway
- `auth.adapter.out.gateway` — External gateways
- `auth.adapter.out.http` — HTTP clients
- `auth.adapter.out.sso` — SSO adapters
- `auth.adapter.out.cipher` — Encryption adapters

### Not Found

- `auth.application.factory` — no factory pattern detected (handlers are direct @Component)

### Naming Convention

- Controllers: `*Controller.kt` (e.g., CqrsAuthController)
- Handlers: `*Handler.kt` (e.g., LoginHandler)
- Commands: `*Command.kt` (e.g., LoginCommand)
- Domain events: `*Event.kt` (e.g., UserRegisteredEvent, TokenIssuedEvent)
- Enums: PascalCase descriptive (e.g., ValidationFailureReason, IssuanceContext)
- Helper services: `*Recorder.kt` for event recording (e.g., TokenEventRecorder)
- Ports: `*Port.kt` or `*Gateway.kt` (e.g., EventStorePort, CaptchaGateway)
- Persistence adapters: `*PersistenceAdapter.kt`
- DTOs: `*RequestDto.kt`, `*Response.kt`
- Event types (string): `iam.{aggregate}.{action}` dot-notation (e.g., `iam.user.registered`, `iam.token.issued`)

## auth-service (shared module)

### Detected Packages

- `shared.exception` — Exception classes: AuthException, InvalidCredentialsException, AccountLockedException, CaptchaRequiredException, CaptchaFailedException, PasswordExpiredException, RateLimitExceededException, AuthErrorCode enum
- `shared.audit` — Audit: AuditLogService, AuditLogEntity, AuditLogRepository
- `shared.config` — Configuration: SecurityProperties
- `shared.filter` — Security filters
- `shared.i18n` — Internationalization
- `shared.persistence` — Shared persistence
- `shared.security` — Security utilities
