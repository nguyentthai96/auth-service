# Technical Design: Base Cache Starter Upgrade & Migration

> **Change ID**: cache-starter-migration-upgrade
> **Profile**: Command | N/A | EXTEND

## 1. System Architecture

Hệ thống bộ nhớ đệm kép (Two-Level Caching) với:
- **L1 (Local Cache):** Caffeine (In-memory, siêu nhanh, dùng cho data tĩnh/ít thay đổi).
- **L2 (Distributed Cache):** Redis (Shared giữa các instances, bền vững hơn).

Quy trình hoạt động (Data Pipeline) ở L2:
- **Write:** Java Object → SmartSerializer (tự chọn JSON/String/Binary) → Compress (Zstd/LZ4) → Encrypt (AES-256-GCM) → Redis.
- **Read:** Redis → Decrypt → Decompress → SmartDeserialize → Java Object.

## 2. Core Components (base-cache-starter)

### 2.1. Cache Key Taxonomy & Lifecycle (Phase 0)
- `CacheKeyCategory` (Enum): Định nghĩa 11 phân loại key.
  ```kotlin
  enum class CacheKeyCategory {
      DATA_CACHE, SESSION_BOUND, RATE_COUNTER, RATE_LOCK, IDEMPOTENCY, PROCESS_TOKEN,
      DISTRIBUTED_LOCK, CRYPTO_SESSION, STATIC_CONFIG, COMPUTED_AGGREGATE, WARMUP_PRELOAD
  }
  ```
- `CacheKeyResolver` (Interface/Impl): Tính toán key name theo format `{service}:{category}:{cacheName}::{springKey}` và tính toán TTL Jitter.

### 2.2. Encryption Engine (Phase 1)
- `CacheEncryptionMode` (Enum): `NONE`, `FULL`, `PARTIAL`.
- `CacheEncrypt` (Annotation): Đánh dấu field cần encrypt trong mode `PARTIAL`.
- `CacheEncryptionKeyProvider` (Interface): Strategy cung cấp AES SecretKey.
  - `InlineCacheEncryptionKeyProvider`: Lấy key từ file YAML (Base64).
  - `KeyStoreCacheEncryptionKeyProvider`: Lấy key từ file .jks/.p12.
- `AesGcmCacheEncryptor`: Core logic encrypt/decrypt.

### 2.3. Compression Engine (Phase 2)
- `CacheCompressionMode` (Enum): `NONE`, `ZSTD`, `LZ4`.
- `CacheCompressor`: Thực hiện nén dữ liệu. Skip nén nếu size < threshold (1KB).

### 2.4. Smart Serialization (Phase 3)
- `SmartCacheSerializer`: Kế thừa Spring `RedisSerializer<Any>`.
  - Hỗ trợ Serialize/Deserialize dựa trên type.
  - Gọi tới `CacheCompressor` và `CacheEncryptor`.

### 2.5. Auto-Configuration (Phase 4)
- Cập nhật `CacheProperties` thêm các cấu hình mã hóa, nén, taxonomy.
- `CacheEncryptionAutoConfiguration`, `CacheCompressionAutoConfiguration`: Khởi tạo Bean dựa trên condition (`@ConditionalOnProperty`).

## 3. Auth-Service Migration (Phase 5)

- **Xóa bỏ các class:**
  - `AbstractTwoTierCache.kt`
  - `CaffeinePermissionCache.kt`
  - `MultiTierPermissionCache.kt`
  - `InMemoryPermissionCache.kt`
  - `PermissionCache.kt` (Interface Port)
- **Cập nhật Handlers:**
  - `GetPermissionsHandler`, `GetUserRolesHandler` thêm `@Cacheable(cacheNames=["permissions"/"roles"])`.
- **Cập nhật Kafka Consumer:**
  - `PermissionChangedConsumer` inject `CacheManager` để gọi `evict()`.

## 4. Dependencies

- `lz4-java` (lz4 compression)
- `zstd-jni` (zstd compression)
- `spring-boot-starter-cache`
- `spring-boot-starter-data-redis`
