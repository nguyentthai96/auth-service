# Service Structure

_Generated: 2026-08-25_

## auth-service

### Detected Packages

**auth module** (primary — `com.ntt.authservice.auth`):
- `auth/application`: Core services — AuthService, MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService, CaptchaVerifier, AltchaCaptchaVerifier, MfaRateLimitService, LoginRateLimitService, AnonymousRateLimitService, LoginSessionService, JwtService, SessionPolicyService, AccountLifecycleService, DomainLookupService, ServiceTokenService, SessionPromotionService, AnonymousSessionDataService, AnonymousSessionResult
- `auth/application/command`: CQRS command handlers — LoginHandler, RegisterHandler, RefreshTokenHandler, SwitchDomainHandler, RevokeSessionsHandler, TokenGenerator, AnonymousSessionHandler, RenewAnonymousTokenHandler
- `auth/application/query`: CQRS query handlers — BuildAuthResponseQuery/Handler
- `auth/application/port/out`: Hexagonal outbound ports — UserPort, TokenStore, DomainPort, EventPublisher, CaptchaGateway, SsoGateway, PermissionCache (7 ports)
- `auth/application/event`: Domain events — NewDeviceLoginEvent, RateLimitExceededEvent
- `auth/application/cipher`: Cipher services — CipherVersionNegotiator, DecryptionVaultService, EncryptedAuditService, X25519KeyExchangeServiceImpl
- `auth/adapter/in/web`: REST controllers — 13 controllers
- `auth/adapter/in/web/dto`: DTOs — MfaDtos, SsoDtos, TokenDtos, RequestDtos, AuthResponse, AnonymousDtos (25+ data classes)
- `auth/adapter/in/web/filter`: HTTP filters — LoginRateLimitFilter, ClientMetadataFilter, ContentLanguageFilter, ServiceAuthFilter
- `auth/adapter/in/kafka`: Kafka consumers — PermissionChangedConsumer
- `auth/adapter/out/sso`: SSO adapters — OAuth2TokenExchanger
- `auth/adapter/out/http`: HTTP clients — HttpCaptchaGateway, HttpSsoGateway, CaptchaClient, SsoProviderClient
- `auth/adapter/out/gateway`: Gateway adapters — CaptchaGatewayAdapter
- `auth/adapter/out/cache`: Cache adapters — CaffeinePermissionCache, InMemoryPermissionCache, MultiTierPermissionCache
- `auth/adapter/out/event`: Event adapters — SpringEventPublisher, KafkaEventPublisher
- `auth/adapter/out/persistence`: Persistence adapters — UserPersistenceAdapter, TokenStorePersistenceAdapter, DomainPersistenceAdapter
- `auth/adapter/out/persistence/entity`: JPA entities — LoginSessionEntity, CipherKeySessionEntity, AccountDeletionRequestEntity, AccountDataExportEntity, VaultAccessLogEntity
- `auth/adapter/out/persistence/mapper`: Entity mappers — UserEntityMapper
- `auth/adapter/out/persistence/repository`: JPA repositories — LoginSessionRepository, CipherKeySessionJpaRepository, VaultAccessLogJpaRepository, AccountDeletionRequestRepository, AccountDataExportRepository
- `auth/adapter/out/cipher`: Cipher adapters — TinkCipherAlgorithmFactory, RedisAntiReplayValidator, RedisCipherKeySessionResolver, HmacFieldSigningServiceImpl
- `auth/domain/model`: Domain models — User, AuthToken, UserStatus (sealed interface)
- `auth/domain/model/vo`: Value objects — DomainCode, Email, PasswordHash, UserId
- `auth/domain/service`: Domain services — TokenHasher

**rbac module** (cross-referenced — `com.ntt.authservice.rbac`):
- `rbac/application`: RbacEngine
- `rbac/application/query`: RBAC queries
- `rbac/domain/model`: RBAC domain models
- `rbac/domain/service`: RBAC domain services
- `rbac/adapter/in/web`: RbacControllers (5 controllers), RolePermissionController
- `rbac/adapter/out/persistence/entity`: 16 entity classes
- `rbac/adapter/out/persistence/mapper`: Entity mappers
- `rbac/adapter/out/persistence/repository`: Repositories.kt — 16 repository interfaces

**pbac module** (cross-referenced — `com.ntt.authservice.pbac`):
- `pbac/application`: PolicyEvaluator
- `pbac/adapter/in/web`: PolicyController
- `pbac/adapter/out/persistence/entity`: PolicyEntity, PolicyConditionEntity
- `pbac/adapter/out/persistence/repository`: PolicyRepository (PolicyJpaRepository)

**shared module** (`com.ntt.authservice.shared`):
- `shared/config`: SecurityConfig, SecurityProperties, RedisConfig, HttpClientConfig, JacksonConfig, JpaAuditingConfig, I18nConfig, KafkaConfig
- `shared/security`: JwtAuthFilter
- `shared/exception`: AuthExceptions, AuthCoreExceptions, AnonymousExceptions, CipherExceptions, AuthErrorCode, GlobalExceptionHandler
- `shared/audit`: AuditLogService, AuditAction
- `shared/persistence`: VersionedAuditableEntity
- `shared/i18n`: DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository
- `shared/cache`: AbstractTwoTierCache
- `shared/filter`: IdempotencyFilter

### Naming Convention

- Controllers: `*Controller.kt` (PascalCase, suffix `Controller`)
- Services: `*Service.kt` (PascalCase, suffix `Service`)
- Handlers: `*Handler.kt` (PascalCase, suffix `Handler` — CQRS)
- Repositories: `*Repository.kt` (PascalCase, suffix `Repository`)
- Entities: `*Entity.kt` (PascalCase, suffix `Entity`)
- DTOs: `*Dtos.kt` (grouped by feature — e.g., `MfaDtos.kt`, `SsoDtos.kt`)
- Data classes: `*Request`, `*Response`, `*Dto` (PascalCase)
- Ports: Interface name only (e.g., `UserPort`, `TokenStore`, `EventPublisher`)
- Adapters: `*Adapter.kt` or `*PersistenceAdapter.kt`
- Filters: `*Filter.kt`
- Events: `*Event.kt`
- Exceptions: `*Exception.kt` — all extend `AuthException`
- Error codes: `AuthErrorCode` enum — `AUTH_XXX` format

### Not Found

- Handler pattern (factory) — NOT FOUND (uses Spring DI `@Service` injection)
- `*Factory.kt` classes — NOT FOUND in auth-service
