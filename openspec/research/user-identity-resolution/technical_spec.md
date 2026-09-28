# Technical Specification: User Identity Resolution & Dual-Key Architecture

> **Feature**: User Identity Resolution for Million-Scale Lookup  
> **Version**: 1.0 | **Date**: 2026-09-28 | **Status**: Research Complete  
> **Recommendation**: BUILD — extend `DualIdPersistentAuditableEntity` + Unified Lookup Table

---

## 1. Tóm tắt bài toán

### Yêu cầu core
- `users` table hỗ trợ **query triệu dòng** không degradation
- User tìm được bởi **3 identifier**: `username`, `email`, `phone`
- Snowflake Long (`id`) **không bao giờ lộ ra API/client**
- API expose **UUID v7** (`uuid` từ `DualIdPersistentAuditableEntity`)
- Mọi identifier resolve về **một canonical UUID** duy nhất

### Tech stack constraints (từ codebase scan)
- Spring Boot 4.x + Kotlin 2.x + PostgreSQL 17 + JPA/Hibernate 6.5+ + Flyway
- `DualIdPersistentAuditableEntity` đã có trong base-core (Snowflake `id` + UUIDv7 `uuid`)
- `UserIdentityEntity` đã có pattern SSO linking — cần extend cho username/email/phone
- **Zero new dependency** cần thêm

---

## 2. Kiến trúc tổng thể

```
┌──────────────────────────────────────────────────────────────────┐
│                   IDENTITY RESOLUTION FLOW                        │
│                                                                   │
│  Client: username / email / phone / uuid                         │
│       │                                                           │
│       ▼  [Step 1 — O(log n), ~0.5ms]                            │
│  user_credential_lookups                                          │
│  (lookup_type, lookup_key) → user_uuid    ← Covering Index Only  │
│       │                                                           │
│       ▼  [Step 2 — O(log n), ~0.5ms]                            │
│  users                                                            │
│  uuid → full user data                    ← Covering Index Only  │
│                                                                   │
│  Total: ~1ms at 10M rows (hot cache)                             │
└──────────────────────────────────────────────────────────────────┘

Dual-Key design:
  id   (Snowflake Long) ← internal: FK, JOIN, audit     [NEVER in API]
  uuid (UUIDv7)         ← public:  API, JWT sub, URLs   [ALWAYS exposed]
```

---

## 3. Database Schema

### 3.1 `users` — Thêm `uuid` column

```sql
-- V24__add_uuid_to_users.sql
ALTER TABLE users ADD COLUMN uuid UUID;
UPDATE users SET uuid = gen_random_uuid() WHERE uuid IS NULL;
ALTER TABLE users ALTER COLUMN uuid SET NOT NULL;

-- Index: unique lookup + covering (Index Only Scan)
CREATE UNIQUE INDEX idx_users_uuid ON users(uuid);
CREATE INDEX idx_users_uuid_covering ON users(uuid)
    INCLUDE (id, username, status, active);

-- Remove default — JPA entity sinh UUIDv7 tự động
ALTER TABLE users ALTER COLUMN uuid DROP DEFAULT;
```

### 3.2 `user_credential_lookups` — Bảng lookup mới

```sql
-- V25__create_user_credential_lookups.sql
CREATE TABLE user_credential_lookups (
    id          BIGINT       PRIMARY KEY,                    -- Snowflake
    uuid        UUID         NOT NULL UNIQUE,                -- UUIDv7
    user_uuid   UUID         NOT NULL,                       -- → users.uuid
    lookup_type VARCHAR(20)  NOT NULL,                       -- USERNAME|EMAIL|PHONE|GOOGLE_SUB|...
    lookup_key  VARCHAR(320) NOT NULL,                       -- normalized (lowercase, E.164)
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_lookup_type_key UNIQUE (lookup_type, lookup_key)
);

-- Primary: type+key → user_uuid [most critical, Index Only Scan]
CREATE UNIQUE INDEX idx_ucl_type_key
    ON user_credential_lookups(lookup_type, lookup_key)
    WHERE active = TRUE;

-- Covering: eliminates heap fetch for login flow
CREATE INDEX idx_ucl_covering
    ON user_credential_lookups(lookup_type, lookup_key)
    INCLUDE (user_uuid, verified)
    WHERE active = TRUE;

-- Reverse: user_uuid → all identifiers
CREATE INDEX idx_ucl_user_uuid
    ON user_credential_lookups(user_uuid)
    WHERE active = TRUE;
```

**lookup_key normalization:**

| lookup_type | Format | Example |
|-------------|--------|---------|
| `USERNAME` | lowercase | `john_doe` |
| `EMAIL` | lowercase | `user@example.com` |
| `PHONE` | E.164 | `+84901234567` |
| `GOOGLE_SUB` | raw | `1234567890` |
| `APPLE_SUB` | raw | `001234.abc...` |

### 3.3 Index strategy cho 10M+ rows

```
users table indexes:
  ✅ idx_users_username (UNIQUE) — existing
  ✅ idx_users_email    (UNIQUE) — existing
  ✅ idx_users_phone    (partial, WHERE phone IS NOT NULL) — existing
  ✅ idx_users_status   (partial, WHERE active = TRUE) — existing
  🆕 idx_users_uuid         (UNIQUE) — new
  🆕 idx_users_uuid_covering (INCLUDE id, username, status, active) — new

user_credential_lookups:
  🆕 idx_ucl_type_key   (UNIQUE, partial active=TRUE) — covering index
  🆕 idx_ucl_user_uuid  (partial active=TRUE) — reverse lookup
```

> ⚠️ **Assumption**: Partitioning không cần thiết ở 10M rows. Re-evaluate khi > 50M rows với Hash Partition trên `id % N`.

---

## 4. Entity Design (Kotlin)

### 4.1 `UserEntity` — đổi base class

```kotlin
@Entity
@Table(name = "users")
class UserEntity : DualIdPersistentAuditableEntity() {
    // Inherited từ DualIdPersistentAuditableEntity:
    //   id: Long?  — Snowflake PK, KHÔNG expose ra API
    //   uuid: UUID — UUIDv7, immutable, expose ra API/JWT
    //   createdAt, updatedAt, createdBy, updatedBy (Instant)
    //   active: Boolean (soft-delete)

    @Column(nullable = false, unique = true, length = 100)
    lateinit var username: String

    @Column(nullable = false, unique = true, length = 255)
    lateinit var email: String

    @Column(name = "password_hash", nullable = false, length = 255)
    lateinit var passwordHash: String

    @Column(name = "full_name", nullable = false, length = 200)
    lateinit var fullName: String

    @Column(length = 20)
    var phone: String? = null

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"

    @Column(name = "failed_login_count", nullable = false)
    var failedLoginCount: Int = 0

    @Column(name = "locked_until_at")
    var lockedUntilAt: java.time.Instant? = null

    @Column(name = "mfa_enabled", nullable = false)
    var mfaEnabled: Boolean = false

    @Column(name = "mfa_method", length = 20)
    var mfaMethod: String = "NONE"

    @Column(name = "totp_secret_encrypted", length = 500)
    var totpSecretEncrypted: String? = null

    @Column(name = "trusted_device_hash", length = 255)
    var trustedDeviceHash: String? = null

    @Column(name = "trusted_device_set_at")
    var trustedDeviceSetAt: java.time.Instant? = null

    @Column(name = "password_changed_at")
    var passwordChangedAt: java.time.Instant? = null

    // @Version var version: Int = 0 → BỎ — duplicate với base class
    // Nếu cần optimistic lock: thêm lại @Version var version: Long = 0 ở đây
}
```

### 4.2 `UserCredentialLookupEntity` — entity mới

```kotlin
enum class CredentialLookupType {
    USERNAME, EMAIL, PHONE,
    GOOGLE_SUB, APPLE_SUB, FACEBOOK_ID, GITHUB_ID, ZALO_ID
}

@Entity
@Table(
    name = "user_credential_lookups",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_lookup_type_key",
        columnNames = ["lookup_type", "lookup_key"]
    )]
)
class UserCredentialLookupEntity : DualIdBaseEntity() {
    // Inherited: id (Snowflake), uuid (UUIDv7)

    @Column(name = "user_uuid", nullable = false, columnDefinition = "uuid")
    lateinit var userUuid: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_type", nullable = false, length = 20)
    lateinit var lookupType: CredentialLookupType

    @Column(name = "lookup_key", nullable = false, length = 320)
    lateinit var lookupKey: String

    @Column(nullable = false)
    var verified: Boolean = false

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: java.time.Instant = java.time.Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: java.time.Instant = java.time.Instant.now()
}
```

---

## 5. Repository

```kotlin
@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByUsernameAndActiveTrue(username: String): UserEntity?
    fun findByEmailAndActiveTrue(email: String): UserEntity?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    // NEW
    fun findByUuidAndActiveTrue(uuid: UUID): UserEntity?
    fun existsByUuid(uuid: UUID): Boolean
}

@Repository
interface UserCredentialLookupRepository : JpaRepository<UserCredentialLookupEntity, Long> {
    fun findByLookupTypeAndLookupKeyAndActiveTrue(
        lookupType: CredentialLookupType,
        lookupKey: String
    ): UserCredentialLookupEntity?

    fun findAllByUserUuidAndActiveTrue(userUuid: UUID): List<UserCredentialLookupEntity>

    fun existsByLookupTypeAndLookupKey(
        lookupType: CredentialLookupType,
        lookupKey: String
    ): Boolean

    @Modifying
    @Query("""
        UPDATE UserCredentialLookupEntity u
        SET u.active = false, u.updatedAt = :now
        WHERE u.userUuid = :userUuid AND u.lookupType = :type
    """)
    fun deactivateByUserUuidAndType(
        @Param("userUuid") userUuid: UUID,
        @Param("type") type: CredentialLookupType,
        @Param("now") now: java.time.Instant = java.time.Instant.now()
    ): Int
}
```

---

## 6. IdentityResolutionService

```kotlin
@Service
class IdentityResolutionService(
    private val userRepo: UserRepository,
    private val lookupRepo: UserCredentialLookupRepository
) {
    /**
     * Universal entry: any identifier → user.uuid
     * Auth service calls this first, then loads full User by UUID.
     */
    fun resolveUserUuid(identifier: String): UUID? {
        val (type, key) = detectAndNormalize(identifier)
        return lookupRepo.findByLookupTypeAndLookupKeyAndActiveTrue(type, key)?.userUuid
    }

    /** Register all identifiers on user creation */
    @Transactional
    fun registerLookups(userUuid: UUID, username: String, email: String, phone: String?) {
        val entries = mutableListOf(
            buildEntry(userUuid, CredentialLookupType.USERNAME, username.lowercase()),
            buildEntry(userUuid, CredentialLookupType.EMAIL, email.lowercase(), verified = true)
        )
        phone?.let { entries += buildEntry(userUuid, CredentialLookupType.PHONE, normalizePhone(it)) }
        lookupRepo.saveAll(entries)
    }

    /** Atomic update when email/phone changes */
    @Transactional
    fun updateLookup(userUuid: UUID, type: CredentialLookupType, newValue: String, verified: Boolean = false) {
        lookupRepo.deactivateByUserUuidAndType(userUuid, type)
        lookupRepo.save(buildEntry(userUuid, type, normalize(type, newValue), verified))
    }

    private fun detectAndNormalize(identifier: String): Pair<CredentialLookupType, String> = when {
        identifier.contains("@")   -> CredentialLookupType.EMAIL   to identifier.lowercase().trim()
        identifier.startsWith("+") -> CredentialLookupType.PHONE   to normalizePhone(identifier)
        else                       -> CredentialLookupType.USERNAME to identifier.lowercase().trim()
    }

    private fun normalizePhone(phone: String): String =
        phone.trim().let { if (it.startsWith("0")) "+84${it.drop(1)}" else it }

    private fun normalize(type: CredentialLookupType, value: String) = when (type) {
        CredentialLookupType.EMAIL    -> value.lowercase().trim()
        CredentialLookupType.USERNAME -> value.lowercase().trim()
        CredentialLookupType.PHONE    -> normalizePhone(value)
        else                          -> value.trim()
    }

    private fun buildEntry(userUuid: UUID, type: CredentialLookupType, key: String, verified: Boolean = false) =
        UserCredentialLookupEntity().apply {
            this.userUuid = userUuid
            this.lookupType = type
            this.lookupKey = key
            this.verified = verified
        }
}
```

---

## 7. Domain & API Integration

### Domain model `User.kt` — thêm `publicId`

```kotlin
class User(
    val id: UserId,       // Snowflake — internal only, KHÔNG expose
    val publicId: UUID,   // UUIDv7 — expose qua API, JWT sub
    val username: String,
    val email: Email,
    // ... rest unchanged
)
```

### JWT `sub` = publicId

```kotlin
Jwts.builder()
    .subject(user.publicId.toString())  // UUIDv7, không phải Snowflake Long
```

### Request/Response DTO

```kotlin
// Request: nhận UUID thay Long
data class AddUserToGroupRequest(val userPublicId: UUID)  // was: userId: Long

// Response: expose uuid, không expose id
data class UserResponse(
    val id: UUID,       // ← users.uuid (UUIDv7), KHÔNG phải users.id (Long)
    val username: String,
    val email: String
)
```

---

## 8. Performance Profile (10M rows)

| Query | Index Used | Type | Latency est. |
|-------|-----------|------|-------------|
| Login by email | `idx_ucl_covering` | Index Only Scan | ~0.3ms |
| Login by phone | `idx_ucl_covering` | Index Only Scan | ~0.3ms |
| Load user by UUID | `idx_users_uuid_covering` | Index Only Scan | ~0.3ms |
| Reverse lookup (all IDs) | `idx_ucl_user_uuid` | Index Scan | ~0.5ms |
| Registration dedup | `idx_ucl_type_key` | Index Scan | ~0.2ms |

**UUIDv7 vs UUIDv4 tại 10M rows:**
- Insert throughput: UUIDv7 **~20x faster** (sequential B-tree, no page splits)
- Index size: UUIDv7 **~24% smaller** (less fragmentation)
- Source: PostgreSQL benchmarks 2024, dev.to, medium.com

---

## 9. Migration Plan

| Version | File | Nội dung |
|---------|------|---------|
| V24 | `add_uuid_to_users.sql` | ADD COLUMN uuid + populate + indexes |
| V25 | `create_user_credential_lookups.sql` | New table + indexes |
| V26 | `migrate_users_to_lookup.sql` | Backfill existing users → lookup rows |
| V27 | `fix_version_column_type.sql` | ALTER version INT → BIGINT (align VersionedAuditableEntity) |

---

## 10. Trade-offs

| Quyết định | Lý do |
|-----------|-------|
| Lookup table thay vì chỉ index | Extensible: thêm Zalo/Apple ID không cần ALTER TABLE users |
| Dual Key (Long + UUID) | FK dùng Long (8B) hiệu quả hơn UUID (16B); API dùng UUID an toàn |
| UUIDv7 thay UUIDv4 | Sequential → B-tree compact, 20x faster insert |
| `user_uuid` (UUID FK) thay `user_id` (Long FK) trong lookup | Decouple lookup từ internal Snowflake; UUID không thay đổi khi shard |
| Sync registration thay async | Consistency tại điểm tạo user quan trọng hơn tốc độ |
| Không partition ở 10M rows | Overhead không justify; re-evaluate > 50M rows |

### ⚠️ Risks

1. **Data sync drift** giữa `users` và `user_credential_lookups` → Mitigation: `@Transactional` + integration test
2. **Phone normalization edge case** (số quốc tế khác VN) → Mitigation: unit test với nhiều format
3. **Migration V26 downtime** nếu users table lớn → Mitigation: batch INSERT với pg_sleep, run off-peak

---

## 11. Implementation Checklist (6 Phases)

### Phase 1 — DB Migration
- [ ] V24: `uuid UUID NOT NULL` + covering index
- [ ] V25: `user_credential_lookups` table + indexes

### Phase 2 — Entity
- [ ] `UserEntity`: base class → `DualIdPersistentAuditableEntity`
- [ ] `UserEntity`: bỏ `override var id`, bỏ `@Version var version: Int`
- [ ] `UserCredentialLookupEntity` + `CredentialLookupType` enum: tạo mới

### Phase 3 — Repository & Service
- [ ] `UserRepository`: thêm `findByUuidAndActiveTrue`
- [ ] `UserCredentialLookupRepository`: tạo mới
- [ ] `IdentityResolutionService`: tạo mới

### Phase 4 — Domain & Mapper
- [ ] `User.kt`: thêm `publicId: UUID`
- [ ] `UserEntityMapper.kt`: map `uuid ↔ publicId`
- [ ] `UserPersistenceAdapter.kt`: thêm `findByUuid()`

### Phase 5 — API
- [ ] Request DTOs: `userId: Long` → `userPublicId: UUID`
- [ ] Response DTOs: expose `uuid`, không expose `id`
- [ ] `JwtService`: `sub` = `user.publicId.toString()`

### Phase 6 — Data Migration
- [ ] V26: backfill `user_credential_lookups` từ existing users
- [ ] Verify: count match giữa 2 bảng

---

*Sources: Supabase blog (choosing-a-postgres-primary-key), PostgreSQL docs, Auth0/Keycloak canonical identity pattern, Hibernate 6.5 UuidGenerator docs, RFC 9562 (UUID v7), dev.to/medium.com UUIDv7 benchmarks 2024*
