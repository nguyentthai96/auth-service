# Impact Analysis: user-identity-dual-key

> **Type**: EXTEND | **Service**: auth-service | **Date**: 2026-09-28

---

## 1. Core Files

| File | Lines | Action | Risk |
|------|-------|--------|------|
| [`UserEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | 65 | [MODIFY] Change base class, remove `override var id` | 🔴 HIGH |
| [`User.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | 117 | [MODIFY] Add `publicId: UUID` field | 🟡 MEDIUM |
| [`UserEntityMapper.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | 56 | [MODIFY] Map `uuid` ↔ `publicId` | 🟡 MEDIUM |
| [`UserPersistenceAdapter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | 87 | [MODIFY] Add `findByPhone`, `findByUuid`, update `toDomain()` | 🟡 MEDIUM |
| [`Repositories.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | 114 | [MODIFY] Add 3 new query methods + `findByIdentifierAny` | 🟢 LOW |
| [`UserPort.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt) | 16 | [MODIFY] Add 3 new port methods | 🟢 LOW |
| `V24__add_uuid_column_to_users.sql` | NEW | [ADD] UUID column + backfill | 🟢 LOW |
| `V25__optimize_auth_indexes.sql` | NEW | [ADD] Covering + Partial indexes | 🟢 LOW |

## 2. Call Tree (UserEntity dependents)

```
UserEntity (d=0)
├── d=1 (Direct callers — 8 files)
│   ├── UserRepository.kt (findByUsername, findByEmail, JpaRepository<UserEntity, Long>)
│   ├── UserPersistenceAdapter.kt (save, findById, toDomain)
│   ├── UserEntityMapper.kt (toDomain, toEntity extension functions)
│   ├── SsoAdapter.kt (provisionSsoUser creates new UserEntity)
│   ├── AccountStatusConsumer.kt (findById → update status)
│   ├── SecurityPreCheckStep.kt (findByUsernameAndActive)
│   ├── SecurityProperties.kt (comment reference only — no code impact)
│   └── VersionedAuditableEntity.kt (comment reference only — no code impact)
│
├── d=2 (Indirect callers — via UserPort/UserRepository)
│   ├── CqrsAuthController.kt (login flow → calls UserPort methods)
│   ├── LoginPipeline steps (via UserPort)
│   └── AdminUserController.kt (user management)
│
└── d=3 (Transitive)
    └── JWT token generation (uses User domain → generates token with sub)
```

## 3. Blast Radius

| Depth | Count | Impact Level | Files |
|-------|-------|-------------|-------|
| d=1 | 8 symbols | 🔴 HIGH | UserRepository, UserPersistenceAdapter, UserEntityMapper, SsoAdapter, AccountStatusConsumer, SecurityPreCheckStep, SecurityProperties(comment), VersionedAuditableEntity(comment) |
| d=2 | 4 symbols | 🟡 MEDIUM | CqrsAuthController, LoginPipeline, AdminUserController, UserPort |
| d=3 | 2 symbols | 🟢 LOW | JWT generation, API response DTOs |

**Conclusion**: Blast radius **HIGH** at d=1 (8 symbols) nhưng chỉ 6 cần code change thực sự (2 là comment references).

## 4. Reuse Map

| FR | Reuse Decision | Source | Action |
|----|---------------|--------|--------|
| FR-001 (UUID column) | **NEW** | Flyway migration | Write V24 |
| FR-002 (Base class) | **REUSE** | `DualIdPersistentAuditableEntity` from base-core | Import + extend |
| FR-003 (publicId) | **NEW** | No existing field | Add to `User.kt` |
| FR-004/005 (Covering Index) | **NEW** | Flyway migration | Write V25 |
| FR-006 (Partial Index) | **NEW** | Flyway migration | Write V25 |
| FR-007 (Identity Resolver) | **NEW** | No existing pattern | New utility in adapter |
| FR-008 (Fallback query) | **NEW** | No existing JPQL query | Add to `UserRepository` |
| FR-009 (Username validation) | **EXTRACT** (80%+) | Existing validation in registration flow | Enhance existing |
| FR-010 (Phone E.164) | **NEW** | No existing normalizer | New utility class |
| FR-011 (Repository methods) | **NEW** | No existing methods | Add to `UserRepository` |
| FR-012 (JWT sub=uuid) | **MODIFY** | Existing JWT generation | Change `sub` claim |
| FR-013 (API response) | **MODIFY** | Existing DTOs | Change `id` → `uuid` field |
| FR-014 (Dual exposure) | **REUSE** | Existing Kafka events | Keep internal id, add public uuid |

## 5. Context Snapshot

```
auth-service/
├── Base class: SnowflakePersistentAuditableEntity → DualIdPersistentAuditableEntity
├── PK type: Long (unchanged)
├── UUID: UUIDv7 (new, from DualId)
├── @Version: Kept on UserEntity directly (not in DualId base)
├── Flyway: V23 (current) → V24, V25 (new)
├── JPA: Hibernate 6.x, Spring Data JPA
├── Architecture: Clean Architecture (Hexagonal ports/adapters)
└── Language: Kotlin + Spring Boot 4.x
```
