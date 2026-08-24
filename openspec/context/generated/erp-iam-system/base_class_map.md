# Base Class Map

_Generated: 2025-07-15 (refreshed) | Services: auth-service, account-service, system-admin-service_

## Entity Base Classes

- `SnowflakePersistentAuditableEntity` — `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` (base-core library)
  - extends: JPA MappedSuperclass with Snowflake Long ID, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `active`
  - used by (auth-service): `DomainEntity`, `UserDomainEntity`, `GroupEntity`, `UserGroupEntity`, `DomainRoleEntity`, `GroupRoleEntity`, `DomainResourceEntity`, `RolePermissionEntity`, `PolicyEntity`, `LoginSessionEntity`, `PasswordPolicyEntity`, `PasswordHistoryEntity`, `UserEntity`, `UserIdentityEntity`, `CipherKeySessionEntity`, `AccountDeletionRequestEntity`, `AccountDataExportEntity`, `VaultAccessLogEntity`, `EventStoreEntity`, `EventOutboxEntity`, `ProcessedEventEntity`, `MfaRecoveryCodeEntity`
  - used by (account-service): `UserProfileEntity`, `UserContactEntity`, `UserDeviceEntity`, `UserPreferenceEntity`
  - used by (system-admin-service): `DepartmentEntity`, `PositionEntity`, `UserPositionEntity`, `WorkflowEntities`, `AuditLogEntity`, `FeatureFlagEntity`

- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - extends: `SnowflakePersistentAuditableEntity`
  - adds: `@Version var version: Long = 0` (optimistic locking)
  - used by: `UserEntity` (via `VersionedAuditableEntity`)

- `TreeEntity` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/persistence/TreeEntity.kt`
  - extends: `SnowflakePersistentAuditableEntity`
  - adds: `parentId`, `name`, `code`, `sortOrder`, `treeLevel`, `treePath`
  - used by: `DepartmentEntity` (organization tree), menu items (planned)

## Controller

- Standard Spring `@RestController` — NO custom base controller class detected.
- auth-service: 20+ controllers using `@RestController` annotation directly.
- account-service: 3 controllers (`ProfileController`, `DeviceController`, `PreferenceController`)
- system-admin-service: 5 controllers (`DepartmentController`, `PositionController`, `WorkflowController`, `AuditController`, `ApiUsageController`)
- Pattern: Constructor injection, no abstract base.
- Examples:
  - `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
  - `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
  - `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - `ProfileController` — `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/web/ProfileController.kt`
  - `DeviceController` — `account-service/src/main/kotlin/com/ntt/accountservice/device/adapter/in/web/DeviceController.kt`
  - `DepartmentController` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/DepartmentController.kt`
  - `PositionController` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/PositionController.kt`
  - `WorkflowController` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/workflow/adapter/in/web/WorkflowController.kt`
  - `AuditController` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/adapter/in/web/AuditController.kt`
  - `ApiUsageController` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/apipartner/adapter/in/web/ApiUsageController.kt`

## Exception Handler

- `AuthControllerAdvice` (auth-service) — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (from `com.ntt.basecore.domain.web.BaseControllerAdvice`)
  - pattern: RFC 7807 ProblemDetail + i18n via MessageSource

- `AccountControllerAdvice` (account-service) — `account-service/src/main/kotlin/com/ntt/accountservice/shared/exception/AccountControllerAdvice.kt`
  - extends: `BaseControllerAdvice` (from base-core)
  - pattern: Same RFC 7807 pattern as auth-service

- `SysAdminControllerAdvice` (system-admin-service) — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminControllerAdvice.kt`
  - extends: `BaseControllerAdvice` (from base-core)
  - pattern: Same RFC 7807 pattern as auth-service

## Service

- No abstract base service class detected. All services use constructor injection + `@Service` annotation.
- auth-service: `AuthService`, `JwtService`, `MfaService`, `TotpService`, `OtpService`, `PasswordPolicyService`, `SsoAdapter`, `LoginSessionService`, `SessionPolicyService`, `SessionPromotionService`, `AccountLifecycleService`, `ServiceTokenService`, `DomainLookupService`, `LoginRateLimitService`, `MfaRateLimitService`, `AnonymousRateLimitService`, `AnonymousSessionDataService`, `TokenBlacklistCacheService`, `SessionCleanupScheduler`, `AuditLogService`
- account-service: `ProfileService` (implicit via ProfileController), `DeviceService`, `PreferenceService`
- system-admin-service: `MenuPermissionService`, `OrganizationService`, `PositionService`, `ApiPartnerService`, `WorkflowEngine`, `WorkflowService`, `WorkflowEscalationScheduler`, `AuditService`, `AuditAspect`, `DomainConfigService`, `FeatureFlagService`

## Handler (CQRS — auth-service only)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`

## Factory

- `TinkCipherAlgorithmFactory` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/TinkCipherAlgorithmFactory.kt` (infrastructure — Google Tink cipher factory)

## Client / Gateway (auth-service)

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`

## Utility (system-admin-service)

- `TreeBuilder` — `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/util/TreeBuilder.kt`
  - Builds hierarchical tree structure from flat entity list

## NOT DETECTED

- Custom base controller abstract class (all services use `@RestController` directly)
- Custom base service abstract class
- Factory pattern for business logic (only infra `TinkCipherAlgorithmFactory`)
- Client/Gateway in account-service or system-admin-service
