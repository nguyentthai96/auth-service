# Service Structure

_Generated: 2026-08-26_

## auth-service

### Detected Packages

- `com.ntt.authservice.auth.domain.event`: Domain events — TokenIssuedEvent, TokenRevokedEvent, EventEnvelope, IssuanceContext, RevocationType
- `com.ntt.authservice.auth.domain.model`: Domain models — User, AuthToken, UserStatus, TokenIssuanceMetadata
- `com.ntt.authservice.auth.domain.model.vo`: Value objects — UserId, PasswordHash, Email, DomainCode
- `com.ntt.authservice.auth.domain.service`: Domain services — TokenHasher
- `com.ntt.authservice.auth.application`: Application services — JwtService, AuthService, MfaService, OtpService, SessionPromotionService, etc.
- `com.ntt.authservice.auth.application.command`: Command handlers — LoginHandler, RegisterHandler, RefreshTokenHandler, TokenGenerator, RevokeSessionsHandler, etc.
- `com.ntt.authservice.auth.application.event`: Event services — EventService, TokenEventRecorder, RateLimitExceededEvent, NewDeviceLoginEvent
- `com.ntt.authservice.auth.application.query`: Query handlers — BuildAuthResponseHandler
- `com.ntt.authservice.auth.application.port.out`: Outbound ports (interfaces) — TokenStore, EventStorePort, OutboxPort, UserPort, DomainPort, SsoGateway, CaptchaGateway, EventPublisher, PermissionCache
- `com.ntt.authservice.auth.application.cipher`: Cipher services — EncryptedAuditService, X25519KeyExchangeServiceImpl, DecryptionVaultService
- `com.ntt.authservice.auth.adapter.in.web`: REST controllers — TokenController, AuthController, etc.
- `com.ntt.authservice.auth.adapter.in.web.dto`: Request/Response DTOs — IntrospectionRequest, IntrospectionResponse, TokenDtos, etc.
- `com.ntt.authservice.auth.adapter.in.web.filter`: Web filters — LoginRateLimitFilter, ServiceAuthFilter
- `com.ntt.authservice.auth.adapter.in.kafka`: Kafka consumers — PermissionChangedConsumer
- `com.ntt.authservice.auth.adapter.out.persistence`: JPA adapters — TokenStorePersistenceAdapter, UserPersistenceAdapter, OutboxPersistenceAdapter, DomainPersistenceAdapter
- `com.ntt.authservice.auth.adapter.out.persistence.entity`: JPA entities — EventStoreEntity, EventOutboxEntity, LoginSessionEntity, etc.
- `com.ntt.authservice.auth.adapter.out.persistence.repository`: JPA repositories — LoginSessionRepository, EventOutboxJpaRepository, etc.
- `com.ntt.authservice.auth.adapter.out.persistence.mapper`: Entity mappers — UserEntityMapper
- `com.ntt.authservice.auth.adapter.out.event`: Event publishers — OutboxPoller, SpringEventPublisher, KafkaEventPublisher
- `com.ntt.authservice.auth.adapter.out.http`: HTTP clients — HttpCaptchaGateway, HttpSsoGateway, SsoProviderClient, CaptchaClient
- `com.ntt.authservice.auth.adapter.out.gateway`: Gateway adapters — CaptchaGatewayAdapter
- `com.ntt.authservice.auth.adapter.out.sso`: SSO adapters — OAuth2TokenExchanger
- `com.ntt.authservice.auth.adapter.out.cache`: Cache adapters (empty — reserved for cache implementations)
- `com.ntt.authservice.auth.adapter.out.cipher`: Cipher adapters — RedisAntiReplayValidator, RedisCipherKeySessionResolver, etc.
- `com.ntt.authservice.rbac.domain.model`: RBAC domain models
- `com.ntt.authservice.rbac.domain.service`: RBAC domain services
- `com.ntt.authservice.rbac.application`: RBAC application services
- `com.ntt.authservice.rbac.application.query`: RBAC query handlers — GetPermissionsHandler
- `com.ntt.authservice.rbac.adapter.in.web`: RBAC controllers
- `com.ntt.authservice.rbac.adapter.out.persistence.entity`: RBAC entities — TokenBlacklistEntity, RefreshTokenEntity, PermissionEntities
- `com.ntt.authservice.rbac.adapter.out.persistence.repository`: RBAC repositories — TokenBlacklistRepository, RefreshTokenRepository
- `com.ntt.authservice.rbac.adapter.out.persistence.mapper`: RBAC mappers
- `com.ntt.authservice.pbac.application`: PBAC application services — policy evaluation
- `com.ntt.authservice.pbac.adapter.in.web`: PBAC controllers
- `com.ntt.authservice.pbac.adapter.out.persistence`: PBAC persistence
- `com.ntt.authservice.shared.config`: Configuration — SecurityConfig, SecurityProperties, RedisConfig, KafkaConfig, JacksonConfig, I18nConfig, HttpClientConfig, JpaAuditingConfig, ObservabilityConfig, OutboxProperties, RedisLuaScriptConfig
- `com.ntt.authservice.shared.security`: Security — JwtAuthFilter
- `com.ntt.authservice.shared.exception`: Exceptions — AuthException hierarchy, AuthErrorCode, GlobalExceptionHandler
- `com.ntt.authservice.shared.filter`: Filters — IdempotencyFilter
- `com.ntt.authservice.shared.i18n`: I18n — DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository
- `com.ntt.authservice.shared.persistence`: Base entities — VersionedAuditableEntity
- `com.ntt.authservice.shared.audit`: Audit — AuditLogEntity, AuditLogService, AuditLogRepository
- `com.ntt.authservice.shared.cache`: Cache (empty — reserved for cache abstractions)

### Not Found

- `factory/` package (no dedicated factory package — factory logic embedded in command handlers)

### Naming Convention

- Controllers: `*Controller` (e.g., TokenController, AuthController)
- Services: `*Service` (e.g., JwtService, AuthService, EventService)
- Handlers: `*Handler` (e.g., LoginHandler, RefreshTokenHandler)
- Commands: `*Command` (e.g., LoginCommand, RefreshTokenCommand)
- Ports: `*Port`, `*Store`, `*Gateway`, `*Cache` (e.g., TokenStore, EventStorePort, SsoGateway)
- Adapters: `*PersistenceAdapter`, `*Gateway` impl (e.g., TokenStorePersistenceAdapter, HttpSsoGateway)
- Entities: `*Entity` (e.g., TokenBlacklistEntity, EventStoreEntity)
- DTOs: `*Request`, `*Response` (e.g., IntrospectionRequest, IntrospectionResponse)
- Events: `*Event` (e.g., TokenIssuedEvent, TokenRevokedEvent)
- Enums: Descriptive (e.g., IssuanceContext, RevocationType, AuthErrorCode)
- Config: `*Config`, `*Properties` (e.g., SecurityConfig, SecurityProperties)
- Filters: `*Filter` (e.g., JwtAuthFilter, IdempotencyFilter)
- Language: Kotlin
- Architecture: Hexagonal (Clean Architecture) — domain/application/adapter layers
