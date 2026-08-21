# SRS: Anonymous Login Optimization (MAINTENANCE — v2)

[CHANGED] Entire scope changed from archive v1 (JTI fix, ThreadLocal, metrics, tests) to v2 (Redis optimization + data integrity hardening). All archive v1 fixes are completed and deployed.

## 1. Feature Overview

Anonymous login feature is fully implemented and functionally correct. This SRS covers MAINTENANCE optimizations: Redis pipelining, Lua scripting for atomicity, sliding window rate limiting, batch data transfer, safe distributed lock release, TOCTOU fix, running data size counter, token blacklist cleanup, and configuration enhancements.

**Zero API contract changes** — all optimizations are internal to Redis operations.

## 2. Functional Requirements

### FR-001: Pipeline Session Creation [MODIFY]
- **Current behavior**: `AnonymousSessionHandler.handle()` makes 2 sequential Redis calls: `opsForHash().putAll()` then `expire()` (L63-64).
- **Expected behavior**: Batch HSET+EXPIRE into single `executePipelined(RedisCallback)` call — 1 RTT instead of 2.
- **Validation**: Session creation produces identical Redis state. P95 latency < 50ms. Pipeline result checked for errors.
- **Fallback**: FR-010 — if pipeline throws exception, fall back to sequential calls.

### FR-002: Sliding Window Rate Limiting [MODIFY]
- **Current behavior**: `AnonymousRateLimitService.checkRateLimit()` uses fixed-window INCR+EXPIRE (L44-54). Allows burst-at-boundary attacks.
- **Expected behavior**: Replace with Lua `sliding_window_rate_limit.lua` script executing atomically. Weighted formula: `prev × (1 - elapsed/window) + current`. Returns count (allowed) or -1 (denied).
- **Redis keys**: `anon:rate:{ip}:{currentWindowId}` and `anon:rate:{ip}:{prevWindowId}`
- **Validation**: Rate limiting precision within 2% of true sliding window. Lua script syntactically valid. Feature flag `slidingWindowEnabled` controls rollout.
- **Fallback**: FR-011 — if Lua script fails, fall back to fixed-window INCR+EXPIRE.

### FR-003: Batch Data Transfer [MODIFY]
- **Current behavior**: `AnonymousSessionDataService.transferData()` iterates per-key: SCAN → per-key GET → per-key SET (O(1+2N) RTTs, L90-115).
- **Expected behavior**: SCAN collect keys → pipeline GET all → pipeline SET all user keys. ~3 RTTs regardless of N.
- **Validation**: All data transferred correctly. `DataTransferResult` API unchanged. P95 latency < 10ms for 10 keys.

### FR-004: Safe Distributed Lock Release [MODIFY]
- **Current behavior**: `SessionPromotionService` uses `LOCK_VALUE = "locked"` (L35) and simple `DELETE lockKey` (L142). Cross-process lock release possible.
- **Expected behavior**: Lock value is UUID (unique per acquisition). Release via Lua `safe_lock_release.lua`: `if GET(key) == uuid then DEL(key) return 1 else return 0 end`.
- **Validation**: Zero cross-process lock releases. Lock acquisition uses UUID. Lua script returns 0 if not owner (logged as warning, no error).

### FR-005: Running Data Size Counter [MODIFY]
- **Current behavior**: `getSessionDataSize()` does SCAN+STRLEN loop (O(N), L129-147).
- **Expected behavior**: Maintain `dataSize` field in session hash (`anon:session:{id}`). Initialize to "0" on session creation. Atomic increment via `atomic_data_store.lua`. Decrement on `deleteData()` via HINCRBY negative.
- **Validation**: `dataSize=0` on new session. Counter accurate to ±0 (Lua atomicity). O(1) read via HGET. Backward compatible — null → 0 for pre-existing sessions.

### FR-006: Observability Tracing Spans [DEFERRED]
- **Rationale**: Existing Micrometer counters and timers (from archive v1) provide adequate operational visibility. Observation spans are incremental improvement — deferred to Phase 4.

### FR-007: Atomic Size Check + Data Write (TOCTOU Fix) [MODIFY]
- **Current behavior**: `storeData()` calls `getSessionDataSize()` → check limit → `set()` (L37-57). Non-atomic — concurrent requests can exceed 64KB limit.
- **Expected behavior**: Single Lua `atomic_data_store.lua` script atomically: HGET dataSize → check limit → SET data EX ttl → HINCRBY dataSize. Returns new total size or -1 (denied).
- **Lua script**: 2 KEYS (session hash, data key), 3 ARGV (maxDataSizeBytes, value, ttlSeconds). ~15 lines.
- **Validation**: Zero over-limit writes under concurrency. Test with CountDownLatch + ExecutorService (N threads).

### FR-008: RedisLuaScriptConfig Configuration Class [NEW]
- **Action**: Create `RedisLuaScriptConfig` Spring `@Configuration` class at `shared/config/`.
- **Beans**:
  - `slidingWindowRateLimitScript(): DefaultRedisScript<Long>` — loads `classpath:redis/sliding_window_rate_limit.lua`
  - `safeLockReleaseScript(): DefaultRedisScript<Long>` — loads `classpath:redis/safe_lock_release.lua`
  - `atomicDataStoreScript(): DefaultRedisScript<Long>` — loads `classpath:redis/atomic_data_store.lua`
- **Validation**: All 3 beans injectable. SHA1 cached automatically by `DefaultRedisScript`.

### FR-009: Lua Script Files [NEW]
- **Action**: Create 3 Lua script files in `src/main/resources/redis/`:
  1. `sliding_window_rate_limit.lua` — ~27 lines, 2 KEYS, 3 ARGV, returns Long
  2. `safe_lock_release.lua` — ~8 lines, 1 KEY, 1 ARGV, returns Long
  3. `atomic_data_store.lua` — ~15 lines, 2 KEYS, 3 ARGV, returns Long
- **Validation**: Scripts syntactically valid Lua. Unit tests verify correct behavior with embedded Redis.

### FR-010: Pipeline Fallback on Failure [MODIFY]
- **Current behavior**: No pipeline usage exists.
- **Expected behavior**: When `executePipelined()` throws exception, fall back to sequential Redis calls. Log warning "Pipeline failed, falling back to sequential". Session created regardless of pipeline failure.
- **Validation**: Session creation succeeds even if pipeline fails.

### FR-011: Lua Script Fallback on Failure [MODIFY]
- **Current behavior**: No Lua script usage exists.
- **Expected behavior**: When Lua script execution fails (Redis version incompatibility, script error), fall back to fixed-window INCR+EXPIRE. Log warning. Rate limiting still functional (degraded).
- **Validation**: Rate limiting works under Lua failure. No request blocked by script errors.

### FR-012: Running Counter Periodic Reconciliation [DEFERRED]
- **Rationale**: `atomic_data_store.lua` makes write + increment atomic — primary drift risk eliminated. Sessions are ephemeral (24h TTL). Deferred to Phase 4.

### FR-013: TokenBlacklist Cleanup Scheduler [MODIFY]
- **Current behavior**: `TokenBlacklistRepository` only has `existsByTokenJti()`. No cleanup of expired entries. Table grows unbounded.
- **Expected behavior**: Extend `SessionCleanupScheduler` with second `@Scheduled` method: `cleanupExpiredBlacklistEntries()`. Runs every 6 hours (configurable). Calls `tokenBlacklistRepository.deleteByExpiresAtBefore(Instant.now())`.
- **Repository change**: Add `fun deleteByExpiresAtBefore(cutoff: Instant): Int` to `TokenBlacklistRepository` (Spring Data JPA derived query).
- **Validation**: Expired entries deleted. Non-expired entries preserved. Scheduler runs on configured interval.

### FR-014: Configuration Enhancements [MODIFY]
- **Current behavior**: `AnonymousProperties` has 6 fields (tokenTtl, sessionTtl, maxDataSize, maxRenewals, promotedDataTtl, rateLimit).
- **Expected behavior**: Add 2 new fields:
  - `val slidingWindowEnabled: Boolean = true` — feature flag for sliding window rate limiting
  - `val scanCount: Int = 100` — SCAN batch size (tunable for large datasets)
- **Validation**: Config has default values. Application starts without explicit configuration.

## 3. Non-Functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | Session creation P95 latency | < 50ms (from ~100ms) |
| NFR-002 | Accuracy | Rate limiting precision | Within 2% of true sliding window |
| NFR-003 | Performance | Data transfer P95 latency (10 keys) | < 10ms (from ~50ms) |
| NFR-004 | Reliability | Lock safety | Zero cross-process lock releases |
| NFR-005 | Performance | Data store with size check P95 | < 5ms (from ~20ms) |
| NFR-006 | Data Integrity | Size limit enforcement under concurrency | Zero over-limit writes |

## 4. API Changes

**None.** All optimizations are internal to Redis operations. Same endpoints, same request/response schemas, same HTTP status codes.

## 5. Traceability Matrix

| FR-ID | Action | Affected File(s) | Phase |
|-------|--------|------------------|-------|
| FR-001 | [MODIFY] | `AnonymousSessionHandler.kt` | 2 |
| FR-002 | [MODIFY] | `AnonymousRateLimitService.kt` | 2 |
| FR-003 | [MODIFY] | `AnonymousSessionDataService.kt` | 2 |
| FR-004 | [MODIFY] | `SessionPromotionService.kt` | 1 |
| FR-005 | [MODIFY] | `AnonymousSessionDataService.kt`, `AnonymousSessionHandler.kt` | 2 |
| FR-006 | [DEFERRED] | — | 4 |
| FR-007 | [MODIFY] | `AnonymousSessionDataService.kt` | 1 |
| FR-008 | [NEW] | `RedisLuaScriptConfig.kt` | 1 |
| FR-009 | [NEW] | `*.lua` files (3) | 1 |
| FR-010 | [MODIFY] | `AnonymousSessionHandler.kt` | 3 |
| FR-011 | [MODIFY] | `AnonymousRateLimitService.kt` | 3 |
| FR-012 | [DEFERRED] | — | 4 |
| FR-013 | [MODIFY] | `SessionCleanupScheduler.kt`, `Repositories.kt` | 3 |
| FR-014 | [MODIFY] | `SecurityProperties.kt` | 3 |

**Active FRs: 12/14** (FR-006 and FR-012 deferred)

## 6. Assumptions

- ⚠️ Assumption: Redis supports Lua EVAL (Redis 2.6+, available since 2012) — verified: project uses Redis 7+
- ⚠️ Assumption: Spring Data Redis `executePipelined()` available with Lettuce driver — verified: Lettuce is default driver for spring-boot-starter-data-redis
- ⚠️ Assumption: Single-node Redis deployment — Redlock unnecessary, simple UUID+SETNX sufficient
- ⚠️ Assumption: `DefaultRedisScript<Long>` SHA1 caching works transparently — documented in Spring Data Redis
- ⚠️ Assumption: Optimizations are internal only — no API contract changes needed

## 7. Open Questions

- ⚠️ OPEN QUESTION: Should `RedisLuaScriptConfig` define scripts as `DefaultRedisScript<Long>` or `RedisScript<Long>` interface? → **Recommendation**: `DefaultRedisScript<Long>` — concrete class with SHA1 caching, simpler bean definition.
- ⚠️ OPEN QUESTION: Should `atomic_data_store.lua` also handle `deleteData()` decrement? → **Recommendation**: Keep delete decrement in Kotlin — deleting already-gone data is safe, no atomicity needed.
- ⚠️ OPEN QUESTION: Should `SessionCleanupScheduler` be renamed to `AuthCleanupScheduler`? → **Recommendation**: Keep as-is — name change is cosmetic and could break references.
