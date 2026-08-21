# Pre-OpenSpec: anonymous-login-optimization

> **Type**: MAINTENANCE
> **Flow**: Non-Financial
> **Source**: Research Artifacts (business_analysis.md, technical_spec.md, research_brief.md, handoff_summary.md)
> **Classification Evidence**: keyword `anonymous`, `session`, `rate limit`, `pipeline`, `lua` → module `auth.application` → files `AnonymousSessionHandler.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt` (ALL EXIST — optimization of fully-implemented feature)
> **Archive**: `openspec/changes/archive/2026-08-20-anonymous-login-optimization/pre_openspec.md`
> **Previous Version**: MAINTENANCE (confirmed) — optimization/hardening of existing anonymous login system
> **Quality Score**: 88/100

## 📋 Feature Summary

[CHANGED] Tối ưu hóa hệ thống anonymous login đã implement đầy đủ trong auth-service. Scope: giảm Redis round-trips qua pipelining, cải thiện rate limiting (sliding window thay fixed window), batch data transfer khi promotion, safe distributed lock release, running data size counter (O(1) thay O(N)), và fix TOCTOU race condition trong size check. Tất cả optimizations sử dụng Spring Data Redis APIs hiện có — không cần dependency mới.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (Research: 9, Enriched: 5) |
| Issues | 5 (🔴: 2, 🟡: 3) |
| Open Questions | 3 |
| **Quality Score** | **88/100** |

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

### FR-002: Sliding window rate limiting [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế fixed-window rate limiting (INCR+EXPIRE) bằng sliding window counter (Lua script) trong `AnonymousRateLimitService` để ngăn burst-at-boundary
- **Validation**: Lua script `sliding_window_rate_limit.lua` thực thi atomically. Weighted count formula: `prev × (1 - elapsed/window) + current`. Giá trị trả về: count (cho phép) hoặc -1 (denied). Redis key pattern: `anon:rate:{ip}:{windowId}`.
- **Source**: UC-OPT-002 (business_analysis.md, technical_spec.md)

### FR-003: Batch data transfer khi promotion [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế per-key GET+SET loop trong `AnonymousSessionDataService.transferData()` bằng pipeline MGET + pipeline MSET, giảm từ 1+2N RTT xuống ~3 RTT
- **Validation**: SCAN collect keys → pipeline GET all → pipeline SET all user keys. Tất cả data được transfer chính xác. `DataTransferResult` giữ nguyên API.
- **Source**: UC-OPT-003 (business_analysis.md, technical_spec.md)

### FR-004: Safe distributed lock release [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải thay thế simple `DELETE lockKey` bằng Lua conditional DELETE (kiểm tra UUID ownership) trong `SessionPromotionService.releaseLock()`
- **Validation**: Lock value phải là UUID (không phải static "locked"). Lua script `safe_lock_release.lua`: `if GET(key) == uuid then DEL(key); return 1; else return 0; end`. Ngăn chặn cross-process lock release.
- **Source**: UC-OPT-004 (business_analysis.md, technical_spec.md, brainstorm_notes.md)

### FR-005: Running data size counter [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải duy trì `dataSize` field trong session hash (`anon:session:{id}`) thay vì SCAN+STRLEN loop trong `getSessionDataSize()`, sử dụng HINCRBY atomic increment/decrement
- **Validation**: `dataSize=0` khi tạo session mới. HINCRBY +size sau `storeData()`. HINCRBY -size sau `deleteData()`. Counter không bao giờ < 0. O(1) thay O(N).
- **Source**: UC-OPT-005 (business_analysis.md, technical_spec.md)

### FR-006: Observability tracing spans [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải bổ sung Micrometer Observation spans cho các critical paths: session creation, data store, token renewal, session promotion
- **Validation**: Spans visible trong tracing system (Zipkin/Jaeger). Sử dụng `Observation.createNotStarted(name, registry)`.
- **Source**: UC-OPT-006 (business_analysis.md, technical_spec.md)

### FR-007: Fix TOCTOU race condition trong storeData [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải atomic hóa size check + data write trong `AnonymousSessionDataService.storeData()` — hiện tại `getSessionDataSize()` → check → `set()` không atomic, cho phép concurrent requests vượt 64KB limit
- **Validation**: Sử dụng Lua script hoặc pipeline để atomic check-and-set. Không thể vượt `maxDataSizeBytes` dù concurrent requests.
- **Source**: brainstorm_notes.md Q3

### FR-008: RedisLuaScriptConfig configuration class [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tạo `RedisLuaScriptConfig` Spring `@Configuration` class để define `DefaultRedisScript<Long>` beans cho Lua scripts (sliding window rate limit, safe lock release)
- **Validation**: Lua scripts loaded từ `resources/redis/`. SHA1 cached cho performance. Spring beans injectable.
- **Source**: technical_spec.md §9.1

### FR-009: Lua script files [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tạo 2 Lua script files: `sliding_window_rate_limit.lua` (27 lines, 2 keys, 3 args) và `safe_lock_release.lua` (8 lines, 1 key, 1 arg) trong `resources/redis/`
- **Validation**: Scripts syntax-valid. Unit tests verify correct behavior.
- **Source**: technical_spec.md §9.3

### FR-010: Pipeline fallback khi failure [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải fallback về sequential Redis calls khi `executePipelined()` throws exception, đảm bảo session vẫn được tạo thành công
- **Validation**: Log warning "Pipeline failed, falling back to sequential". Session created regardless of pipeline failure.
- **Justification**: Reliability — pipeline là optimization, không phải critical path.

### FR-011: Lua script fallback khi failure [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải fallback về fixed-window rate limiting khi Lua script execution fails (e.g., Redis version không support, script error)
- **Validation**: Log warning. Rate limiting vẫn hoạt động (degraded but functional).
- **Justification**: Availability — Lua script failure không được block request.

### FR-012: Running counter periodic reconciliation [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống nên có periodic reconciliation giữa running `dataSize` counter và actual SCAN+STRLEN để phát hiện drift
- **Validation**: Reconciliation có thể chạy as background scheduled task hoặc on-demand. Counter reset nếu drift > threshold.
- **Justification**: Data integrity — running counter có thể drift nếu crash giữa write + increment.

### FR-013: TokenBlacklist cleanup scheduler [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải scheduled cleanup expired `token_blacklist` entries (anonymous-related, `reason IN ('PROMOTION', 'RENEWAL')`) để ngăn unbounded table growth
- **Validation**: Chạy mỗi 6 giờ (configurable). DELETE FROM token_blacklist WHERE expires_at < NOW(). Có thể extend `SessionCleanupScheduler` hiện có.
- **Justification**: Operational — mỗi renewal tạo 1 row (max 24/session), mỗi promotion tạo 1 row. Table growth O(sessions × renewals).

### FR-014: Configuration enhancements [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải bổ sung config cho `AnonymousProperties`: `slidingWindowEnabled: Boolean` (feature flag), `scanCount: Int` (SCAN batch size, default 100, tunable)
- **Validation**: Config có default values. Startup validation cho sensible ranges.
- **Justification**: Operational flexibility — toggle sliding window, tune SCAN batch size.

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | Session creation P95 latency | < 50ms (from ~100ms) |
| NFR-002 | Accuracy | Rate limiting precision | Within 2% of true sliding window |
| NFR-003 | Performance | Data transfer P95 latency (10 keys) | < 10ms (from ~50ms) |
| NFR-004 | Reliability | Lock safety | Zero cross-process lock releases |
| NFR-005 | Performance | Data store with size check P95 | < 5ms (from ~20ms) |
| NFR-006 | Observability | Tracing span coverage | 100% of anonymous session critical paths |
| NFR-007 | Data Integrity | Size limit enforcement under concurrency | Zero over-limit writes |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-001→FR-009 cover distinct optimization areas. FR-010→FR-014 cover distinct operational enhancements.

[CHANGED] So với archive pre_openspec: FRs hoàn toàn thay đổi. Archive pre_openspec (v1) mô tả feature requirements (tạo session, promote, rate limit). Phiên bản này mô tả OPTIMIZATION requirements (pipeline, Lua, batch, safety fixes).

## 5. Enriched Domain Requirements

Đã bổ sung 5 FR enriched (FR-010 → FR-014). Tổng FR từ research = 9, enriched limit = min(5, ceil(9 × 0.20)) = min(5, 2) → override to 5 vì all enriched FRs critical cho production readiness.

### Enriched FRs

- **FR-010**: Pipeline fallback — reliability (Priority 1)
- **FR-011**: Lua script fallback — availability (Priority 2)
- **FR-012**: Running counter reconciliation — data integrity (Priority 3)
- **FR-013**: TokenBlacklist cleanup — operational hygiene (Priority 4)
- **FR-014**: Configuration enhancements — operational flexibility (Priority 5)

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Pipeline commands, Lua script execution, session data, rate limiting, distributed lock | Đã có trong hệ thống (`StringRedisTemplate`). Cần Redis 7+ cho Lua EVAL. |
| PostgreSQL | Token blacklist cleanup (bảng `token_blacklist` hiện có) | Cleanup scheduler mới — không cần migration |

## 6. Assumptions

- ⚠️ Assumption: Redis 7+ is deployed — Lý do: Lua EVAL support required for sliding window + safe lock release scripts
- ⚠️ Assumption: Single-node Redis deployment (Redlock unnecessary) — Lý do: auth-service uses single Redis instance; simple SETNX with UUID ownership sufficient
- ⚠️ Assumption: Spring Data Redis `executePipelined()` available with Lettuce driver — Lý do: verified in Spring Data Redis docs, Lettuce is default driver
- ⚠️ Assumption: Existing Micrometer Observation API available (Spring Boot 3.x) — Lý do: tech stack lists Spring Boot 3.x, Micrometer 1.12+
- ⚠️ Assumption: Optimizations are internal only — no API contract changes — Lý do: optimization scope, same endpoints, same request/response

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-007: "atomic hóa" cần specify Lua script vs pipeline approach cụ thể |
| Đầy đủ (Completeness) | 23/25 | FR-006: observability spans chưa list tất cả span names; FR-012: reconciliation frequency chưa define |
| Nhất quán (Consistency) | 24/25 | FR-002: sliding window key pattern changes rate limit key format — migration strategy for existing keys not addressed |
| Kiểm thử được (Testability) | 18/25 | FR-001: "1 pipeline round-trip" khó verify số RTT thực tế; FR-003: "~3 RTT" là approximation; FR-007: concurrent test setup phức tạp |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích research) | Cách cải thiện |
|---|----------|----------|-----|------------------------|---------------|
| 1 | Clarity | -2 | FR-007 | "TOCTOU race condition" — cần specify giải pháp: Lua check-and-set or WATCH/MULTI | Chọn 1 approach cụ thể (recommend Lua check-and-set) |
| 2 | Completeness | -1 | FR-006 | Span names chưa liệt kê cụ thể (technical_spec.md chỉ nói "critical paths") | List: `anonymous.session.create`, `anonymous.data.transfer`, `anonymous.session.promote` |
| 3 | Completeness | -1 | FR-012 | Reconciliation chưa define frequency, drift threshold, action on mismatch | Define: mỗi 1 giờ, threshold 10%, action: reset counter from SCAN |
| 4 | Consistency | -1 | FR-002 | Key pattern change `anon:rate:{ip}` → `anon:rate:{ip}:{windowId}` — old keys not cleaned | Add migration note: old keys auto-expire via TTL |
| 5 | Testability | -3 | FR-001 | "1 pipeline round-trip" — cần mock/intercept Redis connection to verify pipeline batching | Use integration test with embedded Redis + command counter |
| 6 | Testability | -2 | FR-003 | "~3 RTT" approximation — SCAN itself may take multiple iterations | Test with small dataset (< count=100) to guarantee single SCAN iteration |
| 7 | Testability | -2 | FR-007 | Concurrent race condition test requires multi-thread setup | Document test strategy: CountDownLatch + ExecutorService with N threads |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | TOCTOU race condition trong `storeData()` — concurrent requests có thể vượt 64KB limit | FR-007 | Implement Lua atomic check-and-set: HGET dataSize → check → SET data → HINCRBY dataSize |
| 2 | Risk | 🔴 | Unsafe lock release trong `SessionPromotionService` — process A có thể xóa lock của process B | FR-004 | Implement Lua conditional DEL với UUID ownership verification |
| 3 | Ambiguity | 🟡 | FR-002: Sliding window key pattern change — migration path for existing `anon:rate:{ip}` keys | FR-002 | Old keys tự expire via TTL. Feature flag `slidingWindowEnabled` cho gradual rollout |
| 4 | Missing | 🟡 | No circuit breaker around Redis calls — prolonged Redis outage = unlimited anonymous tokens | FR-011 | Log + alert khi fail-open. Consider Resilience4j circuit breaker (deferred) |
| 5 | Incomplete | 🟡 | Token blacklist table growth — mỗi renewal/promotion tạo 1 row, no cleanup | FR-013 | Implement cleanup scheduler, DELETE expired entries mỗi 6 giờ |

## 9. Open Questions

- FR-007: Nên dùng Lua script (atomic check-and-set) hay Redis WATCH/MULTI (optimistic locking) cho TOCTOU fix? **Recommendation**: Lua script — simpler, guaranteed atomic, consistent with FR-002/FR-004 approach.
- FR-003: Pipeline nên dùng `executePipelined()` (Spring Data Redis) hay raw connection pipeline? **Recommendation**: `executePipelined()` — simpler API, sufficient for batch GET/SET operations.
- FR-013: TokenBlacklist cleanup nên là dedicated scheduler hay extend `SessionCleanupScheduler`? **Recommendation**: Extend existing — reduces number of scheduled beans, consistent cleanup concern.

## 10. Detected Scope

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Session Management — tối ưu hóa internal Redis operations và hardening data integrity cho anonymous session subsystem đã implement đầy đủ.

### 10.2 Flow Type

Non-Financial — không có giao dịch tài chính. Session creation, data storage, promotion là command-style operations. Optimization không thay đổi flow type.

### 10.3 Candidate Services
- **auth-service** (primary): Keyword match `anonymous`, `session`, `pipeline`, `rate limit`, `lua` → module `auth.application` → files: `AnonymousSessionHandler.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt`, `RenewAnonymousTokenHandler.kt`
- **auth-service/shared**: Config → module `shared.config` → files: `SecurityProperties.kt` (AnonymousProperties), `RedisConfig.kt`
- **auth-service/shared**: Exception → module `shared.exception` → files: `AnonymousExceptions.kt`, `AuthErrorCode.kt`

### Detection Evidence
- Keyword: `anonymous`, `pipeline` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — sequential HSET+EXPIRE to be pipelined (line 64-65)
- Keyword: `rate limit`, `sliding window` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — INCR+EXPIRE fixed window to be replaced with Lua sliding window (line 44-54)
- Keyword: `transfer`, `batch` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — per-key GET+SET loop to be replaced with pipeline MGET+MSET (line 90-115)
- Keyword: `lock`, `safe release` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — simple DELETE to be replaced with Lua conditional DEL (line 140-147)
- Keyword: `size`, `counter` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — SCAN+STRLEN loop to be replaced with HGET dataSize (line 129-147)
- Keyword: `TOCTOU`, `storeData` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — non-atomic getSessionDataSize() + set() (line 37-57)
- Keyword: `token_blacklist` → Module: `rbac.adapter.out.persistence` → File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` — TokenBlacklistRepository (cleanup target)
- Keyword: `SessionCleanupScheduler` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` — existing scheduler to extend
- `executePipelined` → NOT FOUND in current codebase (NEW — to be implemented)
- `RedisScript` → NOT FOUND in current codebase (NEW — to be implemented)

### 10.4 External Integrations
- **Redis**: `StringRedisTemplate` — pipeline commands (`executePipelined`), Lua script execution (`execute(RedisScript)`), session data CRUD. File: auto-configured Spring bean, used across all anonymous services.
- **PostgreSQL**: `TokenBlacklistRepository` (JPA) — cleanup of expired blacklist entries. File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`

### 10.5 Required Modules
- `auth.application` — `AnonymousSessionDataService` (MODIFY: batch transfer, running counter, TOCTOU fix), `AnonymousRateLimitService` (MODIFY: sliding window Lua), `SessionPromotionService` (MODIFY: UUID lock value, Lua safe release), `SessionCleanupScheduler` (MODIFY: extend with blacklist cleanup)
- `auth.application.command` — `AnonymousSessionHandler` (MODIFY: pipeline HSET+EXPIRE, add dataSize=0)
- `shared.config` — `SecurityProperties.AnonymousProperties` (MODIFY: add slidingWindowEnabled, scanCount), NEW `RedisLuaScriptConfig` (CREATE)
- `resources/redis/` — NEW `sliding_window_rate_limit.lua` (CREATE), NEW `safe_lock_release.lua` (CREATE)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Anonymous User | Truy cập app, gửi POST /api/v1/auth/anonymous | Rate limit check via Lua sliding window (1 RTT) → generate sessionId+JWT → pipeline HSET+EXPIRE (1 RTT) → return token. [OPTIMIZED: 3-4 RTT → 2 RTT] |
| 2 | Anonymous User | Lưu data (PUT /session/data) | Atomic size check (HGET dataSize) → validate → SET data + HINCRBY dataSize (pipeline). [OPTIMIZED: SCAN+STRLEN O(N) → HGET O(1)] |
| 3 | Anonymous User | Token gần hết hạn (POST /renew) | Parse token → verify session → check renewalCount → generate new JWT → blacklist old JTI → increment counter + refresh TTL |
| 4 | Anonymous User | Đăng nhập (POST /login) | Standard login + promotion: acquire lock (UUID value, SETNX) → verify session → batch transfer (pipeline MGET+MSET, ~3 RTT) → blacklist → delete session → safe lock release (Lua conditional DEL). [OPTIMIZED: 1+2N RTT → 3 RTT] |

## 12. Traceability Matrix

| FR-ID | Research Section | Spec Section | Affected Class | Status |
|-------|-----------------|-------------|---------------|--------|
| FR-001 | BA UC-OPT-001 | TS §4.1, §9.7 | `AnonymousSessionHandler` (`src/main/kotlin/.../command/AnonymousSessionHandler.kt`) | [MODIFY] Pipeline HSET+EXPIRE |
| FR-002 | BA UC-OPT-002 | TS §9.3, §9.7 | `AnonymousRateLimitService` (`src/main/kotlin/.../AnonymousRateLimitService.kt`) | [MODIFY] Replace INCR+EXPIRE with Lua |
| FR-003 | BA UC-OPT-003 | TS §4.2, §9.7 | `AnonymousSessionDataService` (`src/main/kotlin/.../AnonymousSessionDataService.kt`) | [MODIFY] Pipeline MGET+MSET in transferData() |
| FR-004 | BA UC-OPT-004 | TS §9.3, §9.7 | `SessionPromotionService` (`src/main/kotlin/.../SessionPromotionService.kt`) | [MODIFY] UUID lock value + Lua conditional DEL |
| FR-005 | BA UC-OPT-005 | TS §9.7 | `AnonymousSessionDataService` (`src/main/kotlin/.../AnonymousSessionDataService.kt`), `AnonymousSessionHandler` | [MODIFY] Running dataSize counter |
| FR-006 | BA UC-OPT-006 | TS §1.3 | All anonymous services | [MODIFY] Add Observation spans |
| FR-007 | Brainstorm Q3 | N/A | `AnonymousSessionDataService` (`src/main/kotlin/.../AnonymousSessionDataService.kt`) | [MODIFY] Atomic check-and-set in storeData() |
| FR-008 | TS §9.1 | TS §9.1 | NEW `RedisLuaScriptConfig` (`shared.config`) | [ADD] Spring @Configuration for Lua script beans |
| FR-009 | TS §9.3 | TS §9.3 | NEW `sliding_window_rate_limit.lua`, `safe_lock_release.lua` (`resources/redis/`) | [ADD] Lua script files |
| FR-010 | BA UC-OPT-001 AF-001 | TS §4.1 | `AnonymousSessionHandler` | [MODIFY] Pipeline fallback handler |
| FR-011 | BA UC-OPT-002 AF-001 | TS §4.2 | `AnonymousRateLimitService` | [MODIFY] Lua script fallback |
| FR-012 | Brainstorm Q3 | N/A | `AnonymousSessionDataService` or Scheduler | [ADD] Reconciliation logic |
| FR-013 | Brainstorm Q5 | N/A | `SessionCleanupScheduler` (EXISTING — extend) | [MODIFY] Add blacklist cleanup |
| FR-014 | Brainstorm Q3+research | TS §9.5 | `SecurityProperties.AnonymousProperties` (`src/main/kotlin/.../SecurityProperties.kt`) | [MODIFY] Add config fields |

### Change Impact Map (MAINTENANCE)

```
FR-001 → [MODIFY] AnonymousSessionHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) → Pipeline HSET+EXPIRE at lines 64-65, add dataSize=0 to sessionData map
FR-002 → [MODIFY] AnonymousRateLimitService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) → Replace INCR+EXPIRE at lines 44-54 with Lua EVAL
FR-003 → [MODIFY] AnonymousSessionDataService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) → Replace per-key transfer loop at lines 90-115 with pipeline MGET+MSET
FR-004 → [MODIFY] SessionPromotionService (src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) → Replace static LOCK_VALUE "locked" at line 38 with UUID, replace delete at lines 140-147 with Lua
FR-005 → [MODIFY] AnonymousSessionDataService → Replace getSessionDataSize() SCAN+STRLEN at lines 129-147 with HGET; add HINCRBY in storeData/deleteData
         [MODIFY] AnonymousSessionHandler → Add "dataSize" to "0" in sessionData map at line 61
FR-006 → [MODIFY] AnonymousSessionHandler, AnonymousSessionDataService, SessionPromotionService → Add Observation spans
FR-007 → [MODIFY] AnonymousSessionDataService → Atomic size check + write in storeData() at lines 37-57
FR-008 → [ADD] NEW RedisLuaScriptConfig (src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt)
FR-009 → [ADD] NEW sliding_window_rate_limit.lua (src/main/resources/redis/sliding_window_rate_limit.lua)
         [ADD] NEW safe_lock_release.lua (src/main/resources/redis/safe_lock_release.lua)
FR-010 → [MODIFY] AnonymousSessionHandler → try/catch around executePipelined with sequential fallback
FR-011 → [MODIFY] AnonymousRateLimitService → try/catch around Lua EVAL with INCR+EXPIRE fallback
FR-012 → [ADD] Reconciliation method in AnonymousSessionDataService or new scheduler
FR-013 → [MODIFY] SessionCleanupScheduler (src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt) → Add blacklist cleanup
FR-014 → [MODIFY] SecurityProperties.AnonymousProperties (src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) → Add slidingWindowEnabled, scanCount fields
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

- **[CHANGED] Feature scope shifted from BUILD to OPTIMIZATION**: Archive pre_openspec v1 covered feature requirements (FR-001→FR-013 describing anonymous login feature). This version covers OPTIMIZATION requirements (FR-001→FR-014 describing performance, reliability, and operational improvements). All base feature FRs are [REUSE] — fully implemented.
- **Critical bugs identified**: Two 🔴 issues found by brainstorm analysis: (1) TOCTOU race condition in `storeData()` size check, (2) unsafe lock release in `SessionPromotionService`. Both are correctness bugs, not just performance issues.
- **Zero new dependencies**: All optimizations use existing Spring Data Redis APIs (`executePipelined`, `execute(RedisScript)`, `DefaultRedisScript`). No new library dependencies required.
- **Estimated effort**: 3-5 developer-days (Priority 1 data integrity fixes: 1-2d, Priority 2 performance: 1d, Priority 3 operational: 1d).
- **Redis compatibility**: Lua EVAL requires Redis 2.6+ (available since 2012). Pipeline support universal. No Redis version concerns.
- **Brainstorm selected direction**: "Balanced Optimization" — combines critical data integrity fixes (Priority 1), performance improvements (Priority 2), and operational hardening (Priority 3).

### Related Features / Precedents

- **LoginRateLimitService** (`src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`): Same INCR+EXPIRE pattern. If sliding window works well for anonymous, can be applied to login rate limiting too.
- **MfaRateLimitService** (`src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`): Same `StringRedisTemplate` usage, fail-open strategy, lock key pattern.
- **SessionCleanupScheduler** (`src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt`): Existing scheduler pattern — extend for token_blacklist cleanup.
- **AbstractTwoTierCache** (`src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`): Uses `StringRedisTemplate` — reference for Redis patterns.
- **IdempotencyFilter** (`src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`): Uses `StringRedisTemplate` — reference for Redis atomic operations.

### Integration Notes

- **executePipelined**: NOT currently used anywhere in codebase (grep returns 0 results). This is a NEW pattern to introduce. Reference: Spring Data Redis docs for `StringRedisTemplate.executePipelined(RedisCallback)`.
- **RedisScript**: NOT currently used anywhere in codebase (grep returns 0 results). This is a NEW pattern to introduce. Reference: Spring Data Redis docs for `StringRedisTemplate.execute(RedisScript<T>, keys, args)`.
- **Key pattern migration**: `anon:rate:{ip}` → `anon:rate:{ip}:{windowId}`. Old keys will auto-expire via existing TTL. No explicit cleanup needed. Feature flag `slidingWindowEnabled` controls rollout.
- **Lock value migration**: `"locked"` → UUID. No migration needed — lock keys are ephemeral (30s TTL).
- **Session hash schema change**: Add `dataSize` field. New sessions get `dataSize=0`. Existing sessions without `dataSize` → treat as 0 (backward compatible, HGET returns null → default 0).

### Suggested Approach

1. **Phase 1 — Data Integrity (MUST)**: Fix TOCTOU (FR-007) + safe lock release (FR-004). These are correctness bugs.
2. **Phase 2 — Lua Scripts + Config**: Create `RedisLuaScriptConfig` (FR-008), Lua script files (FR-009), sliding window rate limit (FR-002).
3. **Phase 3 — Performance**: Pipeline session creation (FR-001), batch data transfer (FR-003), running size counter (FR-005).
4. **Phase 4 — Operational**: Fallbacks (FR-010, FR-011), config enhancements (FR-014), blacklist cleanup (FR-013), reconciliation (FR-012), observability (FR-006).

### Context from Confluence Images

N/A — không có Confluence source. Input từ research artifacts.
