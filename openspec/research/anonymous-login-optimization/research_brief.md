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

The auth-service has a fully functional anonymous login system. The optimization initiative targets 6 areas of improvement within the existing anonymous session infrastructure. The codebase currently contains:

- `AnonymousSessionHandler` — CQRS handler creating anonymous sessions in Redis, uses `executePipelined()` for HSET+EXPIRE in single RTT (FR-001 implemented), initializes `dataSize=0` running counter (FR-005), falls back to sequential calls on pipeline failure (FR-010).
- `AnonymousAuthController` — REST controller at `/api/v1/auth/anonymous` with endpoints for session creation, data CRUD, token renewal.
- `SessionPromotionService` — Orchestrates anonymous → authenticated promotion with UUID-based distributed lock ownership and Lua-based safe lock release (`safe_lock_release.lua`, FR-004 implemented).
- `AnonymousSessionDataService` — Redis CRUD for namespaced session data. Uses Lua atomic data store script (`atomic_data_store.lua`, FR-007) for TOCTOU-safe size check + write + counter increment. Transfer uses pipeline MGET + pipeline MSET (FR-003 implemented). Running `dataSize` counter replaces O(N) SCAN+STRLEN with O(1) HGET (FR-005 implemented).
- `AnonymousRateLimitService` — Dual-mode rate limiting: sliding window counter via Lua script (`sliding_window_rate_limit.lua`, FR-002 implemented, default) and fixed-window INCR+EXPIRE (legacy fallback). Controlled by `slidingWindowEnabled` flag (DD-105).
- `RenewAnonymousTokenHandler` — Token renewal with max renewal count enforcement.
- `JwtService` extensions — `generateAnonymousToken()` and `parseAnonymousToken()`.
- `RedisLuaScriptConfig` — Configuration class defining 3 `DefaultRedisScript<Long>` beans for all Lua scripts.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Reduce Redis round-trips in anonymous session creation by 50%+ via pipelining/Lua scripts
- [x] Objective 2: Optimize data transfer during promotion using batch Redis pipeline operations (MGET/MSET)
- [x] Objective 3: Replace fixed-window rate limiting with sliding window counter (Lua script)
- [x] Objective 4: Improve session promotion reliability with UUID-based lock ownership and Lua safe release
- [x] Objective 5: Replace O(N) SCAN+STRLEN size calculation with O(1) running counter (HINCRBY)
- [x] Objective 6: Add TOCTOU-safe atomic data store via Lua script (atomic_data_store.lua)
- [ ] Objective 7: Define observability strategy (OpenTelemetry spans, Grafana dashboards, alert rules) — partially addressed

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Redis operation optimization (pipelining, Lua scripts, batch ops) | New anonymous session features (new endpoints, new data types) |
| Rate limiting algorithm improvement (sliding window) | UI/UX changes |
| Session promotion reliability hardening | Domain logic changes (cart merge strategies) |
| TOCTOU-safe atomic data operations | Multi-tenant anonymous session isolation |
| Redis memory optimization (running counter) | Migration to different cache provider |
| Performance benchmarking patterns | Load testing infrastructure setup |
| Error handling improvements (fallback patterns) | Anonymous session analytics/reporting |
| Observability improvements (metrics) | Mobile SDK changes |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `Redis pipelining Spring Boot`
- `Redis Lua scripting Spring Data Redis`
- `sliding window rate limiting Redis`
- `Redis batch operations Java Kotlin`
- `distributed lock reliability UUID ownership`

### 3.2 Secondary Keywords
- `Redis memory optimization`
- `Redis SCAN performance`
- `Spring Boot Micrometer observability`
- `OpenTelemetry Redis tracing`
- `Redis pipelining StringRedisTemplate`
- `session promotion idempotency`
- `Redis hash memory footprint`
- `fixed window vs sliding window rate limit`
- `TOCTOU race condition Redis Lua`
- `Redis EVALSHA script caching`
- `atomic data store Lua`

### 3.3 Domain-Specific Terms
- `Redis Pipelining`: Sending multiple Redis commands in a single network round-trip, reducing latency significantly for multi-command operations.
- `Sliding Window Rate Limiting`: A rate limiting algorithm that provides smooth enforcement without burst-at-boundary issues, using two adjacent fixed windows with weighted counter.
- `TOCTOU (Time-of-Check to Time-of-Use)`: A class of race condition where a check is followed by a use, and the state can change between the two operations. Lua scripts solve this by executing atomically.
- `Fencing Token`: A monotonically increasing token included with each lock acquisition, allowing downstream services to reject stale lock holders.
- `Redis Lua Scripting`: Server-side Lua scripts executed atomically by Redis, enabling complex multi-command operations without race conditions. Scripts cached via EVALSHA/SHA1.
- `Running Counter`: A counter maintained in a Redis hash field (via HINCRBY), updated on every data write/delete, replacing expensive SCAN+STRLEN aggregation.
- `DefaultRedisScript`: Spring Data Redis class for Lua script management. Loads from classpath, caches SHA1 hash for automatic EVALSHA optimization after first execution.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Redis pipelining Spring Data Redis StringRedisTemplate"` | Performance | High |
| 2 | `"sliding window rate limiting Redis implementation"` | Algorithm | High |
| 3 | `"Redis Lua script atomic operations Spring Boot"` | Performance | High |
| 4 | `"Redis SETNX UUID ownership lock pattern"` | Reliability | Medium |
| 5 | `"Redis SCAN vs KEYS performance optimization"` | Performance | Medium |
| 6 | `"Redis hash HINCRBY running counter pattern"` | Memory | Medium |
| 7 | `"OpenTelemetry Spring Boot Redis tracing"` | Observability | Medium |
| 8 | `"Redis MGET MSET batch operations Spring Data"` | Performance | High |
| 9 | `"Redis Lua TOCTOU atomic check-and-set pattern"` | Reliability | High |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| Anonymous Session Creation | `auth.application.command.AnonymousSessionHandler` | High | Uses `executePipelined()` for HSET+EXPIRE in 1 RTT. Initializes `dataSize=0`. Falls back to sequential on failure. |
| Anonymous Data Store | `auth.application.AnonymousSessionDataService.storeData()` | High | Uses Lua `atomic_data_store.lua` for TOCTOU-safe size check + write + counter increment. |
| Data Transfer | `auth.application.AnonymousSessionDataService.transferData()` | High | Pipeline MGET + pipeline MSET for O(3) RTT instead of O(2N). |
| Rate Limiting | `auth.application.AnonymousRateLimitService` | High | Dual-mode: sliding window Lua (default) + fixed-window INCR+EXPIRE (fallback). |
| Session Promotion | `auth.application.SessionPromotionService` | High | UUID-based lock ownership + Lua safe release script. |
| Size Calculation | `auth.application.AnonymousSessionDataService.getSessionDataSize()` | Medium | O(1) HGET `dataSize` from running counter. SCAN+STRLEN kept as private fallback (`getSessionDataSizeScan`). |
| Lua Script Config | `shared.config.RedisLuaScriptConfig` | Medium | 3 beans: `slidingWindowRateLimitScript`, `safeLockReleaseScript`, `atomicDataStoreScript`. |
| Token Renewal | `auth.application.command.RenewAnonymousTokenHandler` | Medium | Multiple Redis calls for renewal flow. |
| Metrics | Across all anonymous services | Medium | Counter/timer metrics: `auth.anonymous.sessions.created`, `auth.anonymous.rate_limited`, `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`, `auth.anonymous.sessions.promoted`, `auth.anonymous.promotion.duration`, `auth.anonymous.token.generation.duration`. |
| Login Rate Limiting | `auth.application.LoginRateLimitService` | Low | Uses fixed-window pattern — sliding window optimization could apply here too. |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture with CQRS Handlers:
- `CommandHandler<C, R>` from `eventsourcing-utils`
- Controllers delegate to handlers, handlers use services
- Redis via `StringRedisTemplate` (not `RedisTemplate<String, Object>`)

**Redis Pipeline Pattern** (implemented):
```kotlin
// AnonymousSessionHandler — Pipeline HSET+EXPIRE (FR-001)
redisTemplate.executePipelined { connection ->
    val rawKey = sessionKey.toByteArray()
    val rawData = sessionData.map { (k, v) -> k.toByteArray() to v.toByteArray() }.toMap()
    connection.hashCommands().hMSet(rawKey, rawData)
    connection.keyCommands().expire(rawKey, sessionTtl.seconds)
    null
}

// AnonymousSessionDataService.transferData() — Pipeline batch transfer (FR-003)
// Step 2: Pipeline GET all values
val values = redisTemplate.executePipelined { connection ->
    allKeys.forEach { key -> connection.stringCommands().get(key.toByteArray()) }
    null
}
// Step 3: Pipeline SET all
redisTemplate.executePipelined { connection ->
    keyValuePairs.forEach { (userKey, value, _) ->
        connection.stringCommands().setEx(userKey.toByteArray(), promotedTtl.seconds, value.toByteArray())
    }
    null
}
```

**Lua Script Pattern** (implemented):
```kotlin
// Sliding window rate limiting (FR-002)
val result = redisTemplate.execute(
    slidingWindowRateLimitScript,
    listOf(currentKey, prevKey),
    config.maxAttempts.toString(), config.windowSeconds.toString(), elapsedSeconds.toString()
)
if (result == -1L) throw AnonymousRateLimitedException(...)

// Safe lock release (FR-004)
val released = redisTemplate.execute(safeLockReleaseScript, listOf(lockKey), ownerUUID)

// Atomic data store (FR-007)
val result = redisTemplate.execute(
    atomicDataStoreScript, listOf(sessionKey, dataKey),
    maxSize.toString(), value, sessionTtl.toString()
)
```

**Lock Pattern** (implemented):
```kotlin
// UUID-based ownership — SETNX with UUID value + TTL
val ownerUUID = UUID.randomUUID().toString()
val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerUUID, Duration.ofSeconds(30))
// Lua-based safe release
redisTemplate.execute(safeLockReleaseScript, listOf(lockKey), ownerUUID)
```

### 4.3 Tech Stack Constraints
- Language: Kotlin 1.9+
- Framework: Spring Boot 3.x (Spring Data Redis, Spring Security)
- Database: PostgreSQL (Flyway migrations)
- Cache: Redis 7+ (StringRedisTemplate, Lettuce, Lua EVAL/EVALSHA)
- Build tool: Gradle (Kotlin DSL) with custom conventions plugin
- Token: JJWT (RS256 primary, HMAC fallback)
- CQRS: `eventsourcing-utils` library (custom `CommandHandler`)
- Metrics: Micrometer (Counter, Timer, Gauge)
- Lua Scripts: 3 scripts in `resources/redis/` managed by `RedisLuaScriptConfig`

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `AnonymousSessionHandler` | Handler | `auth.application.command.AnonymousSessionHandler` | Pipeline HSET+EXPIRE, dataSize=0 init |
| `AnonymousSessionDataService` | Service | `auth.application.AnonymousSessionDataService` | Lua atomic store, pipeline transfer, running counter |
| `AnonymousRateLimitService` | Service | `auth.application.AnonymousRateLimitService` | Lua sliding window + fixed-window fallback |
| `SessionPromotionService` | Service | `auth.application.SessionPromotionService` | UUID lock + Lua safe release |
| `RedisLuaScriptConfig` | Config | `shared.config.RedisLuaScriptConfig` | 3 DefaultRedisScript beans |
| `SecurityProperties` | Config | `shared.config.SecurityProperties` | `AnonymousProperties` with `slidingWindowEnabled`, `scanCount` |
| `StringRedisTemplate` | Spring Bean | Auto-configured | Base Redis client |
| `sliding_window_rate_limit.lua` | Lua Script | `resources/redis/` | 35 lines |
| `safe_lock_release.lua` | Lua Script | `resources/redis/` | 11 lines |
| `atomic_data_store.lua` | Lua Script | `resources/redis/` | 26 lines |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: How to use Redis pipelining with Spring Data Redis `StringRedisTemplate` in Kotlin?
- [x] Q2: What is the best sliding window rate limiting algorithm for Redis (sorted set log vs counter subdivision)?
- [x] Q3: Is Redlock appropriate for session promotion, or is simple SETNX with UUID ownership sufficient?
- [x] Q4: How to batch SCAN+GET+SET operations for data transfer using Redis pipeline?
- [x] Q5: What is the memory overhead of Redis Hash vs String for small session metadata?
- [x] Q6: How to add OpenTelemetry tracing spans to Redis operations in Spring Boot?
- [x] Q7: What are the trade-offs of Lua scripts vs pipelining for atomic multi-command operations?
- [x] Q8: How to maintain a running data size counter instead of SCAN+STRLEN on every write?
- [x] Q9: How to make data store atomic (TOCTOU-safe) — check size limit + write + increment counter?

### 5.2 Assumptions cần verify
- [x] A1: Spring Data Redis `StringRedisTemplate.executePipelined()` provides access to Redis pipelining — **VERIFIED**: implemented and working
- [x] A2: Lettuce (default Redis client in Spring Boot) supports pipelining natively — **VERIFIED**: works with sync API via executePipelined callback
- [x] A3: Redis Lua scripts can be executed via `StringRedisTemplate.execute(RedisScript, ...)` — **VERIFIED**: 3 scripts in production
- [x] A4: Sliding window counter is more appropriate than sorted-set log for high-throughput rate limiting — **VERIFIED**: implemented, performant
- [x] A5: Simple SETNX with UUID ownership check is sufficient (Redlock is overkill for single-node Redis) — **VERIFIED**: implemented and working

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
