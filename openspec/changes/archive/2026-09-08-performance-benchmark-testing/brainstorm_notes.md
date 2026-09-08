---
type: brainstorm_notes
change: performance-benchmark-testing
date: 2026-09-08
selected_direction: "Approach 2 — Bottom-Up Layered with Observability-First"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Performance Benchmark Testing — Full TPS Evaluation

## Date
2026-09-08

## Context

Mở rộng framework kiểm thử hiệu năng auth-service từ nền tảng archive (10 FRs: datasource-proxy, WireMock, K6 basic 4 scripts, JMH 2 benchmarks) sang hệ thống benchmark TPS toàn diện (20 FRs). User yêu cầu test từng API, cụm API (chain), full system load, stress/soak testing, và đánh giá tối ưu điểm nghẽn chi tiết.

## Questions Asked & Answers

- Q1 (OQ-001): `micrometer-registry-prometheus` đã có trong `base-observability-starter` hay cần thêm explicit?
  → **A: ĐÃ CÓ SẴN** — `base-observability-starter/build.gradle.kts` line 11: `implementation("io.micrometer:micrometer-registry-prometheus")`. Không cần thêm dependency vào auth-service. Chỉ cần config actuator expose endpoint `prometheus`.

- Q2 (OQ-002): Production traffic distribution 60/20/10/5/5 có phù hợp không?
  → **A: Bổ sung access log config** — User muốn cấu hình Tomcat access log để thu thập data thực tế, dùng làm input cho traffic distribution ratio. Approach: enable access log → phân tích endpoint distribution → adjust ratios.

- Q3 (OQ-003): Test environment specs cho HikariCP pool-size?
  → **A: 2 profiles** — 4-core/8GB RAM và 8-core/32GB RAM. User yêu cầu công thức hướng dẫn cách tính.

---

## Deep Analysis — HikariCP Pool-Size Formula

### Công thức chuẩn (HikariCP Wiki)

```
connections = (core_count × 2) + effective_spindle_count
```

> **Giải thích:** Mỗi CPU core có thể xử lý 2 I/O-bound threads đồng thời (1 running + 1 waiting for I/O). `effective_spindle_count` = số physical disk spindles cho random I/O. Với SSD, giá trị này ≈ 0-1.

### Profile 1: 4-core / 8GB RAM (Development / Staging)

```
┌────────────────────────────────────────────────────┐
│  DB Server: 4 cores, 8GB RAM, SSD                  │
│                                                    │
│  Formula: connections = (4 × 2) + 1 = 9            │
│                                                    │
│  Recommended Config:                               │
│  ┌──────────────────────────────────────────────┐  │
│  │ maximum-pool-size: 10                        │  │
│  │ minimum-idle: 10                             │  │
│  │ connection-timeout: 30000 (30s)              │  │
│  │ idle-timeout: 600000 (10min)                 │  │
│  │ max-lifetime: 1800000 (30min)                │  │
│  │ leak-detection-threshold: 30000 (30s)        │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  Memory Budget:                                    │
│  - Per connection overhead: ~10MB shared_buffers    │
│  - 10 connections × 10MB ≈ 100MB                   │
│  - Available for data: 8GB - 100MB ≈ 7.9GB         │
│  - shared_buffers recommendation: 2GB (25% of RAM) │
│                                                    │
│  Expected Capacity:                                │
│  - Concurrent queries: ~10                         │
│  - With query avg 5ms: ~2000 queries/sec          │
│  - Bottleneck likely: CPU-bound at 4 cores         │
└────────────────────────────────────────────────────┘
```

### Profile 2: 8-core / 32GB RAM (Production)

```
┌────────────────────────────────────────────────────┐
│  DB Server: 8 cores, 32GB RAM, SSD                 │
│                                                    │
│  Formula: connections = (8 × 2) + 1 = 17           │
│                                                    │
│  Recommended Config:                               │
│  ┌──────────────────────────────────────────────┐  │
│  │ maximum-pool-size: 20                        │  │
│  │ minimum-idle: 20                             │  │
│  │ connection-timeout: 30000 (30s)              │  │
│  │ idle-timeout: 600000 (10min)                 │  │
│  │ max-lifetime: 1800000 (30min)                │  │
│  │ leak-detection-threshold: 30000 (30s)        │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  Memory Budget:                                    │
│  - Per connection overhead: ~10MB shared_buffers    │
│  - 20 connections × 10MB ≈ 200MB                   │
│  - Available for data: 32GB - 200MB ≈ 31.8GB       │
│  - shared_buffers recommendation: 8GB (25% of RAM) │
│  - effective_cache_size: 24GB (75% of RAM)         │
│                                                    │
│  Expected Capacity:                                │
│  - Concurrent queries: ~20                         │
│  - With query avg 5ms: ~4000 queries/sec          │
│  - Headroom for burst: max_connections=100 ở PG    │
│  - ⚠️ QUAN TRỌNG: Nếu có multiple service         │
│    instances, tổng pool phải < max_connections     │
│    Ví dụ: 3 instances × 20 = 60 < 100 PG max     │
└────────────────────────────────────────────────────┘
```

### Config Recommendation (env-variable driven)

```yaml
# application-db.yml — Spring profile agnostic
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:10}      # Dev: 10, Prod: 20
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

### Tính nhanh cho environment bất kỳ

```
╔══════════════════════════════════════════════════════════╗
║  HIKARI POOL-SIZE CALCULATOR                            ║
╠══════════════════════════════════════════════════════════╣
║                                                          ║
║  Step 1: Lấy DB core count (vCPU hoặc physical cores)   ║
║          $ nproc  (trên DB server)                       ║
║                                                          ║
║  Step 2: Xác định disk type                              ║
║          SSD → spindle_count = 1                         ║
║          HDD 1 disk → spindle_count = 1                  ║
║          HDD RAID-10 4 disks → spindle_count = 4         ║
║                                                          ║
║  Step 3: pool_size = (cores × 2) + spindle_count         ║
║                                                          ║
║  Step 4: Round up to nearest 5 (practical buffer)        ║
║                                                          ║
║  Step 5: Verify constraint:                              ║
║          num_instances × pool_size < PG max_connections   ║
║                                                          ║
║  Examples:                                               ║
║  ┌────────┬──────────┬──────┬──────────┬────────────┐    ║
║  │ Cores  │ Disk     │ Calc │ Round Up │ Pool Size  │    ║
║  ├────────┼──────────┼──────┼──────────┼────────────┤    ║
║  │ 2      │ SSD      │ 5    │ 5        │ 5          │    ║
║  │ 4      │ SSD      │ 9    │ 10       │ 10         │    ║
║  │ 8      │ SSD      │ 17   │ 20       │ 20         │    ║
║  │ 16     │ SSD      │ 33   │ 35       │ 35         │    ║
║  │ 4      │ HDD×4    │ 12   │ 15       │ 15         │    ║
║  └────────┴──────────┴──────┴──────────┴────────────┘    ║
╚══════════════════════════════════════════════════════════╝
```

---

## Deep Analysis — Access Log Configuration

### Hiện trạng
- **Không có** Tomcat access log config trong `application.yml`
- Không có endpoint distribution data
- Traffic ratios (60/20/10/5/5) hiện là **ước lượng**

### Đề xuất: Enable Access Log + Analysis

```yaml
# application-core-observability.yml — Bổ sung
server:
  tomcat:
    accesslog:
      enabled: true
      directory: logs
      prefix: access
      suffix: .log
      pattern: "%h %t \"%r\" %s %b %D"  # %D = response time ms
      rotate: true
      max-days: 7
```

### Workflow phân tích traffic distribution

```
┌───────────────────────────────────────────────────────┐
│  Traffic Distribution Analysis Workflow                │
│                                                       │
│  Step 1: Enable access log (config above)             │
│     ↓                                                 │
│  Step 2: Run production/staging cho 24h               │
│     ↓                                                 │
│  Step 3: Parse logs — group by endpoint               │
│     $ awk '{print $6, $7}' access.log                 │
│     | sort | uniq -c | sort -rn                       │
│     ↓                                                 │
│  Step 4: Calculate percentages                        │
│     $ <result> | awk '{sum+=$1} END {                 │
│       for (i=1; i<=NR; i++)                           │
│         printf "%5.1f%% %s\n", $1/sum*100, $2        │
│     }'                                                │
│     ↓                                                 │
│  Step 5: Update K6 mixed workload ratios              │
│     Nếu real = 70/15/8/4/3 → dùng thay 60/20/10/5/5 │
│     ↓                                                 │
│  Step 6: Re-run load test with real ratios            │
└───────────────────────────────────────────────────────┘
```

### Tạm thời: Sử dụng configurable ratios

```javascript
// tests/perf/config/env.js
export const TRAFFIC_MIX = {
    auth: parseFloat(__ENV.MIX_AUTH || '60'),      // Login + Refresh
    profile: parseFloat(__ENV.MIX_PROFILE || '20'), // Profile access
    register: parseFloat(__ENV.MIX_REGISTER || '10'), // Registration
    admin: parseFloat(__ENV.MIX_ADMIN || '5'),      // Admin ops
    mfa_sso: parseFloat(__ENV.MIX_MFA_SSO || '5'),  // MFA + SSO
};
```

→ Khi có access log data thực, chỉ cần thay env variables:
```bash
K6_MIX_AUTH=70 K6_MIX_PROFILE=15 ... k6 run full_load.js
```

---

## Approaches Considered

### Approach 1: Monolithic Test Suite — All-in-One Scripts

```
┌──────────────────────────────────────────┐
│  Single Directory: tests/perf/           │
│  ┌──────────────────────────────────┐    │
│  │  perf_suite.js (1 giant script)  │    │
│  │  - All 23 endpoints              │    │
│  │  - All chains                    │    │
│  │  - All system tests              │    │
│  │  - Switchable via env vars       │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

- **Pros:**
  - Simple — 1 file, 1 command
  - Shared state across scenarios
  - Easy CI/CD integration (1 Gradle task)

- **Cons:**
  - 🔴 Unmaintainable — 2000+ lines single file
  - 🔴 Không thể chạy partial test (must run all)
  - 🔴 Hard to debug — failure ở scenario nào?
  - 🟡 K6 module import overhead grows

**Verdict: ❌ REJECTED** — Không phù hợp cho 23 endpoints + 8 chains + 4 system tests

---

### Approach 2: Bottom-Up Layered with Observability-First ⭐ SELECTED

```
┌──────────────────────────────────────────────────────────────┐
│                     TEST EXECUTION ORDER                      │
│                                                              │
│  Layer 1: Infrastructure Baseline (JMH + infra_baseline.js)  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ BCrypt → JWT → AES → Jackson → Redis → DB Pool → HTTP│    │
│  └──────────────────────────────────────────────────────┘    │
│           ↓ Upper bounds established                         │
│  Layer 2: Single API Tests (tests/perf/scenarios/single/)    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ login.js → refresh.js → validate.js → profile.js     │    │
│  │ register.js → mfa.js → sso.js → rbac.js → ...        │    │
│  └──────────────────────────────────────────────────────┘    │
│           ↓ Per-endpoint baselines established               │
│  Layer 3: API Chain Tests (tests/perf/scenarios/chains/)     │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ auth_basic.js → auth_mfa.js → registration.js        │    │
│  │ sso.js → session_mgmt.js → key_exchange.js            │    │
│  │ admin.js → anon_to_auth.js                            │    │
│  └──────────────────────────────────────────────────────┘    │
│           ↓ User journeys validated                          │
│  Layer 4: System Tests (tests/perf/scenarios/system/)        │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ full_load.js → stress.js → soak.js                    │    │
│  └──────────────────────────────────────────────────────┘    │
│           ↓ System capacity known                            │
│  Layer 5: Report & Optimize                                  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Grafana analysis → Bottleneck → Fix → Re-test         │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

- **Pros:**
  - ✅ Systematic — mỗi layer validate trước khi lên layer tiếp theo
  - ✅ Isolation — failure ở 1 script không block scripts khác
  - ✅ Composable — chạy 1 layer hoặc combination
  - ✅ Maintainable — mỗi script < 200 lines
  - ✅ Observability-first — Prometheus/Grafana setup TRƯỚC tests
  - ✅ Configurable — traffic mix, pool size, SLA via env variables

- **Cons:**
  - 🟡 Nhiều files (20+ K6 scripts)
  - 🟡 Cần Gradle tasks cho mỗi layer
  - 🟡 Initial setup time dài hơn Approach 1

**Verdict: ✅ SELECTED** — Best fit cho mục tiêu "test từng API, cụm API, full system"

---

### Approach 3: Test Matrix + Parameterized Scripts

```
┌──────────────────────────────────────────┐
│  Template-Based Scripts                  │
│  ┌──────────────────────────────────┐    │
│  │  single_api_template.js          │    │
│  │  chain_template.js               │    │
│  │  system_template.js              │    │
│  └──────────────────────────────────┘    │
│  + test_matrix.json (defines all cases)  │
│  + runner.js (reads matrix, runs tests)  │
└──────────────────────────────────────────┘
```

- **Pros:**
  - DRY — templates reused
  - Easy to add new endpoints (just add to matrix)
  - CI/CD can parallelize matrix

- **Cons:**
  - 🔴 K6 doesn't natively support dynamic scenario import from JSON
  - 🟡 Extra complexity — template engine + runner
  - 🟡 Harder to debug specific test failures
  - 🟡 Customization per endpoint difficult (auth flow vs profile vs admin)

**Verdict: ⚠️ PARTIAL ADOPTION** — Dùng concept `thresholds.js` shared config + `env.js` parameterization, nhưng giữ dedicated scripts per endpoint group thay vì template engine

---

## Selected Direction

**Approach 2: Bottom-Up Layered with Observability-First**

### Reasoning

1. **User yêu cầu rõ ràng:** "test từng API và theo từng cụm API cũng như test base core hệ thống" → Layered approach map trực tiếp
2. **Observability-first:** Prometheus/Grafana setup TRƯỚC tests → có data visual ngay từ đầu
3. **Incremental:** Không cần implement tất cả 20 FRs cùng lúc — 5 phases
4. **Existing foundation:** Archive đã có K6 basic + JMH → chỉ extend, không rewrite

### Phasing Strategy (Updated)

```
Phase 1 — Observability (FR-001, FR-002, FR-014, FR-018)
├── Config actuator expose prometheus endpoint (OQ-001 resolved: đã có dep)
├── compose-perf.yaml (Prometheus + Grafana)
├── Grafana dashboard provisioning
├── Access log config cho traffic analysis (OQ-002 addition)
└── Metrics histogram percentiles config
     Duration: 2 days

Phase 2 — Infrastructure Tuning (FR-003, FR-004, FR-020)
├── HikariCP explicit config (OQ-003: 2 profiles env-var driven)
├── JVM ZGC flags documentation
└── Virtual thread pinning detection
     Duration: 1 day

Phase 3 — K6 Test Expansion (FR-005→009, FR-013, FR-016, FR-019)
├── Shared thresholds.js + env.js
├── Infrastructure baseline script
├── Single API tests (P0: login, refresh, validate, register, mfa/verify, internal/validate)
├── Single API tests (P1: session, sso, captcha, key-exchange, rbac)
├── Chain tests (P0: auth_basic, auth_mfa, registration)
├── Chain tests (P1: sso, session_mgmt, key_exchange, admin)
├── Chain tests (P2: anon_to_auth)
├── Full load test (mixed workload, configurable ratios)
├── Stress test (breaking point detection)
├── Soak test (1h CI/CD, 4h manual)
└── K6 → Prometheus remote write
     Duration: 5 days

Phase 4 — JMH Expansion (FR-010, FR-011, FR-012)
├── BCryptBenchmark (strength 12)
├── JwtBenchmark (RS256 sign + verify)
└── RedisCacheBenchmark (Jackson vs JDK vs Kryo serializers)
     Duration: 2 days

Phase 5 — Documentation (FR-015, FR-017)
├── Performance report template (Markdown)
└── Bottleneck analysis checklist (USE Method)
     Duration: 1 day
```

### Architecture Decision: File Structure

```
tests/
├── load/                          # EXISTING (giữ nguyên, backward compatible)
│   ├── auth_flow.js               # Archive FR-003 — keep as-is
│   ├── cache_benchmark.js         # Archive FR-010 — keep as-is
│   ├── helpers.js                 # Archive — keep as-is
│   └── profile_flow.js            # Archive — keep as-is
│
└── perf/                          # NEW (all new work goes here)
    ├── config/
    │   ├── thresholds.js          # FR-016: Centralized SLA definitions
    │   └── env.js                 # Environment config + traffic mix ratios
    ├── helpers/
    │   ├── auth.js                # Login, token management utilities
    │   ├── data.js                # Test data generators (SharedArray)
    │   └── metrics.js             # Custom K6 metrics definitions
    ├── scenarios/
    │   ├── infra_baseline.js      # FR-019: Infrastructure capacity test
    │   ├── single/                # FR-005: Per-endpoint tests
    │   │   ├── auth_p0.js         # login, refresh, validate, register
    │   │   ├── auth_p1.js         # session, mfa, sso, captcha
    │   │   ├── internal.js        # internal/validate, events, rate-limit-admin
    │   │   └── rbac.js            # roles, users, permissions, policies
    │   ├── chains/                # FR-006: API chain tests
    │   │   ├── auth_basic.js      # Login→Profile→Refresh→Logout
    │   │   ├── auth_mfa.js        # Login→MFA Challenge→Verify→Profile
    │   │   ├── registration.js    # Register→Verify→Login→Profile
    │   │   ├── sso.js             # SSO Init→Callback→Profile
    │   │   ├── session_mgmt.js    # Login→List Sessions→Revoke
    │   │   ├── key_exchange.js    # Init Key→Encrypted Request→Response
    │   │   ├── admin.js           # Login(Admin)→List Users→Assign Role
    │   │   └── anon_to_auth.js    # Anon Token→Browse→Register→Promote
    │   └── system/                # FR-007, FR-008, FR-009
    │       ├── full_load.js       # Mixed workload, configurable ratios
    │       ├── stress.js          # Breaking point detection
    │       └── soak.js            # Memory leak detection (1h/4h)
    ├── prometheus.yml             # FR-002: Prometheus scrape config
    ├── grafana/
    │   └── provisioning/
    │       ├── datasources/
    │       │   └── prometheus.yml # FR-002: Auto-provision datasource
    │       └── dashboards/
    │           ├── perf-overview.json  # FR-014: Main dashboard
    │           └── api-detail.json    # FR-014: Per-endpoint detail
    └── reports/
        ├── report_template.md     # FR-015: Structured report template
        └── bottleneck_checklist.md # FR-017: USE Method checklist
```

### Key Decision: Single API Scripts Grouping

**Quyết định:** Group endpoints by priority/domain thay vì 1-file-per-endpoint.

**Lý do:**
- 23 endpoints → 23 files = quá nhiều file overhead
- Group theo domain: auth_p0.js (4 critical endpoints), auth_p1.js (4 secondary), internal.js (3), rbac.js (4)
- Mỗi file dùng K6 `scenarios` object → multiple scenarios within 1 file
- Vẫn có per-endpoint metrics isolation via `tags: { endpoint: 'login' }`

---

## Pre-classifications (preliminary)
- Feature type: EXTEND (confirmed — archive foundation exists)
- Flow type: Command (test scripts → results)
- Affected modules:
  - `tests/perf/` (NEW — all K6 scripts)
  - `src/jmh/kotlin/` (EXTEND — 3 new benchmarks)
  - `src/main/resources/` (MODIFY — actuator + HikariCP config)
  - `compose-perf.yaml` (NEW — observability containers)
  - `build.gradle.kts` (MODIFY — new Gradle tasks)

---

## GitNexus Findings (not explored)

Feature là pure testing infrastructure → GitNexus context không critical. Controller/DTO scan đã thực hiện qua grep trong pre-openspec phase.

---

## Open Questions for Design Phase

- [RESOLVED] OQ-001: micrometer-registry-prometheus → ĐÃ CÓ trong base-observability-starter
- [RESOLVED] OQ-002: Traffic distribution → Enable access log + configurable env vars cho K6 ratios
- [RESOLVED] OQ-003: HikariCP pool-size → 2 profiles (10 cho 4-core, 20 cho 8-core) + formula documented
- [OPEN] Single API grouping: auth_p0.js vs auth_p1.js split — confirm P0/P1 priority assignment phù hợp
- [OPEN] Soak test duration: Fixed 1h cho CI/CD gate, configurable ENV cho manual runs (1h-4h)

## Open Questions for URD Analysis
- N/A — URD analysis completed in pre_openspec phase
