# Proposal: Anonymous Login Optimization (v3 — Focused Completion)

[CHANGED] Scope reduced from archive v2 (12 active FRs: Redis pipelining, Lua scripting, data integrity — ALL IMPLEMENTED) to v3 (FR-006 completion + FR-012 formal closure + performance validation strategy). This is the final iteration of the anonymous-login-optimization initiative.

## Why

Auth-service anonymous login optimization initiative (v2) successfully implemented 12/14 FRs covering Redis pipelining, Lua scripting, data integrity hardening, and operational improvements. Two FRs remain from Phase 4 (deferred):

1. **🟡 FR-006 — Observability Spans (PARTIAL)**: Codebase has 7 Counter/Timer metrics (`auth.anonymous.sessions.created`, `auth.anonymous.rate_limited`, etc.) but zero Micrometer Observation API spans for distributed tracing. The `base-observability-starter` dependency is present but `@Observed` annotation is not used anywhere in the codebase.

2. **🟡 FR-012 — Counter Reconciliation (DEFERRED)**: `getSessionDataSizeScan()` private method retained as on-demand fallback but no scheduled caller. Analysis (brainstorm_notes.md) confirmed this is unnecessary — `atomic_data_store.lua` guarantees atomic writes, sessions are ephemeral (24h TTL).

Additionally, NFR performance targets have not been formally validated.

## Changes

**FR-006 Completion — Observability Spans**:
- Add `@Observed` annotations to 4 critical methods covering the anonymous session lifecycle
- Span names follow `module.operation` pattern: `anonymous.session.create`, `anonymous.data.store`, `anonymous.data.transfer`, `anonymous.session.promote`
- Zero method body changes — annotation-driven AOP approach (DD-101 from brainstorm_notes.md)

**FR-012 Formal Closure — WON'T FIX**:
- Document decision rationale: Lua atomicity + session TTL eliminate counter drift risk
- Retain `getSessionDataSizeScan()` as private debug fallback
- No code changes

**Performance Validation Strategy**:
- Define NFR verification approach using existing Micrometer metrics + new Observation spans
- APM monitoring (Prometheus → Grafana) in staging environment
- K6 load test scripts for traffic generation

## Capabilities

### Completed Capabilities (from v2 — no changes)
- `anonymous-data-integrity`: TOCTOU race condition eliminated via Lua atomic check-and-set
- `anonymous-lock-safety`: Lock release is ownership-verified — zero cross-process releases
- `redis-lua-scripting`: 3 Lua scripts with Spring Data Redis infrastructure
- `sliding-window-rate-limiting`: Precise rate limiting without burst-at-boundary
- `redis-pipelining`: Reduced RTTs for session creation and data transfer
- `running-data-counter`: O(1) session data size calculation
- `token-blacklist-cleanup`: Automated cleanup of expired blacklist entries

### New Capabilities (this iteration)
- `anonymous-observability`: Distributed tracing spans on 4 critical anonymous session methods via `@Observed` annotation — completes 100% tracing coverage (NFR-006)

### Unchanged Capabilities
- All existing anonymous login functionality — zero API contract changes
- All existing Counter/Timer metrics — `@Observed` spans supplement, not replace
- All existing error handling — same exceptions, same HTTP status codes

## Impact

### Backend (auth-service)

**MODIFY** (3 existing files — annotation-only):
- `AnonymousSessionHandler.kt` — add `@Observed(name = "anonymous.session.create")` on `handle()`
- `AnonymousSessionDataService.kt` — add `@Observed(name = "anonymous.data.store")` on `storeData()`, `@Observed(name = "anonymous.data.transfer")` on `transferData()`
- `SessionPromotionService.kt` — add `@Observed(name = "anonymous.session.promote")` on `promoteSession()`

**NEW** (0 files)

### Database
- **No schema changes**

### External Systems
- **No changes** — Observation spans are internal. Tracing data appears in configured tracing backend (Zipkin/Jaeger) via existing infrastructure.
- **No new dependencies** — `base-observability-starter` and `spring-boot-starter-actuator` already present.
