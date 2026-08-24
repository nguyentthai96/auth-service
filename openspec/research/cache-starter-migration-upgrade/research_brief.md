# Research Brief: Cache Starter Migration & Upgrade

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Cache Starter Migration & Upgrade |
| **Ngày tạo** | 2026-08-24 |
| **Input source** | name + implementation_plan.md |
| **Input content** | Migrate auth-service sang base-cache-starter; nâng cấp starter với encryption (FULL/PARTIAL/NONE modes), compression (Zstd/LZ4), smart serialization |
| **Người yêu cầu** | nguyentthai96 |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
Auth-service hiện có custom cache implementation `AbstractTwoTierCache` (~300 LOC) với 3 implementations (CaffeinePermissionCache, MultiTierPermissionCache, InMemoryPermissionCache) và `PermissionCache` port interface. Trong khi đó, `base-cache-starter` từ `base-core` platform đã cung cấp sẵn `TwoLevelCacheManager` + `TwoLevelCache` với Redis Pub/Sub cross-instance invalidation, Micrometer metrics, per-cache config, graceful degradation.

Bài toán chính:
1. **Technical debt**: Duplicate cache code trong auth-service khi `base-cache-starter` đã cung cấp tương đương
2. **Security compliance**: PII data (email, phone, session tokens) trong Redis cache cần encryption at rest (GDPR, internal security policy)
3. **Performance**: Serialization chưa tối ưu — mọi cache entry đều dùng Jackson JSON, chưa có compression cho large payloads

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Migrate auth-service — xóa custom cache code (~300 LOC), dùng `@Cacheable` + `base-cache-starter`
- [x] Objective 2: Nâng cấp base-cache-starter — thêm cache encryption modes (NONE/FULL/PARTIAL) với AES-256-GCM
- [x] Objective 3: Nâng cấp base-cache-starter — thêm compression support (Zstd/LZ4) cho large payloads
- [x] Objective 4: Nâng cấp base-cache-starter — smart serialization per data type (String, Number, Object, List, etc.)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Migration auth-service sang base-cache-starter | Migration các service khác (system-admin-service, account-service) |
| Encryption: NONE, FULL, PARTIAL modes via AES-256-GCM | Key rotation mechanism (deferred — interface supports it) |
| `@CacheEncrypt` annotation cho field-level encryption | Database-level encryption |
| Per-cache encryption/compression/serialization config | Custom serialization format (Protobuf/Kryo/MessagePack) |
| Compression: Zstd L3 (default) + LZ4 (alternative) | Brotli/GZIP (không phù hợp cho cache latency) |
| Smart serialization for String, Number, Bool, Object, List, Set, Map | Custom data structures, binary blob caching |
| CacheKeyGenerator collision-safe key generation | Distributed lock mechanism |
| Magic byte header-based format detection | Schema evolution / versioning |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `two-level cache Spring Boot`
- `cache encryption at rest`
- `field-level encryption cache`
- `Redis serialization optimization`
- `per-cache serializer strategy`
- `Caffeine Redis two-tier cache`

### 3.2 Secondary Keywords
- `AES-GCM cache encryption Java`
- `Spring Cache @Cacheable custom CacheManager`
- `Redis Hash vs String vs JSON performance`
- `cache data type serialization`
- `Zstd compression JVM`
- `LZ4 Java benchmark`
- `cache compression threshold`

### 3.3 Domain-Specific Terms
- `TwoLevelCache`: L1 (Caffeine in-process) + L2 (Redis distributed) cache pattern
- `Cache Invalidation Broadcast`: Redis Pub/Sub để đồng bộ L1 eviction across instances
- `Field-Level Encryption (FLE)`: Mã hóa chỉ những field nhạy cảm (PII/sensitive), giữ nguyên field khác plaintext
- `SmartCacheSerializer`: Type-aware serializer tự detect data type và chọn optimal format
- `CacheEncryptor`: Interface abstraction cho encryption/decryption of cache values
- `CacheCompressor`: Zstd/LZ4 compression with threshold-based auto-detect
- `Magic Byte Header`: First N bytes để detect format (encrypted/compressed/plaintext) khi read

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Spring Boot two-level cache Caffeine Redis migration best practices"` | General patterns | High |
| 2 | `"cache encryption at rest AES-GCM Java Redis"` | Encryption approach | High |
| 3 | `"JetCache vs J2Cache vs Spring Cache multi-level"` | Open Source comparison | High |
| 4 | `"Redis field-level encryption selective PII GDPR"` | Partial encryption | Medium |
| 5 | `"Zstd vs LZ4 Java JVM benchmark compression"` | Compression choice | Medium |
| 6 | `"Spring @Cacheable custom CacheManager two-level"` | Migration approach | Medium |
| 7 | `"AES-GCM vs ChaCha20-Poly1305 Java performance"` | Encryption algorithm | Low |
| 8 | `"Google Tink vs JCA AES performance benchmark"` | Encryption library | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| `AbstractTwoTierCache<K,V>` | `authservice/shared/cache/` | HIGH | Custom L1+L2 generic — target DELETE |
| `MultiTierPermissionCache` | `authservice/auth/adapter/out/cache/` | HIGH | Extends AbstractTwoTierCache — target DELETE |
| `CaffeinePermissionCache` | `authservice/auth/adapter/out/cache/` | HIGH | L1-only Caffeine — target DELETE |
| `InMemoryPermissionCache` | `authservice/auth/adapter/out/cache/` | HIGH | Dead code — target DELETE |
| `PermissionCache` port | `authservice/auth/application/port/out/` | HIGH | Interface — target DELETE (replaced by @Cacheable) |
| `base-cache-starter` (TwoLevelCacheManager) | `base-core/starters/base-cache-starter/` | HIGH | Target starter — will be upgraded |
| `TwoLevelCache` | `base-cache-starter` | HIGH | Spring Cache impl — will gain encryption/compression |
| `CacheAutoConfiguration` | `base-cache-starter` | HIGH | Auto-config — will be extended |
| `CacheProperties` | `base-cache-starter` | HIGH | Config — will gain encryption/compression sections |
| `CacheInvalidationPublisher/Listener` | `base-cache-starter` | HIGH | Redis Pub/Sub — REUSE as-is |
| `SecurityConfig.twoLevelCacheManager()` | `authservice/shared/config/` | HIGH | Custom @Primary CacheManager bean — target MODIFY |
| `GetPermissionsHandler` | `authservice/rbac/application/query/` | HIGH | Already uses @Cacheable — verify works with starter |
| `PermissionChangedConsumer` | `authservice/auth/adapter/in/kafka/` | MEDIUM | Kafka → cache eviction trigger — target MODIFY |
| `DatabaseMessageSource` | `authservice/shared/i18n/` | MEDIUM | Caffeine-only cache — candidate for @Cacheable upgrade |
| `TokenBlacklistCacheService` | `authservice/auth/application/` | MEDIUM | Redis direct — candidate for @Cacheable |
| `RedisCipherKeySessionResolver` | `authservice/auth/adapter/out/cipher/` | LOW | Redis-only — possible candidate |

### 4.2 Existing Code Patterns
- **Architecture**: Clean Architecture (Hexagonal) — ports & adapters pattern
- **Data access**: JPA Repository + Spring Data Redis (`StringRedisTemplate`)
- **API style**: REST Controllers + Handler-based CQRS (command/query separation)
- **Error handling**: `@RestControllerAdvice` + RFC 7807 `ProblemDetail`
- **Cache (current)**: Mix of custom `AbstractTwoTierCache` (manual) + Spring `@Cacheable` (annotation)
- **Logging**: SLF4J + Logback + MDC for trace context
- **Build**: Gradle Kotlin DSL + `com.ntt:platform` BOM + version-catalog
- **Security**: Spring Security + JWT (jjwt) + Google Tink (E2EE)
- **Event system**: Spring Kafka consumer/producer + Event Sourcing utils

### 4.3 Tech Stack Constraints
- Language: Kotlin 2.x, JDK 25
- Framework: Spring Boot 4.1.0
- Database: PostgreSQL (primary) + Redis (cache, pub/sub, session)
- Build tool: Gradle 8.9 + Kotlin DSL
- Encryption: Google Tink already in auth-service for E2EE; JCA (javax.crypto) available natively
- Platform: `com.ntt:platform:0.0.1-SNAPSHOT` manages all base-* starter versions
- Observability: Micrometer + Spring Boot Actuator
- Messaging: Spring Kafka

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `base-cache-starter` | Library (internal) | `components/base-core/starters/base-cache-starter/` | Target for upgrade — add encryption/compression/serialization |
| `platform` BOM | Build dependency | `com.ntt:platform:0.0.1-SNAPSHOT` | Manages base-* versions — needs version bump |
| `version-catalog` | Build dependency | `com.ntt:version-catalog:0.0.1-SNAPSHOT` | Shared versions TOML — add zstd-jni, lz4-java |
| `build-logic` | Build convention | `com.ntt.build:build-logic:0.0.1-SNAPSHOT` | Convention plugins — no change needed |
| `PermissionCache` port | Interface | `auth/application/port/out/PermissionCache.kt` | 3 implementations → DELETE, replace with @Cacheable |
| `PermissionChangedConsumer` | Kafka listener | `auth/adapter/in/kafka/PermissionChangedConsumer.kt` | Cache eviction trigger → MODIFY to use CacheManager.evict() |
| Redis | External infrastructure | via spring-boot-starter-data-redis | L2 cache + pub/sub invalidation |
| Micrometer | Observability | via CacheMetricsAutoConfiguration | Cache metrics (hit/miss/eviction) |
| SecurityConfig | Configuration | `shared/config/SecurityConfig.kt` | Custom @Primary TwoLevelCacheManager bean → SIMPLIFY |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: `base-cache-starter` hiện tại hỗ trợ encryption không? → **KHÔNG** — phải build
- [x] Q2: Có thể thêm per-cache encryption mode mà không breaking existing API? → **CÓ** — additive change, all new features opt-in (disabled by default)
- [x] Q3: Serialization tối ưu cho từng data type nên implement ở đâu? → Custom `SmartCacheSerializer` extends `RedisSerializer<Object>`
- [x] Q4: Có open source nào đã implement cache encryption + two-level? → **KHÔNG** — JetCache, J2Cache đều không có encryption
- [x] Q5: AES-GCM hay AES-CBC cho cache encryption? → **AES-256-GCM** — authenticated encryption, prevents tampering
- [x] Q6: Partial field encryption dùng AOP hay custom serializer? → `@CacheEncrypt` annotation + custom serializer reflection scan
- [x] Q7: Compression algorithm: Zstd vs LZ4 vs Snappy? → **Zstd L3** default (best ratio/speed), LZ4 optional (fastest decompress)
- [x] Q8: Google Tink vs JCA for cache encryption? → **JCA direct** — same perf, zero extra dependency, simpler

### 5.2 Assumptions cần verify
- [x] A1: `base-cache-starter` API đã stable → **VERIFIED** — 0.0.1-SNAPSHOT but internal team owns it, can modify freely
- [x] A2: JCA AES-256-GCM có hardware acceleration (AES-NI) trên deployment target → **VERIFIED** — x86-64 servers with AES-NI
- [x] A3: Redis Pub/Sub channel per service sẽ không conflict → **VERIFIED** — `cache:invalidation:{service}` pattern
- [x] A4: Zstd JNI native library available trên deployment platform → **NEEDS RUNTIME CHECK** — `@ConditionalOnClass` handles gracefully

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ context để design & implement | ≥ 5 unique sources verified |
| Open source options | Đánh giá alternatives trước quyết định build | ≥ 3 repos/projects evaluated |
| Gap analysis | Feature gaps giữa available vs needed | All critical gaps documented |
| Business analysis | Use cases decomposed & specified | All UCs have basic + exception flows |
| Technical spec | Agent-ready specification cho implementation | Architecture + data flow + API + classes listed |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
