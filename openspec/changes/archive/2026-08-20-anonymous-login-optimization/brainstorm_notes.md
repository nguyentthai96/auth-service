---
type: brainstorm_notes
change: anonymous-login-optimization
date: 2025-07-15
selected_direction: "Balanced Optimization v3 — 3-Lua-Script Architecture with 4-Phase Delivery"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Anonymous Login Optimization (v3)

## Date
2025-07-15

## Context

The anonymous login feature is **fully implemented** in the auth-service codebase. This v3 brainstorm synthesizes all prior research, codebase investigation, and the refined `pre_openspec.md` with 14 FRs (9 research + 5 enriched).

**Trigger**: Post-implementation review + formal research (`wf_feature_research`) identified 6 optimization areas and 2 critical correctness bugs:
1. ⚠️ **TOCTOU race condition** in `AnonymousSessionDataService.storeData()` (lines 37-57)
2. ⚠️ **Unsafe lock release** in `SessionPromotionService.releaseLock()` (lines 140-147)
3. Redis pipelining opportunity in `AnonymousSessionHandler` (lines 64-65)
4. Sliding window rate limiting to replace fixed-window (lines 44-54 in `AnonymousRateLimitService`)
5. Batch data transfer replacing per-key N+1 loop (lines 90-115 in `AnonymousSessionDataService`)
6. Running data size counter replacing O(N) SCAN+STRLEN (lines 129-147)

**Key inputs**:
- `pre_openspec.md`: 14 FRs, Quality Score 88/100, MAINTENANCE classification confirmed
- `handoff_summary.md`: "Build in-place optimizations" recommendation, 3-5 dev-days estimate
- `comparison_analysis.md`: Spring Data Redis Pipeline+Lua scored 8.85/10 vs Redisson 6.35/10
- `technical_spec.md`: Detailed code change examples, 2 Lua script designs, 15 test cases
- Direct codebase verification: all 6 optimization targets confirmed at exact line numbers

## Questions Asked & Answers

- Q1: Is the feature functional and covering all requirements? → A: **Yes.** All base FRs are implemented. Unit tests exist for all anonymous services. Integration tests cover anonymous session flow and promotion flow. This is a MAINTENANCE optimization.

- Q2: What are the main performance bottlenecks? → A: **Redis N+1 query patterns.** Both `transferData()` (line 90-115: SCAN → per-key GET+SET) and `getSessionDataSize()` (line 129-147: SCAN → per-key STRLEN) use N+1 patterns. `AnonymousSessionHandler.handle()` (lines 64-65) makes 2 sequential Redis calls that can be pipelined. Rate limiting (lines 44-54) uses 2 Redis calls that can be reduced to 1 Lua EVAL.

- Q3: Are there critical race conditions? → A: **Yes, two confirmed:**
  1. **TOCTOU in `storeData()`**: `getSessionDataSize()` scans and sums STRLENs (O(N) SCAN), then check limit, then `set()`. Between check and write, concurrent requests can exceed 64KB. No MULTI/WATCH or Lua protects this.
  2. **Unsafe lock release**: `releaseLock()` does simple `DELETE` on lock key with static value `"locked"`. If lock TTL expires and another process acquires it, first process's `finally` block deletes wrong lock.

- Q4: Should TOCTOU fix use Lua or WATCH/MULTI? → A: **Lua atomic check-and-set (`atomic_data_store.lua`).** Reasoning:
  - The codebase is already introducing 2 Lua scripts (FR-002 sliding window, FR-004 safe lock release) — Lua is not a new pattern
  - `WATCH/MULTI` in Spring Data Redis requires `SessionCallback` interface, retry loops under contention, and more complex error handling
  - Lua EVAL is guaranteed atomic — zero retry overhead, single RTT
  - All 3 scripts share the same infrastructure (`RedisLuaScriptConfig`, `DefaultRedisScript<Long>`)
  - **Decision**: Lua script — simpler, guaranteed atomic, consistent architecture

- Q5: Should lock release use UUID + Lua or Redlock? → A: **UUID + Lua conditional DEL.** Reasoning:
  - auth-service uses single Redis instance (confirmed in deployment config)
  - Redlock requires 5+ Redis instances — massive overkill for single-node
  - UUID ownership + Lua conditional DEL is the standard single-node safe lock pattern (Kleppmann)
  - **Decision**: UUID lock value + `safe_lock_release.lua`

- Q6: Should pipelining use `executePipelined()` or raw Lettuce connection? → A: **`executePipelined(RedisCallback)`** — Spring-idiomatic, consistent with existing `StringRedisTemplate` usage. Both are new patterns (confirmed: `executePipelined` not found in codebase), but `executePipelined` provides connection pooling and error handling automatically.

- Q7: Should we add a 3rd Lua script for TOCTOU, or combine with FR-005 running counter? → A: **Add `atomic_data_store.lua` (3rd script)** that atomically does: HGET dataSize → check limit → SET data → HINCRBY dataSize. This combines FR-005 (running counter) and FR-007 (TOCTOU fix) into a single atomic operation. The script operates on known key names (no SCAN/KEYS inside Lua).

- Q8: What about token_blacklist table growth? → A: Every renewal (max 24/session) + every promotion writes a row to `token_blacklist`. No cleanup exists (`TokenBlacklistRepository` only has `existsByTokenJti()`). **Decision**: Extend `SessionCleanupScheduler` with a second `@Scheduled` method for blacklist cleanup. Need to add `deleteByExpiresAtBefore(Instant)` to `TokenBlacklistRepository`.

- Q9: Should FR-012 (counter reconciliation) be in initial scope? → A: **Defer to follow-up.** Reasoning:
  - The `atomic_data_store.lua` makes write + increment atomic — the primary drift risk is eliminated
  - Redis sessions are ephemeral (max 24h TTL) — any residual drift from edge cases (Redis crash mid-Lua, extremely unlikely) is temporary
  - Reconciliation adds complexity (scheduled task, threshold detection, counter reset) for a low-probability edge case
  - **Decision**: Deferred to Phase 4 / follow-up. Listed as nice-to-have.

- Q10: Should FR-006 (Micrometer Observation spans) be in initial scope? → A: **Deferred to Phase 4.** Existing metrics (`Counter`, `Timer.start()`) already provide comprehensive operational visibility. Observation spans are an incremental improvement, not a critical fix. Priority goes to correctness (Phase 1) and performance (Phase 2).

- Q11: Should RenewAnonymousTokenHandler get pipelining? → A: **Deferred.** Lines 85-88 make 3 sequential Redis calls. Pipelining would save 2 RTTs, but renewal is low-frequency (max 24/session over 24h). Marginal gain, low priority.

- Q12: How many Lua scripts in total? → A: **3 scripts:**
  1. `sliding_window_rate_limit.lua` — FR-002 (27 lines, 2 KEYS, 3 ARGV)
  2. `safe_lock_release.lua` — FR-004 (8 lines, 1 KEY, 1 ARGV)
  3. `atomic_data_store.lua` — FR-007 + FR-005 (15 lines, 2 KEYS, 3 ARGV)

## Approaches Considered

### Approach A: Performance-Only (Pipeline + Batch)
Focus on Redis pipelining and batch operations only.

- **Scope**: FR-001 (pipeline session), FR-003 (batch transfer), FR-005 (running counter)
- **Pros**: Direct P95 latency improvements; low risk; measurable
- **Cons**: **Misses 2 critical correctness bugs** (TOCTOU, unsafe lock release); incomplete
- **Score**: 5/10 — correctness > performance

### Approach B: Data Integrity Only (Lua + Safety)
Focus on correctness fixes only.

- **Scope**: FR-004 (safe lock), FR-007 (TOCTOU fix), FR-008/FR-009 (Lua infrastructure)
- **Pros**: Eliminates real race conditions; correctness-first
- **Cons**: Misses easy performance wins; leaves N+1 patterns intact
- **Score**: 6/10 — necessary but insufficient

### Approach C: Full Redisson Migration
Replace `StringRedisTemplate` with Redisson for all Redis operations.

- **Scope**: Replace entire Redis layer
- **Pros**: Distributed objects, rate limiters, locks built-in; comprehensive
- **Cons**: 8-12 dev-days (vs 3-5); major migration risk; new dependency (`io.redisson`); overkill for targeted fixes; breaks existing patterns used across entire auth-service
- **Score**: 6.35/10 (from comparison_analysis.md)

### Approach D: Balanced Optimization (SELECTED) ★
Combine critical integrity fixes + key performance optimizations + operational hardening.

- **Scope**: All 14 FRs, phased delivery
- **Pros**: Correctness bugs fixed first (Phase 1); consistent Lua architecture across 3 scripts; zero new dependencies; backward-compatible; incremental delivery — each phase complete and tested independently
- **Cons**: Introduces 3 new patterns (executePipelined, RedisScript, Lua files) — learning curve
- **Score**: 8.85/10 (from comparison_analysis.md)

## Selected Direction

**Approach D: Balanced Optimization v3** — 3-Lua-Script Architecture with 4-Phase Delivery.

### Why This Approach

```
Decision Matrix (weighted scoring from comparison_analysis.md):

┌───────────────────────┬────────┬───────────────┬──────────┬──────────┐
│ Criterion             │ Weight │ Pipeline+Lua  │ Redisson │ No Change│
├───────────────────────┼────────┼───────────────┼──────────┼──────────┤
│ Feature coverage      │  30%   │      9        │    9     │    2     │
│ Integration ease      │  25%   │     10        │    3     │   10     │
│ Maintenan
  │                  │ │                  │ │                  │
  │ checkRateLimit() │ │ releaseLock()    │ │ storeData()      │
  │   → EVAL lua     │ │   → EVAL lua     │ │   → EVAL lua     │
  └──────────────────┘ └──────────────────┘ └──────────────────┘
            │                    │                     │
            ▼                    ▼                     ▼
  ┌──────────────────────────────────────────────────────────────┐
  │           resources/redis/                                   │
  │  ┌────────────────────────────┐                              │
  │  │ sliding_window_rate_       │  2 KEYS, 3 ARGV             │
  │  │ limit.lua (~27 lines)     │  Returns: count or -1        │
  │  ├────────────────────────────┤                              │
  │  │ safe_lock_release.lua     │  1 KEY, 1 ARGV               │
  │  │ (~8 lines)                │  Returns: 1 or 0             │
  │  ├────────────────────────────┤                              │
  │  │ atomic_data_store.lua     │  2 KEYS, 3 ARGV              │
  │  │ (~15 lines)               │  Returns: newTotal or -1     │
  │  └────────────────────────────┘                              │
  └──────────────────────────────────────────────────────────────┘
```

### 4-Phase Implementation Plan

```
Phase 1 — Data Integrity (MUST, ~1-2 days)
═══════════════════════════════════════════
 FR-004: UUID lock value + Lua safe release
 FR-007: Lua atomic check-and-set (TOCTOU fix)
 FR-008: RedisLuaScriptConfig @Configuration
 FR-009: 3 Lua script files
 
Phase 2 — Performance (~1-2 days)
═══════════════════════════════════════════
 FR-001: Pipeline HSET+EXPIRE in AnonymousSessionHandler
 FR-002: Sliding window Lua rate limiting
 FR-003: Pipeline MGET+MSET for batch transfer
 FR-005: Running dataSize counter (via atomic_data_store.lua)

Phase 3 — Reliability & Operations (~1 day)
═══════════════════════════════════════════
 FR-010: Pipeline fallback (try/catch → sequential)
 FR-011: Lua script fallback (try/catch → fixed-window)
 FR-013: TokenBlacklist cleanup scheduler
 FR-014: Config enhancements (slidingWindowEnabled, scanCount)

Phase 4 — Nice-to-have (deferred)
═══════════════════════════════════════════
 FR-006: Micrometer Observation spans
 FR-012: Running counter periodic reconciliation
 RenewAnonymousTokenHandler pipelining
```

### Key Design Decisions

| ID | Decision | Rationale |
|----|----------|-----------|
| DD-01 | Lua over WATCH/MULTI for TOCTOU | Guaranteed atomic, no retry loops, consistent with other Lua scripts |
| DD-02 | UUID lock value (not Redlock) | Single Redis instance; Redlock requires 5+ nodes |
| DD-03 | `executePipelined(RedisCallback)` | Spring-idiomatic, connection pooling, error handling |
| DD-04 | 3rd Lua script combining FR-005 + FR-007 | Atomic size check + write + increment in single EVAL |
| DD-05 | Extend SessionCleanupScheduler | Same concern domain, separate @Scheduled method, 6h interval |
| DD-06 | Defer FR-006/FR-012 | Lua atomicity eliminates primary drift risk; existing metrics adequate |
| DD-07 | Feature flag `slidingWindowEnabled` | Gradual rollout, instant fallback to fixed-window |
| DD-08 | `dataSize` backward compatibility | HGET returns null → treat as 0 for pre-existing sessions |

## Pre-classifications (preliminary)
- Feature type: **MAINTENANCE** (confirmed — all code exists, optimizing/hardening)
- Flow type: **Non-Financial** (no monetary transactions, session lifecycle operations)
- Affected modules:
  - `auth.application` — `SessionPromotionService` (MODIFY), `AnonymousSessionDataService` (MODIFY), `AnonymousRateLimitService` (MODIFY), `SessionCleanupScheduler` (MODIFY)
  - `auth.application.command` — `AnonymousSessionHandler` (MODIFY)
  - `shared.config` — `SecurityProperties.AnonymousProperties` (MODIFY), NEW `RedisLuaScriptConfig` (CREATE)
  - `resources/redis/` — NEW `sliding_window_rate_limit.lua`, `safe_lock_release.lua`, `atomic_data_store.lua` (CREATE)
  - `rbac.adapter.out.persistence.repository` — `TokenBlacklistRepository` (MODIFY — add `deleteByExpiresAtBefore`)

## Codebase Findings

### Verification Results (all confirmed via grep/view_file)

| Check | Result | Evidence |
|-------|:---:|---|
| `executePipelined` exists? | ❌ NEW | `grep executePipelined` → 0 results |
| `RedisScript` exists? | ❌ NEW | `grep RedisScript` → 0 results |
| `.lua` files exist? | ❌ NEW | No `.lua` files in `src/main/resources/` |
| `spring-boot-starter-data-redis` | ✅ | `build.gradle.kts` line 37 |
| Lettuce driver (default) | ✅ | `spring-boot-starter-data-redis` includes Lettuce |
| `LOCK_VALUE = "locked"` | ✅ CONFIRMED | `SessionPromotionService.kt` line 38 |
| TOCTOU pattern | ✅ CONFIRMED | `AnonymousSessionDataService.kt` lines 37-57 |
| Sequential HSET+EXPIRE | ✅ CONFIRMED | `AnonymousSessionHandler.kt` lines 64-65 |
| Fixed-window INCR+EXPIRE | ✅ CONFIRMED | `AnonymousRateLimitService.kt` lines 44-54 |
| Per-key transfer loop | ✅ CONFIRMED | `AnonymousSessionDataService.kt` lines 90-115 |
| SCAN+STRLEN size calc | ✅ CONFIRMED | `AnonymousSessionDataService.kt` lines 129-147 |
| `deleteByExpiresAtBefore` | ❌ NEW | `TokenBlacklistRepository` only has `existsByTokenJti()` |
| Micrometer Observation | ❌ NEW | No `Observation` usage in codebase |

### RTT Analysis — Before vs After

```
Session Creation (POST /api/v1/auth/anonymous):
  Before: INCR(1) + EXPIRE(1) + HSET(1) + EXPIRE(1) = 3-4 RTT
  After:  EVAL lua(1) + executePipelined[HSET+EXPIRE](1) = 2 RTT
  Improvement: -50% RTT

Data Store (PUT /api/v1/auth/anonymous/session/data):
  Before: SCAN(1+) + N×STRLEN + hasKey(1) + getExpire(1) + SET(1) = 4+N RTT
  After:  hasKey(1) + EVAL atomic_data_store.lua(1) = 2 RTT
  Improvement: -50% to -90% RTT (depends on N)

Data Transfer / Promotion (POST /api/v1/auth/login):
  Before: SETNX(1) + SCAN(1+) + N×GET + N×SET + DEL(session) + DEL(lock) = 4+2N RTT
  After:  SETNX(1) + SCAN(1) + pipeline[N×GET](1) + pipeline[N×SET](1) + DEL(session) + EVAL lua(1) = 5 RTT
  Improvement: -60% to -95% RTT (depends on N)
```

### Lua Script Designs

#### Script 1: `sliding_window_rate_limit.lua` (FR-002)
```
KEYS[1] = anon:rate:{ip}:{currentWindow}
KEYS[2] = anon:rate:{ip}:{prevWindow}
ARGV[1] = maxAttempts, ARGV[2] = windowSeconds, ARGV[3] = elapsedSeconds

Flow: INCR current → (if first) EXPIRE 2×window
      GET prev → calculate weight = max(0, (window-elapsed)/window)
      count = prev × weight + current
      if count > max → return -1 (denied)
      return floor(count) (allowed)

Lines: ~27   |   Complexity: MEDIUM   |   Returns: Long
```

#### Script 2: `safe_lock_release.lua` (FR-004)
```
KEYS[1] = anon:lock:{sessionId}
ARGV[1] = ownerUUID

Flow: if GET(key) == uuid → DEL(key) → return 1
      else → return 0 (not owner)

Lines: ~8   |   Complexity: TRIVIAL   |   Returns: Long
```

#### Script 3: `atomic_data_store.lua` (FR-007 + FR-005)
```
KEYS[1] = anon:session:{sessionId}  (hash with dataSize field)
KEYS[2] = anon:data:{sessionId}:{namespace}:{key}  (data key to write)
ARGV[1] = maxDataSizeBytes
ARGV[2] = value (data to store)
ARGV[3] = ttlSeconds (TTL for data key)

Flow: currentSize = tonumber(HGET KEYS[1] "dataSize") or 0
      newSize = #ARGV[2]  (string length in bytes)
      if currentSize + newSize > maxDataSizeBytes → return -1 (limit exceeded)
      SET KEYS[2] ARGV[2] EX ARGV[3]
      HINCRBY KEYS[1] "dataSize" newSize
      return currentSize + newSize

Lines: ~15   |   Complexity: MEDIUM   |   Returns: Long (-1 = denied, ≥0 = new total)
```

### Key Pattern Migration

| Pattern | Before | After | Migration Strategy |
|---------|--------|-------|--------------------|
| Rate limit key | `anon:rate:{ip}` | `anon:rate:{ip}:{windowId}` | Auto — old keys expire via existing TTL (1h max) |
| Lock value | `"locked"` (static) | UUID string | Auto — lock keys are ephemeral (30s TTL) |
| Session hash | `{deviceFP, ip, createdAt, renewalCount}` | + `dataSize` field | Backward compatible — HGET returns null → treat as 0 |
| Size check | SCAN+STRLEN O(N) | HGET dataSize O(1) | Atomic via `atomic_data_store.lua` |
| Data store | getSize() → check → set() (TOCTOU) | EVAL atomic check-and-set (Lua) | Atomic replacement |

### Pattern Reuse Opportunities

| Current Pattern | Optimization | Reuse Target |
|----------------|--------------|--------------|
| Fixed-window INCR+EXPIRE in `AnonymousRateLimitService` | Sliding window Lua | `LoginRateLimitService` (identical pattern at lines 19-80) |
| Fixed-window INCR+EXPIRE in `AnonymousRateLimitService` | Sliding window Lua | `MfaRateLimitService` (identical pattern) |
| Sequential Redis calls | Pipeline | `RenewAnonymousTokenHandler` (lines 85-88, 3 sequential calls) |

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|---|
| Lua script error in production | LOW | MEDIUM | Comprehensive unit tests; SHA1 caching; fallback to fixed-window (FR-011) |
| Pipeline breaking behavior | VERY LOW | LOW | Pipeline returns in order; integration tests; fallback to sequential (FR-010) |
| Running counter drift | LOW | LOW | Lua atomicity eliminates primary cause; sessions are ephemeral (24h TTL) |
| Redis version incompatibility | VERY LOW | HIGH | Lua EVAL since Redis 2.6 (2012); project uses Redis 7+ |
| New pattern learning curve | MEDIUM | LOW | 3 patterns (executePipelined, RedisScript, Lua) — well-documented in Spring Data Redis docs |

## Open Questions for Design Phase
- [RESOLVED] FR-007 approach: **Lua atomic check-and-set** — consistent with FR-002/FR-004, guaranteed atomic
- [RESOLVED] FR-003 pipeline: **`executePipelined(RedisCallback)`** — Spring-idiomatic
- [RESOLVED] FR-013 scheduler: **Extend `SessionCleanupScheduler`** with second `@Scheduled` method
- [RESOLVED] Lua SCAN/KEYS: **N/A** — Lua scripts operate on known key names, SCAN stays in Kotlin
- [RESOLVED] Lock ownership: **UUID value + Lua conditional DEL** — standard single-instance pattern
- [RESOLVED] Counter reconciliation: **Deferred** — Lua atomicity eliminates primary drift risk
- [RESOLVED] Observation spans: **Deferred** — existing metrics adequate for now
- [OPEN] Should `RedisLuaScriptConfig` define scripts as `DefaultRedisScript<Long>` or `RedisScript<Long>` interface? → **Recommendation**: `DefaultRedisScript<Long>` — concrete class with SHA1 caching, simpler bean definition. Design phase should confirm.
- [OPEN] Should `atomic_data_store.lua` also handle `deleteData()` decrement (HINCRBY negative), or keep delete decrement in Kotlin? → **Recommendation**: Keep in Kotlin — delete doesn't need atomicity (deleting data that's already gone is safe). A separate Lua for delete is over-engineering.
- [OPEN] Should `SessionCleanupScheduler` be renamed to `AuthCleanupScheduler`? → **Recommendation**: Keep as-is — name change is cosmetic and could break references. Add method name that's self-documenting.

## Open Questions for URD Analysis
- None — URD analysis completed via `wf_pre_openspec` with quality score 88/100.

## Cross-Cutting Considerations

### Test Strategy
```
Unit Tests:
  - Mock StringRedisTemplate to verify Lua script invocation with correct KEYS/ARGV
  - Verify pipeline callback structure
  - Test sliding window formula at boundary conditions

Integration Tests:
  - Embedded Redis (Testcontainers) for pipeline batching verification
  - Lua script execution with real Redis
  - End-to-end session creation → store data → promotion

Concurrency Tests:
  - CountDownLatch + ExecutorService with N threads for TOCTOU fix verification
  - Simulate lock TTL expiry + concurrent acquisition for safe lock release
  - Verify size limit enforcement under concurrent storeData() calls
```

### Backward Compatibility
- **New sessions**: Get `dataSize=0` in session hash — fully supported
- **Existing sessions** (pre-optimization): No `dataSize` field → HGET returns null → Lua treats as 0 → compatible
- **Rate limit keys**: New pattern `anon:rate:{ip}:{windowId}` ≠ old `anon:rate:{ip}` — no conflict, old keys auto-expire
- **Lock keys**: Ephemeral (30s TTL) — no migration needed
- **API contracts**: Zero changes — all optimization is internal
