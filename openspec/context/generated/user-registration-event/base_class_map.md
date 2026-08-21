# Base Class Map

_Generated: 2025-08-21 | Services: auth-service (auth, shared, rbac, pbac modules)_

## Controller

- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth")`, `@ConditionalOnProperty`
  - Dependencies: LoginHandler, RegisterHandler, RefreshTokenHandler, SwitchDomainHandler, RevokeSessionsHandler
- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
- `InternalApiController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
- `RbacControllers` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`

## Handler (CQRS Command Handlers)

- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - extends: `CommandHandler<RegisterCommand, RegisterResult>` (from `eventsourcing-utils`)
  - Dependencies: UserPort, DomainPort, EventPublisher, TokenGenerator, SessionPromotionService
- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: `CommandHandler<LoginCommand, LoginResult>`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsCommand.kt`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`

## Handler (CQRS Query Handlers)

- `BuildAuthResponseHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt`
- `CheckPermissionHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/CheckPermissionHandler.kt`
- `GetPermissionsHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetPermissionsHandler.kt`
- `GetUserRolesHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetUserRolesHandler.kt`

## Domain Event & Port

- `DomainEvent` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Properties: `eventType: String`
- `EventPublisher` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Method: `publish(event: DomainEvent)`
- `UserPort` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt`
- `DomainPort` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainPort.kt`
- `TokenStore` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
- `PermissionCache` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt`
- `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- `SsoGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt`

## Client / Gateway

- `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - implements: `EventPublisher`
  - annotations: `@Component`, `@Primary`, `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`
- `SpringEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
  - implements: `EventPublisher`
  - annotations: `@Component` (fallback when Kafka not configured)
- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
- `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`

## Kafka Consumer

- `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - annotations: `@Component`, `@ConditionalOnProperty`, `@KafkaListener(topics = ["iam.permission.changed"])`

## Cache

- `AbstractTwoTierCache<K, V>` — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - L1: Caffeine, L2: Redis (StringRedisTemplate)
  - Abstract methods: `toKeyString()`, `deserializeFromRedis()`, `loadFromSource()`
- `MultiTierPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
- `CaffeinePermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/CaffeinePermissionCache.kt`
- `InMemoryPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/InMemoryPermissionCache.kt`

## Persistence Base

- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - extends: `SnowflakePersistentAuditableEntity()` (base-core)
  - adds: `@Version var version: Long` for optimistic locking

## Exception Base

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - properties: `authError: AuthErrorCode`, `httpStatus: HttpStatus`
- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - handles: `AuthException` → RFC 7807 ProblemDetail

## NOT DETECTED

- Factory pattern classes
