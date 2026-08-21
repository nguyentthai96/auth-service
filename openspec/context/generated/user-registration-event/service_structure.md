# Service Structure

_Generated: 2025-08-21_

## auth-service

### Detected Packages

Architecture: **Hexagonal (Ports & Adapters)** with **CQRS** pattern

#### auth module (`com.ntt.authservice.auth`)
- `adapter/in/web/` — REST controllers (CqrsAuthController, AuthController, MfaController, SsoController, etc.)
- `adapter/in/web/dto/` — Request/Response DTOs (RegisterRequestDto, LoginRequestDto, AuthResponse, etc.)
- `adapter/in/web/filter/` — HTTP filters (ClientMetadataFilter, ContentLanguageFilter, LoginRateLimitFilter, ServiceAuthFilter)
- `adapter/in/kafka/` — Kafka consumers (PermissionChangedConsumer)
- `adapter/out/event/` — Event publisher adapters (KafkaEventPublisher, SpringEventPublisher)
- `adapter/out/cache/` — Cache adapters (MultiTierPermissionCache, CaffeinePermissionCache, InMemoryPermissionCache)
- `adapter/out/cipher/` — Encryption adapters (TinkCipherAlgorithmFactory, RedisCipherKeySessionResolver, HmacFieldSigningServiceImpl, RedisAntiReplayValidator)
- `adapter/out/http/` — HTTP client adapters (CaptchaClient, SsoProviderClient, HttpCaptchaGateway, HttpSsoGateway)
- `adapter/out/gateway/` — Gateway adapters (CaptchaGatewayAdapter)
- `adapter/out/sso/` — SSO adapters (OAuth2TokenExchanger)
- `adapter/out/persistence/` — JPA persistence adapters (UserPersistenceAdapter, DomainPersistenceAdapter, TokenStorePersistenceAdapter)
- `adapter/out/persistence/entity/` — JPA entities (LoginSessionEntity, CipherKeySessionEntity, AccountDataExportEntity, AccountDeletionRequestEntity, VaultAccessLogEntity)
- `adapter/out/persistence/mapper/` — Entity-Domain mappers (UserEntityMapper)
- `adapter/out/persistence/repository/` — JPA repositories (LoginSessionRepository, CipherKeySessionJpaRepository, AccountDataExportRepository, etc.)
- `application/` — Application services (AuthService, JwtService, MfaService, SsoAdapter, AccountLifecycleService, etc.)
- `application/command/` — CQRS commands (RegisterCommand, LoginCommand, RefreshTokenCommand, etc.)
- `application/query/` — CQRS queries (BuildAuthResponseQuery)
- `application/event/` — Spring application events (RateLimitExceededEvent, NewDeviceLoginEvent)
- `application/port/out/` — Outbound ports (EventPublisher, UserPort, DomainPort, TokenStore, PermissionCache, CaptchaGateway, SsoGateway)
- `application/cipher/` — Cipher application services (CipherVersionNegotiator, DecryptionVaultService, EncryptedAuditService, X25519KeyExchangeServiceImpl)
- `domain/model/` — Domain models (User, UserStatus, AuthToken)
- `domain/model/vo/` — Value objects (UserId, PasswordHash, Email, DomainCode)
- `domain/service/` — Domain services (TokenHasher)

#### rbac module (`com.ntt.authservice.rbac`)
- `adapter/in/web/` — RBAC controllers (RbacControllers, RolePermissionController)
- `adapter/out/persistence/entity/` — Entities (UserEntity, UserIdentityEntity, RbacEntities, PermissionEntities, PasswordHistoryEntity, PasswordPolicyEntity)
- `adapter/out/persistence/mapper/` — Mappers (PermissionMapper)
- `adapter/out/persistence/repository/` — Repositories
- `application/` — RBAC engine (RbacEngine)
- `application/query/` — CQRS queries (CheckPermissionQuery/Handler, GetPermissionsQuery/Handler, GetUserRolesQuery/Handler, RbacResolver)
- `domain/model/` — Domain models (Role, Permission)
- `domain/service/` — NOT DETECTED

#### pbac module (`com.ntt.authservice.pbac`)
- `adapter/in/web/` — Policy controllers (PolicyController)
- `adapter/out/persistence/entity/` — Entities (PolicyEntities)
- `adapter/out/persistence/repository/` — Repositories (PolicyRepository)
- `application/` — Policy evaluation (PolicyEvaluator)

#### shared module (`com.ntt.authservice.shared`)
- `config/` — Configuration classes (SecurityConfig, RedisConfig, KafkaConfig, JacksonConfig, JpaAuditingConfig, I18nConfig, HttpClientConfig, SecurityProperties, RedisLuaScriptConfig)
- `cache/` — Abstract cache (AbstractTwoTierCache)
- `exception/` — Exception hierarchy (AuthException, AuthErrorCode, GlobalExceptionHandler, AuthExceptions, AuthCoreExceptions, AnonymousExceptions, CipherExceptions)
- `security/` — Security filters (JwtAuthFilter)
- `i18n/` — Internationalization (I18nMessageEntity, DatabaseMessageSource, I18nMessageRepository)
- `persistence/` — Base entities (VersionedAuditableEntity)
- `filter/` — HTTP filters (IdempotencyFilter)
- `audit/` — Audit logging (AuditLogService, AuditAction)

### Not Found

- `factory/` — No factory package detected (no Factory pattern classes)
- `saga/` — No saga package detected (no Saga orchestration)
- `projection/` — No projection package detected (no CQRS read-model projections yet)
- `eventstore/` — No event store package detected (events are fire-and-forget)

### Naming Convention

- **Controllers**: `<Feature>Controller` (e.g., `CqrsAuthController`, `MfaController`)
- **Command Handlers**: `<Action>Handler` (e.g., `RegisterHandler`, `LoginHandler`)
- **Commands**: `<Action>Command` (e.g., `RegisterCommand`, `LoginCommand`)
- **DTOs**: `<Action>RequestDto`, `<Action>Response` (e.g., `RegisterRequestDto`, `AuthResponse`)
- **Ports**: `<Entity>Port` (e.g., `UserPort`, `DomainPort`)
- **Adapters**: `<Impl>Adapter` or `<Protocol><Feature>` (e.g., `UserPersistenceAdapter`, `KafkaEventPublisher`)
- **Events**: `<Entity><Action>Event` (e.g., `UserRegisteredEvent`, `AccountDeactivatedEvent`)
- **Entities**: `<Name>Entity` (e.g., `LoginSessionEntity`, `UserEntity`)
- **Repositories**: `<Name>Repository` (e.g., `LoginSessionRepository`)
- **Configuration**: `<Feature>Config` (e.g., `RedisConfig`, `KafkaConfig`)
- **Exceptions**: `<Condition>Exception` (e.g., `InvalidCredentialsException`, `DuplicateResourceException`)
- **Error Codes**: `AuthErrorCode` enum with pattern `AUTH_NNN` (e.g., `AUTH_001`, `AUTH_020`)
