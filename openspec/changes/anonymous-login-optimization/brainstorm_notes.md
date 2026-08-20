---
type: brainstorm_notes
change: anonymous-login-optimization
date: 2025-01-20
selected_direction: "Balanced Optimization — Critical Data Integrity Fixes + Redis Performance + Operational Hardening"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Anonymous Login Optimization

## Date
2025-01-20

## Context
The anonymous login feature is **fully implemented** in the auth-service codebase. All 13 FRs from pre_openspec.md are implemented with [REUSE] status. The feature classification is MAINTENANCE — scope is optimization, refinement, bug fixes, and hardening of existing code.

Research artifacts (handoff_summary.md, business_analysis.md, technical_spec.md, comparison_analysis.md) were the input source. Codebase investigation confirmed all planned classes exist and follow consistent patterns (CQRS CommandHandler, Redis INCR+EXPIRE, StringRedisTemplate, Micrometer metrics).

**Trigger for optimization**: Post-implementation review identified several areas where the current code could be improved for production readiness — performance, data integrity edge cases, and operational monitoring.

## Questions Asked & Answers

- Q1: Is the feature functional and covering all requirements? → A: Yes. All 13 FRs are implemented. Unit tests exist for `AnonymousRateLimitService`, `AnonymousSessionDataService`, `SessionPromotionService`, and `JwtService` anonymous methods. Integration tests exist for anonymous session flow and session promotion flow.

- Q2: What are the main performance bottlenecks? → A: **Redis SCAN operations** in `AnonymousSessionDataService`. Both `transferData()` and `getSessionDataSize()` use SCAN with count=100 + individual GET/STRLEN per key. This is an N+1 query pattern — for sessions with many data keys, performance degrades linearly.

- Q3: Are there any race conditions or data integrity concerns? → A: Yes. **`storeData()` has a TOCTOU (time-of-check-time-of-use) race condition**: `getSessionDataSize()` is called first (SCAN + sum STRLENs), then the actual SET happens. Between check and write, concurrent requests could exceed the 64KB limit. No MULTI/WATCH or Lua script protects this window.

- Q4: Is the distributed lock in SessionPromotionService safe? → A: **Partially**. `releaseLock()` does a simple `DELETE` on the lock key without verifying ownership. If the lock TTL expires and another process acquires the lock, the first process's `finally` block will delete the second process's lock. This is a classic Redis distributed lock anti-pattern. Should use a unique lock value + Lua script for safe release (Redlock pattern).

- Q5: What about token_blacklist table growth? → A: Every token renewal (max 24 per session) and every promotion writes a row to PostgreSQL `token_blacklist`. With high anonymous traffic, this table can grow rapidly. The `expiresAt` field is set but no scheduled cleanup job was found specifically for expired blacklist entries. Redis TTL handles anonymous session cleanup, but PostgreSQL rows persist.

- Q6: Are there missing API capabilities for client efficiency? → A: The current API only supports single-key CRUD (`PUT /session/data` with one namespace:key pair). Clients needing to store/retrieve multiple items must make N HTTP requests. A batch endpoint would reduce round-trips.

- Q7: Is the fail-open rate limiting acceptable? → A: Yes for availability, but needs **alerting**. `AnonymousRateLimitService` catches `RedisConnectionFailureException` and logs it, but there's no circuit breaker or alert trigger. In a prolonged Redis outage, unlimited anonymous tokens could be created without any rate limit — potential abuse vector.

- Q8: How does the current metrics setup look? → A: Micrometer counters and timers are comprehensive: `sessions.created`, `sessions.renewed`, `sessions.promoted` (with status tags), `data.stored`, `data.size_exceeded`, `rate_limited`, `token.generation.duration`, `promotion.duration`. However, no **Grafana dashboard** or **alert rules** are defined in the repository.

## Approaches Considered

### Approach A: Performance Optimization
Focus on Redis SCAN optimization, Redis pipelining, and batch API endpoints.

**Key changes:**
- Replace SCAN + individual GET/STRLEN with Redis pipeline or Lua script in `transferData()` and `getSessionDataSize()`
- Add batch PUT/GET endpoint for session data
- Tune SCAN count parameter (100 → configurable or higher)

**Pros:**
- Directly reduces latency for data-heavy anonymous sessions
- Reduces Redis round-trips significantly (N+1 → 1-2 calls)
- Batch API improves client DX

**Cons:**
- Lua scripts add operational complexity
- Batch API is a minor API surface expansion (not strictly MAINTENANCE)
- Performance gains only matter for sessions with many data keys (typical sessions may have <10 keys)

### Approach B: Data Integrity & Safety
Focus on atomic size checks, lock ownership verification, and race condition elimination.

**Key changes:**
- Replace TOCTOU size check with Lua script (atomic check-and-set)
- Implement lock ownership verification in `releaseLock()` (store unique value, Lua CAS for delete)
- Add idempotency guard for promotion (check if already promoted before starting)

**Pros:**
- Eliminates real race conditions that can cause data corruption
- Lock safety prevents accidental lock release (critical for concurrent promotions)
- Directly addresses correctness, not just performance

**Cons:**
- Lua scripts require Redis expertise to maintain
- Race conditions may be rare in practice (low traffic edge case)
- Requires careful testing with concurrent scenarios

### Approach C: Operational Excellence
Focus on monitoring dashboards, token_blacklist cleanup, alerting, and configuration validation.

**Key changes:**
- Add scheduled cleanup for expired `token_blacklist` entries (anonymous-related)
- Define Prometheus alert rules for rate limit bypass (fail-open), session count anomalies
- Add Spring Boot Actuator health indicator for anonymous session subsystem
- Validate `AnonymousProperties` configuration on startup (sanity checks)

**Pros:**
- Essential for production readiness
- Cleanup prevents unbounded table growth
- Alerts catch abuse early

**Cons:**
- No functional improvement — purely operational
- Dashboard/alert rules may be outside auth-service repo scope (infra concern)
- Cleanup scheduler adds minor complexity

### Approach D: Balanced (SELECTED)
Combine critical fixes from B + key optimizations from A + essential ops from C.

**Priority 1 (Data Integrity — MUST):**
- Fix lock ownership in `SessionPromotionService` (Lua script for safe release)
- Fix TOCTOU race in `AnonymousSessionDataService.storeData()` (Lua atomic check-and-set)

**Priority 2 (Performance — SHOULD):**
- Pipeline Redis operations in `transferData()` (replace SCAN + individual GET with pipeline)
- Pipeline Redis operations in `getSessionDataSize()` (SCAN + pipeline STRLEN)
- Make SCAN count configurable via `AnonymousProperties`

**Priority 3 (Operational — SHOULD):**
- Add `TokenBlacklistCleanupScheduler` for expired entries
- Add configuration validation for `AnonymousProperties` (startup check)
- Add health indicator for anonymous session subsystem

**Priority 4 (Nice-to-have):**
- Batch session data API endpoint
- Grafana dashboard template
- Alert rules template

**Pros:**
- Addresses all critical issues (integrity > performance > ops)
- Scoped appropriately for MAINTENANCE classification
- Minimal API surface changes (no new public contracts except optional batch endpoint)

**Cons:**
- Wider scope than a single-focus approach
- Requires both Lua script and scheduler knowledge
- More files touched = larger blast radius

## Selected Direction

**Approach D: Balanced Optimization** — selected because it addresses the most impactful issues in priority order:

1. **Data integrity first**: The TOCTOU race condition in size checking and the unsafe lock release are correctness bugs, not just optimizations. They should be fixed regardless of traffic volume.

2. **Performance second**: Redis pipelining in `transferData()` is a straightforward improvement that reduces round-trips from O(N) to O(1) for the common case. The SCAN pattern itself is acceptable (non-blocking, cursor-based) but the per-key operations should be pipelined.

3. **Operations third**: The token_blacklist cleanup is a table growth concern that becomes critical at scale. Configuration validation catches misconfigurations early.

**Why not A-only or B-only**: A misses correctness issues; B misses easy performance wins. The balanced approach maximizes value per development effort.

**Estimated effort**: 3-5 developer-days (Priority 1: 1-2d, Priority 2: 1d, Priority 3: 1d, Priority 4: optional).

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE (confirmed — all code exists, optimizing/hardening)
- Flow type: Non-Financial (no monetary transactions, session lifecycle operations)
- Affected modules:
  - `auth.application` — `SessionPromotionService`, `AnonymousSessionDataService`, `AnonymousRateLimitService`
  - `auth.application.command` — `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`
  - `shared.config` — `SecurityProperties.AnonymousProperties` (add SCAN count config)
  - `auth.adapter.in.web` — `AnonymousAuthController` (optional batch endpoint)
  - Scheduler — new `TokenBlacklistCleanupScheduler` or extend existing `SessionCleanupScheduler`

## Codebase Findings

### Key Files Reviewed

| File | Observation |
|------|------------|
| `SessionPromotionService.kt` | Lock release uses simple DELETE — no ownership verification. Lua script needed for safe release. |
| `AnonymousSessionDataService.kt` | `storeData()` has TOCTOU race: `getSessionDataSize()` → check → `set()` not atomic. `transferData()` uses SCAN + individual `get()` per key — should pipeline. |
| `AnonymousRateLimitService.kt` | Clean implementation. Fail-open is appropriate but should be monitored. |
| `AnonymousSessionHandler.kt` | Solid. Follows `LoginHandler` pattern correctly. No issues found. |
| `RenewAnonymousTokenHandler.kt` | Clean. Renewal count check + blacklist + TTL refresh. `SENTINEL_USER_ID = 0L` is well-documented (DD-004). |
| `AnonymousAuthController.kt` | Comprehensive endpoint set. Missing batch endpoint (nice-to-have). `extractClientIp()` correctly handles `X-Forwarded-For`. |
| `SecurityProperties.kt` | `AnonymousProperties` well-structured with sensible defaults. Missing: SCAN count config, startup validation. |

### Architecture Diagram (Current)

```
    ┌────────────────────────────┐
    │   AnonymousAuthController  │
    │    /api/v1/auth/anonymous  │
    └─────┬────┬────┬───────────┘
          │    │    │
          │    │    └─────────────────────────────┐
          │    │                                  │
    ┌─────▼────┴──────────┐   ┌──────────────┐   ▼
    │ AnonymousSession    │   │   Renew       │ AnonymousSession
    │ Handler (create)    │   │   Handler     │ DataService
    └──────┬──────────────┘   └──────┬────────┘ (CRUD)
           │                         │            │
    ┌──────▼──────────────┐   ┌──────▼────────┐   │
    │ RateLimitService    │   │  JwtService   │◄──┘
    │ (Redis INCR+EXPIRE) │   │ (RS256 JWT)   │
    └──────┬──────────────┘   └──────┬────────┘
           │                      cquireLock: SET lock_key UNIQUE_VALUE NX EX 30
  releaseLock: EVAL "if redis.call('GET', KEYS[1]) == ARGV[1]
                      then return redis.call('DEL', KEYS[1])
                      else return 0 end"
```

#### OPT-3: Pipeline in transferData()
```
Current (N+1):
  SCAN → for each key → GET → SET user key (2N Redis calls)

Fix (pipeline):
  SCAN → collect keys → MGET all values → pipeline SET all user keys (3 Redis calls)
```

#### OPT-4: TokenBlacklist Cleanup
```
Current: No cleanup — rows accumulate forever.
Fix: Scheduled job: DELETE FROM token_blacklist WHERE expires_at < NOW()
     Run every 6 hours (configurable). Can extend existing SessionCleanupScheduler.
```

## Open Questions for Design Phase
- [OPEN] OPT-1 Lua script: Should we use KEYS command inside Lua (blocking) or SCAN inside Lua (Redis 7.0+ required)? KEYS is simpler but blocks for many keys. SCAN in Lua is safe but requires Redis 7+.
- [OPEN] OPT-3 pipeline: Should pipelining use `executePipelined()` (Spring Data Redis) or raw connection pipeline? Spring's `executePipelined()` is simpler but has serialization overhead.
- [OPEN] OPT-4 cleanup: Should TokenBlacklist cleanup be a dedicated scheduler or integrated into existing `SessionCleanupScheduler`? Separate is cleaner but adds another scheduled bean.
- [OPEN] Batch API: Is a batch session data endpoint in scope for MAINTENANCE, or should it be deferred to a separate feature request?
- [RESOLVED] Lock ownership: Use UUID as lock value + Lua CAS delete — standard Redlock single-instance pattern. No need for full Redlock (single Redis instance assumed).
- [RESOLVED] SCAN count tuning: Make configurable via `AnonymousProperties.scanCount` with default 100. Higher values (500-1000) reduce iterations but hold Redis connection longer.

## Open Questions for URD Analysis
- Should the batch session data endpoint be a formal requirement, or is it a developer convenience?
- Is there a specific SLA for token_blacklist table size or cleanup frequency?
- Should anonymous session metrics be exposed via a dedicated dashboard, or integrated into the existing auth-service monitoring?
