# Impact Analysis: anonymous-login-optimization

_Generated: 2025-07-15 (v3 — Focused Completion: FR-006 + FR-012 Closure)_

> **Type**: MAINTENANCE — Observability completion + formal closure of deferred FR
> **Previous**: Archive v2 covered Redis pipelining, Lua scripting, data integrity hardening (ALL IMPLEMENTED)
> **Direction**: Approach D — Focused Completion — FR-006 Observability Spans + FR-012 Decision Closure + Performance Validation Strategy (brainstorm_notes.md)

[CHANGED] Scope reduced from archive v2 (12 active FRs covering Redis optimization + data integrity) to v3 (1 active FR: FR-006 Observability Spans + 1 formal closure: FR-012 WON'T FIX). All v2 FRs are fully implemented in the codebase.

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [AnonymousSessionHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | L40 (class declaration) | [MODIFY] Add `@Observed(name = "anonymous.session.create")` annotation on `handle()` method (FR-006) |
| 2 | [AnonymousSessionDataService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | L37, L88 | [MODIFY] Add `@Observed(name = "anonymous.data.store")` on `storeData()`, `@Observed(name = "anonymous.data.transfer")` on `transferData()` (FR-006) |
| 3 | [SessionPromotionService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | L45 | [MODIFY] Add `@Observed(name = "anonymous.session.promote")` on `promoteSession()` method (FR-006) |

### New Files

None — FR-006 uses annotation-driven AOP, no new files required.

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### FR-006: `@Observed` Annotation Additions — No Logic Changes

```
⟶ AnonymousSessionHandler.handle(command)
│   [ADD @Observed(name = "anonymous.session.create")]  // ← AOP wraps method with Observation span
├── rateLimitService.checkRateLimit()                    // ← covered by parent span
├── sessionId = UUID.randomUUID()
├── token = jwtService.generateAnonymousToken()
├── redisTemplate.executePipelined { ... }               // ← traced via parent span
└── return AnonymousSessionResult(...)

⟶ AnonymousSessionDataService.storeData(sessionId, namespace, key, value)
│   [ADD @Observed(name = "anonymous.data.store")]       // ← AOP wraps method
├── verifySessionExists(sessionId)
├── redisTemplate.execute(atomicDataStoreScript, ...)    // ← Lua execution traced
└── meterRegistry.counter("auth.anonymous.data.stored").increment()

⟶ AnonymousSessionDataService.transferData(sessionId, userId)
│   [ADD @Observed(name = "anonymous.data.transfer")]    // ← AOP wraps method
├── SCAN collect all keys
├── redisTemplate.executePipelined { GET all }            // ← pipeline traced
├── redisTemplate.executePipelined { SET all }            // ← pipeline traced
└── return DataTransferResult(...)

⟶ SessionPromotionService.promoteSession(sessionId, userId, jti)
│   [ADD @Observed(name = "anonymous.session.promote")]  // ← AOP wraps method
├── acquireLock(sessionId)                                // ← UUID lock
├── dataService.transferData(sessionId, userId)           // ← has own span
├── blacklist(jti)                                        // ← DB operation traced
├── deleteSession(sessionId)
└── releaseLock(sessionId, ownerUUID)                     // ← Lua safe release
```

**Note:** `@Observed` creates a parent Observation span around the entire method. Sub-operations (Redis pipeline, Lua EVAL, DB save) are traced via parent span context propagation. No method body changes needed.

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AnonymousSessionHandler.kt` | [AnonymousSessionHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | Add `@Observed` annotation on `handle()` — annotation-only, no logic change |
| 2 | `AnonymousSessionDataService.kt` | [AnonymousSessionDataService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | Add `@Observed` annotations on `storeData()` and `transferData()` — annotation-only |
| 3 | `SessionPromotionService.kt` | [SessionPromotionService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | Add `@Observed` annotation on `promoteSession()` — annotation-only |

### 🟡 Indirect Impact — auth-service (0 files)

No indirect impact. `@Observed` is annotation-driven AOP — transparent to callers. No API signature changes, no behavioral changes.

### 🟠 Cross-service Impact (0 files)

No cross-service impact. Observation spans are internal — they appear in distributed tracing systems (Zipkin/Jaeger) but don't affect inter-service communication.

### 🟢 Shared Utilities (0 files)

No shared utility changes. `@Observed` annotation comes from `io.micrometer.observation.annotation` (provided by `base-observability-starter` dependency). `ObservationRegistry` auto-configured by Spring Boot Actuator.

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| `@Observed` annotation pattern | NOT FOUND in codebase | 0% | NEW | 🟢 (0 callers — annotation addition) | First `@Observed` usage in project — establishes pattern for future observability |
| `MeterRegistry` Counter/Timer metrics | [AnonymousSessionHandler:L73](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt#L73), [AnonymousRateLimitService:L91](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt#L91) | 100% | REUSE — coexists | 🟢 (existing metrics unchanged) | `@Observed` spans SUPPLEMENT existing Counter/Timer — both coexist |
| FR-012 reconciliation (`getSessionDataSizeScan`) | [AnonymousSessionDataService:L203](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt#L203) | 100% | REUSE as-is (WON'T FIX) | 🟢 (0 callers — private, unused) | Retain as debug fallback. No scheduled caller needed — `atomic_data_store.lua` eliminates drift risk. |

**Summary:** Zero EXTRACT needed. FR-006 is annotation-only additions. FR-012 is formal closure with no code changes.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `@Observed` | Micrometer annotation | `@Observed(name = "...", contextualName = "...")` | From `io.micrometer.observation.annotation.Observed`. Requires AOP support (`spring-boot-starter-aop`). `base-observability-starter` likely provides this. |
| `ObservationRegistry` | Auto-configured bean | Auto-injected by Spring Boot Actuator when `@Observed` annotation is detected | No explicit injection needed — AOP aspect handles registration automatically |
| `base-observability-starter` | Project dependency | Provides Micrometer Observation API + auto-configuration | Confirmed in `build.gradle.kts` line 16 |
| `spring-boot-starter-actuator` | Project dependency | Provides auto-configuration for `ObservationRegistry` bean | Confirmed in `build.gradle.kts` line 17 |

### Config Keys

| Key | Source | Example value | Nơi dùng |
|-----|--------|--------------|---------|
| `management.observations.annotations.enabled` | Spring Boot auto-config | `true` (default) | Enables `@Observed` annotation processing |

### Error Codes Thrown

No new error codes. FR-006 adds observability spans — no error handling changes.

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| N/A | N/A | N/A | No new DTOs — annotation-only changes |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `@Observed(name = "...")` | `io.micrometer.observation.annotation.Observed` | Micrometer docs | ✅ Available via `base-observability-starter` |
| `ObservationRegistry` auto-config | Spring Boot `ObservationAutoConfiguration` | Spring Boot 3.x docs | ✅ Auto-configured by Actuator |
| `@Observed` AOP processing | `ObservedAspect` bean | Spring Boot Observation docs | ⚠️ Verify `ObservedAspect` bean exists or needs explicit registration |
