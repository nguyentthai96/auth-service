# Integration Map

_Generated: 2025-07-15 (REUSE — updated scan)_

## Redis (Cache/Session Store)

- Client: `StringRedisTemplate` — auto-configured Spring Bean (Lettuce driver)
- Protocol: Redis protocol via Lettuce
- Config: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Operations used by anonymous features:
  - `opsForValue()`: GET, SET, SETEX, SETNX, INCREMENT, SIZE
  - `opsForHash<String, String>()`: PUTALL, GET, INCREMENT
  - `executePipelined(RedisCallback)`: Batch HSET+EXPIRE, batch GET, batch SETEX
  - `execute(DefaultRedisScript<Long>, keys, args)`: Lua EVAL (3 scripts)
  - `execute(RedisCallback)`: Raw connection for SCAN
  - `delete(key)`, `expire(key, duration)`, `getExpire(key)`, `hasKey(key)`

### Lua Script Integration

- `slidingWindowRateLimitScript` — `src/main/resources/redis/sliding_window_rate_limit.lua` (35 lines)
  - Caller: `AnonymousRateLimitService.checkSlidingWindow()` — `src/main/kotlin/.../AnonymousRateLimitService.kt`
  - KEYS: `anon:rate:{ip}:{currentWindow}`, `anon:rate:{ip}:{prevWindow}`
  - ARGV: maxAttempts, windowSeconds, elapsedSeconds
  - Returns: -1 (denied) or weighted count

- `safeLockReleaseScript` — `src/main/resources/redis/safe_lock_release.lua` (11 lines)
  - Caller: `SessionPromotionService.releaseLock()` — `src/main/kotlin/.../SessionPromotionService.kt`
  - KEYS: `anon:lock:{sessionId}`
  - ARGV: ownerUUID
  - Returns: 1 (released) or 0 (not owner)

- `atomicDataStoreScript` — `src/main/resources/redis/atomic_data_store.lua` (26 lines)
  - Caller: `AnonymousSessionDataService.storeData()` — `src/main/kotlin/.../AnonymousSessionDataService.kt`
  - KEYS: `anon:session:{id}` (session hash), `anon:data:{id}:{ns}:{key}` (data key)
  - ARGV: maxDataSizeBytes, value, ttlSeconds
  - Returns: -1 (limit exceeded) or new total data size

### Pipeline Integration

- `AnonymousSessionHandler.handle()` — pipeline HSET+EXPIRE for session creation (line 69)
- `AnonymousSessionDataService.transferData()` — pipeline GET all keys (line 142), pipeline SET all user keys (line 163)

## PostgreSQL (Database)

- Client: JPA/Hibernate via Spring Data JPA
- Protocol: JDBC (PostgreSQL driver)
- Repository: `TokenBlacklistRepository` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`
  - Used by: `SessionPromotionService` (save blacklist entry during promotion)
  - Used by: `SessionCleanupScheduler` (delete expired entries every 6 hours)
- Migrations: Flyway — `src/main/resources/db/`

## Micrometer (Metrics)

- Client: `MeterRegistry` — auto-configured Spring Bean
- Protocol: Micrometer API (Counter, Timer)
- Metrics defined in anonymous services:
  - `auth.anonymous.sessions.created` — Counter (AnonymousSessionHandler)
  - `auth.anonymous.rate_limited` — Counter (AnonymousRateLimitService)
  - `auth.anonymous.data.stored` — Counter (AnonymousSessionDataService)
  - `auth.anonymous.data.size_exceeded` — Counter (AnonymousSessionDataService)
  - `auth.anonymous.sessions.promoted` — Counter with status tag (SessionPromotionService)
  - `auth.anonymous.promotion.duration` — Timer (SessionPromotionService)
  - `auth.anonymous.token.generation.duration` — Timer (AnonymousSessionHandler)

## NOT DETECTED

- REST/gRPC external integrations — anonymous login operates entirely within auth-service
- Kafka — `KafkaConfig` exists but not used by anonymous features
- External API clients — no outbound HTTP calls in anonymous flow
