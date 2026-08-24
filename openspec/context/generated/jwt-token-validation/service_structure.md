# Service Structure

_Generated: 2025-01-20_

## auth-service (auth module)

### Detected Packages

- `auth.application` — Application services (JwtService, TokenBlacklistCacheService, ClaimValidator chain, ServiceTokenService, LoginSessionService, MfaService, etc.)
- `auth.application.command` — CQRS command handlers (16 handlers detected)
- `auth.application.query` — CQRS query handlers (2 handlers detected)
- `auth.application.event` — Event services (EventService, TokenEventRecorder, NewDeviceLoginEvent, RateLimitExceededEvent)
- `auth.application.port.out` — Outbound ports (TokenStore, EventStorePort, OutboxPort, UserPort, DomainPort, SsoGateway, CaptchaGateway, NotificationGateway, EventPublisher)
- `auth.application.cipher` — E2EE cipher services (4 files)
- `auth.adapter.in.web` — REST controllers (TokenController, CqrsAuthController, MfaController, SessionController, etc. — 13 controllers)
- `auth.adapter.in.web.dto` — DTOs (TokenDtos, AuthResponse, RequestDtos, MfaDtos, SsoDtos, AnonymousDtos)
- `auth.adapter.in.web.filter` — HTTP filters (ServiceAuthFilter, LoginRateLimitFilter, ContentLanguageFilter, ClientMetadataFilter)
- `auth.adapter.in.kafka` — Kafka consumers (2 files)
- `auth.adapter.out` — Outbound adapters (8 directories)
- `auth.domain.event` — Domain events (TokenIssuedEvent, TokenRevokedEvent, TokenValidationFailedEvent, ValidationFailureReason, etc.)
- `auth.domain.model` — Domain models (5 files)
- `auth.domain.service` — Domain services (TokenHasher)

## auth-service (rbac module)

### Detected Packages

- `rbac.adapter.out.persistence.entity` — JPA entities (PermissionEntities.kt — includes TokenBlacklistEntity)
- `rbac.adapter.out.persistence.repository` — JPA repositories (Repositories.kt — includes TokenBlacklistRepository)
- `rbac.application` — RBAC application services (2 files)
- `rbac.domain` — RBAC domain models (2 files)

## auth-service (shared module)

### Detected Packages

- `shared.security` — Security filters (JwtAuthFilter)
- `shared.config` — Configuration (SecurityProperties, SecurityConfig, plus 9 other config files — 11 total)
- `shared.exception` — Exception hierarchy (AuthException, AuthErrorCode, GlobalExceptionHandler, AuthCoreExceptions, CipherExceptions, AnonymousExceptions, AuthExceptions)
- `shared.filter` — Generic filters (1 file)
- `shared.audit` — Audit services (3 files)
- `shared.i18n` — Internationalization (3 files)
- `shared.persistence` — Shared persistence utilities (1 file)
- `shared.cache` — Cache utilities (0 files — empty)

## auth-service (pbac module)

### Detected Packages

- `pbac` — Policy-Based Access Control (2 directories)

### Not Found

- `shared.cache` — directory exists but empty (Caffeine cache is programmatic in TokenBlacklistCacheService, not through Spring Cache)

### Naming Convention

- **Controllers**: `*Controller` (e.g., `TokenController`, `CqrsAuthController`)
- **Services**: `*Service` (e.g., `JwtService`, `TokenBlacklistCacheService`)
- **Filters**: `*Filter` (e.g., `JwtAuthFilter`, `ServiceAuthFilter`)
- **Validators**: `*ClaimValidator` (e.g., `IssuerClaimValidator`)
- **Events**: `*Event` (e.g., `TokenValidationFailedEvent`)
- **DTOs**: `*Request`, `*Response`, `*Dtos` suffix in grouped files
- **Entities**: `*Entity` (e.g., `TokenBlacklistEntity`)
- **Repositories**: `*Repository` (e.g., `TokenBlacklistRepository`)
- **Ports**: interface in `port.out/` (e.g., `TokenStore`, `EventStorePort`)
- **Commands**: `*Command` + `*Handler` (CQRS pattern)
- **Properties**: `*Properties` (e.g., `SecurityProperties`, `BlacklistCacheProperties`)
- **Exceptions**: `*Exception` (e.g., `ClaimValidationException`)
- **Error Codes**: `AuthErrorCode` enum (AUTH_001–AUTH_062)
