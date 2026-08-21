# Kết quả tìm kiếm Open Source: Anonymous Login Optimization

> Đánh giá các dự án open source và thư viện đã triển khai các pattern tối ưu hóa liên quan — Redis pipelining, sliding window rate limiting, distributed locking, atomic operations.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization (Redis perf, rate limiting, lock hardening, atomic data store, observability) |
| **Ngày tìm kiếm** | 2025-07-15 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot 3.x, Kotlin, Redis 7+, Lettuce, Micrometer |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"Redis rate limiting library Java Spring Boot sliding window"` | 4 kết quả relevant | Bucket4j, Resilience4j, Redis-based custom implementations |
| 2 | `"Redisson distributed lock Spring Boot"` | 3 kết quả relevant | Redisson, Spring Integration, custom SETNX patterns |
| 3 | `"Redis pipelining Spring Data optimization library"` | 3 kết quả relevant | Lettuce pipeline, Spring Data executePipelined |
| 4 | `"Redis Lua script rate limiting atomic operations"` | 4 kết quả relevant | Various Lua script implementations for atomic multi-command operations |
| 5 | `"Redis atomic check-and-set Lua TOCTOU"` | 2 kết quả relevant | Lua-based atomic patterns for race condition prevention |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Bucket4j | https://github.com/bucket4j/bucket4j | 2.4k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | Redisson | https://github.com/redisson/redisson | 23k+ | Active (daily) | Apache 2.0 | ✅ Có |
| 3 | Resilience4j | https://github.com/resilience4j/resilience4j | 9.7k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 4 | Spring Data Redis (Pipeline) | https://github.com/spring-projects/spring-data-redis | 1.8k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Lettuce | https://github.com/lettuce-io/lettuce-core | 5.5k+ | Active (weekly) | Apache 2.0 | ❌ Không (already in use via Spring Boot auto-configuration) |
| 6 | Spring Integration Redis Lock | https://github.com/spring-projects/spring-integration | 1.6k+ | Active (weekly) | Apache 2.0 | ❌ Không (too heavy for this use case — full Spring Integration dependency) |
| 7 | RateLimitJ | https://github.com/mokies/ratelimitj | 400+ | Inactive (2+ years) | Apache 2.0 | ❌ Không (inactive/archived, no maintenance) |
| 8 | Redis4j-ratelimiter | https://github.com/redis/redis-ratelimiter | ~200 | Sporadic | Apache 2.0 | ❌ Không (small project, limited adoption) |

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
| Applicability | 8 | 15% | 1.20 | Java library, Spring Boot integration available via `bucket4j-spring-boot-starter`, supports Lettuce and Jedis |
| Activity | 7 | 15% | 1.05 | Regular releases, active maintenance, responsive to issues |
| Documentation | 8 | 15% | 1.20 | Comprehensive documentation site with examples and tutorials |
| Code quality | 8 | 15% | 1.20 | Good test coverage, clean codebase, well-structured modules |
| Community | 7 | 10% | 0.70 | 2.4k+ stars, active community discussions |
| Popularity | 7 | 10% | 0.70 | Growing adoption in JVM ecosystem, mentioned in many blog posts |
| **Tổng điểm** | | | **7.85**/10 | |

#### Redisson

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Complete distributed objects: locks (Redlock, fair, read-write), rate limiters, atomic counters, pipelining via RBatch |
| Applicability | 7 | 15% | 1.05 | Java library, Spring Boot starter available, but replaces default Lettuce client with Redisson API |
| Activity | 9 | 15% | 1.35 | Daily commits, very active development, commercial support available |
| Documentation | 9 | 15% | 1.35 | Excellent wiki, API docs, migration guides, commercial docs |
| Code quality | 8 | 15% | 1.20 | Good test coverage, enterprise-grade, battle-tested in production |
| Community | 10 | 10% | 1.00 | 23k+ stars, massive community, enterprise adoption |
| Popularity | 10 | 10% | 1.00 | Most popular Redis client library for Java, widely adopted by enterprises |
| **Tổng điểm** | | | **8.95**/10 | |

#### Resilience4j

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 6 | 20% | 1.20 | Has RateLimiter module but in-memory only (no Redis backend); excellent circuit breaker and retry modules |
| Applicability | 7 | 15% | 1.05 | First-class Spring Boot support via starter, annotation-based integration |
| Activity | 6 | 15% | 0.90 | Monthly commits, stable project, slower development pace |
| Documentation | 9 | 15% | 1.35 | Excellent docs, many examples, well-documented patterns and use cases |
| Code quality | 9 | 15% | 1.35 | Very clean code, comprehensive tests, functional style |
| Community | 9 | 10% | 0.90 | 9.7k+ stars, active community, conference talks |
| Popularity | 9 | 10% | 0.90 | De facto standard for resilience patterns in Java ecosystem |
| **Tổng điểm** | | | **7.65**/10 | |

#### Spring Data Redis (Pipeline + Lua)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Pipeline via `executePipelined()`, Lua via `execute(RedisScript)`, MGET/MSET — comprehensive but not a dedicated optimization library |
| Applicability | 10 | 15% | 1.50 | Already in project — zero migration needed, same StringRedisTemplate, same Lettuce driver |
| Activity | 9 | 15% | 1.35 | Part of Spring ecosystem, very active, weekly commits, Spring team quality |
| Documentation | 7 | 15% | 1.05 | Pipeline/Lua docs exist but sparse compared to other Spring Data docs; community examples fill gaps |
| Code quality | 9 | 15% | 1.35 | Excellent test coverage, Spring team code quality standards |
| Community | 8 | 10% | 0.80 | 1.8k+ stars for spring-data-redis module specifically |
| Popularity | 10 | 10% | 1.00 | Default Redis integration for Spring Boot — ubiquitous in Spring ecosystem |
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
| Sliding window rate limiting | ✅ | | Token bucket algorithm = smooth rate limiting without burst-at-boundary | Not exactly sliding window counter — uses token refill, different algorithm semantics |
| Redis backend | ✅ | | Supports Lettuce, Jedis, Redisson as backends | Adds dependency, requires bucket configuration per rate limit policy |
| Spring Boot integration | ✅ | | `bucket4j-spring-boot-starter` available | Annotation-based — may not fit CQRS handler pattern used in auth-service |
| Pipelining | | ❌ | - | Rate limiting only — not a general Redis pipelining solution |
| Distributed locking | | ❌ | - | Not in scope — no lock management |
| Atomic data operations | | ❌ | - | No TOCTOU-safe check-and-set patterns |
| Observability | ⚠️ | | Micrometer integration exists | Limited to rate limit metrics only |
| Kotlin support | ✅ | | Java library, works in Kotlin via interop | No Kotlin-specific APIs or coroutine support |

**Verdict**: Tham khảo pattern — Bucket4j's token bucket algorithm concept is useful reference but the auth-service already implements sliding window counter via custom Lua script which better fits the existing architecture.
**Recommendation**: Tham khảo pattern
**Reasoning**: Adding Bucket4j as a dependency introduces overhead for a single concern already solved by the custom `sliding_window_rate_limit.lua` (35 lines). The auth-service's Lua-based approach is more tailored: same `StringRedisTemplate`, same fail-open strategy, same `AnonymousRateLimitedException` integration.

### Redisson — Gap Analysis

**Overall Score**: 8.95 / 10
**URL**: https://github.com/redisson/redisson

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Distributed locking (Redlock) | ✅ | | `RLock`, `RedLock`, `FairLock`, `ReadWriteLock` — full distributed lock implementation | Requires replacing Lettuce with Redisson as Redis client |
| Rate limiting | ✅ | | `RRateLimiter` — Redis-based, distributed, multiple algorithms | Different API than Spring Data Redis, Redisson-specific |
| Pipelining/Batching | ✅ | | `RBatch` for pipelining multiple operations | Redisson-specific API, not compatible with StringRedisTemplate |
| Atomic operations | ✅ | | `RAtomicLong`, `RBucket` with CAS operations | Redisson API only |
| Spring Boot integration | ✅ | | `redisson-spring-boot-starter` with auto-configuration | Replaces default Lettuce auto-configuration entirely |
| Observability | ✅ | | Micrometer integration, tracing support | - |
| Kotlin support | ✅ | | Coroutine support via `redisson-kotlin` module | - |
| Migration complexity | | ❌ | - | Replacing all StringRedisTemplate usage with Redisson API is a major refactor |

**Verdict**: Tham khảo pattern — Redisson is extremely feature-rich but adopting it requires replacing the entire Redis client layer (StringRedisTemplate → Redisson API). Far too invasive for an optimization initiative.
**Recommendation**: Tham khảo pattern
**Reasoning**: The auth-service uses StringRedisTemplate across all services (anonymous, login, RBAC, session). Migrating to Redisson would touch every Redis interaction in the codebase. The UUID-based lock ownership + Lua safe release pattern (already implemented) provides sufficient reliability for single-node Redis. Redisson's Redlock algorithm was referenced when designing the lock pattern.

### Resilience4j — Gap Analysis

**Overall Score**: 7.65 / 10
**URL**: https://github.com/resilience4j/resilience4j

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Rate limiting | ⚠️ | | `RateLimiter` module exists with multiple algorithms | In-memory only — no Redis backend for distributed enforcement |
| Circuit breaker | ✅ | | Excellent circuit breaker for wrapping Redis calls | Could protect against Redis failures gracefully |
| Retry | ✅ | | Retry with exponential backoff | Useful for transient Redis failures |
| Redis backend | | ❌ | - | All state management is in-memory, not distributed |
| Distributed locking | | ❌ | - | Not in scope |
| Spring Boot integration | ✅ | | First-class Spring Boot starter, annotation-based | Already in project's `build.gradle.kts` (resilience4j-spring-boot3) |
| Observability | ✅ | | Native Micrometer integration | - |

**Verdict**: Tham khảo pattern — Resilience4j is already a dependency in the project (for circuit breaker around external service calls, FR-019). Its rate limiter is in-memory only, unsuitable for distributed anonymous session rate limiting.
**Recommendation**: Tham khảo pattern — Consider using Resilience4j circuit breaker around Redis calls for anonymous sessions as a future enhancement (not in current optimization scope).
**Reasoning**: The auth-service already has Resilience4j as a dependency. Rate limiting must remain Redis-based for distributed enforcement. Circuit breaker could enhance the current fail-open strategy but is not a priority optimization.

### Spring Data Redis Pipeline — Gap Analysis

**Overall Score**: 8.45 / 10
**URL**: https://github.com/spring-projects/spring-data-redis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Redis pipelining | ✅ | | `executePipelined(RedisCallback)` — batch multiple commands in 1 RTT | Callback-based API, must return null from callback |
| Lua scripts | ✅ | | `execute(RedisScript, keys, args)` — full Lua execution support | Script management requires `DefaultRedisScript` bean setup |
| Batch operations | ✅ | | `multiGet()`, `multiSet()` on ValueOperations | Requires pre-collecting keys into lists |
| Already in project | ✅ | | Zero migration — existing `StringRedisTemplate` | - |
| EVALSHA caching | ✅ | | `DefaultRedisScript` automatically caches SHA1 hash | Transparent optimization after first execution |
| Documentation | ⚠️ | | Pipeline/Lua docs exist | Sparse compared to other Spring Data docs; examples are basic |
| High-level optimization APIs | | ❌ | - | No automatic pipelining or optimization hints — manual work required |

**Verdict**: Dùng trực tiếp — Spring Data Redis's pipeline and Lua script capabilities are already implemented in the auth-service as the primary optimization approach.
**Recommendation**: Dùng trực tiếp
**Reasoning**: Zero new dependencies. The project already uses `executePipelined()` for session creation and data transfer, and `execute(RedisScript)` for sliding window rate limiting, safe lock release, and atomic data store. All 3 Lua scripts are managed via `RedisLuaScriptConfig`. This is the proven approach.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Redisson | 8.95/10 | Tham khảo pattern | Distributed lock algorithm reference (Redlock, fencing tokens) |
| 🥈 2 | Spring Data Redis (Pipeline) | 8.45/10 | Dùng trực tiếp | Redis pipelining, Lua scripts — zero new dependencies (already implemented) |
| 🥉 3 | Bucket4j | 7.85/10 | Tham khảo pattern | Token bucket algorithm reference for rate limiting |
| 4 | Resilience4j | 7.65/10 | Tham khảo pattern | Circuit breaker around Redis calls (already a dependency, potential future use) |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Optimize in-place using Spring Data Redis** (pipeline + Lua) with algorithm references from Bucket4j (rate limiting) and Redisson (distributed lock) | No new dependencies needed. Spring Data Redis provides `executePipelined()` and `execute(RedisScript)` which are sufficient for all optimization goals. Already implemented with 3 Lua scripts, pipeline session creation, pipeline batch transfer, running size counter, and dual-mode rate limiting. | Spring Data Redis docs, Redisson Redlock algorithm paper, Bucket4j token bucket concept, Kleppmann safe lock analysis |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> **Next step**: Comparison Analysis (comparison_analysis.md)
