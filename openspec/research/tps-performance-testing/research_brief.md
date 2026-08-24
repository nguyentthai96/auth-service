# Research Brief: TPS Performance Testing

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | TPS Performance Testing |
| **Ngày tạo** | 2026-08-21 |
| **Input source** | name (từ pipeline feature) |
| **Input content** | Cung cấp phương pháp và công cụ để viết integration tests nhằm đánh giá performance (TPS), I/O Database/Cache, và API End-to-End — bao gồm SQL query count assertion, WireMock HTTP client latency simulation, K6 E2E load testing, JMH microbenchmark, và cache encryption correctness validation. |
| **Người yêu cầu** | Pipeline (headless) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Auth-service đã có cơ sở hạ tầng K6 load testing sơ bộ (`tests/load/auth_flow.js`, `tests/load/profile_flow.js`) và 2 Gradle tasks (`k6Run`, `k6ProfileRun`). Tuy nhiên, hệ thống thiếu:

1. **SQL query count assertion** — không có cơ chế phát hiện N+1 queries trong integration tests. Dev phải đọc log thủ công.
2. **HTTP Client simulation** — chưa có WireMock setup để giả lập latency của external services (SSO providers, notification).
3. **Comprehensive K6 scenarios** — scripts hiện tại chỉ 50 VUs / 10s, không đủ cho TPS benchmarking thực tế. Thiếu scenario cho cache encryption modes (NONE/FULL/PARTIAL).
4. **JMH microbenchmarking** — chưa có benchmark cho encryption/serialization overhead trong `base-cache-starter`.
5. **Cache encryption correctness** — chưa có integration test verify raw Redis data đúng format encrypted/unencrypted theo config.
6. **CI/CD fail-fast** — K6 thresholds chưa tích hợp vào pipeline CI/CD.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Thiết lập framework `assertQueryCount` dựa trên `datasource-proxy` để phát hiện N+1 queries tự động trong integration tests.
- [x] Objective 2: Mở rộng K6 scripts với 500 VUs, multiple scenarios (auth, profile, cache modes), và CI/CD thresholds.
- [x] Objective 3: Tích hợp WireMock để test HTTP client I/O bottleneck.
- [x] Objective 4: Benchmark JMH cho encryption/serialization overhead (AES-GCM, JSON serialization).
- [x] Objective 5: Thêm integration tests verify cache encryption correctness qua Redis raw data inspection.
- [x] Objective 6: Cấu hình K6 thresholds (p95 < 200ms, error rate < 1%) để fail CI/CD pipeline khi performance regression.

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| `assertQueryCount` helper/annotation dùng datasource-proxy | Database query optimization (chỉ phát hiện, không fix) |
| WireMock simulation cho external HTTP calls | Actual external service integration tests |
| K6 E2E load testing scripts (auth_flow, profile_flow) | Stress/soak/spike testing scenarios |
| K6 Gradle tasks + CI/CD threshold config | Grafana/InfluxDB result visualization |
| JMH microbenchmark cho encryption/serialization | JMH benchmark cho toàn bộ business logic |
| Integration test cache encryption correctness | Cache performance tuning |
| Auth-service + base-testing-starter modules | Các services khác (account-service, notification-service) |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `performance testing`
- `TPS (Transactions Per Second)`
- `load testing`
- `K6 load testing`
- `datasource-proxy`
- `SQL query count assertion`
- `JMH microbenchmark`

### 3.2 Secondary Keywords
- `WireMock latency simulation`
- `N+1 query detection`
- `Spring Boot integration testing`
- `cache encryption testing`
- `Testcontainers Redis`
- `CI/CD performance gate`
- `Grafana K6`

### 3.3 Domain-Specific Terms
- `TPS`: Transactions Per Second — số giao dịch hoàn thành trên giây, đo lường throughput.
- `P95 latency`: 95th percentile response time — 95% requests hoàn thành dưới ngưỡng này.
- `N+1 query`: Anti-pattern JPA/Hibernate khi lazy loading tạo N+1 SQL queries thay vì 1.
- `datasource-proxy`: Thư viện Java intercept JDBC DataSource để đếm/log SQL queries.
- `JMH (Java Microbenchmark Harness)`: Framework benchmark chính xác cho JVM code.
- `WireMock`: Mock server giả lập HTTP APIs cho testing.
- `AES-GCM`: Thuật toán mã hóa authenticated encryption dùng cho cache data.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"K6 load testing Spring Boot authentication API TPS"` | K6 scenarios | High |
| 2 | `"datasource-proxy assertQueryCount Spring Boot JPA N+1"` | SQL assertion | High |
| 3 | `"JMH gradle plugin Kotlin benchmark encryption"` | JMH setup | Medium |
| 4 | `"WireMock Spring Boot external API latency simulation"` | WireMock | Medium |
| 5 | `"K6 CI/CD pipeline threshold fail fast"` | CI integration | High |
| 6 | `"Redis cache encryption integration test Testcontainers"` | Cache test | Medium |
| 7 | `"performance testing framework comparison Gatling K6 JMeter"` | Comparison | Medium |
| 8 | `"Spring Boot performance testing best practices 2024"` | Best practices | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| K6 Load Test (auth_flow.js) | `tests/load/auth_flow.js` | High | 50 VUs, 10s, POST `/api/v1/auth/login`, p95<200ms threshold |
| K6 Load Test (profile_flow.js) | `tests/load/profile_flow.js` | High | 50 VUs, 10s, GET `/api/v1/profiles/me`, static test token |
| K6 Gradle Tasks | `build.gradle.kts` (k6Run, k6ProfileRun) | High | Docker-based K6 execution, `--network host` |
| CacheEncryptionIntegrationTest | `src/test/.../CacheEncryptionIntegrationTest.kt` | High | Existing cache encryption test (basic) |
| TokenBlacklistCacheServiceTest | `src/test/.../TokenBlacklistCacheServiceTest.kt` | Medium | Three-tier cache unit tests |
| base-cache-starter | Dependency (com.ntt:base-cache-starter) | High | Caffeine L1 + Redis L2, encryption modes |
| base-testing-starter | Dependency (com.ntt:base-testing-starter) | High | Shared test utilities — target for assertQueryCount |
| Resilience4j Circuit Breaker | `build.gradle.kts` dependency | Medium | External service call protection |
| Spring Security Filter Chain | `SecurityConfig.kt`, `JwtAuthFilter.kt` | Medium | Auth flow interceptors affecting TPS |
| Testcontainers | Implied by H2 + test configs | Medium | Container-based integration testing |

### 4.2 Existing Code Patterns

- **Architecture**: Clean Architecture — `domain/model`, `domain/event`, `application/command`, `application/query`, `adapter/in/web`, `adapter/out/persistence`
- **Testing**: JUnit 5 + Mockito-Kotlin + ArchUnit. Unit tests in `src/test/kotlin/`. Integration tests use `@SpringBootTest` or `@WebMvcTest`.
- **Load Testing**: K6 via Docker (`grafana/k6`), executed through Gradle `Exec` tasks. Scripts use ES6 module syntax.
- **Database**: PostgreSQL 17 with Flyway migrations. H2 in-memory for unit tests.
- **Cache**: Caffeine L1 (local) + Redis L2 (distributed) via `base-cache-starter`. Encryption support (NONE/FULL/PARTIAL).
- **Event Sourcing**: `eventsourcing-utils` for domain events (TokenIssuedEvent, UserRegisteredEvent, etc.)
- **Security**: JWT (jjwt library, RS256), Spring Security, OAuth2 client+resource-server, TOTP MFA, E2EE via Google Tink.

### 4.3 Tech Stack Constraints
- **Language**: Kotlin (JVM 25)
- **Framework**: Spring Boot (implied 3.x by Spring Cloud + Spring Security 6)
- **Database**: PostgreSQL 17-alpine (Docker)
- **Cache**: Redis 7.4-alpine + Caffeine
- **Build tool**: Gradle (Kotlin DSL) with custom convention plugin `ntt.spring-app-conventions`
- **Container**: Docker / Docker Compose
- **CI/CD**: Gradle tasks → Docker-based K6 runs
- **Test libraries**: JUnit 5, Mockito-Kotlin, ArchUnit, BouncyCastle, H2 (test)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| PostgreSQL DataSource | Database | `build.gradle.kts` (spring-data-jpa, postgresql) | Target for datasource-proxy wrapping |
| Redis (base-cache-starter) | Cache | `RedisConfig.kt`, `base-cache-starter` | Target for cache encryption correctness tests |
| K6 Docker Container | Tool | `tests/load/`, `build.gradle.kts` | Existing — needs expansion |
| Spring Security Filter Chain | Filter | `SecurityConfig.kt`, `JwtAuthFilter.kt` | Affects auth flow TPS |
| Kafka | Message Queue | `KafkaConfig.kt`, `spring-kafka` | Event consumers — potential I/O bottleneck |
| External SSO Providers | HTTP Client | `HttpClientConfig.kt`, OAuth2 | Target for WireMock simulation |
| base-testing-starter | Library | Dependency | Target for assertQueryCount utility |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: `datasource-proxy` vs `p6spy` — tool nào phù hợp hơn cho SQL query count assertion trong Spring Boot?
- [x] Q2: K6 vs Gatling vs JMeter — tool nào tối ưu cho auth service TPS testing trong CI/CD pipeline?
- [x] Q3: JMH Gradle plugin nào active nhất và hỗ trợ Kotlin tốt nhất (me.champeau.jmh)?
- [x] Q4: WireMock standalone vs Spring Cloud Contract WireMock — khi nào dùng cái nào?
- [x] Q5: Làm sao verify raw Redis data đúng format encrypted khi dùng Testcontainers?
- [x] Q6: K6 thresholds integration với CI/CD — exit code mechanism hoạt động thế nào?
- [x] Q7: JVM tuning parameters nào ảnh hưởng TPS (ZGC, heap size)?

### 5.2 Assumptions cần verify
- [x] A1: `base-testing-starter` có thể thêm dependency `datasource-proxy` mà không conflict.
- [x] A2: K6 Docker image `grafana/k6` hỗ trợ threshold-based exit codes (exit 99 khi fail).
- [x] A3: JMH Gradle plugin `me.champeau.jmh` tương thích Kotlin + JVM 25.
- [x] A4: Redis Testcontainers cho phép connect trực tiếp để đọc raw data.

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ thông tin cho 6 FR trong pre_openspec | ≥ 5 sources |
| Open source options | Đánh giá ≥ 3 tools/frameworks | ≥ 3 repos evaluated |
| Gap analysis | Xác định gaps giữa current system và target | All critical gaps identified |
| Business analysis | Use cases cho từng loại test | All UCs documented |
| Technical spec | Đặc tả agent-ready cho implementation | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
