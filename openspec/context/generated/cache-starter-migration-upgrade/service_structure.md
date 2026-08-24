# Service Structure

_Generated: 2026-08-25_

## auth-service

### Detected Packages

- `com.ntt.authservice.auth.adapter.in.web` — REST Controllers (CqrsAuthController, TokenController, SessionController, MfaController, AnonymousAuthController, SsoController, CaptchaController, AccountLifecycleController, InternalApiController, AdminSessionController, RateLimitAdminController, KeyExchangeController, EventStoreController)
- `com.ntt.authservice.auth.adapter.in.web.dto` — Request/Response DTOs (AuthResponse, RequestDtos, MfaDtos, TokenDtos, AnonymousDtos, SsoDtos)
- `com.ntt.authservice.auth.adapter.in.kafka` — Kafka Consumers (PermissionChangedConsumer, AccountStatusConsumer)
- `com.ntt.authservice.auth.adapter.out.cache` — Cache adapters (EMPTY — custom cache code deleted)
- `com.ntt.authservice.auth.adapter.out.cipher` — E2EE cipher adapters (RedisCipherKeySessionResolver, RedisAntiReplayValidator, +3 more)
- `com.ntt.authservice.auth.adapter.out.event` — Event publishing adapters (3 files)
- `com.ntt.authservice.auth.adapter.out.gateway` — External gateway adapters (1 file)
- `com.ntt.authservice.auth.adapter.out.http` — HTTP client adapters (CaptchaClient, SsoProviderClient, +2 more)
- `com.ntt.authservice.auth.adapter.out.notification` — Notification adapters (2 files)
- `com.ntt.authservice.auth.adapter.out.persistence` — JPA persistence adapters (8 files)
- `com.ntt.authservice.auth.adapter.out.sso` — SSO adapters (1 file)
- `com.ntt.authservice.auth.application` — Application services (36 files: JwtService, LoginSessionService, MfaService, OtpService, PasswordPolicyService, TokenBlacklistCacheService, SessionPromotionService, AnonymousSessionDataService, etc.)
- `com.ntt.authservice.auth.application.command` — Command handlers (16 files)
- `com.ntt.authservice.auth.application.query` — Query handlers (2 files: BuildAuthResponseHandler, BuildAuthResponseQuery)
- `com.ntt.authservice.auth.application.event` — Domain events (4 files)
- `com.ntt.authservice.auth.application.port.out` — Output ports (9 interfaces: CaptchaGateway, DomainPort, EventPublisher, EventStorePort, NotificationGateway, OutboxPort, SsoGateway, TokenStore, UserPort)
- `com.ntt.authservice.auth.application.cipher` — Cipher services (4 files: DecryptionVaultService, etc.)
- `com.ntt.authservice.auth.domain` — Domain entities (3 files)
- `com.ntt.authservice.rbac.adapter.in.web` — RBAC REST Controllers (RbacControllers, RolePermissionController)
- `com.ntt.authservice.rbac.adapter.out.persistence` — RBAC JPA persistence
- `com.ntt.authservice.rbac.application` — RBAC services (RbacEngine)
- `com.ntt.authservice.rbac.application.query` — RBAC Query handlers (GetPermissionsHandler, GetUserRolesHandler, CheckPermissionHandler, RbacResolver + queries)
- `com.ntt.authservice.rbac.domain` — RBAC domain entities (2 files)
- `com.ntt.authservice.pbac.adapter.in.web` — PBAC REST Controller (PolicyController)
- `com.ntt.authservice.pbac` — Policy-Based Access Control
- `com.ntt.authservice.shared.audit` — Audit utilities (3 files)
- `com.ntt.authservice.shared.cache` — Cache (EMPTY — custom code deleted)
- `com.ntt.authservice.shared.config` — Configuration classes (SecurityConfig, RedisConfig, I18nConfig, JacksonConfig, KafkaConfig, HttpClientConfig, ObservabilityConfig, JpaAuditingConfig, OutboxProperties, RedisLuaScriptConfig, SecurityProperties)
- `com.ntt.authservice.shared.exception` — Exception handling (AuthErrorCode, AuthException hierarchy, GlobalExceptionHandler)
- `com.ntt.authservice.shared.filter` — HTTP filters (IdempotencyFilter)
- `com.ntt.authservice.shared.i18n` — Internationalization (DatabaseMessageSource, I18nMessageRepository, I18nMessage entity)
- `com.ntt.authservice.shared.persistence` — Shared persistence (1 file)
- `com.ntt.authservice.shared.security` — Security filters (JwtAuthFilter)

### Not Found

- `com.ntt.authservice.shared.cache` — EMPTY (custom cache code deleted, migration target achieved)
- `com.ntt.authservice.auth.adapter.out.cache` — EMPTY (custom cache adapters deleted)

### Naming Convention

- Controllers: `<Feature>Controller.kt` (e.g., `TokenController`, `SessionController`)
- Handlers: `<Action><Entity>Handler.kt` (e.g., `GetPermissionsHandler`, `BuildAuthResponseHandler`)
- Services: `<Feature>Service.kt` (e.g., `JwtService`, `MfaService`, `TokenBlacklistCacheService`)
- Ports: `<Entity>Port.kt` or `<Entity>Gateway.kt` (e.g., `TokenStore`, `CaptchaGateway`)
- DTOs: `<Feature>Dtos.kt` containing multiple data classes (e.g., `MfaDtos.kt`, `TokenDtos.kt`)
- Config: `<Feature>Config.kt` (e.g., `SecurityConfig`, `RedisConfig`, `KafkaConfig`)
- Exceptions: `<Feature>Exceptions.kt` containing multiple exception classes (e.g., `AuthCoreExceptions.kt`)
- Error codes: Enum `AuthErrorCode` with pattern `AUTH_XXX`
- Kafka consumers: `<Event>Consumer.kt` (e.g., `PermissionChangedConsumer`)
