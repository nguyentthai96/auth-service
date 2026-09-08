# Design: Performance Benchmark Testing — Full TPS Evaluation

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                    PERFORMANCE TESTING ARCHITECTURE                   │
│                                                                      │
│  ┌─────────────────────┐    ┌──────────────────────────────────────┐ │
│  │  LOAD GENERATION     │    │  SYSTEM UNDER TEST (auth-service)   │ │
│  │                      │    │                                      │ │
│  │  K6 Docker           │───▶│  Tomcat + Virtual Threads            │ │
│  │  ├── single/*.js     │    │  ├── Security Filter Chain           │ │
│  │  ├── chains/*.js     │    │  ├── Application Layer               │ │
│  │  └── system/*.js     │    │  ├── JPA / HikariCP ──▶ PostgreSQL   │ │
│  │                      │    │  └── Lettuce ──▶ Redis               │ │
│  └─────────┬────────────┘    └──────────┬───────────────────────────┘ │
│            │ remote-write              │ /actuator/prometheus         │
│            ▼                           ▼                              │
│  ┌──────────────────────────────────────────┐                        │
│  │  OBSERVABILITY                            │                        │
│  │  Prometheus ◀── scrape 15s ──▶ Grafana   │                        │
│  │  :9090                          :3001     │                        │
│  └──────────────────────────────────────────┘                        │
│                                                                      │
│  ┌─────────────────────┐                                             │
│  │  MICROBENCHMARK      │                                             │
│  │  JMH (in-process)    │                                             │
│  │  ├── BCryptBenchmark │                                             │
│  │  ├── JwtBenchmark    │                                             │
│  │  └── RedisCacheBench │                                             │
│  └─────────────────────┘                                             │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. File Structure

```
auth-service/
├── compose-perf.yaml                     # FR-002: Observability Docker Compose
├── src/main/resources/
│   ├── application-core-observability.yml # FR-001, FR-018, FR-021: Config changes
│   └── application-db.yml                # FR-003: HikariCP explicit config
├── src/jmh/kotlin/com/ntt/authservice/benchmark/
│   ├── EncryptionBenchmark.kt            # EXISTING
│   ├── SerializationBenchmark.kt         # EXISTING
│   ├── BCryptBenchmark.kt                # FR-010: NEW
│   ├── JwtBenchmark.kt                   # FR-011: NEW
│   └── RedisCacheBenchmark.kt            # FR-012: NEW
├── tests/
│   ├── load/                              # EXISTING (backward compatible)
│   │   ├── auth_flow.js
│   │   ├── cache_benchmark.js
│   │   ├── helpers.js
│   │   └── profile_flow.js
│   └── perf/                              # NEW (all new work)
│       ├── config/
│       │   ├── thresholds.js              # FR-016: Centralized SLA
│       │   └── env.js                     # Environment + traffic mix config
│       ├── helpers/
│       │   ├── auth.js                    # Login, token utils (reuse from tests/load/helpers.js)
│       │   ├── data.js                    # Test data generators (SharedArray)
│       │   └── metrics.js                 # Custom K6 metrics definitions
│       ├── scenarios/
│       │   ├── infra_baseline.js          # FR-019: Infrastructure baseline
│       │   ├── single/                    # FR-005: Per-endpoint tests
│       │   │   ├── auth_p0.js             # login, refresh, validate, register
│       │   │   ├── auth_p1.js             # session, mfa/verify, sso, captcha
│       │   │   ├── internal.js            # internal/validate, events, rate-limits
│       │   │   └── rbac.js                # roles, users, permissions, policies
│       │   ├── chains/                    # FR-006: API chain tests
│       │   │   ├── auth_basic.js          # Login→Profile→Refresh→Logout
│       │   │   ├── auth_mfa.js            # Login→MFA→Verify→Profile
│       │   │   ├── registration.js        # Register→Verify→Login→Profile
│       │   │   ├── sso.js                 # SSO Init→Callback→Profile
│       │   │   ├── session_mgmt.js        # Login→Sessions→Revoke
│       │   │   ├── key_exchange.js        # Init Key→Encrypt→Response
│       │   │   ├── admin.js               # Login(Admin)→Users→Role→Verify
│       │   │   └── anon_to_auth.js        # Anon→Browse→Register→Promote
│       │   └── system/                    # FR-007, FR-008, FR-009
│       │       ├── full_load.js           # Mixed workload
│       │       ├── stress.js              # Breaking point
│       │       └── soak.js                # Memory leak detection
│       ├── prometheus.yml                 # FR-002: Scrape config
│       ├── grafana/
│       │   └── provisioning/
│       │       ├── datasources/
│       │       │   └── prometheus.yml     # FR-002: Auto-provision datasource
│       │       └── dashboards/
│       │           ├── dashboard.yml      # FR-014: Provider config
│       │           ├── perf-overview.json # FR-014: Main dashboard
│       │           └── api-detail.json    # FR-014: Per-endpoint detail
│       ├── docs/
│       │   ├── jvm-flags.md              # FR-004: JVM flags documentation
│       │   └── virtual-thread-pinning.md # FR-020: Pinning detection guide
│       └── reports/
│           ├── report_template.md        # FR-015: Structured report
│           └── bottleneck_checklist.md   # FR-017: USE Method checklist
```

## 3. Component Design

### 3.1 Config Changes (MODIFY existing files)

#### `application-core-observability.yml` (FR-001 + FR-018 + FR-021)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus    # FR-001: add prometheus
  endpoint:
    health:
      show-details: when-authorized
    prometheus:                                     # FR-001: enable endpoint
      enabled: true
  metrics:                                          # FR-018: histogram config
    tags:
      application: auth-service
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.95,0.99

server:                                             # FR-021: access log
  tomcat:
    accesslog:
      enabled: ${ACCESS_LOG_ENABLED:false}
      directory: logs
      prefix: access
      suffix: .log
      pattern: "%h %t \"%r\" %s %b %D"
      rotate: true
      max-days: 7
```

#### `application-db.yml` (FR-003)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db
    username: auth_user
    password: auth_pass
    driver-class-name: org.postgresql.Driver
    hikari:                                         # FR-003: explicit tuning
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN:${DB_POOL_MAX:10}}
      connection-timeout: ${DB_CONN_TIMEOUT:30000}
      idle-timeout: ${DB_IDLE_TIMEOUT:600000}
      max-lifetime: ${DB_MAX_LIFETIME:1800000}
      pool-name: AuthServicePool
      leak-detection-threshold: ${DB_LEAK_THRESHOLD:30000}
      data-source-properties:
        reWriteBatchedInserts: true
        prepareThreshold: 5
  # ... rest unchanged
```

### 3.2 Docker Compose Extension (NEW file)

#### `compose-perf.yaml` (FR-002)
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.54.0
    container_name: auth-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./tests/perf/prometheus.yml:/etc/prometheus/prometheus.yml
    extra_hosts:
      - "host.docker.internal:host-gateway"
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--web.enable-remote-write-receiver'
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.2.0
    container_name: auth-grafana
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_AUTH_ANONYMOUS_ENABLED=true
    volumes:
      - ./tests/perf/grafana/provisioning:/etc/grafana/provisioning
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus
    restart: unless-stopped

volumes:
  grafana_data:
    driver: local
```

### 3.3 K6 Test Design

#### Shared Helpers (`tests/perf/helpers/`)

```javascript
// auth.js — Token management for authenticated endpoints
import { BASE_URL, DEFAULT_HEADERS } from '../../load/helpers.js';

export function getAuthToken(username, password) { /* reuse login logic */ }
export function authHeaders(token) { /* return headers with Bearer token */ }
export { BASE_URL, DEFAULT_HEADERS };
```

```javascript
// metrics.js — Custom metrics definitions
import { Trend, Rate, Counter } from 'k6/metrics';

// Per-endpoint metrics (used in single API tests)
export const endpointLatency = new Trend('endpoint_latency', true);
export const endpointErrors = new Rate('endpoint_errors');

// Chain-level metrics
export const chainDuration = new Trend('chain_duration', true);
export const chainSuccess = new Rate('chain_success');

// System metrics
export const tpsCounter = new Counter('total_transactions');
```

#### K6 Executor Strategy

| Test Type | Executor | Config |
|-----------|----------|--------|
| Single API | constant-arrival-rate | 100 req/s, 30s, preAlloc 50 VUs |
| Chain | per-vu-iterations | 50 VUs, 10 iterations each |
| Full Load | ramping-vus | 0→100→500→1000 VUs, 10min |
| Stress | ramping-arrival-rate | 100→3000 req/s, step 2min |
| Soak | constant-vus | 200 VUs, 1h (configurable) |
| Infra Baseline | ramping-arrival-rate | 100→10000 req/s, health endpoint |

### 3.4 JMH Benchmark Design

All new benchmarks follow existing pattern from `EncryptionBenchmark.kt`:

```kotlin
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
open class <Name>Benchmark {
    // @Setup → prepare test data
    // @Benchmark → measure operation
    // Blackhole.consume() → prevent dead code elimination
}
```

| Benchmark | Operations | Key Setup |
|-----------|-----------|-----------|
| BCryptBenchmark | hashPassword, verifyPassword | BCrypt.gensalt(12) |
| JwtBenchmark | signJwtRS256, verifyJwtRS256 | RSA key pair from test resources |
| RedisCacheBenchmark | serializeJackson, serializeJdk, deserializeJackson, deserializeJdk | GenericJackson2JsonRedisSerializer + JdkSerializationRedisSerializer + representative payload |

### 3.5 Gradle Tasks (build.gradle.kts additions)

```kotlin
// New Gradle tasks for perf test suite
tasks.register<Exec>("k6PerfSingle") {
    group = "Performance"
    description = "Run K6 Single API Tests"
    workingDir = file("tests/perf")
    commandLine("docker", "run", "--rm", "-i",
        "-v", "${workingDir}:/scripts",
        "--network", "host",
        "grafana/k6", "run",
        "--out", "experimental-prometheus-rw",
        "/scripts/scenarios/single/auth_p0.js")
}

// Similar tasks for: k6PerfChains, k6PerfLoad, k6PerfStress, k6PerfSoak
```

## 4. Data Flow

```
Engineer → K6 Scripts → HTTP → Auth Service
                                  │
                           ┌──────┴──────┐
                           ▼              ▼
                     PostgreSQL        Redis
                     (HikariCP)       (Lettuce)
                           │              │
                           └──────┬──────┘
                                  ▼
                         /actuator/prometheus
                                  │
                           ┌──────┴──────┐
                           ▼              ▼
                      Prometheus     K6 remote-write
                           │
                           ▼
                        Grafana → Dashboard → Analysis → Report
```

## 5. Dependencies

| Dependency | Source | Status |
|-----------|--------|--------|
| `micrometer-registry-prometheus` | `base-observability-starter` | ✅ Already available |
| `spring-boot-starter-actuator` | `build.gradle.kts:19` | ✅ Already available |
| JMH plugin `me.champeau.jmh` | `build.gradle.kts:4` | ✅ Already available |
| `grafana/k6` Docker image | Docker Hub | ✅ Already used |
| `prom/prometheus:v2.54.0` | Docker Hub | 🆕 New container |
| `grafana/grafana:11.2.0` | Docker Hub | 🆕 New container |
| `tests/load/helpers.js` | Archive implementation | ✅ Reuse |
