# Open Source Findings — Performance Testing Tools

## 1. Load Testing Tools

### 1.1 Grafana k6 ⭐ (Recommended — Already in use)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | Grafana Labs backed, 25k+ GitHub stars |
| Fit with Stack | 5 | JavaScript/TS, Docker, CI/CD native |
| Performance | 5 | Go runtime, single binary, low overhead |
| Community | 5 | Active community, extensive docs |
| CI/CD Integration | 5 | Built-in thresholds, exit codes |
| Reporting | 4 | Web dashboard built-in, Prometheus/Grafana integration |
| **Total** | **29/30** | |

**Gap Analysis:**
- ✅ Already integrated trong project (3 K6 scripts)
- ✅ Docker-based runner (no install needed)
- ✅ Multi-scenario support (ramping-vus, constant-arrival-rate)
- ⚠️ Cần mở rộng: test cho tất cả 17 controllers
- ⚠️ Cần thêm: Prometheus remote write output
- ⚠️ Cần thêm: Sequential API chain testing pattern

**Source:** https://k6.io/, https://github.com/grafana/k6

---

### 1.2 Gatling (Kotlin DSL)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | 10+ years, enterprise-grade |
| Fit with Stack | 4 | Kotlin DSL available, JVM-native |
| Performance | 5 | Async Akka/Netty, high VU density |
| Community | 4 | Active, good docs |
| CI/CD Integration | 4 | Gradle plugin, assertions API |
| Reporting | 5 | Built-in HTML reports — best in class |
| **Total** | **27/30** | |

**Gap Analysis:**
- ✅ Kotlin DSL — cùng ngôn ngữ với project
- ✅ Excellent HTML reports auto-generated
- ✅ Gradle integration via plugin
- ⚠️ Thêm complexity — đã dùng K6
- ⚠️ Cần thêm dependency vào build
- ❌ Không cần thiết nếu K6 đáp ứng đủ

**Source:** https://gatling.io/, https://docs.gatling.io/tutorials/scripting-intro-kt/

---

### 1.3 Apache JMeter

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | 20+ years, Apache Foundation |
| Fit with Stack | 3 | Java-based nhưng GUI-heavy |
| Performance | 3 | Thread-per-user, high memory overhead |
| Community | 5 | Massive community, plugins ecosystem |
| CI/CD Integration | 3 | Cần thêm Maven/Gradle plugin |
| Reporting | 4 | JMeter Dashboard Report |
| **Total** | **23/30** | |

**Gap Analysis:**
- ✅ Multi-protocol support (JDBC direct testing possible)
- ⚠️ GUI-centric workflow — không fit CI/CD first approach
- ❌ Higher memory than K6/Gatling
- ❌ Complexity overhead cho simple REST testing

**Source:** https://jmeter.apache.org/

---

### 1.4 Locust

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 4 | Python-based, good community |
| Fit with Stack | 2 | Python — khác tech stack |
| Performance | 3 | gevent-based, lower than Go/JVM |
| Community | 4 | Active community |
| CI/CD Integration | 3 | CLI-based, custom reporting |
| Reporting | 3 | Basic web UI |
| **Total** | **19/30** | |

---

### 1.5 wrk2 / Vegeta (Specialized)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 3 | Targeted tools |
| Fit with Stack | 3 | CLI-only |
| Performance | 5 | Ultra-lightweight |
| Community | 2 | Limited |
| CI/CD Integration | 2 | Manual scripting |
| Reporting | 1 | Text output only |
| **Total** | **16/30** | |

**Use case:** Quick HTTP benchmark, constant-rate stress test. Complementary to K6.

---

## 2. Microbenchmark Tools

### 2.1 JMH ⭐ (Already in use)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | OpenJDK project, de facto standard |
| Fit with Stack | 5 | Kotlin/JVM native |
| Accuracy | 5 | JIT warmup, GC control, fork isolation |
| Community | 5 | Standard for JVM benchmarking |
| Integration | 5 | Gradle plugin (`me.champeau.jmh`) |
| **Total** | **25/25** | |

**Gap Analysis:**
- ✅ Already integrated (2 benchmarks: Encryption + Serialization)
- ⚠️ Cần thêm benchmarks: BCrypt hashing, JWT generation/parsing, Redis serialization
- ⚠️ Cần thêm: Result comparison automation

---

## 3. Observability & Monitoring

### 3.1 Prometheus + Grafana ⭐ (Recommended)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | CNCF graduated, industry standard |
| Fit with Stack | 5 | Spring Boot Actuator + Micrometer native |
| Features | 5 | Time-series DB + alerting + dashboards |
| K6 Integration | 5 | Prometheus remote write output |
| **Total** | **20/20** | |

**Gap Analysis:**
- ⚠️ Actuator chỉ expose `health,info,metrics` — cần thêm `prometheus` endpoint
- ⚠️ Cần thêm `micrometer-registry-prometheus` dependency
- ⚠️ Cần Docker compose cho Prometheus + Grafana containers

### 3.2 OpenTelemetry + Jaeger/Tempo (Distributed Tracing)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | CNCF standard |
| Fit with Stack | 4 | `base-observability-starter` đã có |
| Features | 5 | Traces + Metrics + Logs |
| **Total** | **14/15** | |

---

## 4. Profiling Tools

### 4.1 Async Profiler (JVM)

| Tiêu chí | Điểm (1-5) | Ghi chú |
|----------|------------|---------|
| Maturity | 5 | Standard JVM profiler |
| Features | 5 | CPU + Heap + Lock + Wall clock profiling |
| Output | 5 | Flame graphs |
| **Total** | **15/15** | |

### 4.2 datasource-proxy (SQL)

- Already in test dependencies (`net.ttddyy:datasource-proxy:1.10`)
- Cần activate cho N+1 detection và query counting

---

## 5. Recommendation Matrix

| Concern | Recommended Tool | Priority |
|---------|-----------------|----------|
| **Load Testing (HTTP)** | K6 (extend existing) | P0 |
| **Microbenchmark (JVM)** | JMH (extend existing) | P0 |
| **Observability** | Prometheus + Grafana | P0 |
| **Distributed Tracing** | OpenTelemetry (đã có starter) | P1 |
| **JVM Profiling** | Async Profiler | P1 |
| **SQL Profiling** | datasource-proxy (đã có dep) | P1 |
| **Quick HTTP Benchmark** | wrk2 / Vegeta | P2 |
| **Alternative Load Tool** | Gatling (backup) | P2 |
