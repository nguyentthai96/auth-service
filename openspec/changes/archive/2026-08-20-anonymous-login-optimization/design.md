# Design: Anonymous Login Optimization (MAINTENANCE — v2)

[CHANGED] Entire scope changed from archive v1 (JTI fix, ThreadLocal removal, metrics, tests) to v2 (Redis pipelining, Lua scripting, data integrity hardening). All archive v1 fixes are completed and deployed.

## Context

Auth-service anonymous login feature is **fully implemented and functionally correct** (post archive v1 fixes). Research analysis (`wf_feature_research`) identified 6 optimization opportunities and 2 critical data integrity bugs in Redis operations:

1. **🔴 TOCTOU race condition** in `AnonymousSessionDataService.storeData()` — non-atomic `getSessionDataSize()` → check → `set()` allows concurrent requests to exceed 64KB limit.
2. **🔴 Unsafe lock release** in `SessionPromotionService.releaseLock()` — simple `DELETE` with static `LOCK_VALUE = "locked"` enables cross-process lock release.
3. **🟡 Sequential HSET+EXPIRE** in `AnonymousSessionHandler` — 2 RTTs reducible to 1 via pipeline.
4. **🟡 Fixed-window rate limiting** in `AnonymousRateLimitService` — burst-at-boundary vulnerability.
5. **🟡 N+1 data transfer** in `AnonymousSessionDataService.transferData()` — O(2N) RTTs reducible to O(3).
6. **🟡 O(N) size calculation** in `getSessionDataSize()` — SCAN+STRLEN loop replaceable with O(1) HGET.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (`CommandHandler<C, R>`), Spring Boot 3.x + Kotlin
- Redis: `StringRedisTemplate` (Lettuce driver) — `executePipelined` and `RedisScript` NOT yet used anywhere in codebase
- All target files exist and are functional — optimization of working code
- Micrometer metrics already injected (archive v1) — no additional metrics work needed

### Selected Design Direction (from brainstorm_notes.md)
**Approach D — Balanced Optimization v3 — 3-Lua-Script Architecture with 4-Phase Delivery**: Combines critical integrity fixes (Phase 1), performance improvements (Phase 2), and operational hardening (Phase 3). Zero new dependencies — all optimizations use existing Spring Data Redis APIs.

## Goals / Non-Goals

**Goals:**
- Fix TOCTOU race condition — zero over-limit writes under concurrency (FR-007)
- Fix unsafe lock release — zero cross-process lock releases (FR-004)
- Reduce Redis RTTs via pipelining — session creation, data transfer (FR-001, FR-003)
- Improve rate limiting precision via sliding window Lua script (FR-002)
- Replace O(N) size calculation with O(1) running counter (FR-005)
- Introduce Lua script infrastructure for Redis atomic operations (FR-008, FR-009)
- Add fallback mechanisms for graceful degradation (FR-010, FR-011)
- Automated cleanup of expired token blacklist entries (FR-013)
- Configuration enhancements for operational flexibility (FR-014)

**Non-Goals:**
- API contract changes (all optimization is internal)
- Micrometer Observation spans (deferred — existing metrics adequate)
- Running counter periodic reconciliation (deferred — Lua atomicity eliminates primary drift)
- RenewAnonymousTokenHandler pipelining (deferred — low frequency, marginal gain)
- Redisson migration (overkill for targeted fixes, 8-12 dev-days vs 3-5)

## Decisions

### DD-101: 3-Lua-Script Architecture [NEW]
- **Decision**: Introduce 3 Lua scripts sharing common infrastructure (`RedisLuaScriptConfig`, `DefaultRedisScript<Long>`)
- **Scripts**: `sliding_window_rate_limit.lua`, `safe_lock_release.lua`, `atomic_data_store.lua`
- **Rationale**: Lua EVAL is guaranteed atomic — no retry loops, single RTT. Consistent pattern across all 3 use cases.
- **Alternative rejected**: WATCH/MULTI (requires `SessionCallback`, retry loops under contention, more complex error handling)

### DD-102: Lua over WATCH/MULTI for TOCTOU fix [NEW]
- **Decision**: Use Lua `atomic_data_store.lua` for atomic size check + data write + counter increment
- **Rationale**: Guaranteed atomic in single EVAL. Combines FR-005 (running counter) and FR-007 (TOCTOU fix) in one operation. Consistent with other Lua scripts.
- **Impact**: `storeData()` changes from 4+ RTTs to 2 RTTs (verifySession + Lua EVAL)

### DD-103: UUID lock value instead of Redlock [NEW]
- **Decision**: Replace static `LOCK_VALUE = "locked"` with UUID per acquisition. Release via Lua conditional DEL.
- **Rationale**: auth-service uses single Redis instance. Redlock requires 5+ nodes — massive overkill.
- **Impact**: `acquireLock()` stores UUID, `releaseLock()` passes UUID to Lua script for ownership verification.

### DD-104: `executePipelined(RedisCallback)` for batching [NEW]
- **Decision**: Use Spring Data Redis `executePipelined(RedisCallback)` for session creation and data transfer
- **Rationale**: Spring-idiomatic, connection pooling, error handling built-in. Both `executePipelined` and raw Lettuce pipeline are new patterns — `executePipelined` is simpler.
- **Alternative rejected**: Raw Lettuce connection pipeline — lower level, no Spring transaction/connection management

### DD-105: Feature flag for sliding window [NEW]
- **Decision**: Add `slidingWindowEnabled: Boolean = true` to `AnonymousProperties` for gradual rollout
- **Rationale**: Allows instant fallback to fixed-window. Reduces risk of Lua script issues in production.
- **Impact**: `AnonymousRateLimitService.checkRateLimit()` checks flag before using Lua.

### DD-106: Extend SessionCleanupScheduler (not rename) [NEW]
- **Decision**: Add second `@Scheduled` method to existing `SessionCleanupScheduler` for blacklist cleanup
- **Rationale**: Same concern domain (cleanup). Separate `@Scheduled` method with independent cron. Renaming to `AuthCleanupScheduler` is cosmetic and breaks references.
- **Impact**: `SessionCleanupScheduler` gains `TokenBlacklistRepository` dependency and `cleanupExpiredBlacklistEntries()` method.

### DD-107: `dataSize` backward compatibility [NEW]
- **Decision**: New sessions get `dataSize=0` in session hash. Pre-existing sessions without `dataSize` → HGET returns null → Lua treats as 0.
- **Rationale**: Zero migration needed. Sessions are ephemeral (24h TTL) — all sessions will have `dataSize` within 24h of deployment.

### DD-108: `DefaultRedisScript<Long>` for script beans [NEW]
- **Decision**: Use concrete `DefaultRedisScript<Long>` class, not `RedisScript<Long>` interface
- **Rationale**: `DefaultRedisScript` provides automatic SHA1 caching (EVALSHA optimization). Simpler bean definition.
- **Impact**: `RedisLuaScriptConfig` defines 3 `@Bean` methods returning `DefaultRedisScript<Long>`.

### DD-109: deleteData decrement in Kotlin (not Lua) [NEW]
- **Decision**: Keep `deleteData()` size decrement as Kotlin HINCRBY (negative) — no Lua script for delete
- **Rationale**: Deleting already-gone data is safe. No atomicity needed for delete — worst case is counter slightly high (conservative, data is gone, next store will still check).
- **Impact**: `deleteData()` adds `redisTemplate.opsForHash().increment(sessionKey, "dataSize", -deletedSize)`

## Component Mapping

### Modified Components

| Component | File | Action | FR(s) |
|-----------|------|--------|-------|
| `AnonymousSessionHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | [MODIFY] | FR-001, FR-005, FR-010 |
| `AnonymousRateLimitService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | [MODIFY] | FR-002, FR-011 |
| `AnonymousSessionDataService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | [MODIFY] | FR-003, FR-005, FR-007 |
| `SessionPromotionService` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | [MODIFY] | FR-004 |
| `SessionCleanupScheduler` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` | [MODIFY] | FR-013 |
| `SecurityProperties` | `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | [MODIFY] | FR-014 |
| `TokenBlacklistRepository` | `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` | [MODIFY] | FR-013 |

### New Components

| Component | File | FR(s) |
|-----------|------|-------|
| `RedisLuaScriptConfig` | `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` | FR-008 |
| `sliding_window_rate_limit.lua` | `src/main/resources/redis/sliding_window_rate_limit.lua` | FR-009 |
| `safe_lock_release.lua` | `src/main/resources/redis/safe_lock_release.lua` | FR-009 |
| `atomic_data_store.lua` | `src/main/resources/redis/atomic_data_store.lua` | FR-009 |

### Reused Components (no changes needed)

| Component | File | Usage |
|-----------|------|-------|
| `StringRedisTemplate` | Auto-configured Spring bean | All services — `executePipelined()`, `execute(RedisScript)`, `opsForHash()`, `opsForValue()` |
| `LoginHandler` | `src/main/kotlin/.../command/LoginHandler.kt` | Calls `SessionPromotionService.promoteSession()` — internal changes transparent |
| `RegisterHandler` | `src/main/kotlin/.../command/RegisterHandler.kt` | Same as LoginHandler — no API change |
| `AnonymousAuthController` | `src/main/kotlin/.../web/AnonymousAuthController.kt` | Calls handlers — all optimizations internal |
| `MeterRegistry` | Auto-configured Actuator bean | Already injected (archive v1) — no additional metrics needed |
| `RedisConfig` | `src/main/kotlin/.../config/RedisConfig.kt` | Existing `StringRedisTemplate` bean — no modification |

## Sequence Diagrams

### Session Creation with Pipeline (FR-001 + FR-005 + FR-010)

```
Client          AnonymousAuthController    AnonymousSessionHandler     AnonymousRateLimitService       Redis
  │                     │                         │                            │                         │
  │ POST /api/v1/auth/  │                         │                            │                         │
  │   anonymous         │                         │                            │                         │
  │────────────────────→│                         │                            │                         │
  │                     │ handle(command)          │                            │                         │
  │                     │────────────────────────→│                            │                         │
  │                     │                         │ checkRateLimit(ip)          │                         │
  │                     │                         │───────────────────────────→│                         │
  │                     │                         │                            │ EVAL sliding_window.lua  │
  │                     │                         │                            │ (if enabled)             │
  │                     │                         │                            │────────────────────────→│
  │                     │                         │                            │       count or -1       │
  │                     │                         │                            │←────────────────────────│
  │                     │                         │      OK (under limit)      │                         │
  │                     │                         │←───────────────────────────│                         │
  │                     │                         │                            │                         │
  │                     │                         │ sessionData = {...,        │                         │ │
  │                     │                         │ else → success                            │
  │ 200 OK / 413        │                         │                                          │
  │←────────────────────│                         │                                          │
```

### Promotion with UUID Lock + Lua Safe Release (FR-004) + Pipeline Transfer (FR-003)

```
LoginHandler       SessionPromotionService    AnonymousSessionDataService       Redis              DB
  │                       │                         │                              │                │
  │ promoteSession(       │                         │                              │                │
  │   sid, userId, jti)   │                         │                              │                │
  │──────────────────────→│                         │                              │                │
  │                       │ ownerUUID = UUID()      │                              │                │
  │                       │ SETNX lockKey           │                              │                │
  │                       │   ownerUUID (30s TTL)   │ ← UUID, not "locked" (FR-004)│                │
  │                       │──────────────────────────────────────────────────────→│                │
  │                       │       true (acquired)   │                              │                │
  │                       │←──────────────────────────────────────────────────────│                │
  │                       │                         │                              │                │
  │                       │ transferData(sid,uid)   │                              │                │
  │                       │────────────────────────→│                              │                │
  │                       │                         │ SCAN "anon:data:{sid}:*"     │                │
  │                       │                         │──────────────────────────────→│                │
  │                       │                         │       [key1, key2, ...]       │                │
  │                       │                         │←──────────────────────────────│                │
  │                       │                         │ executePipelined { GET all }  │ ← FR-003      │
  │                       │                         │──────────────────────────────→│                │
  │                       │                         │       [val1, val2, ...]       │                │
  │                       │                         │←──────────────────────────────│                │
  │                       │                         │ executePipelined { SET all }  │ ← FR-003      │
  │                       │                         │──────────────────────────────→│                │
  │                       │                         │       OK                      │                │
  │                       │                         │←──────────────────────────────│                │
  │                       │ DataTransferResult      │                              │                │
  │                       │←────────────────────────│                              │                │
  │                       │                         │                              │                │
  │                       │ blacklist(jti)           │                              │                │
  │                       │───────────────────────────────────────────────────────────────────────→│
  │                       │ delete session + data    │                              │                │
  │                       │──────────────────────────────────────────────────────→│                │
  │                       │                         │                              │                │
  │                       │ EVAL safe_lock_release   │                              │                │
  │                       │   KEY = lockKey          │                              │                     │
│  │   _store.lua         │  │ • EVAL safe_lock     │                   │
│  │ • executePipelined   │  │   _release.lua       │                   │
│  │   MGET+MSET          │  │ • promoteSession()   │                   │
│  │ • HGET dataSize O(1) │  │   unchanged API      │                   │
│  │ • deleteData HINCRBY │  │                      │                   │
│  └──────────────────────┘  └──────────────────────┘                   │
│                                                                        │
│  ┌──────────────────────┐  ┌──────────────────────┐                   │
│  │ SessionCleanup       │  │ SecurityProperties   │                   │
│  │ Scheduler            │  │ .AnonymousProperties │                   │
│  │ [FR-013]             │  │ [FR-014]             │                   │
│  │                      │  │                      │                   │
│  │ + cleanupExpired     │  │ + slidingWindow      │                   │
│  │   BlacklistEntries() │  │   Enabled: Boolean   │                   │
│  │ + TokenBlacklistRepo │  │ + scanCount: Int     │                   │
│  └──────────────────────┘  └──────────────────────┘                   │
└────────────────────────────────────────────────────────────────────────┘
```

## Lua Script Designs

### Script 1: `sliding_window_rate_limit.lua` (FR-002)

```
KEYS[1] = anon:rate:{ip}:{currentWindow}
KEYS[2] = anon:rate:{ip}:{prevWindow}
ARGV[1] = maxAttempts
ARGV[2] = windowSeconds
ARGV[3] = elapsedSeconds (seconds elapsed in current window)

Algorithm:
  1. INCR KEYS[1] (current window counter)
  2. If first increment → EXPIRE KEYS[1] to 2×windowSeconds
  3. GET KEYS[2] (previous window counter, or 0)
  4. weight = max(0, (windowSeconds - elapsedSeconds) / windowSeconds)
  5. count = prev × weight + current
  6. If count > maxAttempts → return -1 (denied)
  7. Return floor(count) (allowed)

Returns: Long (-1 = denied, ≥0 = weighted count)
Lines: ~27 | Complexity: MEDIUM
```

### Script 2: `safe_lock_release.lua` (FR-004)

```
KEYS[1] = anon:lock:{sessionId}
ARGV[1] = ownerUUID

Algorithm:
  1. If GET(KEYS[1]) == ARGV[1] → DEL(KEYS[1]) → return 1
  2. Else → return 0 (not owner, do not delete)

Returns: Long (1 = released, 0 = not owner)
Lines: ~8 | Complexity: TRIVIAL
```

### Script 3: `atomic_data_store.lua` (FR-007 + FR-005)

```
KEYS[1] = anon:session:{sessionId} (hash with dataSize field)
KEYS[2] = anon:data:{sessionId}:{namespace}:{key} (data key to write)
ARGV[1] = maxDataSizeBytes
ARGV[2] = value (data to store)
ARGV[3] = ttlSeconds (TTL for data key)

Algorithm:
  1. currentSize = tonumber(HGET(KEYS[1], "dataSize")) or 0
  2. newSize = #ARGV[2] (string length in bytes)
  3. If currentSize + newSize > tonumber(ARGV[1]) → return -1
  4. SET KEYS[2] ARGV[2] EX tonumber(ARGV[3])
  5. HINCRBY KEYS[1] "dataSize" newSize
  6. Return currentSize + newSize

Returns: Long (-1 = limit exceeded, ≥0 = new total size)
Lines: ~15 | Complexity: MEDIUM
```

## Key Pattern Changes

| Pattern | Before | After | Migration |
|---------|--------|-------|-----------|
| Rate limit key | `anon:rate:{ip}` | `anon:rate:{ip}:{windowId}` | Auto — old keys expire via existing TTL (1h max) |
| Lock value | `"locked"` (static) | UUID string (per acquisition) | Auto — lock keys are ephemeral (30s TTL) |
| Session hash | `{deviceFP, ip, createdAt, renewalCount}` | + `dataSize` field | Backward compatible — HGET null → 0 |
| Size check | SCAN+STRLEN O(N) | HGET dataSize O(1) | Atomic via `atomic_data_store.lua` |
| Data store | getSize() → check → set() (TOCTOU) | EVAL atomic check-and-set (Lua) | Atomic replacement |

## RTT Improvements

```
Session Creation (POST /api/v1/auth/anonymous):
  Before: INCR(1) + EXPIRE(1) + HSET(1) + EXPIRE(1) = 3-4 RTT
  After:  EVAL lua(1) + executePipelined[HSET+EXPIRE](1) = 2 RTT
  Improvement: -50% RTT

Data Store (PUT /api/v1/auth/anonymous/session/data):
  Before: SCAN(1+) + N×STRLEN + hasKey(1) + getExpire(1) + SET(1) = 4+N RTT
  After:  hasKey(1) + getExpire(1) + EVAL atomic_data_store.lua(1) = 3 RTT
  Improvement: -50% to -90% RTT (depends on N)

Data Transfer / Promotion (POST /api/v1/auth/login):
  Before: SETNX(1) + SCAN(1+) + N×GET + N×SET + DEL(session) + DEL(lock) = 4+2N RTT
  After:  SETNX(1) + SCAN(1) + pipeline[N×GET](1) + pipeline[N×SET](1) + DEL(session) + EVAL lua(1) = 5 RTT
  Improvement: -60% to -95% RTT (depends on N)
```

## Implementation Scorecard

```
BEFORE OPTIMIZATION (current state):
═══════════════════════════════════
  Functional Completeness     ████████████████████  100%  ✅
  Performance (RTT)           ██████████████░░░░░░   65%  🟡 (N+1 patterns)
  Data Integrity              ███████████████░░░░░   75%  🔴 (TOCTOU, unsafe lock)
  Lock Safety                 ████████████░░░░░░░░   60%  🔴 (cross-process risk)
  Rate Limit Precision        ██████████████████░░   90%  🟡 (burst-at-boundary)
  Operational                 ███████████████░░░░░   75%  🟡 (no cleanup, no flags)
  OVERALL                                           78%  🟡

AFTER OPTIMIZATION (target state):
═══════════════════════════════════
  Functional Completeness     ████████████████████  100%  ✅
  Performance (RTT)           ████████████████████   95%  ✅ (pipelined)
  Data Integrity              ████████████████████  100%  ✅ (Lua atomic)
  Lock Safety                 ████████████████████  100%  ✅ (UUID + Lua)
  Rate Limit Precision        ████████████████████   98%  ✅ (sliding window)
  Operational                 ████████████████████   95%  ✅ (cleanup, config)
  OVERALL                                           97%  ✅
```
