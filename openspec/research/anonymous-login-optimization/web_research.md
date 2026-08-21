# Kết quả nghiên cứu Internet: Anonymous Login Optimization

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization |
| **Ngày nghiên cứu** | 2025-07-15 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | `Redis pipelining`, `sliding window rate limiting`, `distributed lock reliability`, `Redis Lua scripting`, `batch operations` |
| **Keywords phát triển** | `Redis pipeline Spring Data`, `token bucket vs sliding window`, `Redlock vs SETNX`, `SCAN cursor batch`, `Redis hash ziplist encoding`, `OpenTelemetry Redis` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Redis pipelining Spring Data Redis performance optimization"` | Spring Data Redis provides `executePipelined(RedisCallback)` and `executePipelined(SessionCallback)` for batching Redis commands. Lettuce (default client) supports auto-pipelining. Key insight: pipelining reduces network round-trips by 60-80% for multi-command operations. | `executePipelined`, `RedisCallback`, `auto-pipelining`, `Lettuce` |
| 2 | `"sliding window rate limiting Redis algorithm comparison"` | Three main approaches: (1) Fixed window — simple but burst-at-boundary, (2) Sliding window log — precise but memory-heavy (uses ZRANGEBYSCORE), (3) Sliding window counter — balanced approach using two adjacent fixed windows with weighted count. Redis Labs recommends sliding window counter for high-throughput. | `ZRANGEBYSCORE`, `sliding window counter`, `weighted count`, `token bucket` |
| 3 | `"distributed lock Redis reliability SETNX vs Redlock"` | Martin Kleppmann's famous critique of Redlock (2016): Redlock is not safe for correctness guarantees without fencing tokens. For single-node Redis, SETNX with value-based ownership and WATCH/MULTI for safe release is sufficient. Redlock adds complexity without true safety guarantees. | `fencing token`, `value-based ownership`, `DEL with Lua check`, `WATCH/MULTI` |

**Takeaways Iteration 1:**
- Redis pipelining via `executePipelined()` is well-supported in Spring Data Redis — straightforward to adopt
- Sliding window counter is the recommended algorithm for rate limiting (balance of accuracy and performance)
- Redlock is controversial and overkill for single-node Redis; SETNX with ownership verification (Lua-based safe release) is sufficient
- Lua scripts are the recommended approach for atomic multi-command operations

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://redis.io/docs/latest/develop/use/pipelining/ | Redis Pipelining (official docs) | Pipelining sends N commands without waiting for replies, then reads N replies. Not atomic — use Lua EVAL for atomicity. Reduces RTT from N×RTT to 1×RTT. Lettuce auto-pipelines when using reactive/async API; sync API requires explicit pipeline mode. | 9 |
| 2 | https://redis.io/glossary/rate-limiting/ | Rate Limiting Patterns (Redis) | Redis Labs documents 4 algorithms: (1) Fixed window (INCR+EXPIRE), (2) Sliding window log (ZADD+ZRANGEBYSCORE), (3) Sliding window counter (two counters with weighted sum), (4) Token bucket (DECR with periodic refill). Recommends sliding window counter for most use cases. | 9 |
| 3 | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html | How to do distributed locking (Kleppmann) | Critique of Redlock: GC pauses, clock drift, and network delays can cause safety violations. For single-node: SETNX with UUID value + Lua-based conditional DEL is safe. Fencing tokens (monotonic counter) provide stronger guarantees. | 8 |
| 4 | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html | Spring Data Redis Pipelining | `executePipelined(RedisCallback<?>)` batches commands. Results returned as `List<Object>`. Cannot read intermediate results. For Lua: `execute(RedisScript<T>, keys, args)`. RedisScript cached by SHA1 hash for efficiency. | 9 |
| 5 | https://redis.io/docs/latest/develop/interact/programmability/eval-intro/ | Redis Lua Scripting Guide | EVAL/EVALSHA for atomic operations. Scripts are cached server-side. KEYS[] and ARGV[] parameter passing. Scripts block other commands — keep short. No external I/O from scripts. | 8 |

**Takeaways Iteration 2:**
- **Pipelining approach**: Use `StringRedisTemplate.executePipelined()` to batch HSET+EXPIRE in session creation. For data transfer, collect all keys via SCAN first, then use pipeline for batch GET, then pipeline for batch SET.
- **Sliding window counter**: Implement using two Redis keys (current window + previous window) with Lua script for atomic increment + weighted count. Formula: `count = (prev_count × overlap_ratio) + current_count`.
- **Lock improvement**: Replace simple `delete(lockKey)` with Lua-based conditional delete: `if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end`. Store UUID as lock value for ownership verification.
- **Lua scripts vs pipelining**: Lua provides atomicity (no interleaving). Pipelining provides batching (no atomicity). Use Lua for rate limiting (needs atomicity). Use pipelining for session creation (doesn't need atomicity, just batching).

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"Redis hash ziplist encoding memory optimization"` (memory gap) | Redis uses ziplist encoding for hashes with ≤128 fields and values ≤64 bytes. Ziplist is ~10x more memory-efficient than hashtable encoding. Anonymous session hashes (4 fields, short values) qualify for ziplist. Key insight: keep field names short to maximize ziplist eligibility. | ✅ |
| 2 | `"Spring Data Redis executePipelined Kotlin example"` (implementation gap) | Kotlin usage: `redisTemplate.executePipelined { connection -> connection.hashCommands().hSet(...); connection.keyCommands().expire(...); null }`. Return type `List<Object>` contains results. Must return `null` from callback. | ✅ |
| 3 | `"OpenTelemetry Redis instrumentation Spring Boot"` (observability gap) | Lettuce supports OpenTelemetry instrumentation via `io.opentelemetry.instrumentation:opentelemetry-lettuce-5.1`. Spring Boot 3.x auto-configures Micrometer observation with `management.observations.key-values`. For custom spans: use `Observation.createNotStarted("anonymous.session.create", registry)`. | ✅ |

**Stop reason**: All critical optimization questions answered; remaining details are implementation-specific.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Redis Pipelining | https://redis.io/docs/latest/develop/use/pipelining/ | RTT reduction, not atomic, Lettuce auto-pipeline for async | 9 | Official, authoritative | Doesn't cover Spring Data wrapper |
| 2 | Docs | Redis Rate Limiting | https://redis.io/glossary/rate-limiting/ | 4 algorithm comparison, sliding window counter recommended | 9 | Algorithm-agnostic, clear trade-offs | No implementation code |
| 3 | Article | Distributed Locking (Kleppmann) | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html | SETNX + UUID ownership + Lua conditional DEL is sufficient for single-node | 8 | Industry-leading analysis, well-reasoned | Long article, academic tone |
| 4 | Docs | Spring Data Redis Pipelining | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html | executePipelined API, RedisScript caching | 9 | Directly applicable, same stack | Sparse examples |
| 5 | Docs | Redis Lua Scripting | https://redis.io/docs/latest/develop/interact/programmability/eval-intro/ | EVAL/EVALSHA, parameter passing, server-side caching | 8 | Official, covers all Lua capabilities | Complex for beginners |
| 6 | Docs | Redis Hash Memory Optimization | https://redis.io/docs/latest/develop/use/memory-optimization/ | Ziplist encoding for small hashes, field name length impact | 7 | Memory reduction techniques | Requires specific configuration knowledge |
| 7 | Docs | Micrometer Observation API | https://micrometer.io/docs/observation | Observation.createNotStarted() for custom spans, key-values | 7 | Native Spring Boot 3.x support | New API, evolving documentation |
| 8 | Docs | OpenTelemetry Lettuce Instrumentation | https://opentelemetry.io/docs/languages/java/automatic/spring-boot/ | Auto-instrumentation for Lettuce Redis commands | 7 | Automatic span creation for Redis | Requires OTel agent or starter |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Redis Pipeline (native) | Batch multiple Redis commands into single network round-trip | `executePipelined(RedisCallback)` in Spring Data Redis | 60-80% RTT reduction for multi-command operations | Zero dependencies, native API | Not atomic — interleaving possible | No atomicity guarantee |
| Redis Lua Scripts | Execute atomic multi-command operations server-side | `EVAL`/`EVALSHA` via `StringRedisTemplate.execute(RedisScript)` | Atomic operations, server-side caching, single RTT | Zero dependencies, atomic guarantees | Blocks other commands, debugging harder | Script management complexity |
| Sliding Window Counter | Two-counter approach with weighted sum for smooth rate limiting | Lua script with `current_count + prev_count × weight` formula | No burst-at-boundary, memory efficient (2 keys per IP) | O(1) operations, predictable memory | Slightly less accurate than log-based | Approximate — not exact count |
| SETNX + UUID + Lua DEL | Lock with ownership verification — safe release via conditional delete | `SET key uuid NX EX 30` + Lua `if GET == uuid then DEL` | Safe release, no accidental unlock by wrong owner | Simple, proven, no new deps | No fencing token (not needed for best-effort promotion) | No Redlock multi-node guarantee |

### So sánh tính năng chi tiết

| Feature | Redis Pipeline | Redis Lua | Sliding Window | SETNX+UUID | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| RTT reduction | ✅ | ✅ | N/A | N/A | ⭐ Must |
| Atomicity | ❌ | ✅ | ✅ (via Lua) | ✅ (via Lua) | ⭐ Must (for rate limit) |
| Memory efficiency | N/A | N/A | ✅ | N/A | Nice to have |
| Spring Data Redis support | ✅ | ✅ | ✅ (custom) | ✅ | ⭐ Must |
| Zero new dependencies | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Easy to implement | ✅ | ⚠️ | ⚠️ | ✅ | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Pipeline Session Creation** | Batch HSET+EXPIRE into single `executePipelined()` call | 2-3 RTT → 1 RTT; simple API | No atomicity — but not needed here since operations are independent | Session creation optimization | https://redis.io/docs/latest/develop/use/pipelining/ |
| 2 | **Lua Sliding Window Counter** | Atomic Lua script: INCR current window + calculate weighted sum with previous window | No burst-at-boundary; atomic; O(1); 2 keys per IP | Slightly more complex than INCR+EXPIRE; approximate count | Rate limiting optimization | https://redis.io/glossary/rate-limiting/ |
| 3 | **Pipeline Batch Transfer** | Collect keys via SCAN → pipeline MGET → pipeline MSET to user namespace | N×2 RTT → 2 RTT; dramatic improvement for sessions with many data keys | SCAN still required for key discovery; MGET returns list (order matters) | Data transfer optimization | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html |
| 4 | **Lua-based Safe Lock Release** | Store UUID in lock value → release via Lua `if GET==uuid then DEL` | Prevents accidental unlock by wrong owner; simple to implement | No fencing token — not needed since promotion is best-effort | Lock reliability improvement | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html |
| 5 | **Running Size Counter** | Maintain `dataSize` field in session hash, increment/decrement on data write/delete | Eliminates SCAN+STRLEN loop for size checks; O(1) instead of O(N) | Must keep counter in sync; corruption risk if decrement missed | Data size enforcement optimization | Redis hash operations documentation |
| 6 | **Ziplist-aware Key Design** | Keep hash field names short (<64 bytes) and field count ≤128 to stay in ziplist encoding | ~10x memory reduction vs hashtable encoding | Limits field name expressiveness; must monitor encoding changes | Memory optimization | https://redis.io/docs/latest/develop/use/memory-optimization/ |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the exact latency improvement from pipelining in the production Redis deployment? | ✅ | Depends on network topology (local vs remote Redis), packet size, Redis version — requires benchmarking in actual environment | Low — improvement is guaranteed, magnitude varies |
| 2 | Should we add OpenTelemetry auto-instrumentation or manual spans? | ✅ | Trade-off: auto-instrumentation captures all Redis commands (noisy) vs manual spans capture business operations (cleaner). Recommend: manual spans for critical paths + auto for debugging | Low — configurable, both approaches work |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> **Next step**: Comparison Analysis (comparison_analysis.md)
