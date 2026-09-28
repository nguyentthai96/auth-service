# Base Class Map — user-identity-dual-key

## Entity Base Classes

| Class | Type | File |
|-------|------|------|
| `SnowflakePersistentAuditableEntity` | Entity base (current) | `base-core/base-model/.../id/SnowflakePersistentAuditableEntity.kt` |
| `DualIdPersistentAuditableEntity` | Entity base (target) | [`base-core/base-model/.../id/DualIdEntities.kt:108`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/model/id/DualIdEntities.kt#L108) |
| `SnowflakeBaseEntity` | Entity base (simple) | `base-core/base-model/.../id/SnowflakeBaseEntity.kt` |
| `VersionedAuditableEntity` | Entity base + @Version | [`shared/persistence/VersionedAuditableEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt) |

## Inheritance Hierarchy

```
BaseEntity<Long>
  └── PersistentAuditableEntity<Long>
        ├── SnowflakePersistentAuditableEntity  ← UserEntity (CURRENT)
        │     └── VersionedAuditableEntity (adds @Version)
        └── DualIdPersistentAuditableEntity     ← UserEntity (TARGET)
              ├── id: Long? (Snowflake PK)
              └── uuid: UUID (UUIDv7, immutable)
```

## DualIdPersistentAuditableEntity Details

```kotlin
// File: base-core/base-model/.../id/DualIdEntities.kt:108-122
@MappedSuperclass
abstract class DualIdPersistentAuditableEntity : PersistentAuditableEntity<Long>() {
    @Id @SnowflakeId
    override val id: Long? = null

    @Column(nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
    val uuid: UUID = Uuid.generateV7().toJavaUuid()
}
```

## Key Observation
- `DualIdPersistentAuditableEntity` does NOT include `@Version` — UserEntity currently has `@Version var version: Int = 0` which must be preserved manually.
- PK type remains `Long` → compatible with `AbstractCrudService`, `JpaRepository<UserEntity, Long>`.
