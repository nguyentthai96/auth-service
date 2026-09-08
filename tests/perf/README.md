# 🚀 Performance Testing Guide — auth-service

> Hướng dẫn chi tiết cấu hình, khởi chạy, đánh giá và cải thiện hiệu năng hệ thống auth-service.

---

## Mục lục

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Kiến trúc Performance Testing](#2-kiến-trúc-performance-testing)
3. [Cấu hình môi trường](#3-cấu-hình-môi-trường)
4. [Khởi động hệ thống](#4-khởi-động-hệ-thống)
5. [Chạy Performance Tests](#5-chạy-performance-tests)
6. [JMH Microbenchmarks](#6-jmh-microbenchmarks)
7. [Xem kết quả & Dashboard](#7-xem-kết-quả--dashboard)
8. [Quy trình đánh giá & cải thiện](#8-quy-trình-đánh-giá--cải-thiện)
9. [Troubleshooting](#9-troubleshooting)
10. [Tham khảo](#10-tham-khảo)

---

## 1. Yêu cầu hệ thống

### Phần mềm bắt buộc

| Tool | Version | Kiểm tra |
|------|---------|----------|
| **JDK** | 21+ (với Virtual Threads) | `java --version` |
| **Docker** | 24+ | `docker --version` |
| **Docker Compose** | v2+ | `docker compose version` |
| **Gradle** | 9.x (wrapper đi kèm) | `./gradlew --version` |

> **K6 KHÔNG cần cài trên máy host** — chạy qua Docker image `grafana/k6`.

### Yêu cầu phần cứng tối thiểu

| Profile | CPU | RAM | Mục đích |
|---------|-----|-----|----------|
| **Dev/Test** | 4 cores | 8 GB | Chạy single API, chain tests |
| **Full Load** | 8 cores | 16 GB | Stress test, soak test |

> ⚠️ K6 runner, app, DB, Redis, Prometheus, Grafana đều chạy trên cùng 1 máy.
> Với stress test >1000 VUs nên tách K6 runner ra máy riêng.

---

## 2. Kiến trúc Performance Testing

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   K6 Tests   │────▶│ auth-service │────▶│  PostgreSQL  │
│  (Docker)    │     │  :8080       │     │  :5432       │
└──────┬───────┘     └──────┬───────┘     └──────────────┘
       │                    │                     
       │ remote-write       │ /actuator/          ┌──────────┐
       ▼                    │ prometheus          │  Redis   │
┌──────────────┐            ▼                     │  :6379   │
│  Prometheus  │◀───────────────────────          └──────────┘
│  :9090       │     scrape metrics
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Grafana    │  ◀─── Dashboards tự động provisioned
│  :3001       │       • perf-overview (TPS, Latency, Resources)
└──────────────┘       • api-detail (Per-endpoint drill-down)
```

### Cấu trúc thư mục `tests/perf/`

```
tests/perf/
├── config/
│   ├── thresholds.js         # SLA definitions (source of truth)
│   └── env.js                # Base URL, traffic mix, credentials
├── helpers/
│   ├── auth.js               # Login, authHeaders utilities
│   ├── data.js               # SharedArray test users, generators
│   └── metrics.js            # Custom K6 metrics
├── scenarios/
│   ├── infra_baseline.js     # Health TPS, DB pool, Redis
│   ├── single/               # Per-endpoint tests
│   │   ├── auth_p0.js        # login, refresh, validate, register
│   │   ├── auth_p1.js        # sessions, MFA, SSO, captcha
│   │   ├── internal.js       # internal validate, events, rate-limits
│   │   └── rbac.js           # roles, users, permissions, policies
│   ├── chains/               # Multi-API flow tests
│   │   ├── auth_basic.js     # Login → Profile → Refresh → Logout
│   │   ├── auth_mfa.js       # Login → MFA → Verify → Profile
│   │   ├── registration.js   # Register → Login → Profile
│   │   ├── sso.js            # SSO Init → Callback → Profile
│   │   ├── session_mgmt.js   # Login → Sessions → Revoke
│   │   ├── key_exchange.js   # Key Init → Encrypted Request
│   │   ├── admin.js          # Admin Login → Users → Role → Verify
│   │   └── anon_to_auth.js   # Anonymous → Browse → Register → Login
│   └── system/               # Full system tests
│       ├── full_load.js      # Mixed workload (60/20/10/5/5)
│       ├── stress.js         # Breaking point (100→3000 req/s)
│       └── soak.js           # Memory leak (200 VUs, 1-4h)
├── docs/
│   ├── jvm-flags.md          # ZGC, heap, pool sizing formula
│   └── virtual-thread-pinning.md
├── reports/
│   ├── report_template.md    # Báo cáo kết quả (template)
│   └── bottleneck_checklist.md  # USE Method checklist
├── grafana/provisioning/     # Auto-provisioned dashboards + datasource
└── prometheus.yml            # Scrape config
```

---

## 3. Cấu hình môi trường

### 3.1 Tạo test users trong database

Trước khi chạy K6, cần có test users trong DB. Tạo SQL seed:

```sql
-- Tạo 10 test users cơ bản cho dev testing
-- Password hash = BCrypt("PerfTest{i}!") — dùng BCryptPasswordEncoder(12)
-- Hoặc chạy Register API để tạo:
INSERT INTO users (username, email, password, enabled)
SELECT 
    'perfuser' || i,
    'perfuser' || i || '@test.local',
    '$2a$12$...',  -- BCrypt hash
    true
FROM generate_series(1, 10) AS i
ON CONFLICT (username) DO NOTHING;

-- Tạo admin user
INSERT INTO users (username, email, password, enabled)
VALUES ('admin', 'admin@test.local', '$2a$12$...', true)
ON CONFLICT (username) DO NOTHING;
```

> 💡 **Cách nhanh**: Dùng Register API để K6 tự tạo users:
> ```bash
> # Chạy registration chain sẽ tự tạo unique users
> ./gradlew k6PerfChains  # hoặc chạy registration.js trực tiếp
> ```

### 3.2 Environment Variables

Tất cả cấu hình đều hỗ trợ override qua env vars:

#### Application (auth-service)

| Variable | Default | Mô tả |
|----------|---------|--------|
| `DB_POOL_MAX` | `10` | HikariCP max pool size |
| `DB_POOL_MIN` | `= DB_POOL_MAX` | HikariCP min idle |
| `DB_CONN_TIMEOUT` | `30000` | Connection timeout (ms) |
| `DB_IDLE_TIMEOUT` | `600000` | Idle timeout (ms) |
| `DB_MAX_LIFETIME` | `1800000` | Max connection lifetime (ms) |
| `DB_LEAK_THRESHOLD` | `30000` | Leak detection threshold (ms) |
| `ACCESS_LOG_ENABLED` | `false` | Tomcat access log |

#### K6 Tests

| Variable | Default | Mô tả |
|----------|---------|--------|
| `BASE_URL` | `http://localhost:8080` | auth-service URL |
| `TEST_USER` | `perfuser` | Test user username |
| `TEST_PASS` | `PerfTest123!` | Test user password |
| `ADMIN_USER` | `admin` | Admin username |
| `ADMIN_PASS` | `Admin123!` | Admin password |
| `MIX_AUTH` | `60` | Traffic mix: Auth (%) |
| `MIX_PROFILE` | `20` | Traffic mix: Profile (%) |
| `MIX_REGISTER` | `10` | Traffic mix: Register (%) |
| `MIX_ADMIN` | `5` | Traffic mix: Admin (%) |
| `MIX_MFA_SSO` | `5` | Traffic mix: MFA+SSO (%) |
| `SOAK_DURATION` | `1h` | Soak test duration |

### 3.3 HikariCP Pool Sizing

**Công thức**: `connections = (DB_cores × 2) + effective_spindle_count`

| DB Server | CPU | RAM | SSD | Pool Size | Command |
|-----------|-----|-----|-----|-----------|---------|
| Dev | 4 cores | 8 GB | Yes | **10** | `export DB_POOL_MAX=10` |
| Prod | 8 cores | 32 GB | Yes | **20** | `export DB_POOL_MAX=20` |

> **SSD → spindle_count = 1** (no rotational penalty)
>
> **Constraint**: `num_app_instances × pool_size < PostgreSQL max_connections` (default 100)
>
> Ví dụ: 3 instances × pool 20 = 60 connections → OK (< 100)

### 3.4 JVM Performance Flags

```bash
export PERF_JVM_OPTS="\
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx1024m \
  -XX:+AlwaysPreTouch \
  -Djdk.tracePinnedThreads=full \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./heapdumps"
```

| Flag | Tác dụng |
|------|----------|
| `-XX:+UseZGC` | Z Garbage Collector — GC pause <10ms |
| `-XX:+ZGenerational` | ZGC thế hệ mới (JDK 21+) — giảm CPU overhead |
| `-Xms512m -Xmx1024m` | Heap cố định — loại bỏ pause khi resize |
| `-XX:+AlwaysPreTouch` | Pre-touch heap pages — loại bỏ page faults |
| `-Djdk.tracePinnedThreads=full` | Log virtual thread pinning events |

---

## 4. Khởi động hệ thống

### Bước 1: Start Infrastructure (PostgreSQL + Redis)

```bash
docker compose up -d
```

Kiểm tra healthy:
```bash
docker compose ps
# Cả postgres và redis phải hiện "healthy"
```

### Bước 2: Start Observability Stack (Prometheus + Grafana)

```bash
docker compose -f compose.yaml -f compose-perf.yaml up -d
```

Kiểm tra:
```bash
# Prometheus
curl -s http://localhost:9090/-/ready
# Expected: "Prometheus Server is Ready."

# Grafana
curl -s http://localhost:3001/api/health | jq .status
# Expected: "ok"
```

### Bước 3: Start auth-service

```bash
# Development (không có JVM perf flags)
./gradlew bootRun

# Performance testing (với ZGC + virtual thread tracing)
JAVA_OPTS="$PERF_JVM_OPTS" ./gradlew bootRun

# Với custom pool size
DB_POOL_MAX=20 ACCESS_LOG_ENABLED=true ./gradlew bootRun
```

Kiểm tra:
```bash
# Health check
curl -s http://localhost:8080/actuator/health | jq .status
# Expected: "UP"

# Prometheus metrics
curl -s http://localhost:8081/actuator/prometheus | head -5
# Expected: Prometheus text format metrics

# Prometheus scraping auth-service
# Mở http://localhost:9090/targets — auth-service phải hiện UP
```

### Bước 4: Verify toàn bộ stack

```bash
echo "=== Infrastructure ==="
docker compose -f compose.yaml -f compose-perf.yaml ps

echo "=== App Health ==="
curl -s http://localhost:8080/actuator/health | jq .

echo "=== Prometheus Targets ==="
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

echo "=== Grafana Datasources ==="
curl -s http://localhost:3001/api/datasources | jq '.[].name'
```

---

## 5. Chạy Performance Tests

### 5.1 Quy trình chạy theo thứ tự (Bottom-Up)

```
Phase 1: Infra Baseline ──── "Server xử lý được bao nhiêu?"
    │
Phase 2: Single API ──────── "Từng API nhanh/chậm thế nào?"
    │
Phase 3: Chain Tests ─────── "Chuỗi API end-to-end mất bao lâu?"
    │
Phase 4: Full Load ──────── "Hệ thống chịu tải mixed workload ra sao?"
    │
Phase 5: Stress Test ─────── "Điểm giới hạn (breaking point) ở đâu?"
    │
Phase 6: Soak Test ──────── "Chạy lâu có memory leak không?"
```

### Phase 1: Infrastructure Baseline

> **Mục đích**: Đo raw capacity trước khi test business logic.

```bash
./gradlew k6PerfInfra
```

**Kỳ vọng:**
- Health endpoint: ≥5000 req/s
- DB pool không pending tại pool_size connections
- Redis: ≥10000 ops/s

**Nếu FAIL tại đây** → vấn đề infra (Docker resources, network, DB config), KHÔNG cần test tiếp.

### Phase 2: Single API Tests

> **Mục đích**: Baseline latency từng endpoint riêng lẻ.

```bash
# Auth P0 (critical path: login, refresh, validate, register)
./gradlew k6PerfSingle

# Hoặc chạy từng file riêng:
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/single/auth_p0.js

# Auth P1 (secondary: sessions, MFA, SSO, captcha)
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/single/auth_p1.js

# Internal endpoints
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/single/internal.js

# RBAC endpoints
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/single/rbac.js
```

**SLA Thresholds (tự động kiểm tra):**

| Endpoint | P95 | P99 | Error Rate |
|----------|-----|-----|------------|
| `POST /auth/login` | <200ms | <500ms | <1% |
| `POST /auth/refresh` | <150ms | <300ms | <1% |
| `POST /auth/validate` | <50ms | <100ms | <0.1% |
| `POST /auth/register` | <300ms | <500ms | <1% |
| Profile / CRUD | <500ms | <1000ms | <1% |

> K6 tự động exit code 99 khi vi phạm threshold.

### Phase 3: Chain Tests

> **Mục đích**: Đo end-to-end flow, phát hiện bottleneck khi API chaining.

```bash
# Auth Basic chain (Login → Profile → Refresh → Logout)
./gradlew k6PerfChains

# Hoặc chạy từng chain:
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/chains/auth_basic.js

# Registration chain (Register → Login → Profile)
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  grafana/k6 run /scripts/scenarios/chains/registration.js

# Danh sách chains khác:
#   auth_mfa.js      — Login → MFA Challenge → Verify → Profile
#   sso.js           — SSO Init → OAuth2 Callback → Profile
#   session_mgmt.js  — Login → List Sessions → Revoke
#   key_exchange.js  — Key Init → Encrypted Request
#   admin.js         — Admin Login → Users → Assign Role → Verify
#   anon_to_auth.js  — Anonymous → Browse → Register → Login
```

### Phase 4: Full Load Test (Mixed Workload)

> **Mục đích**: Mô phỏng production traffic với tỷ lệ thực tế.

```bash
./gradlew k6PerfLoad
```

**Traffic mix mặc định:**

| Loại | Tỷ lệ | Override |
|------|--------|---------|
| Auth (login/refresh) | 60% | `MIX_AUTH=70` |
| Profile | 20% | `MIX_PROFILE=15` |
| Register | 10% | `MIX_REGISTER=8` |
| Admin | 5% | `MIX_ADMIN=4` |
| MFA + SSO | 5% | `MIX_MFA_SSO=3` |

**Ramp profile**: 0 → 100 → 500 → 1000 VUs, tổng 10 phút.

**Target SLA**: TPS ≥500, P99 <1000ms, Error <0.1%

**Custom traffic mix:**
```bash
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  -e MIX_AUTH=70 \
  -e MIX_PROFILE=15 \
  -e MIX_REGISTER=8 \
  -e MIX_ADMIN=4 \
  -e MIX_MFA_SSO=3 \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  grafana/k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/system/full_load.js
```

### Phase 5: Stress Test (Breaking Point)

> **Mục đích**: Tìm điểm giới hạn — TPS tối đa trước khi degrade.

```bash
./gradlew k6PerfStress
```

**Ramp**: 100 → 200 → 500 → 1000 → 2000 → 3000 req/s, mỗi level 2 phút.

**Kết quả cần ghi nhận:**
- TPS tại điểm latency bắt đầu tăng (degradation onset)
- TPS max trước khi error rate >1% (breaking point)
- Recovery time sau khi ramp down

### Phase 6: Soak Test (Memory Leak Detection)

> **Mục đích**: Phát hiện memory leak, connection leak, resource exhaustion.

```bash
# Mặc định 1h
./gradlew k6PerfSoak

# Manual run 4h
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  -e SOAK_DURATION=4h \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  grafana/k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/system/soak.js
```

**Giám sát trong khi chạy soak:**
- Grafana → "Auth Service — Performance Overview" → JVM Memory row
- Heap trend phải **ổn định** (không tăng monotonically)
- HikariCP pending connections phải = 0
- Error rate phải ổn định <0.01%

---

## 6. JMH Microbenchmarks

> **Mục đích**: Đo throughput thuần (ops/sec) các crypto primitive — KHÔNG qua HTTP.

### Chạy toàn bộ benchmarks

```bash
./gradlew jmh
```

**Output**: `build/results/jmh/results.json`

### Chạy benchmark cụ thể

```bash
# BCrypt (bottleneck chính của login)
./gradlew jmh -Pjmh.include=BCryptBenchmark

# JWT RS256 (sign/verify trên mỗi request)
./gradlew jmh -Pjmh.include=JwtBenchmark

# Redis Cache serialization (Jackson vs JDK)
./gradlew jmh -Pjmh.include=RedisCacheBenchmark

# AES-GCM Encryption
./gradlew jmh -Pjmh.include=EncryptionBenchmark

# Serialization comparison
./gradlew jmh -Pjmh.include=SerializationBenchmark
```

### Danh sách Benchmarks

| Benchmark | File | Đo cái gì | Ý nghĩa thực tế |
|-----------|------|-----------|------------------|
| `BCryptBenchmark` | `BCryptBenchmark.kt` | BCrypt hash/verify (strength=12) | **Login TPS ceiling**: nếu BCrypt ~4 ops/s → 1 thread max 4 login/s |
| `JwtBenchmark` | `JwtBenchmark.kt` | JWT RS256 sign/verify | Token creation & validation throughput |
| `RedisCacheBenchmark` | `RedisCacheBenchmark.kt` | Jackson vs JDK serialization | Cache read/write overhead |
| `EncryptionBenchmark` | `EncryptionBenchmark.kt` | AES-GCM encrypt/decrypt | E2EE overhead per request |
| `SerializationBenchmark` | `SerializationBenchmark.kt` | Jackson serialization variants | General serialization cost |

### Đọc kết quả JMH

```bash
cat build/results/jmh/results.json | jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score, unit: .primaryMetric.scoreUnit}'
```

**Ví dụ output:**
```json
{
  "benchmark": "BCryptBenchmark.hashPassword",
  "score": 3.85,
  "unit": "ops/s"
}
```

→ Trên 1 thread, máy này hash được ~3.85 passwords/giây.
→ Với 10 virtual threads → theoretical max ≈ 38.5 login/s (crypto-bound).

---

## 7. Xem kết quả & Dashboard

### 7.1 Grafana Dashboards

Mở trình duyệt: **http://localhost:3001** (user: `admin`, pass: `admin`)

#### Dashboard 1: Performance Overview

> Folder: Performance → "Auth Service — Performance Overview"

| Row | Panels | Prometheus Queries |
|-----|--------|--------------------|
| **Request Performance** | TPS over time, Latency P50/P95/P99, Error Rate | `rate(http_server_requests_seconds_count[1m])` |
| **Resource Utilization** | CPU gauge, JVM Memory, GC Pauses | `process_cpu_usage`, `jvm_memory_used_bytes` |
| **Infrastructure** | HikariCP pool, Redis ops/s, Thread pool | `hikaricp_connections_active`, `jvm_threads_live_threads` |
| **Per-Endpoint** | P95 latency table by URI | `histogram_quantile(0.95, ...)` |

#### Dashboard 2: API Detail

> Folder: Performance → "Auth Service — API Detail"

- **Variable selector**: chọn endpoint URI cụ thể
- Panels: Latency distribution, Error rate, Throughput, DB pool correlation

### 7.2 Prometheus Queries hữu ích

```promql
# TPS hiện tại
sum(rate(http_server_requests_seconds_count{application="auth-service"}[1m]))

# P95 Latency
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="auth-service"}[1m])) by (le))

# Error rate (%)
sum(rate(http_server_requests_seconds_count{application="auth-service", status=~"5.."}[1m]))
/ sum(rate(http_server_requests_seconds_count{application="auth-service"}[1m])) * 100

# HikariCP pending (saturation indicator)
hikaricp_connections_pending{application="auth-service"}

# JVM Heap trend
jvm_memory_used_bytes{application="auth-service", area="heap"}

# GC pause duration
jvm_gc_pause_seconds_max{application="auth-service"}

# Per-endpoint P95 breakdown
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{application="auth-service"}[5m])) by (le, uri)
)
```

### 7.3 K6 Summary Output

K6 tự in summary sau mỗi test run. Các metrics quan trọng:

```
http_req_duration......: avg=45ms  min=5ms  med=30ms  max=1.2s  p(90)=80ms  p(95)=120ms
http_req_failed........: 0.05%    ✓ 50   ✗ 99950
http_reqs..............: 100000   ~833/s
vus_max................: 500
```

---

## 8. Quy trình đánh giá & cải thiện

### 8.1 Quy trình tổng thể (Iterative Optimization Cycle)

```
┌─────────────────────────────────────────────────────────────────┐
│  📊 MEASURE: Chạy benchmark suite, thu thập baseline metrics   │
│                                                                 │
│  🔍 ANALYZE: USE Method — tìm bottleneck                       │
│     └── Utilization → Saturation → Errors (per resource)        │
│                                                                 │
│  🔧 OPTIMIZE: Thay đổi 1 biến tại 1 thời điểm                 │
│     └── Giữ nguyên tất cả config khác                           │
│                                                                 │
│  📊 VERIFY: Chạy lại CÙNG benchmark, so sánh before/after       │
│     └── Nếu cải thiện → commit, ghi nhận                       │
│     └── Nếu tệ hơn → revert, thử hướng khác                   │
│                                                                 │
│  📝 DOCUMENT: Ghi kết quả vào report_template.md               │
│                                                                 │
│  🔁 Lặp lại cho bottleneck tiếp theo                           │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 USE Method — Phân tích bottleneck có hệ thống

> **U**tilization → **S**aturation → **E**rrors cho từng resource

Tham khảo chi tiết: [`reports/bottleneck_checklist.md`](tests/perf/reports/bottleneck_checklist.md)

#### Decision Flowchart

```
P95 Latency cao bất thường?
│
├── CPU > 80%?
│   ├── Yes → Profile app (async-profiler)
│   │         → Giảm BCrypt strength (12→10)?
│   │         → Kiểm tra serialization cost
│   └── No ↓
│
├── DB Pool Pending > 0?
│   ├── Yes → Tăng pool size (DB_POOL_MAX)
│   │         → Optimize slow queries (EXPLAIN ANALYZE)
│   │         → Thêm cache layer
│   └── No ↓
│
├── GC Pause > 100ms?
│   ├── Yes → Chuyển sang ZGC (-XX:+UseZGC)
│   │         → Tăng heap (-Xmx)
│   │         → Giảm allocation rate
│   └── No ↓
│
├── Redis Latency > 10ms?
│   ├── Yes → Kiểm tra network latency
│   │         → Giảm value size
│   │         → Dùng pipeline
│   └── No ↓
│
├── Tomcat Threads Full?
│   ├── Yes → Bật Virtual Threads
│   │         → Tăng max-threads
│   │         → Kiểm tra blocking calls
│   └── No ↓
│
└── Không tìm thấy?
    → Kiểm tra downstream services
    → Kiểm tra DNS, network hops
    → Kiểm tra access log pattern
```

### 8.3 Hướng dẫn tối ưu cụ thể theo từng resource

#### 🔧 Tối ưu HikariCP Pool

**Triệu chứng**: `hikaricp_connections_pending > 0`, request timeout.

```bash
# Bước 1: Kiểm tra pool hiện tại
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections | jq

# Bước 2: Tính pool size đúng theo công thức
# pool = (DB_cores × 2) + 1 (SSD)
# 4-core DB → 10, 8-core DB → 20

# Bước 3: Thay đổi
export DB_POOL_MAX=20

# Bước 4: Restart app, chạy lại test
./gradlew bootRun
./gradlew k6PerfSingle  # So sánh latency trước/sau
```

#### 🔧 Tối ưu BCrypt

**Triệu chứng**: Login P95 rất cao, CPU near 100%.

```bash
# Bước 1: Chạy JMH benchmark để đo baseline
./gradlew jmh -Pjmh.include=BCryptBenchmark

# Bước 2: Đánh giá
# BCrypt strength=12: ~3-5 ops/s/thread
# BCrypt strength=10: ~12-15 ops/s/thread (4x faster)

# Bước 3: Nếu chấp nhận giảm strength
# Sửa SecurityConfig: new BCryptPasswordEncoder(10)
# ⚠️ TRADE-OFF: Giảm brute-force resistance

# Bước 4: Verify
./gradlew jmh -Pjmh.include=BCryptBenchmark  # So sánh ops/s
./gradlew k6PerfSingle  # Kiểm tra login latency
```

#### 🔧 Tối ưu JVM / GC

**Triệu chứng**: GC pause spikes trên Grafana, latency đột biến.

```bash
# Bước 1: Bật ZGC
export PERF_JVM_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1024m"

# Bước 2: Nếu vẫn có vấn đề → tăng heap
export PERF_JVM_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms1g -Xmx2g"

# Bước 3: Kiểm tra virtual thread pinning
export PERF_JVM_OPTS="$PERF_JVM_OPTS -Djdk.tracePinnedThreads=full"
# Chạy test, grep logs:
grep -c "onPinned" app.log
# Nếu > 0: thay synchronized bằng ReentrantLock
# Xem chi tiết: tests/perf/docs/virtual-thread-pinning.md
```

#### 🔧 Tối ưu Redis Cache

**Triệu chứng**: Cache hit rate thấp, Redis latency cao.

```bash
# Bước 1: Chạy serialization benchmark
./gradlew jmh -Pjmh.include=RedisCacheBenchmark

# Bước 2: So sánh Jackson vs JDK
# Jackson thường nhanh hơn 5-10x cho serialize
# JDK thường nhỏ hơn byte size
# → Dùng Jackson (default trong Spring)

# Bước 3: Kiểm tra TTL strategy
# Quá ngắn → nhiều cache miss → DB overload
# Quá dài → stale data
# Recommend: 5-15 min cho session, 1-5 min cho frequently updated data

# Bước 4: Pipeline commands nếu có batch operations
```

### 8.4 Template báo cáo

Sau mỗi vòng optimization, copy và điền [`reports/report_template.md`](tests/perf/reports/report_template.md):

1. **Executive Summary** — PASS/FAIL/CONDITIONAL
2. **Test Configuration** — env, JVM flags, pool size
3. **Baseline Metrics** — per-endpoint P50/P95/P99 table
4. **Bottleneck Analysis** — USE Method per resource
5. **Optimization Results** — Before/After comparison
6. **JMH Results** — BCrypt, JWT, Redis ops/s
7. **Recommendations** — Prioritized by impact

### 8.5 Ví dụ kịch bản optimization hoàn chỉnh

```bash
# ===== ROUND 1: Baseline =====
# Start stack
docker compose -f compose.yaml -f compose-perf.yaml up -d
DB_POOL_MAX=10 ./gradlew bootRun

# Run infra baseline
./gradlew k6PerfInfra
# → Health: 8000 req/s ✅

# Run single API
./gradlew k6PerfSingle
# → Login P95: 350ms ❌ (SLA: <200ms)
# → Validate P95: 30ms ✅
# → Refresh P95: 120ms ✅

# ===== ROUND 1: Analyze =====
# Grafana shows CPU ~85% during login test
# BCrypt is CPU-bound → check JMH
./gradlew jmh -Pjmh.include=BCryptBenchmark
# → hashPassword: 3.2 ops/s/thread
# → Root cause: BCrypt strength=12 quá nặng cho target TPS

# ===== ROUND 2: Optimize =====
# Option A: Tăng pool → Không giúp (CPU-bound, not IO-bound)
# Option B: Tăng threads → Đã dùng virtual threads
# Option C: Scale horizontally → Khả thi nhưng costly
# → Chọn: Tạm giữ BCrypt 12, ghi nhận ceiling

# Thử tối ưu khác: Pool + ZGC
DB_POOL_MAX=20 JAVA_OPTS="-XX:+UseZGC -Xms1g -Xmx2g" ./gradlew bootRun

./gradlew k6PerfSingle
# → Login P95: 280ms (improved từ 350ms)
# → GC pauses: <5ms ✅

# ===== ROUND 3: Full Load =====
./gradlew k6PerfLoad
# → TPS: 620 req/s ✅ (target: 500)
# → P99: 850ms ✅ (target: <1000ms)
# → Error: 0.02% ✅ (target: <0.1%)

# ===== DOCUMENT =====
# Copy report_template.md → reports/report_2024-01-15.md
# Fill in all metrics, before/after tables
```

---

## 9. Troubleshooting

### K6 container không kết nối được auth-service

```bash
# Kiểm tra network mode
docker run --rm -i --network host grafana/k6 run ...
# --network host bắt buộc vì app chạy trên localhost

# Kiểm tra auth-service đang chạy
curl http://localhost:8080/actuator/health
```

### Prometheus không scrape được metrics

```bash
# Kiểm tra management port (8081, không phải 8080)
curl http://localhost:8081/actuator/prometheus

# Kiểm tra prometheus.yml target
# target phải là host.docker.internal:8081 (Docker → host)
cat tests/perf/prometheus.yml

# Kiểm tra Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
```

### K6 metrics không hiện trên Grafana

```bash
# Kiểm tra Prometheus remote write receiver
curl -s http://localhost:9090/-/ready

# Kiểm tra K6 gửi metrics
docker run --rm -i \
  -v $(pwd)/tests/perf:/scripts \
  --network host \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  grafana/k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/single/auth_p0.js
# Xem log K6 có "Prometheus remote write" output
```

### HikariCP connection timeout

```bash
# Kiểm tra PostgreSQL max_connections
docker exec auth-postgres psql -U auth_user -d auth_db -c "SHOW max_connections;"
# Default: 100

# Nếu pool_size × instances > max_connections → tăng PostgreSQL
docker exec auth-postgres psql -U auth_user -d auth_db \
  -c "ALTER SYSTEM SET max_connections = 200;"
# Restart postgres
```

### JMH benchmark fail khi compile

```bash
# Đảm bảo Spring Security dependency có trong jmh classpath
# BCryptPasswordEncoder cần spring-security-crypto
# Kiểm tra build.gradle.kts có:
#   jmh("org.openjdk.jmh:jmh-core:1.37")
#   jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")

# Clean build
./gradlew clean jmh
```

### Virtual thread pinning

```bash
# Bật detection
JAVA_OPTS="-Djdk.tracePinnedThreads=full" ./gradlew bootRun

# Chạy test
./gradlew k6PerfSingle

# Kiểm tra pinning events
grep -c "VirtualThread.*onPinned" logs/spring.log

# Xem chi tiết fix
cat tests/perf/docs/virtual-thread-pinning.md
```

---

## 10. Tham khảo

### File quan trọng

| File | Mô tả |
|------|--------|
| [`compose-perf.yaml`](compose-perf.yaml) | Docker Compose cho Prometheus + Grafana |
| [`tests/perf/config/thresholds.js`](tests/perf/config/thresholds.js) | SLA definitions (source of truth) |
| [`tests/perf/config/env.js`](tests/perf/config/env.js) | Environment config + traffic mix |
| [`tests/perf/docs/jvm-flags.md`](tests/perf/docs/jvm-flags.md) | JVM flags + pool sizing formula |
| [`tests/perf/docs/virtual-thread-pinning.md`](tests/perf/docs/virtual-thread-pinning.md) | Virtual thread pinning guide |
| [`tests/perf/reports/report_template.md`](tests/perf/reports/report_template.md) | Performance report template |
| [`tests/perf/reports/bottleneck_checklist.md`](tests/perf/reports/bottleneck_checklist.md) | USE Method checklist |

### Gradle Tasks

| Task | Group | Mô tả |
|------|-------|--------|
| `k6PerfInfra` | Performance | Infrastructure baseline test |
| `k6PerfSingle` | Performance | Single API endpoint tests (auth P0) |
| `k6PerfChains` | Performance | Chain test flows (auth_basic) |
| `k6PerfLoad` | Performance | Full mixed workload |
| `k6PerfStress` | Performance | Breaking point detection |
| `k6PerfSoak` | Performance | Memory leak detection (1h default) |
| `jmh` | Verification | All JMH microbenchmarks |
| `k6Run` | Verification | Legacy K6 auth flow test |

### URLs khi hệ thống chạy

| Service | URL |
|---------|-----|
| auth-service | http://localhost:8080 |
| Actuator / Prometheus metrics | http://localhost:8081/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin/admin) |
| PostgreSQL | localhost:5432 (auth_user/auth_pass) |
| Redis | localhost:6379 |
