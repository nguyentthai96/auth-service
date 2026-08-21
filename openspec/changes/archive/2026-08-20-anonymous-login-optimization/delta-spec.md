# Delta Spec: anonymous-login-optimization v2

> **Type**: MAINTENANCE | **Impact**: Internal optimization — zero API contract changes

## Behavioral Changes

| Component | Before | After | Impact |
|-----------|--------|-------|--------|
| `AnonymousSessionHandler.handle()` | 2 sequential Redis calls (HSET + EXPIRE) | 1 pipelined call (HSET+EXPIRE), fallback to sequential on failure | P95 latency reduced. Session hash now includes `dataSize=0` field |
| `AnonymousRateLimitService.checkRateLimit()` | Fixed-window INCR+EXPIRE | Sliding window Lua script (default), fixed-window fallback | More accurate rate limiting, prevents burst-at-boundary. Controllable via `slidingWindowEnabled` config |
| `AnonymousSessionDataService.storeData()` | Non-atomic: `getSessionDataSize()` → check → `set()` | Atomic: Lua `atomic_data_store.lua` (check+write+counter in single EVAL) | **TOCTOU race condition eliminated**. Concurrent requests cannot exceed 64KB limit |
| `AnonymousSessionDataService.deleteData()` | Simple `delete(key)` | `delete(key)` + HINCRBY decrement of `dataSize` counter | Running counter stays accurate on delete |
| `AnonymousSessionDataService.getSessionDataSize()` | O(N) SCAN + STRLEN loop | O(1) HGET `dataSize` from session hash | Significant performance improvement for sessions with many data keys |
| `AnonymousSessionDataService.transferData()` | O(2N) RTT: per-key GET+SET in SCAN loop | O(3) RTT: SCAN → pipeline GET all → pipeline SET all | Batch transfer, ~10x fewer Redis round-trips |
| `SessionPromotionService.acquireLock()` | Returns `Boolean`, stores static `"locked"` | Returns `String?` (ownerUUID), stores UUID per acquisition | Enables ownership verification on release |
| `SessionPromotionService.releaseLock()` | Simple `DELETE` lock key | Lua conditional DEL (verify UUID ownership before delete) | **Cross-process lock release eliminated** |
| `SessionCleanupScheduler` | Only cleans inactive sessions | Also cleans expired token_blacklist entries (every 6h) | Prevents unbounded table growth |

## Configuration Changes

| Property | Default | Description |
|----------|---------|-------------|
| `app.security.anonymous.slidingWindowEnabled` | `true` | Feature flag for sliding window rate limiting |
| `app.security.anonymous.scanCount` | `100` | SCAN batch size for Redis key iteration |
| `app.security.anonymous.blacklist-cleanup-cron` | `0 0 */6 * * *` | Cron for token blacklist cleanup |

## New Redis Infrastructure

| Artifact | Type | Purpose |
|----------|------|---------|
| `redis/sliding_window_rate_limit.lua` | Lua script | Weighted sliding window counter |
| `redis/safe_lock_release.lua` | Lua script | Ownership-checked lock release |
| `redis/atomic_data_store.lua` | Lua script | TOCTOU-safe data store with size limit |
| `RedisLuaScriptConfig` | Spring @Configuration | Bean definitions for 3 Lua scripts |

## Backward Compatibility

- **API contracts**: Zero changes — all endpoints return same shapes
- **Redis data**: Backward compatible — pre-existing sessions without `dataSize` field default to 0
- **Configuration**: All new properties have sensible defaults — application starts without explicit config
- **Session lifecycle**: Sessions are ephemeral (24h TTL) — all sessions will have `dataSize` within 24h of deployment
