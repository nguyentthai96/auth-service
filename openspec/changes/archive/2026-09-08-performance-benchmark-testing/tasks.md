<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: performance-benchmark-testing

> **Profile**: Command | N/A | EXTEND
> **FRs**: 21 (FR-001 → FR-021)
> **Phases**: 5 (Observability → Infrastructure → K6 → JMH → Documentation)

---

## Phase 1: Observability Stack (Day 1-2)

- [x] **Task 1: Actuator Prometheus endpoint config**
  - File: `src/main/resources/application-core-observability.yml` | Action: [MODIFY]
  - FR: FR-001 — Expose `/actuator/prometheus` endpoint
  - Change: Add `prometheus` to `management.endpoints.web.exposure.include` (line 5: `health,info,metrics` → `health,info,metrics,prometheus`). Add `endpoint.prometheus.enabled: true`
  - Dependencies: `micrometer-registry-prometheus` already in `base-observability-starter/build.gradle.kts:11` — NO new dependency
  - Verify: `curl http://localhost:8081/actuator/prometheus` returns Prometheus text format

- [x] **Task 2: Metrics histogram + application tag config**
  - File: `src/main/resources/application-core-observability.yml` | Action: [MODIFY]
  - FR: FR-018 — Micrometer percentile histograms for `http.server.requests`
  - Change: Add `management.metrics.tags.application: auth-service`, `distribution.percentiles-histogram.http.server.requests: true`, `distribution.percentiles.http.server.requests: 0.5,0.95,0.99`
  - Pattern: YAML append, no existing keys modified
  - Verify: Prometheus query `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` returns valid value

- [x] **Task 3: Tomcat access log config**
  - File: `src/main/resources/application-core-observability.yml` | Action: [MODIFY]
  - FR: FR-021 — Enable Tomcat access log for traffic distribution analysis
  - Change: Add `server.tomcat.accesslog.enabled: ${ACCESS_LOG_ENABLED:false}`, pattern `"%h %t \"%r\" %s %b %D"`, rotate true, max-days 7
  - Pattern: Default disabled, enable via env var for perf testing sessions
  - Verify: Set `ACCESS_LOG_ENABLED=true`, restart, check `logs/access.log` created with request entries

- [x] **Task 4: Prometheus scrape config**
  - File: `tests/perf/prometheus.yml` | Action: [NEW]
  - FR: FR-002 — Prometheus configuration to scrape auth-service
  - Content: Global scrape_interval 15s, job `auth-service` target `host.docker.internal:8081`, metrics_path `/actuator/prometheus`. Enable remote write receiver
  - Verify: Prometheus UI `/targets` shows auth-service target as UP

- [x] **Task 5: Grafana datasource provisioning**
  - File: `tests/perf/grafana/provisioning/datasources/prometheus.yml` | Action: [NEW]
  - FR: FR-002 — Auto-provision Prometheus datasource in Grafana
  - Content: apiVersion 1, datasource type prometheus, url `http://prometheus:9090`, isDefault true
  - Verify: Grafana UI → Configuration → Data Sources shows Prometheus auto-provisioned

- [x] **Task 6: Docker Compose perf extension**
  - File: `compose-perf.yaml` | Action: [NEW]
  - FR: FR-002 — Prometheus + Grafana containers
  - Content: prometheus service (prom/prometheus:v2.54.0, port 9090, volumes prometheus.yml, extra_hosts, --web.enable-remote-write-receiver), grafana service (grafana/grafana:11.2.0, port 3001, volumes provisioning/, depends_on prometheus). Both restart: unless-stopped
  - Dependencies: Must run with `docker compose -f compose.yaml -f compose-perf.yaml up -d`
  - Verify: Both containers healthy within 30s

- [x] **Task 7: Grafana dashboard provider config**
  - File: `tests/perf/grafana/provisioning/dashboards/dashboard.yml` | Action: [NEW]
  - FR: FR-014 — Dashboard auto-provisioning config
  - Content: apiVersion 1, provider name 'performance', folder 'Performance', path `/var/lib/grafana/dashboards`, disableDeletion false

- [x] **Task 8: Grafana perf-overview dashboard**
  - File: `tests/perf/grafana/provisioning/dashboards/perf-overview.json` | Action: [NEW]
  - FR: FR-014 — Main performance dashboard
  - Content: 4 rows — Row 1: TPS over time + Latency percentiles (p50/p95/p99), Row 2: CPU + JVM Memory + GC Pauses, Row 3: HikariCP pool status + Redis commands/sec + Thread pool, Row 4: Per-endpoint latency heatmap
  - Panels use Prometheus queries (e.g., `rate(http_server_requests_seconds_count[1m])` for TPS)

- [x] **Task 9: Grafana api-detail dashboard**
  - File: `tests/perf/grafana/provisioning/dashboards/api-detail.json` | Action: [NEW]
  - FR: FR-014 — Per-endpoint detail dashboard
  - Content: Variable dropdown for endpoint URI, panels for latency distribution, error rate, throughput, DB pool correlation

---

## Phase 2: Infrastructure Tuning (Day 3)

- [x] **Task 10: HikariCP explicit configuration**
  - File: `src/main/resources/application-db.yml` | Action: [MODIFY]
  - FR: FR-003 — Explicit pool config with env vars
  - Change: Add `spring.datasource.hikari` section after `driver-class-name` (line 6). Keys: `maximum-pool-size: ${DB_POOL_MAX:10}`, `minimum-idle: ${DB_POOL_MIN:${DB_POOL_MAX:10}}`, `connection-timeout: ${DB_CONN_TIMEOUT:30000}`, `idle-timeout: ${DB_IDLE_TIMEOUT:600000}`, `max-lifetime: ${DB_MAX_LIFETIME:1800000}`, `pool-name: AuthServicePool`, `leak-detection-threshold: ${DB_LEAK_THRESHOLD:30000}`, `data-source-properties.reWriteBatchedInserts: true`, `data-source-properties.prepareThreshold: 5`
  - Formula: `pool_size = (DB_cores × 2) + spindle_count` → 4-core/SSD=10, 8-core/SSD=20
  - Verify: Start app, confirm `HikariPool-1 - Pool stats (total=10, active=0, idle=10, waiting=0)` in logs

- [x] **Task 11: JVM flags documentation**
  - File: `tests/perf/docs/jvm-flags.md` | Action: [NEW]
  - FR: FR-004 — Document JVM performance flags
  - Content: ZGC config (`-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1024m -XX:+AlwaysPreTouch`), heap dump (`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdumps`), virtual threads (`-Djdk.tracePinnedThreads=full`), example `JAVA_OPTS` export command

- [x] **Task 12: Virtual thread pinning guide**
  - File: `tests/perf/docs/virtual-thread-pinning.md` | Action: [NEW]
  - FR: FR-020 — Detection + fix documentation
  - Content: What is pinning, JVM flag to enable, sample log output, how to fix (replace `synchronized` with `ReentrantLock`), common offenders in Spring ecosystem

---

## Phase 3: K6 Test Expansion (Day 4-8)

- [x] **Task 13: K6 shared thresholds config**
  - File: `tests/perf/config/thresholds.js` | Action: [NEW]
  - FR: FR-016 — Centralized SLA definitions
  - Content: Export `SLA` object with per-category thresholds: `login: {p95: 200, p99: 500}`, `refresh: {p95: 150}`, `validate: {p95: 50}`, `profile: {p95: 100}`, `crud: {p95: 500}`, `authChain: {p95: 800}`, `system: {targetTPS: 500, maxP99: 1000}`
  - Pattern: Reusable across all K6 scripts via `import { SLA } from '../config/thresholds.js'`

- [x] **Task 14: K6 environment config**
  - File: `tests/perf/config/env.js` | Action: [NEW]
  - FR: FR-007 support — Environment + traffic mix configuration
  - Content: Export `BASE_URL` (from env or default), `TRAFFIC_MIX` object (auth/profile/register/admin/mfa_sso ratios from env vars with defaults 60/20/10/5/5), `SOAK_DURATION` from env

- [x] **Task 15: K6 auth helpers**
  - File: `tests/perf/helpers/auth.js` | Action: [NEW]
  - FR: FR-005, FR-006 support — Shared auth utilities
  - Source: Reuse logic from `tests/load/helpers.js` (`loginAndGetToken`, `BASE_URL`, `DEFAULT_HEADERS`)
  - Content: Re-export from `../../load/helpers.js` + add `authHeaders(token)` utility

- [x] **Task 16: K6 test data generators**
  - File: `tests/perf/helpers/data.js` | Action: [NEW]
  - FR: FR-005 support — Test data management
  - Content: `SharedArray` with pre-generated test users, unique username generator per VU, admin credentials

- [x] **Task 17: K6 custom metrics definitions**
  - File: `tests/perf/helpers/metrics.js` | Action: [NEW]
  - FR: FR-005, FR-006 support — Custom K6 metrics
  - Content: `Trend` per-endpoint latency, `Rate` per-endpoint errors, `Trend` chain duration, `Rate` chain success, `Counter` total transactions

- [x] **Task 18: K6 infrastructure baseline test**
  - File: `tests/perf/scenarios/infra_baseline.js` | Action: [NEW]
  - FR: FR-019 — Infrastructure capacity measurement
  - Content: 3 scenarios — (1) Health endpoint max TPS (ramping-arrival-rate to 10000 req/s), (2) DB pool saturation (concurrent queries exceeding pool-size), (3) Redis throughput (GET/SET operations)
  - Verify: Health TPS ≥5000 req/s

- [x] **Task 19: K6 single API tests — auth P0**
  - File: `tests/perf/scenarios/single/auth_p0.js` | Action: [NEW]
  - FR: FR-005 — P0 auth endpoints
  - Content: 4 scenarios — `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/validate`, `POST /api/v1/auth/register`
  - Executor: constant-arrival-rate, 100 req/s, 30s
  - Thresholds: Import from `thresholds.js`, P95 <200ms for login/refresh/validate

- [x] **Task 20: K6 single API tests — auth P1**
  - File: `tests/perf/scenarios/single/auth_p1.js` | Action: [NEW]
  - FR: FR-005 — P1 auth endpoints
  - Content: 4 scenarios — `GET /api/v1/auth/sessions`, `POST /api/v1/auth/mfa/verify`, `GET /api/v1/auth/sso/login`, `GET /api/v1/auth/captcha/challenge`

- [x] **Task 21: K6 single API tests — internal**
  - File: `tests/perf/scenarios/single/internal.js` | Action: [NEW]
  - FR: FR-005 — Internal + admin endpoints
  - Content: 3 scenarios — `POST /api/v1/internal/validate` (P0), `GET /api/v1/events` (P2), `GET /api/v1/admin/rate-limits` (P2)

- [x] **Task 22: K6 single API tests — RBAC**
  - File: `tests/perf/scenarios/single/rbac.js` | Action: [NEW]
  - FR: FR-005 — RBAC endpoints
  - Content: 4 scenarios — `GET /api/v1/roles`, `GET /api/v1/users`, `POST /api/v1/roles/{id}/permissions`, `GET /api/v1/policies`

- [x] **Task 23: K6 chain — auth basic (P0)**
  - File: `tests/perf/scenarios/chains/auth_basic.js` | Action: [NEW]
  - FR: FR-006 — Auth Basic chain: Login → Profile → Refresh Token → Logout
  - Content: Sequential chain per VU, token correlation between steps, per-step latency tracking, chain success rate
  - Executor: per-vu-iterations, 50 VUs, 10 iterations

- [x] **Task 24: K6 chain — auth MFA (P0)**
  - File: `tests/perf/scenarios/chains/auth_mfa.js` | Action: [NEW]
  - FR: FR-006 — Auth+MFA chain: Login → MFA Challenge → MFA Verify → Profile

- [x] **Task 25: K6 chain — registration (P0)**
  - File: `tests/perf/scenarios/chains/registration.js` | Action: [NEW]
  - FR: FR-006 — Registration chain: Register → Verify Email → Login → Profile
  - Note: Needs unique username per VU via `data.js` SharedArray

- [x] **Task 26: K6 chain — SSO (P1)**
  - File: `tests/perf/scenarios/chains/sso.js` | Action: [NEW]
  - FR: FR-006 — SSO chain: SSO Init → OAuth2 Callback → Profile
  - Note: SSO callback may need WireMock mock (archive already has WireMock setup)

- [x] **Task 27: K6 chain — session management (P1)**
  - File: `tests/perf/scenarios/chains/session_mgmt.js` | Action: [NEW]
  - FR: FR-006 — Session chain: Login → List Sessions → Revoke Session

- [x] **Task 28: K6 chain — key exchange (P1)**
  - File: `tests/perf/scenarios/chains/key_exchange.js` | Action: [NEW]
  - FR: FR-006 — Key Exchange chain: Init Key Exchange → Encrypted Request → Response

- [x] **Task 29: K6 chain — admin (P1)**
  - File: `tests/perf/scenarios/chains/admin.js` | Action: [NEW]
  - FR: FR-006 — Admin chain: Login(Admin) → List Users → Assign Role → Verify

- [x] **Task 30: K6 chain — anon to auth (P2)**
  - File: `tests/perf/scenarios/chains/anon_to_auth.js` | Action: [NEW]
  - FR: FR-006 — Anonymous→Auth chain: Anon Token → Browse → Register → Promote Data

- [x] **Task 31: K6 full load test (mixed workload)**
  - File: `tests/perf/scenarios/system/full_load.js` | Action: [NEW]
  - FR: FR-007 — Mixed workload simulating production traffic
  - Content: Multi-scenario with configurable ratios from `env.js` (default 60/20/10/5/5)
  - Ramp: 0→100→500→1000 VUs over 10min
  - Thresholds: TPS ≥500, P99 <1000ms, error rate <0.1%
  - K6 output: `--out experimental-prometheus-rw` (FR-013)

- [x] **Task 32: K6 stress test**
  - File: `tests/perf/scenarios/system/stress.js` | Action: [NEW]
  - FR: FR-008 — Breaking point detection
  - Content: ramping-arrival-rate: 100→200→500→1000→2000→3000 req/s, 2min per level
  - Output: Breaking point TPS, latency degradation onset, recovery verification

- [x] **Task 33: K6 soak test**
  - File: `tests/perf/scenarios/system/soak.js` | Action: [NEW]
  - FR: FR-009 — Memory leak / resource exhaustion detection
  - Content: constant-vus, 200 VUs, duration `${SOAK_DURATION:1h}`
  - ⚠️ OPEN QUESTION: Soak test duration — Fixed 1h cho CI/CD gate, configurable ENV cho manual runs (1h-4h)
  - Monitor: JVM heap trend, connection pool stats, error rate stability

- [x] **Task 34: Gradle performance tasks**
  - File: `build.gradle.kts` | Action: [MODIFY]
  - FR: FR-013 — K6 Prometheus remote write + new tasks
  - Change: Add `k6PerfSingle`, `k6PerfChains`, `k6PerfLoad`, `k6PerfStress`, `k6PerfSoak` tasks. All use `--out experimental-prometheus-rw` flag
  - Pattern: Follow existing `k6Run` task structure (line 91-96)

---

## Phase 4: JMH Expansion (Day 9-10)

- [x] **Task 35: JMH BCrypt benchmark**
  - File: `src/jmh/kotlin/com/ntt/authservice/benchmark/BCryptBenchmark.kt` | Action: [NEW]
  - FR: FR-010 — BCrypt password hashing benchmark
  - Pattern: Follow `EncryptionBenchmark.kt` structure (@State, @BenchmarkMode(Throughput), @Fork(2))
  - Content: `hashPassword()` with BCrypt.gensalt(12), `verifyPassword()` with pre-hashed value
  - Verify: `./gradlew jmh --include BCryptBenchmark` produces `build/results/jmh/results.json`

- [x] **Task 36: JMH JWT RS256 benchmark**
  - File: `src/jmh/kotlin/com/ntt/authservice/benchmark/JwtBenchmark.kt` | Action: [NEW]
  - FR: FR-011 — JWT signing + verification benchmark
  - Content: `signJwtRS256()` with jjwt RS256, `verifyJwtRS256()` parse+verify pre-signed token
  - Dependencies: `io.jsonwebtoken:jjwt-api` (already in build.gradle.kts:39)

- [x] **Task 37: JMH Redis serialization comparison**
  - File: `src/jmh/kotlin/com/ntt/authservice/benchmark/RedisCacheBenchmark.kt` | Action: [NEW]
  - FR: FR-012 — Redis serializer comparison benchmark
  - Source: Extend pattern from `SerializationBenchmark.kt`
  - Content: `serializeJackson()`, `serializeJdk()`, `deserializeJackson()`, `deserializeJdk()` with representative payload (UserSession-like object)
  - Output: ops/sec + serialized byte size comparison

---

## Phase 5: Documentation (Day 11)

- [x] **Task 38: Performance report template**
  - File: `tests/perf/reports/report_template.md` | Action: [NEW]
  - FR: FR-015 — Structured performance report
  - Content: Sections: Executive Summary, Test Configuration (env, VUs, duration, JVM flags), Baseline Metrics (per-endpoint table), Bottleneck Analysis (USE Method), Optimization Results (before/after comparison with evidence), Recommendations (prioritized by impact)
  - Template: Markdown with placeholder sections, before/after comparison tables

- [x] **Task 39: Bottleneck analysis checklist**
  - File: `tests/perf/reports/bottleneck_checklist.md` | Action: [NEW]
  - FR: FR-017 — USE Method systematic checklist
  - Content: Per-resource table (CPU, Memory, GC, DB Pool, Redis, HTTP Server) with: metric name, Prometheus query, threshold, action if exceeded. Decision flowchart (ASCII diagram matching technical_spec.md §3.2)

---

## Summary

| Phase | Tasks | New Files | Modified Files |
|-------|-------|-----------|----------------|
| 1. Observability | 9 | 7 | 1 (`application-core-observability.yml`) |
| 2. Infrastructure | 3 | 2 | 1 (`application-db.yml`) |
| 3. K6 Expansion | 17 | 16 | 1 (`build.gradle.kts`) |
| 4. JMH Expansion | 3 | 3 | 0 |
| 5. Documentation | 2 | 2 | 0 |
| **Total** | **34** | **30** | **3** |

FR Coverage: 21/21 ✅ (FR-001 → FR-021 all mapped to tasks)
