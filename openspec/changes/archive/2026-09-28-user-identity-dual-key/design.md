# Design: User Identity Dual-Key Architecture

> **Change**: `user-identity-dual-key`
> **Profile**: Command | EXTEND
> **Service**: auth-service
> **Base class**: `DualIdPersistentAuditableEntity` (base-core)

---

## 1. Component Mapping

```
┌─────────────────────────────────────────────────────────────┐
│                     auth-service                             │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Adapter IN (Web)                                    │    │
│  │  └── CqrsAuthController ← login uses IdentityResolver│   │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │ calls                              │
│  ┌──────────────────────▼──────────────────────────────┐    │
│  │  Application Layer                                   │    │
│  │  ├── LoginPipeline (uses UserPort)                   │    │
│  │  ├── IdentityResolver [NEW]                          │    │
│  │  └── port/out/UserPort [MODIFY] (add 3 methods)      │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │ implements                         │
│  ┌──────────────────────▼──────────────────────────────┐    │
│  │  Adapter OUT (Persistence)                           │    │
│  │  ├── UserPersistenceAdapter [MODIFY] (new methods)    │    │
│  │  ├── mapper/UserEntityMapper [MODIFY] (uuid mapping)  │    │
│  │  ├── entity/UserEntity [MODIFY] (base class change)   │    │
│  │  └── repository/UserRepository [MODIFY] (3 new methods)│   │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │ JPA                                │
│  ┌──────────────────────▼──────────────────────────────┐    │
│  │  PostgreSQL 17                                       │    │
│  │  ├── V24: ALTER TABLE users ADD COLUMN uuid          │    │
│  │  ├── V25: Covering indexes + Partial phone index     │    │
│  │  └── users table (Dual-Key: id + uuid)               │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Domain Model                                        │    │
│  │  └── User [MODIFY] (add publicId: UUID)               │    │
│  └──────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## 2. Database Schema Changes

### V24: UUID Column

```sql
ALTER TABLE users ADD COLUMN uuid UUID;
UPDATE users SET uuid = gen_random_uuid() WHERE uuid IS NULL;
ALTER TABLE users ALTER COLUMN uuid SET NOT NULL;
CREATE UNIQUE INDEX idx_users_uuid ON users(uuid);
```

### V25: Optimized Auth Indexes

```sql
-- Covering index for email auth (Index-Only Scan)
CREATE INDEX idx_users_email_auth_covering
    ON users(email)
    INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled)
    WHERE active = TRUE;

-- Covering index for username auth
CREATE INDEX idx_users_username_auth_covering
    ON users(username)
    INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled)
    WHERE active = TRUE;

-- Partial unique index for phone
CREATE UNIQUE INDEX idx_users_phone_partial
    ON users(phone)
    WHERE phone IS NOT NULL AND active = TRUE;

-- UUID covering index for API lookups
CREATE INDEX idx_users_uuid_covering
    ON users(uuid)
    INCLUDE (id, username, email, status, active)
    WHERE active = TRUE;
```

## 3. Entity Design

### UserEntity — Before/After

```diff
 package com.ntt.authservice.rbac.adapter.out.persistence.entity
 
-import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
+import com.ntt.basecore.model.id.DualIdPersistentAuditableEntity
 import jakarta.persistence.*
 
 @Entity
 @Table(name = "users")
-class UserEntity : SnowflakePersistentAuditableEntity() {
+class UserEntity : DualIdPersistentAuditableEntity() {
+    // Inherited from DualId: id (Snowflake Long PK), uuid (UUIDv7)
 
-    override var id: Long? = null
+    // REMOVED: override var id — now inherited from DualIdPersistentAuditableEntity
 
     @Column(nullable = false, unique = true, length = 100)
     lateinit var username: String
     // ... (all other fields unchanged)
 
     @Version
     var version: Int = 0  // Kept here — DualId base does NOT have @Version
 }
```

### User Domain Model — Before/After

```diff
 class User(
     val id: UserId,
+    val publicId: java.util.UUID,  // UUIDv7 — public-facing identifier
     val username: String,
     val email: Email,
     // ... (all other fields unchanged)
 )
```

## 4. Identity Resolver Design

```kotlin
// New: IdentityResolver utility
// Location: auth/application/service/IdentityResolver.kt

class IdentityResolver(private val userPort: UserPort) {

    fun resolve(identifier: String): User? {
        val clean = identifier.trim()

        // FAST PATH (99.9%): App-level routing → Single Index Scan
        val fast = when {
            clean.contains('@') ->
                userPort.findByEmailAndActive(clean.lowercase())
            clean.startsWith('+') || (clean.length in 8..15 && clean.all { it.isDigit() }) ->
                userPort.findByPhoneAndActive(clean)
            else ->
                userPort.findByUsernameAndActive(clean.lowercase())
        }
        if (fast != null) return fast

        // SAFE FALLBACK (0.1%): OR query
        return userPort.findByIdentifierAny(clean)
    }
}
```

## 5. Mapper Changes

### UserEntityMapper.kt

```diff
 fun UserEntity.toDomain() = User(
     id = UserId(this.id ?: 0L),
+    publicId = this.uuid,
     username = this.username ?: "",
     // ... (all other fields unchanged)
 )
```

### UserPersistenceAdapter.kt

```diff
 fun UserEntity.toDomain(): User = User(
     id = UserId(this.id!!),
+    publicId = this.uuid,
     username = this.username,
     // ... (all other fields unchanged)
 )
```

## 6. Repository Additions

```kotlin
// UserRepository additions
interface UserRepository : JpaRepository<UserEntity, Long> {
    // Existing
    fun findByUsernameAndActiveTrue(username: String): UserEntity?
    fun findByEmailAndActiveTrue(email: String): UserEntity?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean

    // New
    fun findByPhoneAndActiveTrue(phone: String): UserEntity?
    fun findByUuidAndActiveTrue(uuid: java.util.UUID): UserEntity?

    @Query("""
        SELECT u FROM UserEntity u
        WHERE (u.username = :identifier OR u.email = :identifier OR u.phone = :identifier)
        AND u.active = true
    """)
    fun findByIdentifierAny(@Param("identifier") identifier: String): UserEntity?
}
```

## 7. Port Interface Additions

```kotlin
// UserPort additions
interface UserPort {
    // Existing
    fun findById(userId: Long): User?
    fun findByUsernameAndActive(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun save(user: User): User

    // New
    fun findByEmailAndActive(email: String): User?
    fun findByPhoneAndActive(phone: String): User?
    fun findByUuidAndActive(uuid: java.util.UUID): User?
    fun findByIdentifierAny(identifier: String): User?
}
```

## 8. Identifier Exposure Contract

```
┌──────────────────────────────────────────────┐
│ Boundary    │ Identifier │ Format            │
├─────────────┼────────────┼───────────────────┤
│ JWT sub     │ uuid       │ UUID string       │
│ API Response│ uuid       │ UUID string       │
│ Kafka events│ id         │ Long              │
│ Internal FK │ id         │ Long              │
│ DB PK       │ id         │ Long (Snowflake)  │
│ DB uuid col │ uuid       │ UUID (v7)         │
└──────────────────────────────────────────────┘
```
