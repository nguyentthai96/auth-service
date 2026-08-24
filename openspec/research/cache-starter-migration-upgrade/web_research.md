# Kết quả nghiên cứu Internet: Cache Starter Migration & Upgrade

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Cache Starter Migration & Upgrade (Encryption + Compression + Smart Serialization) |
| **Ngày nghiên cứu** | 2026-08-24 |
| **Số iterations** | 7 |
| **Tổng sources** | 12 unique |
| **Keywords ban đầu** | `two-level cache`, `cache encryption at rest`, `Redis serialization`, `AES-GCM Java` |
| **Keywords phát triển** | `Zstd JVM benchmark`, `LZ4 Java`, `field-level encryption PII`, `magic byte header`, `AES-NI intrinsics JDK`, `Google Tink vs JCA performance` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD: Cache Encryption Patterns

**Mục tiêu**: Phát hiện landscape, thu thập approaches cho cache encryption at rest

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Spring Cache Redis encrypted cache data at rest AES encryption"` | Client-side encryption là standard pattern — Redis không encrypt per-entry. Custom `RedisSerializer<Object>` là injection point. | `RedisSerializer`, `client-side encryption` |
| 2 | `"cache encryption best practices Java 2025"` | AES-256-GCM industry standard. Never hardcode keys. Vault/env/KeyStore for key management. | `AES-256-GCM`, `KeyStore`, `key management` |
| 3 | `"encrypted data Redis limitations SORT INCREMENT"` | Encrypted data prevents Redis server-side operations (SORT, INCR, pattern matching). L1 keeps plaintext for local operations. | `plaintext L1`, `encrypted L2 only` |

**Takeaways Iteration 1:**
- Client-side encryption via custom `RedisSerializer` is the correct approach for Spring Cache
- L1 (Caffeine) should keep plaintext — it's in-process memory, no network exposure
- L2 (Redis) should encrypt — data at rest in external datastore
- Redis operations on encrypted values are impossible → design must account for this

---

### Iteration 2 — DEEP DIVE: Partial Field Encryption

**Mục tiêu**: Đọc sâu approaches cho field-level encryption trong cache context

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | redis.io/docs/management/security/encryption | Redis Security - Encryption at Rest | Redis Enterprise has TDE (Transparent Data Encryption) but OSS does not. Client-side encryption needed for OSS Redis. | 8 |
| 2 | spring.io/blog/spring-cache-abstraction | Spring Cache Abstraction Guide | `CacheManager` → `Cache` → `RedisCache` → `RedisCacheWriter` pipeline. Custom serializer injected via `RedisCacheConfiguration`. | 9 |
| 3 | mongodb.com/docs/manual/core/csfle | MongoDB Client-Side Field Level Encryption | MongoDB CSFLE pattern: annotate fields, serialize with encryption per-field. Applicable pattern for cache. | 7 |
| 4 | owasp.org/www-project-web-security-testing-guide | OWASP Data Protection Testing | PII fields (email, phone, SSN) must be encrypted at rest. 3 strategies: full encryption, field-level, tokenization. | 8 |

**Takeaways Iteration 2:**
- MongoDB CSFLE (Client-Side Field Level Encryption) pattern is applicable: annotate sensitive fields → serializer encrypts only those
- `@CacheEncrypt` annotation on data class fields → serializer uses reflection to find marked fields → encrypt values before JSON serialization
- For PARTIAL mode: serialize to JSON Map → scan for annotated fields → encrypt field values → re-serialize
- Performance: reflection scan adds ~microseconds, negligible vs AES-GCM operation

---

### Iteration 3 — DEEP DIVE: Data Type Serialization Optimization

**Mục tiêu**: Đọc sâu top approaches cho Redis serialization optimization

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | redis.io/docs/data-types | Redis Data Types | Hashes → flat objects (ziplist encoding < 128 fields, memory efficient). Strings → complex nested JSON. | 8 |
| 2 | medium.com/redis-serialization-performance | Redis Serialization Performance Comparison | JSON readable/debuggable vs MessagePack 40% smaller vs Protobuf 60% smaller. For cache: JSON default (debug) + binary option (perf). | 7 |
| 3 | spring.io/docs/data-redis/serialization | Spring Data Redis Serialization | `GenericJackson2JsonRedisSerializer` includes type info. `Jackson2JsonRedisSerializer` is type-specific. Custom `RedisSerializer<Object>` for full control. | 9 |

**Takeaways Iteration 3:**
- JSON is best default for cache — readable, debuggable via Redis CLI
- MessagePack/Protobuf are overkill for most cache entries (< 1KB)
- For large entries (> 1KB): compression gives better ROI than format change
- Type-aware serialization: String → raw UTF-8, Number → ASCII, Boolean → 1 byte, Object → JSON

---

### Iteration 4 — TARGETED: Compression Libraries Benchmark

**Mục tiêu**: Choose optimal compression for cache payloads

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"Zstd vs LZ4 Java JVM benchmark 2025"` (từ gap trong Iter 3 — need compression comparison) | Zstd L3: ~300 MB/s compress, ~1.5 GB/s decompress, 2.8x ratio. LZ4: ~800 MB/s compress, ~4 GB/s decompress, 2.0x ratio. | ✅ |
| 2 | `"Snappy vs Zstd vs LZ4 cache performance"` (verify ranking) | Snappy outperformed by both Zstd and LZ4 in modern benchmarks. GZIP too slow for cache. | ✅ |

**Key Data Points:**

| Algorithm | Library (Java) | Compress Speed | Decompress Speed | Ratio | Best For |
|-----------|---------------|:-----------:|:----------------:|:-----:|----------|
| Zstd L1 | zstd-jni:1.5.7-15 | ~500 MB/s | ~1.5 GB/s | 2.5x | Balance (default) |
| Zstd L3 | zstd-jni:1.5.7-15 | ~300 MB/s | ~1.5 GB/s | 2.8x | Better ratio |
| LZ4 | lz4-java:1.8.0 | ~800 MB/s | ~4 GB/s | 2.0x | Speed-first |
| Snappy | snappy-java | ~500 MB/s | ~1.5 GB/s | 2.1x | Legacy (avoid) |
| GZIP | java.util.zip | ~30 MB/s | ~300 MB/s | 3.0x | Never for cache |
| Brotli | Google Brotli | ~20 MB/s | ~400 MB/s | 3.5x | Static assets only |

**Decision**: Zstd L3 as default (best ratio/speed balance), LZ4 as optional (fastest decompress for latency-sensitive caches)

---

### Iteration 5 — TARGETED: Encryption Algorithm Performance

**Mục tiêu**: Confirm AES-GCM as optimal choice for cache encryption

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"AES-GCM vs ChaCha20-Poly1305 Java JDK performance server"` (verify encryption algo choice) | AES-256-GCM + AES-NI: GB/s throughput on x86-64. ChaCha20: faster on ARM/mobile without AES-NI. | ✅ |
| 2 | `"AES-SIV nonce reuse performance overhead"` (verify AES-SIV is too slow) | AES-SIV: 3-5x slower than AES-GCM (2-pass). Only needed for nonce-misuse protection, not relevant for cache. | ✅ |

**Conclusion**: AES-256-GCM is optimal for server-side cache encryption (x86-64 with AES-NI). ChaCha20 unnecessary.

---

### Iteration 6 — TARGETED: Key Management Strategy

**Mục tiêu**: Determine key storage approach for cache encryption keys

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | docs.oracle.com/javase/17/docs/api/java.base/java.security/KeyStore.html | Java KeyStore API | PKCS12 KeyStore: store SecretKey, load at startup, cache in memory. JCEKS also supports symmetric keys. | 8 |
| 2 | spring.io/guides/vault-config | Spring Vault Configuration | HashiCorp Vault for production key management. For development: env var or KeyStore file. | 7 |

**Decision**:
- **Strategy Pattern**: `CacheEncryptionKeyProvider` interface → swap implementations
- **inline** (default): Base64-encoded key from YAML/env var — simplest, development-friendly
- **keystore**: PKCS12 file — production-grade, key never in config
- **custom**: User implements `CacheEncryptionKeyProvider` — Vault, KMS, etc.

---

### Iteration 7 — TARGETED: Google Tink vs JCA Performance

**Mục tiêu**: Decide between Tink (already in auth-service) vs raw JCA for cache encryption

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"Google Tink vs JCA AES-GCM performance benchmark"` (Tink already in project — evaluate reuse) | Identical core throughput — Tink wraps JCE internally. Tink adds ~microseconds for key management. | ✅ |
| 2 | `"Tink dependency size overhead"` (evaluate dependency cost) | Tink: ~3MB + transitive deps. JCA: 0 extra deps (built into JDK). | ✅ |

**Decision**: JCA direct for cache encryption — simpler, zero dependency, identical performance. Tink reserved for E2EE (already in use for that purpose).

**Stop reason**: All research questions answered. Diminishing returns on further iterations.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Redis Security - Encryption at Rest | redis.io/docs/management/security/encryption | Client-side encryption needed for OSS Redis | 9 | Official source | Redis Enterprise-focused |
| 2 | Docs | Spring Cache Abstraction | spring.io/docs/spring-framework/cache | CacheManager → Cache → RedisCache pipeline, custom serializer injection | 9 | Authoritative | Generic, no two-level |
| 3 | Docs | Spring Data Redis Serialization | spring.io/docs/data-redis/serialization | RedisSerializer interface, Jackson options | 9 | Directly applicable | - |
| 4 | Article | MongoDB CSFLE (Client-Side FLE) | mongodb.com/docs/manual/core/csfle | Field-level encryption annotation pattern, applicable to cache | 7 | Well-designed pattern | MongoDB-specific impl |
| 5 | Benchmark | Zstd/LZ4/Snappy Comparison | Various (manishrjain.com, medium.com) | Zstd L3 best ratio/speed, LZ4 fastest decompress | 8 | Data-driven | Benchmark env varies |
| 6 | Docs | Java KeyStore API | docs.oracle.com | PKCS12 KeyStore for symmetric key storage | 7 | JDK built-in | API complexity |
| 7 | Guide | OWASP Data Protection | owasp.org | PII must be encrypted at rest, 3 strategies | 8 | Security standard | Generic guidelines |
| 8 | Docs | Java AES-GCM (JCA) | docs.oracle.com/javase/security | AES-NI hardware acceleration, Cipher API | 8 | Built into JDK | Low-level API |
| 9 | Article | Google Tink vs JCA Performance | Various tech blogs | Identical throughput, Tink wraps JCE | 7 | Confirms JCA choice | Limited formal benchmarks |
| 10 | Docs | Micrometer Cache Instrumentation | micrometer.io/docs/cache | CacheMeterBinder for custom cache metrics | 7 | Standard metrics | - |
| 11 | Docs | JetCache Documentation | github.com/alibaba/jetcache/wiki | Two-level cache design patterns, auto-refresh | 7 | Production patterns | Chinese-primary |
| 12 | Article | Redis Data Types Best Practices | redis.io/docs/data-types | Hash vs String, ziplist encoding | 8 | Official Redis guidance | - |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Redis Enterprise TDE | Transparent Data Encryption at storage layer | Automatic encryption/decryption, key rotation | Zero code change | Transparent, managed | Commercial license required, vendor lock-in | Not applicable for OSS Redis |
| AWS ElastiCache | Managed Redis with encryption at rest | AES-256, KMS integration | Fully managed | No code change | Cloud vendor lock-in, cost | Must use AWS |
| Redisson PRO | Java Redis client with advanced features | Codec-based serialization, distributed objects | Rich feature set | Many codecs available | Commercial license, overkill | No field-level encryption |
| JCA (javax.crypto) | Direct encryption API in JDK | AES-GCM, AES-CBC, key management | Zero dependency, full control | Built-in, AES-NI support | Low-level API, manual nonce management | Needs wrapper code |

### So sánh tính năng chi tiết

| Feature | Redis Enterprise TDE | AWS ElastiCache | JCA (javax.crypto) | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Encryption at rest | ✅ | ✅ | ✅ (manual) | ✅ | ⭐ Must |
| Field-level encryption | ❌ | ❌ | ✅ (manual) | ✅ | Nice to have |
| No vendor lock-in | ❌ | ❌ | ✅ | ✅ | ⭐ Must |
| Zero extra cost | ❌ | ❌ | ✅ | ✅ | ⭐ Must |
| Spring Cache integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Compression support | ❌ | ❌ | ❌ | ✅ | Nice to have |
| Per-cache config | ❌ | ❌ | N/A | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Client-Side Full Encryption | Encrypt entire serialized value before storing in Redis | Simple, covers all data | Cannot query/sort on encrypted data, larger payload | Sensitive data (sessions, tokens) | redis.io, OWASP |
| 2 | Client-Side Field-Level Encryption | Encrypt only annotated fields within JSON | Selective protection, queryable non-sensitive fields | Complex serializer, reflection overhead | PII in mixed data (user profiles) | MongoDB CSFLE pattern |
| 3 | Transparent Data Encryption (TDE) | Storage-layer encryption managed by database | Zero code change | Vendor lock-in, commercial cost | Cloud/managed Redis | Redis Enterprise docs |
| 4 | Envelope Encryption | Data encrypted with DEK, DEK encrypted with KEK | Key rotation without re-encrypting data | Complex key management | Multi-tenant, cloud KMS | AWS KMS docs |
| 5 | Compress-Then-Encrypt | Compress payload → encrypt compressed data | Better compression ratio (compress before encryption) | Must decompress on every read | Large payloads with encryption | Cryptography best practices |
| 6 | Magic Byte Header Detection | Use first N bytes to detect format (encrypted/compressed/plaintext) | Self-describing format, backward compatible | Small overhead per entry | Mixed encryption modes per cache | Custom pattern |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Key rotation mechanism — auto-rotate encryption keys without cache invalidation | ✅ | Out of scope for Phase 1. CacheEncryptionKeyProvider interface supports future implementation. | Low — keys can be rotated by evicting cache (TTL-based expiry) |
| 2 | Multi-tenant key isolation — different encryption keys per tenant | ✅ | Complex, requires tenant-aware key provider. Deferred to future enhancement. | Low — current auth-service is single-key |
| 3 | Compression ratio for typical auth-service cache entries | ❌ | Need runtime measurement — cache entries are mostly small (< 500 bytes) | Medium — compression may not be beneficial for small payloads (threshold handling) |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-24
> **Next step**: Comparison Analysis (comparison_analysis.md)
