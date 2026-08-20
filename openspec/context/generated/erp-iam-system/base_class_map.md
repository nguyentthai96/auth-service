# Base Class Map

_Generated: 2026-08-05 (refreshed) | Services: auth-service_

## Entity Base Classes

- `SnowflakePersistentAuditableEntity` — `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` (base-core library)
  - extends: JPA MappedSuperclass with Snowflake Long ID, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `active`
  - used by: `DomainEntity`, `UserDomainEntity`, `GroupEntity`, `UserGroupEntity`, `DomainRoleEntity`, `GroupRoleEntity`, `DomainResourceEntity`, `RolePermissionEntity`, `PolicyEntity`, `LoginSessionEntity`, `PasswordPolicyEntity`, `PasswordHistoryEntity`, `UserEntity`, `UserIdentityEntity`, `CipherKeySessionEntity`, `AccountDeletionRequestEntity`, `AccountDataExportEntity`, `VaultAccessLogEntity`

- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - extends: `SnowflakePersistentAuditableEntity`
  - adds: `@Version var version: Long = 0` (optimistic locking)
  - used by: `UserEntity` (via `VersionedAuditableEntity`)

## Controller

- Standard Spring `@RestController` — NO custom base controller class detected.
- 20+ controllers using `@RestController` annotation directly.
- Pattern: Constructor injection, no abstract base.
- Examples:
  - `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
  - `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
  - `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - `DomainController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - `RoleController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - `GroupController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`

## Exception Handler

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (from `com.ntt.basecore.domain.web.BaseControllerAdvice`)
  - pattern: RFC 7807 ProblemDetail + i18n via MessageSource

## Service

- No abstract base service class detected. Services use constructor injection + `@Service` annotation.
- CQRS pattern: `*Command` + `*Handler` classes in `auth/application/command/`

## Handler (CQRS)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`

## Factory

- NOT DETECTED — no `*Factory` pattern in service code (except `TinkCipherAlgorithmFactory` which is infrastructure).

## Client / Gateway

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`

## NOT DETECTED
- Custom base controller abstract class
- Custom base service abstract class
- Factory pattern for business logic (only infra `TinkCipherAlgorithmFactory`)
- TreeEntity base class (needed for menu/department trees — confirmed not in base-core)
