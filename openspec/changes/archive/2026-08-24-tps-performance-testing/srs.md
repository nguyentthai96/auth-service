# SRS: tps-performance-testing

_Generated: 2026-08-27_
_Profile: Command | N/A (test infrastructure) | EXTEND_

---

## 1. System Context

- **Feature Name**: TPS Performance Testing — Comprehensive Testing Infrastructure
- **Domain**: Authentication & Authorization — performance testing infrastructure
- **Flow**: Command (test scripts execute one-way, return Pass/Fail report)
- **Services**: auth-service (primary), base-testing-starter (shared utility target)
- **Source**: Pre-OpenSpec (quality score 88/100) + Brainstorm (Approach 2 — 4-Phase Incremental selected)
- **Scope**: EXTEND existing testing infrastructure with 4 new testing layers. NO production code changes.

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Developer | Writes integration tests (assertQueryCount, WireMock, cache encryption), runs K6 load tests locally, runs JMH benchmarks, reviews results | JUnit 5 tests, Gradle tasks (k6Run, jmh), IDE |
| CI/CD Pipeline (System) | Automatically runs K6 load tests on merge/PR, enforces performance gate via thresholds, fails pipeline on regression | Gradle Exec tasks → K6 Docker → exit code propagation |
| DevOps Engineer | Configures Gradle tasks, Docker, JVM args for load testing environment | build.gradle.kts, Docker daemon, CI configuration |

---

## 3. Functional Requirements

### 3.1 Phase 1 — Foundation (datasource-proxy in base-testing-starter)

#### FR-001: Assert số lượng SQL queries [EXTEND]
- **Actor**: Developer
- **Precondition**: `base-testing-starter` is included as `testImplementation` dependency. DataSource is a JDBC-compliant datasource (H2, PostgreSQL).
- **Action**: Hệ thống phải cung cấp `assertQueryCount` DSL (Kotlin) và `@AssertQueryCount` annotation dùng datasource-proxy để đếm SQL queries (SELECT, INSERT, UPDATE, DELETE) trong integration tests khi developer test bất kỳ service method nào liên quan đến JPA entity.
- **DSL Usage**:
  ```kotlin
  assertQueryCount(select = 2, insert = 1) {
      authService.login(loginRequest)
  }
  ```
- **Annotation Usage**:
  ```kotlin
  @Test
  @AssertQueryCount(select = 2)
  fun `login should execute exactly 2 SELECT queries`() {
      authService.login(loginRequest)
  }
  ```
- **Validation**:
  - Query count MUST be thread-local (`ThreadLocal` reset before/after test)
  - Match → PASS
  - Mismatch → FAIL with message: `"Expected N SELECT queries, but got M. Queries executed: [...]"`
- **Affected Files**:
  - `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/DataSourceProxyConfig.kt` [NEW]
  - `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/QueryCountAssertions.kt` [NEW]
  - `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCount.kt` [NEW]
  - `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCountExtension.kt` [NEW]
- **NFR**: assertQueryCount overhead < 5ms per test (NFR-001)

#### FR-009: Datasource-proxy configuration [EXTEND]
- **Actor**: Developer
- **Precondition**: `net.ttddyy:datasource-proxy:1.10+` dependency available in test classpath.
- **Action**: Hệ thống phải cung cấp `DataSourceProxyConfig` (@TestConfiguration) trong test context để wrap DataSource bằng datasource-proxy khi chạy integration tests cần SQL query counting.
- **Implementation**:
  - `@TestConfiguration` class (NOT `BeanPostProcessor`) — explicit, predictable
  - Wraps existing DataSource via `ProxyDataSourceBuilder.create(dataSource).countQuery().build()`
  - `@Primary` bean to override default DataSource in test context
  - Only active when test class imports this configuration
- **Validation**:
  - datasource-proxy MUST be transparent — no impact on business logic
  - Only active in test profile
  - `QueryCountHolder.getGrandTotal()` returns accurate per-thread counts
- **Affected Files**:
  - `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/DataSourceProxyConfig.kt` [NEW]
  - `base-testing-starter/build.gradle.kts` [MODIFY] — add `api("net.ttddyy:datasource-proxy:1.10")`

### 3.2 Phase 2 — Integration Tests (auth-service)

#### FR-002: Giả lập latency HTTP services [EXTEND]
- **Actor**: Developer
- **Precondition**: `spring-cloud-contract-wiremock` dependency in testImplementation. `HttpClientConfig` creates `RestClient` beans for SSO and Captcha with configurable timeouts.
- **Action**: Hệ thống phải hỗ trợ WireMock setup (via `spring-cloud-contract-wiremock` + `@AutoConfigureWireMock`) để giả lập latency/fault cho external HTTP services (SsoProviderClient, CaptchaClient) khi developer cần test timeout handling và circuit breaker activation.
- **Test Scenarios**:
  1. **SSO Latency**: WireMock stub `/oauth2/token` with `withFixedDelay(15000)` (> 10s read timeout) → Assert `HttpSsoGateway.exchangeAuthorizationCode()` throws exception → Circuit breaker opens
  2. **SSO Circuit Breaker**: After 5+ failed calls (50% failure rate, sliding window 10) → circuit breaker opens → fallback throws `AuthException(SSO_TOKEN_INVALID)` with HTTP 503
  3. **Captcha Graceful Degradation**: WireMock fault on captcha endpoint → circuit breaker opens → fallback returns `true` (auto-pass)
  4. **Captcha Noop**: `provider=noop` → bypass WireMock entirely → auto-verify
- **Validation**:
  - WireMock stubs support `withFixedDelay()` for latency simulation
  - Port uses `@AutoConfigureWireMock(port = 0)` (dynamic port) to avoid conflict
  - SSO base URL injected via `app.security.sso.provider-base-url=http://localhost:${wiremock.server.port}`
  - Captcha base URL injected similarly via `app.security.captcha.verify-url`
- **Affected Files**:
  - `auth-service/src/test/kotlin/com/ntt/authservice/auth/adapter/out/http/WireMockExternalServiceTest.kt` [NEW]
  - `auth-service/build.gradle.kts` [MODIFY] — add `testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")`

#### FR-007: Test cache encryption correctness [EXTEND]
- **Actor**: Developer
- **Precondition**: Redis Testcontainer running. `StringRedisTemplate` available. `base-cache-starter` with AES-GCM encryption configured.
- **Action**: Hệ thống phải cung cấp integration tests verify Redis raw data format cho từng encryption mode:
  - `mode=NONE` → raw data phải là valid JSON (contains `"{"`)
  - `mode=FULL` → raw data KHÔNG được là valid JSON (encrypted binary/base64 with AES IV)
  - `mode=PARTIAL` → JSON structure preserved but field values are encrypted
- **Implementation**:
  - Use MockMvc to call API endpoints (login → creates cache entry)
  - Use `StringRedisTemplate` direct access to read raw Redis data
  - Fixed encryption key in `application-test.yml` for reproducibility: `dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3OA==`
- **Validation**:
  - Test MUST run with Redis Testcontainer (not embedded)
  - Results MUST be deterministic (fixed encryption key)
  - Each mode tested in separate test method for isolation
- **Affected Files**:
  - `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt` [MODIFY] — expand from basic set/get to NONE/FULL/PARTIAL mode verification

#### FR-008: Phát hiện N+1 queries tự động [EXTEND]
- **Actor**: Developer
- **Precondition**: `DataSourceProxyConfig` loaded in test context. JPA entities with lazy-loaded relationships exist.
- **Action**: Hệ thống phải sử dụng `assertQueryCount` để tự động phát hiện N+1 JPA query anti-pattern khi developer viết test cho bất kỳ lazy-loaded entity relationship nào.
- **Test Scenarios**:
  - Login flow: assert expected SELECT count for user + session queries
  - Token refresh: assert expected SELECT count for token lookup
  - If N+1 detected: test FAILS with detailed query list
- **Validation**:
  - Test FAIL khi expected query count < actual query count
  - Message: `"Expected N SELECT queries, but got M. Queries executed: [...]"`
- **Affected Files**:
  - `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/QueryCountIntegrationTest.kt` [NEW]

### 3.3 Phase 3 — K6 Expansion

#### FR-003: E2E load test K6 mở rộng [EXTEND]
- **Actor**: Developer, CI/CD Pipeline
- **Precondition**: K6 Docker image (`grafana/k6`) available. Auth-service running on `localhost:8080`.
- **Action**: Hệ thống phải mở rộng K6 scripts hiện có với multi-scenario:
  - **Scenario 1: `auth_login`** (ramping-vus): Stages 0→100 (10s), 100→500 (20s), 500→0 (10s). Exec: `loginFlow()` → POST `/api/v1/auth/login`
  - **Scenario 2: `profile_get`** (constant-arrival-rate): 100 req/s, preAllocatedVUs: 200, duration: 30s. Exec: `profileFlow()` → GET `/api/v1/profiles/me` (with token from login)
- **URL Standardization**: `http://localhost:8080` (not `host.docker.internal`) — consistent with `--network host`
- **Token Management**: `helpers.js` exports `loginAndGetToken()` function for dynamic token acquisition
- **Validation**:
  - P95 latency < 200ms for login (NFR-003)
  - P95 latency < 150ms for profile (NFR-008)
  - Error rate < 1% (NFR-004)
  - Results reproducible (≤ 5% variance)
- **Affected Files**:
  - `auth-service/tests/load/auth_flow.js` [MODIFY] — expanded multi-scenario
  - `auth-service/tests/load/profile_flow.js` [MODIFY] — constant-arrival-rate + dynamic token
  - `auth-service/tests/load/helpers.js` [NEW] — shared utility module

#### FR-004: Tích hợp K6 vào Gradle [EXTEND]
- **Actor**: Developer, CI/CD Pipeline
- **Precondition**: Docker daemon running. `grafana/k6` image available.
- **Action**: Hệ thống phải cung cấp Gradle Exec tasks:
  - `k6Run` [EXISTING] — runs `auth_flow.js`
  - `k6ProfileRun` [EXISTING] — runs `profile_flow.js`
  - `k6CacheBenchmark` [NEW] — runs `cache_benchmark.js` with `CACHE_ENCRYPTION_MODE` env var
- **Validation**:
  - Tasks MUST mount correct volume, use `--network host`, propagate exit code
  - `k6CacheBenchmark` accepts `CACHE_ENCRYPTION_MODE` as Gradle project property
- **Affected Files**:
  - `auth-service/build.gradle.kts` [MODIFY] — add `k6CacheBenchmark` task

#### FR-010: Cache benchmark K6 script [EXTEND]
- **Actor**: Developer
- **Precondition**: Auth-service running with specified `CACHE_ENCRYPTION_MODE`.
- **Action**: Hệ thống phải cung cấp K6 script `cache_benchmark.js` để benchmark TPS impact của các cache encryption modes (NONE/FULL/PARTIAL).
- **Script Design**:
  - `constant-arrival-rate` executor: 100 req/s, preAllocatedVUs: 200, duration: 30s
  - Environment variable: `CACHE_ENCRYPTION_MODE` (default: `NONE`)
  - Executes: login → profile get sequence to exercise cache read/write
  - Tags results with encryption mode for comparison
- **Validation**:
  - Script supports `CACHE_ENCRYPTION_MODE` env var
  - Results comparable across modes (same request pattern)
- **Affected Files**:
  - `auth-service/tests/load/cache_benchmark.js` [NEW]

### 3.4 Phase 4 — JMH + CI/CD Gate

#### FR-005: JMH microbenchmark setup [EXTEND]
- **Actor**: Developer
- **Precondition**: JVM 25. Gradle with `me.champeau.jmh` plugin.
- **Action**: Hệ thống phải tích hợp JMH Gradle plugin (`me.champeau.jmh` v0.7.2) với source set `src/jmh/kotlin/` khi developer cần benchmark:
  - **EncryptionBenchmark**: AES-GCM encrypt/decrypt throughput (ops/sec)
  - **SerializationBenchmark**: Jackson `JsonMapper.writeValueAsBytes()` / `readValue()` throughput (ops/sec)
- **JMH Configuration**:
  - Fork: 2 JVM instances
  - Warmup: 5 iterations
  - Measurement: 5 iterations
  - Output: JSON format → `build/results/jmh/results.json`
- **Important**: JMH benchmarks run WITHOUT Spring context (isolated JVM forks). Dependencies instantiated manually.
- **Validation**:
  - `./gradlew jmh` produces `build/results/jmh/results.json`
  - Results show ops/sec for each benchmark method
- **Affected Files**:
  - `auth-service/build.gradle.kts` [MODIFY] — add JMH plugin + jmh dependencies + config block
  - `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt` [NEW]
  - `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt` [NEW]

#### FR-006: CI/CD performance gate [EXTEND]
- **Actor**: CI/CD Pipeline
- **Precondition**: K6 scripts have `thresholds` configured. CI/CD pipeline runs K6 via Gradle Exec task.
- **Action**: Hệ thống phải cấu hình K6 thresholds và fail pipeline khi performance metrics violate ngưỡng:
  - `http_req_duration{scenario:auth_login}`: p(95) < 200ms
  - `http_req_duration{scenario:profile_get}`: p(95) < 150ms
  - `http_req_failed`: rate < 0.01 (1%)
- **Exit Code Handling**:
  - K6 threshold violation → exit code 99
  - Gradle `Exec` task propagates exit code → `BUILD FAILED`
  - CI/CD pipeline stops on non-zero exit
- **Validation**:
  - K6 exit code 99 → Gradle BUILD FAILED → pipeline stops
  - K6 exit code 0 → pipeline continues
- **Affected Files**:
  - `auth-service/tests/load/auth_flow.js` [MODIFY] — thresholds already embedded in FR-003 script changes

---

## 4. Non-Functional Requirements

| NFR-ID | Category | Requirement | Target | Validation Method |
|--------|----------|-------------|--------|-------------------|
| NFR-001 | Performance | assertQueryCount overhead per test | < 5ms | JUnit timing comparison |
| NFR-002 | Compatibility | WireMock + Spring Boot 4 + JVM 25 | Compatible | Integration test execution |
| NFR-003 | Performance | K6 threshold: P95 latency (login) | < 200ms | K6 threshold enforcement |
| NFR-004 | Performance | K6 threshold: Error rate | < 1% | K6 threshold enforcement |
| NFR-005 | Reliability | Cache test reproducibility | Deterministic (fixed encryption key) | Repeated test runs |
| NFR-006 | Usability | K6 full suite execution time | < 2 minutes | Timed execution |
| NFR-007 | Usability | Integration test suite time | < 2 minutes | Timed execution |
| NFR-008 | Performance | K6 threshold: P95 latency (profile) | < 150ms | K6 threshold enforcement |

---

## 5. External Integrations

| System | Protocol | Purpose | Status |
|--------|----------|---------|--------|
| PostgreSQL | JDBC (Spring Data JPA) | Target for datasource-proxy SQL counting | EXISTING |
| Redis (Testcontainer) | Spring Data Redis | Target for cache encryption correctness tests | EXISTING |
| K6 Docker Container (grafana/k6) | Docker Exec | E2E load test execution engine | EXISTING — extend |
| WireMock | HTTP (spring-cloud-contract) | Mock SsoProviderClient, CaptchaClient | NEW |
| JMH (me.champeau.jmh) | Gradle plugin | Microbenchmark engine | NEW |
| datasource-proxy | JDBC wrapper | SQL query counting for integration tests | NEW |

---

## 6. Assumptions

- ⚠️ Assumption: `base-testing-starter` cho phép thêm dependency `datasource-proxy` mà không conflict — Lý do: additive change, test-scoped only
- ⚠️ Assumption: K6 Docker container (`grafana/k6`) hỗ trợ threshold-based exit codes (exit 99) — Lý do: proven in existing k6Run task, K6 documentation confirms
- ⚠️ Assumption: JMH Gradle plugin `me.champeau.jmh` v0.7.2 tương thích JVM 25 — Lý do: JMH tracks latest JVM releases
- ⚠️ Assumption: Redis Testcontainers cho phép connect trực tiếp via `StringRedisTemplate` — Lý do: existing `CacheEncryptionIntegrationTest` already does this
- ⚠️ Assumption: `@DirtiesContext(AFTER_EACH_TEST_METHOD)` from `AbstractIntegrationTest` does not conflict with datasource-proxy wrapper — Lý do: proxy wraps DataSource, context refresh re-wraps

---

## 7. FR Traceability

| FR-ID | URD Source | Phase | Status |
|-------|-----------|-------|--------|
| FR-001 | BA UC-001, TS 9.1 | Phase 1 | Pending |
| FR-002 | BA UC-002, TS 9.3 | Phase 2 | Pending |
| FR-003 | BA UC-003, TS 9.4 | Phase 3 | Pending |
| FR-004 | BA UC-003, TS 9.4 | Phase 3 | Pending |
| FR-005 | BA UC-005, TS 9.5 | Phase 4 | Pending |
| FR-006 | [ENRICHED] TS 9.4 | Phase 4 | Pending |
| FR-007 | BA UC-004, TS 9.2 | Phase 2 | Pending |
| FR-008 | BA UC-001 (use case) | Phase 2 | Pending |
| FR-009 | [ENRICHED] TS 9.1 | Phase 1 | Pending |
| FR-010 | [ENRICHED] TS 9.4 | Phase 3 | Pending |

**Coverage**: 10/10 FRs covered.
