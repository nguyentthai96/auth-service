# Research Brief: Base Cache Starter Migration & Upgrade

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Base Cache Starter Migration & Upgrade |
| **Ngày tạo** | 2026-08-21 |
| **Input source** | name + implementation_plan.md |
| **Input content** | Migrate auth-service sang base-cache-starter; nâng cấp starter với encryption (full/partial/plaintext) + data type optimization |
| **Người yêu cầu** | nguyentthai96 |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
Auth-service tự implement `AbstractTwoTierCache` (~300 LOC) trong khi `base-cache-starter` từ base-core đã có sẵn `TwoLevelCacheManager` + `TwoLevelCache` với Redis Pub/Sub cross-instance invalidation, Micrometer metrics, per-cache config. Cần:
1. **Migrate** auth-service sang dùng `base-cache-starter`
2. **Nâng cấp** `base-cache-starter` thêm 2 tính năng mới:
   - Encryption support (full encrypt, partial field encrypt, plaintext)
   - Data type optimization (smart serialization cho từng loại data)

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Migrate auth-service — xóa custom cache code, dùng `@Cacheable` + `base-cache-starter`
- [x] Objective 2: Nâng cấp base-cache-starter — thêm cache encryption modes (NONE/FULL/PARTIAL)
- [x] Objective 3: Nâng cấp base-cache-starter — tối ưu serialization cho từng data type

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Migration auth-service sang base-cache-starter | Migration system-admin-service, account-service |
| Encryption: NONE, FULL, PARTIAL modes | Key rotation mechanism (deferred) |
| Annotation-based encryption (`@CacheEncrypt`) | Database-level encryption |
| Per-cache serialization strategy config | Custom serialization format (Protobuf/Kryo) |
| Smart serialization for String, Number, Bool, Object, List, Set, SortedSet, Map, Binary | Custom data structures |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `two-level cache Spring Boot`
- `cache encryption at rest`
- `field-level encryption cache`
- `Redis serialization optimization`
- `per-cache serializer strategy`

### 3.2 Secondary Keywords
- `AES-GCM cache encryption`
- `Caffeine Redis two-tier cache`
- `Spring Cache @Cacheable custom CacheManager`
- `Redis Hash vs String vs JSON performance`
- `cache data type serialization`

### 3.3 Domain-Specific Terms
- `TwoLevelCache`: L1 (Caffeine local) + L2 (Redis distributed) cache pattern
- `Cache Invalidation Broadcast`: Redis Pub/Sub để đồng bộ L1 eviction across instances
- `Field-Level Encryption`: Mã hóa chỉ những field nhạy cảm (PII/sensitive), giữ nguyên field khác plaintext
- `CacheValueType`: Enum định nghĩa kiểu dữ liệu cache (STRING, NUMBER, BOOL, OBJECT, LIST, SET, SORTED_SET, MAP, BINARY)

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| `AbstractTwoTierCache` | `auth-service/shared/cache/` | HIGH | Custom L1+L2 — sẽ bị thay thế |
| `MultiTierPermissionCache` | `auth-service/auth/adapter/out/cache/` | HIGH | Extends AbstractTwoTierCache — sẽ bị thay thế bằng @Cacheable |
| `CaffeinePermissionCache` | `auth-service/auth/adapter/out/cache/` | HIGH | @Primary conflict — sẽ bị xóa |
| `InMemoryPermissionCache` | `auth-service/auth/adapter/out/cache/` | HIGH | Dead code — sẽ bị xóa |
| `PermissionCache` port | `auth-service/auth/application/port/out/` | HIGH | Interface — sẽ bị đơn giản hóa |
| `base-cache-starter` | `base-core/starters/base-cache-starter/` | HIGH | Target starter — sẽ được nâng cấp |
| `TwoLevelCacheManager` | `base-cache-starter` | HIGH | Sẽ thêm encryption + serialization config |
| `RedisCipherKeySessionResolver` | `auth-service/auth/adapter/out/cipher/` | MEDIUM | Redis-only — candidate for @Cacheable upgrade |
| `DatabaseMessageSource` | `auth-service/shared/i18n/` | MEDIUM | L1-only — candidate for two-tier upgrade |
| `PasswordPolicyService` | `auth-service/auth/application/` | MEDIUM | ConcurrentHashMap — candidate for @Cacheable |

### 4.2 Existing Code Patterns
- **Architecture**: Clean Architecture (Hexagonal) — ports & adapters
- **Data access**: JPA Repository + Redis Template
- **API style**: REST + Handler-based CQRS
- **Error handling**: @ControllerAdvice + ProblemDetail (RFC 7807)
- **Cache**: Custom `AbstractTwoTierCache` (L1 Caffeine + L2 Redis), Spring Cache annotations NOT used
- **Build**: Gradle + Kotlin DSL + version-catalog from base-core

### 4.3 Tech Stack Constraints
- Language: Kotlin 2.3.21, JDK 25
- Framework: Spring Boot 4.1.0
- Database: PostgreSQL + Redis
- Build tool: Gradle 8.9 + Kotlin DSL
- Encryption: Google Tink (already in auth-service for E2EE)
- Key deps: `com.ntt:platform:0.0.1-SNAPSHOT` (BOM manages versions)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `base-cache-starter` | Library | `components/base-core/starters/base-cache-starter/` | Target upgrade |
| `platform` BOM | Build | `com.ntt:platform:0.0.1-SNAPSHOT` | Manages base-* versions |
| `version-catalog` | Build | `com.ntt:version-catalog:0.0.1-SNAPSHOT` | Shared versions TOML |
| `build-logic` | Build | `com.ntt.build:build-logic:0.0.1-SNAPSHOT` | Convention plugins |
| `PermissionCache` port | Interface | `auth/application/port/out/PermissionCache.kt` | 3 implementations → simplify |
| `PermissionChangedConsumer` | Kafka | `auth/adapter/in/kafka/PermissionChangedConsumer.kt` | Cache invalidation trigger |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: base-cache-starter hiện tại hỗ trợ encryption không? → KHÔNG
- [x] Q2: Có thể thêm per-cache encryption mode mà không breaking existing API? → CÓ (additive change)
- [x] Q3: Serialization tối ưu cho từng data type nên implement ở đâu? → Custom RedisSerializer per cache
- [x] Q4: Có open source nào đã implement cache encryption + two-level? → JetCache, J2Cache (không có encryption)
- [x] Q5: AES-GCM hay AES-CBC cho cache encryption? → AES-GCM (authenticated encryption)
- [x] Q6: Partial field encryption dùng AOP hay custom serializer? → Annotation + custom serializer

### 5.2 Assumptions cần verify
- [x] A1: `base-cache-starter` đã stable → CẦN VERIFY (0.0.1-SNAPSHOT)
- [x] A2: Google Tink có thể dùng cho cache encryption → CÓ (auth-service đã dùng)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ thông tin để implement | ≥ 5 sources |
| Open source options | Đánh giá alternatives | ≥ 3 repos evaluated |
| Gap analysis | Xác định feature gaps | All critical gaps identified |
| Business analysis | Use cases documented | All UCs documented |
| Technical spec | Agent-ready for implementation | Detailed spec with diagrams |
