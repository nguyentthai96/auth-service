# Pre-OpenSpec: anonymous-login-optimization

> **Type**: MAINTENANCE
> **Flow**: Non-Financial
> **Source**: Research Artifacts (business_analysis.md, technical_spec.md, research_brief.md, handoff_summary.md)
> **Classification Evidence**: keyword `anonymous`, `session`, `rate limit`, `pipeline`, `lua` → module `auth.application` → files `AnonymousSessionHandler.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt` (ALL EXIST — optimization of fully-implemented feature)
> **Archive**: `openspec/changes/archive/2026-08-20-anonymous-login-optimization/pre_openspec.md`
> **Previous Version**: MAINTENANCE (confirmed) — optimization/hardening of existing anonymous login system
> **Quality Score**: 90/100

## 📋 Feature Summary

[REUSE] Tối ưu hóa hệ thống anonymous login đã implement đầy đủ trong auth-service. Tất cả optimizations từ archive pre_openspec đã được implement thành công trong codebase hiện tại. Scope gồm: Redis round-trip reduction qua pipelining (`executePipelined`), sliding window rate limiting qua Lua script, batch data transfer khi promotion, safe distributed lock release với UUID ownership + Lua, running data size counter O(1), TOCTOU-safe atomic data store qua Lua script, và operational enhancements (cleanup scheduler, config, fallbacks). Không cần dependency mới — tất cả sử dụng Spring Data Redis APIs hiện có.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (Research: 9, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **Anonymous User**: Visitor chưa đăng nhập, tương tác qua anonymous JWT token. Trải nghiệm cải thiện latency từ optimization.
- **Authenticated User**: User đã xác thực, nhận dữ liệu từ anonymous session sau khi promotion (nhanh hơn nhờ batch transfer).
- **Auth Service (System)**: Service được tối ưu hóa — xử lý anonymous token lifecycle, session data CRUD, promotion logic.
- **Redis (External System)**: Nhận pipelined commands, thực thi Lua scripts, lưu trữ anonymous session data.
- **DevOps/SRE**: Hưởng lợi từ observability improvements (tracing spans).

## 2. Functional Requirements

### FR-001: Pipeline tạo anonymous session [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải batch HSET + EXPIRE thành 1 pipeline call khi tạo anonymous session thay vì 2 lệnh Redis tuần tự
- **Validation**: Session creation chỉ tạo 1 Redis pipeline round-trip (thay vì 2). Kết quả Redis state giống hệt behavior cũ. P95 latency < 50ms.
- **Source**: UC-OPT-001 (research_brief.md, business_analysis.md)
- **Status**: ✅ IMPLEMENTED — `AnonymousSessionHandler.kt` lines 69-76, `executePipelined { connection -> hMSet + expire }`

### FR-002: Sliding window rate limiting [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế fixed-window rate limiting (INCR+EXPIRE) bằng sliding window counter (Lua script) trong `AnonymousRateLimitService` để ngăn burst-at-boundary
- **Validation**: Lua script `sliding_window_rate_limit.lua` thực thi atomically. Weighted count formula: `prev × (1 - elapsed/window) + current`. Giá trị trả về: count (cho phép) hoặc -1 (denied). Redis key pattern: `anon:rate:{ip}:{windowId}`.
- **Source**: UC-OPT-002 (business_analysis.md, technical_spec.md)
- **Status**: ✅ IMPLEMENTED — `AnonymousRateLimitService.kt` dual-mode (sliding window default + fixed-window fallback), `sliding_window_rate_limit.lua` 35 lines

### FR-003: Batch data transfer khi promotion [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế per-key GET+SET loop trong `AnonymousSessionDataService.transferData()` bằng pipeline MGET + pipeline MSET, giảm từ 1+2N RTT xuống ~3 RTT
- **Validation**: SCAN collect keys → pipeline GET all → pipeline SET all user keys. Tất cả data được transfer chính xác. `DataTransferResult` giữ nguyên API.
- **Source**: UC-OPT-003 (business_analysis.md, technical_spec.md)
- **Status**: ✅ IMPLEMENTED — `AnonymousSessionDataService.kt` lines 142-170, dual `executePipelined` calls for GET+SET

### FR-004: Safe distributed lock release [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế simple `DELETE lockKey` bằng Lua conditional DELETE (kiểm tra UUID ownership) trong `SessionPromotionService.releaseLock()`
- **Validation**: Lock value phải là UUID (không phải static "locked"). Lua script `safe_lock_release.lua`: `if GET(key) == uuid then DEL(key); return 1; else return 0; end`. Ngăn chặn cross-process lock release.
- **Source**: UC-OPT-004 (business_analysis.md, technical_spec.md, brainstorm_notes.md)
- **Status**: ✅ IMPLEMENTED — `SessionPromotionService.kt` UUID lock (`UUID.randomUUID()`) + Lua `safeLockReleaseScript` at lines 152-156

### FR-005: Running data size counter [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải duy trì `dataSize` field trong session hash (`anon:session:{id}`) thay vì SCAN+STRLEN loop trong `getSessionDataSize()`, sử dụng HINCRBY atomic increment/decrement
- **Validation**: `dataSize=0` khi tạo session mới. HINCRBY +size sau `storeData()`. HINCRBY -size sau `deleteData()`. Counter không bao giờ < 0. O(1) thay O(N).
- **Source**: UC-OPT-005 (business_analysis.md, technical_spec.md)
- **Status**: ✅ IMPLEMENTED — `AnonymousSessionHandler.kt` line 66 (`"dataSize" to "0"`), `AnonymousSessionDataService.kt` `getSessionDataSize()` reads via HGET, `deleteData()` decrements via `increment(sessionKey, "dataSize", -deletedSize)`, `storeData()` incremented atomically via Lua

### FR-006: Observability tracing spans [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải bổ sung Micrometer Observation spans cho các critical paths: session creation, data store, token renewal, session promotion
- **Validation**: Spans visible trong tracing system (Zipkin/Jaeger). Sử dụng `Observation.createNotStarted(name, registry)`.
- **Source**: UC-OPT-006 (business_analysis.md, technical_spec.md)
- **Status**: 🟡 PARTIAL — Micrometer counters/timers exist (`auth.anonymous.sessions.created`, `auth.anonymous.rate_limited`, `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`, `auth.anonymous.sessions.promoted`, `auth.anonymous.promotion.duration`, `auth.anonymous.token.generation.duration`). Full Observation API spans not yet added (only Counter/Timer used, not `Observation.createNotStarted()`).

### FR-007: Fix TOCTOU race condition trong storeData [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải atomic hóa size check + data write trong `AnonymousSessionDataService.storeData()` — sử dụng Lua script `atomic_data_store.lua` cho TOCTOU-safe check-and-set
- **Validation**: Lua script atomically: HGET dataSize → check limit → SET data → HINCRBY dataSize. Không thể vượt `maxDataSizeBytes` dù concurrent requests.
- **Source**: brainstorm_notes.md Q3
- **Status**: ✅ IMPLEMENTED — `atomic_data_store.lua` 26 lines, `AnonymousSessionDataService.storeData()` uses `atomicDataStoreScript`

### FR-008: RedisLuaScriptConfig configuration class [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tạo `RedisLuaScriptConfig` Spring `@Configuration` class để define `DefaultRedisScript<Long>` beans cho Lua scripts
- **Validation**: Lua scripts loaded từ `resources/redis/`. SHA1 cached cho performance. Spring beans injectable.
- **Source**: technical_spec.md §9.1
- **Status**: ✅ IMPLEMENTED — `RedisLuaScriptConfig.kt` with 3 beans: `slidingWindowRateLimitScript`, `safeLockReleaseScript`, `atomicDataStoreScript`

### FR-009: Lua script files [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tạo 3 Lua script files trong `resources/redis/`: `sliding_window_rate_limit.lua` (weighted sliding window counter), `safe_lock_release.lua` (ownership-checked lock release), `atomic_data_store.lua` (TOCTOU-safe data store with size enforcement)
- **Validation**: Scripts syntax-valid. Unit tests verify correct behavior. SHA1 cached via `DefaultRedisScript`.
- **Source**: technical_spec.md §9.3
- **Status**: ✅ IMPLEMENTED — `sliding_window_rate_limit.lua` (35 lines), `safe_lock_release.lua` (11 lines), `atomic_data_store.lua` (26 lines)

### FR-010: Pipeline fallback khi failure [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải fallback về sequential Redis calls khi `executePipelined()` throws exception, đảm bảo session vẫn được tạo thành công
- **Validation**: Log warning "Pipeline failed, falling back to sequential". Session created regardless of pipeline failure.
- **Justification**: Reliability — pipeline là optimization, không phải critical path.
- **Status**: ✅ IMPLEMENTED — `AnonymousSessionHandler.kt` try/catch around `executePipelined` with sequential fallback at lines 78-81

### FR-011: Lua script fallback khi failure [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải fallback về fixed-window rate limiting khi Lua script execution fails (e.g., Redis version không support, script error)
- **Validation**: Log warning. Rate limiting vẫn hoạt động (degraded but functional).
- **Justification**: Availability — Lua script failure không được block request.
- **Status**: ✅ IMPLEMENTED — `AnonymousRateLimitService.kt` catch-all around Lua EVAL falls back to `checkFixedWindow()` at lines 96-98

### FR-012: Running counter periodic reconciliation [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống nên có periodic reconciliation giữa running `dataSize` counter và actual SCAN+STRLEN để phát hiện drift
- **Validation**: Reconciliation có thể chạy as background scheduled task hoặc on-demand. Counter reset nếu drift > threshold.
- **Justification**: Data integrity — running counter có thể drift nếu crash giữa write + increment.
- **Status**: 🟡 DEFERRED — `getSessionDataSizeScan()` private method available as on-demand fallback but no scheduled caller implemented

### FR-013: TokenBlacklist cleanup scheduler [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải scheduled cleanup expired `token_blacklist` entries (anonymous-related, `reason IN ('PROMOTION', 'RENEWAL')`) để ngăn unbounded table growth
- **Validation**: Chạy mỗi 6 giờ (configurable). DELETE FROM token_blacklist WHERE expires_at < NOW(). Extend `SessionCleanupScheduler` hiện có.
- **Justification**: Operational — mỗi renewal tạo 1 row (max 24/session), mỗi promotion tạo 1 row. Table growth O(sessions × renewals).
- **Status**: ✅ IMPLEMENTED — `SessionCleanupScheduler.kt` method `cleanupExpiredBlacklistEntries()`, cron `0 0 */6 * * *`

### FR-014: Configuration enhancements [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải bổ sung config cho `AnonymousProperties`: `slidingWindowEnabled: Boolean` (feature flag), `scanCount: Int` (SCAN batch size, default 100, tunable)
- **Validation**: Config có default values. Startup validation cho sensible ranges.
- **Justification**: Operational flexibility — toggle sliding window, tune SCAN batch size.
- **Status**: ✅ IMPLEMENTED — `SecurityProperties.kt` `AnonymousProperties` has `slidingWindowEnabled: Boolean = true` and `scanCount: Int = 100`

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Status |
|--------|------|---------|--------|--------|
| NFR-001 | Performance | Session creation P95 latency | < 50ms (from ~100ms) | ✅ Pipeline implemented |
| NFR-002 | Accuracy | Rate limiting precision | Within 2% of true sliding window | ✅ Lua script implemented |
| NFR-003 | Performance | Data transfer P95 latency (10 keys) | < 10ms (from ~50ms) | ✅ Pipeline MGET+MSET implemented |
| NFR-004 | Reliability | Lock safety | Zero cross-process lock releases | ✅ UUID + Lua safe release |
| NFR-005 | Performance | Data store with size check P95 | < 5ms (from ~20ms) | ✅ Running counter + atomic Lua |
| NFR-006 | Observability | Tracing span coverage | 100% of anonymous session critical paths | 🟡 Counters/Timers only (no Observation spans) |
| NFR-007 | Data Integrity | Size limit enforcement under concurrency | Zero over-limit writes | ✅ Atomic Lua script |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-001→FR-009 cover distinct optimization areas. FR-010→FR-014 cover distinct operational enhancements.

[REUSE] So với archive pre_openspec: FRs giữ nguyên cấu trúc. 12/14 FRs đã implemented, 2 FRs chưa hoàn thành (FR-006 partial, FR-012 deferred).

## 5. Enriched Domain Requirements

Đã bổ sung 5 FR enriched (FR-010 → FR-014) — giữ nguyên từ archive.

### Enriched FRs

- **FR-010**: Pipeline fallback — reliability (Priority 1) — ✅ IMPLEMENTED
- **FR-011**: Lua script fallback — availability (Priority 2) — ✅ IMPLEMENTED
- **FR-012**: Running counter reconciliation — data integrity (Priority 3) — 🟡 DEFERRED
- **FR-013**: TokenBlacklist cleanup — operational hygiene (Priority 4) — ✅ IMPLEMENTED
- **FR-014**: Configuration enhancements — operational flexibility (Priority 5) — ✅ IMPLEMENTED

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Pipeline commands, Lua script execution, session data, rate limiting, distributed lock | `StringRedisTemplate` + 3 Lua scripts via `RedisLuaScriptConfig`. Redis 7+ required for EVAL. |
| PostgreSQL | Token blacklist cleanup (bảng `token_blacklist` hiện có) | Cleanup via `SessionCleanupScheduler.cleanupExpiredBlacklistEntries()` |

## 6. Assumptions

- ⚠️ Assumption: Redis 7+ is deployed — Lý do: Lua EVAL support required for sliding window + safe lock release + atomic data store scripts
- ⚠️ Assumption: Single-node Redis deployment (Redlock unnecessary) — Lý do: auth-service uses single Redis instance; simple SETNX with UUID ownership sufficient
- ⚠️ Assumption: Spring Data Redis `executePipelined()` available with Lettuce driver — Lý do: verified in codebase — `executePipelined` used in 3 places (AnonymousSessionHandler, AnonymousSessionDataService×2)
- ⚠️ Assumption: Existing Micrometer API available (Spring Boot 3.x) — Lý do: `MeterRegistry` injected and used in all anonymous services
- ⚠️ Assumption: Optimizations are internal only — no API contract changes — Lý do: confirmed — same endpoints, same request/response, same controller

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-006: "observability spans" cần list span names cụ thể |
| Đầy đủ (Completeness) | 23/25 | FR-012: reconciliation chưa implement; FR-006: chỉ Counter/Timer, chưa có Observation spans |
| Nhất quán (Consistency) | 24/25 | FR-002: key pattern migration implicit (old keys auto-expire via TTL) — documented nhưng chưa explicit verify |
| Kiểm thử được (Testability) | 19/25 | FR-001: pipeline RTT count khó verify chính xác; FR-003: "~3 RTT" là approximation; FR-006: observability span testing strategy chưa define |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích research) | Cách cải thiện |
|---|----------|----------|-----|------------------------|---------------|
| 1 | Clarity | -1 | FR-006 | Span names chưa liệt kê cụ thể — chỉ nói "critical paths" | List cụ thể: `anonymous.session.create`, `anonymous.data.store`, `anonymous.data.transfer`, `anonymous.session.promote` |
| 2 | Completeness | -1 | FR-012 | Reconciliation deferred — `getSessionDataSizeScan()` có sẵn nhưng no scheduled caller | Implement scheduled reconciliation task hoặc document decision to defer |
| 3 | Completeness | -1 | FR-006 | Counter/Timer metrics exist nhưng Observation API spans chưa implement | Add `Observation.createNotStarted()` spans cho full distributed tracing |
| 4 | Consistency | -1 | FR-002 | Key pattern change documented nhưng migration verification chưa có integration test | Add integration test verifying old keys don't interfere with new sliding window |
| 5 | Testability | -3 | FR-001 | Pipeline RTT count verification cần Redis command monitor hoặc interceptor | Use embedded Redis integration test with command counting |
| 6 | Testability | -3 | FR-003 | "~3 RTT" approximation — SCAN iterations depend on data volume | Test with dataset < count=100 to guarantee single SCAN iteration |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Incomplete | 🟡 | FR-006: Observability chỉ có Counter/Timer metrics — chưa có Observation API spans cho distributed tracing | FR-006 | Add `Observation.createNotStarted()` spans. Consider dùng `@Observed` annotation (Micrometer AOP). |
| 2 | Missing | 🟡 | FR-012: Running counter reconciliation deferred — `getSessionDataSizeScan()` private method available nhưng no periodic caller | FR-012 | Implement scheduled reconciliation hoặc explicit defer decision. Counter drift risk thấp do atomic Lua script (FR-007) bảo vệ writes. |
| 3 | Incomplete | 🟡 | Performance benchmarking chưa thực hiện — optimizations implemented nhưng chưa verify P95 improvements | NFR-001, NFR-003, NFR-005 | Run load test so sánh before/after latency. Cần embedded Redis hoặc staging environment. |

## 9. Open Questions

- FR-006: Nên dùng `Observation.createNotStarted()` (programmatic) hay `@Observed` annotation (AOP-based) cho observability spans? **Recommendation**: `@Observed` — less invasive, consistent with Spring Boot 3.x best practices.
- FR-012: Có cần implement reconciliation scheduler hay defer vĩnh viễn? **Recommendation**: Defer — `atomic_data_store.lua` đã bảo vệ writes atomically nên counter drift risk rất thấp. Giữ `getSessionDataSizeScan()` method as on-demand fallback.

## 10. Detected Scope

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Session Management — tối ưu hóa internal Redis operations và hardening data integrity cho anonymous session subsystem đã implement đầy đủ.

### 10.2 Flow Type

Non-Financial — không có giao dịch tài chính. Session creation, data storage, promotion là command-style operations. Optimization không thay đổi flow type.

### 10.3 Candidate Services
- **auth-service** (primary): Keyword match `anonymous`, `session`, `pipeline`, `rate limit`, `lua` → module `auth.application` → files: `AnonymousSessionHandler.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt`, `RenewAnonymousTokenHandler.kt`, `SessionCleanupScheduler.kt`
- **auth-service/shared**: Config → module `shared.config` → files: `SecurityProperties.kt` (AnonymousProperties), `RedisConfig.kt`, `RedisLuaScriptConfig.kt`
- **auth-service/shared**: Exception → module `shared.exception` → files: `AnonymousExceptions.kt` (5 exception classes), `AuthErrorCode.kt`
- **auth-service/resources**: Lua scripts → `resources/redis/` → files: `sliding_window_rate_limit.lua`, `safe_lock_release.lua`, `atomic_data_store.lua`

### Detection Evidence
- Keyword: `anonymous`, `pipeline` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — `executePipelined` at line 69, `dataSize=0` at line 66 ✅ VERIFIED
- Keyword: `rate limit`, `sliding window` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — Lua script execution at line 79-84, dual-mode with fallback ✅ VERIFIED
- Keyword: `transfer`, `batch` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — pipeline MGET at line 142, pipeline MSET at line 163 ✅ VERIFIED
- Keyword: `lock`, `safe release` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — UUID lock at line 135, Lua safe release at lines 152-156 ✅ VERIFIED
- Keyword: `size`, `counter` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — HGET dataSize in `getSessionDataSize()` at line 195, HINCRBY decrement in `deleteData()` ✅ VERIFIED
- Keyword: `atomic`, `storeData` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — `atomicDataStoreScript` execution in `storeData()` at lines 55-61 ✅ VERIFIED
- Keyword: `RedisLuaScriptConfig` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` — 3 `DefaultRedisScript<Long>` beans ✅ VERIFIED
- Keyword: `Lua script` → Module: `resources/redis` → Files: `sliding_window_rate_limit.lua` (35 lines), `safe_lock_release.lua` (11 lines), `atomic_data_store.lua` (26 lines) ✅ VERIFIED
- Keyword: `token_blacklist cleanup` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` — `cleanupExpiredBlacklistEntries()` at line 63 ✅ VERIFIED
- Keyword: `slidingWindowEnabled`, `scanCount` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` — `AnonymousProperties` at lines 203-204 ✅ VERIFIED
- `executePipelined` → FOUND in 3 locations (AnonymousSessionHandler:69, AnonymousSessionDataService:142, AnonymousSessionDataService:163) ✅ VERIFIED
- `DefaultRedisScript` → FOUND in 4 files (RedisLuaScriptConfig, AnonymousSessionDataService, SessionPromotionService, AnonymousRateLimitService) ✅ VERIFIED

### 10.4 External Integrations
- **Redis**: `StringRedisTemplate` — pipeline commands (`executePipelined`), Lua script execution (`execute(DefaultRedisScript)`), session data CRUD. Auto-configured Spring bean, used across all anonymous services.
- **PostgreSQL**: `TokenBlacklistRepository` (JPA) — cleanup of expired blacklist entries. File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`

### 10.5 Required Modules
- `auth.application` — `AnonymousSessionDataService` (IMPLEMENTED: batch transfer, running counter, TOCTOU fix via Lua), `AnonymousRateLimitService` (IMPLEMENTED: sliding window Lua + fixed-window fallback), `SessionPromotionService` (IMPLEMENTED: UUID lock + Lua safe release), `SessionCleanupScheduler` (IMPLEMENTED: blacklist cleanup)
- `auth.application.command` — `AnonymousSessionHandler` (IMPLEMENTED: pipeline HSET+EXPIRE, dataSize=0, fallback)
- `shared.config` — `SecurityProperties.AnonymousProperties` (IMPLEMENTED: slidingWindowEnabled, scanCount), `RedisLuaScriptConfig` (IMPLEMENTED: 3 script beans)
- `resources/redis/` — `sliding_window_rate_limit.lua` (IMPLEMENTED), `safe_lock_release.lua` (IMPLEMENTED), `atomic_data_store.lua` (IMPLEMENTED)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Anonymous User | Truy cập app, gửi POST /api/v1/auth/anonymous | Rate limit check via Lua sliding window (1 RTT) → generate sessionId+JWT → pipeline HSET+EXPIRE (1 RTT) → return token. [OPTIMIZED: 3-4 RTT → 2 RTT] ✅ |
| 2 | Anonymous User | Lưu data (PUT /session/data) | Atomic size check via Lua `atomic_data_store.lua` (HGET dataSize → check limit → SET data → HINCRBY dataSize, 1 RTT). [OPTIMIZED: SCAN+STRLEN O(N) → Lua O(1)] ✅ |
| 3 | Anonymous User | Token gần hết hạn (POST /renew) | Parse token → verify session → check renewalCount → generate new JWT → blacklist old JTI → increment counter + refresh TTL |
| 4 | Anonymous User | Đăng nhập (POST /login) | Standard login + promotion: acquire lock (UUID value, SETNX) → verify session → batch transfer (pipeline MGET+MSET, ~3 RTT) → blacklist → delete session → safe lock release (Lua conditional DEL). [OPTIMIZED: 1+2N RTT → 3 RTT] ✅ |

## 12. Traceability Matrix

| FR-ID | Research Section | Spec Section | Affected Class | Status |
|-------|-----------------|-------------|---------------|--------|
| FR-001 | BA UC-OPT-001 | TS §4.1, §9.7 | `AnonymousSessionHandler` (`src/main/kotlin/.../command/AnonymousSessionHandler.kt`) | ✅ [MODIFY] Pipeline HSET+EXPIRE — DONE |
| FR-002 | BA UC-OPT-002 | TS §9.3, §9.7 | `AnonymousRateLimitService` (`src/main/kotlin/.../AnonymousRateLimitService.kt`) | ✅ [MODIFY] Sliding window Lua + fallback — DONE |
| FR-003 | BA UC-OPT-003 | TS §4.2, §9.7 | `AnonymousSessionDataService` (`src/main/kotlin/.../AnonymousSessionDataService.kt`) | ✅ [MODIFY] Pipeline MGET+MSET in transferData() — DONE |
| FR-004 | BA UC-OPT-004 | TS §9.3, §9.7 | `SessionPromotionService` (`src/main/kotlin/.../SessionPromotionService.kt`) | ✅ [MODIFY] UUID lock + Lua safe release — DONE |
| FR-005 | BA UC-OPT-005 | TS §9.7 | `AnonymousSessionDataService`, `AnonymousSessionHandler` | ✅ [MODIFY] Running dataSize counter — DONE |
| FR-006 | BA UC-OPT-006 | TS §1.3 | All anonymous services | 🟡 [MODIFY] Counter/Timer metrics exist; Observation spans pending |
| FR-007 | Brainstorm Q3 | N/A | `AnonymousSessionDataService` (`src/main/kotlin/.../AnonymousSessionDataService.kt`) | ✅ [MODIFY] Atomic Lua check-and-set in storeData() — DONE |
| FR-008 | TS §9.1 | TS §9.1 | `RedisLuaScriptConfig` (`shared.config`) | ✅ [ADD] 3 DefaultRedisScript beans — DONE |
| FR-009 | TS §9.3 | TS §9.3 | Lua scripts in `resources/redis/` | ✅ [ADD] 3 Lua scripts — DONE |
| FR-010 | BA UC-OPT-001 AF-001 | TS §4.1 | `AnonymousSessionHandler` | ✅ [MODIFY] Pipeline fallback — DONE |
| FR-011 | BA UC-OPT-002 AF-001 | TS §4.2 | `AnonymousRateLimitService` | ✅ [MODIFY] Lua script fallback — DONE |
| FR-012 | Brainstorm Q3 | N/A | `AnonymousSessionDataService` or Scheduler | 🟡 [ADD] Deferred — SCAN fallback method retained |
| FR-013 | Brainstorm Q5 | N/A | `SessionCleanupScheduler` (EXISTING) | ✅ [MODIFY] Blacklist cleanup added — DONE |
| FR-014 | Brainstorm Q3+research | TS §9.5 | `SecurityProperties.AnonymousProperties` | ✅ [MODIFY] slidingWindowEnabled + scanCount — DONE |

### Change Impact Map (MAINTENANCE)

```
FR-001 → [REUSE] AnonymousSessionHandler — Pipeline HSET+EXPIRE already implemented (line 69)
FR-002 → [REUSE] AnonymousRateLimitService — Sliding window Lua already implemented (dual-mode)
FR-003 → [REUSE] AnonymousSessionDataService — Pipeline MGET+MSET already implemented (lines 142, 163)
FR-004 → [REUSE] SessionPromotionService — UUID lock + Lua safe release already implemented
FR-005 → [REUSE] AnonymousSessionDataService + AnonymousSessionHandler — Running counter already implemented
FR-006 → [MODIFY] All anonymous services → Need Observation spans (current: Counter/Timer only)
FR-007 → [REUSE] AnonymousSessionDataService — atomic_data_store.lua already implemented
FR-008 → [REUSE] RedisLuaScriptConfig — 3 script beans already created
FR-009 → [REUSE] resources/redis/ — 3 Lua scripts already created
FR-010 → [REUSE] AnonymousSessionHandler — Pipeline fallback already implemented (lines 78-81)
FR-011 → [REUSE] AnonymousRateLimitService — Lua fallback already implemented (lines 96-98)
FR-012 → [ADD] Reconciliation scheduler — deferred (getSessionDataSizeScan retained as fallback)
FR-013 → [REUSE] SessionCleanupScheduler — Blacklist cleanup already implemented
FR-014 → [REUSE] SecurityProperties.AnonymousProperties — Config fields already added
```

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations

- **[REUSE] Implementation status: 12/14 FRs complete**: Codebase hiện tại đã implement đầy đủ hầu hết optimizations từ archive pre_openspec. Chỉ còn 2 FRs chưa hoàn thành: FR-006 (observability — partial, chỉ Counter/Timer chưa có Observation spans) và FR-012 (reconciliation — deferred, SCAN fallback method có sẵn nhưng chưa có scheduled caller).
- **3-Lua-Script Architecture hoàn chỉnh**: `sliding_window_rate_limit.lua` (35 lines), `safe_lock_release.lua` (11 lines), `atomic_data_store.lua` (26 lines) — tất cả managed qua `RedisLuaScriptConfig` with SHA1 caching.
- **Critical bugs đã fix**: TOCTOU race condition (FR-007) fixed via `atomic_data_store.lua`, unsafe lock release (FR-004) fixed via UUID ownership + `safe_lock_release.lua`.
- **Zero new dependencies**: Tất cả optimizations sử dụng Spring Data Redis APIs hiện có (`executePipelined`, `execute(DefaultRedisScript)`).
- **Dual-mode pattern**: `AnonymousRateLimitService` supports both sliding window (Lua, default) and fixed-window (INCR+EXPIRE, fallback) — toggled via `slidingWindowEnabled` feature flag.
- **Metrics comprehensive**: 7 metric points covering session creation, rate limiting, data operations, promotion — only missing full Observation spans.

### Related Features / Precedents

- **LoginRateLimitService** (`src/main/kotlin/.../LoginRateLimitService.kt`): Uses fixed-window pattern. Sliding window could be applied here too if needed.
- **MfaRateLimitService** (`src/main/kotlin/.../MfaRateLimitService.kt`): Same `StringRedisTemplate` usage, fail-open strategy, lock key pattern.
- **SessionCleanupScheduler** (`src/main/kotlin/.../SessionCleanupScheduler.kt`): Extended with blacklist cleanup (FR-013). Two cron jobs: inactive sessions (5 min) + expired blacklist (6 hrs).

### Integration Notes

- **executePipelined**: Now used in 3 locations — AnonymousSessionHandler (session creation), AnonymousSessionDataService (transfer MGET, transfer MSET).
- **DefaultRedisScript**: Now used in 3 service classes — AnonymousRateLimitService (sliding window), SessionPromotionService (safe lock release), AnonymousSessionDataService (atomic data store).
- **Key patterns stable**: `anon:session:{id}` (hash), `anon:data:{id}:{ns}:{key}` (string), `anon:rate:{ip}:{windowId}` (counter), `anon:lock:{id}` (lock). No key pattern changes needed.
- **Backward compatible**: Sessions created before optimization have no `dataSize` field → `getSessionDataSize()` returns 0 (null-safe via `?.toLongOrNull() ?: 0L`).

### Suggested Approach (Remaining Work)

1. **FR-006 Observability Spans**: Add `@Observed` annotations or `Observation.createNotStarted()` to critical methods in AnonymousSessionHandler, AnonymousSessionDataService, SessionPromotionService. Estimated: 0.5 dev-day.
2. **FR-012 Reconciliation (Optional)**: Implement scheduled reconciliation task using existing `getSessionDataSizeScan()` method. Low priority — `atomic_data_store.lua` makes counter drift nearly impossible. Estimated: 0.5 dev-day if desired.
3. **Performance Validation**: Run load test comparing P95 latency metrics against NFR targets. Requires staging environment with Redis.

### Context from Confluence Images

N/A — không có Confluence source. Input từ research artifacts.
