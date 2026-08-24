# Kết quả tìm kiếm Open Source: TPS Performance Testing

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | TPS Performance Testing |
| **Ngày tìm kiếm** | 2026-08-21 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 5 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot / Gradle / PostgreSQL / Redis |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"K6 load testing GitHub"` | Grafana/K6 — top result | Official tool, massive community |
| 2 | `"datasource-proxy SQL query count assertion Spring Boot"` | ttddyy/datasource-proxy | Well-known JDBC proxy |
| 3 | `"JMH Gradle plugin Kotlin"` | me.champeau.jmh | Official JMH Gradle plugin |
| 4 | `"WireMock Spring Boot integration testing"` | wiremock/wiremock | Official mock server |
| 5 | `"Gatling load testing Spring Boot"` | gatling/gatling | Alternative to K6 |
| 6 | `"p6spy SQL logging Spring Boot"` | p6spy/p6spy | Alternative to datasource-proxy |
| 7 | `"Spring Boot performance testing open source"` | Multiple results | General landscape |
| 8 | `"Testcontainers Redis integration test"` | testcontainers/testcontainers-java | Container infra |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Grafana K6 | https://github.com/grafana/k6 | ~26k | Active (weekly) | AGPL-3.0 | ✅ Có |
| 2 | datasource-proxy | https://github.com/ttddyy/datasource-proxy | ~600 | Active (monthly) | MIT | ✅ Có |
| 3 | JMH Gradle Plugin | https://github.com/melix/jmh-gradle-plugin | ~700 | Active (monthly) | Apache-2.0 | ✅ Có |
| 4 | WireMock | https://github.com/wiremock/wiremock | ~6.5k | Active (weekly) | Apache-2.0 | ✅ Có |
| 5 | Gatling | https://github.com/gatling/gatling | ~6.5k | Active (weekly) | Apache-2.0 | ✅ Có |
| 6 | p6spy | https://github.com/p6spy/p6spy | ~2k | Active (quarterly) | Apache-2.0 | ❌ Không (less programmatic API than datasource-proxy) |
| 7 | Apache JMeter | https://github.com/apache/jmeter | ~8.5k | Active (monthly) | Apache-2.0 | ❌ Không (heavy GUI tool, poor CI/CD fit) |
| 8 | Testcontainers | https://github.com/testcontainers/testcontainers-java | ~8k | Active (weekly) | MIT | ❌ Không (infrastructure, not perf testing — evaluated as support tool) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Grafana K6

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full scripting (JS/ES6), thresholds, scenarios, built-in metrics, cloud integration |
| Applicability | 9 | 15% | 1.35 | Docker-based, HTTP protocol native, Gradle Exec task integration proven |
| Activity | 10 | 15% | 1.50 | Weekly commits, backed by Grafana Labs |
| Documentation | 10 | 15% | 1.50 | Comprehensive docs at k6.io, tutorials, examples |
| Code quality | 9 | 15% | 1.35 | Written in Go, well-tested, clean architecture |
| Community | 10 | 10% | 1.00 | ~26k stars, massive adoption |
| Popularity | 10 | 10% | 1.00 | Industry standard for modern load testing |
| **Tổng điểm** | | | **9.50/10** | |

#### datasource-proxy (ttddyy)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Query counting, logging, slow query detection, listener API |
| Applicability | 9 | 15% | 1.35 | Java/Kotlin native, Spring Boot auto-config, wraps any DataSource |
| Activity | 6 | 15% | 0.90 | Stable project, monthly/quarterly commits — mature |
| Documentation | 7 | 15% | 1.05 | Good README, Spring Boot examples, but no dedicated docs site |
| Code quality | 8 | 15% | 1.20 | Well-tested, clean listener pattern |
| Community | 5 | 10% | 0.50 | ~600 stars, niche but respected |
| Popularity | 6 | 10% | 0.60 | Widely used in JPA/Hibernate testing community |
| **Tổng điểm** | | | **7.20/10** | |

#### JMH Gradle Plugin (me.champeau.jmh)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Full JMH lifecycle (compile, run, report), fork/warmup config |
| Applicability | 8 | 15% | 1.20 | Gradle native, Kotlin source support, JVM 21+ compatible |
| Activity | 7 | 15% | 1.05 | Maintained by Cédric Champeau (ex-Gradle team), regular releases |
| Documentation | 7 | 15% | 1.05 | Good README, examples, but limited advanced docs |
| Code quality | 8 | 15% | 1.20 | Clean Gradle plugin architecture |
| Community | 5 | 10% | 0.50 | ~700 stars, niche |
| Popularity | 7 | 10% | 0.70 | De facto JMH plugin for Gradle |
| **Tổng điểm** | | | **7.30/10** | |

#### WireMock

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Request matching, response templating, fault injection, latency simulation, stateful behavior |
| Applicability | 9 | 15% | 1.35 | Spring Boot starter available, JUnit 5 extension, standalone or embedded |
| Activity | 9 | 15% | 1.35 | Weekly commits, active development by WireMock Inc |
| Documentation | 9 | 15% | 1.35 | Comprehensive wiremock.org docs, migration guides |
| Code quality | 8 | 15% | 1.20 | Well-tested, extensible architecture |
| Community | 8 | 10% | 0.80 | ~6.5k stars, widely adopted |
| Popularity | 9 | 10% | 0.90 | Industry standard for HTTP API mocking |
| **Tổng điểm** | | | **8.75/10** | |

#### Gatling

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Scenario DSL (Scala/Java/Kotlin), advanced simulations, HTML reports |
| Applicability | 6 | 15% | 0.90 | JVM-based but Scala-centric, Gradle plugin exists but less mature |
| Activity | 8 | 15% | 1.20 | Weekly commits, backed by Gatling Corp |
| Documentation | 8 | 15% | 1.20 | Good docs, academy |
| Code quality | 9 | 15% | 1.35 | Clean Scala codebase, well-architected |
| Community | 8 | 10% | 0.80 | ~6.5k stars |
| Popularity | 7 | 10% | 0.70 | Popular in enterprise JVM projects |
| **Tổng điểm** | | | **7.95/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Grafana K6 | 9 | 9 | 10 | 10 | 9 | 10 | 10 | **9.50** |
| 2 | WireMock | 9 | 9 | 9 | 9 | 8 | 8 | 9 | **8.75** |
| 3 | Gatling | 9 | 6 | 8 | 8 | 9 | 8 | 7 | **7.95** |
| 4 | JMH Gradle Plugin | 8 | 8 | 7 | 7 | 8 | 5 | 7 | **7.30** |
| 5 | datasource-proxy | 8 | 9 | 6 | 7 | 8 | 5 | 6 | **7.20** |

---

## 4. Gap Analysis chi tiết

### Grafana K6 — Gap Analysis

**Overall Score**: 9.50 / 10
**URL**: https://github.com/grafana/k6

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| HTTP Load Testing | ✅ | | Full protocol support, VU simulation | - |
| Threshold-based CI/CD | ✅ | | Exit code 99 on threshold breach | - |
| JavaScript Scripting | ✅ | | ES6 modules, modular test structure | - |
| Docker Execution | ✅ | | Official `grafana/k6` image | - |
| Scenario Modeling | ✅ | | `ramping-vus`, `constant-arrival-rate`, etc. | - |
| Built-in Metrics | ✅ | | `http_req_duration`, `http_req_failed`, `http_reqs` (TPS) | - |
| HTML Report | | ❌ | - | Requires K6 Cloud or third-party (k6-reporter) |
| gRPC Support | ✅ | | xk6-grpc extension | - |
| Gradle Integration | ⚠️ | | Works via Exec task | No official Gradle plugin |

**Verdict**: Dùng trực tiếp — đã proven trong project (k6Run, k6ProfileRun tasks)
**Recommendation**: Dùng trực tiếp
**Reasoning**: K6 đã tích hợp sẵn, community lớn nhất, scripting ES6 dễ viết, threshold exit codes hoạt động natively cho CI/CD.

### datasource-proxy — Gap Analysis

**Overall Score**: 7.20 / 10
**URL**: https://github.com/ttddyy/datasource-proxy

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| SQL Query Counting | ✅ | | `QueryCountHolder` API for per-thread counting | - |
| Spring Boot Integration | ✅ | | DataSource proxy wrapping via BeanPostProcessor | - |
| Query Logging | ✅ | | SLF4J-based, parameterized query log | - |
| Slow Query Detection | ✅ | | Threshold-based slow query listener | - |
| Annotation Support | | ❌ | - | No built-in `@AssertQueryCount` annotation |
| Kotlin Extension | | ❌ | - | Java API only, Kotlin extensions must be written |
| Auto-configuration | ⚠️ | | Spring Boot 3 support via `datasource-proxy-spring-boot-starter` | Third-party starter |
| Connection Pool Compatible | ✅ | | Works with HikariCP, DBCP, etc. | - |

**Verdict**: Dùng trực tiếp + build custom annotation wrapper
**Recommendation**: Dùng trực tiếp
**Reasoning**: De facto standard cho JDBC query counting trong Spring ecosystem. Cần build `@AssertQueryCount` annotation wrapper và `assertQueryCount {}` Kotlin DSL function on top.

### JMH Gradle Plugin — Gap Analysis

**Overall Score**: 7.30 / 10
**URL**: https://github.com/melix/jmh-gradle-plugin

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Gradle Integration | ✅ | | Native Gradle plugin, `jmh` source set | - |
| JMH Core Features | ✅ | | Full benchmark lifecycle | - |
| Kotlin Support | ✅ | | Compiles Kotlin benchmark sources | - |
| Multiple Output Formats | ✅ | | JSON, CSV, text | - |
| Fork/Warmup Config | ✅ | | Configurable via plugin DSL | - |
| JVM 25 Compatibility | ⚠️ | | JVM 21+ tested, JVM 25 likely OK | Not explicitly verified |
| Spring Context | | ❌ | - | JMH benchmarks run without Spring context |
| CI/CD Integration | ⚠️ | | Produces output files | No built-in threshold/comparison |

**Verdict**: Dùng trực tiếp
**Recommendation**: Dùng trực tiếp
**Reasoning**: De facto JMH Gradle plugin. Supports Kotlin source sets. Perfect for benchmarking encryption (AES-GCM) and serialization overhead.

### WireMock — Gap Analysis

**Overall Score**: 8.75 / 10
**URL**: https://github.com/wiremock/wiremock

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Latency Simulation | ✅ | | `withFixedDelay()`, `withLogNormalRandomDelay()` | - |
| Fault Injection | ✅ | | Connection reset, empty response, malformed | - |
| Spring Boot Starter | ✅ | | `spring-cloud-contract-wiremock` | - |
| JUnit 5 Extension | ✅ | | `@WireMockTest` annotation | - |
| Request Matching | ✅ | | URL, headers, body, JSON path | - |
| Stateful Scenarios | ✅ | | State machine for multi-step flows | - |
| Standalone Mode | ✅ | | Docker or JAR | - |
| OAuth2 Mock | ⚠️ | | Can stub token endpoints | Not purpose-built for OAuth |

**Verdict**: Dùng trực tiếp
**Recommendation**: Dùng trực tiếp
**Reasoning**: Industry standard HTTP mock server. Spring Cloud Contract integration makes it first-class in Spring Boot. Latency simulation is exactly what FR-002 needs.

### Gatling — Gap Analysis

**Overall Score**: 7.95 / 10
**URL**: https://github.com/gatling/gatling

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Load Testing | ✅ | | Highly capable, Akka-based async | - |
| JVM Native | ✅ | | Runs on JVM | - |
| HTML Reports | ✅ | | Beautiful built-in reports | - |
| Kotlin DSL | ✅ | | Kotlin DSL (since Gatling 3.9) | - |
| Gradle Plugin | ⚠️ | | Official plugin exists | Less Docker-friendly than K6 |
| Lightweight CI/CD | | ❌ | - | Heavier than K6, longer startup |
| Existing Integration | | ❌ | - | Project uses K6, switching has cost |
| Community | ✅ | | Large community | - |

**Verdict**: Tham khảo pattern — không adopt vì project đã dùng K6
**Recommendation**: Tham khảo pattern
**Reasoning**: Excellent tool nhưng project đã invest vào K6 (2 scripts + 2 Gradle tasks). Switching cost > benefit. Gatling's Kotlin DSL cho scenario modeling là worth studying for pattern reference.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Grafana K6 | 9.50/10 | Dùng trực tiếp | E2E Load Testing, TPS measurement, CI/CD thresholds |
| 🥈 2 | WireMock | 8.75/10 | Dùng trực tiếp | HTTP Client latency simulation, external API mocking |
| 🥉 3 | Gatling | 7.95/10 | Tham khảo pattern | (Reference only — scenario modeling patterns) |
| 4 | JMH Gradle Plugin | 7.30/10 | Dùng trực tiếp | Encryption/serialization microbenchmarks |
| 5 | datasource-proxy | 7.20/10 | Dùng trực tiếp | SQL query count assertion, N+1 detection |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| Dùng K6 (keep, extend) | Đã tích hợp sẵn, top-scored, CI/CD native | Existing `k6Run` tasks, threshold exit codes |
| Dùng datasource-proxy (add) | Unique SQL counting capability, Spring Boot native | Standard in JPA testing community |
| Dùng JMH Plugin (add) | De facto standard JVM benchmarking, Gradle native | Kotlin source support |
| Dùng WireMock (add) | HTTP latency simulation, Spring Cloud Contract support | `spring-cloud-contract-wiremock` dependency |
| Không dùng Gatling | Project đã invest K6, switching cost > benefit | 2 existing K6 scripts, 2 Gradle tasks |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-21
> **Next step**: Comparison Analysis (comparison_analysis.md)
