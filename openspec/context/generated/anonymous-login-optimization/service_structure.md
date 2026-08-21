# Service Structure

_Generated: 2025-07-15_

## auth-service

### Detected Packages

- `auth.application` — Application services: AnonymousSessionDataService, AnonymousRateLimitService, SessionPromotionService, JwtService, SessionCleanupScheduler, LoginRateLimitService, MfaRateLimitService, LoginSessionService, SessionPolicyService, AuthService, MfaService, OtpService, TotpService, PasswordPolicyService, AccountLifecycleService, DomainLookupService, SsoAdapter, AltchaCaptchaVerifier, CaptchaVerifier
- `auth.application.command` — CQRS commands + handlers: AnonymousSessionHandler, RenewAnonymousTokenHandler, LoginHandler, RegisterHandler, RefreshTokenHandler, RevokeSessionsHandler, SwitchDomainHandler, TokenGenerator, CreateAnonymousSessionCommand, RenewAnonymousTokenCommand, LoginCommand, RegisterCommand, RefreshTokenCommand, RevokeSessionsCommand, SwitchDomainCommand, AuthDomainEvents
- `auth.application.event` — Domain event handlers
- `auth.application.port.out` — Output ports (CaptchaGateway, SsoGateway, UserPort, DomainPort, TokenStore, PermissionCache, EventPublisher)
- `auth.application.query` — Query handlers
- `auth.application.cipher` — E2EE cipher services (DecryptionVaultService)
- `auth.adapter.in.web` — REST controllers: AnonymousAuthController, CqrsAuthController, AuthController, SessionController, MfaController, SsoController, CaptchaController, AccountLifecycleController, AdminSessionController, RateLimitAdminController, TokenController, KeyExchangeController, InternalApiController
- `auth.adapter.in.web.dto` — Request/Response DTOs: AnonymousDtos, RequestDtos, AuthResponse, MfaDtos, SsoDtos, TokenDtos
- `auth.adapter.in.web.filter` — Request filters: LoginRateLimitFilter
- `auth.adapter.out.http` — HTTP gateways: HttpCaptchaGateway, HttpSsoGateway
- `auth.adapter.out.gateway` — Gateway adapters: CaptchaGatewayAdapter
- `auth.adapter.out.persistence` — JPA repositories
- `auth.adapter.out.cipher` — Cipher adapters: RedisAntiReplayValidator, RedisCipherKeySessionResolver
- `auth.adapter.out.cache` — Cache adapters: MultiTierPermissionCache
- `auth.domain.model` — Domain entities (User, AuthToken, UserStatus), value objects
- `auth.domain.service` — Domain services (TokenHasher)
- `shared.config` — Configuration: SecurityConfig, SecurityProperties, RedisConfig + TO BE CREATED: RedisLuaScriptConfig
- `shared.security` — Security filters: JwtAuthFilter
- `shared.exception` — Exceptions: AuthException hierarchy, AuthErrorCode enum, GlobalExceptionHandler, AnonymousExceptions, AuthCoreExceptions, CipherExceptions
- `shared.i18n` — Internationalization: I18nMessageRepository
- `shared.audit` — Audit infrastructure
- `shared.cache` — Cache: AbstractTwoTierCache
- `shared.filter` — Filters: IdempotencyFilter
- `shared.persistence` — Shared persistence utilities
- `rbac.adapter.out.persistence.entity` — RBAC entities: TokenBlacklistEntity, PermissionEntities
- `rbac.adapter.out.persistence.repository` — RBAC repositories: TokenBlacklistRepository
- `pbac.adapter.in.web` — Policy-Based Access Control controllers

### Optimization-Specific Packages

- `auth.application` — **MODIFY**: AnonymousSessionDataService (batch transfer, running counter, TOCTOU fix), AnonymousRateLimitService (Lua sliding window), SessionPromotionService (UUID lock, Lua safe release), SessionCleanupScheduler (blacklist cleanup)
- `auth.application.command` — **MODIFY**: AnonymousSessionHandler (pipeline, dataSize init)
- `shared.config` — **MODIFY**: SecurityProperties.AnonymousProperties (add config fields), **CREATE**: RedisLuaScriptConfig
- `resources/redis/` — **CREATE**: sliding_window_rate_limit.lua, safe_lock_release.lua

### Not Found

- `*/factory/` — NOT FOUND (no factory package; token generation done by `TokenGenerator` component)
- `*/repository/` under `auth` — NOT FOUND (repositories are in `rbac.adapter.out.persistence.repository`)
- `executePipelined` — NOT FOUND in codebase (new pattern to introduce)
- `RedisScript` / `DefaultRedisScript` — NOT FOUND in codebase (new pattern to introduce)

### Naming Convention

- **Controllers**: `*Controller.kt` (e.g., `AnonymousAuthController`, `CqrsAuthController`)
- **Handlers (CQRS)**: `*Handler.kt` (e.g., `AnonymousSessionHandler`, `LoginHandler`)
- **Commands (CQRS)**: `*Command.kt` (e.g., `CreateAnonymousSessionCommand`, `LoginCommand`)
- **Services**: `*Service.kt` (e.g., `AnonymousSessionDataService`, `SessionPromotionService`)
- **DTOs**: `*Dtos.kt` (e.g., `AnonymousDtos.kt`, `MfaDtos.kt`)
- **Response**: `*Response.kt` (e.g., `AuthResponse.kt`, `AnonymousTokenResponse`)
- **Results**: `*Result.kt` (e.g., `PromotionResult`, `LoginResult`, `AnonymousSessionResult`)
- **Properties**: `*Properties.kt` (e.g., `SecurityProperties`, nested `AnonymousProperties`)
- **Exceptions**: `*Exception.kt` or `*Exceptions.kt` (e.g., `AnonymousExceptions.kt`)
- **Config**: `*Config.kt` (e.g., `SecurityConfig`, `RedisConfig`)
- **Filter**: `*Filter.kt` (e.g., `JwtAuthFilter`, `IdempotencyFilter`)
- **Scheduler**: `*Scheduler.kt` (e.g., `SessionCleanupScheduler`)
- **Gateway**: `*Gateway.kt` (e.g., `CaptchaGateway`, `HttpCaptchaGateway`)
- **Port**: interfaces in `port/out/` (e.g., `CaptchaGateway`, `UserPort`)
