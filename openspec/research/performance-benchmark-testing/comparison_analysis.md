# Comparison Analysis — Performance Testing Strategy

## 1. Tool Selection — Final Comparison Matrix

### 1.1 Load Testing Tool Selection

| Criteria (Weight) | K6 (extend) | Gatling (new) | JMeter (new) | Locust (new) |
|-------------------|:-----------:|:-------------:|:------------:|:------------:|
| Already in project (×3) | ⭐⭐⭐ 15 | ❌ 0 | ❌ 0 | ❌ 0 |
| CI/CD Native (×2) | ⭐⭐⭐ 10 | ⭐⭐ 8 | ⭐ 4 | ⭐ 4 |
| Learning Curve (×2) | ⭐⭐⭐ 10 | ⭐⭐ 6 | ⭐⭐ 6 | ⭐⭐ 6 |
| Performance (×2) | ⭐⭐⭐ 10 | ⭐⭐⭐ 10 | ⭐⭐ 6 | ⭐⭐ 6 |
| Multi-scenario (×2) | ⭐⭐⭐ 10 | ⭐⭐⭐ 10 | ⭐⭐ 8 | ⭐⭐ 6 |
| Reporting (×1) | ⭐⭐ 4 | ⭐⭐⭐ 5 | ⭐⭐ 4 | ⭐⭐ 3 |
| Prometheus Integration (×1) | ⭐⭐⭐ 5 | ⭐⭐ 3 | ⭐ 2 | ⭐⭐ 3 |
| **TOTAL** | **64** | **42** | **30** | **28** |

**Decision: K6 (extend)** — Đã có 3 scripts, Docker runner, Gradle tasks. Mở rộng thay vì thêm tool mới.

---

### 1.2 Microbenchmark Tool Selection

| Criteria | JMH (extend) | Custom Benchmark |
|----------|:------------:|:----------------:|
| Already in project | ⭐⭐⭐ | ❌ |
| JVM accuracy | ⭐⭐⭐ | ⭐ |
| Industry standard | ⭐⭐⭐ | ❌ |
| **Decision** | **JMH** | — |

---

### 1.3 Observability Stack Selection

| Criteria | Prometheus+Grafana | SigNoz | Datadog |
|----------|:------------------:|:------:|:-------:|
| Cost | Free/OSS | Free/OSS | Paid |
| Spring Boot Integration | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| K6 Integration | ⭐⭐⭐ | ⭐ | ⭐⭐ |
| Existing `base-observability-starter` | ⭐⭐⭐ | ⭐ | ⭐ |
| **Decision** | **Prometheus+Grafana** | — | — |

---

## 2. Feature Comparison — Testing Approaches

### 2.1 Infrastructure Baseline Test

| Approach | Tool | What it measures | When to run |
|----------|------|-----------------|-------------|
| DB Pool Saturation | K6 + Actuator metrics | Max connections, queue time, timeout rate | Trước optimization |
| Redis Throughput | K6 + Redis INFO | Commands/sec, memory, connected clients | Trước optimization |
| HTTP Server Capacity | K6 + wrk2 | Max concurrent connections, accept rate | Trước optimization |
| Crypto Overhead | JMH | Ops/sec for AES, BCrypt, JWT, X25519 | Bất kỳ lúc nào |
| Serialization | JMH | Ops/sec for Jackson JSON, Redis serializer | Bất kỳ lúc nào |

### 2.2 API-Level Testing Approach

| Test Type | Purpose | K6 Pattern | Metrics |
|-----------|---------|------------|---------|
| Single API | Baseline per endpoint | `constant-arrival-rate` per scenario | p50/p95/p99, error rate |
| API Chain | Realistic user flow | Sequential requests in `default` function | End-to-end latency, chain error rate |
| Mixed Workload | Production simulation | Multiple scenarios with weighted ratios | Aggregate TPS, resource utilization |
| Stress | Breaking point | `ramping-arrival-rate` beyond capacity | Saturation point, degradation pattern |
| Soak | Long-term stability | `constant-vus` for hours | Memory trend, connection leak |

### 2.3 Optimization Area Comparison

| Area | Current State | Target State | Effort | Impact |
|------|---------------|-------------|--------|--------|
| **HikariCP** | Default config | Explicit tuning + monitoring | Low | HIGH |
| **Redis Serialization** | GenericJackson2Json | Benchmark alternatives | Medium | MEDIUM |
| **JVM Flags** | None | ZGC + heap tuning | Low | HIGH |
| **Actuator** | health,info,metrics | + prometheus endpoint | Low | HIGH |
| **K6 Scenarios** | 3 scripts | 10+ scenarios | Medium | HIGH |
| **JMH Benchmarks** | 2 benchmarks | 7+ benchmarks | Medium | MEDIUM |
| **Profiling** | None | Async Profiler + datasource-proxy | Medium | HIGH |
| **Docker Compose** | postgres + redis | + prometheus + grafana | Low | HIGH |
| **Virtual Thread Monitoring** | No pinning detection | JVM flag + monitoring | Low | MEDIUM |

---

## 3. Gap Analysis

### 3.1 Current Coverage vs Required

```
                         Current          Required
                         -------          --------
Load Testing:            ████░░░░░░  30%  ██████████ 100%
  - Single API test      ██░░░░░░░░  20%  ██████████ 100%
  - API Chain test       ░░░░░░░░░░   0%  ██████████ 100%
  - Mixed Workload       ░░░░░░░░░░   0%  ██████████ 100%
  - Stress/Soak          ░░░░░░░░░░   0%  ████████░░  80%

Microbenchmark:          ████░░░░░░  30%  ██████████ 100%
  - Encryption (AES)     ██████████ 100%  ██████████ 100%
  - Serialization        ██████████ 100%  ██████████ 100%
  - BCrypt               ░░░░░░░░░░   0%  ██████████ 100%
  - JWT                  ░░░░░░░░░░   0%  ██████████ 100%
  - Redis Serializer     ░░░░░░░░░░   0%  ██████████ 100%

Observability:           ██░░░░░░░░  15%  ██████████ 100%
  - Actuator basic       ██████████ 100%  ██████████ 100%
  - Prometheus endpoint  ░░░░░░░░░░   0%  ██████████ 100%
  - Grafana dashboards   ░░░░░░░░░░   0%  ██████████ 100%
  - K6→Prometheus        ░░░░░░░░░░   0%  ██████████ 100%

Infrastructure Tuning:   █░░░░░░░░░   5%  ██████████ 100%
  - HikariCP config      ░░░░░░░░░░   0%  ██████████ 100%
  - JVM flags            ░░░░░░░░░░   0%  ██████████ 100%
  - Virtual thread check ░░░░░░░░░░   0%  ████████░░  80%

Reporting:               ██░░░░░░░░  15%  ██████████ 100%
  - K6 console output    ██████████ 100%  ██████████ 100%
  - Structured report    ░░░░░░░░░░   0%  ██████████ 100%
  - Grafana dashboard    ░░░░░░░░░░   0%  ██████████ 100%
  - Comparison reports   ░░░░░░░░░░   0%  ██████████ 100%
```

### 3.2 Gaps Summary

| # | Gap | Impact | Resolution |
|---|-----|--------|------------|
| G1 | Thiếu test cho 14/17 API endpoints | Không biết baseline per-endpoint | Tạo K6 scenarios cho mọi endpoint |
| G2 | Thiếu API chain tests | Không đo được realistic user journey | Tạo K6 sequential chain scenarios |
| G3 | Thiếu infrastructure baseline | Không biết bottleneck ở layer nào | HikariCP tune + JVM flags + Prometheus |
| G4 | Thiếu Prometheus/Grafana | Không có real-time correlation | Docker compose + Actuator config |
| G5 | Thiếu JMH benchmarks cho BCrypt/JWT | Không biết crypto overhead thật | Thêm JMH benchmarks |
| G6 | Thiếu report template | Không so sánh được giữa các lần test | Structured report format |
| G7 | Thiếu soak test | Không phát hiện memory/connection leak | K6 soak test scenario |
| G8 | Thiếu stress test | Không biết breaking point | K6 stress test scenario |

---

## 4. Recommendation

### Strategy: **Extend & Integrate** (Build on existing foundation)

**Decision:** `BUILD` — Extend existing K6 + JMH foundation, thêm observability stack

**Reasoning:**
1. Đã có nền tảng tốt (3 K6 scripts + 2 JMH benchmarks)
2. K6 đã được Docker-hóa và có Gradle tasks
3. Spring Boot Actuator + Micrometer đã có sẵn
4. Chỉ cần extend, không cần thay đổi architecture

### Implementation Priority

```
Sprint 1 (P0 — Foundation):
├── HikariCP explicit config
├── JVM flags (ZGC)
├── Prometheus endpoint + Grafana docker compose
├── K6 → Prometheus remote write
└── Infrastructure baseline tests

Sprint 2 (P0 — API Coverage):
├── K6 single API tests cho tất cả endpoints
├── K6 API chain tests
├── JMH BCrypt + JWT benchmarks
└── First performance report

Sprint 3 (P1 — Deep Analysis):
├── K6 mixed workload
├── K6 stress test
├── Async Profiler integration
├── datasource-proxy activation
└── Optimization iteration 1

Sprint 4 (P2 — Long-term):
├── K6 soak test
├── Redis serializer comparison
├── Virtual thread pinning analysis
├── Full Grafana dashboards
└── CI/CD performance gates
```
