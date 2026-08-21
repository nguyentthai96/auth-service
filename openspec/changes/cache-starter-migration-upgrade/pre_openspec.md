# Pre-OpenSpec: cache-starter-migration-upgrade

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD) + Implementation Plan
> **Classification Evidence**: keyword `cache` → module `shared/cache/`, `auth/adapter/out/cache/` → file `AbstractTwoTierCache.kt`, `MultiTierPermissionCache.kt`, `CaffeinePermissionCache.kt`
> **Archive**: N/A
> **Quality Score**: 82/100

## 📋 Feature Summary

Nâng cấp `base-cache-starter` (trong `base-core`) với 3 tính năng mới: Encryption Engine (NONE/FULL/PARTIAL), Compression Engine (Zstd/LZ4), Smart Serialization (type-aware). Sau đó migrate `auth-service` từ custom `AbstractTwoTierCache` sang sử dụng `base-cache-starter` với Spring `@Cacheable` annotation.

| Metric | Giá trị |
|--------|---------|
| Số FR | 22 (Idea: 17, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **82/100** |

---

## 1. Actors

- **Developer**: Sử dụng `@Cacheable` annotation và `@CacheEncrypt` annotation trong application code
- **System (base-cache-starter)**: Tự động encrypt, compress, serialize cache data trước khi ghi Redis L2
- **System (auth-service)**: Service tiêu thụ cache cho permissions, roles, sessions, password policy, i18n messages
- **DevOps**: Cấu hình encryption key, compression settings qua YAML/env/KeyStore

## 2. Functional Requirements

### FR-001: Migrate Permission Cache sang @Cacheable [IDEA]
- **Actor**: System (auth-service)
- **Action**: Hệ thống phải thay thế custom `AbstractTwoTierCache` + `MultiTierPermissionCache` bằng Spring `@Cacheable` annotation + `base-cache-starter` TwoLevelCacheManager khi query permissions/roles
- **Validation**: `@Cacheable(cacheNames=["permissions"])` hoạt động đúng → L1 hit → L2 hit → DB fallback

### FR-002: Xóa custom cache code [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải xóa toàn bộ custom cache code: `AbstractTwoTierCache.kt`, `CaffeinePermissionCache.kt`, `MultiTierPermissionCache.kt`, `InMemoryPermissionCache.kt`, `PermissionCache.kt` port interface
- **Validation**: Không còn import từ `shared.cache` hoặc `adapter.out.cache` trong codebase (trừ nếu có cache khác dùng)

### FR-003: Encryption Engine — Mode NONE [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải hỗ trợ mode NONE — cache data lưu Redis L2 dạng plaintext JSON, không encrypt
- **Validation**: Redis `GET` trả về JSON readable

### FR-004: Encryption Engine — Mode FULL [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải encrypt toàn bộ value bằng AES-256-GCM (AES-NI hardware accelerated) trước khi ghi Redis L2. L1 (Caffeine) giữ plaintext.
- **Validation**: Redis `GET` trả về encrypted blob (binary), decrypt roundtrip thành công

### FR-005: Encryption Engine — Mode PARTIAL [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải chỉ encrypt các fields có annotation `@CacheEncrypt` trong object, giữ các fields khác plaintext
- **Validation**: Redis `GET` trả về JSON với PII fields có prefix `ENC:`, non-PII fields readable

### FR-006: @CacheEncrypt Annotation [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cung cấp `@CacheEncrypt` annotation để developer đánh dấu fields cần encrypt trong PARTIAL mode
- **Validation**: Annotation chỉ apply cho String fields, có retention RUNTIME

### FR-007: Strategy Key Provider — Inline [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải hỗ trợ cấu hình encryption key từ YAML/env variable dạng Base64 string (`app.cache.encryption.secret-key`)
- **Validation**: Key được load 1 lần, cached in-memory, SecretKeySpec tạo thành công

### FR-008: Strategy Key Provider — KeyStore [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải hỗ trợ load encryption key từ file KeyStore (.p12/.jceks) với alias và password từ env var
- **Validation**: KeyStore load thành công, SecretKey extract từ alias

### FR-009: Strategy Key Provider — Custom [IDEA]
- **Actor**: Developer
- **Action**: Hệ thống phải cho phép developer implement `CacheEncryptionKeyProvider` interface để provide custom key (ví dụ: Tink, Vault, AWS KMS)
- **Validation**: Custom provider bean được detect qua Spring conditional

### FR-010: Compression Engine — Zstd [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải compress cache value bằng Zstd (zstd-jni) trước khi ghi Redis L2, chỉ compress khi payload > threshold (default 1KB)
- **Validation**: Compressed data có Zstd magic bytes header, decompress roundtrip thành công

### FR-011: Compression Engine — LZ4 Fallback [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải hỗ trợ LZ4 compression (lz4-java) như fallback alternative khi cần ultra-low latency
- **Validation**: Configurable per-cache `app.cache.caches.xxx.compression.algorithm=LZ4`

### FR-012: Compression Auto-Detect [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải tự động detect format khi decompress bằng magic bytes header (Zstd/LZ4/plaintext)
- **Validation**: Decompress auto-detect đúng format không cần metadata ngoài

### FR-013: Smart Serialization — Type-Aware [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải auto-detect data type và chọn serialization strategy tối ưu: String → UTF-8, Number → ASCII, Boolean → 1 byte, Object/List/Set/Map → Jackson JSON
- **Validation**: Roundtrip serialize/deserialize chính xác cho tất cả 17 data types trong spec

### FR-014: Per-Cache Configuration [IDEA]
- **Actor**: DevOps
- **Action**: Hệ thống phải cho phép override encryption mode, compression settings, serialization format riêng cho từng cache name qua `app.cache.caches.<name>.encryption/compression/serialization`
- **Validation**: Cache "permissions" = NONE + no compression, cache "userSessions" = FULL + ZSTD

### FR-015: Data Pipeline L2 Write [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải thực hiện pipeline: Object → SmartSerializer → JSON → Compressor → Encryptor → Redis L2
- **Validation**: Pipeline chạy đúng thứ tự, mỗi stage optional (configurable)

### FR-016: Data Pipeline L2 Read [IDEA]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải thực hiện reverse pipeline: Redis → Decryptor → Decompressor → SmartSerializer → Object
- **Validation**: Read pipeline auto-detect format → decrypt → decompress → deserialize

### FR-017: Cache Eviction via Kafka [IDEA]
- **Actor**: System (auth-service)
- **Action**: Hệ thống phải sử dụng `CacheManager.getCache("permissions")?.evict(key)` trong `PermissionChangedConsumer` thay vì custom `PermissionCache.evict()`
- **Validation**: Kafka event → evict L1+L2 + Pub/Sub broadcast

### FR-018: Graceful Degradation cho Encryption [ENRICHED]
- **Actor**: System (base-cache-starter)
- **Action**: Hệ thống phải graceful degrade khi encryption key missing hoặc decrypt fail — skip encrypt/log WARNING/evict stale entry
- **Validation**: App vẫn hoạt động, metrics ghi nhận encryption failures

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

- **NFR-001**: AES-256-GCM encryption latency ≤ 2μs/KB (AES-NI accelerated)
- **NFR-002**: Zstd L3 compression throughput ≥ 300MB/s
- **NFR-003**: Zero-downtime migration — old cache entries expire via TTL, new entries use new format
- **NFR-004**: All new features opt-in (disabled by default) — backward compatible

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
| Redis Pub/Sub | Cross-instance L1 invalidation | Existing — không thay đổi |
| Kafka | Permission change events | auth-service consumer — evict cache |
| Micrometer | Metrics export | Encryption + compression metrics |

## 6. Assumptions

- ⚠️ Assumption: Server có AES-NI instruction set → AES-256-GCM hardware accelerated
- ⚠️ Assumption: Encryption key rotation handled qua TTL expiry (old entries expire, new entries use new key)
- ⚠️ Assumption: PARTIAL mode chỉ hỗ trợ `String` fields (encrypted String → Base64)

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-005: "PII fields" chưa define rõ danh sách cụ thể |
| Đầy đủ (Completeness) | 20/25 | FR-009: Custom provider chưa có ví dụ concrete |
| Nhất quán (Consistency) | 22/25 | FR-015/016: Pipeline order rõ nhưng error handling giữa stages chưa spec |
| Kiểm thử được (Testability) | 18/25 | FR-013: "17 data types" — test case matrix lớn |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -3 | FR-005 | "PII fields" chưa define cụ thể, phụ thuộc vào annotation | Define default PII field names list |
| 2 | Completeness | -5 | FR-009 | Custom provider interface defined nhưng chưa có ví dụ concrete | Add TinkKeyProvider example trong docs |
| 3 | Consistency | -3 | FR-015/016 | Error handling giữa pipeline stages chưa rõ (encrypt fail → skip? → exception?) | Add FR cho pipeline error handling strategy |
| 4 | Testability | -7 | FR-013 | 17 data types → test matrix rất lớn, sealed class cần @JsonTypeInfo | Prioritize top 10 most-used types, defer edge cases |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Key rotation strategy chưa được định nghĩa rõ — encrypt với old key → decrypt fail | FR-004, FR-007 | Implement version prefix trong encrypted blob, support multi-key decrypt |
| 2 | Missing | 🟡 | Không có FR cho cache warmup sau restart — cold start vẫn phải query DB | FR-001 | Consider cache preloading strategy (optional) |
| 3 | Risk | 🟡 | Zstd JNI có thể gặp `UnsatisfiedLinkError` trên container images thiếu native lib | FR-010 | Thêm graceful fallback: try load → catch → disable compression + log WARN |

> Không phát hiện issues 🔴 Critical.

## 9. Open Questions

1. **Key rotation**: Khi đổi encryption key, cache entries cũ sẽ decrypt fail. Nên evict all hay support multi-key decrypt?
2. **PARTIAL mode scope**: `@CacheEncrypt` chỉ hỗ trợ top-level String fields hay cần support nested object fields?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Cache Infrastructure (base-core starter)
- Auth Domain — Permission caching, Session caching

### 10.2 Flow Type
- Command (internal infrastructure change, no user-facing flow)

### 10.3 Candidate Services
- **base-cache-starter** (`components/base-core/starters/base-cache-starter/`): Primary target — upgrade with encryption + compression + smart serialization
- **auth-service** (`services/auth-service/`): Consumer — migrate custom cache to base-cache-starter

### Detection Evidence
- Keyword: `cache`, `AbstractTwoTierCache`, `PermissionCache` → Module: `shared/cache/`, `auth/adapter/out/cache/` → File: `AbstractTwoTierCache.kt`, `MultiTierPermissionCache.kt`, `CaffeinePermissionCache.kt`
- Keyword: `TwoLevelCache`, `CacheAutoConfiguration` → Module: `base-cache-starter` → File: `TwoLevelCache.kt`, `CacheAutoConfiguration.kt`

### 10.4 External Integrations
- Redis (L2 cache + Pub/Sub invalidation)
- Kafka (permission change events)
- Micrometer (metrics)

### 10.5 Required Modules
- `com.ntt.basecore.autoconfigure.cache` (base-cache-starter — 10 existing classes)
- `com.ntt.authservice.shared.cache` (auth-service — 1 class, DELETE)
- `com.ntt.authservice.auth.adapter.out.cache` (auth-service — 3 classes, DELETE)
- `com.ntt.authservice.auth.application.port.out` (PermissionCache interface, DELETE)
- `com.ntt.authservice.rbac.application.query` (GetPermissionsHandler, GetUserRolesHandler — MODIFY)
- `com.ntt.authservice.auth.adapter.in.kafka` (PermissionChangedConsumer — MODIFY)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Thêm `base-cache-starter` dependency vào auth-service | Gradle |
| 2 | Developer | Cấu hình `app.cache` section trong application.yml | Spring Config |
| 3 | Developer | Thêm `@Cacheable` annotation vào query methods | Spring Cache |
| 4 | System | App startup → `CacheAutoConfiguration` detect classpath → create `TwoLevelCacheManager` | base-cache-starter |
| 5 | System | Cache miss → query DB → serialize + compress + encrypt → store Redis | Pipeline |
| 6 | System | Cache hit → read Redis → decrypt + decompress + deserialize → return | Pipeline |
| 7 | System | Kafka event → `@CacheEvict` → evict L1+L2 + Pub/Sub broadcast | Invalidation |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Idea (user) | Sec 2 | `GetPermissionsHandler`, `GetUserRolesHandler` | [MODIFY] |
| FR-002 | Idea (user) | Sec 2 | `AbstractTwoTierCache`, `CaffeinePermissionCache`, `MultiTierPermissionCache`, `InMemoryPermissionCache`, `PermissionCache` | [DELETE] |
| FR-003 | Idea (user) | Sec 2 | `SmartCacheSerializer` | [ADD] |
| FR-004 | Idea (user) | Sec 2 | `AesGcmCacheEncryptor`, `CacheEncryptor` | [ADD] |
| FR-005 | Idea (user) | Sec 2 | `SmartCacheSerializer` (PARTIAL logic) | [ADD] |
| FR-006 | Idea (user) | Sec 2 | `CacheEncrypt` annotation | [ADD] |
| FR-007 | Idea (user) | Sec 2 | `InlineCacheEncryptionKeyProvider` | [ADD] |
| FR-008 | Idea (user) | Sec 2 | `KeyStoreCacheEncryptionKeyProvider` | [ADD] |
| FR-009 | Idea (user) | Sec 2 | `CacheEncryptionKeyProvider` interface | [ADD] |
| FR-010 | Idea (user) | Sec 2 | `CacheCompressor` | [ADD] |
| FR-011 | Idea (user) | Sec 2 | `CacheCompressor` (LZ4 branch) | [ADD] |
| FR-012 | Idea (user) | Sec 2 | `CacheCompressor.decompress()` | [ADD] |
| FR-013 | Idea (user) | Sec 2 | `SmartCacheSerializer` | [ADD] |
| FR-014 | Idea (user) | Sec 2 | `CacheProperties` | [MODIFY] |
| FR-015 | Idea (user) | Sec 2 | `TwoLevelCache.put()` | [MODIFY] |
| FR-016 | Idea (user) | Sec 2 | `TwoLevelCache.get()` | [MODIFY] |
| FR-017 | Idea (user) | Sec 2 | `PermissionChangedConsumer` | [MODIFY] |
| FR-018 | Enriched | Sec 5 | `AesGcmCacheEncryptor` | [ADD] |
| FR-019 | Enriched | Sec 5 | `CacheCompressor` | [ADD] |
| FR-020 | Enriched | Sec 5 | `CacheMetricsRegistrar` | [MODIFY] |
| FR-021 | Enriched | Sec 5 | `CacheMetricsRegistrar` | [MODIFY] |
| FR-022 | Enriched | Sec 5 | `CacheEncryptionAutoConfiguration` | [ADD] |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature có scope CROSS-SERVICE: upgrade base-cache-starter (trong base-core) + migration auth-service.
- Complexity cao nhưng risk thấp vì tất cả tính năng mới đều opt-in (disabled by default).
- Code xóa ~300 LOC custom cache, thêm ~464 LOC mới trong base-cache-starter — net improvement vì code mới reusable cho tất cả services.

### Related Features / Precedents
- `2026-08-15-e2ee-performance-compliance`: Auth-service đã dùng Google Tink cho E2EE — có thể tham khảo encryption pattern.
- `2026-08-05-architecture-optimization`: Refactored `AbstractTwoTierCache` từ `MultiTierPermissionCache` — đây là code sẽ bị DELETE.

### Integration Notes
- **Redis**: base-cache-starter đã có full Redis integration (RedisCacheManager, Pub/Sub) — chỉ cần thêm custom serializer hook.
- **Kafka**: auth-service đã có `PermissionChangedConsumer` — chỉ cần thay `permissionCache.evict()` bằng `cacheManager.getCache("permissions")?.evict()`.
- **Micrometer**: `CacheMetricsAutoConfiguration` đã tồn tại — cần extend thêm encryption/compression metrics.

### Suggested Approach
1. **Phase 1**: Upgrade base-cache-starter — add encryption module (interfaces + AesGcm impl + key providers)
2. **Phase 2**: Add compression module (CacheCompressor + Zstd/LZ4)
3. **Phase 3**: Add SmartCacheSerializer (type-aware + encrypt + compress pipeline)
4. **Phase 4**: Expand CacheProperties + CacheAutoConfiguration
5. **Phase 5**: Migrate auth-service (delete custom cache, add @Cacheable, update consumers)
6. **Phase 6**: Write unit + integration tests

### Context from Confluence Images
N/A — source là user idea, không có Confluence.
