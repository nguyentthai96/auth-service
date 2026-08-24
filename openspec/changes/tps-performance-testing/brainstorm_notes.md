---
type: brainstorm_notes
change: tps-performance-testing
date: 2026-08-21
selected_direction: "4-Phase Hybrid: datasource-proxy Foundation → Integration Tests (WireMock + Cache) → K6 Expansion → JMH + CI/CD Gate"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: TPS Performance Testing — Comprehensive Testing Infrastructure

## Date
2026-08-21

## Context
Auth-service là hệ thống xác thực trung tâm của kiến trúc microservices. Hiện tại có:
- 2 K6 scripts cơ bản (`auth_flow.js`: 50 VUs/10s, `profile_flow.js`: 50 VUs/10s)
- 2 Gradle Exec tasks (`k6Run`, `k6ProfileRun`)
- 1 CacheEncryptionIntegrationTest cơ bản (chỉ verify key-value set/get)
- Resilience4j circuit breaker config cho `ssoProvider` và `captchaProvider`
- Clean Architecture pattern (domain/application/adapter layers)
- `AbstractIntegrationTest` base class (Java) with MockMvc + Jackson 3.x

**Gaps identified from research**:
1. Không có SQL query count assertion → N+1 detection bằng tay
2. Không có WireMock → external service latency chưa test
3. K6 chỉ 50 VUs/10s → không đủ cho TPS baseline measurement
4. Không có JMH → encryption overhead chưa benchmark
5. CacheEncryptionIntegrationTest chỉ test basic set/get → chưa verify raw encrypted format
6. Không có CI/CD performance gate → regression detection sau deploy

Research recommendation: **Hybrid approach** compose 4 tools (K6 + datasource-proxy + JMH + WireMock), mỗi tool best-in-class cho 1 testing layer. Scored 8.65/10 weighted vs Gatling 5.15 vs QuickPerf+K6 5.95.

---

## Questions Asked & Answers

### Q1: assertQueryCount nên đặt ở đâu — auth-service hay base-testing-starter?
- **A**: `base-testing-starter` — vì nó là shared test utility library, các service khác cũng benefit. Đặt `DataSourceProxyConfig`, `QueryCountAssertions` DSL, `@AssertQueryCount` annotation, và `AssertQueryCountExtension` vào `com.ntt.basetesting.assertion` package. Auth-service chỉ consume, không own.
- **Risk**: base-testing-starter là shared module → thay đổi ảnh hưởng tất cả consumers. Nhưng scope là test-only + additive (không breaking change).
- **⚠️ Assumption**: `base-testing-starter` cho phép thêm dependency `datasource-proxy` mà không conflict.

### Q2: WireMock target — SSO hay Captcha trước?
- **A**: SSO (`SsoProviderClient`) trước — vì:
  1. `HttpSsoGateway.exchangeAuthorizationCode()` gọi 2 HTTP calls liên tiếp (exchangeToken + getUserInfo) → complex flow, dễ bị latency cascade
  2. `HttpCaptchaGateway.verify()` có noop shortcut (`provider == "noop"` → bypass) → ít risk hơn
  3. SSO là critical path (login flow), captcha là optional guard
- **Note**: Cả hai đều đã có `@CircuitBreaker` annotation → WireMock test sẽ verify circuit breaker activation khi latency > threshold

### Q3: K6 500 VUs — `host.docker.internal` hay `--network host`?
- **A**: Giữ `--network host` (đã proven). Existing scripts dùng `host.docker.internal` cho URL nhưng container chạy `--network host`. Khi refactor, đổi URL sang `localhost:8080` cho consistency với `--network host`. Trên Linux CI, `host.docker.internal` có thể không resolve nhưng `--network host` + `localhost` luôn work.
- **Decision**: Standardize URL = `http://localhost:8080` + Docker flag = `--network host`

### Q4: Cache encryption key cho Testcontainer — fixed hay random?
- **A**: Fixed key cho reproducibility. Dùng hardcoded key trong `application-test.yml`:
  ```yaml
  cache:
    encryption:
      key: "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3OA=="  # Base64 of test key
  ```
  Random key → non-deterministic test results → flaky tests. Fixed key cho phép assert exact encrypted output.

### Q5: JMH chạy có Spring Context không?
- **A**: Không — JMH benchmarks chạy trong isolated JVM forks, không có Spring context. Benchmark target là pure functions:
  - `AesGcmEncryptor.encrypt(payload)` — measure AES-GCM throughput
  - `ObjectMapper.writeValueAsBytes(obj)` — measure Jackson serialization
  - Không benchmark toàn bộ Spring request lifecycle (đó là job của K6)
- **Implication**: Benchmark classes cần instantiate dependencies manually, không dùng @Autowired

### Q6: K6 constant-arrival-rate hay ramping-vus cho TPS measurement?
- **A**: Dùng cả hai, cho mục đích khác nhau:
  - `ramping-vus` (0→500) cho `auth_login` scenario → stress test, tìm breaking point
  - `constant-arrival-rate` (100 req/s) cho `profile_get` scenario → đo sustained TPS capacity
  - `constant-arrival-rate` cho `cache_benchmark` → so sánh TPS across encryption modes
- **Reasoning**: ramping-vus tăng load dần, thấy được điểm saturation. constant-arrival-rate giữ fixed request rate, thấy được latency under steady load.

### Q7: datasource-proxy wrap pattern — BeanPostProcessor hay @TestConfiguration?
- **A**: `@TestConfiguration` + explicit DataSource wrapping. Lý do:
  1. `BeanPostProcessor` approach wrap mọi DataSource → có thể conflict với HikariCP internal management
  2. `@TestConfiguration` chỉ active khi test class import → explicit, predictable
  3. Pattern đã proven: Vlad Mihalcea's SQLStatementCountValidator approach
- **Risk**: `@Primary` conflict nếu HikariCP DataSource cũng `@Primary`. Mitigation: dùng `@DependsOn` hoặc `@Order`.

### Q8: Scope của `account-service` trong feature này?
- **A**: OUT OF SCOPE. Pre_openspec xác định candidate services là `auth-service` + `base-testing-starter` only. Account-service cache testing là separate feature. Previous brainstorm nhắc đến account-service nhưng pre_openspec (source of truth) không include.

---

## Approaches Considered

### Approach 1: All-at-once (Single Phase)
- **Mô tả**: Implement all 10 FRs simultaneously — datasource-proxy, WireMock, K6 expansion, JMH, cache tests, CI/CD gate trong 1 sprint.
- **Pros**:
  - One-time integration effort
  - All testing infrastructure available at once
  - No intermediate dependency conflicts
- **Cons**:
  - HIGH RISK: Large scope (10 FRs, 4 new tools) → high probability of integration issues
  - Hard to debug if multiple tools have config conflicts
  - No incremental validation
  - Estimated 8-10 dev-days without validation checkpoints

### Approach 2: 4-Phase Incremental (pre_openspec recommended)
- **Mô tả**:
  - **Phase 1 — Foundation**: datasource-proxy dependency, `DataSourceProxyConfig`, `QueryCountAssertions` DSL, `@AssertQueryCount` annotation + JUnit 5 Extension trong `base-testing-starter`
  - **Phase 2 — Integration Tests**: assertQueryCount tests cho auth-service, WireMock tests cho SSO/Captcha, mở rộng CacheEncryptionIntegrationTest (NONE/FULL/PARTIAL)
  - **Phase 3 — K6 Expansion**: auth_flow.js (500 VUs, multi-scenario), cache_benchmark.js, k6CacheBenchmark Gradle task
  - **Phase 4 — JMH + CI/CD**: JMH plugin, EncryptionBenchmark, SerializationBenchmark, CI/CD performance gate verification
- **Pros**:
  - Incremental validation — each phase is independently testable
  - Phase 1-2 give immediate value (N+1 detection)
  - Risk spread across 4 checkpoints
  - Natural dependency flow (foundation → consumers)
- **Cons**:
  - Slightly more overhead for phased planning
  - 5-8 dev-days total (same as single phase, just structured)

### Approach 3: Priority-based (Must-haves only, defer rest)
- **Mô tả**: Implement only Must-have features (FR-001 assertQueryCount, FR-003 K6 expansion, FR-004 K6 Gradle, FR-006 CI/CD gate, FR-008 N+1 detection, FR-009 datasource-proxy config). Defer Should/Nice-to-have (FR-002 WireMock, FR-005 JMH, FR-007 Cache correctness, FR-010 Cache Benchmark) to a future sprint.
- **Pros**:
  - Fastest time-to-value (3-4 dev-days)
  - Focus on highest-impact items first
  - Reduce initial complexity
- **Cons**:
  - Loses WireMock tests emock.port}     │
    │                                                          │
    │  Verification points:                                    │
    │  - readTimeout (10s) triggers after delay > 10s          │
    │  - CircuitBreaker opens after 50% failure rate           │
    │  - SSO fallback throws AuthException(SSO_TOKEN_INVALID)  │
    │  - Captcha fallback auto-passes (graceful degradation)   │
    └──────────────────────────────────────────────────────────┘
```

**K6 multi-scenario design**:

```
    auth_flow.js (Enhanced)

    Scenario 1: auth_login (ramping-vus)
    VUs
    500 |                  /------\
    400 |                /          \
    300 |              /              \
    200 |            /                  \
    100 |          /                      \
      0 |--------/                          \----
        +------+------+------+------+------+------
        0s     10s    20s    30s    40s    50s
    Stages: 0->100(10s), 100->500(20s), 500->0(10s)
    Exec: loginFlow() -> POST /api/v1/auth/login

    Scenario 2: profile_get (constant-arrival-rate)
    req/s
    100 |========================================
        +------+------+------+------+------+------
        0s     5s     10s    15s    20s    25s  30s
    Rate: 100 req/s, preAllocatedVUs: 200
    Exec: profileFlow() -> GET /api/v1/profiles/me

    Thresholds:
    - http_req_duration{scenario:auth_login}:  p(95) < 200ms
    - http_req_duration{scenario:profile_get}: p(95) < 150ms
    - http_req_failed: rate < 0.01 (1%)
```

---

## Pre-classifications (preliminary)
- **Feature type**: EXTEND (mở rộng testing infrastructure cho modules có sẵn)
- **Flow type**: Command (test scripts execute one-way, return Pass/Fail report)
- **Affected modules**: `auth-service` (primary), `base-testing-starter` (shared utility target)

---

## GitNexus Findings (if explored)

Codebase investigation qua grep/view_file (GitNexus MCP not available):

- **Existing K6 scripts**: `tests/load/auth_flow.js` (50 VUs, `host.docker.internal:8080`, single default function), `tests/load/profile_flow.js` (50 VUs, static test token)
- **Existing Gradle tasks**: `k6Run` and `k6ProfileRun` in `build.gradle.kts` (lines 87-97), both using `grafana/k6` Docker + `--network host`
- **HTTP clients**: `SsoProviderClient` (@HttpExchange, 2 endpoints: `/oauth2/token`, `/userinfo`), `CaptchaClient` (@HttpExchange, 1 endpoint: POST verify)
- **Gateway pattern**: `HttpSsoGateway` and `HttpCaptchaGateway` both use `@CircuitBreaker` with fallback methods
- **Resilience config**: `application-core-resilience.yml` — COUNT_BASED sliding window (size 10), 50% failure rate threshold, 30s wait in open state
- **HttpClientConfig**: Creates `RestClient` per service with configurable timeouts (connect: 5s, read: 10s) via `SimpleClientHttpRequestFactory`
- **Existing test base**: `AbstractIntegrationTest.java` with MockMvc, Jackson 3.x `JsonMapper`, `@ActiveProfiles("test")`, `@DirtiesContext(AFTER_EACH_TEST_METHOD)`
- **CacheEncryptionIntegrationTest**: Basic test — only sets/gets a manual key-value, does NOT test actual cache encryption modes through API flow
- **Related integration tests**: `SsoCallbackIntegrationTest`, `MfaLoginFlowIntegrationTest`, `TokenIntrospectionIntegrationTest` — patterns to follow

---

## Implementation Phase Plan

### Phase 1: Foundation (datasource-proxy in base-testing-starter)
**Scope**: FR-001, FR-009
**Effort**: 1-2 dev-days
**New files in base-testing-starter**:
- `DataSourceProxyConfig.kt` — `@TestConfiguration` wrapping DataSource with `ProxyDataSourceBuilder.create().countQuery().build()`
- `QueryCountAssertions.kt` — Kotlin object with `assertQueryCount(select=N) { block }` DSL
- `AssertQueryCount.kt` — Annotation (`@AssertQueryCount(select=2)`)
- `AssertQueryCountExtension.kt` — JUnit 5 Extension (BeforeEachCallback + AfterEachCallback)

**Validation checkpoint**: Unit test assertQueryCount with H2 DataSource

### Phase 2: Integration Tests (auth-service)
**Scope**: FR-002, FR-007, FR-008
**Effort**: 2-3 dev-days
**New files in auth-service**:
- `QueryCountIntegrationTest.kt` — assertQueryCount tests for login, token refresh (N+1 detection)
- `WireMockExternalServiceTest.kt` — WireMock latency/fault tests for SSO + Captcha circuit breaker
- Enhanced `CacheEncryptionIntegrationTest.kt` — NONE/FULL/PARTIAL raw data verification via StringRedisTemplate

**New dependencies in build.gradle.kts**:
- `net.ttddyy:datasource-proxy:1.10` (testImplementation)
- `org.springframework.cloud:spring-cloud-contract-wiremock` (testImplementation)

**Validation checkpoint**: All integration tests green with H2 + Redis Testcontainer + WireMock

### Phase 3: K6 Expansion
**Scope**: FR-003, FR-004, FR-010
**Effort**: 1-2 dev-days
**Modified files**:
- `tests/load/auth_flow.js` — expanded to 500 VUs, ramping-vus + constant-arrival-rate dual scenario, dynamic token acquisition
- `tests/load/profile_flow.js` — constant-arrival-rate, dynamic token from login response

**New files**:
- `tests/load/cache_benchmark.js` — cache encryption mode benchmark script with `CACHE_ENCRYPTION_MODE` env var
- `tests/load/helpers.js` — shared utility (loginAndGetToken function)

**New Gradle task**:
- `k6CacheBenchmark` — Exec task for cache benchmark

**Validation checkpoint**: K6 scripts execute successfully against running auth-service

### Phase 4: JMH + CI/CD Gate
**Scope**: FR-005, FR-006
**Effort**: 1-2 dev-days
**New plugin**: `me.champeau.jmh` v0.7.2
**New files**:
- `src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt` — AES-GCM throughput (ops/sec)
- `src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt` — Jackson serialization (ops/sec)

**Modified files**:
- `build.gradle.kts` — JMH plugin + jmh dependencies + jmh config block

**Validation checkpoint**: `./gradlew jmh` produces `build/results/jmh/results.json`, K6 thresholds enforce exit code 99

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| datasource-proxy conflicts with HikariCP in test context | LOW | MEDIUM | Use `@TestConfiguration` pattern (not BeanPostProcessor). Test with H2 first. |
| WireMock port conflicts in parallel test execution | MEDIUM | LOW | Use `@AutoConfigureWireMock(port = 0)` (dynamic port). Inject via `${wiremock.server.port}`. |
| JMH plugin incompatible with JVM 25 | LOW | LOW | Fallback: use JMH jar directly, pin compatible version. |
| K6 Docker container network issues on CI | LOW | HIGH | `--network host` already proven. Document CI Docker daemon requirements. |
| base-testing-starter change breaks other services | LOW | MEDIUM | Additive change only (new classes, no modification). Test in isolation first. |
| K6 500 VUs overwhelms local dev machine | MEDIUM | LOW | Document minimum hardware requirements. Provide `k6RunLite` task with 50 VUs for local dev. |

---

## Open Questions for Design Phase
- [OPEN] `base-testing-starter` build system — does it use same Gradle convention plugin? Need to verify dependency management for datasource-proxy.
- [OPEN] Should K6 `helpers.js` export a shared `loginAndGetToken()` function, or should each script handle auth independently?
- [OPEN] JMH benchmark — should we benchmark `base-cache-starter`'s encryption utilities directly, or create simplified test doubles?
- [RESOLVED] SSO vs Captcha WireMock priority → Both, but SSO first (more complex flow, critical path)
- [RESOLVED] Cache encryption key → Fixed in test config for reproducibility
- [RESOLVED] K6 URL standardization → `localhost:8080` with `--network host`
- [RESOLVED] datasource-proxy wrap pattern → `@TestConfiguration` (not BeanPostProcessor)
- [RESOLVED] account-service scope → OUT OF SCOPE per pre_openspec

## Open Questions for URD Analysis
- N/A — URD already analyzed in pre_openspec.md (Quality Score 88/100)
