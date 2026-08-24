# Impact Analysis: tps-performance-testing

_Generated: 2026-08-27_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. Test-only scope — no production files modified.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [build.gradle.kts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | L1-L97 | Add JMH plugin, datasource-proxy/WireMock testImplementation, k6CacheBenchmark task |
| 2 | [auth_flow.js](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/tests/load/auth_flow.js) | L1-L33 | Expand from 50 VUs single scenario to 500 VUs multi-scenario |
| 3 | [profile_flow.js](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/tests/load/profile_flow.js) | L1-L31 | Expand with constant-arrival-rate, dynamic token |
| 4 | [CacheEncryptionIntegrationTest.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt) | L1-L26 | Extend from basic set/get to NONE/FULL/PARTIAL mode verification |

### New Files (auth-service)

| # | File | Type | Purpose |
|---|------|------|---------|
| 5 | `src/test/kotlin/com/ntt/authservice/auth/adapter/out/http/WireMockExternalServiceTest.kt` | Integration Test | WireMock latency/fault tests for SSO + Captcha |
| 6 | `src/test/kotlin/com/ntt/authservice/auth/application/QueryCountIntegrationTest.kt` | Integration Test | assertQueryCount N+1 detection tests |
| 7 | `tests/load/cache_benchmark.js` | K6 Script | Cache encryption mode TPS benchmark |
| 8 | `tests/load/helpers.js` | K6 Utility | Shared loginAndGetToken() function |
| 9 | `src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt` | JMH Benchmark | AES-GCM throughput measurement |
| 10 | `src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt` | JMH Benchmark | Jackson serialization throughput |

### New Files (base-testing-starter)

| # | File | Type | Purpose |
|---|------|------|---------|
| 11 | `src/main/kotlin/com/ntt/basecore/testing/assertion/DataSourceProxyConfig.kt` | @TestConfiguration | Wrap DataSource with datasource-proxy |
| 12 | `src/main/kotlin/com/ntt/basecore/testing/assertion/QueryCountAssertions.kt` | Kotlin Object | assertQueryCount {} DSL |
| 13 | `src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCount.kt` | Annotation | @AssertQueryCount(select=2) declarative |
| 14 | `src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCountExtension.kt` | JUnit Extension | BeforeEach/AfterEach query count reset/assert |

---

## 2. Call Tree — LOGIC CẦN SỬA

> Test infrastructure — no production call trees modified. Below shows test execution flows.

#### Integration Test Flow: `QueryCountIntegrationTest`

```
⟶ @Test loginQueryCount()
├── QueryCountHolder.clear()  // ← reset thread-local counters
├── mockMvc.perform(POST /api/v1/auth/login)
│   └── AuthService.login()
│       ├── UserRepository.findByUsername()  // ← SELECT #1
│       └── SessionRepository.save()         // ← INSERT #1
├── QueryCountHolder.getGrandTotal()
│   └── Assert: select == expected
│       ├── match → PASS
│       └── mismatch → FAIL("Expected N SELECT, got M")
└── QueryCountHolder.clear()  // ← cleanup
```

#### Integration Test Flow: `WireMockExternalServiceTest`

```
⟶ @Test ssoLatencyExceedsTimeout()
├── WireMock.stubFor(post("/oauth2/token")
│   .willReturn(aResponse().withFixedDelay(15000)))  // ← 15s > 10s timeout
├── httpSsoGateway.exchangeAuthorizationCode()
│   └── ssoProviderClient.exchangeToken()
│       └── RestClient → WireMock server (timeout)
│           └── SimpleClientHttpRequestFactory.readTimeout = 10s
├── Assert: exception thrown (timeout or circuit breaker)
└── Repeat 5+ times → @CircuitBreaker opens
    └── exchangeCodeFallback()
        └── throw AuthException(SSO_TOKEN_INVALID, HTTP 503)
```

#### K6 Script Flow: `auth_flow.js` (Enhanced)

```
⟶ K6 init
├── import { loginAndGetToken } from './helpers.js'
├── Scenario 1: auth_login (ramping-vus 0→500)
│   └── loginFlow()
│       ├── POST /api/v1/auth/login
│       └── check(res, { 'is status 200': r => r.status === 200 })
├── Scenario 2: profile_get (constant-arrival-rate 100 req/s)
│   └── profileFlow()
│       ├── token = loginAndGetToken()  // ← setup phase
│       ├── GET /api/v1/profiles/me (Authorization: Bearer ${token})
│       └── check(res, { 'is status 200': r => r.status === 200 })
└── Thresholds evaluation
    ├── PASS → exit(0)
    └── FAIL → exit(99) → Gradle BUILD FAILED
```

---

## 3. Blast Radius

> All changes are test-scoped. Blast radius is minimal.

### 🟢 Direct Impact — auth-service (4 modified files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `build.gradle.kts` | [build.gradle.kts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | Add 2 testImplementation deps, 1 plugin, 1 Gradle task. No production dependency changes. |
| 2 | `auth_flow.js` | [auth_flow.js](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/tests/load/auth_flow.js) | Complete rewrite: 50 VUs → 500 VUs multi-scenario. Old script replaced entirely. |
| 3 | `profile_flow.js` | [profile_flow.js](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/tests/load/profile_flow.js) | Complete rewrite: static token → dynamic token, constant-arrival-rate. |
| 4 | `CacheEncryptionIntegrationTest.kt` | [CacheEncryptionIntegrationTest.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt) | Extend: add 3 test methods for NONE/FULL/PARTIAL verification. Keep existing test. |

### 🟡 Indirect Impact — base-testing-starter (1 modified, 4 new files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `build.gradle.kts` | [base-testing-starter/build.gradle.kts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-testing-starter/build.gradle.kts) | Add `api("net.ttddyy:datasource-proxy:1.10")`. Additive — no existing dependency changes. |
| 2-5 | 4 new files/src/main/resources/application-core-resilience.yml) | 100% | REUSE | 🟢 (existing) | Read config values for WireMock test assertions |
| K6 Gradle task pattern | [build.gradle.kts:L85-97](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts#L85-L97) | 90% | REUSE | 🟢 (2 existing tasks) | Copy pattern for k6CacheBenchmark |

**No EXTRACT actions needed** — all existing code is either REUSE (as-is) or MODIFY (in-place extension). No shared logic blocks duplicated across files.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `AbstractIntegrationTest` | Base class (Java) | `mockMvc`, `jsonMapper`, `performPostRequest()` | Spring Boot test base with MockMvc + Jackson 3.x |
| `HttpSsoGateway` | Gateway (Spring Component) | `exchangeAuthorizationCode()`, `getUserInfo()` | @CircuitBreaker("ssoProvider") with fallback |
| `HttpCaptchaGateway` | Gateway (Spring Component) | `verify()` | @CircuitBreaker("captchaProvider"), noop shortcut |
| `SsoProviderClient` | @HttpExchange interface | `exchangeToken()`, `getUserInfo()` | RestClient-based, configurable timeout |
| `CaptchaClient` | @HttpExchange interface | `verify()` | RestClient-based, configurable timeout |
| `StringRedisTemplate` | Spring Data Redis | `opsForValue().get()`, `opsForValue().set()` | Direct raw data access for cache tests |
| `QueryCountHolder` | datasource-proxy static | `getGrandTotal()`, `clear()` | Thread-local SQL query counters |
| `ProxyDataSourceBuilder` | datasource-proxy builder | `create(ds).countQuery().build()` | Wraps DataSource for counting |

### Config Keys

| Key | Source | Example Value | Nơi dùng |
|-----|--------|--------------|---------|
| `app.security.sso.provider-base-url` | application.yml | `https://oauth2.provider.com` | `HttpClientConfig.ssoProviderClient()` — override with WireMock URL in tests |
| `app.security.captcha.verify-url` | application.yml | `https://challenges.cloudflare.com/turnstile/v0/siteverify` | `HttpClientConfig.captchaClient()` — override with WireMock URL in tests |
| `app.http.sso.read-timeout-ms` | application.yml | `10000` | `HttpClientConfig.createTimeoutFactory()` — 10s read timeout |
| `app.http.captcha.read-timeout-ms` | application.yml | `10000` | `HttpClientConfig.createTimeoutFactory()` — 10s read timeout |
| `app.security.captcha.provider` | application.yml | `noop` / `cloudflare` | `HttpCaptchaGateway.verify()` — noop bypasses |
| `cache.encryption.key` | application-test.yml | `dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3OA==` | Cache encryption test — fixed key |
| `resilience4j.circuitbreaker.configs.default.failureRateThreshold` | application-core-resilience.yml | `50` | 50% failure rate → circuit opens |
| `resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls` | application-core-resilience.yml | `5` | Min 5 calls before evaluating |
| `resilience4j.circuitbreaker.configs.default.slidingWindowSize` | application-core-resilience.yml | `10` | COUNT_BASED sliding window |

### Error Codes Thrown (by existing code — tested by WireMock tests)

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `AuthErrorCode.SSO_TOKEN_INVALID` | Circuit breaker open for SSO provider | [HttpSsoGateway.exchangeCodeFallback():L64](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt#L64) |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| N/A | N/A | N/A | No new DTOs — test infrastructure only |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| POST /api/v1/auth/login | K6 script target | grep existing K6 auth_flow.js | ✅ Confirmed |
| GET /api/v1/profiles/me | K6 script target | grep existing K6 profile_flow.js | ✅ Confirmed |
| POST /oauth2/token | `SsoProviderClient.exchangeToken()` | view_file SsoProviderClient.kt | ✅ Confirmed |
| GET /userinfo | `SsoProviderClient.getUserInfo()` | view_file SsoProviderClient.kt | ✅ Confirmed |
| POST (captcha verify) | `CaptchaClient.verify()` | view_file CaptchaClient.kt | ✅ Confirmed |
