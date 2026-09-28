# Service Structure — user-identity-dual-key

## Package Structure (auth-service)

```
com.ntt.authservice/
├── auth/                          ← Authentication domain
│   ├── adapter/
│   │   ├── in/
│   │   │   └── web/
│   │   │       └── CqrsAuthController.kt        ← Uses findByEmail
│   │   └── out/
│   │       └── persistence/
│   │           ├── UserPersistenceAdapter.kt     ← [MODIFY] toDomain() mapper
│   │           └── mapper/
│   │               └── UserEntityMapper.kt       ← [MODIFY] toDomain()/toEntity()
│   ├── application/
│   │   ├── port/
│   │   │   └── out/
│   │   │       └── UserPort.kt                   ← [MODIFY] add findByPhone, findByUuid
│   │   ├── pipeline/
│   │   │   └── SecurityPreCheckStep.kt           ← Uses findByUsernameAndActive
│   │   └── SsoAdapter.kt                         ← Creates UserEntity for SSO
│   └── domain/
│       └── model/
│           ├── User.kt                           ← [MODIFY] add publicId: UUID
│           └── vo/
│               └── UserId.kt
│
├── rbac/                          ← RBAC domain (entity layer)
│   └── adapter/
│       └── out/
│           └── persistence/
│               ├── entity/
│               │   ├── UserEntity.kt             ← [MODIFY] change base class
│               │   ├── UserIdentityEntity.kt     ← No change (SSO)
│               │   ├── RbacEntities.kt           ← No change
│               │   └── PermissionEntities.kt     ← No change
│               └── repository/
│                   └── Repositories.kt           ← [MODIFY] add 3 methods
│
├── shared/
│   ├── persistence/
│   │   └── VersionedAuditableEntity.kt           ← Reference (version pattern)
│   └── exception/
│       ├── AuthExceptions.kt                     ← Reuse existing
│       └── AuthErrorCode.kt                      ← Reuse existing
│
└── resources/
    └── db/migration/
        ├── V23__seed_default_sysadmin.sql        ← Current latest
        ├── V24__add_uuid_column_to_users.sql     ← [ADD] new
        └── V25__optimize_auth_indexes.sql        ← [ADD] new
```

## Naming Conventions
- Entity: `<Name>Entity` (e.g., `UserEntity`)
- Repository: `<Name>Repository` (e.g., `UserRepository`)
- Port: `<Name>Port` (e.g., `UserPort`)
- Adapter: `<Name>PersistenceAdapter` (e.g., `UserPersistenceAdapter`)
- Domain: Plain name (e.g., `User`)
- Mapper: `<Name>EntityMapper` (e.g., `UserEntityMapper`)
