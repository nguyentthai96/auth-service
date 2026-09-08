# Pre-OpenSpec: performance-benchmark-testing

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (research artifacts — business_analysis.md + technical_spec.md + web_research.md)
> **Classification Evidence**: keyword `K6`, `load test`, `JMH` → module `tests/load/`, `src/jmh/`, `build.gradle.kts` → file `tests/load/auth_flow.js`, `src/jmh/kotlin/.../EncryptionBenchmark.kt`
> **Archive**: `openspec/changes/archive/2026-08-24-tps-performance-testing/` (10 FRs, EXTEND, implemented)
> **Quality Score**: 82/100
> **Mode**: DELTA — Archive implemented, new research expands scope significantly (Observability, Infrastructure Tuning, API Coverage, Chain Tests, Stress/Soak)

## 📋 Feature Summary

Mở rộng framework kiểm thử hiệu năng auth-service từ nền tảng đã implemented (archive: 10 FRs cho datasource-proxy, WireMock, K6 3→4 scripts, JMH 2 benchmarks) sang hệ thống benchmark TPS toàn diện. Phạm vi mới bao gồm: (1) Prometheus + Grafana observability stack tích hợp qua Docker Compose, (2) HikariCP explicit tuning + ZGC JVM flags, (3) K6 mở rộng từ 4 scripts lên 15+ scenarios bao phủ 23 API endpoints, (4) 8 API chain tests mô phỏng user journey thực tế, (5) JMH mở rộng thêm BCrypt/JWT/Redis benchmarks, (6) Stress test tìm breaking point và Soak test phát hiện memory leak, (7) Structured performance report template.

| Metric | Giá trị |
|--------|---------|
| Số FR | 21 (URD: 16, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 0 (3 resolved via brainstorm) |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **Performance Engineer**: Thiết kế test scenarios, chạy full load test, phân tích bottleneck, tối ưu hệ thống
- **Developer**: Viết JMH benchmarks, chạy single API tests, review kết quả per-endpoint
- **CI/CD Pipeline (System)**: Tự động chạy K6 tests mỗi merge/PR, enforce performance gates
- **DevOps Engineer**: Cấu hình observability stack (Prometheus + Grafana), JVM flags, Docker compose

## 2. Functional Requirements

### FR-001: Prometheus metrics endpoint [URD]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải expose `/actuator/prometheus` endpoint bằng cách cập nhật `application-core-observability.yml` thêm `prometheus` vào `exposure.include`. Dependency `micrometer-registry-prometheus` **ĐÃ CÓ SẴN** trong `base-observability-starter` — KHÔNG cần thêm vào `build.gradle.kts`
- **Validation**: Endpoint phải trả về Prometheus text format. Metrics bao gồm: `http_server_requests_seconds`, `hikaricp_connections_*`, `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `process_cpu_usage`
- **Evidence**: `base-observability-starter/build.gradle.kts:11` → `implementation("io.micrometer:micrometer-registry-prometheus")`

### FR-002: Grafana + Prometheus Docker Compose [URD]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải cung cấp `compose-perf.yaml` mở rộng từ `compose.yaml` hiện có, thêm Prometheus (prom/prometheus:v2.54+) và Grafana (grafana/grafana:11+) containers khi cần visual monitoring trong quá trình load test
- **Validation**: Prometheus scrape auth-service `/actuator/prometheus` endpoint mỗi 15s. Grafana tự động provision datasource Prometheus. Cả 2 container healthy trong 30s sau `docker compose up`

### FR-003: HikariCP explicit tuning [URD]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải cấu hình HikariCP explicit trong `application-db.yml` với `maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`, `max-lifetime`, `leak-detection-threshold` khi cần kiểm soát database connection pool cho high-throughput workloads
- **Validation**: Pool size tuning theo formula: `connections = (DB_cores × 2) + effective_spindle_count`. 2 profiles: **Profile 1** (4-core/8GB) → `maximum-pool-size=10`; **Profile 2** (8-core/32GB) → `maximum-pool-size=20`. Config qua env variables `${DB_POOL_MAX:10}`. Leak detection alert nếu connection held > 30s

### FR-004: JVM performance flags [URD]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải cung cấp JVM flags profile cho performance testing: ZGC garbage collector, fixed heap size, pre-touch, virtual thread pinning detection khi chạy benchmark hoặc load test
- **Validation**: `-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1024m -XX:+AlwaysPreTouch -Djdk.tracePinnedThreads=full`. GC pause < 50ms tại p99

### FR-005: K6 single API test cho tất cả endpoints [URD]
- **Actor**: Developer, Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 test scripts cho TỪNG API endpoint (23 endpoints từ 17+ controllers) với constant-arrival-rate executor khi cần xây dựng per-endpoint baseline latency
- **Validation**: Mỗi script đo p50/p95/p99 latency, error rate, TPS. Chạy tối thiểu 30s cho JIT warmup. P95 < 200ms cho auth endpoints, P95 < 500ms cho CRUD

### FR-006: K6 API chain test [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 sequential chain tests mô phỏng user journey thực tế (Login→Profile→Refresh, Register→Login→MFA→Profile, SSO→Profile→Permission, v.v.) khi cần đo end-to-end flow latency
- **Validation**: 8 chain scenarios defined. Each VU thực hiện toàn bộ chain sequentially. Chain success rate > 99%. Data correlation giữa steps (token from login → header for profile)

### FR-007: K6 mixed workload test [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 multi-scenario test mô phỏng production traffic mix (60% login/refresh, 20% profile, 10% registration, 5% admin, 5% MFA/SSO) khi cần đánh giá TPS capacity tổng thể
- **Validation**: Ramp pattern 0→100→500→1000 VUs over 10min. Target TPS ≥ 500 req/s sustained. P99 < 1000ms. Error rate < 0.1%

### FR-008: K6 stress test [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 stress test script đẩy hệ thống vượt quá capacity dự kiến (ramping-arrival-rate beyond target TPS) khi cần xác định breaking point
- **Validation**: Record TPS saturation point, latency degradation onset, error rate spike. Hệ thống phải recover sau khi giảm tải

### FR-009: K6 soak test [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 soak test script duy trì tải ổn định trong thời gian dài (1-4 giờ, constant-vus) khi cần phát hiện memory leak, connection leak, hoặc resource exhaustion
- **Validation**: JVM heap usage không tăng liên tục (no upward trend). No connection pool exhaustion. No thread leak. Error rate stable

### FR-010: JMH BCrypt benchmark [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp JMH benchmark cho BCrypt password hashing (strength 12, hiện tại cấu hình `bcrypt-strength: 12`) khi cần đo crypto overhead thật sự trên authentication flow
- **Validation**: Benchmark ops/sec. Warmup 5 iterations, measurement 5 iterations, fork 2. Output JSON

### FR-011: JMH JWT RS256 benchmark [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp JMH benchmark cho JWT RS256 signing và verification khi cần đo token generation throughput
- **Validation**: Separate benchmark cho sign và verify. Measure ops/sec cho mỗi operation. Key size matching production (RS256)

### FR-012: JMH Redis serialization comparison [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp JMH benchmark so sánh Redis serializers (GenericJackson2JsonRedisSerializer vs JdkSerializationRedisSerializer vs alternative) khi cần optimize cache throughput
- **Validation**: So sánh ops/sec và serialized size cho cùng payload. Payload phải representative (UserSession, Permission objects)

### FR-013: K6 → Prometheus remote write [URD]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải cấu hình K6 scripts output kết quả sang Prometheus via remote write (`--out experimental-prometheus-rw`) khi cần correlation giữa K6 metrics và application metrics trên cùng Grafana dashboard
- **Validation**: K6 metrics xuất hiện trong Prometheus targets. Grafana hiển thị K6 TPS overlay với application latency

### FR-014: Grafana performance dashboard [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp Grafana dashboard provisioning configs (JSON) hiển thị: TPS over time, latency percentiles (p50/p95/p99), error rate, CPU/memory, HikariCP pool status, Redis commands/sec, per-endpoint latency heatmap khi cần visual analysis
- **Validation**: Dashboard auto-provisioned khi Grafana start. Tối thiểu 4 rows: Request Performance, Resource Utilization, Infrastructure, Per-Endpoint Breakdown

### FR-015: Performance report template [URD]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp structured performance report template (Markdown) với sections: Executive Summary, Test Configuration, Baseline Metrics, Bottleneck Analysis (USE Method), Optimization Results, Recommendations khi cần document và so sánh kết quả giữa các lần test
- **Validation**: Template có before/after comparison section. Mỗi recommendation có expected impact. Mỗi bottleneck có evidence (metric + chart reference)

### FR-016: K6 shared SLA thresholds [URD]
- **Actor**: Developer, CI/CD Pipeline
- **Action**: Hệ thống phải cung cấp centralized SLA threshold config (`thresholds.js`) định nghĩa P95/P99/error rate targets cho từng loại endpoint (auth, cache, CRUD, chain) khi cần reuse across multiple K6 scripts
- **Validation**: Shared import. K6 exit code 99 khi violate. SLA values match business rules (BR-001 đến BR-010)

### FR-017: Bottleneck analysis automation [ENRICHED]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải document quy trình systematic bottleneck analysis theo USE Method (Utilization → Saturation → Errors) per resource (CPU, Memory, DB Pool, Redis, HTTP Server, Network) khi cần identify chính xác layer gây nghẽn
- **Validation**: Checklist format. Mỗi resource có: metric name, threshold, action if exceeded. Decision flowchart

### FR-018: Actuator metrics histogram config [ENRICHED]
- **Actor**: DevOps Engineer
- **Action**: Hệ thống phải cấu hình Micrometer percentile histograms cho `http.server.requests` (percentiles: 0.5, 0.95, 0.99) khi cần accurate latency distribution data cho Prometheus queries
- **Validation**: Prometheus query `histogram_quantile(0.95, ...)` trả về p95 chính xác. Application tag `auth-service` attached

### FR-019: Infrastructure baseline test script [ENRICHED]
- **Actor**: Performance Engineer
- **Action**: Hệ thống phải cung cấp K6 script đo infrastructure baseline (bare health endpoint max TPS, DB connection pool saturation test, Redis throughput test) TRƯỚC KHI chạy application-level tests khi cần xác định upper bound capacity
- **Validation**: Health check ≥ 5000 req/s. DB pool report active/pending/timeout counts. Redis report commands/sec

### FR-020: Virtual thread pinning detection [ENRICHED]
- **Actor**: Developer, DevOps Engineer
- **Action**: Hệ thống phải enable JVM flag `-Djdk.tracePinnedThreads=full` và document cách đọc output khi cần detect virtual thread pinning issues gây performance degradation
- **Validation**: JVM logs pinning events. Documentation giải thích cách fix (replace `synchronized` with `ReentrantLock`)

### FR-021: Tomcat access log configuration [ENRICHED]
- **Actor**: DevOps Engineer, Performance Engineer
- **Action**: Hệ thống phải enable Tomcat access log trong `application-core-observability.yml` với pattern `"%h %t \"%r\" %s %b %D"` (include response time ms) khi cần thu thập real traffic distribution data cho K6 mixed workload ratios
- **Validation**: Access log file tạo tại `logs/access.log`. Log chứa: source IP, timestamp, request method + path, status, body size, response time (ms). Có thể parse bằng awk/grep để tính endpoint distribution percentages. Rotate mỗi 7 ngày

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | Auth endpoint P95 latency | < 200ms |
| NFR-002 | Performance | Auth endpoint P99 latency | < 500ms |
| NFR-003 | Performance | CRUD endpoint P95 latency | < 500ms |
| NFR-004 | Performance | System-wide error rate | < 0.1% |
| NFR-005 | Performance | Target sustained TPS | ≥ 500 req/s |
| NFR-006 | Performance | API chain success rate | > 99% |
| NFR-007 | Performance | System P99 latency at target TPS | < 1000ms |
| NFR-008 | Stability | No memory leak over 30-min soak test | Heap stable |
| NFR-009 | Usability | K6 full suite time | < 15 minutes |
| NFR-010 | Usability | JMH benchmark suite time | < 10 minutes |
| NFR-011 | Performance | GC pause at p99 | < 50ms (ZGC) |

---

## 4. Deduplicated & Consolidated

### Archive DELTA Analysis

| Archive FR | New FR | Relationship |
|-----------|--------|-------------|
| Archive FR-001 (assertQueryCount) | — | RETAINED (đã implemented) |
| Archive FR-002 (WireMock) | — | RETAINED (đã implemented) |
| Archive FR-003 (K6 expand 500 VUs) | FR-005, FR-007 | SUPERSEDED — New FRs mở rộng từ 500 VUs lên multi-scenario per-endpoint + mixed workload |
| Archive FR-004 (Gradle K6 tasks) | FR-013 | SUPERSEDED — New FR thêm Prometheus remote write output |
| Archive FR-005 (JMH setup) | FR-010, FR-011, FR-012 | EXTENDED — New FRs thêm BCrypt, JWT, Redis serializer benchmarks |
| Archive FR-006 (CI/CD gate) | FR-016 | EXTENDED — New FR centralize SLA thresholds |
| Archive FR-007 (cache test) | — | RETAINED (đã implemented) |
| Archive FR-008 (N+1 detect) | — | RETAINED (đã implemented) |
| Archive FR-009 (datasource-proxy) | — | RETAINED (đã implemented) |
| Archive FR-010 (cache benchmark K6) | — | RETAINED (đã implemented) |
| — | FR-001 (Prometheus) | NEW — Observability stack |
| — | FR-002 (Grafana Docker) | NEW — Observability stack |
| — | FR-003 (HikariCP) | NEW — Infrastructure tuning |
| — | FR-004 (JVM flags) | NEW — Infrastructure tuning |
| — | FR-006 (API chain) | NEW — User journey testing |
| — | FR-008 (Stress test) | NEW — Breaking point detection |
| — | FR-009 (Soak test) | NEW — Memory leak detection |
| — | FR-014 (Grafana dashboard) | NEW — Visual analysis |
| — | FR-015 (Report template) | NEW — Structured reporting |

- Không phát hiện trùng lặp giữa new FRs
- Archive FRs đã implemented → không cần re-implement, chỉ extend

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-017**: Bottleneck analysis automation — Bổ sung vì URD nói cần "tìm điểm nghẽn" nhưng chưa có systematic methodology documented
- **FR-018**: Actuator metrics histogram — Bổ sung vì FR-001 (Prometheus endpoint) cần histogram config để P95/P99 queries chính xác
- **FR-019**: Infrastructure baseline test — Bổ sung vì UC-001 yêu cầu baseline TRƯỚC application tests nhưng chưa có script cụ thể
- **FR-020**: Virtual thread pinning — Bổ sung vì hệ thống dùng virtual threads nhưng chưa có monitoring cho pinning issues
- **FR-021**: Tomcat access log — Bổ sung từ brainstorm để thu thập real traffic distribution data cho K6 mixed workload ratios (OQ-002)

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| PostgreSQL (HikariCP) | Target for connection pool tuning & monitoring | Existing — cần explicit config |
| Redis (Lettuce) | Target for cache throughput benchmark | Existing — serializer comparison |
| Prometheus | Metrics collection server | NEW container (Docker Compose) |
| Grafana | Dashboard visualization | NEW container (Docker Compose) |
| K6 Docker (grafana/k6) | Load test execution engine | Existing — cần expand scenarios |
| JMH | JVM microbenchmark engine | Existing — cần thêm benchmarks |

## 6. Assumptions

- ⚠️ Assumption: `micrometer-registry-prometheus` dependency đã có sẵn qua `base-observability-starter` hoặc cần thêm explicit — Lý do: Spring Boot 3 autoconfigure Prometheus nếu dependency present
- ⚠️ Assumption: Docker network cho Prometheus scrape auth-service trên host — Lý do: cần `extra_hosts: host.docker.internal:host-gateway` hoặc `--network host`
- ⚠️ Assumption: ZGC tương thích với tất cả libraries hiện tại (Kotlin coroutines, virtual threads) — Lý do: ZGC is production-ready từ JDK 15+
- ⚠️ Assumption: K6 remote write output đã stable (không còn experimental) tại thời điểm implement — Lý do: K6 v0.49+ moved to stable

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-007: "production traffic mix" ratios (60/20/10/5/5) chưa validated từ real analytics |
| Đầy đủ (Completeness) | 20/25 | FR-005: "23 endpoints" — chưa xác nhận endpoint nào cần auth, data seed, rate limit disable |
| Nhất quán (Consistency) | 22/25 | FR-016: SLA thresholds cần align chính xác với NFR targets |
| Kiểm thử được (Testability) | 18/25 | FR-009: Soak test "1-4 hours" quá rộng — cần fixed duration; FR-017: "USE Method" cần concrete metric thresholds |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -3 | FR-007 | "60% login/refresh, 20% profile..." — ratios ước lượng, chưa có production analytics | Thu thập real traffic distribution từ access logs |
| 2 | Completeness | -5 | FR-005 | "23 endpoints" — chưa detail endpoint nào cần prerequisites (auth token, seeded data, disabled rate limit) | Tạo endpoint prerequisite matrix |
| 3 | Consistency | -3 | FR-016 | SLA values phải đồng bộ giữa thresholds.js và NFR table | Single source of truth cho SLA values |
| 4 | Testability | -4 | FR-009 | "1-4 giờ" — range quá rộng cho soak test | Fix tại 1h cho CI/CD, 4h cho manual |
| 5 | Testability | -3 | FR-017 | USE Method thresholds chưa concrete (CPU > 80%? DB pool > 90%?) | Define numeric thresholds per resource |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|------|
| 1 | Missing | 🟡 | HikariCP pool-size formula cần biết DB server specs (cores, spindles) — đã được resolve với 2 profiles + env vars | FR-003 | ✅ Resolved: 2 profiles documented |
| 2 | Risk | 🟡 | Soak test 4h cần stable test environment — Docker containers có thể restart | FR-009 | Use `restart: unless-stopped` + health checks |
| 3 | Ambiguity | 🟡 | K6 Prometheus remote write version stability — `experimental-prometheus-rw` naming | FR-013 | Check K6 version in Docker image tag |

> Không phát hiện 🔴 critical issues.

## 9. Open Questions

- ~~OQ-001~~: [RESOLVED via brainstorm] `micrometer-registry-prometheus` **ĐÃ CÓ** trong `base-observability-starter` (`build.gradle.kts:11`). Không cần thêm dependency. Chỉ cần config actuator expose `prometheus` endpoint.
- ~~OQ-002~~: [RESOLVED via brainstorm] Bổ sung FR-021 (Tomcat access log) + configurable K6 env vars cho traffic ratios. Tạm dùng 60/20/10/5/5, sau khi có access log data thực → điều chỉnh qua env `K6_MIX_AUTH`, `K6_MIX_PROFILE`, etc.
- ~~OQ-003~~: [RESOLVED via brainstorm] 2 profiles documented: **Profile 1** (4-core/8GB SSD) → `pool-size=10`; **Profile 2** (8-core/32GB SSD) → `pool-size=20`. Formula: `connections = (cores × 2) + spindle_count`. Config qua env variable `${DB_POOL_MAX:10}`.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Performance Testing Infrastructure — mở rộng testing framework
- Observability — Prometheus/Grafana integration
- Infrastructure Tuning — HikariCP, JVM, Redis

### 10.2 Flow Type
- Command — test scripts chạy một chiều, trả về kết quả Report (Pass/Fail)

### 10.3 Candidate Services
- `auth-service`: Dự án chính — chứa K6 scripts (`tests/load/`), JMH benchmarks (`src/jmh/kotlin/`), Docker compose (`compose.yaml`), Actuator config (`application-core-observability.yml`), DB config (`application-db.yml`). Evidence: `@RestController` × 22 controllers, `k6Run` Gradle task, JMH plugin `me.champeau.jmh`

### Detection Evidence
- Keyword: `K6` → Module: `tests/load/` → File: `tests/load/auth_flow.js`, `tests/load/cache_benchmark.js`, `tests/load/helpers.js`, `tests/load/profile_flow.js`
- Keyword: `JMH` → Module: `src/jmh/kotlin/` → File: `src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt`, `SerializationBenchmark.kt`
- Keyword: `k6Run` → Module: `build.gradle.kts` → File: `build.gradle.kts:91`
- Keyword: `me.champeau.jmh` → Module: `build.gradle.kts` → File: `build.gradle.kts:4`
- Keyword: `datasource-proxy` → Module: `build.gradle.kts` → File: `build.gradle.kts:70-71`
- Keyword: `exposure: health,info,metrics` → Module: `application-core-observability.yml` → File: `src/main/resources/application-core-observability.yml:5`
- Keyword: `datasource` → Module: `application-db.yml` → File: `src/main/resources/application-db.yml` (NO hikari config)
- Keyword: `compose` → Module: `compose.yaml` → File: `compose.yaml` (postgres + redis only, NO prometheus/grafana)

### 10.4 External Integrations
- PostgreSQL (Spring Data JPA, HikariCP) — existing, target for pool tuning
- Redis (base-cache-starter, Lettuce) — existing, target for serializer benchmark
- Prometheus (NEW) — metrics scraping server
- Grafana (NEW) — visualization dashboards
- K6 Docker (grafana/k6) — existing, needs expansion
- JMH (me.champeau.jmh) — existing, needs expansion

### 10.5 Required Modules
- `micrometer-registry-prometheus` — VERIFY (may exist in base-observability-starter)
- `compose-perf.yaml` — NEW file (Docker Compose extension)
- `tests/perf/` — NEW directory (expanded test structure)
- `tests/perf/prometheus.yml` — NEW file (Prometheus config)
- `tests/perf/grafana/provisioning/` — NEW directory (Grafana provisioning)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Performance Engineer | Chọn test level (baseline/single/chain/full/stress/soak) | — |
| 2 | — | — | K6/JMH execute test scenarios |
| 3 | — | — | Auth-service processes requests under load |
| 4 | — | — | Actuator exposes metrics → Prometheus scrapes |
| 5 | — | — | K6 reports metrics → Prometheus remote write |
| 6 | Performance Engineer | View Grafana dashboards | Grafana queries Prometheus |
| 7 | Performance Engineer | Analyze bottleneck (USE Method) | — |
| 8 | Performance Engineer | Apply optimization | Config/code changes |
| 9 | — | — | Re-run test → compare before/after |
| 10 | Performance Engineer | Generate report | Structured report template |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class/File | Status |
|-------|-------------|-------------|---------------------|--------|
| FR-001 | BA §2 UC-001, TS §6.3 | Observability | `application-core-observability.yml` | Pending |
| FR-002 | TS §6.1 | Docker | `compose-perf.yaml` (NEW) | Pending |
| FR-003 | WR §3.1 | Infrastructure | `application-db.yml` | Pending |
| FR-004 | WR §3.3 | Infrastructure | `gradle.properties` or Docker env | Pending |
| FR-005 | BA §2 UC-002, TS §5.1 | K6 Tests | `tests/perf/scenarios/single/*.js` (NEW) | Pending |
| FR-006 | BA §2 UC-003, TS §5.1 | K6 Tests | `tests/perf/scenarios/chains/*.js` (NEW) | Pending |
| FR-007 | BA §2 UC-004, TS §6.5 | K6 Tests | `tests/perf/scenarios/system/full_load.js` (NEW) | Pending |
| FR-008 | BA §2 UC-004 | K6 Tests | `tests/perf/scenarios/system/stress.js` (NEW) | Pending |
| FR-009 | BA §2 UC-004 | K6 Tests | `tests/perf/scenarios/system/soak.js` (NEW) | Pending |
| FR-010 | WR §3.5, TS §6.6 | JMH | `src/jmh/kotlin/.../BCryptBenchmark.kt` (NEW) | Pending |
| FR-011 | WR §3.5, TS §6.6 | JMH | `src/jmh/kotlin/.../JwtBenchmark.kt` (NEW) | Pending |
| FR-012 | WR §3.5, TS §6.6 | JMH | `src/jmh/kotlin/.../RedisCacheBenchmark.kt` (NEW) | Pending |
| FR-013 | WR §1 Iter.3, TS §2.1 | K6 Config | K6 scripts `--out` flag | Pending |
| FR-014 | TS §4.1 | Grafana | `tests/perf/grafana/provisioning/dashboards/` (NEW) | Pending |
| FR-015 | WR §5, BA §2 UC-005 | Documentation | `tests/perf/reports/report_template.md` (NEW) | Pending |
| FR-016 | TS §7 | K6 Config | `tests/perf/config/thresholds.js` (NEW) | Pending |
| FR-017 | WR §2.2 | Documentation | `tests/perf/reports/bottleneck_checklist.md` (NEW) | Pending |
| FR-018 | TS §6.3 | Observability | `application-core-observability.yml` | Pending |
| FR-019 | BA §2 UC-001 | K6 Tests | `tests/perf/scenarios/infra_baseline.js` (NEW) | Pending |
| FR-020 | WR §3.4 | Infrastructure | JVM flags + documentation | Pending |
| FR-021 | Brainstorm OQ-002 | Observability | `application-core-observability.yml` (access log) | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này là **pure testing infrastructure** — KHÔNG thay đổi production business logic
- Archive (2026-08-24) đã implement phần foundation tốt (datasource-proxy, WireMock, K6 basic, JMH basic)
- Scope mới mở rộng đáng kể: từ "test individual components" sang "full-system performance benchmarking with observability"
- Complexity: MEDIUM-HIGH — nhiều files mới nhưng phần lớn là config/scripts, không phải business logic
- Risk: LOW — toàn bộ changes là test/config scope, no production code modifications

### Related Features / Precedents
- Archive: `openspec/changes/archive/2026-08-24-tps-performance-testing/` — Foundation đã implemented (10 tasks, 4 phases)
- Archive tasks.md confirmed: datasource-proxy, WireMock, K6 expand, JMH setup đều DONE
- Research: `openspec/research/performance-benchmark-testing/` — 8 research documents (complete)

### Integration Notes
- **Prometheus** cần scrape auth-service trên host network — Docker `extra_hosts` hoặc `--network host`
- **K6 remote write** cần Prometheus remote write receiver endpoint — config `--web.enable-remote-write-receiver` flag
- **Grafana** auto-provision via `provisioning/` directory mount — no manual setup needed
- **HikariCP** config change là non-breaking — defaults được giữ qua env variables `${DB_POOL_MAX:10}`

### Suggested Approach
1. **Phase 1 — Observability Stack (FR-001, FR-002, FR-014, FR-018)**: Prometheus + Grafana Docker compose, Actuator prometheus endpoint, metrics histogram config, dashboard provisioning
2. **Phase 2 — Infrastructure Tuning (FR-003, FR-004, FR-020)**: HikariCP explicit config, JVM flags, virtual thread pinning detection
3. **Phase 3 — K6 Test Expansion (FR-005, FR-006, FR-007, FR-008, FR-009, FR-013, FR-016, FR-019)**: Single API tests, chain tests, mixed workload, stress, soak, Prometheus output, shared thresholds, infrastructure baseline
4. **Phase 4 — JMH Expansion (FR-010, FR-011, FR-012)**: BCrypt, JWT, Redis serializer benchmarks
5. **Phase 5 — Documentation (FR-015, FR-017)**: Performance report template, bottleneck analysis checklist

### Context from Confluence Images
N/A — research-based input, không có Confluence source
