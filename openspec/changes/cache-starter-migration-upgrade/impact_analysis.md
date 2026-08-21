# Impact Analysis: cache-starter-migration-upgrade

## 1. Core Files
The following files are identified as the primary targets for modification or removal:
- `com.ntt.authservice.shared.cache.AbstractTwoTierCache.kt` (DELETE)
- `com.ntt.authservice.auth.adapter.out.cache.MultiTierPermissionCache.kt` (DELETE)
- `com.ntt.authservice.auth.adapter.out.cache.CaffeinePermissionCache.kt` (DELETE)
- `com.ntt.authservice.auth.adapter.out.cache.InMemoryPermissionCache.kt` (DELETE)
- `com.ntt.authservice.auth.application.port.out.PermissionCache.kt` (DELETE)

## 2. Call Tree & Dependencies
The `PermissionCache` port is currently injected and used in:
- `com.ntt.authservice.auth.application.command.TokenGenerator.kt`
- `com.ntt.authservice.auth.application.command.LoginHandler.kt`
- `com.ntt.authservice.rbac.application.query.GetPermissionsHandler.kt`
- `com.ntt.authservice.rbac.application.query.GetUserRolesHandler.kt`
- `com.ntt.authservice.auth.adapter.in.kafka.PermissionChangedConsumer.kt`

## 3. Blast Radius Assessment
- **Depth 1 (Direct Callers):** 5 classes (TokenGenerator, LoginHandler, GetPermissionsHandler, GetUserRolesHandler, PermissionChangedConsumer).
- **Impact Level:** 🟡 Medium. 
- **Action:**
  - Remove `PermissionCache` injection from these classes.
  - For handlers `GetPermissionsHandler` & `GetUserRolesHandler`, apply `@Cacheable(cacheNames=["permissions"/"roles"])` at the method level.
  - For `PermissionChangedConsumer`, inject `CacheManager` instead of `PermissionCache` and call `cacheManager.getCache("permissions")?.evict(key)`.
  - For `TokenGenerator` and `LoginHandler`, they seem to rely on `PermissionCache` to fetch roles/permissions during token generation. They should call `GetPermissionsHandler` or the Database Port directly, which will be cached via `@Cacheable`.

## 4. Reuse Map
- The new `base-cache-starter` will act as a shared library for the entire BigBang ecosystem.
- `TwoLevelCacheManager` will natively handle L1 (Caffeine) and L2 (Redis) orchestration, completely replacing the custom `AbstractTwoTierCache` logic in `auth-service`.

## 5. Context Snapshot
By moving the cache logic to `base-cache-starter` with Spring's `@Cacheable`, we decouple the domain logic from cache implementation details, simplifying the codebase and reducing maintenance overhead. The new Starter introduces 11 category taxonomies (e.g., `DATA_CACHE`, `SESSION_BOUND`) allowing fine-grained control over TTL jitter, encryption, and compression per cache.
