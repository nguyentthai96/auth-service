# Service Structure

_Generated: 2025-01-20_

## auth-service

### Detected Packages

- `auth.domain.model` — Domain entities (User, AuthToken, UserStatus), value objects (UserId, Email, PasswordHash)
- `auth.domain.service` — Domain services (TokenHasher)
- `auth.application` — Application services: JwtService, AuthService, AnonymousSessionDataService, AnonymousRateLimitService, SessionPromotionService, LoginRateLimitService, MfaRateLimitService, SessionPolicyService, LoginSessionService, MfaService, OtpService, TotpService, PasswordPolicyService, AccountLifecycleService, DomainLookupService, SessionCleanupScheduler, SsoAdapter, AltchaCaptchaVerifier, CaptchaVerifier
- `auth.application.command` — CQRS commands + handlers: LoginCommand/Handler, RegisterCommand/Handler, AnonymousSessionHandler, RenewAnonymousTokenHandler, RefreshTokenHandler, RevokeSessionsHandler, SwitchDomainHandler, TokenGenerator, AuthDomainEvents
- `auth.application.event` — Domain event handlers
- `auth.application.port.out` — Output ports (CaptchaGateway, SsoGateway, UserPort, DomainPort, TokenStore, PermissionCache, EventPublisher)
- `auth.application.query` — Query handlers
- `auth.application.cipher` — E2EE cipher services (DecryptionVaultService)
- `auth.adapter.in.web` — REST controllers: AnonymousAuthController, CqrsAuthController, AuthController, SessionController, MfaController, SsoController, CaptchaController, AccountLifecycleController, AdminSessionController, RateLimitAdminController, TokenController, KeyExchangeController
- `auth.adapter.in.web.dto` — Request/Response DTOs: AnonymousDtos, RequestDtos, AuthResponse, MfaDtos, SsoDtos, TokenDtos
- `auth.adapter.in.web.filter` — Request filters: LoginRateLimitFilter
- `auth.adapter.in.kafka` — Kafka consumer
- `auth.adapter.out.http` — HTTP gateways: HttpCaptchaGateway, HttpSsoGateway
- `auth.adapter.out.gateway` — Gateway adapters: CaptchaGatewayAdapter
- `auth.adapter.out.persistence` — JPA repositories
- `auth.adapter.out.cipher` — Cipher adapters: RedisAntiReplayValidator, RedisCipherKeySessionResolver
- `shared.config` — Configuration: SecurityConfig, SecurityProperties, RedisConfig
- `shared.security` — Security filters: JwtAuthFilter
- `shared.exception` — Exceptions: AuthException hierarchy, AuthErrorCode enum, GlobalExceptionHandler, AnonymousExceptions, CipherExceptions
- `shared.i18n` — Internationalization: I18nMessageRepository
- `shared.audit` — Audit infrastructure
- `shared.persistence` — Shared persistence utilities
- `rbac.adapter.out.persistence.entity` — RBAC entities: TokenBlacklistEntity, PermissionEntities
- `rbac.adapter.out.persistence.repository` — RBAC repositories: TokenBlacklistRepository
- `pbac.adapter.in.web` — Policy-Based Access Control controllers

### Anonymous-Login-Optimization Specific Packages

- `auth.application` — AnonymousSessionDataService, AnonymousRateLimitService, SessionPromotionService, PromotionResult, AnonymousSessionResult
- `auth.application.command` — AnonymousSessionHandler, RenewAnonymousTokenHandler, CreateAnonymousSessionCommand, RenewAnonymousTokenCommand
- `auth.adapter.in.web` — AnonymousAuthController
- `auth.adapter.in.web.dto` — AnonymousDtos (CreateAnonymousSessionRequest, StoreSessionDataRequest, AnonymousTokenResponse, SessionDataResponse, DataTransferredInfo)
- `shared.exception` — AnonymousExceptions (AnonymousSessionExpiredException, AnonymousDataLimitExceededException, AnonymousPromotionConflictException, AnonymousRateLimitedException, AnonymousMaxRenewalsException)

### Not Found

- `*/factory/` — NOT FOUND (no factory package; token generation done by `TokenGenerator` component)
- `*/repository/` under `auth` — NOT FOUND (repositories are in `rbac.adapter.out.persistence.repository`)

### Naming Convention

- **Controllers**: `*Controller.kt` (e.g., `AnonymousAuthController`, `CqrsAuthController`)
- **Handlers (CQRS)**: `*Handler.kt` (e.g., `AnonymousSessionHandler`, `LoginHandler`)
- **Commands (CQRS)**: `*Command.kt` (e.g., `CreateAnonymousSessionCommand`, `LoginCommand`)
- **Services**: `*Service.kt` (e.g., `AnonymousSessionDataService`, `SessionPromotionService`)
- **DTOs**: `*Dtos.kt` or `*Dto.kt` (e.g., `AnonymousDtos.kt`, `LoginRequestDto`)
- **Response**: `*Response.kt` (e.g., `AuthResponse.kt`, `AnonymousTokenResponse`)
- **Results**: `*Result.kt` (e.g., `PromotionResult`, `LoginResult`, `AnonymousSessionResult`)
- **Properties**: `*Properties.kt` (e.g., `SecurityProperties`, nested `AnonymousProperties`)
- **Exceptions**: `*Exception.kt` or `*Exceptions.kt` (e.g., `AnonymousExceptions.kt`)
- **Config**: `*Config.kt` (e.g., `SecurityConfig`, `RedisConfig`)
- **Filter**: `*Filter.kt` (e.g., `JwtAuthFilter`, `LoginRateLimitFilter`)
- **Gateway**: `*Gateway.kt` (e.g., `CaptchaGateway`, `HttpCaptchaGateway`)
- **Port**: interfaces in `port/out/` (e.g., `CaptchaGateway`, `UserPort`)
