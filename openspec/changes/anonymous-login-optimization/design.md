# Design: Anonymous Login Optimization (MAINTENANCE — v3 Focused Completion)

[CHANGED] Scope reduced from archive v2 (Redis pipelining, Lua scripting, data integrity hardening — ALL IMPLEMENTED) to v3 (FR-006 Observability Spans completion + FR-012 formal closure + Performance Validation Strategy). This is the final design iteration for the anonymous-login-optimization initiative.

## Context

Auth-service anonymous login optimization initiative has successfully completed 12/14 FRs across 3 phases:
- **Phase 1** (Data Integrity): UUID lock + Lua safe release, TOCTOU fix via atomic Lua, RedisLuaScriptConfig, 3 Lua scripts ✅
- **Phase 2** (Performance): Pipeline HSET+EXPIRE, sliding window Lua, pipeline MGET+MSET, running counter ✅
- **Phase 3** (Reliability & Operations): Pipeline fallback, Lua fallback, blacklist cleanup, config enhancements ✅

**Remaining Phase 4 work:**
- **FR-006 PARTIAL**: 7 Counter/Timer metrics exist but zero Micrometer Observation API spans for distributed tracing
- **FR-012 DEFERRED**: Counter reconciliation — analysis confirmed unnecessary (Lua atomicity + session TTL)

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (`CommandHandler<C, R>`), Spring Boot 3.x + Kotlin
- Observability: `base-observability-starter` dependency present, `spring-boot-starter-actuator` present
- `@Observed` annotation: Not used anywhere in codebase — this will be the first usage
- `ObservationRegistry`: Not explicitly referenced — auto-configured by Spring Boot Actuator
- Existing metrics: `MeterRegistry` injected in all anonymous services (Counter/Timer only)

### Selected Design Direction (from brainstorm_notes.md)
**Approach D — Focused Completion**: FR-006 Observability Spans + FR-012 Decision Closure + Performance Validation Strategy. Annotation-driven `@Observed` approach (DD-101). 4 critical methods receive spans (DD-102). FR-012 closed as WON'T FIX (DD-103).

## Goals / Non-Goals

**Goals:**
- Complete FR-006: Add `@Observed` distributed tracing spans to 4 critical anonymous session methods
- Achieve NFR-006: 100% tracing coverage for anonymous session critical paths
- Formally close FR-012: Document WON'T FIX decision with rationale
- Define performance validation strategy for NFR-001, NFR-003, NFR-005
- Establish `@Observed` annotation pattern for future observability additions across the codebase

**Non-Goals:**
- Implementing FR-012 counter reconciliation (formally closed — WON'T FIX)
- `RenewAnonymousTokenHandler` pipelining (out of scope — DD-104, low-frequency operation)
- Full method body changes — annotation-only approach
- New metrics — existing Counter/Timer metrics are sufficient
- Dedicated performance benchmark framework (APM monitoring is sufficient — DD-105)

## Decisions

### DD-101: `@Observed` over `Observation.createNotStarted()` [FROM BRAINSTORM]
- **Decision**: Use `@Observed` annotation (AOP-based) for distributed tracing spans
- **Rationale**: 
  - Less invasive — no code changes inside method bodies
  - Consistent with Spring Boot 3.x / Micrometer best practices
  - AOP-based — spans automatically start/stop around method execution
  - Named spans via `@Observed(name = "anonymous.session.create")` are self-documenting
- **Trade-off accepted**: `@Observed` cannot span sub-sections within a method. Full-method spans are sufficient for anonymous session operations.
- **Fallback**: If `@Observed` AOP support not available from `base-observability-starter`, register `ObservedAspect` bean manually or use programmatic `Observation.createNotStarted()`.

### DD-102: 4 methods get spans (not all public methods) [FROM BRAINSTORM]
- **Decision**: Apply `@Observed` to exactly 4 methods covering the critical anonymous session lifecycle
- **Methods**:
  1. `AnonymousSessionHandler.handle()` — session creation entry point
  2. `AnonymousSessionDataService.storeData()` — atomic Lua data store
  3. `AnonymousSessionDataService.transferData()` — batch pipeline transfer
  4. `SessionPromotionService.promoteSession()` — full promotion orchestration
- **Excluded**: `getData()`, `deleteData()` (simple ops), `checkRateLimit()` (covered by parent span), `RenewAnonymousTokenHandler.handle()` (low-frequency), `cleanupExpiredBlacklistEntries()` (background job)
- **Rationale**: Avoid span noise. Focus on methods where latency monitoring provides actionable insights.

### DD-103: FR-012 WON'T FIX [FROM BRAINSTORM]
- **Decision**: Close FR-012 (counter reconciliation) as WON'T FIX
- **Rationale**: 
  1. `atomic_data_store.lua` guarantees atomic write + counter increment — primary drift eliminated
  2. `deleteData()` decrement is atomic (HINCRBY negative)
  3. Sessions ephemeral (24h TTL) — drift self-corrects
  4. `getSessionDataSizeScan()` retained as private debug fallback
  5. Engineering effort for near-zero-probability edge case not justified
- **Risk assessment**: Acceptable — counter integrity sufficiently protected by Lua atomicity + session TTL

### DD-104: RenewAnonymousTokenHandler pipelining OUT OF SCOPE [FROM BRAINSTORM]
- **Decision**: Renewal pipelining not included in this iteration
- **Rationale**: Low-frequency operation (max 24/session over 24h). Marginal latency gain. Separate micro-optimization if needed.

### DD-105: Performance validation via APM, not dedicated benchmark [FROM BRAINSTORM]
- **Decision**: Use existing Micrometer metrics + new Observation spans + K6 load tests for NFR validation
- **Rationale**: Existing infrastructure sufficient. No dedicated benchmark framework needed.

## Component Mapping

### Modified Components

| Component | File | Action | FR(s) |
|-----------|------|--------|-------|
| `AnonymousSessionHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | [MODIFY] annotation-only | FR-006 |
| `AnonymousSessionDataService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | [MODIFY] annotation-only | FR-006 |
| `SessionPromotionService` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | [MODIFY] annotation-only | FR-006 |

### New Components

None — annotation-only changes.

### Reused Components (no changes needed)

| Component | File | Usage |
|-----------|------|-------|
| `AnonymousRateLimitService` | `src/main/kotlin/.../AnonymousRateLimitService.kt` | Rate limit covered by parent `handle()` span — no separate span needed |
| `RenewAnonymousTokenHandler` | `src/main/kotlin/.../command/RenewAnonymousTokenHandler.kt` | Low-frequency — excluded from spans |
| `SessionCleanupScheduler` | `src/main/kotlin/.../SessionCleanupScheduler.kt` | Background job — excluded from spans |
| `RedisLuaScriptConfig` | `src/main/kotlin/.../config/RedisLuaScriptConfig.kt` | Lua scripts unchanged |
| `SecurityProperties` | `src/main/kotlin/.../config/SecurityProperties.kt` | Config unchanged |
| `base-observability-starter` | `build.gradle.kts` line 16 | Provides `@Observed` annotation support |
| `spring-boot-starter-actuator` | `build.gradle.kts` line 17 | Provides `ObservationRegistry` auto-config |
| `MeterRegistry` | Auto-configured Actuator bean | Existing Counter/Timer metrics coexist with new spans |

## @Observed Span Architecture

```
  ┌──────────────────────────────────────────────────────────────────┐
  │                     @Observed Span Hierarchy                     │
  │                                                                  │
  │  POST /api/v1/auth/anonymous                                     │
  │  └── [anonymous.session.create]  ← AnonymousSessionHandler      │
  │      ├── checkRateLimit(ip)      (covered by parent span)       │
  │      ├── generateAnonymousToken  (covered by parent span)       │
  │      └── executePipelined        (covered by parent span)       │
  │                                                                  │
  │  PUT /api/v1/auth/anonymous/session/data                         │
  │  └── [anonymous.data.store]      ← AnonymousSessionDataService  │
  │      ├── verifySessionExists     (covered by parent span)       │
  │      └── EVAL atomic_data_store  (covered by parent span)       │
  │                                                                  │
  │  POST /api/v1/auth/login (promotion path)                        │
  │  └── [anonymous.session.promote] ← SessionPromotionService      │
  │      ├── acquireLock (UUID)      (covered by parent span)       │
  │      ├── [anonymous.data.transfer] ← nested child span          │
  │      │   ├── SCAN keys           (covered by parent span)       │
  │      │   ├── pipeline GET all    (covered by parent span)       │
  │      │   └── pipeline SET all    (covered by parent span)       │
  │      ├── blacklist(jti)          (covered by parent span)       │
  │      ├── deleteSession           (covered by parent span)       │
  │      └── releaseLock (Lua)       (covered by parent span)       │
  └──────────────────────────────────────────────────────────────────┘

  Note: promoteSession() calls transferData() → nested span hierarchy
  [anonymous.session.promote] → [anonymous.data.transfer] (child)
```

## `@Observed` Implementation Pattern

```kotlin
// Import
import io.micrometer.observation.annotation.Observed

// Usage pattern — annotation on method
@Observed(
    name = "anonymous.session.create",
    contextualName = "create-anonymous-session"
)
override fun handle(command: CreateAnonymousSessionCommand): AnonymousSessionResult {
    // ... existing method body unchanged ...
}
```

**Key implementation notes:**
- `name` = low-cardinality metric name (for dashboards)
- `contextualName` = human-readable span name (for tracing UI)
- No `lowCardinalityKeyValues` needed — method-level granularity is sufficient
- AOP proxy intercepts the method call → creates Observation → executes method → stops Observation
- If method throws exception → Observation records error automatically

## ObservedAspect Verification

```kotlin
// IF base-observability-starter does NOT auto-configure ObservedAspect:
// Add this bean to a @Configuration class (e.g., RedisLuaScriptConfig or new ObservabilityConfig)

@Configuration
class ObservabilityConfig {
    @Bean
    @ConditionalOnMissingBean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(registry)
    }
}
```

**⚠️ OPEN QUESTION**: Check at implementation time whether `base-observability-starter` already registers `ObservedAspect`. If it does, the above config is unnecessary. If not, add it.

## Performance Validation Strategy

### NFR Verification Approach

| NFR | Metric Source | Target | How to Validate |
|-----|--------------|--------|----------------|
| NFR-001 | `anonymous.session.create` span P95 | < 50ms | APM dashboard: filter by span name, check P95 percentile |
| NFR-003 | `anonymous.data.transfer` span P95 | < 10ms | APM dashboard: filter by span name, generate 10-key sessions |
| NFR-005 | `anonymous.data.store` span P95 | < 5ms | APM dashboard: filter by span name, concurrent store requests |
| NFR-006 | Span count / method coverage | 100% critical paths | Verify 4 span names appear in tracing backend |

### Validation Tooling
- **Traffic generation**: K6 load test scripts (existing in project)
- **Metrics collection**: Micrometer → Prome✅ IMPLEMENTED | 3 Lua script files |
| FR-010 | ✅ IMPLEMENTED | Pipeline fallback |
| FR-011 | ✅ IMPLEMENTED | Lua script fallback |
| FR-012 | ❌ WON'T FIX | Formally closed — acceptable risk |
| FR-013 | ✅ IMPLEMENTED | Blacklist cleanup scheduler |
| FR-014 | ✅ IMPLEMENTED | Config enhancements |
