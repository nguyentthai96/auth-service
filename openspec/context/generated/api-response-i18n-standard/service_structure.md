# Service Structure

_Generated: 2026-08-25_

## auth-service

### Detected Packages

- `com.ntt.authservice.shared.config` — Configuration classes (I18nConfig, SecurityConfig, SecurityProperties)
- `com.ntt.authservice.shared.exception` — Exception hierarchy (AuthException, AuthErrorCode, AuthControllerAdvice/GlobalExceptionHandler, AuthCoreExceptions, CipherExceptions, AnonymousExceptions)
- `com.ntt.authservice.shared.i18n` — i18n infrastructure (DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository)
- `com.ntt.authservice.shared.security` — Security filters (JwtAuthFilter)
- `com.ntt.authservice.shared.filter` — Cross-cutting filters (IdempotencyFilter)
- `com.ntt.authservice.shared.cache` — Cache abstractions (AbstractTwoTierCache)
- `com.ntt.authservice.shared.persistence` — Persistence utilities
- `com.ntt.authservice.shared.audit` — Audit infrastructure
- `com.ntt.authservice.auth.domain.model` — Domain models
- `com.ntt.authservice.auth.domain.model.vo` — Value objects
- `com.ntt.authservice.auth.domain.service` — Domain services
- `com.ntt.authservice.auth.application` — Application services (AuthService, JwtService, MfaService, LoginSessionService, etc.)
- `com.ntt.authservice.auth.application.command` — CQRS commands + handlers (LoginHandler, RegisterHandler, etc.)
- `com.ntt.authservice.auth.application.query` — CQRS queries + handlers (BuildAuthResponseQuery/Handler)
- `com.ntt.authservice.auth.application.event` — Domain events
- `com.ntt.authservice.auth.application.cipher` — E2EE cipher services
- `com.ntt.authservice.auth.application.port.out` — Output ports
- `com.ntt.authservice.auth.adapter.in.web` — REST controllers (CqrsAuthController, SessionController, MfaController, etc.)
- `com.ntt.authservice.auth.adapter.in.web.dto` — Request/Response DTOs
- `com.ntt.authservice.auth.adapter.in.web.filter` — Web filters (LoginRateLimitFilter, ClientMetadataFilter, ContentLanguageFilter, ServiceAuthFilter)
- `com.ntt.authservice.auth.adapter.in.kafka` — Kafka consumers
- `com.ntt.authservice.auth.adapter.out.http` — HTTP clients (CaptchaClient, HttpCaptchaGateway)
- `com.ntt.authservice.auth.adapter.out.sso` — SSO adapters (OAuth2TokenExchanger)
- `com.ntt.authservice.auth.adapter.out.cipher` — Cipher adapters (RedisAntiReplayValidator, RedisCipherKeySessionResolver)
- `com.ntt.authservice.auth.adapter.out.cache` — Cache adapters (MultiTierPermissionCache)
- `com.ntt.authservice.auth.adapter.out.gateway` — External gateways
- `com.ntt.authservice.auth.adapter.out.persistence` — JPA persistence
- `com.ntt.authservice.auth.adapter.out.persistence.entity` — JPA entities
- `com.ntt.authservice.auth.adapter.out.persistence.mapper` — Entity mappers
- `com.ntt.authservice.auth.adapter.out.persistence.repository` — Spring Data repositories
- `com.ntt.authservice.auth.adapter.out.event` — Event publishers
- `com.ntt.authservice.rbac.domain.model` — RBAC domain models
- `com.ntt.authservice.rbac.domain.service` — RBAC domain services
- `com.ntt.authservice.rbac.application.query` — RBAC query handlers
- `com.ntt.authservice.rbac.adapter.in.web` — RBAC REST controllers (RbacControllers, RolePermissionController)
- `com.ntt.authservice.rbac.adapter.out.persistence` — RBAC persistence
- `com.ntt.authservice.pbac.domain.model` — PBAC domain models
- `com.ntt.authservice.pbac.adapter.in.web` — PBAC REST controllers (PolicyController)
- `com.ntt.authservice.pbac.adapter.out.persistence` — PBAC persistence

### Not Found

- `com.ntt.authservice.auth.adapter.in.web.controller` — controllers are directly in `web/` package (not in sub-package)

### Naming Convention

- Controllers: `{Feature}Controller.kt` (e.g., `SessionController.kt`, `MfaController.kt`)
- CQRS: `{Feature}Handler.kt` (command), `{Feature}Query.kt` / `{Feature}Handler.kt` (query)
- Filters: `{Feature}Filter.kt` (e.g., `LoginRateLimitFilter.kt`, `ContentLanguageFilter.kt`)
- DTOs: `{Feature}Dtos.kt` files containing multiple data classes (e.g., `MfaDtos.kt`, `AnonymousDtos.kt`)
- Exceptions: `{Domain}Exceptions.kt` files (e.g., `AuthCoreExceptions.kt`, `CipherExceptions.kt`, `AnonymousExceptions.kt`)
- Configuration: `{Feature}Config.kt` (e.g., `I18nConfig.kt`, `SecurityConfig.kt`)
- Language: Kotlin (100%)
- Architecture: Hexagonal (ports & adapters) with CQRS in auth module
