# System Requirements Specification: Base Cache Starter Upgrade & Migration

> **Change ID**: cache-starter-migration-upgrade
> **Profile**: Command | N/A | EXTEND

## 1. Introduction

Nâng cấp module `base-cache-starter` (thuộc thư mục `components/base-core/starters/base-cache-starter`) để hỗ trợ 3 tính năng caching nâng cao:
1. **Encryption Engine**: Mã hóa toàn bộ (FULL) hoặc một phần (PARTIAL) bằng thuật toán AES-256-GCM.
2. **Compression Engine**: Nén dữ liệu với Zstd (và fallback LZ4) dành cho large payloads (threshold > 1KB).
3. **Smart Serialization**: Serialization format (JSON/Binary/Plain) tùy vào Type và cấu hình compression/encryption.

Song song đó, thực hiện migration `auth-service` để loại bỏ bộ mã cache custom (`AbstractTwoTierCache`, `MultiTierPermissionCache`) và thay thế bằng Spring annotation-driven caching (`@Cacheable`) kết hợp với `base-cache-starter` phiên bản mới. 

Thêm vào đó, từ kết quả Brainstorming, thiết lập hệ thống **Cache Key Taxonomy & Lifecycle Manager** hỗ trợ 11 nhóm key (Categories), cấu trúc key name chặt chẽ (`{service}:{category}:{cache}::{key}`), và cơ chế chống stampede thông qua **TTL Jitter**.

## 2. Functional Requirements

### 2.1. Feature: Cache Key Taxonomy & Lifecycle (Phase 0)
- **FR-023**: Hệ thống phải định nghĩa enum `CacheKeyCategory` chứa 11 mục đích cache cơ bản (DATA_CACHE, SESSION_BOUND, RATE_COUNTER, RATE_LOCK, IDEMPOTENCY, PROCESS_TOKEN, DISTRIBUTED_LOCK, CRYPTO_SESSION, STATIC_CONFIG, COMPUTED_AGGREGATE, WARMUP_PRELOAD).
- **FR-024**: Hệ thống phải implement `CacheKeyResolver` để định dạng format key chuẩn (`{service}:{category}:{cache}::{key}`).
- **FR-025**: Hệ thống phải hỗ trợ tính toán TTL Jitter anti-stampede (chỉ bật cho các categories được chỉ định) với công thức: `baseTtl * (1.0 - jitter/200 + random * jitter/100)`.
- **FR-026**: Hệ thống cho phép override cấu hình Taxonomy (chọn category cho mỗi cache name) qua `KeyTaxonomyProperties` (YAML override) bên trong `CacheProperties`.
- **FR-027**: Mọi định nghĩa cache phải link được với một Category để xác định vòng đời.
- **FR-028**: Hệ thống phải áp dụng key prefix convention mới này vào Redis.

### 2.2. Feature: Encryption Engine (Phase 1)
- **FR-003**: Chế độ `NONE` - ghi thẳng plaintext vào Redis L2.
- **FR-004**: Chế độ `FULL` - mã hóa toàn bộ dữ liệu (bằng AES-256-GCM) trước khi lưu.
- **FR-005**: Chế độ `PARTIAL` - chỉ mã hóa các field thuộc tính Object đánh dấu bằng `@CacheEncrypt`.
- **FR-006**: Cung cấp annotation `@CacheEncrypt` cho Developer cấu hình ở mức class property.
- **FR-007**: Hệ thống hỗ trợ đọc Encryption Key dạng chuỗi Base64 từ biến môi trường/YAML (`InlineCacheEncryptionKeyProvider`).
- **FR-008**: Hỗ trợ đọc Key từ file KeyStore (optional) thông qua `KeyStoreCacheEncryptionKeyProvider`.
- **FR-009**: Interface `CacheEncryptionKeyProvider` cho phép Developer tự định nghĩa cách cung cấp (KMS/Vault) trong tương lai.

### 2.3. Feature: Compression Engine (Phase 2)
- **FR-010**: Hệ thống phải nén cache entries bằng thuật toán `Zstd`.
- **FR-011**: Hỗ trợ fallback dùng `LZ4` cho các luồng đòi hỏi ultra-low latency.
- **FR-012**: Hệ thống có khả năng tự detect được (Auto-Detect) định dạng được nén (Magic Bytes) để Decompress.
- **FR-019**: `Compression Threshold Skip` - chỉ nén khi payload lớn hơn một mức dung lượng định trước (mặc định 1KB).

### 2.4. Feature: Smart Serialization (Phase 3)
- **FR-013**: Hệ thống có khả năng detect kiểu biến được đẩy vào cache (vd: String, List, Object) để chọn Serializer phù hợp, tránh deserialize sai format.
- **FR-015**: Chiều Ghi L2 (Write Pipeline): Object → Serialize → Compress → Encrypt → Redis L2.
- **FR-016**: Chiều Đọc L2 (Read Pipeline): Redis L2 → Decrypt → Decompress → Deserialize → Object.
- **FR-018**: Tính năng Graceful Degradation - nếu decrypt thất bại, trả về Null (cache miss) thay vì throw Exception phá sập Request.

### 2.5. Feature: Auto Configuration & Metrics (Phase 4)
- **FR-014**: Tất cả Encryption mode, Compression mode, Category cho từng cache name có thể cấu hình riêng lẻ.
- **FR-022**: Auto-config Conditionals - Các bean liên quan mã hóa/nén chỉ khởi tạo khi tính năng đó được bật (`enabled: true`), tránh bắt buộc khai báo dependency.
- **FR-020**: Expose các Micrometer metrics liên quan tới mã hóa: count, duration, fail rate.
- **FR-021**: Expose các metrics nén: compress ratio, duration.

### 2.6. Feature: Auth-Service Migration (Phase 5)
- **FR-001**: Auth-Service thay thế các class thao tác cache cũ thành các method dùng Spring annotation `@Cacheable(cacheNames="permissions")`.
- **FR-002**: Xóa bỏ các đoạn mã thừa thải trong quá khứ (`AbstractTwoTierCache.kt`, `CaffeinePermissionCache.kt`, `MultiTierPermissionCache.kt`, `PermissionCache.kt`).
- **FR-017**: Cập nhật Kafka Consumer `PermissionChangedConsumer` sử dụng Spring `CacheManager.getCache(...).evict(...)` để xóa cache.

## 3. Assumptions & Constraints
- Redis caching implementation sử dụng Redisson hoặc Lettuce client với Spring Data Redis.
- App server có trang bị cấu trúc Hardware AES-NI để đảm bảo tốc độ mã hóa.
- Quá trình roll-out sẽ làm mất/thất bại cache entry hiện tại (do đổi key prefix hoặc format format từ Plaintext sang Encrypted). Chấp nhận Cold-Start hoặc Cache Miss trong 30 phút sau khi deploy.
