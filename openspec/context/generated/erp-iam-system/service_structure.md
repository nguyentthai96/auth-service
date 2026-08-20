# Service Structure

_Generated: 2026-08-05 (refreshed)_

## auth-service (`com.ntt.authservice`)

### Detected Packages

| Layer | Package | Status | Key Files |
|-------|---------|--------|-----------|
| **auth module** | | | |
| Controller | `auth.adapter.in.web` | FOUND | `AuthController.kt`, `MfaController.kt`, `SsoController.kt`, `SessionController.kt`, `AdminSessionController.kt`, `TokenController.kt`, `CqrsAuthController.kt`, `CaptchaController.kt`, `AnonymousAuthController.kt`, `AccountLifecycleController.kt`, `KeyExchangeController.kt`, `RateLimitAdminController.kt` |
| DTO | `auth.adapter.in.web.dto` | FOUND | `RequestDtos.kt`, `AuthResponse.kt`, `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`, `AnonymousDtos.kt` |
| Filter | `auth.adapter.in.web.filter` | FOUND | 3 filter files |
| Kafka Consumer | `auth.adapter.in.kafka` | FOUND | `PermissionChangedConsumer.kt` |
| Persistence Entity | `auth.adapter.out.persistence.entity` | FOUND | `LoginSessionEntity.kt`, `CipherKeySessionEntity.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt`, `VaultAccessLogEntity.kt` |
| Repository | `auth.adapter.out.persistence.repository` | FOUND | `LoginSessionRepository.kt`, `CipherKeySessionJpaRepository.kt`, `AccountDeletionRequestRepository.kt`, `AccountDataExportRepository.kt`, `VaultAccessLogJpaRepository.kt` |
| Mapper | `auth.adapter.out.persistence.mapper` | FOUND | 1 mapper file |
| Persistence Adapter | `auth.adapter.out.persistence` | FOUND | `UserPersistenceAdapter.kt`, `DomainPersistenceAdapter.kt`, `TokenStorePersistenceAdapter.kt` |
| Cache | `auth.adapter.out.cache` | FOUND | `CaffeinePermissionCache.kt`, `InMemoryPermissionCache.kt`, `MultiTierPermissionCache.kt` |
| HTTP Client | `auth.adapter.out.http` | FOUND | `CaptchaClient.kt`, `HttpCaptchaGateway.kt`, `HttpSsoGateway.kt`, `SsoProviderClient.kt` |
| SSO | `auth.adapter.out.sso` | FOUND | `OAuth2TokenExchanger.kt` |
| Cipher | `auth.adapter.out.cipher` | FOUND | `TinkCipherAlgorithmFactory.kt`, `RedisAntiReplayValidator.kt`, `RedisCipherKeySessionResolver.kt`, `HmacFieldSigningServiceImpl.kt` |
| Gateway | `auth.adapter.out.gateway` | FOUND | `CaptchaGatewayAdapter.kt` |
| Event | `auth.adapter.out.event` | FOUND | `SpringEventPublisher.kt` |
| Application | `auth.application` | FOUND | `AuthService.kt`, `JwtService.kt`, `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `PasswordPolicyService.kt`, `SsoAdapter.kt`, `LoginSessionService.kt`, `SessionPolicyService.kt`, `DomainLookupService.kt`, `LoginRateLimitService.kt`, `MfaRateLimitService.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt`, `SessionCleanupScheduler.kt`, `AccountLifecycleService.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaVerifier.kt` |
| CQRS Commands | `auth.application.command` | FOUND | `LoginCommand.kt`, `LoginHandler.kt`, `RegisterCommand.kt`, `RegisterHandler.kt`, `RefreshTokenCommand.kt`, `RefreshTokenHandler.kt`, `AnonymousSessionHandler.kt`, `RenewAnonymousTokenHandler.kt`, `SwitchDomainHandler.kt`, `RevokeSessionsHandler.kt`, `TokenGenerator.kt`, `AuthDomainEvents.kt` |
| CQRS Queries | `auth.application.query` | FOUND | 2 query files |
| Cipher Application | `auth.application.cipher` | FOUND | `DecryptionVaultService.kt`, `CipherVersionNegotiator.kt`, `X25519KeyExchangeServiceImpl.kt` |
| Port | `auth.application.port` | FOUND | `EventPublisher.kt` (port interface) |
| Event | `auth.application.event` | FOUND | 2 event files |
| Domain Model | `auth.domain.model` | FOUND | 4 model files |
| Domain Service | `auth.domain.service` | FOUND | 1 domain service file |
| **rbac module** | | | |
| Controller | `rbac.adapter.in.web` | FOUND | `RbacControllers.kt` (DomainController, RoleController, GroupController, ResourceController, PermissionCheckController), `RolePermissionController.kt` |
| Entity | `rbac.adapter.out.persistence.entity` | FOUND | `RbacEntities.kt` (DomainEntity, UserDomainEntity, GroupEntity, UserGroupEntity, DomainRoleEntity, GroupRoleEntity), `PermissionEntities.kt` (ActionEntity, DomainResourceEntity, PermissionEntity, RolePermissionEntity, PermissionCacheEntity, PermissionProjection), `UserEntity.kt`, `UserIdentityEntity.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` |
| Repository | `rbac.adapter.out.persistence.repository` | FOUND | 1 repository file |
| Mapper | `rbac.adapter.out.persistence.mapper` | FOUND | 1 mapper file |
| Application | `rbac.application` | FOUND | `RbacEngine.kt` |
| Queries | `rbac.application.query` | FOUND | 7 query files |
| Domain | `rbac.domain.model` | FOUND | 4 model files |
| Domain Service | `rbac.domain.service` | FOUND | 1 domain service |
| **pbac module** | | | |
| Controller | `pbac.adapter.in.web` | FOUND | `PolicyController.kt` |
| Entity | `pbac.adapter.out.persistence.entity` | FOUND | `PolicyEntities.kt` (PolicyEntity, PolicyConditionEntity) |
| Repository | `pbac.adapter.out.persistence.repository` | FOUND | 1 repository file |
| Application | `pbac.application` | FOUND | `PolicyEvaluator.kt` |
| **shared module** | | | |
| Config | `shared.config` | FOUND | `SecurityConfig.kt`, `SecurityProperties.kt`, `RedisConfig.kt`, `HttpClientConfig.kt`, `I18nConfig.kt`, `JacksonConfig.kt`, `JpaAuditingConfig.kt` |
| Exception | `shared.exception` | FOUND | `GlobalExceptionHandler.kt`, `AuthErrorCode.kt`, `AuthExceptions.kt`, `AuthCoreExceptions.kt`, `AnonymousExceptions.kt`, `CipherExceptions.kt` |
| Security | `shared.security` | FOUND | Security filter chain |
| I18n | `shared.i18n` | FOUND | `I18nMessageEntity.kt`, `DatabaseMessageSource.kt` + 1 more |
| Audit | `shared.audit` | FOUND | `AuditLogService.kt` |
| Persistence | `shared.persistence` | FOUND | `VersionedAuditableEntity.kt` |

### Not Found (standard packages not in auth-service)
- `factory/` — No factory pattern for business logic
- `validator/` — Validation in DTOs via Jakarta annotations
- `specification/` — JPA Specifications not used (queries in repository)

### Naming Convention

- Controller: `*Controller.kt` (e.g., `AuthController.kt`, `MfaController.kt`)
- Service: `*Service.kt` (e.g., `AuthService.kt`, `MfaService.kt`)
- Entity: `*Entity.kt` (e.g., `UserEntity.kt`, `LoginSessionEntity.kt`)
- Repository: `*Repository.kt` or `*JpaRepository.kt`
- DTO: `*RequestDto` / `*Response` (e.g., `LoginRequestDto`, `AuthResponse`)
- Handler (CQRS): `*Handler.kt` (e.g., `LoginHandler.kt`)
- Command (CQRS): `*Command.kt` (e.g., `LoginCommand.kt`)
- Error Code: `AuthErrorCode` enum (AUTH_001..AUTH_044)
- Adapter: `*Adapter.kt` or `*PersistenceAdapter.kt`
