# SRS: Performance Benchmark Testing — Full TPS Evaluation

## 1. Document Info

| Field | Value |
|-------|-------|
| Change | performance-benchmark-testing |
| Type | EXTEND |
| Flow | Command |
| FRs | 21 (16 URD + 5 Enriched) |
| Archive | 2026-08-24-tps-performance-testing (10 FRs, implemented) |

---

## 2. Functional Requirements Specification

### Phase 1: Observability Stack

#### FR-001: Prometheus metrics endpoint
- **Input**: N/A (config change)
- **Processing**: Cập nhật `application-core-observability.yml` — thêm `prometheus` vào `management.endpoints.web.exposure.include`
- **Output**: `/actuator/prometheus` endpoint trả về Prometheus text format
- **Metrics exposed**: `http_server_requests_seconds`, `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_timeout_total`, `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live_threads`, `process_cpu_usage`, `spring_data_redis_commands_seconds`, `cache_gets_total`
- **Dependency**: `micrometer-registry-prometheus` từ `base-observability-starter` (đã có sẵn)
- **Error**: N/A — config-only change

#### FR-002: Grafana + Prometheus Docker Compose
- **Input**: `compose.yaml` (existing infrastructure)
- **Processing**: Tạo `compose-perf.yaml` extend từ `compose.yaml`. Thêm:
  - `prometheus` service: `prom/prometheus:v2.54.0`, port 9090, mount `tests/perf/prometheus.yml`, `extra_hosts: host.docker.internal:host-gateway`
  - `grafana` service: `grafana/grafana:11.2.0`, port 3001, mount `tests/perf/grafana/provisioning/`, env `GF_SECURITY_ADMIN_PASSWORD=admin`, depends_on prometheus
- **Output**: `docker compose -f compose.yaml -f compose-perf.yaml up -d` starts full observability stack
- **Validation**: Prometheus UI tại `http://localhost:9090/targets` hiển thị auth-service target UP. Grafana tại `http://localhost:3001` hiển thị provisioned datasource
- **Files**:
  - `compose-perf.yaml` (NEW)
  - `tests/perf/prometheus.yml` (NEW) — scrape config
  - `tests/perf/grafana/provisioning/datasources/prometheus.yml` (NEW)

#### FR-014: Grafana performance dashboard
- **Input**: Prometheus metrics
- **Processing**: Tạo Grafana dashboard JSON files trong `tests/perf/grafana/provisioning/dashboards/`
- **Output**: 2 dashboards auto-provisioned:
  1. `perf-overview.json` — 4 rows: Request Performance (TPS, P50/P95/P99), Resource Utilization (CPU, Memory, GC), Infrastructure (HikariCP, Redis, Threads), Per-Endpoint Breakdown (latency heatmap)
  2. `api-detail.json` — Per-endpoint detail (latency distribution, error rate, throughput)
- **Files**: `tests/perf/grafana/provisioning/dashboards/perf-overview.json` (NEW), `api-detail.json` (NEW), `tests/perf/grafana/provisioning/dashboards/dashboard.yml` (NEW — provider config)

#### FR-018: Actuator metrics histogram config
- **Input**: N/A (config change)
- **Processing**: Cập nhật `application-core-observability.yml` — thêm:
  ```yaml
  management:
    metrics:
      tags:
        application: auth-service
      distribution:
        percentiles-histogram:
          http.server.requests: true
        percentiles:
          http.server.requests: 0.5,0.95,0.99
  ```
- **Output**: Prometheus histogram buckets cho `http_server_requests_seconds`. Query `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` trả về P95 chính xác
- **Error**: N/A — additive config

#### FR-021: Tomcat access log configuration
- **Input**: N/A (config change)
- **Processing**: Cập nhật `application-core-observability.yml` — thêm:
  ```yaml
  server:
    tomcat:
      accesslog:
        enabled: true
        directory: logs
        prefix: access
        suffix: .log
        pattern: "%h %t \"%r\" %s %b %D"
        rotate: true
        max-days: 7
  ```
- **Output**: Access log file `logs/access.log` chứa per-request data: source IP, timestamp, method+path, status, body size, response time (ms)
- **Usage**: Parse bằng `awk '{print $6, $7}' access.log | sort | uniq -c | sort -rn` → endpoint distribution percentages → update K6 traffic mix ratios

---

### Phase 2: Infrastructure Tuning

#### FR-003: HikariCP explicit tuning
- **Input**: N/A (config change)
- **Processing**: Cập nhật `application-db.yml` — thêm `spring.datasource.hikari` section:
  ```yaml
  hikari:
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
  ```
- **Formula**: `connections = (DB_cores × 2) + effective_spindle_count`
  - Profile 1 (4-core/8GB SSD): `pool-size=10`
  - Profile 2 (8-core/32GB SSD): `pool-size=20`
- **Constraint**: `num_instances × pool_size < PostgreSQL max_connections`
- **Output**: HikariCP pool tuned per environment. Leak detection threshold 30s
- **Error**: `HikariPool-1 - Connection not available, request timed out after 30000ms` → increase pool size or optimize query

#### FR-004: JVM performance flags
- **Input**: N/A (documentation + config)
- **Processing**: Document JVM flags profile:
  ```
  -XX:+UseZGC -XX:+ZGenerational
  -Xms512m -Xmx1024m
  -XX:+AlwaysPreTouch
  -Djdk.tracePinnedThreads=full
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=./heapdumps
  ```
- **Output**: `PERF_JVM_OPTS` env var documented. GC pause <50ms at p99 (ZGC target)
- **Files**: Documentation trong `tests/perf/docs/jvm-flags.md` (NEW)

#### FR-020: Virtual thread pinning detection
- **Input**: JVM flag `-Djdk.tracePinnedThreads=full`
- **Processing**: Document cách đọc pinning output + cách fix (replace `synchronized` with `ReentrantLock`)
- **Output**: Pinning events logged to stdout khi detect. Documentation trong `tests/perf/docs/virtual-thread-pinning.md` (NEW)

---

### Phase 3: K6 Test Expansion

#### FR-016: K6 shared SLA thresholds
- **Input**: Business rules BR-001 → BR-010
- **Processing**: Tạo `tests/perf/config/thresholds.js`:
  ```javascript
  export const SLA = {
    login: { p95: 200, p99: 500, errorRate: 0.01 },
    refresh: { p95: 150, p99: 300, errorRate: 0.01 },
    validate: { p95: 50, p99: 100, errorRate: 0.001 },
    profile: { p95: 100, p99: 200, errorRate: 0.01 },
    crud: { p95: 500, p99: 1000, errorRate: 0.01 },
    authChain: { p95: 800, p99: 1500, errorRate: 0.01 },
    system: { targetTPS: 500, maxP99: 1000, maxErrorRate: 0.001 },
  };
  ```
- **Output**: Shared import across all K6 scripts. K6 exit code 99 on SLA violation

#### FR-019: Infrastructure baseline test
- **Input**: Running auth-service + observability stack
- **Processing**: Tạo `tests/perf/scenarios/infra_baseline.js`:
  1. Health endpoint max TPS test (constant-arrival-rate → ramp to max)
  2. DB connection pool saturation test (concurrent queries > pool-size)
  3. Redis throughput test (GET/SET operations)
- **Output**: Baseline numbers: health TPS ≥5000, DB pool active/pending/timeout counts, Redis ops/sec

#### FR-005: K6 single API tests
- **Input**: 23 API endpoints from 17+ controllers
- **Processing**: Tạo 4 script groups:
  - `tests/perf/scenarios/single/auth_p0.js` — login, refresh, validate, register (P0)
  - `tests/perf/scenarios/single/auth_p1.js` — session, mfa/verify, sso, captcha (P1)
  - `tests/perf/scenarios/single/internal.js` — internal/validate, events, rate-limit-admin (P0+P2)
  - `tests/perf/scenarios/single/rbac.js` — roles, users, permissions, policies (P1+P2)
- **Output**: Per-endpoint baseline: p50/p95/p99 latency, error rate, TPS
- **Executor**: constant-arrival-rate, 100 req/s, 30s minimum (JIT warmup)
- **Helpers**: Reuse `tests/load/helpers.js` (`loginAndGetToken`, `BASE_URL`, `DEFAULT_HEADERS`)

#### FR-006: K6 API chain tests
- **Input**: Dependent API sequences
- **Processing**: Tạo 8 chain scripts:

| File | Chain | Steps | Priority |
|------|-------|-------|----------|
| `chains/auth_basic.js` | Auth Basic | Login→Profile→Refresh→Logout | P0 |
| `chains/auth_mfa.js` | Auth+MFA | Login→MFA Challenge→Verify→Profile | P0 |
| `chains/registration.js` | Registration | Register→Verify Email→Login→Profile | P0 |
| `chains/sso.js` | SSO | SSO Init→Callback→Profile | P1 |
| `chains/session_mgmt.js` | Session | Login→List Sessions→Revoke Session | P1 |
| `chains/key_exchange.js` | Key Exchange | Init Key→Encrypted Request→Response | P1 |
| `chains/admin.js` | Admin | Login(Admin)→List Users→Assign Role→Verify | P1 |
| `chains/anon_to_auth.js` | Anon→Auth | Anon Token→Browse→Register→Promote | P2 |

- **Output**: Per-chain: end-to-end latency, per-step latency, chain success rate >99%
- **Data correlation**: Token from step N → header in step N+1

#### FR-007: K6 mixed workload test
- **Input**: Traffic mix ratios (configurable via env vars)
- **Processing**: Tạo `tests/perf/scenarios/system/full_load.js` multi-scenario:
  - 60% auth (login+refresh) — `${MIX_AUTH:60}`
  - 20% profile — `${MIX_PROFILE:20}`
  - 10% registration — `${MIX_REGISTER:10}`
  - 5% admin — `${MIX_ADMIN:5}`
  - 5% MFA/SSO — `${MIX_MFA_SSO:5}`
- **Ramp pattern**: 0→100→500→1000 VUs over 10min
- **Output**: TPS saturation point, resource utilization correlation, error onset point
- **Thresholds**: TPS ≥500, P99 <1000ms, error rate <0.1%

#### FR-008: K6 stress test
- **Input**: Target TPS from FR-007
- **Processing**: Tạo `tests/perf/scenarios/system/stress.js`:
  - Executor: ramping-arrival-rate
  - Ramp: 100→200→500→1000→2000→3000 req/s
  - Hold each level 2min
- **Output**: Breaking point (TPS where error rate >1% or P99 >5s). Recovery verification after ramp-down

#### FR-009: K6 soak test
- **Input**: Sustainable TPS from FR-007
- **Processing**: Tạo `tests/perf/scenarios/system/soak.js`:
  - Executor: constant-vus
  - Duration: `${SOAK_DURATION:1h}` (CI/CD: 1h, manual: up to 4h)
  - VUs: target sustainable load (e.g., 200 VUs)
- **Output**: Memory trend analysis (JVM heap over time), connection leak detection, error rate stability

#### FR-013: K6 → Prometheus remote write
- **Input**: All K6 scripts
- **Processing**: Add `--out experimental-prometheus-rw` flag to K6 run commands. Config Prometheus with `--web.enable-remote-write-receiver`
- **Output**: K6 metrics appear in Prometheus → Grafana overlay K6 TPS with application latency
- **Files**: Update K6 docker run commands in Gradle tasks + documentation

---

### Phase 4: JMH Expansion

#### FR-010: JMH BCrypt benchmark
- **Input**: BCrypt strength=12 (production config)
- **Processing**: Tạo `src/jmh/kotlin/com/ntt/authservice/benchmark/BCryptBenchmark.kt`:
  - Benchmark `hashPassword`: `BCrypt.hashpw("password123", BCrypt.gensalt(12))`
  - Benchmark `verifyPassword`: `BCrypt.checkpw("password123", preHashed)`
- **Output**: ops/sec for hash + verify. Warmup 5 iter, measurement 5 iter, fork 2

#### FR-011: JMH JWT RS256 benchmark
- **Input**: RS256 key pair (matching production config)
- **Processing**: Tạo `src/jmh/kotlin/com/ntt/authservice/benchmark/JwtBenchmark.kt`:
  - Benchmark `signJwtRS256`: Create + sign JWT token
  - Benchmark `verifyJwtRS256`: Parse + verify JWT token
- **Output**: ops/sec for sign + verify separately

#### FR-012: JMH Redis serialization comparison
- **Input**: Representative payloads (UserSession, Permission objects)
- **Processing**: Tạo `src/jmh/kotlin/com/ntt/authservice/benchmark/RedisCacheBenchmark.kt`:
  - Benchmark `serializeJackson`: GenericJackson2JsonRedisSerializer
  - Benchmark `serializeJdk`: JdkSerializationRedisSerializer
  - Benchmark `deserializeJackson` + `deserializeJdk`
- **Output**: ops/sec + serialized byte size comparison

---

### Phase 5: Documentation

#### FR-015: Performance report template
- **Input**: Test results from Phase 3+4
- **Processing**: Tạo `tests/perf/reports/report_template.md` với sections:
  1. Executive Summary (TPS, P95, error rate headline numbers)
  2. Test Configuration (environment, VUs, duration, JVM flags)
  3. Baseline Metrics (per-endpoint table)
  4. Bottleneck Analysis (USE Method per resource)
  5. Optimization Results (before/after comparison)
  6. Recommendations (prioritized by expected impact)

#### FR-017: Bottleneck analysis checklist
- **Input**: USE Method framework
- **Processing**: Tạo `tests/perf/reports/bottleneck_checklist.md` với:

| Resource | Metric | Threshold | Action if Exceeded |
|----------|--------|-----------|-------------------|
| CPU | `process_cpu_usage` | >80% | Profile with async-profiler |
| Memory | `jvm_memory_used_bytes` / max | >85% | Increase heap, analyze allocations |
| GC | `jvm_gc_pause_seconds` p99 | >50ms | Switch to ZGC, tune heap |
| DB Pool | `hikaricp_connections_pending` | >0 sustained | Increase pool-size, optimize queries |
| Redis | `spring_data_redis_commands_seconds` p95 | >10ms | Check serializer, pipeline commands |
| HTTP | `http_server_requests_seconds` p99 | >1000ms | Profile endpoint, add caching |

---

## 3. Non-functional Requirements

| NFR-ID | Category | Requirement | Target |
|--------|----------|-------------|--------|
| NFR-001 | Performance | Auth endpoint P95 | <200ms |
| NFR-002 | Performance | Auth endpoint P99 | <500ms |
| NFR-003 | Performance | CRUD endpoint P95 | <500ms |
| NFR-004 | Performance | System-wide error rate | <0.1% |
| NFR-005 | Performance | Target sustained TPS | ≥500 req/s |
| NFR-006 | Performance | Chain success rate | >99% |
| NFR-007 | Performance | System P99 at target TPS | <1000ms |
| NFR-008 | Stability | Soak test memory leak | None (heap stable 30min) |
| NFR-009 | Usability | K6 full suite runtime | <15 min |
| NFR-010 | Usability | JMH suite runtime | <10 min |
| NFR-011 | Performance | GC pause p99 | <50ms (ZGC) |

## 4. Error Codes

N/A — Feature is testing infrastructure, no production error codes added.

## 5. FR Traceability

All 21 FRs (FR-001 → FR-021) specified above. Coverage: 21/21 (100%).
