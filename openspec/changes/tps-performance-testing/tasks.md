<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Command" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: tps-performance-testing

_Generated: 2026-08-27_
_Profile: Command | N/A | EXTEND_
_Direction: 4-Phase Hybrid (from brainstorm — Approach 2)_
_FRs: 10/10 covered_

---

## Changes

[EXTEND] Thiết lập framework kiểm thử hiệu năng toàn diện cho auth-service qua 4 phases, triển khai 10 FRs. Toàn bộ test-scoped — **không thay đổi production code**.

**Phase 1 — Foundation (base-testing-starter, 4 new files, 1 modified)**: Add `datasource-proxy` dependency. Create `DataSourceProxyConfig` (@TestConfiguration wrapping DataSource with `ProxyDataSourceBuilder`), `QueryCountAssertions` (Kotlin DSL `assertQueryCount(select=N) { block }`), `@AssertQueryCount` annotation, `AssertQueryCountExtension` (JUnit 5 BeforeEach/AfterEach).

**Phase 2 — Integration Tests (auth-service, 2 new files, 2 modified)**: Add `spring-cloud-contract-wiremock` + `datasource-proxy` testImplementation. Create `WireMockExternalServiceTest` (SSO/Captcha latency/fault simulation, circuit breaker verification via `@AutoConfigureWireMock(port=0)`) and `QueryCountIntegrationTest` (N+1 detection). Enhance `CacheEncryptionIntegrationTest` (NONE/FULL/PARTIAL raw Redis data format verification).

**Phase 3 — K6 Expansion (2 new scripts, 3 modified files)**: Create `helpers.js` (shared `loginAndGetToken()`), `cache_benchmark.js` (TPS benchmark across encryption modes). Rewrite `auth_flow.js` (50→500 VUs, ramping-vus + constant-arrival-rate dual scenario, `localhost:8080`). Rewrite `profile_flow.js` (constant-arrival-rate, dynamic token). Add `k6CacheBenchmark` Gradle Exec task.

**Phase 4 — JMH + CI/CD (2 new benchmark files, 1 modified build)**: Add `me.champeau.jmh` v0.7.2 plugin + `jmh-core` 1.37 dependencies. Create `EncryptionBenchmark` (AES-GCM throughput ops/sec) and `SerializationBenchmark` (Jackson 3.x ops/sec) in `src/jmh/kotlin/`. JMH config: fork 2, warmup 5, measurement 5, JSON output. CI/CD gate enforced via K6 thresholds (P95<200ms login, P95<150ms profile, error<1%) → exit code 99 → BUILD FAILED.

**Total**: 10 new files, 7 modified files, 17 tasks across 4 phases. Zero production code changes.

---

## Task Summary

| # | Task | Action | File | Phase | FRs |
|---|------|--------|------|-------|-----|
| 1 | Add datasource-proxy to base-testing-starter | MODIFY | `base-testing-starter/build.gradle.kts` | 1 | FR-009 |
| 2 | Create DataSourceProxyConfig | NEW | `assertion/DataSourceProxyConfig.kt` | 1 | FR-009 |
| 3 | Create QueryCountAssertions DSL | NEW | `assertion/QueryCountAssertions.kt` | 1 | FR-001 |
| 4 | Create @AssertQueryCount annotation | NEW | `assertion/AssertQueryCount.kt` | 1 | FR-001 |
| 5 | Create AssertQueryCountExtension | NEW | `assertion/AssertQueryCountExtension.kt` | 1 | FR-001, FR-008 |
| 6 | Add WireMock + datasource-proxy deps to auth-service | MODIFY | `auth-service/build.gradle.kts` | 2 | FR-002, FR-009 |
| 7 | Create WireMockExternalServiceTest | NEW | `WireMockExternalServiceTest.kt` | 2 | FR-002 |
| 8 | Enhance CacheEncryptionIntegrationTest | MODIFY | `CacheEncryptionIntegrationTest.kt` | 2 | FR-007 |
| 9 | Create QueryCountIntegrationTest | NEW | `QueryCountIntegrationTest.kt` | 2 | FR-008 |
| 10 | Create helpers.js shared utility | NEW | `tests/load/helpers.js` | 3 | FR-003, FR-010 |
| 11 | Expand auth_flow.js to multi-scenario | MODIFY | `tests/load/auth_flow.js` | 3 | FR-003, FR-006 |
| 12 | Expand profile_flow.js | MODIFY | `tests/load/profile_flow.js` | 3 | FR-003 |
| 13 | Create cache_benchmark.js | NEW | `tests/load/cache_benchmark.js` | 3 | FR-010 |
| 14 | Add k6CacheBenchmark Gradle task | MODIFY | `auth-service/build.gradle.kts` | 3 | FR-004 |
| 15 | Add JMH plugin and configuration | MODIFY | `auth-service/build.gradle.kts` | 4 | FR-005 |
| 16 | Create EncryptionBenchmark | NEW | `benchmark/EncryptionBenchmark.kt` | 4 | FR-005 |
| 17 | Create SerializationBenchmark | NEW | `benchmark/SerializationBenchmark.kt` | 4 | FR-005 |

---

## Phase 1: Foundation — datasource-proxy in base-testing-starter

- [ ] **Task 1: Add datasource-proxy dependency to base-testing-starter**
  - File: `base-testing-starter/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-009 — Datasource-proxy configuration
  - Pattern: Add `api("net.ttddyy:datasource-proxy:1.10")` in dependencies block. Use `api` scope for transitive exposure to consuming services.
  - Dependencies: None (first task)

- [ ] **Task 2: Create DataSourceProxyConfig**
  - File: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/DataSourceProxyConfig.kt` | Action: [NEW]
  - FR: FR-009 — Datasource-proxy configuration
  - Pattern: `@TestConfiguration` class with `@Bean @Primary fun dataSourceProxy(originalDataSource: DataSource): DataSource` wrapping via `ProxyDataSourceBuilder.create(originalDataSource).countQuery().build()`. Only active when explicitly imported by test class.
  - Dependencies: datasource-proxy library (Task 1)

- [ ] **Task 3: Create QueryCountAssertions DSL**
  - File: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/QueryCountAssertions.kt` | Action: [NEW]
  - FR: FR-001 — Assert số lượng SQL queries
  - Pattern: Kotlin `object` with `inline fun assertQueryCount(select: Int? = null, insert: Int? = null, update: Int? = null, delete: Int? = null, block: () -> Unit)`. Uses `QueryCountHolder.clear()` before and after block. Asserts each non-null query type count. On mismatch: `fail("Expected $expected SELECT queries, but got $actual. Total queries: S=... I=... U=... D=...")`.
  - Dependencies: DataSourceProxyConfig (Task 2)

- [ ] **Task 4: Create @AssertQueryCount annotation**
  - File: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCount.kt` | Action: [NEW]
  - FR: FR-001 — Assert số lượng SQL queries
  - Pattern: `@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) @ExtendWith(AssertQueryCountExtension::class) annotation class AssertQueryCount(val select: Int = -1, val insert: Int = -1, val update: Int = -1, val delete: Int = -1)`. Value `-1` = don't assert.
  - Dependencies: AssertQueryCountExtension (Task 5)

- [ ] **Task 5: Create AssertQueryCountExtension**
  - File: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCountExtension.kt` | Action: [NEW]
  - FR: FR-001, FR-008 — Assert queries + N+1 detection
  - Pattern: Implements JUnit 5 `BeforeEachCallback` + `AfterEachCallback`. `beforeEach()`: `QueryCountHolder.clear()`. `afterEach()`: read `@AssertQueryCount` annotation from test method, compare expected vs actual via `QueryCountHolder.getGrandTotal()`, `QueryCountHolder.clear()`.
  - Dependencies: @AssertQueryCount annotation (Task 4), datasource-proxy (Task 1)

---

## Phase 2: Integration Tests — auth-service

- [ ] **Task 6: Add WireMock + datasource-proxy dependencies to auth-service**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-002, FR-009
  - Pattern: Add in TESTING section: `testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")` and `testImplementation("net.ttddyy:datasource-proxy:1.10")`. Note: datasource-proxy is also in base-testing-starter via `api` scope, but explicit declaration ensures version alignment.
  - Dependencies: None (build config)

- [ ] **Task 7: Create WireMockExternalServiceTest**
  - File: `auth-service/src/test/kotlin/com/ntt/authservice/auth/adapter/out/http/WireMockExternalServiceTest.kt` | Action: [NEW]
  - FR: FR-002 — Giả lập latency HTTP services
  - Pattern: `@SpringBootTest @AutoConfigureWireMock(port = 0) @ActiveProfiles("test")`. Override `app.security.sso.provider-base-url` and `app.security.captcha.verify-url` with `http://localhost:${wiremock.server.port}` via `@DynamicPropertySource`. Test scenarios: (1) SSO latency > 10s timeout → exception, (2) SSO circuit breaker opens after 5+ failures (Resilience4j: COUNT_BASED window 10, 50% threshold, min 5 calls), (3) SSO fallback throws `AuthException(SSO_TOKEN_INVALID, HTTP 503)`, (4) Captcha fallback returns `true` (graceful degradation), (5) Captcha noop bypasses WireMock.
  - Dependencies: spring-cloud-contract-wiremock (Task 6), HttpSsoGateway, HttpCaptchaGateway, HttpClientConfig

- [ ] **Task 8: Enhance CacheEncryptionIntegrationTest**
  - File: `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt` | Action: [MODIFY]
  - FR: FR-007 — Test cache encryption correctness
  - Pattern: Keep existing test. Add 3 new test methods: (1) `mode NONE - raw data should be valid JSON` — trigger cache write via MockMvc API call, read raw Redis via `StringRedisTemplate`, assert `raw.contains("{\"")`. (2) `mode FULL - raw data should NOT be valid JSON` — same trigger, assert raw is NOT valid JSON (encrypted). (3) `mode PARTIAL - JSON structure with encrypted field values` — assert JSON parseable but field values are not plaintext. Fixed encryption key: `dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3OA==` in application-test.yml. Use Redis Testcontainer.
  - Dependencies: StringRedisTemplate, base-cache-starter encryption config

- [ ] **Task 9: Create QueryCountIntegrationTest**
  - File: `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/QueryCountIntegrationTest.kt` | Action: [NEW]
  - FR: FR-008 — Phát hiện N+1 queries tự động
  - Pattern: `@SpringBootTest @ActiveProfiles("test") @Import(DataSourceProxyConfig::class)`. Test methods: (1) `login should not produce N+1 queries` — `assertQueryCount(select = N) { mockMvc.perform(POST /api/v1/auth/login) }`, (2) `token refresh should execute expected SELECT count` — `@AssertQueryCount(select = 1)` annotation. On N+1 detection → test FAILS with detailed query list.
  - Dependencies: DataSourceProxyConfig (from base-testing-starter, Task 2), QueryCountAssertions (Task 3)

---

## Phase 3: K6 Expansion

- [ ] **Task 10: Create helpers.js shared utility**
  - File: `auth-service/tests/load/helpers.js` | Action: [NEW]
  - FR: FR-003, FR-010 — K6 shared utility
  - Pattern: Export `loginAndGetToken(baseUrl)` function: POST `/api/v1/auth/login` with `{username: 'loaduser', password: 'password123'}`, parse response, return `data.accessToken`. Fallback: return `null` if login fails.
  - Dependencies: None

- [ ] **Task 11: Expand auth_flow.js to multi-scenario**
  - File: `auth-service/tests/load/auth_flow.js` | Action: [MODIFY]
  - FR: FR-003, FR-006 — K6 expansion + CI/CD gate
  - Pattern: Complete rewrite. Import `loginAndGetToken` from `./helpers.js`. Define `export const options` with `scenarios`: (1) `auth_login`: executor `ramping-vus`, stages `[{duration:'10s',target:100},{duration:'20s',target:500},{duration:'10s',target:0}]`, exec `loginFlow`. (2) `profile_get`: executor `constant-arrival-rate`, rate 100, preAllocatedVUs 200, duration '30s', exec `profileFlow`. Thresholds: `'http_req_duration{scenario:auth_login}': ['p(95)<200']`, `'http_req_duration{scenario:profile_get}': ['p(95)<150']`, `'http_req_failed': ['rate<0.01']`. URL: `http://localhost:8080` (not `host.docker.internal`). Export named functions `loginFlow()` and `profileFlow()`.
  - Dependencies: helpers.js (Task 10)

- [ ] **Task 12: Expand profile_flow.js**
  - File: `auth-service/tests/load/profile_flow.js` | Action: [MODIFY]
  - FR: FR-003 — K6 expansion
  - Pattern: Complete rewrite. Import `loginAndGetToken` from `./helpers.js`. Executor: `constant-arrival-rate`, rate 100 req/s, preAllocatedVUs 200, duration 30s. Setup: call `loginAndGetToken()` in setup function. Default function: GET `/api/v1/profiles/me` with `Authorization: Bearer ${token}`. Thresholds: `'http_req_duration': ['p(95)<150']`, `'http_req_failed': ['rate<0.01']`. URL: `http://localhost:8080`.
  - Dependencies: helpers.js (Task 10)

- [ ] **Task 13: Create cache_benchmark.js**
  - File: `auth-service/tests/load/cache_benchmark.js` | Action: [NEW]
  - FR: FR-010 — Cache benchmark K6 script
  - Pattern: Import `loginAndGetToken` from `./helpers.js`. Executor: `constant-arrival-rate`, rate 100 req/s, preAllocatedVUs 200, duration 30s. Env var: `__ENV.CACHE_ENCRYPTION_MODE` (default: `NONE`). Flow: login → profile get (exercises cache write + read). Tag results with `{ encryption_mode: __ENV.CACHE_ENCRYPTION_MODE }`. Thresholds: `'http_req_duration': ['p(95)<200']`, `'http_req_failed': ['rate<0.01']`.
  - Dependencies: helpers.js (Task 10)

- [ ] **Task 14: Add k6CacheBenchmark Gradle task**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-004 — K6 Gradle integration
  - Pattern: Add `tasks.register<Exec>("k6CacheBenchmark")` after existing k6ProfileRun task. `group = "Verification"`. `description = "Run K6 Cache Encryption Benchmark"`. `workingDir = file("tests/load")`. Accept `cacheMode` project property: `val encryptionMode = project.findProperty("cacheMode")?.toString() ?: "NONE"`. Docker command: `docker run --rm -i -v ${workingDir}:/scripts -e CACHE_ENCRYPTION_MODE=$encryptionMode --network host grafana/k6 run /scripts/cache_benchmark.js`. Usage: `./gradlew k6CacheBenchmark -PcacheMode=FULL`.
  - Dependencies: cache_benchmark.js (Task 13)

---

## Phase 4: JMH + CI/CD Gate

- [ ] **Task 15: Add JMH plugin and configuration**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - FR: FR-005 — JMH microbenchmark setup
  - Pattern: Add plugin: `id("me.champeau.jmh") version "0.7.2"` in plugins block. Add dependencies: `jmh("org.openjdk.jmh:jmh-core:1.37")` and `jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")`. Add jmh config block: `jmh { fork.set(2); warmupIterations.set(5); iterations.set(5); resultFormat.set("JSON"); resultsFile.set(project.file("build/results/jmh/results.json")) }`.
  - Dependencies: None (build config)

- [ ] **Task 16: Create EncryptionBenchmark**
  - File: `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt` | Action: [NEW]
  - FR: FR-005 — JMH microbenchmark
  - Pattern: `@BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @State(Scope.Thread) open class EncryptionBenchmark`. `@Setup fun setup()`: initialize AES-GCM `Cipher` with test key, prepare 1KB test payload (typical session data size). Benchmark methods: `@Benchmark fun encryptAesGcm(bh: Blackhole)` — encrypt payload, consume result. `@Benchmark fun decryptAesGcm(bh: Blackhole)` — decrypt pre-encrypted data, consume result. No Spring context — manual dependency instantiation.
  - Dependencies: JMH plugin (Task 15), AES-GCM (javax.crypto)

- [ ] **Task 17: Create SerializationBenchmark**
  - File: `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt` | Action: [NEW]
  - FR: FR-005 — JMH microbenchmark
  - Pattern: `@BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @State(Scope.Thread) open class SerializationBenchmark`. `@Setup fun setup()`: create `JsonMapper.builder().build()` (Jackson 3.x), create test object matching typical auth response size. Benchmark methods: `@Benchmark fun serialize(bh: Blackhole)` — `jsonMapper.writeValueAsBytes(testObject)`, consume. `@Benchmark fun deserialize(bh: Blackhole)` — `jsonMapper.readValue(bytes, TestDto::class.java)`, consume. No Spring context.
  - Dependencies: JMH plugin (Task 15), Jackson 3.x `tools.jackson.databind.json.JsonMapper`

---

## Summary

| Phase | Tasks | FRs Covered | New Files | Modified Files |
|-------|-------|-------------|-----------|----------------|
| Phase 1: Foundation | Task 1-5 | FR-001, FR-008, FR-009 | 4 | 1 |
| Phase 2: Integration Tests | Task 6-9 | FR-002, FR-007, FR-008 | 2 | 2 |
| Phase 3: K6 Expansion | Task 10-14 | FR-003, FR-004, FR-006, FR-010 | 2 | 3 |
| Phase 4: JMH + CI/CD | Task 15-17 | FR-005, FR-006 | 2 | 1 |
| **Total** | **17 tasks** | **10/10 FRs** | **10 new** | **7 modified** |

### FR Traceability Check

| FR-ID | Task(s) | Status |
|-------|---------|--------|
| FR-001 | Task 3, 4, 5 | ✅ Covered |
| FR-002 | Task 6, 7 | ✅ Covered |
| FR-003 | Task 10, 11, 12 | ✅ Covered |
| FR-004 | Task 14 | ✅ Covered |
| FR-005 | Task 15, 16, 17 | ✅ Covered |
| FR-006 | Task 11 (thresholds) | ✅ Covered |
| FR-007 | Task 8 | ✅ Covered |
| FR-008 | Task 5, 9 | ✅ Covered |
| FR-009 | Task 1, 2, 6 | ✅ Covered |
| FR-010 | Task 13, 14 | ✅ Covered |

**Coverage**: 10/10 — all FRs have corresponding implementation tasks.
