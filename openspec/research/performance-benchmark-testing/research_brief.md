# Research Brief — Performance Benchmark & TPS Testing

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature Name** | Performance Benchmark & TPS Testing Strategy |
| **Input Mode** | Idea — Mô tả tự do từ user |
| **Priority** | HIGH — Yêu cầu nghiên cứu toàn diện về hiệu năng hệ thống |
| **Scope** | auth-service (Spring Boot / Kotlin) + observability stack |

### Mô tả
Nghiên cứu và thiết kế chiến lược performance testing toàn diện cho hệ thống auth-service, bao gồm:
- **TPS Benchmark**: Đo lường throughput tối đa dưới full load với hàng trăm/ngàn user đồng thời
- **API-level Testing**: Test từng API endpoint riêng lẻ để xác định baseline
- **API Cluster Testing**: Test chuỗi API phụ thuộc nhau (ví dụ: login → get profile → refresh token)
- **Infrastructure Testing**: HTTP server capacity, connection pool, cache throughput, encryption overhead
- **Bottleneck Identification**: Tìm điểm nghẽn qua từng layer (HTTP → Application → Database → Cache → Crypto)
- **Progressive Optimization**: Report chi tiết để cải thiện dần theo từng iteration

---

## 2. Research Keywords

1. `Spring Boot performance testing TPS benchmark`
2. `k6 load testing multi-scenario API chain`
3. `Gatling Kotlin DSL performance testing`
4. `JMH microbenchmark Spring Boot`
5. `HikariCP connection pool tuning PostgreSQL`
6. `Redis Lettuce performance pipelining benchmark`
7. `Virtual threads Tomcat throughput optimization`
8. `ZGC garbage collector low-latency`
9. `AES-GCM encryption TPS overhead benchmark`
10. `Prometheus Grafana k6 performance dashboard observability`
11. `Performance bottleneck identification USE method`
12. `Spring Boot Actuator Micrometer metrics performance`

---

## 3. Search Queries

| # | Query | Purpose |
|---|-------|---------|
| 1 | "Spring Boot 3 performance benchmark k6 vs Gatling 2025" | So sánh công cụ load testing |
| 2 | "HikariCP optimal pool size formula PostgreSQL" | Tối ưu DB connection pool |
| 3 | "Redis Lettuce connection tuning Spring Boot" | Tối ưu cache connection |
| 4 | "k6 multi-scenario sequential API chain testing" | Pattern test chuỗi API |
| 5 | "JMH benchmark AES encryption throughput" | Benchmark overhead mã hóa |
| 6 | "Virtual threads pinning detection profiling" | Phát hiện vấn đề virtual threads |
| 7 | "Grafana k6 Prometheus real-time dashboard" | Observability stack |
| 8 | "USE method performance bottleneck systematic" | Phương pháp tìm bottleneck |

---

## 4. Current System Analysis

### 4.1 Related Features — Existing Performance Testing

| Component | Status | Location |
|-----------|--------|----------|
| K6 Load Tests | ✅ Có sẵn | `tests/load/auth_flow.js` — Login 500 VUs, Profile 100 req/s |
| K6 Cache Benchmark | ✅ Có sẵn | `tests/load/cache_benchmark.js` — So sánh encryption modes |
| K6 Profile Flow | ✅ Có sẵn | `tests/load/profile_flow.js` — Profile endpoint 100 req/s |
| K6 Helpers | ✅ Có sẵn | `tests/load/helpers.js` — Login helper, auth headers |
| JMH Encryption | ✅ Có sẵn | `src/jmh/.../EncryptionBenchmark.kt` — AES-GCM microbenchmark |
| JMH Serialization | ✅ Có sẵn | `src/jmh/.../SerializationBenchmark.kt` — Jackson 3.x benchmark |
| Gradle K6 Tasks | ✅ Có sẵn | `k6Run`, `k6ProfileRun`, `k6CacheBenchmark` |
| Gradle JMH Config | ✅ Có sẵn | Fork=2, Warmup=5, Iterations=5, Mode=thrpt |

**Nhận xét**: Hệ thống đã có nền tảng tốt với K6 + JMH. Cần mở rộng thêm:
- Chưa có test cho chuỗi API dependencies
- Chưa có test cho từng API endpoint riêng lẻ (chỉ có login + profile)
- Chưa có infrastructure benchmark (DB pool, Redis, HTTP server)
- Chưa có observability integration (Prometheus/Grafana)
- Chưa có systematic bottleneck identification methodology

### 4.2 Existing Patterns

| Pattern | Detail |
|---------|--------|
| **Architecture** | Clean Architecture (adapter/application/domain layers) |
| **Virtual Threads** | ✅ Enabled (`spring.threads.virtual.enabled: true`) |
| **Cache** | L1 Caffeine + L2 Redis (Lettuce), TTL-based |
| **Database** | PostgreSQL 17 via HikariCP (default config — chưa tune) |
| **Redis** | Redis 7.4, GenericJackson2JsonRedisSerializer |
| **Resilience** | Resilience4j CircuitBreaker (SSO + Captcha) |
| **Encryption** | AES-GCM (Google Tink), X25519 key exchange |
| **HTTP Client** | RestClient + SimpleClientHttpRequestFactory |
| **Rate Limiting** | Redis Lua script (sliding window) |
| **Server** | Tomcat on port 8081 |

### 4.3 Tech Stack Constraints

| Technology | Version | Notes |
|------------|---------|-------|
| Kotlin | Latest | Primary language |
| Spring Boot | 3.x | With virtual threads |
| PostgreSQL | 17-alpine | Docker compose |
| Redis | 7.4-alpine | Docker compose |
| JMH | 1.37 | Microbenchmark |
| K6 | Latest (Docker) | Load testing |
| Jackson | 3.x | Serialization |
| Resilience4j | 2.2.0 | Circuit breaker |
| Google Tink | 1.15.0 | Encryption |
| Caffeine | Latest | L1 cache |
| HikariCP | Default (Spring Boot) | DB pool |
| Lettuce | Default (Spring Boot) | Redis client |

### 4.4 API Endpoints (17 Controllers)

| Controller | Domain | Key APIs |
|------------|--------|----------|
| `TokenController` | Auth | Login, Refresh, Validate |
| `SessionController` | Auth | List sessions, Revoke |
| `MfaController` | Auth | Setup TOTP, Verify OTP |
| `SsoController` | Auth | SSO Login, Callback |
| `CaptchaController` | Auth | Generate, Verify challenge |
| `KeyExchangeController` | Auth | X25519 key exchange |
| `AnonymousAuthController` | Auth | Anonymous token, Renew |
| `DeviceController` | Auth | Trusted devices management |
| `AccountLifecycleController` | Auth | Register, Deactivate |
| `AdminSessionController` | Auth | Admin session management |
| `CqrsAuthController` | Auth | CQRS login/register |
| `EventStoreController` | Auth | Event sourcing queries |
| `InternalApiController` | Auth | Service-to-service validation |
| `RateLimitAdminController` | Auth | Rate limit admin |
| `PolicyController` | PBAC | Policy CRUD |
| `RbacControllers` | RBAC | Role/User CRUD |
| `RolePermissionController` | RBAC | Role-Permission mapping |

### 4.5 Identified Performance-Critical Paths

```
1. Login Flow (heaviest):
   Captcha → Login → MFA Check → JWT Generation → Session Create → Event Store → Redis Rate Limit

2. Token Refresh:
   JWT Validate → RefreshToken Lookup (DB) → New JWT Generate → Old Token Revoke

3. Profile Access (cache-dependent):
   JWT Validate → Permission Check (L1→L2→DB) → Profile Fetch

4. Key Exchange (crypto-heavy):
   X25519 Key Generation → DH Agreement → AES Key Derive

5. SSO Login (external dependency):
   Redirect → OAuth2 Token Exchange → UserInfo Fetch → User Provision → JWT Issue
```

---

## 5. Research Gaps to Investigate

1. **HikariCP default settings** — Chưa có explicit config, cần benchmark để tìm optimal pool size
2. **Tomcat thread config** — Virtual threads enabled nhưng chưa đo throughput impact
3. **Redis serialization overhead** — GenericJackson2JsonRedisSerializer có thể chậm hơn alternatives
4. **Encryption TPS impact** — AES-GCM + X25519 overhead trên request lifecycle
5. **JPA N+1 potential** — Chưa có datasource-proxy runtime monitoring
6. **Observability gap** — Actuator chỉ expose health/info/metrics, thiếu prometheus endpoint
7. **GC tuning** — Chưa có JVM flags config cho ZGC
