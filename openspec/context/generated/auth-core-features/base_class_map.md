# Base Class Map

_Generated: 2026-08-25 | Services: auth-service_

## Entity Base Classes

- `SnowflakePersistentAuditableEntity` — `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` (base-core library — Snowflake ID + audit fields + soft-delete via `active`)
  - Used by: `UserEntity`, `LoginSessionEntity`, `DomainEntity`, `UserDomainEntity`, `GroupEntity`, `UserGroupEntity`, `DomainRoleEntity`, `GroupRoleEntity`, `DomainResourceEntity`, `RolePermissionEntity`, `PolicyEntity`
- `SnowflakeBaseEntity` — `com.ntt.basecore.model.id.SnowflakeBaseEntity` (base-core library — Snowflake ID only)
  - Used by: `UserIdentityEntity`, `PasswordPolicyEntity`, `PasswordHistoryEntity`, `ActionEntity`, `PermissionEntity`, `RefreshTokenEntity`, `TokenBlacklistEntity`, `PolicyConditionEntity`
- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - Extends `SnowflakePersistentAuditableEntity` + `@Version` for optimistic locking
  - Local extension in auth-service

## Controller (13 @RestController classes)

- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
- `InternalApiController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`

## Handler (Command/Query Handlers — CQRS)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` (⚠️ Assumption: inferred from CQRS pattern)
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` (⚠️ Assumption: inferred from CQRS pattern)
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` (⚠️ Assumption: inferred from CQRS pattern)
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` (⚠️ Assumption: inferred from CQRS pattern)
- `TokenGenerator` — `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`
- `BuildAuthResponseHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt` (⚠️ Assumption: inferred from CQRS pattern)

## Client / Gateway

- `SsoGateway` (port) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt`
- `HttpSsoGateway` (adapter) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `CaptchaGateway` (port) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`

## Event Publishers

- `EventPublisher` (port) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
- `SpringEventPublisher` (adapter) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
- `KafkaEventPublisher` (adapter, @Primary) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`

## Exception Handling

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - `@RestControllerAdvice` — extends `BaseControllerAdvice` (base-core)
  - Handles `AuthException` hierarchy with RFC 7807 ProblemDetail

## Cache (abstract)

- `AbstractTwoTierCache` — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - Abstract base for L1 (Caffeine) + L2 (Redis) caching

## NOT DETECTED

- Factory classes (no factory pattern detected in auth-service — uses Spring DI instead)
