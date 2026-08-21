---
type: brainstorm_notes
change: anonymous-login-optimization
date: 2025-07-15
selected_direction: "Focused Completion — FR-006 Observability Spans + FR-012 Decision Closure + Performance Validation Strategy"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Anonymous Login Optimization (v4 — Remaining Work)

## Date
2025-07-15

## Context

This is a **continuation brainstorm** for the anonymous-login-optimization MAINTENANCE change. The previous brainstorm (archived at `openspec/changes/archive/2026-08-20-anonymous-login-optimization/brainstorm_notes.md`) selected "Balanced Optimization v3 — 3-Lua-Script Architecture with 4-Phase Delivery" and deferred FR-006 (Observability spans) and FR-012 (Counter reconciliation) to Phase 4.

**Current implementation status** (verified via codebase inspection):
- **12/14 FRs FULLY IMPLEMENTED** — all Phase 1 (Data Integrity), Phase 2 (Performance), and Phase 3 (Reliability & Operations) FRs are complete in the codebase
- **FR-006 PARTIAL** — 7 Counter/Timer metrics exist (`auth.anonymous.sessions.created`, `auth.anonymous.rate_limited`, `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`, `auth.anonymous.sessions.promoted`, `auth.anonymous.promotion.duration`, `auth.anonymous.token.generation.duration`). No Micrometer Observation API spans for distributed tracing.
- **FR-012 DEFERRED** — `getSessionDataSizeScan()` private method retained as on-demand fallback at line 203 of `AnonymousSessionDataService.kt`. No scheduled caller implemented.

**Key codebase facts**:
- `base-observability-starter` is already a project dependency (provides Micrometer Observation API)
- No `@Observed` annotations or `Observation.createNotStarted()` usage anywhere in the codebase
- `spring-boot-starter-actuator` is present — auto-configuration for Observation should be available
- All 3 Lua scripts verified: `sliding_window_rate_limit.lua` (35 lines), `safe_lock_release.lua` (11 lines), `atomic_data_store.lua` (26 lines)
- `RedisLuaScriptConfig` correctly defines 3 `DefaultRedisScript<Long>` beans
- Dual-mode rate limiting works (sliding window default + fixed-window fallback via `slidingWindowEnabled`)
- Pipeline fallbacks implemented in both `AnonymousSessionHandler` and `AnonymousRateLimitService`
- `RenewAnonymousTokenHandler` still uses 3 sequential Redis calls (lines 85-88) — not in current scope

## Questions Asked & Answers

- Q1: What is the remaining scope for this MAINTENANCE optimization? → A: **2 FRs remain**: FR-006 (observability spans — partial) and FR-012 (counter reconciliation — deferred). Additionally, NFR performance validation has not been conducted.

- Q2: Is FR-006 (Observation spans) still relevant given existing Counter/Timer metrics? → A: **Yes, but with reduced priority.** Counter/Timer metrics provide operational visibility (request counts, durations). Observation spans add **distributed tracing context** — critical for correlating anonymous session operations across microservice boundaries (e.g., tracing a login flow from API gateway → auth-service → Redis → PostgreSQL). The `base-observability-starter` dependency is already present, making this a low-effort addition.

- Q3: Should FR-006 use `@Observed` annotation or programmatic `Observation.createNotStarted()`? → A: **`@Observed` annotation** is preferred for the following reasons:
  - Less invasive — no code changes inside method bodies
  - Consistent with Spring Boot 3.x/Micrometer best practices
  - AOP-based — spans automatically start/stop around method execution
  - Requires `micrometer-observation` + `spring-boot-starter-aop` (likely provided by `base-observability-starter`)
  - Named spans via `@Observed(name = "anonymous.session.create")` are self-documenting
  - **Trade-off accepted**: `@Observed` cannot span sub-sections within a method (e.g., "just the Redis pipeline call"). Full-method spans are sufficient for anonymous session operations.
  - **Fallback**: If `@Observed` is not available (AOP not configured), use programmatic `Observation.createNotStarted()` in 4 key methods.

- Q4: Which methods should receive Observation spans? → A: **4 critical methods** covering the anonymous session lifecycle:

  | # | Method | Class | Span Name | Rationale |
  |---|--------|-------|-----------|-----------|
  | 1 | `handle()` | `AnonymousSessionHandler` | `anonymous.session.create` | Session creation entry point — covers rate limit + pipeline + JWT generation |
  | 2 | `storeData()` | `AnonymousSessionDataService` | `anonymous.data.store` | Atomic Lua data store — critical for size enforcement monitoring |
  | 3 | `transferData()` | `AnonymousSessionDataService` | `anonymous.data.transfer` | Batch pipeline transfer — key performance path during promotion |
  | 4 | `promoteSession()` | `SessionPromotionService` | `anonymous.session.promote` | Full promotion orchestration — lock + transfer + blacklist + cleanup |

  **Excluded** (not critical enough for spans):
  - `getData()` / `deleteData()` — simple single-key operations
  - `checkRateLimit()` — already covered by parent `handle()` span
  - `handle()` in `RenewAnonymousTokenHandler` — low-frequency operation
  - `cleanupExpiredBlacklistEntries()` — background job, not user-facing

- Q5: Should FR-012 (counter reconciliation) be implemented or permanently deferred? → A: **Permanently defer with documented rationale.** Reasoning:
  1. `atomic_data_store.lua` guarantees atomic size check + write + counter increment — the primary TOCTOU drift risk is eliminated
  2. Counter decrement in `deleteData()` uses Kotlin `HINCRBY -size` which is atomic per-operation (not Lua-wrapped, but delete-of-absent-key is safe)
  3. Sessions are ephemeral (max 24h TTL via `session-ttl-seconds: 86400`) — any residual drift from extreme edge cases (Redis crash mid-Lua, connection drop between operations) is temporary and self-correcting
  4. The `getSessionDataSizeScan()` method is retained as a private on-demand fallback if debugging is ever needed
  5. Implementing a scheduled reconciliation task adds: a new scheduled job, threshold detection logic, counter reset logic, logging, metrics, configuration — all for a near-zero-probability edge case
  6. **Decision: CLOSE FR-012 as WON'T FIX** — document as acceptable risk. Remove from active FR list. Counter integrity is sufficiently protected by Lua atomicity + session TTL.

- Q6: Should `RenewAnonymousTokenHandler` pipelining be added to scope? → A: **No.** Reasoning:
  1. Renewal is low-frequency (max 24 times per session over 24 hours)
  2. Lines 85-88 make 3 sequential Redis calls: `blacklistToken()`, `ops.put()`, `redisTemplate.expire()` — saving 2 RTTs on a low-frequency operation has minimal impact
  3. Adding pipeline here would increase the diff size for marginal gain
  4. Can be addressed in a separate micro-optimization if needed
  5. **Decision: OUT OF SCOPE** — not blocking, not impactful enough

- Q7: Is performance validation needed before closing this optimization? → A: **Yes, as a validation strategy (not implementation).** The pre_openspec defines specific NFR targets:
  - NFR-001: Session creation P95 < 50ms (from ~100ms)
  - NFR-003: Data transfer P95 < 10ms (from ~50ms for 10 keys)
  - NFR-005: Data store P95 < 5ms (from ~20ms)
  - NFR-006: 100% tracing coverage (after FR-006 completion)
  These should be validated via load testing or APM monitoring in a staging environment. The implementation task should define HOW to validate, not require the validation itself.

- Q8: Are there any new risks or concerns since the original brainstorm? → A: **One observation**: The `RenewAnonymousTokenHandler` has a `sessions.renewed` counter metric that was not listed in the pre_openspec's metric inventory. This is actually fine — it was added as part of the handler implementation, not part of the optimization FRs. No action needed.

## Approaches Considered

### Approach A: FR-006 Only (Observability Spans)
Focus exclusively on adding `@Observed` annotations to 4 critical methods.

- **Scope**: FR-006 completion (add Observation spans)
- **Effort**: 0.5 dev-day
- **Pros**: Minimal scope, low risk, quick delivery
- **Cons**: Leaves FR-012 in limbo (neither implemented nor formally closed); no performance validation guidance; incomplete closure of the optimization initiative
- **Score**: 6/10

### Approach B: FR-006 + FR-012 Implementation (Spans + Reconciliation)
Add observability spans AND implement counter reconciliation scheduler.

- **Scope**: FR-006 completion + FR-012 implementation
- **Effort**: 1.0 dev-day
- **Pros**: All 14 FRs would be complete
- **Cons**: FR-012 has near-zero ROI — `atomic_data_store.lua` already protects writes; sessions are ephemeral; adds unnecessary complexity (scheduler, threshold, counter reset); engineering time better spent elsewhere
- **Score**: 5/10

### Approach C: FR-006 + Renewal Pipelining (Spans + Bonus Optimization)
Add observability spans AND pipeline `RenewAnonymousTokenHandler`.

- **Scope**: FR-006 + new FR (renewal pipeline)
- **Effort**: 1.0 dev-day
- **Pros**: Broader performance optimization coverage
- **Cons**: Renewal is low-frequency (max 24/session over 24h) — marginal gain does not justify the added scope; FR-012 still left in limbo; scope creep risk introducing a new FR in a closing-out iteration
- **Score**: 5/10

### Approach D: Focused Completion (Spans + FR-012 Closure + Validation Strategy) ★ SELECTED
Add observability spans, formally close FR-012 as WON'T FIX, and define performance validation strategy.

- **Scope**: FR-006 completion + FR-012 formal closure (documented decision, not implementation) + NFR performance validation strategy
- **Effort**: 0.5-1.0 dev-day
- **Pros**: Cleanly closes all 14 FRs (12 implemented, 1 completed via FR-006, 1 formally closed via FR-012 WON'T FIX); provides NFR validation guidance; no over-engineering; no scope creep; complete and defensible closure of the optimization initiative
- **Cons**: FR-012 not implemented — but analysis shows this is the correct decision (Lua atomicity + session TTL make reconciliation unnecessary)
- **Score**: 9/10

## Selected Direction

**Approach D: Focused Completion** — FR-006 Observability Spans + FR-012 Decision Closure + Performance Validation Strategy.

### Why This Approach

1. **Completeness over checkbox-ticking**: Implementing FR-012 would technically complete "14/14 FRs" but would add unnecessary complexity for a near-zero-probability edge case. Formally closing it as WON'T FIX is the honest, engineering-sound decision.

2. **Observability is the real gap**: The codebase has comprehensive Counter/Timer metrics but zero distributed tracing spans. Adding `@Observed` on 4 critical methods fills this gap with minimal effort (annotation-driven, no method body changes).

3. **Performance validation closes the loop**: The optimization was implemented but never formally validated against NFR targets. Defining the validation strategy in design.md ensures NFR accountability without blocking the current iteration.

4. **Clean closure**: After this iteration, the anonymous-login-optimization has a clear status for every FR:
   - FR-001 through FR-005, FR-007 through FR-011, FR-013, FR-014: ✅ IMPLEMENTED
   - FR-006: ✅ COMPLETED (Counter/Timer metrics + Observation spans)
   - FR-012: ❌ WON'T FIX (documented, acceptable risk)

### Architecture Diagram — Current State + Remaining Work

```
┌─────────────────────────────────────────────────────────────────────┐
│                 Anonymous Login Optimization                        │
│                 Implementation Status (v4)                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Phase 1 — Data Integrity ✅ COMPLETE                               │
│    FR-004: UUID lock + Lua safe release                             │
│    FR-007: Lua atomic check-and-set (TOCTOU fix)                    │
│    FR-008: RedisLuaScriptConfig @Configuration                      │
│    FR-009: 3 Lua script files                                       │
│                                                                     │
│  Phase 2 — Performance ✅ COMPLETE                                   │
│    FR-001: Pipeline HSET+EXPIRE in AnonymousSessionHandler          │
│    FR-002: Sliding window Lua rate limiting                         │
│    FR-003: Pipeline MGET+MSET for batch transfer                    │
│    FR-005: Running dataSize counter (via atomic_data_store.lua)      │
│                                                                     │
│  Phase 3 — Reliability & Operations ✅ COMPLETE                      │
│    FR-010: Pipeline fallback (try/catch → sequential)               │
│    FR-011: Lua script fallback (try/catch → fixed-window)           │
│    FR-013: TokenBlacklist cleanup scheduler                         │
│    FR-014: Config enhancements (slidingWindowEnabled, scanCount)    │
│                                                                     │
│  Phase 4 — Observability (THIS ITERATION) ← remaining work         │
│    FR-006: @Observed spans on 4 critical methods           [TO DO]  │
│    FR-012: Counter reconciliation                   [WON'T FIX]     │
│    Performance validation strategy                         [TO DO]  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Observation Spans Architecture

```
  ┌──────────────────────────────────────────────────────────────────┐
  │                     @Observed Span Hierarchy                     │
  │                                                                  │
  │  POST /api/v1/auth/anonymous  mance Validation Strategy (NFR verification)
═════════════════════════════════════════════
  Define validation approach in design.md:
  - Method: APM monitoring (Micrometer → Prometheus → Grafana) in staging
  - Metrics to monitor: P95 latency per span from @Observed annotations
  - Baseline: Use archived pre-optimization values from pre_openspec
  - Target: NFR-001 (<50ms session create), NFR-003 (<10ms transfer), NFR-005 (<5ms data store)
  - Tooling: Existing K6 load tests (tasks in build.gradle.kts) can be extended
```

### Key Design Decisions (Additions to Archive)

| ID | Decision | Rationale |
|----|----------|-----------|
| DD-101 | `@Observed` over `Observation.createNotStarted()` | Less invasive, AOP-based, consistent with Spring Boot 3.x. `base-observability-starter` likely configures AOP support. |
| DD-102 | 4 methods get spans (not all public methods) | Spans on session create, data store, data transfer, promotion — the 4 critical paths. Low-frequency ops (getData, deleteData, renewal) excluded to avoid span noise. |
| DD-103 | FR-012 WON'T FIX | `atomic_data_store.lua` guarantees atomic writes. Counter drift risk approaches zero. Sessions are ephemeral (24h). Reconciliation complexity not justified. |
| DD-104 | RenewAnonymousTokenHandler pipelining OUT OF SCOPE | Low-frequency operation (max 24/session). Marginal latency gain. Can be separate micro-optimization later. |
| DD-105 | Performance validation via APM, not dedicated benchmark | Existing Micrometer metrics + new Observation spans provide sufficient data. K6 load test scripts in project can generate traffic. No dedicated benchmark framework needed. |

## Pre-classifications (preliminary)
- Feature type: **MAINTENANCE** (confirmed — optimizing/hardening fully implemented feature)
- Flow type: **Non-Financial** (no monetary transactions, session lifecycle operations)
- Affected modules:
  - `auth.application.command` — `AnonymousSessionHandler` (MODIFY — add `@Observed`)
  - `auth.application` — `AnonymousSessionDataService` (MODIFY — add `@Observed` on 2 methods), `SessionPromotionService` (MODIFY — add `@Observed`)
  - Documentation artifacts — design.md, tasks.md (FR-012 closure, performance validation strategy)

## GitNexus Findings (if explored)

GitNexus was not explored in this brainstorm iteration. Codebase investigation was performed via direct file inspection (`view_file`, `grep_search`) which provided sufficient verification:

- Related processes: Anonymous session lifecycle (create → store data → renew → promote → cleanup)
- Key symbols: `AnonymousSessionHandler`, `AnonymousSessionDataService`, `AnonymousRateLimitService`, `SessionPromotionService`, `SessionCleanupScheduler`, `RedisLuaScriptConfig`
- Architecture insights: Clean Architecture with CQRS Handlers. Redis via `StringRedisTemplate`. 3-Lua-Script architecture for atomicity. Dual-mode rate limiting. Pipeline fallbacks throughout.

## Codebase Findings

### Verification Results (v4 — updated)

| Check | Result | Evidence |
|-------|:---:|---|
| `@Observed` annotation in codebase? | ❌ NEW | `grep @Observed` → 0 results — this will be the first usage |
| `Observation.createNotStarted` in codebase? | ❌ NEW | `grep Observation` → 0 results — no Observation API usage |
| `ObservationRegistry` in codebase? | ❌ NEW | `grep ObservationRegistry` → 0 results |
| `base-observability-starter` dependency? | ✅ EXISTS | `build.gradle.kts` line 14 |
| `spring-boot-starter-actuator` dependency? | ✅ EXISTS | `build.gradle.kts` line 17 |
| `MeterRegistry` usage? | ✅ EXISTS | Used in 5 anonymous service classes + 5 test classes |
| All 3 Lua scripts present? | ✅ EXISTS | `resources/redis/` — 3 files (35+11+26 lines) |
| `RedisLuaScriptConfig` present? | ✅ EXISTS | `shared.config.RedisLuaScriptConfig` — 3 beans |
| `executePipelined` usage? | ✅ EXISTS | 3 locations (AnonymousSessionHandler:69, AnonymousSessionDataService:142, AnonymousSessionDataService:163) |
| `slidingWindowEnabled` config? | ✅ EXISTS | `SecurityProperties.AnonymousProperties` — `slidingWindowEnabled: Boolean = true` |
| `getSessionDataSizeScan()` retained? | ✅ EXISTS | `AnonymousSessionDataService.kt` line 203 — private, @Suppress("unused") |
| `SessionCleanupScheduler` blacklist cleanup? | ✅ EXISTS | Lines 63-78 — cron `0 0 */6 * * *` |

### Counter Drift Analysis (FR-012 Closure Justification)

```
Scenario 1: Concurrent storeData() calls — PROTECTED ✅
  atomic_data_store.lua: HGET dataSize → check → SET → HINCRBY
  Entire sequence is atomic (Lua EVAL) — no concurrent interleaving
  Two concurrent calls: first succeeds, second sees updated dataSize
  Zero drift.

Scenario 2: deleteData() race with storeData() — SAFE ✅
  deleteData() decrements via Kotlin HINCRBY(-size) — atomic Redis command
  storeData() increments via Lua HINCRBY(+size) — atomic Lua script
  Both operations are individually atomic against Redis state
  Possible ordering: delete sees stale size → decrement too much → counter < 0
  Guard: counter floor at 0 is implicit (HINCRBY allows negative, but
         getSessionDataSize() returns max(0, value) — no negative effects)
  Worst case: counter slightly low → allows slightly more data than limit
  Impact: trivial — 1 extra key worth of data, session expires in 24h

Scenario 3: Redis crash mid-Lua — EXTREMELY RARE ⚠️
  If Redis crashes mid-atomic_data_store.lua execution:
  - Before SET: no data written, no counter change → consistent
  - After SET, before HINCRBY: data written, counter not incremented → drift DOWN
  Probability: requires SIGKILL of Redis process — extremely rare
  Even if drift occurs: session expires in max 24h → drift is ephemeral
    
Scenario 4: Network disconnect between storeData() Lua and response — EDGE CASE ⚠️
  Data IS stored (Lua completed on Redis side) but client doesn't know
  Client might retry → new Lua EVAL adds another increment → drift UP
  But: Lua checks current size → would reject if already over limit
  Net effect: at most +1 extra entry's worth of drift — trivially small
    
Conclusion: Counter drift probability approaches 0% with Lua atomicity.
Impact of drift: session is ephemeral (24h max) → self-correcting.
getSessionDataSizeScan() retained as on-demand debug tool.
★ DECISION: WON'T FIX — acceptable risk
```

## Open Questions for Design Phase

- [RESOLVED] FR-006 approach: **`@Observed` annotation** — AOP-based, Spring Boot 3.x idiomatic. Fallback to programmatic Observation if AOP not configured.
- [RESOLVED] Which methods get spans: **4 methods** — AnonymousSessionHandler.handle(), AnonymousSessionDataService.storeData(), AnonymousSessionDataService.transferData(), SessionPromotionService.promoteSession()
- [RESOLVED] FR-012 disposition: **WON'T FIX** — Lua atomicity + session TTL make reconciliation unnecessary. getSessionDataSizeScan() retained as fallback.
- [RESOLVED] RenewAnonymousTokenHandler pipelining: **OUT OF SCOPE** — low-frequency operation, marginal gain.
- [OPEN] Does `base-observability-starter` include `@Observed` annotation support (AOP auto-configuration)? → **Action**: Verify at implementation time. If not, either add AOP dependency or use programmatic `Observation.createNotStarted()`.
- [OPEN] Should span names follow `module.operation` pattern (e.g., `anonymous.session.create`) or `method.name` pattern (e.g., `AnonymousSessionHandler.handle`)? → **Recommendation**: `module.operation` — more stable across refactoring, better for dashboards.

## Open Questions for URD Analysis

- None — URD analysis completed via `wf_pre_openspec` with quality score 90/100. All remaining work is implementation-level.
