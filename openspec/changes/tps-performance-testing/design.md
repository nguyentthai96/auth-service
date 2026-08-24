# Design: tps-performance-testing

_Generated: 2026-08-27_

---

## Locked Profile

| Field | Value |
|-------|-------|
| flow | Command |
| factory | N/A (test infrastructure — no transaction factory) |
| feature_type | EXTEND |
| transaction_flow | Command — test execution is one-way, returns Pass/Fail report |

---

## 1. Architecture Overview

**Tri-Layer Testing Architecture** — each layer targets a specific performance dimension with a best-in-class tool:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Developer / CI/CD Pipeline                      │
│                                                                     │
│  Gradle Tasks: k6Run │ k6ProfileRun │ k6CacheBenchmark │ jmh │ test│
└───────┬──────────────┬──────────────┬───────────────────┬──────┬────┘
        │              │              │                   │    t | `base-testing-starter` (shared) | Reusable across all services; additive, test-scoped only |
| datasource-proxy wrap pattern | `@TestConfiguration` (not BeanPostProcessor) | Explicit, predictable, avoids HikariCP conflict |
| WireMock target priority | SSO first, then Captcha | SSO has 2 HTTP calls (exchangeToken + getUserInfo), more complex flow |
| K6 URL | `localhost:8080` (not `host.docker.internal`) | Consistent with `--network host`; Linux CI compatible |
| K6 executors | ramping-vus for stress + constant-arrival-rate for steady | Different measurement goals per scenario |
| JMH Spring context | No Spring — isolated JVM forks | Pure function benchmarking, no framework noise |
| Cache encryption key | Fixed in test config | Reproducible, deterministic test results |
| Phasing | 4-phase incremental | Independent validation per phase, natural dependency flow |

---

## 2. Phase 1 — Foundation Design (base-testing-starter)

### 2.1 DataSourceProxyConfig

**File**: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/DataSourceProxyConfig.kt`

```kotlin
@TestConfiguration
class DataSourceProxyConfig {

    @Bean
    @Primary
    fun dataSourceProxy(originalDataSource: DataSource): DataSource {
        return ProxyDataSourceBuilder.create(originalDataSource)
            .countQuery()
            .build()
    }
}
```

**Design Notes**:
- `@TestConfiguration` → only active when explicitly imported by test class
- `@Primary` → overrides default DataSource in test context
- `ProxyDataSourceBuilder.create().countQuery()` → enables `QueryCountHolder` per-thread counting
- Transparent to business logic — all JDBC calls pass through to original datasource

### 2.2 QueryCountAssertions DSL

**File**: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/QueryCountAssertions.kt`

```kotlin
object QueryCountAssertions {

    inline fun assertQueryCount(
        select: Int? = null,
        insert: Int? = null,
        update: Int? = null,
        delete: Int? = null,
        block: () -> Unit
    ) {
        QueryCountHolder.clear()
        block()
        val grandTotal = QueryCountHolder.getGrandTotal()

        select?.let { expected ->
            val actual = grandTotal.select.toLong()
            if (actual != expected.toLong()) {
                fail("Expected $expected SELECT queries, but got $actual. " +
                     "Total queries: S=${grandTotal.select} I=${grandTotal.insert} " +
                     "U=${grandTotal.update} D=${grandTotal.delete}")
            }
        }
        // Similar for insert, update, delete...
        QueryCountHolder.clear()
    }
}
```

**Design Notes**:
- Kotlin `object` → no instantiation needed, call as `QueryCountAssertions.assertQueryCount { }`
- Thread-local via `QueryCountHolder` → safe for parallel test execution
- `QueryCountHolder.clear()` before AND after block → no leaking between tests
- Each query type is optional (`null` = don't assert)

### 2.3 @AssertQueryCount Annotation

**File**: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCount.kt`

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(AssertQueryCountExtension::class)
annotation class AssertQueryCount(
    val select: Int = -1,  // -1 = don't assert
    val insert: Int = -1,
    val update: Int = -1,
    val delete: Int = -1
)
```

### 2.4 AssertQueryCountExtension

**File**: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/assertion/AssertQueryCountExtension.kt`

```kotlin
class AssertQueryCountExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        QueryCountHolder.clear()
    }

    override fun afterEach(context: ExtensionContext) {
        val annotation = context.requiredTestMethod
            .getAnnotation(AssertQueryCount::class.java) ?: return

        val grandTotal = QueryCountHolder.getGrandTotal()

        if (annotation.select >= 0) {
            assertEquals(annotation.select.toLong(), grandTotal.select,
                "Expected ${annotation.select} SELECT, got ${grandTotal.select}")
        }
        // Similar for insert, update, delete...
        QueryCountHolder.clear()
    }
}
```

### 2.5 Dependency Addition

**File**: `base-testing-starter/build.gradle.kts`

```kotlin
dependencies {
    api("net.ttddyy:datasource-proxy:1.10")
}
```

**Rationale**: `api` scope so consuming services get datasource-proxy transitively when using base-testing-starter.

---

## 3. Phase 2 — Integration Tests Design (auth-service)

### 3.1 WireMockExternalServiceTest

**File**: `auth-service/src/test/kotlin/com/ntt/authservice/auth/adapter/out/http/WireMockExternalServiceTest.kt`

```
Test Architecture:

    WireMockExternalServiceTest
    @SpringBootTest
    @AutoConfigureWireMock(port = 0)
    @ActiveProfiles("test")

    Test Method                      WireMock Server (dynamic port)
    1. Setup WireMock stubs    →     /oauth2/token  → delay 15s (> 10s timeout)
                                     /userinfo      → delay 15s
    2. Call SSO Gateway        →     (receives timeout / delayed response)
    3. Assert:
       a) Timeout → exception thrown
       b) After 5+ calls → circuit breaker opens
       c) Fallback → AuthException(SSO_TOKEN_INVALID, HTTP 503)

    Property Override:
      app.security.sso.provider-base-url=http://localhost:${wiremock.server.port}
```

**Key Test Scenarios**:

| Test Method | WireMock Setup | Assertion |
|-------------|---------------|-----------|
| `sso latency exceeds timeout` | `/oauth2/token` → 15s delay | Exception thrown (timeout) |
| `sso circuit breaker opens after failures` | `/oauth2/token` → fault | After 5 calls: circuit breaker OPEN state |
| `sso fallback throws AuthException` | Circuit open | `AuthException(SSO_TOKEN_INVALID)`, HTTP 503 |
| `captcha circuit breaker graceful degradation` | Captcha endpoint → fault | Fallback returns `true` (auto-pass) |
| `captcha noop bypasses WireMock` | No stub needed | `provider=noop` → `true` without HTTP call |

**Configuration Override** (via `@DynamicPropertySource` or `@TestPropertySource`):
```properties
app.security.sso.provider-base-url=http://localhost:${wiremock.server.port}
app.security.captcha.verify-url=http://localhost:${wiremock.server.port}
```

### 3.2 CacheEncryptionIntegrationTest (Enhanced)

**File**: `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt`

**Current** (basic set/get):
```kotlin
@Test
fun `redis raw data should verify cache behavior`() {
    stringRedisTemplate.opsForValue().set("test:tps:key", "{\"hello\":\"world\"}")
    val raw = stringRedisTemplate.opsForValue().get("test:tps:key")
    assertTrue(raw != null)
    assertTrue(raw.contains("hello"))
}
```

**Enhanced** (NONE/FULL/PARTIAL verification):
```kotlin
@Test
fun `mode NONE - raw data should be valid JSON`() {
    // Trigger cache write via API call (login/profile)
    // Read raw Redis data via StringRedisTemplate
    // Assert: raw.contains("{\"") == true (valid JSON)
}

@Test
fun `mode FULL - raw data should NOT be valid JSON`() {
    // Same trigger
    // Assert: raw data is NOT valid JSON (encrypted binary/base64)
    // Assert: raw data length > 0 (not empty)
}

@Test
fun `mode PARTIAL - JSON structure with encrypted field values`() {
    // Same trigger
    // Assert: raw data IS valid JSON structure
    // Assert: field values are encrypted (not plaintext)
}
```

**Configuration**: Fixed encryption key in `application-test.yml`:
```yaml
cache:
  encryption:
    key: "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3OA=="
```

### 3.3 QueryCountIntegrationTest

**File**: `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/QueryCountIntegrationTest.kt`

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Import(DataSourceProxyConfig::class)
class QueryCountIntegrationTest {

    @Test
    fun `login should not produce N+1 queries`() {
        assertQueryCount(select = 2) {  // user lookup + session creation check
            // invoke login via service or MockMvc
        }
    }

    @Test
    @AssertQueryCount(select = 1)
    fun `token refresh should execute single SELECT`() {
        // invoke token refresh
    }
}
```

---

## 4. Phase 3 — K6 Expansion Design

### 4.1 auth_flow.js (Enhanced)

**File**: `auth-service/tests/load/auth_flow.js`

```
Scenario Design:

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
    Stages: 0→100 (10s), 100→500 (20s), 500→0 (10s)
    Exec: loginFlow() → POST /api/v1/auth/login

    Scenario 2: profile_get (constant-arrival-rate)
    req/s
    100 |========================================
        +------+------+------+------+------+------
        0s     5s     10s    15s    20s    25s  30s
    Rate: 100 req/s, preAllocatedVUs: 200, duration: 30s
    Exec: profileFlow() → GET /api/v1/profiles/me (with token)

    Thresholds:
    - http_req_duration{scenario:auth_login}:  p(95) < 200ms
    - http_req_duration{scenario:profile_get}: p(95) < 150ms
    - http_req_failed: rate < 0.01 (1%)
```

**Key Changes from Current Script**:
- URL: `localhost:8080` (was `host.docker.internal:8080`)
- VUs: 500 max (was 50)
- Duration: ~50s total (was 10s)
- Scenarios: 2 (was 1 default function)
- Token: Dynamic from login response (was static/hardcoded)
- Thresholds: Scenario-specific (was global)

### 4.2 helpers.js (New)

**File**: `auth-service/tests/load/helpers.js`

```javascript
// Shared K6 utility module
import http from 'k6/http';

export function loginAndGetToken(baseUrl) {
    const res = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
        username: 'loaduser',
        password: 'password123'
    }), { headers: { 'Content-Type': 'application/json' } });

    if (res.status === 200) {
        return JSON.parse(res.body).data.accessToken;
    }
    return null;
}
```

⚠️ OPEN QUESTION: Should `helpers.js` export shared functions, or should each script handle auth independently? Recommendation: shared — reduces duplication, single point of credential management.

### 4.3 cache_benchmark.js (New)

**File**: `auth-service/tests/load/cache_benchmark.js`

```
Design:
- Executor: constant-arrival-rate (100 req/s)
- preAllocatedVUs: 200
- Duration: 30s
- Env var: CACHE_ENCRYPTION_MODE (default: NONE)
- Flow: login → profile get (exercises cache write + read)
- Tags: { encryption_mode: __ENV.CACHE_ENCRYPTION_MODE }
- Thresholds: Same as auth_flow.js
```

### 4.4 k6CacheBenchmark Gradle Task

**File**: `auth-service/build.gradle.kts`

```kotlin
tasks.register<Exec>("k6CacheBenchmark") {
    group = "Verification"
    description = "Run K6 Cache Encryption Benchmark"
    workingDir = file("tests/load")
    val encryptionMode = project.findProperty("cacheMode")?.toString() ?: "NONE"
    commandLine("docker", "run", "--rm", "-i",
        "-v", "${workingDir}:/scripts",
        "-e", "CACHE_ENCRYPTION_MODE=$encryptionMode",
        "--network", "host",
        "grafana/k6", "run", "/scripts/cache_benchmark.js")
}
```

Usage: `./gradlew k6CacheBenchmark -PcacheMode=FULL`

---

## 5. Phase 4 — JMH + CI/CD Gate Design

### 5.1 JMH Plugin Configuration

**File**: `auth-service/build.gradle.kts`

```kotlin
plugins {
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    fork.set(2)
    warmupIterations.set(5)
    iterations.set(5)
    resultFormat.set("JSON")
    resultsFile.set(project.file("build/results/jmh/results.json"))
}
```

### 5.2 EncryptionBenchmark

**File**: `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt`

```kotlin
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
open class EncryptionBenchmark {

    private lateinit var cipher: Cipher
    private lateinit var testPayload: ByteArray

    @Setup
    fun setup() {
        // Initialize AES-GCM cipher with test key
        // Prepare 1KB test payload (typical session data size)
    }

    @Benchmark
    fun encryptAesGcm(bh: Blackhole) {
        // Encrypt testPayload → bh.consume(result)
    }

    @Benchmark
    fun decryptAesGcm(bh: Blackhole) {
        // Decrypt pre-encrypted data → bh.consume(result)
    }
}
```

**Important**: No Spring context. Manual instantiation of AES-GCM cipher. Benchmarks pure cryptographic operation throughput.

### 5.3 SerializationBenchmark

**File**: `auth-service/src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt`

```kotlin
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
open class SerializationBenchmark {

    private lateinit var jsonMapper: JsonMapper
    private lateinit var testObject: Any  // typical auth response DTO

    @Setup
    fun setup() {
        jsonMapper = JsonMapper.builder().build()
        // Create test object matching typical auth response size
    }

    @Benchmark
    fun serialize(bh: Blackhole) {
        bh.consume(jsonMapper.writeValueAsBytes(testObject))
    }

    @Benchmark
    fun deserialize(bh: Blackhole) {
        bh.consume(jsonMapper.readValue(serializedBytes, testObject::class.java))
    }
}
```

**Important**: Uses Jackson 3.x `JsonMapper` (same as production). No Spring context.

### 5.4 CI/CD Performance Gate

K6 thresholds are embedded in the script `options` object:

```javascript
export const options = {
    thresholds: {
        'http_req_duration{scenario:auth_login}': ['p(95)<200'],
        'http_req_duration{scenario:profile_get}': ['p(95)<150'],
        'http_req_failed': ['rate<0.01'],
    },
};
```

**Exit Code Flow**:
```
K6 threshold violation → K6 exit(99) → Docker container exit(99)
→ Gradle Exec task sees non-zero → BUILD FAILED → CI/CD pipeline stops
```

No additional CI/CD configuration needed — K6's built-in threshold mechanism handles this natively.

---

## 6. Dependencies Summary

### New Dependencies (auth-service build.gradle.kts)

```kotlin
// TESTING — Performance
testImplementation("net.ttddyy:datasource-proxy:1.10")  // FR-001, FR-009
testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")  // FR-002
```

### New Plugin (auth-service build.gradle.kts)

```kotlin
plugins {
    id("me.champeau.jmh") version "0.7.2"  // FR-005
}
```

### New Dependencies (base-testing-starter build.gradle.kts)

```kotlin
api("net.ttddyy:datasource-proxy:1.10")  // FR-001, FR-009
```

---

## 7. Component Mapping

| Component | Layer | Files | FRs |
|-----------|-------|-------|-----|
| DataSourceProxyConfig | base-testing-starter | `assertion/DataSourceProxyConfig.kt` | FR-009 |
| QueryCountAssertions | base-testing-starter | `assertion/QueryCountAssertions.kt` | FR-001 |
| AssertQueryCount | base-testing-starter | `assertion/AssertQueryCount.kt` | FR-001 |
| AssertQueryCountExtension | base-testing-starter | `assertion/AssertQueryCountExtension.kt` | FR-001, FR-008 |
| QueryCountIntegrationTest | auth-service/test | `auth/application/QueryCountIntegrationTest.kt` | FR-008 |
| WireMockExternalServiceTest | auth-service/test | `auth/adapter/out/http/WireMockExternalServiceTest.kt` | FR-002 |
| CacheEncryptionIntegrationTest | auth-service/test | `auth/application/CacheEncryptionIntegrationTest.kt` | FR-007 |
| auth_flow.js | auth-service/tests/load | `tests/load/auth_flow.js` | FR-003, FR-006 |
| profile_flow.js | auth-service/tests/load | `tests/load/profile_flow.js` | FR-003 |
| cache_benchmark.js | auth-service/tests/load | `tests/load/cache_benchmark.js` | FR-010 |
| helpers.js | auth-service/tests/load | `tests/load/helpers.js` | FR-003, FR-010 |
| EncryptionBenchmark | auth-service/jmh | `benchmark/EncryptionBenchmark.kt` | FR-005 |
| SerializationBenchmark | auth-service/jmh | `benchmark/SerializationBenchmark.kt` | FR-005 |
| build.gradle.kts | auth-service | `build.gradle.kts` | FR-004, FR-005, FR-009 |
