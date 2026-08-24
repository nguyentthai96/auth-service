# Base Class Map

_Generated: 2026-08-25 | Services: auth-service_

## Controller

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - implements: N/A
  - role: Auth-specific exception handling with RFC 7807 ProblemDetail
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - extends: N/A (standalone @RestController)
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - extends: N/A (standalone @RestController)
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
  - extends: N/A (standalone @RestController)
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - extends: N/A (standalone @RestController)
- `RbacControllers` (multiple) — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - extends: N/A (standalone @RestControllers)
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
  - extends: N/A (standalone @RestController)
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
  - extends: N/A (standalone @RestController)
- `InternalApiController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
  - extends: N/A (standalone @RestController)
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
  - extends: N/A (standalone @RestController)

## Handler (CQRS Query/Command)

- `GetPermissionsHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetPermissionsHandler.kt`
  - implements: `QueryHandler<GetPermissionsQuery, List<String>>` (eventsourcing-utils)
  - annotations: `@Component`, `@Cacheable(cacheNames=["permissions"], keyGenerator="baseCacheKeyGenerator")`
- `GetUserRolesHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetUserRolesHandler.kt`
  - implements: `QueryHandler<GetUserRolesQuery, List<String>>` (eventsourcing-utils)
  - annotations: `@Component`, `@Cacheable(cacheNames=["roles"], keyGenerator="baseCacheKeyGenerator")`
- `CheckPermissionHandler` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/CheckPermissionHandler.kt`
  - implements: `QueryHandler<CheckPermissionQuery, Boolean>` (eventsourcing-utils)
- `BuildAuthResponseHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt`
  - implements: `QueryHandler<BuildAuthResponseQuery, AuthToken>` (eventsourcing-utils)

## Factory

NOT DETECTED — no factory pattern classes found in auth-service.

## Client / Gateway

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - type: HTTP client (external)
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
  - type: HTTP client (external)

## Cache-Related (Feature-Specific)

- `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - role: Custom L1 (Caffeine) + L2 (Redis) + DB fallback cache with circuit breaker
  - status: Custom implementation — candidate for future @Cacheable migration
- `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
  - role: Caffeine-only cache for i18n messages (5-min TTL, max 500 entries)
  - extends: `AbstractMessageSource` (Spring)
- `SecurityConfig.twoLevelCacheManager()` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`
  - role: @Primary @Bean creating TwoLevelCacheManager with ConcurrentMapCacheManager L1
  - status: Target MODIFY — should use Caffeine L1 or delegate to auto-config
- `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - role: Kafka consumer for cache eviction — uses `CacheManager.getCache().clear()`
  - status: Target MODIFY — should use targeted evict(key)
