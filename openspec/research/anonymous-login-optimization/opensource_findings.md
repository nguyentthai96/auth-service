# Kết quả tìm kiếm Open Source: Anonymous Login Optimization

> Đánh giá các dự án open source và thư viện đã triển khai các pattern tối ưu hóa liên quan — Redis pipelining, sliding window rate limiting, distributed locking.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization (Redis perf, rate limiting, lock hardening, observability) |
| **Ngày tìm kiếm** | 2025-07-15 |
| **Số dự án tìm thấy** | 7 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot 3.x, Kotlin, Redis 7+, Lettuce, Micrometer |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"Redis rate limiting library Java Spring Boot sliding window"` | 4 kết quả relevant | bucket4j, resilience4j, Redis-based custom |
| 2 | `"Redisson distributed lock Spring Boot"` | 3 kết quả relevant | Redisson, Spring Integration, custom SETNX |
| 3 | `"Redis pipelining Spring Data optimization library"` | 3 kết quả relevant | Lettuce pipeline, Spring Data executePipelined |
| 4 | `"Redis Lua script rate limiting atomic operations"` | 4 kết quả relevant | Various Lua script implementations |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Bucket4j | https://github.com/bucket4j/bucket4j | 2.4k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | Redisson | https://github.com/redisson/redisson | 23k+ | Active (daily) | Apache 2.0 | ✅ Có |
| 3 | Resilience4j | https://github.com/resilience4j/resilience4j | 9.7k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 4 | Spring Data Redis (Pipeline) | https://github.com/spring-projects/spring-data-redis | 1.8k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Lettuce | https://github.com/lettuce-io/lettuce-core | 5.5k+ | Active (weekly) | Apache 2.0 | ❌ Không (already in use via Spring Boot) |
| 6 | Spring Integration Redis Lock | https://github.com/spring-projects/spring-integration | 1.6k+ | Active (weekly) | Apache 2.0 | ❌ Không (too heavy for this use case) |
| 7 | RateLimitJ | https://github.com/mokies/ratelimitj | 400+ | Inactive (2+ years) | Apache 2.0 | ❌ Không (inactive, archived) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing optimization features needed | Has basics | Full-featured for the optimization area |
| **Applicability** (phù hợp tech stack) | 15% | Different stack/language | Partial fit | Same stack (Spring Boot + Redis), easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples + guides |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Bucket4j

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Token bucket and sliding window algorithms; Redis, Hazelcast, Infinispan backends; bandwidth limiting |
| Applicability | 8 | 15% | 1.20 | Java library, Spring Boot integration available, supports Lettuce and Jedis for Redis backend |
| Activity | 7 | 15% | 1.05 | Regular releases, active maintenance, responsive to issues |
| Documentation | 8 | 15% | 1.20 | Comprehensive documentation with examples |
| Code quality | 8 | 15% | 1.20 | Good test coverage, clean codebase |
| Community | 7 | 10% | 0.70 | 2.4k+ stars, active community |
| Popularity | 7 | 10% | 0.70 | Growing adoption, well-known in JVM ecosystem |
| **Tổng điểm** | | | **7.85**/10 | |

#### Redisson

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Complete distributed objects: locks (Redlock, fair, read-write), rate limiters, atomic counters, pipelining |
| Applicability | 7 | 15% | 1.05 | Java library, Spring Boot starter available, but replaces default Lettuce client |
| Activity | 9 | 15% | 1.35 | Daily commits, very active development |
| Documentation | 9 | 15% | 1.35 | Excellent wiki, API docs, migration guides |
| Code quality | 8 | 15% | 1.20 | Good test coverage, enterprise-grade |
| Community | 10 | 10% | 1.00 | 23k+ stars, massive community |
| Popularity | 10 | 10% | 1.00 | Most popular Redis client library for Java |
| **Tổng điểm** | | | **8.95**/10 | |

#### Resilience4j

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 6 | 20% | 1.20 | Has RateLimiter but in-memory only (no Redis backend); circuit breaker and retry modules excellent |
| Applicability | 7 | 15% | 1.05 | First-class Spring Boot support, easy integration |
| Activity | 6 | 15% | 0.90 | Monthly commits, stable but slower development |
| Documentation | 9 | 15% | 1.35 | Excellent docs, many examples, well-documented patterns |
| Code quality | 9 | 15% | 1.35 | Very clean code, comprehensive tests |
| Community | 9 | 10% | 0.90 | 9.7k+ stars, active community |
| Popularity | 9 | 10% | 0.90 | De facto standard for resilience patterns in Java |
| **Tổng điểm** | | | **7.65**/10 | |

#### Spring Data Redis (Pipeline + Lua)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Pipeline via executePipelined(), Lua via execute(RedisScript), MGET/MSET — not a complete optimization library |
| Applicability | 10 | 15% | 1.50 | Already in project — zero migration, same StringRedisTemplate |
| Activity | 9 | 15% | 1.35 | Part of Spring ecosystem, very active, weekly commits |
| Documentation | 7 | 15% | 1.05 | Pipeline/Lua docs exist but sparse; community examples fill gaps |
| Code quality | 9 | 15% | 1.35 | Excellent test coverage, Spring team quality |
| Community | 8 | 10% | 0.80 | 1.8k+ stars (Spring Data Redis module specifically) |
| Popularity | 10 | 10% | 1.00 | Default Redis integration for Spring Boot — ubiquitous |
| **Tổng điểm** | | | **8.45**/10 | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Bucket4j | 9 | 8 | 7 | 8 | 8 | 7 | 7 | **7.85** |
| 2 | Redisson | 10 | 7 | 9 | 9 | 8 | 10 | 10 | **8.95** |
| 3 | Resilience4j | 6 | 7 | 6 | 9 | 9 | 9 | 9 | **7.65** |
| 4 | Spring Data Redis (Pipeline) | 7 | 10 | 9 | 7 | 9 | 8 | 10 | **8.45** |

---

## 4. Gap Analysis chi tiết

### Bucket4j — Gap Analysis

**Overall Score**: 7.85 / 10
**URL**: https://github.com/bucket4j/bucket4j

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Sliding window rate limiting | ✅ | | Token bucket = smooth rate limiting without burst-at-boundary | Not exactly sliding window — uses token refill, different algorithm |
| Redis backend | ✅ | | Supports Lettuce, Jedis, Redisson as backends | Adds dependency, requires bucket configuration |
| Spring Boot integration | ✅ | | `bucket4j-spring-boot-starter` available | Annotation-based — may not fit CQRS handler pattern |
| Pipelining | | ❌ | - | Rate limiting only — not a general Redis pipelining solution |
| Distributed locking | | ❌ | - | Not in scope |
| Observability | ⚠️ | | Micrometer integration exists | Limited — only rate limit metrics |
| Kotlin support | ✅ | | Java library, works in Kotlin | No Kotlin-specific APIs |

**Verdict**: Có thể dùng trực tiếp — For rate limiting optimization only. Bucket4j's token bucket algorithm solves the burst-at-boundary problem of fixed-window rate limiting.
**Recommendation**: Tham khảo pattern — Use Bucket4j's token bucket algorithm concept but implement using existing `StringRedisTemplate` with Lua scripts to avoid adding a new dependency.
**Reasoning**: Adding Bucket4j as a dependency is viable but introduces a new library for a single concern that can be solved with a custom Lua script (10-15 lines). The auth-service already has Redis Lua capability. Reference Bucket4j's algorithm, implement natively.

### Redisson — Gap Analysis

**Overall Score**: 8.95 / 10
**URL**: https://github.com/redisson/redisson

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Distributed locking (Redlock) | ✅ | | `RLock`, `RedLock`, `FairLock` — full distributed lock implementation | Requires replacing Lettuce with Redisson as Redis client |
| Rate limiting | ✅ | | `RRateLimiter` — Redis-based, distributed | Different API than Spring Data Redis |
| Pipelining/Batching | ✅ | | `RBatch` for pipelining multiple operations | Redisson-specific API, not Spring Data compatible |
| Spring Boot integration | ✅ | | `redisson-spring-boot-starter` | Replaces default Lettuce auto-configuration |
| Observability | ✅ | | Micrometer integration, tracing support | - |
| Kotlin support | ✅ | | Coroutine support via `redisson-kotlin` | - |
| Migration complexity | | ❌ | - | Replacing StringRedisTemplate with Redisson API is a significant migration |

**Verdict**: Tham khảo pattern — Redisson is extremely feature-rich but adopting it requires replacing the existing Redis client layer (StringRedisTemplate → Redisson API). Too invasive for optimization scope.
**Recommendation**: Tham khảo pattern
**Reasoning**: While Redisson has the best distributed lock implementation (Redlock), adopting it means migrating all existing Redis code from StringRedisTemplate. This is a major refactor beyond the optimization scope. Better to reference Redisson's lock patterns and implement targeted improvements using existing Spring Data Redis.

### Resilience4j — Gap Analysis

**Overall Score**: 7.65 / 10
**URL**: https://github.com/resilience4j/resilience4j

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Rate limiting | ⚠️ | | `RateLimiter` module exists | In-memory only — no Redis backend |
| Circuit breaker | ✅ | | Excellent circuit breaker for Redis failures | Could wrap Redis calls |
| Retry | ✅ | | Retry with exponential backoff | Useful for Redis failures |
| Redis backend | | ❌ | - | All rate limiters are in-memory |
| Distributed locking | | ❌ | - | Not in scope |
| Spring Boot integration | ✅ | | First-class Spring Boot support | - |

**Verdict**: Tham khảo pattern — Resilience4j is excellent for circuit breaker/retry patterns around Redis calls, but its rate limiter is in-memory only (not suitable for distributed systems).
**Recommendation**: Tham khảo pattern — Consider adding circuit breaker around Redis calls for anonymous session creation (fail-fast when Redis is down).
**Reasoning**: The auth-service could benefit from Resilience4j's circuit breaker for Redis failure scenarios (currently handles with try-catch + fail-open). However, rate limiting must remain Redis-based for distributed enforcement.

### Spring Data Redis Pipeline — Gap Analysis

**Overall Score**: 8.45 / 10
**URL**: https://github.com/spring-projects/spring-data-redis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Redis pipelining | ✅ | | `executePipelined(RedisCallback)` — batch multiple commands | Callback-based API, less intuitive |
| Lua scripts | ✅ | | `execute(RedisScript, keys, args)` — Lua execution | Script management requires `DefaultRedisScript` bean |
| Batch operations | ✅ | | `multiGet()`, `multiSet()` on ValueOperations | Requires pre-collecting keys |
| Already in project | ✅ | | Zero migration — existing `StringRedisTemplate` | - |
| Documentation | ⚠️ | | Pipeline/Lua docs exist but sparse | Community examples fill the gap |
| High-level optimization APIs | | ❌ | - | No automatic pipelining — manual work required |

**Verdict**: Dùng trực tiếp — Spring Data Redis's pipeline and Lua script capabilities are the optimal path for optimization. No new dependencies required.
**Recommendation**: Dùng trực tiếp
**Reasoning**: The project already uses StringRedisTemplate. Using `executePipelined()` for session creation and `execute(RedisScript)` for atomic rate limiting requires zero new dependencies and minimal code changes. This is the recommended approach.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Redisson | 8.95/10 | Tham khảo pattern | Distributed lock patterns (Redlock algorithm reference) |
| 🥈 2 | Spring Data Redis (Pipeline) | 8.45/10 | Dùng trực tiếp | Redis pipelining, Lua scripts — zero new dependencies |
| 🥉 3 | Bucket4j | 7.85/10 | Tham khảo pattern | Token bucket algorithm reference for rate limiting |
| 4 | Resilience4j | 7.65/10 | Tham khảo pattern | Circuit breaker around Redis calls |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Optimize in-place using Spring Data Redis** (pipeline + Lua) with algorithm references from Bucket4j (rate limiting) and Redisson (distributed lock) | No new dependencies needed. Spring Data Redis provides `executePipelined()` and `execute(RedisScript)` which are sufficient for all optimization goals. External libraries would add dependency overhead for single-concern improvements that can be achieved with 30-50 lines of Lua script and pipeline code. | Spring Data Redis docs, Redisson Redlock algorithm paper, Bucket4j token bucket algorithm |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> **Next step**: Comparison Analysis (comparison_analysis.md)
