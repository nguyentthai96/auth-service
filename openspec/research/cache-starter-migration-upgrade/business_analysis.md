# Business Analysis: Base Cache Starter Migration & Upgrade

## 1. Business Context

Auth-service cần tối ưu cache data để:
- **Giảm code trùng lặp** (~300 LOC custom cache) bằng cách dùng base-cache-starter có sẵn
- **Bảo mật dữ liệu cache** — PII (email, phone, SSN) trong Redis cần được mã hóa (GDPR compliance)
- **Tối ưu hiệu năng** — serialization thông minh cho từng loại dữ liệu

## 2. Use Cases

### UC-001: Migrate Permission Cache sang @Cacheable

**Semantic**: Khi user login hoặc gọi API, hệ thống cần tra cứu permissions/roles. Hiện tại dùng custom cache code phức tạp (3 implementations, @Primary conflict). Cần đơn giản hóa bằng Spring @Cacheable + base-cache-starter.

**Basic Flow**:
1. User gọi API cần authorization
2. Handler gọi `PermissionQueryService.getPermissions(userId, domainId)`
3. `@Cacheable` tự động check L1 (Caffeine) → L2 (Redis) → DB
4. Return permissions list

**Exception Flow**:
- E1: Redis down → Graceful degradation: L1-only hoặc direct DB query
- E2: Cache stale → Kafka event trigger `@CacheEvict`

**Business Rules**:
- BR-001: Permission cache TTL L1=30s, L2=30min
- BR-002: Khi permission thay đổi, Kafka consumer evict cache cụ thể (not all)

---

### UC-002: Cache Encryption — Full Mode

**Semantic**: Khi lưu session data hoặc user profile vào Redis cache, toàn bộ value phải được mã hóa AES-256-GCM để đảm bảo data at rest security.

**Basic Flow**:
1. Application cần cache user session data
2. `SmartCacheSerializer` serialize object → JSON bytes
3. `AesGcmCacheEncryptor` encrypt JSON bytes → ciphertext
4. Store ciphertext vào Redis L2
5. L1 (Caffeine) giữ plaintext (in-process memory, không cần encrypt)

**Exception Flow**:
- E1: Encryption key missing → Throw `CacheEncryptionException`, log ERROR, skip cache (direct DB)
- E2: Decryption failure (key rotation) → Evict stale entry, re-load from DB

**Business Rules**:
- BR-003: L1 KHÔNG encrypt (performance, in-process memory)
- BR-004: Encryption key từ environment variable hoặc Vault

---

### UC-003: Cache Encryption — Partial Mode (Field-Level)

**Semantic**: Khi cache user profile, chỉ encrypt PII fields (email, phone) trong khi giữ non-sensitive fields (userId, roles) plaintext.

**Basic Flow**:
1. Application cache UserProfile object
2. Serializer scan `@CacheEncrypt` annotation trên fields
3. Encrypt chỉ marked fields: `email → "ENC:base64..."`, `phone → "ENC:base64..."`
4. Serialize JSON: `{"userId": 1, "email": "ENC:xxx", "phone": "ENC:yyy", "roles": ["ADMIN"]}`
5. Store vào Redis

**Exception Flow**:
- E1: Object không có `@CacheEncrypt` fields → Serialize plaintext (optimize skip scan)
- E2: Field value null → Skip encryption for null fields

**Business Rules**:
- BR-005: PARTIAL mode chỉ áp dụng cho POJO/data class (not primitives)
- BR-006: `@CacheEncrypt` annotation chỉ hỗ trợ String fields (encrypt String → Base64)

---

### UC-004: Smart Serialization — Data Type Optimization

**Semantic**: Tùy theo loại data, hệ thống tự động chọn serialization strategy tối ưu thay vì luôn dùng JSON.

**Basic Flow**:
1. Application cache data value
2. `SmartCacheSerializer` detect data type
3. Chọn strategy: String → UTF-8 bytes, Number → ASCII digits, Boolean → 1 byte, Object → JSON
4. Apply encryption mode (if configured)
5. Store vào Redis

**Business Rules**:
- BR-007: Default format là JSON — readable và debuggable
- BR-008: Per-cache có thể override format (STRING, JSON, BINARY)
- BR-009: Compression chỉ apply khi payload > threshold (default 1KB)

---

### UC-005: Cross-Instance Cache Invalidation

**Semantic**: Khi instance A evict cache entry, tất cả instances khác phải đồng bộ evict L1 cache cùng entry đó.

**Basic Flow**:
1. Instance A nhận Kafka event "permission changed for user 123"
2. Instance A gọi `cacheManager.getCache("permissions")?.evict("perm:123:1")`
3. `TwoLevelCache.evict()` → evict L1 + L2 + publish Redis Pub/Sub
4. Instances B, C nhận Pub/Sub message
5. `CacheInvalidationListener` → `cache.evictLocal("perm:123:1")` (L1 only)

**Business Rules**:
- BR-010: Pub/Sub channel per service: `cache:invalidation:auth`
- BR-011: Retry 2 lần nếu Redis publish fail

## 3. Traceability Matrix

| Use Case | Business Rule | Implementation |
|----------|--------------|----------------|
| UC-001 | BR-001, BR-002 | `@Cacheable` + `PermissionChangedConsumer` |
| UC-002 | BR-003, BR-004 | `AesGcmCacheEncryptor` + `SmartCacheSerializer` |
| UC-003 | BR-005, BR-006 | `@CacheEncrypt` + partial serialization |
| UC-004 | BR-007, BR-008, BR-009 | `SmartCacheSerializer` + `CacheProperties` |
| UC-005 | BR-010, BR-011 | `RedisCacheInvalidationPublisher` (existing) |
