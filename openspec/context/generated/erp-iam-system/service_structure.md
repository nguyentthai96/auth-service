# Service Structure

_Generated: 2025-07-15 (refreshed)_

## auth-service (`com.ntt.authservice`)

### Detected Packages

| Layer | Package | Status | Key Files |
|-------|---------|--------|-----------|
| **auth module** | | | |
| Controller | `auth.adapter.in.web` | FOUND | `AuthController.kt`, `MfaController.kt`, `SsoController.kt`, `SessionController.kt`, `AdminSessionController.kt`, `TokenController.kt`, `CqrsAuthController.kt`, `CaptchaController.kt`, `AnonymousAuthController.kt`, `AccountLifecycleController.kt`, `KeyExchangeController.kt`, `RateLimitAdminController.kt`, `InternalApiController.kt`, `EventStoreController.kt` |
| DTO | `auth.adapter.in.web.dto` | FOUND | `RequestDtos.kt`, `AuthResponse.kt`, `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`, `AnonymousDtos.kt` |
| Filter | `auth.adapter.in.web.filter` | FOUND | 4 filter files |
| Kafka Consumer | `auth.adapter.in.kafka` | FOUND | `PermissionChangedConsumer.kt` + 1 more |
| Persistence Entity | `auth.adapter.out.persistence.entity` | FOUND | `LoginSessionEntity.kt`, `CipherKeySessionEntity.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt`, `VaultAccessLogEntity.kt`, `EventOutboxEntity.kt`, `EventStoreEntity.kt`, `MfaRecoveryCodeEntity.kt`, `ProcessedEventEntity.kt` |
| Repository | `auth.adapter.out.persistence.repository` | FOUND | 9 repository files |
| Mapper | `auth.adapter.out.persistence.mapper` | FOUND | 1 mapper file |
| Persistence Adapter | `auth.adapter.out.persistence` | FOUND | `UserPersistenceAdapter.kt`, `DomainPersistenceAdapter.kt`, `TokenStorePersistenceAdapter.kt`, `EventStorePersistenceAdapter.kt`, `OutboxPersistenceAdapter.kt` |
| HTTP Client | `auth.adapter.out.http` | FOUND | `CaptchaClient.kt`, `HttpCaptchaGateway.kt`, `HttpSsoGateway.kt`, `SsoProviderClient.kt` |
| SSO | `auth.adapter.out.sso` | FOUND | `OAuth2TokenExchanger.kt` |
| Cipher | `auth.adapter.out.cipher` | FOUND | `TinkCipherAlgorithmFactory.kt`, `RedisAntiReplayValidator.kt`, `RedisCipherKeySessionResolver.kt`, `HmacFieldSigningServiceImpl.kt` |
| Gateway | `auth.adapter.out.gateway` | FOUND | `CaptchaGatewayAdapter.kt` |
| Notification | `auth.adapter.out.notification` | FOUND | `KafkaNotificationGateway.kt` + 1 more |
| Event Publisher | `auth.adapter.out.event` | FOUND | `SpringEventPublisher.kt`, `KafkaEventPublisher.kt`, `OutboxPoller.kt` |
| Application | `auth.application` | FOUND | `AuthService.kt`, `JwtService.kt`, `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `PasswordPolicyService.kt`, `SsoAdapter.kt`, `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionPromotionService.kt`, `DomainLookupService.kt`, `LoginRateLimitService.kt`, `MfaRateLimitService.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionCleanupScheduler.kt`, `AccountLifecycleService.kt`, `ServiceTokenService.kt`, `TokenBlacklistCacheService.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaVerifier.kt` + claim validators |
| CQRS Commands | `auth.application.command` | FOUND | `LoginCommand.kt`, `LoginHandler.kt`, `RegisterCommand.kt`, `RegisterHandler.kt`, `RefreshTokenCommand.kt`, `RefreshTokenHandler.kt`, `AnonymousSessionHandler.kt`, `CreateAnonymousSessionCommand.kt`, `RenewAnonymousTokenHandler.kt`, `RenewAnonymousTokenCommand.kt`, `SwitchDomainHandler.kt`, `SwitchDomainCommand.kt`, `RevokeSessionsHandler.kt`, `RevokeSessionsCommand.kt`, `TokenGenerator.kt`, `AuthDomainEvents.kt` |
| CQRS Queries | `auth.application.query` | FOUND | 2 query files |
| Cipher Application | `auth.application.cipher` | FOUND | `DecryptionVaultService.kt`, `CipherVersionNegotiator.kt`, `X25519KeyExchangeServiceImpl.kt`, `EncryptedAuditService.kt` |
| Port | `auth.application.port` | FOUND | `EventPublisher.kt` (port interface) |
| Event | `auth.application.event` | FOUND | Event processing files |
| Domain Model | `auth.domain.model` | FOUND | Domain model files |
| Domain Service | `auth.domain.service` | FOUND | Domain service file |
| **rbac module** | | | |
| Controller | `rbac.adapter.in.web` | FOUND | `RbacControllers.kt` (DomainController, RoleController, GroupController, ResourceController, PermissionCheckController), `RolePermissionController.kt` |
| Entity | `rbac.adapter.out.persistence.entity` | FOUND | `RbacEntities.kt` (DomainEntity, UserDomainEntity, GroupEntity, UserGroupEntity, DomainRoleEntity, GroupRoleEntity), `PermissionEntities.kt` (ActionEntity, DomainResourceEntity, PermissionEntity, RolePermissionEntity, PermissionCacheEntity, PermissionProjection), `UserEntity.kt`, `UserIdentityEntity.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` |
| Repository | `rbac.adapter.out.persistence.repository` | FOUND | Repository file |
| Application | `rbac.application` | FOUND | `RbacEngine.kt` |
| Queries | `rbac.application.query` | FOUND | 7 query files |
| Domain | `rbac.domain.model` | FOUND | 4 model files |
| **pbac module** | | | |
| Controller | `pbac.adapter.in.web` | FOUND | `PolicyController.kt` |
| Entity | `pbac.adapter.out.persistence.entity` | FOUND | `PolicyEntities.kt` (PolicyEntity, PolicyConditionEntity) |
| Application | `pbac.application` | FOUND | `PolicyEvaluator.kt` |
| **shared module** | | | |
| Config | `shared.config` | FOUND | `SecurityConfig.kt`, `SecurityProperties.kt`, `RedisConfig.kt`, `HttpClientConfig.kt`, `I18nConfig.kt`, `JacksonConfig.kt`, `JpaAuditingConfig.kt` + 4 more |
| Exception | `shared.exception` | FOUND | `GlobalExceptionHandler.kt`, `AuthErrorCode.kt`, `AuthExceptions.kt`, `AuthCoreExceptions.kt`, `AnonymousExceptions.kt`, `CipherExceptions.kt` |
| Security | `shared.security` | FOUND | Security filter chain |
| I18n | `shared.i18n` | FOUND | `I18nMessageEntity.kt`, `DatabaseMessageSource.kt` + 1 more |
| Audit | `shared.audit` | FOUND | `AuditLogService.kt` + 2 more |
| Persistence | `shared.persistence` | FOUND | `VersionedAuditableEntity.kt` |
| Filter | `shared.filter` | FOUND | `IdempotencyFilter.kt` |

---

## account-service (`com.ntt.accountservice`)

### Detected Packages

| Layer | Package | Status | Key Files |
|-------|---------|--------|-----------|
| **profile module** | | | |
| Controller | `profile.adapter.in.web` | FOUND | `ProfileController.kt` |
| DTO | `profile.adapter.in.web.dto` | FOUND | `ProfileDtos.kt` |
| Kafka Consumer | `profile.adapter.in.kafka` | FOUND | `ProfileKafkaListener.kt` |
| Entity | `profile.adapter.out.persistence.entity` | FOUND | `UserProfileEntity.kt`, `UserContactEntity.kt` |
| Application | `profile.application` | FOUND | Service file (ProfileService) |
| **device module** | | | |
| Controller | `device.adapter.in.web` | FOUND | `DeviceController.kt` |
| DTO | `device.adapter.in.web.dto` | FOUND | `DeviceDtos.kt` |
| Entity | `device.adapter.out.persistence.entity` | FOUND | `UserDeviceEntity.kt` |
| Application | `device.application` | FOUND | Service file (DeviceService) |
| **preference module** | | | |
| Controller | `preference.adapter.in.web` | FOUND | `PreferenceController.kt` |
| Entity | `preference.adapter.out.persistence.entity` | FOUND | `UserPreferenceEntity.kt` |
| Application | `preference.application` | FOUND | Service file (PreferenceService) |
| **shared module** | | | |
| Exception | `shared.exception` | FOUND | `AccountControllerAdvice.kt`, `AccountErrorCode.kt`, `AccountExceptions.kt` |

### Not Found (account-service)
- `repository/` — Repositories likely embedded within persistence entities or application layer
- `factory/` — No factory pattern
- `config/` — No service-specific config (inherits from base-core)

---

## system-admin-service (`com.ntt.sysadminservice`)

### Detected Packages

| Layer | Package | Status | Key Files |
|-------|---------|--------|-----------|
| **menu module** | | | |
| Application | `menu.application` | FOUND | `MenuPermissionService.kt` |
| **organization module** | | | |
| Controller | `organization.adapter.in.web` | FOUND | `DepartmentController.kt`, `PositionController.kt` |
| Entity | `organization.adapter.out.persistence.entity` | FOUND | `DepartmentEntity.kt`, `PositionEntity.kt`, `UserPositionEntity.kt` |
| Application | `organization.application` | FOUND | `OrganizationService.kt`, `PositionService.kt` |
| **apipartner module** | | | |
| Controller | `apipartner.adapter.in.web` | FOUND | `ApiUsageController.kt` |
| Application | `apipartner.application` | FOUND | `ApiPartnerService.kt` |
| **workflow module** | | | |
| Controller | `workflow.adapter.in.web` | FOUND | `WorkflowController.kt` |
| Entity | `workflow.adapter.out.persistence.entity` | FOUND | `WorkflowEntities.kt` |
| Application | `workflow.application` | FOUND | `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowEscalationScheduler.kt` |
| **audit module** | | | |
| Controller | `audit.adapter.in.web` | FOUND | `AuditController.kt` |
| Entity | `audit.adapter.out.persistence.entity` | FOUND | `AuditLogEntity.kt` |
| Application | `audit.application` | FOUND | `AuditAspect.kt`, `AuditService.kt` |
| **config module** | | | |
| Entity | `config.adapter.out.persistence.entity` | FOUND | `FeatureFlagEntity.kt` |
| Application | `config.application` | FOUND | `DomainConfigService.kt`, `FeatureFlagService.kt` |
| **shared module** | | | |
| Exception | `shared.exception` | FOUND | `SysAdminControllerAdvice.kt`, `SysAdminErrorCode.kt`, `SysAdminExceptions.kt` |
| Persistence | `shared.persistence` | FOUND | `TreeEntity.kt` |
| Utility | `shared.util` | FOUND | `TreeBuilder.kt` |

### Not Found (system-admin-service)
- `menu.adapter.in.web/` — Menu controller NOT DETECTED (only `MenuPermissionService.kt` in application)
- `factory/` — No factory pattern
- `config/shared.config` — No service-specific config directory

---

## Cross-Service Naming Convention

| Pattern | Convention | auth-service | account-service | system-admin-service |
|---------|-----------|-------------|----------------|---------------------|
| Controller | `*Controller.kt` | ✅ | ✅ | ✅ |
| Service | `*Service.kt` | ✅ | ✅ | ✅ |
| Entity | `*Entity.kt` | ✅ | ✅ | ✅ |
| DTO | `*Dtos.kt` / `*Response.kt` | ✅ | ✅ | NOT DETECTED |
| Error Code | `*ErrorCode` enum | `AuthErrorCode` | `AccountErrorCode` | `SysAdminErrorCode` |
| Exception | `*Exception.kt` | ✅ | ✅ | ✅ |
| ControllerAdvice | `*ControllerAdvice.kt` | ✅ | ✅ | ✅ |
| CQRS Command | `*Command.kt` + `*Handler.kt` | ✅ | NOT USED | NOT USED |
| Architecture | Clean Architecture (Hexagonal) | `adapter/in/web`, `adapter/out/persistence`, `application`, `domain` | Same pattern | Same pattern |
