# Base Class Map

_Generated: 2026-08-20 | Services: auth-service_

## Controller

- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` — @RestController
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` — @RestController
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` — @RestController
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt` — @RestController
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` — @RestController
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt` — @RestController
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` — @RestController
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` — @RestController
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt` — @RestController
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt` — @RestController
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt` — @RestController
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` — @RestController
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt` — @RestController
- `RbacControllers` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` — @RestController (5 controllers in 1 file)
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt` — @RestController

## Handler (CQRS Command Handlers)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` — @Service, handles `LoginCommand`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` — @Service, handles `RegisterCommand`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` — @Service, handles `RefreshTokenCommand`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` — @Service, handles `SwitchDomainCommand`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` — @Service, handles `RevokeSessionsCommand`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — @Service, handles `CreateAnonymousSessionCommand`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — @Service, handles `RenewAnonymousTokenCommand`
- `TokenGenerator` — `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt` — @Service, JWT token generation
- `BuildAuthResponseHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt` — @Service, handles `BuildAuthResponseQuery`

## Entity Base Classes

- `SnowflakePersistentAuditableEntity` — `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` (base-core dependency) — Snowflake ID + audit fields (createdAt, updatedAt, active)
- `SnowflakeBaseEntity` — `com.ntt.basecore.model.id.SnowflakeBaseEntity` (base-core dependency) — Snowflake ID only
- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt` — extends `SnowflakePersistentAuditableEntity` + `@Version` for optimistic locking

## Client / Gateway

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt` — interface (HTTP client)
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt` — interface (HTTP client)
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt` — HTTP gateway implementation
- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt` — HTTP gateway implementation
- `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt` — adapter for CaptchaGateway port
- `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt` — @Component, exchanges OAuth2 auth code

## Port Interfaces (Hexagonal)

- `UserPort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt` — interface
- `TokenStore` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt` — interface
- `DomainPort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainPort.kt` — interface
- `EventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt` — interface
- `CaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt` — interface
- `SsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt` — interface
- `PermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt` — interface

## Persistence Adapters

- `UserPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt` — implements UserPort
- `TokenStorePersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` — implements TokenStore
- `DomainPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/DomainPersistenceAdapter.kt` — implements DomainPort

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` — @RestControllerAdvice, extends BaseControllerAdvice (base-core)

## Factory

NOT DETECTED — no explicit Factory pattern classes found. CAPTCHA verification uses `CaptchaVerifier` interface with pluggable implementations.

## NOT DETECTED

- Traditional Factory classes
