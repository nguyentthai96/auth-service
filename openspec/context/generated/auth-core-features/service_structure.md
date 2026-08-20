# Service Structure

_Generated: 2026-08-20_

## auth-service

### Detected Packages

#### auth module (primary — auth core features)
- `auth/domain/model`: Domain model — `User.kt`, `AuthToken.kt`, `UserStatus.kt` (sealed interface)
- `auth/domain/model/vo`: Value objects — `DomainCode.kt`, `Email.kt`, `PasswordHash.kt`, `UserId.kt`
- `auth/domain/service`: Domain services — `TokenHasher.kt`
- `auth/application`: Application services — `AuthService.kt`, `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `SsoAdapter.kt`, `PasswordPolicyService.kt`, `CaptchaVerifier.kt` (interface), `AltchaCaptchaVerifier.kt`, `MfaRateLimitService.kt`, `LoginRateLimitService.kt`, `LoginSessionService.kt`, `JwtService.kt`, `SessionPolicyService.kt`, `SessionPromotionService.kt`, `AccountLifecycleService.kt`, `DomainLookupService.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionCleanupScheduler.kt`, `LoginResult.kt`, `RegisterResult.kt`, `PromotionResult.kt`, `AnonymousSessionResult.kt`
- `auth/application/command`: CQRS commands — `LoginCommand.kt`/`LoginHandler.kt`, `RegisterCommand.kt`/`RegisterHandler.kt`, `RefreshTokenCommand.kt`/`RefreshTokenHandler.kt`, `SwitchDomainCommand.kt`/`SwitchDomainHandler.kt`, `RevokeSessionsCommand.kt`/`RevokeSessionsHandler.kt`, `TokenGenerator.kt`, `CreateAnonymousSessionCommand.kt`/`AnonymousSessionHandler.kt`, `RenewAnonymousTokenCommand.kt`/`RenewAnonymousTokenHandler.kt`, `AuthDomainEvents.kt`
- `auth/application/query`: CQRS queries — `BuildAuthResponseQuery.kt`/`BuildAuthResponseHandler.kt`
- `auth/application/port/out`: Outbound ports (hexagonal) — `UserPort.kt`, `TokenStore.kt`, `DomainPort.kt`, `EventPublisher.kt`, `CaptchaGateway.kt`, `SsoGateway.kt`, `PermissionCache.kt`
- `auth/application/cipher`: E2EE services — `CipherVersionNegotiator.kt`, `DecryptionVaultService.kt`, `EncryptedAuditService.kt`, `X25519KeyExchangeServiceImpl.kt`
- `auth/application/event`: Domain events — `NewDeviceLoginEvent.kt`, `RateLimitExceededEvent.kt`
- `auth/adapter/in/web`: Inbound web controllers (12 controllers)
- `auth/adapter/in/web/dto`: DTOs — `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`, `RequestDtos.kt`, `AuthResponse.kt`, `AnonymousDtos.kt`
- `auth/adapter/in/web/filter`: Web filters — `LoginRateLimitFilter.kt`, `ClientMetadataFilter.kt`, `ContentLanguageFilter.kt`
- `auth/adapter/in/kafka`: Kafka consumer — `PermissionChangedConsumer.kt`
- `auth/adapter/out/sso`: SSO adapter — `OAuth2TokenExchanger.kt`
- `auth/adapter/out/http`: HTTP clients — `HttpCaptchaGateway.kt`, `HttpSsoGateway.kt`, `CaptchaClient.kt` (interface), `SsoProviderClient.kt` (interface)
- `auth/adapter/out/gateway`: Gateway adapters — `CaptchaGatewayAdapter.kt`
- `auth/adapter/out/cache`: Cache adapters — `CaffeinePermissionCache.kt`, `InMemoryPermissionCache.kt`, `MultiTierPermissionCache.kt`
- `auth/adapter/out/event`: Event publisher — `SpringEventPublisher.kt`
- `auth/adapter/out/cipher`: E2EE adapters — `HmacFieldSigningServiceImpl.kt`, `RedisAntiReplayValidator.kt`, `RedisCipherKeySessionResolver.kt`, `TinkCipherAlgorithmFactory.kt`
- `auth/adapter/out/persistence`: Persistence adapters — `UserPersistenceAdapter.kt`, `TokenStorePersistenceAdapter.kt`, `DomainPersistenceAdapter.kt`
- `auth/adapter/out/persistence/entity`: Persistence entities — `LoginSessionEntity.kt`, `CipherKeySessionEntity.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt`, `VaultAccessLogEntity.kt`
- `auth/adapter/out/persistence/mapper`: Mappers — `UserEntityMapper.kt`
- `auth/adapter/out/persistence/repository`: Repositories — `LoginSessionRepository.kt`, `CipherKeySessionJpaRepository.kt`, `AccountDeletionRequestRepository.kt`, `AccountDataExportRepository.kt`, `VaultAccessLogJpaRepository.kt`

#### rbac module
- `rbac/domain/model`: Domain model — `Permission.kt`, `Role.kt`
- `rbac/domain/service`: Domain services — (none detected in listing)
- `rbac/application`: Application services — `RbacEngine.kt`
- `rbac/application/query`: CQRS queries — `CheckPermissionQuery.kt`/`CheckPermissionHandler.kt`, `GetPermissionsQuery.kt`/`GetPermissionsHandler.kt`, `GetUserRolesQuery.kt`/`GetUserRolesHandler.kt`, `RbacResolver.kt`
- `rbac/adapter/in/web`: Controllers — `RbacControllers.kt` (5 controllers), `RolePermissionController.kt`
- `rbac/adapter/out/persistence/entity`: Entities — `UserEntity.kt`, `UserIdentityEntity.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`, `RbacEntities.kt` (DomainEntity, UserDomainEntity, GroupEntity, UserGroupEntity, DomainRoleEntity, GroupRoleEntity), `PermissionEntities.kt` (ActionEntity, DomainResourceEntity, PermissionEntity, RolePermissionEntity, RefreshTokenEntity, TokenBlacklistEntity)
- `rbac/adapter/out/persistence/mapper`: Mappers — `PermissionMapper.kt`
- `rbac/adapter/out/persistence/repository`: Repositories — `Repositories.kt` (16 repository interfaces)

#### pbac module
- `pbac/application`: Application services — `PolicyEvaluator.kt`
- `pbac/adapter/in/web`: Controllers — `PolicyController.kt`
- `pbac/adapter/out/persistence/entity`: Entities — `PolicyEntities.kt` (PolicyEntity, PolicyConditionEntity)
- `pbac/adapter/out/persistence/repository`: Repositories — `PolicyRepository.kt` (PolicyJpaRepository)

#### shared module
- `shared/config`: Configuration — `SecurityConfig.kt`, `SecurityProperties.kt`, `RedisConfig.kt`, `HttpClientConfig.kt`, `JacksonConfig.kt`, `JpaAuditingConfig.kt`, `I18nConfig.kt`
- `shared/security`: Security filter — `JwtAuthFilter.kt`
- `shared/exception`: Exception handling — `AuthExceptions.kt`, `AuthCoreExceptions.kt`, `AnonymousExceptions.kt`, `CipherExceptions.kt`, `AuthErrorCode.kt` (enum, 45 codes), `GlobalExceptionHandler.kt`
- `shared/audit`: Audit logging — `AuditLogService.kt` (AuditLogService + AuditAction enum, 25 actions)
- `shared/persistence`: Entity base — `VersionedAuditableEntity.kt`
- `shared/i18n`: Internationalization — `DatabaseMessageSource.kt`, `I18nMessageEntity.kt`, `I18nMessageRepository.kt`

### Not Found

- `factory/` — No explicit factory package (CAPTCHA uses interface-based plugin pattern instead)
- `mapper/` in auth module — UserEntityMapper is in adapter/out/persistence/mapper

### Naming Convention

- Controllers: `{Name}Controller.kt` — @RestController, located in `adapter/in/web`
- Services: `{Name}Service.kt` — @Service, located in `application`
- Commands: `{Name}Command.kt` + `{Name}Handler.kt` — CQRS pattern, located in `application/command`
- Queries: `{Name}Query.kt` + `{Name}Handler.kt` — CQRS pattern, located in `application/query`
- Entities: `{Name}Entity.kt` — @Entity, extends SnowflakePersistentAuditableEntity or SnowflakeBaseEntity
- Repositories: `{Name}Repository.kt` — interface extends JpaRepository
- DTOs: `{Name}Dtos.kt` or `{Name}Request/Response` — Kotlin data classes
- Ports: interface in `application/port/out/` — `{Name}Port.kt` or `{Name}Gateway.kt`
- Adapters: `{Name}Adapter.kt` or `{Name}PersistenceAdapter.kt`
- Exceptions: `{Module}Exceptions.kt` — grouped by module
- Value Objects: Located in `domain/model/vo/` — simple data classes
