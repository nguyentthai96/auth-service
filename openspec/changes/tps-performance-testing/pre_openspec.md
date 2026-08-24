# Pre-OpenSpec: tps-performance-testing

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (research artifacts — business_analysis.md + technical_spec.md)
> **Classification Evidence**: keyword `K6`, `load test`, `performance` → module `tests/load/`, `build.gradle.kts` (k6Run, k6ProfileRun tasks) → file `tests/load/auth_flow.js`, `tests/load/profile_flow.js`
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Thiết lập framework kiểm thử hiệu năng toàn diện cho auth-service bao gồm: (1) assertQueryCount DSL dùng datasource-proxy để phát hiện N+1 queries tự động trong integration tests, (2) mở rộng K6 E2E load testing scripts từ 50 VUs lên 500 VUs với multi-scenario và CI/CD thresholds, (3) tích hợp WireMock giả lập latency/fault cho external HTTP services (SSO, Captcha), (4) JMH microbenchmark cho encryption/serialization overhead, và (5) integration tests kiểm tra tính đúng đắn của cache encryption modes (NONE/FULL/PARTIAL).

| Metric | Giá trị |
|--------|---------|
| Số FR | 10 (URD: 7, Enriched: 3) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Developer**: Viết integration tests (assertQueryCount, WireMock, cache encryption), chạy K6 load tests local, chạy JMH benchmarks, review kết quả
- **CI/CD Pipeline (System)**: Tự động chạy K6 load tests mỗi merge/PR, enforce performance gate qua thresholds, fail pipeline khi performance regression
- **DevOps Engineer**: Cấu hình Gradle tasks, Docker, JVM args cho load testing environment

## 2. Functional Requirements

### FR-001: Assert số lượng SQL queries [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `assertQueryCount` DSL (Kotlin) và `@AssertQueryCount` annotation dùng datasource-proxy để đếm SQL queries (SELECT, INSERT, UPDATE, DELETE) trong integration tests khi developer test bất kỳ service method nào liên quan đến JPA entity
- **Validation**: Query count phải thread-local (ThreadLocal reset before/after). Kết quả phải chính xác: expected vs actual match → PASS, mismatch → FAIL với message chi tiết

### FR-002: Giả lập latency HTTP services [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải hỗ trợ WireMock setup (via `spring-cloud-contract-wiremock` và `@AutoConfigureWireMock`) để giả lập latency/fault cho external HTTP services (SsoProviderClient, CaptchaClient) khi developer cần test timeout handling và circuit breaker activation
- **Validation**: WireMock stubs phải hỗ trợ `withFixedDelay()` cho latency simulation và fault injection. Port phải dùng `dynamicPort()` để tránh conflict khi chạy parallel

### FR-003: E2E load test K6 mở rộng [IDEA]
- **Actor**: Developer, CI/CD Pipeline
- **Action**: Hệ thống phải mở rộng K6 scripts hiện có (`auth_flow.js`) với multi-scenario: `auth_login` (ramping-vus, 0→500 VUs, 30s) và `profile_get` (constant-arrival-rate, 100 req/s) khi cần đo TPS baseline cho auth endpoints
- **Validation**: P95 latency < 200ms cho login, < 150ms cho profile. Error rate < 1%. Kết quả phải reproducible (≤ 5% variance)

### FR-004: Tích hợp K6 vào Gradle [IDEA]
- **Actor**: Developer, CI/CD Pipeline
- **Action**: Hệ thống phải cung cấp Gradle Exec tasks (`k6Run`, `k6ProfileRun`, `k6CacheBenchmark`) chạy K6 via Docker container (`grafana/k6`) với `--network host` khi developer hoặc CI/CD trigger load tests
- **Validation**: Gradle tasks phải truyền đúng script path, mount volume, và propagate exit code từ K6 container

### FR-005: JMH microbenchmark setup [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải tích hợp JMH Gradle plugin (`me.champeau.jmh` v0.7.2) với source set `src/jmh/kotlin/` khi developer cần benchmark AES-GCM encryption throughput và Jackson serialization overhead
- **Validation**: JMH phải fork JVM, chạy warmup + measurement iterations, output results dạng JSON (`build/results/jmh/results.json`)

### FR-006: CI/CD performance gate [ENRICHED]
- **Actor**: CI/CD Pipeline
- **Action**: Hệ thống phải cấu hình K6 thresholds (P95 < 200ms, error rate < 1%) và fail CI/CD pipeline (exit code 99) khi performance metrics vi phạm ngưỡng khi có merge request hoặc push to main branch
- **Validation**: K6 exit code 99 → Gradle BUILD FAILED → pipeline stops. K6 exit code 0 → pipeline continues

### FR-007: Test cache encryption correctness [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp integration tests verify Redis raw data format cho từng encryption mode: mode=NONE → raw data phải là valid JSON, mode=FULL → raw data KHÔNG được là valid JSON, mode=PARTIAL → JSON với encrypted field values khi developer thay đổi cache config hoặc encryption logic
- **Validation**: Dùng `StringRedisTemplate` direct access để đọc raw data. Test phải chạy với Redis Testcontainer và fixed encryption key cho reproducibility

### FR-008: Phát hiện N+1 queries tự động [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải sử dụng `assertQueryCount` để tự động phát hiện N+1 JPA query anti-pattern khi developer viết test cho bất kỳ lazy-loaded entity relationship nào
- **Validation**: Test FAIL khi expected query count < actual query count với message: "Expected N SELECT queries, but got M. Queries executed: [...]"

### FR-009: Datasource-proxy configuration [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `DataSourceProxyConfig` (@TestConfiguration) trong test context để wrap DataSource bằng datasource-proxy khi chạy integration tests cần SQL query counting
- **Validation**: datasource-proxy phải transparent — không ảnh hưởng đến business logic. Chỉ active trong test profile

### FR-010: Cache benchmark K6 script [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp K6 script `cache_benchmark.js` để benchmark TPS impact của các cache encryption modes (NONE/FULL/PARTIAL) khi cần so sánh hiệu năng giữa các cấu hình
- **Validation**: Script phải hỗ trợ environment variable `CACHE_ENCRYPTION_MODE` để chọn mode. Results phải comparable across modes

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | assertQueryCount overhead | < 5ms per test |
| NFR-002 | Compatibility | WireMock + Spring Boot 3 + JVM 25 | Compatible |
| NFR-003 | Performance | K6 threshold: P95 latency (login) | < 200ms |
| NFR-004 | Performance | K6 threshold: Error rate | < 1% |
| NFR-005 | Reliability | Cache test reproducibility | Deterministic results (fixed encryption key) |
| NFR-006 | Usability | K6 full suite time | < 2 minutes |
| NFR-007 | Usability | Integration test suite time | < 2 minutes |
| NFR-008 | Performance | K6 threshold: P95 latency (profile) | < 150ms |

---

## 4. Deduplicated & Consolidated

- FR-001 và FR-008 liên quan (assertQueryCount và N+1 detection) — giữ tách vì FR-001 là infrastructure DSL, FR-008 là use case cụ thể
- FR-003 và FR-004 liên quan (K6 scripts và Gradle tasks) — giữ tách vì khác scope: scripts vs build system
- Không phát hiện trùng lặp thực sự

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-006**: CI/CD performance gate — bổ sung vì URD chỉ nói K6 thresholds nhưng chưa rõ mechanism fail CI/CD. Cần explicit exit code handling
- **FR-009**: Datasource-proxy configuration — bổ sung vì FR-001 cần infrastructure config (DataSourceProxyConfig) trước khi sử dụng assertQueryCount
- **FR-010**: Cache benchmark K6 script — bổ sung vì URD nói cần benchmark cache encryption modes nhưng chưa có script cụ thể

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| PostgreSQL | Target cho datasource-proxy SQL query counting | Via Spring Data JPA, existing |
| Redis (Testcontainer) | Target cho cache encryption correctness tests | Direct raw access via StringRedisTemplate |
| K6 Docker Container (grafana/k6) | Execution engine cho E2E load tests | Existing — cần mở rộng scenarios |
| WireMock | Mock SsoProviderClient, CaptchaClient | New dependency: spring-cloud-contract-wiremock |
| JMH | Microbenchmark engine cho encryption/serialization | New plugin: me.champeau.jmh |

## 6. Assumptions

- ⚠️ Assumption: `base-testing-starter` cho phép thêm dependency `datasource-proxy` mà không conflict — Lý do: module là shared test utility library, đã có `base-testing-starter` trong testImplementation
- ⚠️ Assumption: K6 Docker container (`grafana/k6`) hỗ trợ threshold-based exit codes (exit 99 khi fail) — Lý do: đã proven trong existing k6Run task, K6 documentation confirms
- ⚠️ Assumption: JMH Gradle plugin `me.champeau.jmh` tương thích JVM 25 — Lý do: JMH tracks latest JVM releases, JVM 21+ tested
- ⚠️ Assumption: Redis Testcontainers cho phép connect trực tiếp 2/token`, `/userinfo`) hay CaptchaClient (`/turnstile/v0/siteverify`)?
- OQ-002: Encryption key cho Redis Testcontainer — dùng fixed key cho reproducibility (default recommendation)

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Authentication & Authorization — performance testing infrastructure
- Testing infrastructure (no production code changes)

### 10.2 Flow Type
- Command — test scripts chạy một chiều, trả về kết quả Report (Pass/Fail)

### 10.3 Candidate Services
- `auth-service`: Dự án chính — chứa K6 scripts (`tests/load/`), integration tests (`src/test/`), Gradle tasks (`build.gradle.kts`), và JMH benchmarks (`src/jmh/kotlin/`). Evidence: keyword `K6`, `load test` → `tests/load/auth_flow.js`, `tests/load/profile_flow.js`; `k6Run`, `k6ProfileRun` tasks trong `build.gradle.kts`
- `base-testing-starter`: Shared test utility library — target cho `assertQueryCount` DSL và `DataSourceProxyConfig`. Evidence: `testImplementation("com.ntt:base-testing-starter")` trong `build.gradle.kts`

### Detection Evidence
- Keyword: `K6`, `load test` → Module: `tests/load/` → File: `tests/load/auth_flow.js` (50 VUs, 10s, POST /api/v1/auth/login)
- Keyword: `k6Run`, `k6ProfileRun` → Module: `build.gradle.kts` → File: `build.gradle.kts` (lines 87-97)
- Keyword: `CacheEncryption` → Module: `src/test/kotlin/.../auth/application/` → File: `CacheEncryptionIntegrationTest.kt`
- Keyword: `base-testing-starter` → Module: dependencies → File: `build.gradle.kts` (testImplementation)
- Keyword: `WireMock target` → Module: `auth/adapter/out/http/` → File: `SsoProviderClient.kt`, `CaptchaClient.kt` (declarative @HttpExchange clients)
- Keyword: `HttpClientConfig` → Module: `shared/config/` → File: `HttpClientConfig.kt` (RestClient + circuit breaker config)

### 10.4 External Integrations
- PostgreSQL (Spring Data JPA) — existing, target for datasource-proxy wrapping
- Redis (base-cache-starter, Caffeine L1 + Redis L2) — existing, target for cache encryption tests
- Kafka (spring-kafka) — existing, event consumers (not primary test target)
- SSO Provider (SsoProviderClient, @HttpExchange) — existing, target for WireMock simulation
- Captcha Provider (CaptchaClient, @HttpExchange) — existing, target for WireMock simulation
- K6 Docker Container (grafana/k6) — existing, needs expansion

### 10.5 Required Modules
- `datasource-proxy` (net.ttddyy:datasource-proxy:1.10+) — NEW dependency (testImplementation)
- `spring-cloud-contract-wiremock` — NEW dependency (testImplementation)
- `me.champeau.jmh` Gradle plugin (v0.7.2) — NEW plugin
- `jmh-core` + `jmh-generator-annprocess` (1.37+) — NEW dependency (jmh configuration)
- `base-testing-starter` — EXISTING (target for assertQueryCount utility)
- `base-cache-starter` — EXISTING (target for cache encryption tests)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer / CI/CD | Trigger Gradle task (k6Run, test, jmh) | Gradle starts execution environment |
| 2 | - | - | System executes tests/benchmarks (K6 Docker, JUnit, JMH fork) |
| 3 | - | - | System collects metrics (TPS, P95, query counts, ops/sec) |
| 4 | - | - | System evaluates thresholds/assertions |
| 5 | - | - | System outputs result: PASS (exit 0) hoặc FAIL (exit 99/non-zero) |
| 6 | Developer / CI/CD | Review results | Pipeline continues (pass) hoặc stops (fail) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | BA UC-001: Assert SQL Query Count | TS 9.1 #1-4 | `DataSourceProxyConfig`, `QueryCountAssertions`, `AssertQueryCount`, `AssertQueryCountExtension` (base-testing-starter — NEW) | Pending |
| FR-002 | BA UC-002: Simulatessor pattern). KHÔNG ảnh hưởng production vì chỉ active trong test profile
- **WireMock**: Dùng `@AutoConfigureWireMock(port = 0)` + dynamic port injection vào `SsoProviderClient`/`CaptchaClient` base URL
- **K6**: Docker-based execution, `--network host` cho access localhost. K6 scripts dùng ES6 module syntax
- **JMH**: Separate source set `src/jmh/kotlin/`, plugin manages compilation và execution. Fork 2 JVMs, 5 warmup + 5 measurement iterations
- **Testcontainers**: Đã có Redis Testcontainer support (CacheEncryptionIntegrationTest). Cần thêm PostgreSQL Testcontainer cho datasource-proxy tests nếu H2 không đủ

### Suggested Approach
1. **Phase 1 — Foundation**: Thêm datasource-proxy dependency, tạo `DataSourceProxyConfig`, `QueryCountAssertions` DSL, `@AssertQueryCount` annotation + JUnit 5 Extension trong `base-testing-starter`
2. **Phase 2 — Integration Tests**: Tạo assertQueryCount tests cho auth-service (login, token refresh), WireMock tests cho SSO/Captcha latency, mở rộng CacheEncryptionIntegrationTest cho NONE/FULL/PARTIAL modes
3. **Phase 3 — K6 Expansion**: Mở rộng auth_flow.js (500 VUs, multi-scenario), tạo cache_benchmark.js, thêm k6CacheBenchmark Gradle task
4. **Phase 4 — JMH & CI/CD**: Tích hợp JMH plugin, tạo EncryptionBenchmark + SerializationBenchmark, verify CI/CD performance gate

### Context from Confluence Images
N/A — research-based input, không có Confluence source
