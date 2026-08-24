# Pre-OpenSpec: cache-starter-migration-upgrade

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD) + Implementation Plan + Research Artifacts
> **Classification Evidence**: keyword `cache`, `TwoLevelCacheManager`, `@Cacheable` → module `shared/config/SecurityConfig.kt`, `rbac/application/query/GetPermissionsHandler.kt` → file `SecurityConfig.kt` (TwoLevelCacheManager bean), `GetPermissionsHandler.kt` (@Cacheable), `PermissionChangedConsumer.kt` (CacheManager eviction)
> **Archive**: N/A
> **Quality Score**: 85/100

## 📋 Feature Summary

Nâng cấp `base-cache-starter` (trong `base-core`) với 3 tính năng mới: Encryption Engine (NONE/FULL/PARTIAL modes via AES-256-GCM), Compression Engine (Zstd/LZ4 with threshold), Smart Serialization (type-aware pipeline). Sau đó hoàn thiện migration `auth-service` — custom cache code đã được xóa trong lần refactor trước, hiện tại `@Cacheable` + `TwoLevelCacheManager` đã hoạt động cho permissions/roles. Cần nâng cấp starter + cải thiện `SecurityConfig` bean + thêm cache encryption config.

| Metric | Giá trị |
|--------|---------|
| Số FR | 22 (Idea: 17, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **85/100** |

---

## 1. Actors

- **Developer**: Sử dụng `@Cacheable` annotation và `@CacheEncrypt` annotation trong application code; cấu hình cache per-name trong YAML
- **System (base-cache-starter)**: Tự động encrypt, compress, serialize cache data trước khi ghi Redis L2 (pipeline: serialize → compress → encrypt)
- **System (auth-service)**: Service tiêu thụ cache cho permissions, roles, token blacklist, i18n messages
- **DevOps**: Cấu hình encryption key, compression settings qua YAML/env/KeyStore

## 2. Functional Requirements

### FR-001: Hoàn thiện Migration Permission Cache [IDEA]
- **Actor**: System (auth-service)
- **Action**: Hệ thống phải hoàn thiện migration cache — custom `AbstractTwoTierCache` + implementations đã được xóa (5 files, ~300 LOC). `@Cacheable` đã được thêm vào `GetPermissionsHandler` và `GetUserRolesHandler`. Cần đơn giản hóa `SecurityConfig.twoLevelCacheManager()` bean để tận dụng auto-configuration từ base-cache-starter thay vì tạo manual `ConcurrentMapCacheManager` L1.
- **Validation**: `TwoLevelCacheManager` auto-configured với Caffeine L1 (thay vì ConcurrentMapCacheManager hiện tại) + Redis L2

### FR-002: Xác nhận custom cache code đã xóa [IDEA]
- **Actor**: Developer
- **Action**: Xác nhận custom cache code đã được xóa thành công: `AbstractTwoTierCache.kt`, `CaffeinePermissionCache.kt`, `MultiTierPermissionCache.kt`, `InMemoryPermissionCache.kt`, `PermissionCache.kt` port interface — tổng 5 files. Directories `shared/cache/` và `auth/adapter/out/cache/` đã empty.
- **Validation**: `shared/cache/` = empty, `auth/adapter/out/cache/` = empty, `auth/application/port/out/PermissionCache.kt` = không tồn tại

### FR-003: Encryption Engine — Mode NONE [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải hỗ trợ mode NONE — cache data lưu Redis L2 dạng plaintext JSON, không encrypt. Đây là default mode.
- **Validation**: Redis `GET` trả về JSON readable, backward compatible với hiện tại

### FR-004: Encryption Engine — Mode FULL [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải encrypt toàn bộ value bằng AES-256-GCM (AES-NI hardware accelerated) trước khi ghi Redis L2. L1 (Caffeine) giữ plaintext. Magic header `0xCA 0xCE 0x01` + 12-byte IV + ciphertext.
- **Validation**: Redis `GET` trả về encrypted blob (binary), decrypt roundtrip thành công, L1 vẫn plaintext

### FR-005: Encryption Engine — Mode PARTIAL [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải chỉ encrypt các fields có annotation `@CacheEncrypt` trong object, giữ các fields khác plaintext. Encrypted fields có prefix `ENC:` + Base64(IV + ciphertext + tag).
- **Validation**: Redis `GET` trả về JSON với PII fields có prefix `ENC:`, non-PII fields readable

### FR-006: @CacheEncrypt Annotation [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `@CacheEncrypt` annotation để developer đánh dấu fields cần encrypt trong PARTIAL mode. Annotation chỉ apply cho String fields.
- **Validation**: Annotation có retention RUNTIME, target FIELD. Reflection scan cached per data class.

### FR-007: Strategy Key Provider — Inline [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải hỗ trợ cấu hình encryption key từ YAML/env variable dạng Base64 string (`app.cache.encryption.secret-key`)
- **Validation**: Key được load 1 lần, cached in-memory, SecretKeySpec(AES, 256-bit) tạo thành công

### FR-008: Strategy Key Provider — KeyStore [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải hỗ trợ load encryption key từ file KeyStore (.p12/.jceks) với alias và password từ env var
- **Validation**: KeyStore load thành công, SecretKey extract từ alias

### FR-009: Strategy Key Provider — Custom [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cho phép developer implement `CacheEncryptionKeyProvider` interface để provide custom key (ví dụ: Tink, Vault, AWS KMS)
- **Validation**: Custom provider bean được detect qua Spring `@ConditionalOnMissingBean`

### FR-010: Compression Engine — Zstd [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải compress cache value bằng Zstd (zstd-jni 1.5.7-15) trước khi ghi Redis L2, chỉ compress khi payload > threshold (default 1KB)
- **Validation**: Compressed data có Zstd magic bytes header `0x28 0xB5 0x2F 0xFD`, decompress roundtrip thành công

### FR-011: Compression Engine — LZ4 Fallback [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải hỗ trợ LZ4 compression (lz4-java 1.8.0) như fallback alternative khi cần ultra-low latency
- **Validation**: Configurable per-cache `app.cache.caches.xxx.compression.algorithm=LZ4`

### FR-012: Compression Auto-Detect [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải tự động detect format khi decompress bằng magic bytes header (Zstd/LZ4/plaintext)
- **Validation**: Decompress auto-detect đúng format không cần metadata ngoài

### FR-013: Smart Serialization — Type-Aware [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải auto-detect data type và chọn serialization strategy tối ưu: String → UTF-8, Number → ASCII, Boolean → 1 byte, Object/List/Set/Map → Jackson JSON
- **Validation**: Roundtrip serialize/deserialize chính xác cho tất cả data types. Magic byte header per format.

### FR-014: Per-Cache Configuration [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải cho phép override encryption mode, compression settings, serialization format riêng cho từng cache name qua `app.cache.caches.<name>.encryption/compression/serialization`
- **Validation**: Cache "permissions" = NONE + no compression, cache "sessions" = FULL + ZSTD

### FR-015: Data Pipeline L2 Write [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải thực hiện pipeline: Object → SmartSerializer → JSON → Compressor → Encryptor → Redis L2. Pipeline chạy đúng thứ tự, mỗi stage optional (configurable).
- **Validation**: Pipeline write path hoạt động đúng, mỗi stage detect qua config

### FR-016: Data Pipeline L2 Read [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải thực hiện reverse pipeline: Redis → Decryptor → Decompressor → SmartSerializer → Object. Auto-detect format via magic bytes.
- **Validation**: Read pipeline auto-detect format → decrypt → decompress → deserialize

### FR-017: Cache Eviction via Kafka [IDEA]
- **Actor**: System (auth-service)
- **Action**: `PermissionChangedConsumer` đã sử dụng `CacheManager.getCache("permissions")?.clear()` và `cacheManager.getCache("roles")?.clear()`. Cần cải thiện từ `clear()` (xóa toàn bộ) sang targeted `evict(key)` dựa trên userId/domainId từ Kafka event.
- **Validation**: Kafka event chứa userId/domainId → evict chỉ key tương ứng (L1+L2 + Pub/Sub broadcast)

### FR-018: Graceful Degradation cho Encryption [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải graceful degrade khi decrypt fail — evict stale entry, log WARN, fall through to DB query. Encryption key missing at startup → fail-fast.
- **Validation**: App vẫn hoạt động khi decrypt fail, metrics ghi nhận encryption failures

### FR-019: Compression Threshold Skip [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải skip compression cho payload nhỏ hơn threshold (default 1KB) — tránh overhead khi compress data nhỏ
- **Validation**: Payload 500 bytes → không compressed, payload 2KB → compressed

### FR-020: Encryption Metrics [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải expose Micrometer metrics cho encryption operations: encrypt count, decrypt count, encrypt/decrypt duration, encryption failures
- **Validation**: `/actuator/metrics/cache.encryption.*` endpoints tồn tại

### FR-021: Compression Metrics [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải expose metrics cho compression: compress count, compress ratio, decompress duration
- **Validation**: `/actuator/metrics/cache.compression.*` endpoints tồn tại

### FR-022: Auto-Config Conditional [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải tự động enable/disable encryption và compression beans dựa trên classpath + config properties — không bắt buộc consumer phải mang thêm dependency nếu không dùng
- **Validation**: `compileOnly` deps — app chạy OK khi không có zstd-jni/lz4-java trên classpath

## 3. Non-functional Requirements

- **NFR-001**: AES-256-GCM encryption overhead ≤ 50μs/KB (AES-NI accelerated)
- **NFR-002**: Zstd L3 compression throughput ≥ 300MB/s, ratio ≥ 2.5x for >1KB payloads
- **NFR-003**: Zero-downtime migration — old cache entries expire via TTL, new entries use new format
- **NFR-004**: All new features opt-in (disabled by default) — 100% backward compatible
- **NFR-005**: Permission lookup L1 hit < 1ms P95, L2 hit < 10ms P95
- **NFR-006**: Cross-instance eviction propagation < 100ms via Redis Pub/Sub

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Tất cả 22 FRs có scope rõ ràng và không overlap.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-018** [ENRICHED]: Graceful degradation — cần thiết khi encryption key bị thay đổi/missing
- **FR-019** [ENRICHED]: Compression threshold — tránh overhead không cần thiết
- **FR-020** [ENRICHED]: Encryption metrics — observability quan trọng cho production
- **FR-021** [ENRICHED]: Compression metrics — monitoring compression effectiveness
- **FR-022** [ENRICHED]: Auto-config conditional — đảm bảo backward compatibility

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis L2 | Distributed cache storage | Encrypted + compressed data stored here |
| Redis Pub/Sub | Cross-instance L1 invalidation | Existing via `CacheInvalidationPublisher` — không thay đổi |
| Kafka | Permission change events | `PermissionChangedConsumer` — topic `iam.permission.changed` — MODIFY (targeted evict) |
| Micrometer | Metrics export | Encryption + compression metrics — EXTEND |

## 6. Assumptions

- ⚠️ Assumption: Server có AES-NI instruction set — Lý do: x86-64 server CPUs đều hỗ trợ AES-NI từ ~2010
- ⚠️ Assumption: Encryption key rotation handled qua TTL expiry (old entries expire, new entries use new key) — Lý do: Cache data ephemeral, TTL tự xóa entries cũ
- ⚠️ Assumption: PARTIAL mode chỉ hỗ trợ `String` fields — Lý do: encrypted String → Base64 prefix `ENC:`, other types phức tạp hơn
- ⚠️ Assumption: `base-cache-starter` API stable cho extension — Lý do: 0.0.1-SNAPSHOT nhưng internal team owns it, chỉ additive changes

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-005: "PII fields" phụ thuộc vào @CacheEncrypt annotation — danh sách cụ thể tùy developer |
| Đầy đủ (Completeness) | 22/25 | FR-001: Cần verify SecurityConfig auto-config thay thế custom bean |
| Nhất quán (Consistency) | 23/25 | FR-015/016: Pipeline error handling giữa stages rõ ràng hơn (FR-018 covers) |
| Kiểm thử được (Testability) | 18/25 | FR-013: Smart serialization cho nhiều data types → test matrix lớn |
| **Tổng** | **85/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -3 | FR-005 | "PII fields" phụ thuộc vào @CacheEncrypt annotation, không define danh sách cụ thể | Chấp nhận — linh hoạt theo developer annotation |
| 2 | Completeness | -3 | FR-001 | SecurityConfig twoLevelCacheManager bean cần verify auto-config path | Add integration test verify auto-config |
| 3 | Consistency | -2 | FR-015 | Pipeline stage error handling — FR-018 covers graceful degradation nhưng cần rõ hơn per stage | Document per-stage error behavior |
| 4 | Testability | -7 | FR-013 | Smart serialization test matrix lớn (String, Number, Boolean, Object, List, Set, Map, null) | Prioritize top types, defer edge cases |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Key rotation strategy chưa được định nghĩa — encrypt với old key → decrypt fail → TTL-based recovery | FR-004, FR-007 | Accept TTL-based approach cho v1, implement version prefix cho v2 |
| 2 | Missing | 🟡 | `PermissionChangedConsumer` đang dùng `clear()` thay vì targeted `evict(key)` — inefficient | FR-017 | Parse Kafka event JSON → extract userId/domainId → targeted eviction |
| 3 | Risk | 🟡 | Zstd JNI `UnsatisfiedLinkError` trên container images thiếu native lib | FR-010 | `@ConditionalOnClass` + graceful fallback: try load → catch → disable compression + log WARN |

> Không phát hiện issues 🔴 Critical.

## 9. Open Questions

1. **Key rotation**: Khi đổi encryption key, nên evict all hay support multi-key decrypt? → ⚠️ Assumption: TTL-based expiry cho v1
2. **PARTIAL mode scope**: `@CacheEncrypt` chỉ hỗ trợ top-level String fields hay cần support nested object fields? → ⚠️ Assumption: Top-level String only cho v1

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Cache Infrastructure (base-core starter — base-cache-starter)
- Auth Domain — Permission caching, Role caching, Token blacklist caching

### 10.2 Flow Type
- Command (internal infrastructure change, no user-facing API changes)

### 10.3 Candidate Services
- **base-cache-starter** (`components/base-core/starters/base-cache-starter/`): Primary target — upgrade with encryption engine + compression engine + smart serialization pipeline
- **auth-service** (`services/auth-service/`): Consumer — hoàn thiện migration, cải thiện SecurityConfig bean, thêm encryption config

### Detection Evidence
- Keyword: `@Cacheable`, `baseCacheKeyGenerator` → Module: `rbac/application/query/` → File: `GetPermissionsHandler.kt` (line 33), `GetUserRolesHandler.kt` (line 26)
- Keyword: `TwoLevelCacheManager`, `CacheProperties`, `CacheInvalidationPublisher` → Module: `shared/config/` → File: `SecurityConfig.kt` (lines 105-118)
- Keyword: `CacheManager`, `cache.evict` → Module: `auth/adapter/in/kafka/` → File: `PermissionChangedConsumer.kt` (lines 19, 32-33)
- Keyword: `Caffeine`, `RedisTemplate`, cache → Module: `auth/application/` → File: `TokenBlacklistCacheService.kt` (custom L1+L2 with circuit breaker)
- Keyword: `Caffeine`, cache → Module: `shared/i18n/` → File: `DatabaseMessageSource.kt` (Caffeine-only cache, candidate for @Cacheable upgrade)

### 10.4 External Integrations
- **Redis** (L2 cache + Pub/Sub invalidation) — `spring-boot-starter-data-redis` already in build.gradle.kts
- **Kafka** — `spring-kafka` for permission change events → cache eviction
- **Micrometer** — `spring-boot-starter-actuator` for cache metrics
- **Caffeine** — `com.github.ben-manes.caffeine:caffeine` already in build.gradle.kts
- **base-cache-starter** — `com.ntt:base-cache-starter` already in build.gradle.kts

### 10.5 Required Modules
- `com.ntt.basecore.autoconfigure.cache` (base-cache-starter — existing: TwoLevelCacheManager, TwoLevelCache, CacheProperties, CacheAutoConfiguration, CacheInvalidationPublisher, CacheInvalidationListener)
- `com.ntt.basecore.autoconfigure.cache.encryption` (base-cache-starter — NEW: CacheEncryptor, AesGcmCacheEncryptor, CacheEncryptionKeyProvider, InlineCacheEncryptionKeyProvider, KeyStoreCacheEncryptionKeyProvider, CacheEncrypt annotation, EncryptionMode enum, CacheEncryptionAutoConfiguration)
- `com.ntt.basecore.autoconfigure.cache.compression` (base-cache-starter — NEW: CacheCompressor, CompressionAlgorithm enum)
- `com.ntt.basecore.autoconfigure.cache.serialization` (base-cache-starter — NEW: SmartCacheSerializer, SerializationFormat enum)
- `com.ntt.authservice.shared.config` (auth-service — MODIFY: SecurityConfig.twoLevelCacheManager())
- `com.ntt.authservice.auth.adapter.in.kafka` (auth-service — MODIFY: PermissionChangedConsumer targeted eviction)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Upgrade base-cache-starter với encryption/compression/serialization modules | Gradle multi-module build |
| 2 | Developer | Mở rộng CacheProperties với encryption/compression/serialization sections | base-cache-starter |
| 3 | Developer | Cập nhật CacheAutoConfiguration register encryption/compression beans | base-cache-starter |
| 4 | Developer | Cải thiện SecurityConfig.twoLevelCacheManager() — delegate to auto-config hoặc dùng Caffeine L1 thay ConcurrentMapCacheManager | auth-service |
| 5 | Developer | Thêm app.cache.encryption config trong application.yml | auth-service |
| 6 | Developer | Cải thiện PermissionChangedConsumer — targeted eviction thay vì clear() | auth-service |
| 7 | System | App startup → CacheAutoConfiguration detect classpath → create TwoLevelCacheManager + SmartCacheSerializer | base-cache-starter |
| 8 | System | Cache miss → query DB → serialize + compress + encrypt → store Redis | Pipeline |
| 9 | System | Cache hit → read Redis → decrypt + decompress + deserialize → return | Pipeline |
| 10 | System | Kafka event → evict(key) → L1+L2 + Pub/Sub broadcast | Invalidation |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Idea (user) | Sec 2 | `SecurityConfig.twoLevelCacheManager()` (`shared/config/SecurityConfig.kt`) | [MODIFY] |
| FR-002 | Idea (user) | Sec 2 | `shared/cache/` (empty), `auth/adapter/out/cache/` (empty) | [DONE] — verified deleted |
| FR-003 | Idea (user) | Sec 2 | `SmartCacheSerializer` (NEW in base-cache-starter) | [ADD] |
| FR-004 | Idea (user) | Sec 2 | `AesGcmCacheEncryptor`, `CacheEncryptor` (NEW in base-cache-starter) | [ADD] |
| FR-005 | Idea (user) | Sec 2 | `SmartCacheSerializer` PARTIAL logic (NEW in base-cache-starter) | [ADD] |
| FR-006 | Idea (user) | Sec 2 | `CacheEncrypt` annotation (NEW in base-cache-starter) | [ADD] |
| FR-007 | Idea (user) | Sec 2 | `InlineCacheEncryptionKeyProvider` (NEW in base-cache-starter) | [ADD] |
| FR-008 | Idea (user) | Sec 2 | `KeyStoreCacheEncryptionKeyProvider` (NEW in base-cache-starter) | [ADD] |
| FR-009 | Idea (user) | Sec 2 | `CacheEncryptionKeyProvider` interface (NEW in base-cache-starter) | [ADD] |
| FR-010 | Idea (user) | Sec 2 | `CacheCompressor` (NEW in base-cache-starter) | [ADD] |
| FR-011 | Idea (user) | Sec 2 | `CacheCompressor` LZ4 branch (NEW in base-cache-starter) | [ADD] |
| FR-012 | Idea (user) | Sec 2 | `CacheCompressor.decompress()` magic byte detection (NEW) | [ADD] |
| FR-013 | Idea (user) | Sec 2 | `SmartCacheSerializer` (NEW in base-cache-starter) | [ADD] |
| FR-014 | Idea (user) | Sec 2 | `CacheProperties` (`base-cache-starter`) | [MODIFY] |
| FR-015 | Idea (user) | Sec 2 | `TwoLevelCache.put()` (`base-cache-starter`) | [MODIFY] |
| FR-016 | Idea (user) | Sec 2 | `TwoLevelCache.get()` (`base-cache-starter`) | [MODIFY] |
| FR-017 | Idea (user) | Sec 2 | `PermissionChangedConsumer` (`auth/adapter/in/kafka/PermissionChangedConsumer.kt`) | [MODIFY] |
| FR-018 | Enriched | Sec 5 | `AesGcmCacheEncryptor` error handling (NEW) | [ADD] |
| FR-019 | Enriched | Sec 5 | `CacheCompressor` threshold logic (NEW) | [ADD] |
| FR-020 | Enriched | Sec 5 | `CacheMetricsRegistrar` (`base-cache-starter`) | [MODIFY] |
| FR-021 | Enriched | Sec 5 | `CacheMetricsRegistrar` (`base-cache-starter`) | [MODIFY] |
| FR-022 | Enriched | Sec 5 | `CacheEncryptionAutoConfiguration` (NEW in base-cache-starter) | [ADD] |

### Change Impact Map (EXTEND)

```
FR-001 → [MODIFY] SecurityConfig.twoLevelCacheManager() (shared/config/SecurityConfig.kt) → simplify to use Caffeine L1
FR-002 → [DONE] 5 files already deleted (shared/cache/, auth/adapter/out/cache/, auth/application/port/out/PermissionCache.kt)
FR-003~FR-016 → [ADD] 12 new classes in base-cache-starter (encryption/compression/serialization packages)
FR-014 → [MODIFY] CacheProperties (base-cache-starter) → add encryption/compression/serialization sections
FR-015 → [MODIFY] TwoLevelCache.put() (base-cache-starter) → wire SmartCacheSerializer
FR-016 → [MODIFY] TwoLevelCache.get() (base-cache-starter) → wire SmartCacheSerializer
FR-017 → [MODIFY] PermissionChangedConsumer (auth/adapter/in/kafka/PermissionChangedConsumer.kt) → targeted evict(key)
FR-020~FR-021 → [MODIFY] CacheMetricsRegistrar (base-cache-starter) → add encryption/compression metrics
```

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations

- **Migration trạng thái hiện tại**: Custom cache code (AbstractTwoTierCache, CaffeinePermissionCache, MultiTierPermissionCache, InMemoryPermissionCache, PermissionCache port) đã được xóa thành công trong lần refactor trước. Directories `shared/cache/` và `auth/adapter/out/cache/` đều empty. `GetPermissionsHandler` và `GetUserRolesHandler` đã có `@Cacheable` annotation.
- **SecurityConfig cần cải thiện**: Bean `twoLevelCacheManager()` hiện tại tạo `ConcurrentMapCacheManager` thay vì `CaffeineCacheManager` cho L1. Nên delegate cho auto-configuration hoặc sử dụng Caffeine CacheManager.
- **PermissionChangedConsumer**: Đang dùng `clear()` (xóa toàn bộ cache) thay vì targeted `evict(key)`. Cần parse Kafka event JSON để extract userId/domainId.
- **TokenBlacklistCacheService**: Custom L1+L2 cache implementation (155 LOC) với Caffeine + Redis + circuit breaker. Đây là candidate cho @Cacheable migration nhưng circuit breaker logic phức tạp — cân nhắc keep as-is hoặc migrate sau.
- **DatabaseMessageSource**: Caffeine-only cache cho i18n messages. Candidate cho @Cacheable upgrade — thấp ưu tiên.
- **AltchaCaptchaVerifier**: Caffeine-only cache cho replay protection. Keep as-is — in-memory only, không cần L2.
- Feature có scope **CROSS-SERVICE**: upgrade base-cache-starter (trong base-core) + hoàn thiện auth-service. Complexity cao nhưng risk thấp vì tất cả tính năng mới đều opt-in (disabled by default).
- Code thêm ~464 LOC mới trong base-cache-starter — net improvement vì reusable cho tất cả services.

### Related Features / Precedents
- `2026-08-15-e2ee-performance-compliance` (archive): Auth-service đã dùng Google Tink cho E2EE — có thể tham khảo encryption pattern. Nhưng JCA preferred cho cache encryption (simpler, no extra dep).
- `2026-08-21-cache-metrics-refactoring` (archive): Cache metrics refactoring — related to FR-020/FR-021.
- `2026-08-05-architecture-optimization` (archive): Refactored AbstractTwoTierCache — đây là code đã được DELETE.

### Integration Notes
- **Redis**: `base-cache-starter` đã có full Redis integration (TwoLevelCacheManager, Pub/Sub). `auth-service` đã có `spring-boot-starter-data-redis` + `RedisConfig` bean.
- **Kafka**: `PermissionChangedConsumer` đã active, topic `iam.permission.changed`, groupId `auth-service`. `@ConditionalOnProperty` gate.
- **Micrometer**: `spring-boot-starter-actuator` đã trong build.gradle.kts. `base-observability-starter` provides base metrics.
- **Build**: `com.ntt:platform:0.0.1-SNAPSHOT` BOM manages base-* versions. `com.ntt:base-cache-starter` already in dependencies. Need to add `zstd-jni` and `lz4-java` to version-catalog.

### Suggested Approach
1. **Phase 1**: Upgrade base-cache-starter — add encryption module (interfaces + AesGcm impl + key providers + @CacheEncrypt annotation + EncryptionMode enum)
2. **Phase 2**: Add compression module (CacheCompressor + Zstd/LZ4 + CompressionAlgorithm enum)
3. **Phase 3**: Add SmartCacheSerializer (type-aware + encrypt + compress pipeline)
4. **Phase 4**: Expand CacheProperties + CacheAutoConfiguration + CacheEncryptionAutoConfiguration
5. **Phase 5**: Auth-service — simplify SecurityConfig bean + add encryption config in application.yml + improve PermissionChangedConsumer
6. **Phase 6**: Unit tests + integration tests

### Context from Confluence Images
N/A — source là user idea + research artifacts, không có Confluence.
