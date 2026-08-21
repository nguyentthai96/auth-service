# Research Brief: Anonymous Login Optimization

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Anonymous Login Optimization |
| **Ngày tạo** | 2025-07-15 |
| **Input source** | Name + Description (pipeline context) |
| **Input content** | Optimize the existing anonymous/guest authentication system in auth-service. Focus on: performance tuning for anonymous session creation (reduce Redis round-trips), session promotion reliability improvements, data transfer optimization (batch operations vs per-key), rate limiting enhancements (sliding window vs fixed window), token renewal efficiency, Redis memory footprint reduction, and observability improvements (metrics, tracing, alerting). |
| **Người yêu cầu** | Pipeline (headless mode) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already has a fully functional anonymous login system implemented with:
- `AnonymousSessionHandler` — CQRS handler creating anonymous sessions in Redis
- `AnonymousAuthController` — REST controller at `/api/v1/auth/anonymous` with 5 endpoints
- `SessionPromotionService` — orchestrates anonymous → authenticated promotion with distributed locking
- `AnonymousSessionDataService` — Redis CRUD for namespaced session data with size enforcement
- `AnonymousRateLimitService` — IP-based fixed-window rate limiting
- `RenewAnonymousTokenHandler` — token renewal with max renewal count enforcement
- `JwtService` extensions — `generateAnonymousToken()` and `parseAnonymousToken()`

The system works correctly but has several optimization opportunities identified through code review:

1. **Redis Round-Trip Reduction**: `AnonymousSessionHandler.handle()` makes 3 Redis calls sequentially (HSET + EXPIRE + rate limit check). These can be pipelined or combined with Lua scripts.
2. **Data Transfer Optimization**: `AnonymousSessionDataService.transferData()` uses per-key GET+SET in a loop via SCAN cursor. For sessions with many data keys, this creates N×2 Redis round-trips. Pipeline/batch operations would be more efficient.
3. **Rate Limiting Enhancement**: Current `AnonymousRateLimitService` uses fixed-window rate limiting (INCR + EXPIRE). Sliding window log or sliding window counter would provide smoother rate limiting without burst-at-boundary issues.
4. **Session Promotion Reliability**: `SessionPromotionService` uses simple SETNX for distributed lock. This lacks fencing tokens and could have issues in Redis failover scenarios.
5. **Observability Gaps**: Metrics exist (counters/timers) but no distributed tracing spans, no alerting thresholds defined, no dashboard configuration.
6. **Memory Optimization**: `getSessionDataSize()` uses SCAN to sum STRLEN of all keys — inefficient for frequent size checks. Could maintain a running total in the session hash.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Reduce Redis round-trips in anonymous session creation by 30-50% via pipelining/Lua scripts
- [x] Objective 2: Optimize data transfer during promotion using batch Redis operations (MGET/MSET or pipeline)
- [x] Objective 3: Evaluate and recommend sliding window rate limiting vs current fixed-window approach
- [x] Objective 4: Improve session promotion reliability with Redlock or fencing token patterns
- [x] Objective 5: Define observability strategy (OpenTelemetry spans, Grafana dashboards, alert rules)
- [x] Objective 6: Reduce per-session Redis memory footprint through key compression and data structure optimization

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Redis operation optimization (pipelining, Lua scripts, batch ops) | New anonymous session features (new endpoints, new data types) |
| Rate limiting algorithm improvement (sliding window) | UI/UX changes |
| Session promotion reliability hardening | Domain logic changes (cart merge strategies) |
| Observability and monitoring setup | Multi-tenant anonymous session isolation |
| Redis memory optimization | Migration to different cache provider |
| Performance benchmarking | Load testing infrastructure setup |
| Error handling improvements | Anonymous session analytics/reporting |
| Code cleanup and refactoring | Mobile SDK changes |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `Redis pipelining Spring Boot`
- `Redis Lua scripting Spring Data Redis`
- `sliding window rate limiting Redis`
- `Redis batch operations Java Kotlin`
- `distributed lock reliability Redlock`

### 3.2 Secondary Keywords
- `Redis memory optimization`
- `Redis SCAN performance`
- `Spring Boot Micrometer observability`
- `OpenTelemetry Redis tracing`
- `Redis pipelining StringRedisTemplate`
- `session promotion idempotency`
- `Redis hash memory footprint`
- `fixed window vs sliding window rate limit`

### 3.3 Domain-Specific Terms
- `Redis Pipelining`: Sending multiple Redis commands in a single network round-trip, reducing latency significantly for multi-command operations.
- `Sliding Window Rate Limiting`: A rate limiting algorithm that provides smooth enforcement without burst-at-boundary issues, using either sorted sets (log-based) or counter subdivisions.
- `Redlock`: A distributed lock algorithm by Redis creator Salvatore Sanfilippo, using N independent Redis instances for fault-tolerant locking.
- `Fencing Token`: A monotonically increasing token included with each lock acquisition, allowing downstream services to reject stale lock holders.
- `Redis Lua Scripting`: Server-side Lua scripts executed atomically by Redis, enabling complex multi-command operations without race conditions.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Redis pipelining Spring Data Redis StringRedisTemplate"` | Performance | High |
| 2 | `"sliding window rate limiting Redis implementation"` | Algorithm | High |
| 3 | `"Redis Lua script atomic operations Spring Boot"` | Performance | High |
| 4 | `"Redlock distributed lock reliability production"` | Reliability | Medium |
| 5 | `"Redis SCAN vs KEYS performance optimization"` | Performance | Medium |
| 6 | `"Redis memory optimization hash small values"` | Memory | Medium |
| 7 | `"OpenTelemetry Spring Boot Redis tracing"` | Observability | Medium |
| 8 | `"Redis MGET MSET batch operations Spring Data"` | Performance | High |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| Anonymous Session Creation | `auth.application.command.AnonymousSessionHandler` | High | 3 sequential Redis calls: rate limit check + HSET + EXPIRE |
| Anonymous Data Transfer | `auth.application.AnonymousSessionDataService.transferData()` | High | Per-key SCAN+GET+SET loop — N×2 Redis round-trips |
| Rate Limiting | `auth.application.AnonymousRateLimitService` | High | Fixed-window INCR+EXPIRE — burst-at-boundary issue |
| Session Promotion | `auth.application.SessionPromotionService` | High | SETNX lock — no fencing, no Redlock |
| Token Renewal | `auth.application.command.RenewAnonymousTokenHandler` | Medium | Multiple Redis calls: parse token + EXISTS + HGET + HINCRBY + EXPIRE + generate token |
| Data Size Calculation | `auth.application.AnonymousSessionDataService.getSessionDataSize()` | Medium | SCAN + STRLEN loop — expensive for frequent calls |
| Metrics | Across all anonymous services | Medium | Counter/timer metrics exist but no tracing spans |
| Login Rate Limiting | `auth.application.LoginRateLimitService` | Low | Same fixed-window pattern — optimization applies here too |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture with CQRS Handlers:
- `CommandHandler<C, R>` from `eventsourcing-utils`
- Controllers delegate to handlers, handlers use services
- Redis via `StringRedisTemplate` (not `RedisTemplate<String, Object>`)

**Redis Usage Pattern** (current):
```kotlin
// Pattern 1: Sequential calls (AnonymousSessionHandler)
rateLimitService.checkRateLimit(ip)           // 1-2 Redis calls
val token = jwtService.generateAnonymousToken(sessionId)  // CPU only
redisTemplate.opsForHash<String, String>().putAll(key, data)  // 1 Redis call
redisTemplate.expire(key, ttl)                 // 1 Redis call
// Total: 3-4 Redis round-trips

// Pattern 2: Loop per key (AnonymousSessionDataService.transferData)
cursor.use {
    while (cursor.hasNext()) {
        val rawKey = String(cursor.next())     // SCAN iteration
        val value = redisTemplate.opsForValue().get(rawKey)  // GET per key
        redisTemplate.opsForValue().set(userKey, value, ttl) // SET per key
    }
}
// Total: 1 SCAN + N×GET + N×SET = 1 + 2N round-trips
```

**Rate Limiting Pattern** (current):
```kotlin
// Fixed window: INCR key → if first → EXPIRE key window → if count > max → throw
val attempts = ops.increment(key) ?: 1
if (attempts == 1L) redisTemplate.expire(key, Duration.ofSeconds(config.windowSeconds))
if (attempts > config.maxAttempts) throw AnonymousRateLimitedException(...)
```

**Distributed Lock Pattern** (current):
```kotlin
// Simple SETNX: set if not exists with TTL
redisTemplate.opsForValue().setIfAbsent(lockKey, LOCK_VALUE, Duration.ofSeconds(30))
// Release: simple DEL (no ownership verification)
redisTemplate.delete(lockKey)
```

### 4.3 Tech Stack Constraints
- Language: Kotlin
- Framework: Spring Boot 3.x (Spring Data Redis, Spring Security)
- Database: PostgreSQL (Flyway migrations, existing `token_blacklist` table)
- Cache: Redis 7+ (StringRedisTemplate, no Lettuce pipeline exposed by default)
- Build tool: Gradle (Kotlin DSL) with custom conventions plugin
- Token: JJWT (RS256 primary, HMAC fallback)
- CQRS: `eventsourcing-utils` library (custom `CommandHandler`)
- Metrics: Micrometer (Counter, Timer, Gauge)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `AnonymousSessionHandler` | Service | `auth.application.command.AnonymousSessionHandler` | Optimize Redis calls via pipelining |
| `AnonymousSessionDataService` | Service | `auth.application.AnonymousSessionDataService` | Optimize transferData() with batch ops |
| `AnonymousRateLimitService` | Service | `auth.application.AnonymousRateLimitService` | Replace fixed-window with sliding window |
| `SessionPromotionService` | Service | `auth.application.SessionPromotionService` | Harden distributed lock |
| `RenewAnonymousTokenHandler` | Handler | `auth.application.command.RenewAnonymousTokenHandler` | Reduce Redis round-trips |
| `StringRedisTemplate` | Spring Bean | Auto-configured | Base Redis client — need pipeline/Lua access |
| `application.yml` | Config | `src/main/resources/application.yml` | Anonymous config at `app.security.anonymous` |
| `SecurityProperties` | Config | `shared.config.SecurityProperties` | `AnonymousProperties` data class |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: How to use Redis pipelining with Spring Data Redis `StringRedisTemplate` in Kotlin?
- [x] Q2: What is the best sliding window rate limiting algorithm for Redis (sorted set log vs counter subdivision)?
- [x] Q3: Is Redlock appropriate for session promotion, or is simple SETNX with fencing tokens sufficient?
- [x] Q4: How to batch SCAN+GET+SET operations for data transfer using Redis pipeline?
- [x] Q5: What is the memory overhead of Redis Hash vs String for small session metadata?
- [x] Q6: How to add OpenTelemetry tracing spans to Redis operations in Spring Boot?
- [x] Q7: What are the trade-offs of Lua scripts vs pipelining for atomic multi-command operations?
- [x] Q8: How to maintain a running data size counter instead of SCAN+STRLEN on every write?

### 5.2 Assumptions cần verify
- [x] A1: Spring Data Redis `StringRedisTemplate.executePipelined()` provides access to Redis pipelining
- [x] A2: Lettuce (default Redis client in Spring Boot) supports pipelining natively
- [x] A3: Redis Lua scripts can be executed via `StringRedisTemplate.execute(RedisScript, ...)` 
- [x] A4: Sliding window counter is more appropriate than sorted-set log for high-throughput rate limiting
- [x] A5: Simple SETNX with value-based ownership check is sufficient (Redlock is overkill for single-node Redis)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive survey of Redis optimization patterns | ≥ 5 sources analyzed |
| Open source options | Evaluate existing libraries/patterns | ≥ 3 approaches evaluated |
| Gap analysis | Identify optimization opportunities with estimated impact | All critical optimizations identified |
| Business analysis | Use case decomposition for optimization changes | All optimization UCs documented |
| Technical spec | Actionable spec with before/after code patterns | Agent-ready with specific code changes |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
