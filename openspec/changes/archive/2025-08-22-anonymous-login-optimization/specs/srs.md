# SRS: Anonymous Login Optimization (MAINTENANCE — v3 Focused Completion)

[CHANGED] Scope reduced from archive v2 (12 active FRs: Redis optimization + data integrity) to v3 (FR-006 completion + FR-012 formal closure). All v2 FRs are implemented in the codebase. This SRS covers the remaining Phase 4 work.

## 1. Feature Overview

Anonymous login optimization initiative (v2) has been fully implemented: Redis pipelining, 3-Lua-Script architecture, sliding window rate limiting, batch data transfer, safe distributed lock release, TOCTOU fix, running data size counter, token blacklist cleanup, and configuration enhancements. This v3 SRS covers the final Phase 4 completion: adding Micrometer Observation distributed tracing spans (`@Observed`) to 4 critical methods, and formally closing FR-012 (counter reconciliation) as WON'T FIX.

**Zero API contract changes** — all modifications are annotation-only additions.

## 2. Functional Requirements

### FR-001 through FR-005: [UNCHANGED] ✅ IMPLEMENTED
- FR-001: Pipeline session creation — `AnonymousSessionHandler.kt` lines 69-76
- FR-002: Sliding window rate limiting — `AnonymousRateLimitService.kt` dual-mode with Lua
- FR-003: Batch data transfer — `AnonymousSessionDataService.kt` pipeline MGET+MSET
- FR-004: Safe distributed lock release — `SessionPromotionService.kt` UUID + Lua
- FR-005: Running data size counter — `AnonymousSessionHandler.kt` + `AnonymousSessionDataService.kt`

### FR-006: Observability Tracing Spans [MODIFY] — COMPLETING
- **Current behavior**: 7 Counter/Timer metrics exist (`auth.anonymous.sessions.created`, `auth.anonymous.rate_limited`, `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`, `auth.anonymous.sessions.promoted`, `auth.anonymous.promotion.duration`, `auth.anonymous.token.generation.duration`). Zero Micrometer Observation API spans. Zero `@Observed` annotation usage in codebase.
- **Expected behavior**: Add `@Observed` annotations to 4 critical methods covering the anonymous session lifecycle:

  | # | Method | Class | Span Name | Rationale |
  |---|--------|-------|-----------|-----------|
  | 1 | `handle()` | `AnonymousSessionHandler` | `anonymous.session.create` | Session creation entry point — covers rate limit + pipeline + JWT generation |
  | 2 | `storeData()` | `AnonymousSessionDataService` | `anonymous.data.store` | Atomic Lua data store — critical for size enforcement monitoring |
  | 3 | `transferData()` | `AnonymousSessionDataService` | `anonymous.data.transfer` | Batch pipeline transfer — key performance path during promotion |
  | 4 | `promoteSession()` | `SessionPromotionService` | `anonymous.session.promote` | Full promotion orchestration — lock + transfer + blacklist + cleanup |

- **Approach**: `@Observed` annotation (AOP-based) per DD-101 from brainstorm_notes.md. If `@Observed` not available (AOP not configured by `base-observability-starter`), fallback to programmatic `Observation.createNotStarted(name, observationRegistry)`.
- **Excluded methods** (not critical enough for spans): `getData()`, `deleteData()`, `checkRateLimit()` (covered by parent `handle()` span), `handle()` in `RenewAnonymousTokenHandler` (low-frequency), `cleanupExpiredBlacklistEntries()` (background job).
- **Validation**: 
  - Spans visible in configured tracing backend (Zipkin/Jaeger)
  - Span names follow `module.operation` pattern
  - Existing Counter/Timer metrics continue to function (coexistence)
  - Application starts successfully with `@Observed` annotations

### FR-007 through FR-011: [UNCHANGED] ✅ IMPLEMENTED
- FR-007: Atomic storeData via Lua — `atomic_data_store.lua`
- FR-008: RedisLuaScriptConfig — 3 `DefaultRedisScript<Long>` beans
- FR-009: 3 Lua script files in `resources/redis/`
- FR-010: Pipeline fallback — `AnonymousSessionHandler.kt` try/catch
- FR-011: Lua script fallback — `AnonymousRateLimitService.kt` catch-all

### FR-012: Running Counter Periodic Reconciliation [CLOSED — WON'T FIX]
- **Decision**: Formally close as WON'T FIX per brainstorm_notes.md DD-103
- **Rationale**:
  1. `atomic_data_store.lua` guarantees atomic size check + write + counter increment — primary TOCTOU drift risk eliminated
  2. Counter decrement in `deleteData()` uses atomic HINCRBY — safe per-operation
  3. Sessions are ephemeral (max 24h TTL) — any residual drift is temporary and self-correcting
  4. Redis crash mid-Lua is extremely rare; even if drift occurs, session expires in 24h
  5. `getSessionDataSizeScan()` method retained as private on-demand debug fallback at `AnonymousSessionDataService.kt` line 203
- **Validation**: No code changes. Document decision in design.md and tasks.md.

### FR-013 through FR-014: [UNCHANGED] ✅ IMPLEMENTED
- FR-013: TokenBlacklist cleanup scheduler — `SessionCleanupScheduler.kt`
- FR-014: Configuration enhancements — `SecurityProperties.kt` AnonymousProperties

## 3. Non-Functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Status |
|--------|------|---------|--------|--------|
| NFR-001 | Performance | Session creation P95 latency | < 50ms (from ~100ms) | ✅ Pipeline implemented — pending validation |
| NFR-002 | Accuracy | Rate limiting precision | Within 2% of true sliding window | ✅ Lua script implemented |
| NFR-003 | Performance | Data transfer P95 latency (10 keys) | < 10ms (from ~50ms) | ✅ Pipeline MGET+MSET implemented — pending validation |
| NFR-004 | Reliability | Lock safety | Zero cross-process lock releases | ✅ UUID + Lua safe release |
| NFR-005 | Performance | Data store with size check P95 | < 5ms (from ~20ms) | ✅ Running counter + atomic Lua — pending validation |
| NFR-006 | Observability | Tracing span coverage | 100% of anonymous session critical paths | 🟡 Pending FR-006 completion |
| NFR-007 | Data Integrity | Size limit enforcement under concurrency | Zero over-limit writes | ✅ Atomic Lua script |

### Performance Validation Strategy
- **Method**: APM monitoring (Micrometer → Prometheus → Grafana) in staging environment
- **Metrics to monitor**: P95 latency per `@Observed` span + existing Timer metrics
- **Baseline**: Pre-optimization values documented in pre_openspec.md
- **Targets**: NFR-001 (<50ms), NFR-003 (<10ms), NFR-005 (<5ms)
- **Tooling**: Existing K6 load tests can generate traffic. New `@Observed` spans provide per-operation latency data.

## 4. API Changes

**None.** All changes are annotation-only additions to existing methods. Same endpoints, same request/response schemas, same HTTP status codes.

## 5. Traceability Matrix

| FR-ID | Action | Affected File(s) | Status |
|-------|--------|------------------|--------|
| FR-001 | [UNCHANGED] | `AnonymousSessionHandler.kt` | ✅ IMPLEMENTED |
| FR-002 | [UNCHANGED] | `AnonymousRateLimitService.kt` | ✅ IMPLEMENTED |
| FR-003 | [UNCHANGED] | `AnonymousSessionDataService.kt` | ✅ IMPLEMENTED |
| FR-004 | [UNCHANGED] | `SessionPromotionService.kt` | ✅ IMPLEMENTED |
| FR-005 | [UNCHANGED] | `AnonymousSessionDataService.kt`, `AnonymousSessionHandler.kt` | ✅ IMPLEMENTED |
| FR-006 | [MODIFY] | `AnonymousSessionHandler.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt` | 🟡 THIS ITERATION |
| FR-007 | [UNCHANGED] | `AnonymousSessionDataService.kt` | ✅ IMPLEMENTED |
| FR-008 | [UNCHANGED] | `RedisLuaScriptConfig.kt` | ✅ IMPLEMENTED |
| FR-009 | [UNCHANGED] | `*.lua` files (3) | ✅ IMPLEMENTED |
| FR-010 | [UNCHANGED] | `AnonymousSessionHandler.kt` | ✅ IMPLEMENTED |
| FR-011 | [UNCHANGED] | `AnonymousRateLimitService.kt` | ✅ IMPLEMENTED |
| FR-012 | [CLOSED — WON'T FIX] | — | ❌ Formally closed |
| FR-013 | [UNCHANGED] | `SessionCleanupScheduler.kt`, `Repositories.kt` | ✅ IMPLEMENTED |
| FR-014 | [UNCHANGED] | `SecurityProperties.kt` | ✅ IMPLEMENTED |

**Active FRs this iteration: 1/14** (FR-006). **Closed: 1** (FR-012 WON'T FIX). **Implemented: 12/14**.

## 6. Assumptions

- ⚠️ Assumption: `base-observability-starter` includes `@Observed` AOP support (auto-configures `ObservedAspect` bean) — Lý do: Standard Spring Boot 3.x observability starter behavior. If not, explicit `ObservedAspect` bean registration needed.
- ⚠️ Assumption: `spring-boot-starter-aop` is transitively included by `base-observability-starter` — Lý do: `@Observed` requires AOP proxying. Verified: `base-observability-starter` is a custom starter — AOP configuration needs verification at implementation time.
- ⚠️ Assumption: Span names with dots (`anonymous.session.create`) are valid Micrometer observation names — Lý do: Micrometer uses dot-separated naming by convention (same as metric names).
- ⚠️ Assumption: All previously implemented FRs (FR-001 through FR-005, FR-007 through FR-011, FR-013, FR-014) remain functional — Lý do: No code changes to those components in this iteration.

## 7. Open Questions

- ⚠️ OPEN QUESTION: Does `base-observability-starter` auto-configure `ObservedAspect` bean, or does it need explicit registration? → **Action**: Verify at implementation time. If missing, add `@Bean fun observedAspect(registry: ObservationRegistry) = ObservedAspect(registry)` to a `@Configuration` class.
- ⚠️ OPEN QUESTION: Should span names follow `module.operation` pattern (`anonymous.session.create`) or `method.name` pattern (`AnonymousSessionHandler.handle`)? → **Recommendation**: `module.operation` — more stable across refactoring, better for dashboards (per brainstorm_notes.md).
