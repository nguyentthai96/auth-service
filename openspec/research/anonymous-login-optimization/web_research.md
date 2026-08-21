# Kết quả nghiên cứu Internet: Anonymous Login Optimization

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization |
| **Ngày nghiên cứu** | 2025-07-15 |
| **Số iterations** | 3 |
| **Tổng sources** | 9 unique |
| **Keywords ban đầu** | `Redis pipelining`, `sliding window rate limiting`, `distributed lock reliability`, `Redis Lua scripting`, `batch operations` |
| **Keywords phát triển** | `Redis pipeline Spring Data`, `token bucket vs sliding window`, `Redlock vs SETNX`, `SCAN cursor batch`, `Redis hash ziplist encoding`, `OpenTelemetry Redis`, `TOCTOU Lua atomic`, `EVALSHA SHA1 caching` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Redis pipelining Spring Data Redis performance optimization"` | Spring Data Redis provides `executePipelined(RedisCallback)` and `executePipelined(SessionCallback)` for batching Redis commands. Lettuce (default client) supports auto-pipelining in reactive mode. Key insight: pipelining reduces network round-trips by 60-80% for multi-command operations. Sync mode requires explicit pipeline via callback. | `executePipelined`, `RedisCallback`, `auto-pipelining`, `Lettuce`, `hMSet` |
| 2 | `"sliding window rate limiting Redis algorithm comparison"` | Three main approaches: (1) Fixed window — simple but burst-at-boundary vulnerability, (2) Sliding window log — precise but memory-heavy (uses ZRANGEBYSCORE, O(log N) per check), (3) Sliding window counter — balanced approach using two adjacent fixed windows with weighted count. Redis Labs recommends sliding window counter for high-throughput scenarios. Formula: `count = prev × (window - elapsed) / window + current`. | `ZRANGEBYSCORE`, `sliding window counter`, `weighted count`, `token bucket`, `burst-at-boundary` |
| 3 | `"distributed lock Redis reliability SETNX vs Redlock"` | Martin Kleppmann's critique (2016): Redlock is not safe for correctness guarantees without fencing tokens due to GC pauses, clock drift, network delays. For single-node Redis, SETNX with value-based ownership (UUID) and Lua-based conditional DELETE is sufficient and simpler. Antirez responded but debate remains. Consensus: for best-effort locks (like session promotion), SETNX + UUID is adequate. | `fencing token`, `value-based ownership`, `Lua conditional DEL`, `GC pause`, `clock drift` |

**Takeaways Iteration 1:**
- Redis pipelining via `executePipelined()` is well-supported and straightforward to adopt
- Sliding window counter is the recommended algorithm — balance of accuracy (within 1-2%) and performance (O(1))
- Redlock is controversial and overkill for single-node Redis; SETNX with UUID ownership + Lua safe release is sufficient for best-effort locking
- Lua scripts provide atomicity (no interleaving) while pipeline provides batching (no atomicity guarantee)

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://redis.io/docs/latest/develop/use/pipelining/ | Redis Pipelining (official docs) | Pipelining sends N commands without waiting for replies, then reads N replies. Not atomic — use Lua EVAL for atomicity. Reduces RTT from N×RTT to 1×RTT. Important: pipeline is NOT a transaction — commands can be interleaved with other clients. For operations that need atomicity, use Lua EVAL instead. | 9 |
| 2 | https://redis.io/glossary/rate-limiting/ | Rate Limiting Patterns (Redis) | Redis Labs documents 4 algorithms: (1) Fixed window (INCR+EXPIRE), (2) Sliding window log (ZADD+ZRANGEBYSCORE), (3) Sliding window counter (two counters with weighted sum), (4) Token bucket (DECR with periodic refill). Sliding window counter recommended for most production use cases. Uses O(1) operations per check, O(2 keys per IP) memory. | 9 |
| 3 | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html | How to do distributed locking (Kleppmann) | Comprehensive analysis: GC pauses, clock drift, network delays can cause safety violations in Redlock. For single-node: SETNX with UUID value + Lua conditional DEL is the safe pattern. Key insight: `if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end` — this prevents Process A from unlocking Process B's lock. | 8 |
| 4 | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html | Spring Data Redis Pipelining | `executePipelined(RedisCallback<?>)` batches commands. Results returned as `List<Object>`. Cannot read intermediate results within callback. For Lua: `execute(RedisScript<T>, keys, args)`. `DefaultRedisScript` cached by SHA1 hash via EVALSHA — after first execution, only sends SHA1, not full script. Must return null from callback. | 9 |
| 5 | https://redis.io/docs/latest/develop/interact/programmability/eval-intro/ | Redis Lua Scripting Guide | EVAL/EVALSHA for atomic operations. Scripts cached server-side by SHA1 hash. KEYS[] and ARGV[] parameter passing. Scripts block other commands during execution — keep scripts short (< 5ms recommended). No external I/O from scripts. EVALSHA preferred in production for reduced network overhead. | 8 |
| 6 | https://redis.io/docs/latest/develop/use/memory-optimization/ | Redis Memory Optimization | Redis uses ziplist encoding for hashes with ≤128 fields and values ≤64 bytes (configurable via `hash-max-ziplist-entries` and `hash-max-ziplist-value`). Ziplist is ~10x more memory-efficient than hashtable encoding. Anonymous session hashes (5-6 fields, short values) qualify for ziplist. Keep field names short to stay within ziplist threshold. | 7 |

**Takeaways Iteration 2:**
- **Pipelining approach**: Use `StringRedisTemplate.executePipelined()` to batch HSET+EXPIRE in session creation. For data transfer, collect all keys via SCAN first, then pipeline GET, then pipeline SET. Three separate pipelines = 3 RTTs (vs 1+2N).
- **Sliding window counter**: Two Redis keys (current window + previous window) with Lua script for atomic increment + weighted count. Formula: `count = (prev_count × overlap_ratio) + current_count`. TTL = 2× window for overlap preservation.
- **Lock improvement**: Replace simple `delete(lockKey)` with Lua conditional delete: `if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end`. Store UUID as lock value for ownership verification.
- **Lua vs pipeline trade-off**: Lua provides atomicity (no interleaving) — use for rate limiting and lock release. Pipeline provides batching (no atomicity) — use for session creation and data transfer where operations are independent.
- **TOCTOU prevention**: For data store with size limit, Lua script can atomically: read current size → check limit → write data → increment counter. This prevents race condition where two concurrent writes both pass the size check.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"Redis hash ziplist encoding memory optimization"` (memory gap) | Redis uses ziplist encoding for hashes with ≤128 fields and values ≤64 bytes. Ziplist is ~10x more memory-efficient than hashtable encoding. Anonymous session hashes (5 fields: deviceFingerprint, ipAddress, createdAt, renewalCount, dataSize) with short values qualify for ziplist. Adding `dataSize` field (6th field) still within ziplist threshold. | ✅ |
| 2 | `"Spring Data Redis executePipelined Kotlin example"` (implementation gap) | Kotlin usage: `redisTemplate.executePipelined { connection -> connection.hashCommands().hMSet(...); connection.keyCommands().expire(...); null }`. Return type `List<Object>` contains results in order. Must return `null` from callback (not the connection result). Works with both RedisCallback and SessionCallback. | ✅ |
| 3 | `"OpenTelemetry Redis instrumentation Spring Boot"` (observability gap) | Lettuce supports auto-instrumentation via OpenTelemetry Java agent. For manual spans: use Micrometer `Observation.createNotStarted("anonymous.session.create", registry)`. Spring Boot 3.x auto-configures observation with `management.observations.key-values`. Both approaches work — auto for broad coverage, manual for business-specific spans. | ✅ |
| 4 | `"Redis Lua atomic check-and-set TOCTOU prevention"` (atomic data gap) | Lua EVAL executes atomically on Redis server — no other commands can interleave. This makes it ideal for TOCTOU-sensitive operations like "check size limit → write data → increment counter". The atomic_data_store pattern uses HGET + size comparison + SET + HINCRBY in a single Lua script, eliminating the race condition window entirely. | ✅ |

**Stop reason**: All critical optimization questions answered with high confidence. Remaining questions (exact latency numbers, OTel agent vs manual) are deployment-specific.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Redis Pipelining | https://redis.io/docs/latest/develop/use/pipelining/ | RTT reduction, not atomic, Lettuce auto-pipeline for async | 9 | Official, authoritative, clear examples | Doesn't cover Spring Data wrapper API |
| 2 | Docs | Redis Rate Limiting | https://redis.io/glossary/rate-limiting/ | 4 algorithm comparison, sliding window counter recommended | 9 | Algorithm-agnostic, clear trade-offs, production guidance | No implementation code |
| 3 | Article | Distributed Locking (Kleppmann) | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html | SETNX + UUID ownership + Lua conditional DEL is sufficient for single-node | 8 | Industry-leading analysis, well-reasoned, peer-reviewed | Long article, academic tone |
| 4 | Docs | Spring Data Redis Pipelining | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html | executePipelined API, RedisScript caching, SHA1 optimization | 9 | Directly applicable to existing code, same stack | Sparse examples, needs community supplements |
| 5 | Docs | Redis Lua Scripting | https://redis.io/docs/latest/develop/interact/programmability/eval-intro/ | EVAL/EVALSHA, parameter passing, server-side caching, execution model | 8 | Official, covers all Lua capabilities and limitations | Complex for beginners |
| 6 | Docs | Redis Hash Memory Optimization | https://redis.io/docs/latest/develop/use/memory-optimization/ | Ziplist encoding for small hashes, field name length impact, encoding thresholds | 7 | Memory reduction techniques, measurable impact | Requires specific Redis configuration knowledge |
| 7 | Docs | Micrometer Observation API | https://micrometer.io/docs/observation | Observation.createNotStarted() for custom spans, key-values, conventions | 7 | Native Spring Boot 3.x support, trace + metric in one API | Newer API, documentation still evolving |
| 8 | Docs | OpenTelemetry Spring Boot | https://opentelemetry.io/docs/languages/java/automatic/spring-boot/ | Auto-instrumentation for Lettuce Redis commands, span attributes | 7 | Automatic span creation for all Redis operations | Requires OTel agent or starter, can be noisy |
| 9 | Docs | Redis EVAL/EVALSHA | https://redis.io/docs/latest/commands/eval/ | EVAL command reference, EVALSHA for SHA1-based execution, error handling | 8 | Complete command reference, production patterns | Low-level, needs Spring Data wrapper knowledge |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Redis Pipeline (native) | Batch multiple Redis commands into single network round-trip | `executePipelined(RedisCallback)` in Spring Data Redis | 60-80% RTT reduction for multi-command operations | Zero dependencies, native API, well-understood | Not atomic — interleaving possible between pipelined commands | No atomicity guarantee needed for independent operations |
| Redis Lua Scripts | Execute atomic multi-command operations server-side | `EVAL`/`EVALSHA` via `StringRedisTemplate.execute(RedisScript)` | Atomic operations, server-side SHA1 caching, single RTT | Zero dependencies, atomic guarantees, TOCTOU-safe | Blocks other commands during execution, debugging harder | Script management requires DefaultRedisScript beans |
| Sliding Window Counter | Two-counter approach with weighted sum for smooth rate limiting | Lua script: `current_count + prev_count × weight` formula | No burst-at-boundary, memory efficient (2 keys per IP), O(1) | Predictable memory, simple formula, battle-tested | Approximate — not exact count (within 1-2%) | Sufficient accuracy for rate limiting |
| SETNX + UUID + Lua DEL | Lock with ownership verification — safe release via conditional delete | `SET key uuid NX EX 30` + Lua `if GET == uuid then DEL` | Safe release, no accidental unlock by wrong owner, simple | Proven pattern, no new deps, Kleppmann-endorsed | No fencing token (not needed for best-effort promotion) | No Redlock multi-node guarantee |
| Lua Atomic Data Store | TOCTOU-safe check-and-set for data with size limits | Lua: HGET size → check limit → SET data → HINCRBY counter | Eliminates race condition in concurrent data writes | Atomic guarantee, prevents over-limit writes | More complex than simple SET, script management | Script is small (26 lines), manageable |

### So sánh tính năng chi tiết

| Feature | Redis Pipeline | Redis Lua | Sliding Window | SETNX+UUID | Atomic Store | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| RTT reduction | ✅ | ✅ | N/A | N/A | ✅ | ⭐ Must |
| Atomicity | ❌ | ✅ | ✅ (via Lua) | ✅ (via Lua) | ✅ (via Lua) | ⭐ Must (for rate limit, data store) |
| TOCTOU safety | ❌ | ✅ | N/A | N/A | ✅ | ⭐ Must (for data store) |
| Memory efficiency | N/A | N/A | ✅ | N/A | ✅ | Nice to have |
| Spring Data Redis support | ✅ | ✅ | ✅ (custom) | ✅ | ✅ (custom) | ⭐ Must |
| Zero new dependencies | ✅ | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Easy to implement | ✅ | ⚠️ | ⚠️ | ✅ | ⚠️ | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Pipeline Session Creation** | Batch HSET+EXPIRE into single `executePipelined()` call | 2-3 RTT → 1 RTT; simple callback API | No atomicity — but operations are independent, so safe | Session creation with multiple independent Redis commands | https://redis.io/docs/latest/develop/use/pipelining/ |
| 2 | **Lua Sliding Window Counter** | Atomic Lua script: INCR current window + calculate weighted sum with previous window | No burst-at-boundary; atomic; O(1); 2 keys per IP | Slightly more complex than INCR+EXPIRE; approximate count (within 1-2%) | Rate limiting needing smooth enforcement across window boundaries | https://redis.io/glossary/rate-limiting/ |
| 3 | **Pipeline Batch Transfer** | Collect keys via SCAN → pipeline MGET → pipeline MSET to user namespace | N×2 RTT → 3 RTT; dramatic improvement for sessions with many data keys | SCAN still required for key discovery; MGET returns list (order matters) | Data transfer between namespaces with many keys | https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html |
| 4 | **Lua-based Safe Lock Release** | Store UUID in lock value → release via Lua `if GET==uuid then DEL` | Prevents accidental unlock by wrong owner; simple to implement | No fencing token (not needed for best-effort operations) | Distributed lock where ownership verification matters | https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html |
| 5 | **Running Size Counter** | Maintain `dataSize` field in session hash, increment/decrement via HINCRBY | Eliminates SCAN+STRLEN loop; O(1) instead of O(N) | Must keep counter in sync; potential drift if decrement missed | Data size enforcement needing fast checks | Redis hash operations documentation |
| 6 | **Lua Atomic Data Store** | Lua script: HGET size → check limit → SET data → HINCRBY counter | TOCTOU-safe; prevents concurrent writes from exceeding size limit | More complex than simple SET; requires Lua script management | Data writes with concurrent access and size enforcement | https://redis.io/docs/latest/develop/interact/programmability/eval-intro/ |
| 7 | **Ziplist-aware Key Design** | Keep hash field names short (<64 bytes) and field count ≤128 to stay in ziplist encoding | ~10x memory reduction vs hashtable encoding | Limits field name expressiveness; must monitor encoding changes | Memory optimization for small Redis hashes | https://redis.io/docs/latest/develop/use/memory-optimization/ |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the exact latency improvement from pipelining in production? | ✅ | Depends on network topology (local vs remote Redis), packet size, Redis version — requires benchmarking in actual deployment | Low — improvement is guaranteed, magnitude varies by environment |
| 2 | Should we add OpenTelemetry auto-instrumentation or manual Observation spans? | ✅ | Trade-off: auto-instrumentation captures all Redis commands (comprehensive but noisy) vs manual spans capture business operations (cleaner, more meaningful). Both approaches work. Recommendation: manual spans for critical business flows + auto for debugging. | Low — configurable, both approaches valid |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> **Next step**: Comparison Analysis (comparison_analysis.md)
