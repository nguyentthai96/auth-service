<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: anonymous-login-optimization (v3 — Focused Completion)

> **Type**: MAINTENANCE | **Flow**: Non-Financial | **FRs**: 14 (12 implemented, 1 completing, 1 closed)
> **Direction**: Approach D — Focused Completion — FR-006 Observability Spans + FR-012 Decision Closure + Performance Validation Strategy

## Changes

[CHANGED] Scope reduced from archive v2 (12 active FRs: Redis pipelining, Lua scripting, data integrity — ALL IMPLEMENTED) to v3 (FR-006 completion via `@Observed` annotations + FR-012 WON'T FIX closure).

**This Iteration (Phase 4 — Observability)**:
- FR-006: Add `@Observed` annotations to 4 critical methods (3 files modified)
- FR-012: Formally close as WON'T FIX (0 files modified)
- Performance validation strategy defined in design.md

| Metric | Value |
|--------|-------|
| Modified files | 3 |
| New files | 0 (possibly 1 if `ObservedAspect` bean needed) |
| Active FRs this iteration | 1 (FR-006) |
| Closed FRs | 1 (FR-012 — WON'T FIX) |
| Previously implemented FRs | 12 |
| New dependencies | 0 |
| Database migrations | 0 |

---

## Phase 4A: ObservedAspect Verification

- [ ] **Task 1: Verify `ObservedAspect` bean availability** `[VERIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/` | Action: [VERIFY] or [NEW]
  - FR: FR-006 — Observability tracing spans (prerequisite)
  - Details:
    - Check if `base-observability-starter` auto-configures `ObservedAspect` bean
    - Run application and check for `ObservedAspect` in Spring context: `applicationContext.getBean(ObservedAspect::class.java)`
    - **If NOT auto-configured** → Create `ObservabilityConfig.kt`:
      ```kotlin
      package com.ntt.authservice.shared.config

      import io.micrometer.observation.ObservationRegistry
      import io.micrometer.observation.aop.ObservedAspect
      import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
      import org.springframework.context.annotation.Bean
      import org.springframework.context.annotation.Configuration

      @Configuration
      class ObservabilityConfig {
          @Bean
          @ConditionalOnMissingBean
          fun observedAspect(registry: ObservationRegistry): ObservedAspect {
              return ObservedAspect(registry)
          }
      }
      ```
    - **If auto-configured** → No file creation needed, skip to Task 2
    - Pattern: Follows existing `RedisLuaScriptConfig.kt` configuration pattern in `shared.config`
  - Dependencies: `base-observability-starter` (build.gradle.kts line 16), `spring-boot-starter-actuator` (line 17)

## Phase 4B: Observability Spans

- [ ] **Task 2: Add `@Observed` span to `AnonymousSessionHandler.handle()`** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | Action: [MODIFY]
  - FR: FR-006 — Observability tracing spans
  - Base: `AnonymousSessionHandler` from `com.ntt.authservice.auth.application.command` — implements `CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>`
  - Details:
    - Add import: `import io.micrometer.observation.annotation.Observed`
    - Add annotation on `handle()` method:
      ```kotlin
      @Observed(
          name = "anonymous.session.create",
          contextualName = "create-anonymous-session"
      )
      override fun handle(command: CreateAnonymousSessionCommand): AnonymousSessionResult {
          // ... existing method body UNCHANGED ...
      }
      ```
    - Zero changes to method body — annotation-only
    - Span covers: rate limit check → UUID generation → JWT generation → pipeline HSET+EXPIRE → return result
  - Dependencies: Task 1 (ObservedAspect must be available)

- [ ] **Task 3: Add `@Observed` spans to `AnonymousSessionDataService.storeData()` and `transferData()`** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | Action: [MODIFY]
  - FR: FR-006 — Observability tracing spans
  - Base: `AnonymousSessionDataService` from `com.ntt.authservice.auth.application`
  - Details:
    - Add import: `import io.micrometer.observation.annotation.Observed`
    - Add annotation on `storeData()` method:
      ```kotlin
      @Observed(
          name = "anonymous.data.store",
          contextualName = "store-anonymous-data"
      )
      fun storeData(sessionId: String, namespace: String, key: String, value: String) {
          // ... existing method body UNCHANGED ...
      }
      ```
    - Add annotation on `transferData()` method:
      ```kotlin
      @Observed(
          name = "anonymous.data.transfer",
          contextualName = "transfer-anonymous-data"
      )
      fun transferData(sessionId: String, userId: Long): DataTransferResult {
          // ... existing method body UNCHANGED ...
      }
      ```
    - Zero changes to method bodies — annotation-only
    - `storeData` span covers: session verify → Lua atomic_data_store EVAL → metric increment
    - `transferData` span covers: SCAN keys → pipeline GET all → pipeline SET all → return result
    - Note: `transferData()` is called from `SessionPromotionService.promoteSession()` → creates nested child span under `anonymous.session.promote`
  - Dependencies: Task 1 (ObservedAspect must be available)

- [ ] **Task 4: Add `@Observed` span to `SessionPromotionService.promoteSession()`** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | Action: [MODIFY]
  - FR: FR-006 — Observability tracing spans
  - Base: `SessionPromotionService` from `com.ntt.authservice.auth.application`
  - Details:
    - Add import: `import io.micrometer.observation.annotation.Observed`
    - Add annotation on `promoteSession()` method:
      ```kotlin
      @Observed(
          name = "anonymous.session.promote",
          contextualName = "promote-anonymous-session"
      )
      fun promoteSession(sessionId: String, userId: Long, tokenJti: String): PromotionResult {
          // ... existing method body UNCHANGED ...
      }
      ```
    - Zero changes to method body — annotation-only
    - Span covers: lock acquire (UUID) → verify session → transfer data (nested child span) → blacklist JTI → delete session → release lock (Lua)
    - Nested span hierarchy: `[anonymous.session.promote]` → `[anonymous.data.transfer]` (automatic via AOP when `transferData()` is called)
  - Dependencies: Task 1 (ObservedAspect must be available)

## Phase 4C: Verification & Closure

- [ ] **Task 5: FR-012 formal closure documentation** `[DOCUMENT]`
  - File: N/A | Action: [DOCUMENT]
  - FR: FR-012 — Running counter periodic reconciliation
  - Details:
    - FR-012 is formally closed as **WON'T FIX**
    - Rationale (from brainstorm_notes.md):
      1. `atomic_data_store.lua` guarantees atomic size check + write + counter increment
      2. Counter decrement in `deleteData()` uses atomic HINCRBY
      3. Sessions are ephemeral (max 24h TTL) — drift self-corrects
      4. `getSessionDataSizeScan()` retained at `AnonymousSessionDataService.kt` line 203 as private debug fallback
      5. Counter drift probability approaches 0% with Lua atomicity
    - **No code changes required** — this is a documentation-only task
    - Status: All other v2 FRs (FR-001 through FR-005, FR-007 through FR-011, FR-013, FR-014) remain ✅ IMPLEMENTED
  - Dependencies: None

- [ ] **Task 6: Verify application startup with `@Observed` annotations** `[VERIFY]`
  - File: N/A | Action: [VERIFY]
  - FR: FR-006 — Observability tracing spans (validation)
  - Details:
    - Run `./gradlew bootRun` or application startup
    - Verify no startup errors related to `@Observed` or `ObservedAspect`
    - Verify existing Counter/Timer metrics still function (coexistence check)
    - Verify span names appear in tracing backend (if connected):
      - `anonymous.session.create`
      - `anonymous.data.store`
      - `anonymous.data.transfer`
      - `anonymous.session.promote`
    - Verify nested span hierarchy: `promoteSession()` → `transferData()` creates parent-child relationship
  - Dependencies: Tasks 1-4

- [ ] **Task 7: FR traceability verification**
  - Verify all 14 FRs addressed:
    - FR-001: ✅ IMPLEMENTED (v2 — pipeline HSET+EXPIRE)
    - FR-002: ✅ IMPLEMENTED (v2 — sliding window Lua)
    - FR-003: ✅ IMPLEMENTED (v2 — pipeline MGET+MSET)
    - FR-004: ✅ IMPLEMENTED (v2 — UUID lock + Lua safe release)
    - FR-005: ✅ IMPLEMENTED (v2 — running counter via Lua)
    - FR-006: ✓ Task 2-4 (v3 — `@Observed` spans on 4 methods)
    - FR-007: ✅ IMPLEMENTED (v2 — atomic check-and-set via Lua)
    - FR-008: ✅ IMPLEMENTED (v2 — RedisLuaScriptConfig)
    - FR-009: ✅ IMPLEMENTED (v2 — 3 Lua script files)
    - FR-010: ✅ IMPLEMENTED (v2 — pipeline fallback)
    - FR-011: ✅ IMPLEMENTED (v2 — Lua script fallback)
    - FR-012: ❌ WON'T FIX (v3 — formally closed, Task 5)
    - FR-013: ✅ IMPLEMENTED (v2 — blacklist cleanup scheduler)
    - FR-014: ✅ IMPLEMENTED (v2 — config enhancements)
  - Coverage: **13/14 addressed** (12 implemented + 1 completing in this iteration). 1 formally closed.

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 7 (1 verify + 3 annotation + 1 document + 1 verify + 1 traceability) |
| Modified files | 3 (annotation-only) |
| New files | 0-1 (ObservabilityConfig.kt if needed) |
| FR-006 coverage | 4 spans on 4 critical methods |
| FR-012 disposition | WON'T FIX (documented) |
| Previously implemented FRs | 12/14 |
| New dependencies | 0 |
| Database migrations | 0 |
| Estimated effort | 0.5-1.0 developer-day |
| Risk | 🟢 Low — annotation-only changes, no logic modifications |
