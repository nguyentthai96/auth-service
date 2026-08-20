# Service Structure

_Generated: 2026-08-22_

## auth-service

### Detected Packages

- `com.ntt.authservice` — Root package
  - `auth/adapter/in/web` — REST controllers (Clean Architecture inbound adapter)
  - `auth/adapter/in/web/dto` — Request/Response DTOs
  - `auth/adapter/in/web/filter` — Servlet filters (ClientMetadataFilter, ContentLanguageFilter, LoginRateLimitFilter)
  - `auth/adapter/in/kafka` — Kafka consumer adapter
  - `auth/adapter/out/cache` — Cache adapters (InMemoryPermissionCache, CaffeinePermissionCache, MultiTierPermissionCache)
  - `auth/adapter/out/cipher` — E2EE cipher adapters (RedisAntiReplayValidator)
  - `auth/adapter/out/event` — Event publishing adapter
  - `auth/adapter/out/gateway` — External gateway adapter
  - `auth/adapter/out/http` — HTTP client adapters (CaptchaClient, HttpCaptchaGateway)
  - `auth/adapter/out/persistence` — JPA persistence adapters
  - `auth/adapter/out/sso` — SSO adapter (OAuth2TokenExchanger)
  - `auth/application` — Application services (JwtService, LoginSessionService, PasswordPolicyService, OtpService, MfaRateLimitService, etc.)
  - `auth/application/command` — CQRS command handlers (LoginHandler, RegisterHandler, etc.)
  - `auth/application/query` — CQRS query handlers (BuildAuthResponseHandler)
  - `auth/application/cipher` — Cipher application services (X25519KeyExchangeServiceImpl, DecryptionVaultService)
  - `auth/application/port/out` — Output port interfaces (PermissionCache)
  - `auth/domain/model` — Domain models (AuthToken)
  - `auth/domain/entity` — Domain entities
  - `shared/config` — Configuration classes (I18nConfig, SecurityConfig, SecurityProperties, RedisConfig, JacksonConfig, JpaAuditingConfig, HttpClientConfig)
  - `shared/exception` — Exception hierarchy + ControllerAdvice (AuthException, AuthErrorCode, GlobalExceptionHandler, CipherExceptions, AuthCoreExceptions, AnonymousExceptions)
  - `shared/i18n` — i18n infrastructure (DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository)
  - `shared/audit` — Audit infrastructure
  - `shared/persistence` — Shared persistence
  - `shared/security` — Security infrastructure
  - `rbac/adapter` — RBAC adapters
  - `rbac/application` — RBAC application services
  - `rbac/domain` — RBAC domain model
  - `pbac/adapter` — PBAC adapters
  - `pbac/application` — PBAC application services

### Not Found

- `factory/` — NOT FOUND (handlers are injected directly)
- `service/` — NOT FOUND as separate package (services are in `application/`)

### Naming Convention

- Controllers: `*Controller.kt` (e.g., `CqrsAuthController`, `MfaController`, `SessionController`)
- Handlers: `*Handler.kt` (e.g., `LoginHandler`, `RegisterHandler`)
- Services: `*Service.kt` (e.g., `JwtService`, `LoginSessionService`, `PasswordPolicyService`)
- Filters: `*Filter.kt` (e.g., `ClientMetadataFilter`, `ContentLanguageFilter`)
- DTOs: `*RequestDto.kt`, `*Response.kt`, `*Dtos.kt`
- Exceptions: `*Exception.kt` (e.g., `AuthException`, `InvalidCredentialsException`)
- Entities: `*Entity.kt` (e.g., `I18nMessageEntity`)
- Repositories: `*Repository.kt` (e.g., `I18nMessageRepository`)
- Config: `*Config.kt` or `*Properties.kt` (e.g., `I18nConfig`, `SecurityProperties`)
- Error codes: `*ErrorCode.kt` as enum (e.g., `AuthErrorCode`)
- Architecture: Clean Architecture (adapter/application/domain) with CQRS (command/query)
