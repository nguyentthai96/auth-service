# Kết quả nghiên cứu Internet: TPS Performance Testing

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | TPS Performance Testing |
| **Ngày nghiên cứu** | 2026-08-21 |
| **Số iterations** | 3 |
| **Tổng sources** | 12 unique |
| **Keywords ban đầu** | performance testing, TPS, K6, datasource-proxy, JMH, WireMock |
| **Keywords phát triển** | N+1 query detection, cache encryption benchmark, CI/CD performance gate, Spring Boot load testing, constant-arrival-rate scenario |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Spring Boot performance testing best practices 2024"` | Các bài viết nhấn mạnh: observability-first approach, realistic data seeding, baseline-then-optimize | `baseline testing`, `performance regression` |
| 2 | `"K6 CI/CD pipeline threshold configuration"` | K6 docs: thresholds property, exit code 99 khi fail, `abortOnFail` option | `abortOnFail`, `exit code 99`, `constant-arrival-rate` |
| 3 | `"N+1 query detection JPA Spring Boot automated"` | datasource-proxy + `QuickPerfTestRunner` (QuickPerf), Hibernate Statistics | `QuickPerf`, `Hibernate Statistics`, `datasource-proxy-spring-boot-starter` |

**Takeaways Iteration 1:**
- K6 là dominant choice cho CI/CD performance testing — lightweight, scriptable, threshold-native.
- N+1 detection có 3 approaches: datasource-proxy (counting), QuickPerf (annotation-based), Hibernate Statistics (built-in nhưng coarse-grained).
- Performance testing cần baseline: measure hiện tại trước, đặt thresholds sau.

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://k6.io/docs/using-k6/thresholds/ | K6 Thresholds Documentation | `thresholds` property trên `options`: `http_req_duration: ['p(95)<200']`, `http_req_failed: ['rate<0.01']`. K6 trả exit code 99 khi threshold bị breach. `abortOnFail: true` dừng test sớm. | 10 |
| 2 | https://k6.io/docs/using-k6/scenarios/ | K6 Scenarios Documentation | `constant-arrival-rate`: đảm bảo fixed request rate (TPS) bất kể response time. `ramping-vus`: gradual load increase. Multi-scenario support cho test different endpoints cùng lúc. | 9 |
| 3 | https://ttddyy.github.io/datasource-proxy/docs/ | datasource-proxy Documentation | `ProxyDataSourceBuilder.create(dataSource).countQuery().build()`. `QueryCountHolder.getGrandTotal()` để lấy tổng queries. `QueryCountHolder.clear()` reset per-thread. Spring Boot: dùng `DataSourceProxyBeanPostProcessor`. | 9 |
| 4 | https://wiremock.org/docs/simulating-faults/ | WireMock Fault Simulation | `withFixedDelay(2000)` — 2s delay. `withLogNormalRandomDelay(90, 0.1)` — realistic latency distribution. `aFault().withFault(Fault.CONNECTION_RESET_BY_PEER)` — simulate failures. | 8 |
| 5 | https://github.com/melix/jmh-gradle-plugin#usage | JMH Gradle Plugin Usage | Gradle DSL: `jmh { fork = 2; warmupIterations = 5; iterations = 5 }`. Source set: `src/jmh/kotlin/`. `./gradlew jmh` to run. Output: `build/results/jmh/`. | 8 |
| 6 | https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/ | Vlad Mihalcea: N+1 Detection | Custom `SQLStatementCountValidator` using datasource-proxy. Pattern: `reset() → execute code → assertSelectCount(expected)`. Annotation approach possible via custom JUnit Extension. | 9 |

**Takeaways Iteration 2:**
- K6 `constant-arrival-rate` scenario tối ưu cho TPS testing — giữ fixed request rate thay vì fixed VU count.
- datasource-proxy `QueryCountHolder` cung cấp per-thread query counting — perfect cho Kotlin `assertQueryCount {}` DSL.
- WireMock fault simulation có thể test resilience4j circuit breaker behavior.
- JMH benchmark needs separate source set — `src/jmh/kotlin/` — không share Spring context.
- Vlad Mihalcea's approach (SQLStatementCountValidator) là reference implementation tốt nhất cho `assertQueryCount`.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"K6 Docker Gradle integration exec task"` (từ gap: Gradle integration best practice) | Xác nhận: Docker Exec task là approach đúng. `--network host` cho localhost access. Env vars qua `-e`. | ✅ |
| 2 | `"datasource-proxy vs p6spy Spring Boot 2024"` (verify conflict) | datasource-proxy: programmatic API (counting), lightweight. p6spy: log-focused, heavier. datasource-proxy wins for assertion use case. | ✅ |
| 3 | `"Redis Testcontainers raw data inspection encryption verification"` (từ gap: cache test) | Testcontainers Redis: dùng `GenericContainer("redis:7.4-alpine")`. Connect via `StringRedisTemplate` with Testcontainer host/port. Raw GET key → check if encrypted. | ✅ |
| 4 | `"K6 multiple scenarios same script different endpoints"` (gap: multi-endpoint testing) | K6 supports `scenarios` object: mỗi scenario có `exec` function riêng, `executor`, `vus`, `duration`. Chạy parallel hoặc sequential. | ✅ |
| 5 | `"JMH Kotlin annotation processor kapt"` (gap: Kotlin JMH compat) | JMH annotations (`@Benchmark`, `@State`, `@Setup`) work in Kotlin via `kapt` or standard annotation processing. Plugin handles this natively. | ✅ |
| 6 | `"ZGC vs G1GC performance testing Spring Boot"` (gap: JVM tuning) | ZGC: ultra-low pause times (<1ms), good for latency-sensitive. G1GC: balanced throughput/latency. For TPS testing, match production JVM config. | ✅ |

**Stop reason**: All research questions answered, diminishing returns on additional searches.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | K6 Thresholds | https://k6.io/docs/using-k6/thresholds/ | Exit code 99, abortOnFail, metric-based thresholds | 10 | Official, comprehensive | - |
| 2 | Docs | K6 Scenarios | https://k6.io/docs/using-k6/scenarios/ | constant-arrival-rate for fixed TPS | 9 | Detailed executor docs | - |
| 3 | Blog | Vlad Mihalcea: N+1 Detection | https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/ | datasource-proxy based SQLStatementCountValidator | 9 | Proven pattern, real-world | Java-only examples |
| 4 | Docs | datasource-proxy Documentation | https://ttddyy.github.io/datasource-proxy/docs/ | QueryCountHolder API, ProxyDataSourceBuilder | 9 | Official docs | Sparse on Spring Boot 3 |
| 5 | Docs | WireMock Fault Simulation | https://wiremock.org/docs/simulating-faults/ | Delay simulation, fault injection | 8 | Well-documented | - |
| 6 | Docs | JMH Gradle Plugin README | https://github.com/melix/jmh-gradle-plugin#usage | Plugin DSL, source sets, Kotlin support | 8 | Clear examples | Limited advanced use cases |
| 7 | Blog | Spring Boot Performance Testing Patterns | https://spring.io/blog (various) | Testcontainers for integration, profile-based config | 7 | Official Spring perspective | General, not specific |
| 8 | Docs | K6 k6-reporter extension | https://github.com/benc-uk/k6-reporter | HTML report generation from K6 JSON output | 6 | Simple, standalone | Not official K6 |
| 9 | Blog | Redis Cache Encryption Testing | https://docs.spring.io/spring-data/redis/reference/redis/template.html | StringRedisTemplate for raw data access | 7 | Official Spring Data Redis | Not encryption-specific |
| 10 | Docs | Testcontainers Redis Module | https://java.testcontainers.org/modules/databases/redis/ | GenericContainer for Redis, dynamic port mapping | 7 | Well-maintained | - |
| 11 | Article | JVM Garbage Collectors Comparison | https://openjdk.org/jeps/333 | ZGC pause times, throughput comparison | 6 | JEP source | JVM-specific |
| 12 | Docs | Resilience4j + WireMock Testing | https://resilience4j.readme.io/docs/getting-started | Circuit breaker testing with mock delays | 7 | Official R4J docs | - |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| K6 Cloud (Grafana) | SaaS load testing with distributed execution | Cloud execution, trend analysis, team collaboration | No infra management | Free tier available, integrated with K6 OSS | Paid for large tests, data leaves project | Not needed — Docker local sufficient for auth-service |
| Gatling Enterprise | Commercial load testing platform | Distributed testing, real-time reports, CI plugins | Enterprise support | Professional reports | License cost, Scala-centric | Project uses K6, switching not justified |
| QuickPerf | Annotation-based SQL perf testing | `@ExpectSelect(2)`, `@DisableQueriesWithoutBindParameters` | Zero-config assertion | JUnit 4/5 support | Less flexible than datasource-proxy DSL, less maintained | More opinionated than needed |
| Testcontainers Cloud | Managed test containers | Cloud-based containers, faster CI | No local Docker needed | Fast, reliable | Cost, dependency on cloud | Over-engineering for this scope |

### So sánh tính năng chi tiết

| Feature | K6 OSS + Docker | Gatling OSS | QuickPerf | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| HTTP Load Testing | ✅ | ✅ | ❌ | ❌ | ⭐ Must |
| TPS Measurement | ✅ | ✅ | ❌ | ❌ | ⭐ Must |
| SQL Query Counting | ❌ | ❌ | ✅ | ✅ (datasource-proxy) | ⭐ Must |
| CI/CD Threshold Gate | ✅ | ⚠️ | ❌ | ❌ | ⭐ Must |
| JVM Microbenchmark | ❌ | ❌ | ❌ | ✅ (JMH) | Nice to have |
| HTTP Mock/Fault Injection | ❌ | ❌ | ❌ | ✅ (WireMock) | ⭐ Must |
| Docker-native Execution | ✅ | ⚠️ | ❌ | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Baseline → Threshold → Regression** | Chạy baseline test → extract metrics → set thresholds = baseline × 1.2 → CI detects regression | Scientific, evidence-based | Requires initial baseline run | Setting up new perf testing framework | K6 docs best practices |
| 2 | **assertQueryCount DSL** | Kotlin DSL wrapping datasource-proxy: `assertQueryCount(select = 2) { service.findAll() }` | Clean, readable, reusable | Needs datasource-proxy setup | JPA integration tests, N+1 detection | Vlad Mihalcea blog |
| 3 | **Constant Arrival Rate** | K6 `constant-arrival-rate` executor: fixed req/s regardless of response time | Measures true server capacity (TPS) | Can overwhelm server if rate too high | TPS measurement under controlled conditions | K6 scenarios docs |
| 4 | **Profile-based Cache Config** | Spring profiles: `test-cache-none`, `test-cache-full`, `test-cache-partial` | Clean separation, no `@DirtiesContext` | Multiple app starts in CI | Cache encryption benchmarking | Brainstorm notes |
| 5 | **WireMock Latency Simulation** | `WireMock.stubFor(get(...).willReturn(aResponse().withFixedDelay(500)))` | Deterministic, reproducible | Not real network conditions | Testing circuit breaker / timeout behavior | WireMock docs |
| 6 | **JMH Separate Source Set** | `src/jmh/kotlin/` with `@Benchmark` annotations, isolated from Spring context | Pure CPU measurement, no I/O noise | No Spring context, manual setup | Encryption/serialization algorithm benchmarking | JMH plugin docs |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Cụ thể external service nào của auth-service sẽ được WireMock đầu tiên? | ✅ | Pre_openspec ghi nhận là open question — cần input từ team. SSO provider (OAuth2) là ứng viên mạnh nhất. | Low — WireMock setup là generic, service-specific config dễ thêm sau |
| 2 | Encryption key cho Testcontainers cache tests — random hay fixed? | ✅ | Brainstorm notes ghi nhận open question. Fixed key đơn giản hơn, random key realistic hơn. | Low — implementation detail, default fixed key cho reproducibility |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-21
> **Next step**: Comparison Analysis (comparison_analysis.md)
