# Proposal: Anonymous Login Optimization

[CHANGED] Scope entirely new vs archive v1 (which covered JTI fix, ThreadLocal removal, metrics, tests). This version covers Redis performance optimization + data integrity hardening.

## Why

Auth-service anonymous login feature is fully implemented and functionally correct (post archive v1 fixes). However, research analysis (`wf_feature_research`) identified 6 optimization areas and 2 critical data integrity issues:

1. **🔴 BUG — TOCTOU Race Condition**: `AnonymousSessionDataService.storeData()` (L37-57) performs non-atomic `getSessionDataSize()` → check → `set()`. Concurrent requests can exceed the 64KB data size limit.
2. **🔴 BUG — Unsafe Lock Release**: `SessionPromotionService.releaseLock()` (L140-147) uses simple `DELETE` with static `LOCK_VALUE = "locked"`. If lock TTL expires and another process acquires it, first process's `finally` block deletes the wrong lock.
3. **🟡 PERF — Sequential Redis Calls**: `AnonymousSessionHandler` makes 2 sequential calls (HSET+EXPIRE) that can be pipelined into 1 RTT.
4. **🟡 PERF — Fixed Window Rate Limiting**: `AnonymousRateLimitService` uses INCR+EXPIRE which allows burst-at-boundary attacks.
5. **🟡 PERF — N+1 Data Transfer**: `AnonymousSessionDataService.transferData()` iterates per-key GET+SET in a loop — O(2N) RTTs instead of O(3) with pipelining.
6. **🟡 PERF — O(N) Size Calculation**: `getSessionDataSize()` does SCAN+STRLEN loop (O(N)) instead of maintaining a running counter (O(1)).

Additionally, `token_blacklist` table has no cleanup scheduler for expired anonymous entries, and configuration lacks feature flags for gradual rollout.

## Changes

**Phase 1 — Data Integrity (MUST)**:
- **FR-004**: Safe distributed lock release — UUID lock value + Lua conditional DEL
- **FR-007**: Atomic size check + data write via Lua script (TOCTOU fix)
- **FR-008**: `RedisLuaScriptConfig` Spring @Configuration for 3 Lua script beans
- **FR-009**: 3 Lua script files: `sliding_window_rate_limit.lua`, `safe_lock_release.lua`, `atomic_data_store.lua`

**Phase 2 — Performance**:
- **FR-001**: Pipeline HSET+EXPIRE in `AnonymousSessionHandler`
- **FR-002**: Sliding window Lua rate limiting in `AnonymousRateLimitService`
- **FR-003**: Pipeline MGET+MSET for batch data transfer in `AnonymousSessionDataService`
- **FR-005**: Running `dataSize` counter via HINCRBY (combined with FR-007 in Lua)

**Phase 3 — Reliability & Operations**:
- **FR-010**: Pipeline fallback to sequential on failure
- **FR-011**: Lua script fallback to fixed-window on failure
- **FR-013**: TokenBlacklist cleanup scheduler (extend `SessionCleanupScheduler`)
- **FR-014**: Config enhancements (`slidingWindowEnabled`, `scanCount`)

**Phase 4 — Nice-to-have (deferred)**:
- **FR-006**: Micrometer Observation spans (existing metrics adequate)
- **FR-012**: Running counter periodic reconciliation (Lua atomicity eliminates primary drift risk)

## Capabilities

### Fixed Capabilities
- `anonymous-data-integrity`: TOCTOU race condition eliminated via Lua atomic check-and-set (FR-007)
- `anonymous-lock-safety`: Lock release is ownership-verified — zero cross-process lock releases (FR-004)

### New Capabilities
- `redis-lua-scripting`: Infrastructure for Lua script execution via Spring Data Redis (FR-008, FR-009)
- `sliding-window-rate-limiting`: Precise rate limiting without burst-at-boundary vulnerability (FR-002)
- `redis-pipelining`: Reduced RTTs for session creation and data transfer (FR-001, FR-003)
- `running-data-counter`: O(1) session data size calculation (FR-005)
- `token-blacklist-cleanup`: Automated cleanup of expired blacklist entries (FR-013)

### Unchanged Capabilities
- All existing anonymous login functionality — zero API contract changes
- All existing error handling — same exceptions, same HTTP status codes
- All existing authentication/authorization flow — optimization is internal only

## Impact

### Backend (auth-service)

**MODIFY** (7 existing files):
- `AnonymousSessionHandler.kt` — pipeline HSET+EXPIRE, add `dataSize=0`, pipeline fallback
- `AnonymousRateLimitService.kt` — Lua sliding window, Lua fallback
- `AnonymousSessionDataService.kt` — Lua atomic store, pipeline transfer, HGET counter
- `SessionPromotionService.kt` — UUID lock + Lua safe release
- `SessionCleanupScheduler.kt` — add blacklist cleanup method + TokenBlacklistRepository dependency
- `SecurityProperties.kt` — add `slidingWindowEnabled`, `scanCount` to AnonymousProperties
- `Repositories.kt` — add `deleteByExpiresAtBefore()` to TokenBlacklistRepository

**NEW** (4 files):
- `RedisLuaScriptConfig.kt` — Spring @Configuration for Lua script beans
- `sliding_window_rate_limit.lua` — Sliding window rate limit script
- `safe_lock_release.lua` — Safe lock release script
- `atomic_data_store.lua` — Atomic data store + size counter script

### Database
- **No schema changes** — `deleteByExpiresAtBefore()` uses existing `expires_at` column in `token_blacklist` table

### External Systems
- **Redis**: Same key namespaces. New key pattern `anon:rate:{ip}:{windowId}` (old keys auto-expire). New `dataSize` field in session hash (backward compatible — null → 0).
- **No new dependencies** — all optimizations use existing Spring Data Redis APIs.
