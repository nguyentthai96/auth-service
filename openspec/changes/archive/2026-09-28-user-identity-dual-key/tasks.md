<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "single" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: User Identity Dual-Key Architecture

> **Change**: `user-identity-dual-key`
> **Profile**: Command | EXTEND
> **Total Tasks**: 8 (grouped by phase)

---

## Phase 1: Database Migration

- [x] **Task 1: Flyway V10 — Add uuid column to users table**
  - File: `src/main/resources/db/migration/V10__add_uuid_column_to_users.sql` | Action: [ADD]
  - FR: FR-001 — Thêm cột UUID vào bảng users
  - Pattern: Flyway naming `V{N}__{description}.sql`
  - Steps:
    1. `ALTER TABLE users ADD COLUMN uuid UUID`
    2. `UPDATE users SET uuid = gen_random_uuid() WHERE uuid IS NULL` (backfill)
    3. `ALTER TABLE users ALTER COLUMN uuid SET NOT NULL`
    4. `CREATE UNIQUE INDEX idx_users_uuid ON users(uuid)`

- [x] **Task 2: Flyway V11 — Create covering + partial indexes**
  - File: `src/main/resources/db/migration/V11__optimize_auth_indexes.sql` | Action: [ADD]
  - FR: FR-004 (email covering), FR-005 (username covering), FR-006 (phone partial)
  - Steps:
    1. `CREATE INDEX idx_users_email_auth_covering ON users(email) INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled) WHERE active = TRUE`
    2. `CREATE INDEX idx_users_username_auth_covering ON users(username) INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled) WHERE active = TRUE`
    3. `CREATE UNIQUE INDEX idx_users_phone_partial ON users(phone) WHERE phone IS NOT NULL AND active = TRUE`
    4. `CREATE INDEX idx_users_uuid_covering ON users(uuid) INCLUDE (id, username, email, status, active) WHERE active = TRUE`

---

## Phase 2: Entity + Domain Model

- [x] **Task 3: Change UserEntity base class to DualIdPersistentAuditableEntity**
  - File: [`src/main/kotlin/.../rbac/adapter/out/persistence/entity/UserEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | Action: [MODIFY]
  - Base: `DualIdPersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-002 — Đổi base class UserEntity
  - Steps:
    1. Change import: `SnowflakePersistentAuditableEntity` → `DualIdPersistentAuditableEntity`
    2. Change class declaration: `class UserEntity : DualIdPersistentAuditableEntity()`
    3. Remove `override var id: Long? = null` (inherited from DualId)
    4. Keep `@Version var version: Int = 0` (DualId does NOT have @Version)
    5. Verify compile: `id` and `uuid` fields now inherited

- [x] **Task 4: Add publicId to User domain model + update mappers**
  - File: [`src/main/kotlin/.../auth/domain/model/User.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | Action: [MODIFY]
  - File: [`src/main/kotlin/.../auth/adapter/out/persistence/mapper/UserEntityMapper.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | Action: [MODIFY]
  - File: [`src/main/kotlin/.../auth/adapter/out/persistence/UserPersistenceAdapter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | Action: [MODIFY]
  - FR: FR-003 — Thêm publicId vào domain model
  - Steps:
    1. `User.kt`: Add `val publicId: java.util.UUID` after `id` parameter
    2. `UserEntityMapper.kt`: Add `publicId = this.uuid` in `toDomain()` (line ~16)
    3. `UserPersistenceAdapter.kt`: Add `publicId = this.uuid` in companion `toDomain()` (line ~67)
    4. `UserPersistenceAdapter.kt`: Update `save()` — uuid is auto-generated, no manual assignment needed for new entities

---

## Phase 3: Repository + Identity Resolver

- [x] **Task 5: Add new query methods to UserRepository**
  - File: [`src/main/kotlin/.../rbac/adapter/out/persistence/repository/Repositories.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | Action: [MODIFY]
  - FR: FR-011 — Repository methods mới
  - Steps:
    1. Add `fun findByPhoneAndActiveTrue(phone: String): UserEntity?`
    2. Add `fun findByUuidAndActiveTrue(uuid: java.util.UUID): UserEntity?`
    3. Add `@Query` method `findByIdentifierAny(identifier: String): UserEntity?`
    4. Add import for `java.util.UUID`

- [x] **Task 6: Update UserPort + UserPersistenceAdapter with new port methods**
  - File: [`src/main/kotlin/.../auth/application/port/out/UserPort.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt) | Action: [MODIFY]
  - File: [`src/main/kotlin/.../auth/adapter/out/persistence/UserPersistenceAdapter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | Action: [MODIFY]
  - FR: FR-007 (Identity Resolver), FR-008 (Fallback), FR-011 (Repository methods)
  - Steps:
    1. `UserPort.kt`: Add `findByEmailAndActive(email: String): User?`
    2. `UserPort.kt`: Add `findByPhoneAndActive(phone: String): User?`
    3. `UserPort.kt`: Add `findByUuidAndActive(uuid: UUID): User?`
    4. `UserPort.kt`: Add `findByIdentifierAny(identifier: String): User?`
    5. `UserPersistenceAdapter.kt`: Implement all 4 new methods (delegate to repository + toDomain)

---

## Phase 4: JWT + API + Validation

- [x] **Task 7: Implement Identity Resolver + validation constraints**
  - File: `src/main/kotlin/.../auth/application/service/IdentityResolver.kt` | Action: [ADD]
  - FR: FR-007 (Identity Resolver), FR-008 (Fallback), FR-009 (Username validation), FR-010 (Phone E.164)
  - Dependencies: `UserPort` (from Task 6)
  - Steps:
    1. Create `IdentityResolver` class with `resolve(identifier: String): User?`
    2. Implement app-level routing: `@` → email, `+`/digits → phone, else → username
    3. Implement fallback: if fast path returns null → `findByIdentifierAny()`
    4. Add username validation pattern: `^[a-z][a-z0-9_]{2,29}$` (enforce at registration)
    5. Add phone E.164 normalization utility (if needed)

- [x] **Task 8: JWT sub claim migration + API response update**
  - FR: FR-012 (JWT sub=uuid), FR-013 (API response), FR-014 (Dual exposure)
  - Steps:
    1. Find JWT token generation code → change `sub` from `user.id` to `user.publicId.toString()`
    2. Find API response DTOs → change `userId` field from `Long` to `UUID`
    3. Verify Kafka events still use `userId: Long` (no change needed)
    4. Dev env: invalidate all existing sessions/tokens after deployment

- [x] **Task 8+: MfaService UUID migration (discovered during implementation)**
  - File: [`src/main/kotlin/.../auth/application/MfaService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Action: [MODIFY]
  - FR: FR-012 (JWT sub=uuid)
  - Lý do: MfaService có 3 nơi dùng `claims.subject.toLong()` — cần chuyển sang UUID resolution
  - Steps:
    1. `initiateMfa`: Look up user's uuid from repository, pass to `generateMfaToken(uuid, method)`
    2. `verifyMfa`: Parse UUID from token subject, resolve via `findByUuidAndActiveTrue`
    3. `resendOtp`: Same UUID resolution pattern
    4. `verifyRecoveryCodeMfa`: Same UUID resolution pattern
    5. Keep `userId: Long` for all internal operations (rate limit, OTP, recovery code)

---

## Execution Order

```
Task 1 (V24) → Task 2 (V25) → Task 3 (Entity) → Task 4 (Domain+Mapper)
    → Task 5 (Repository) → Task 6 (Port+Adapter) → Task 7 (Resolver) → Task 8 (JWT+API)
```

## FR Coverage Matrix

| FR | Task | Status |
|----|------|--------|
| FR-001 | Task 1 | ✅ |
| FR-002 | Task 3 | ✅ |
| FR-003 | Task 4 | ✅ |
| FR-004 | Task 2 | ✅ |
| FR-005 | Task 2 | ✅ |
| FR-006 | Task 2 | ✅ |
| FR-007 | Task 7 | ✅ |
| FR-008 | Task 7 | ✅ |
| FR-009 | Task 7 | ✅ |
| FR-010 | Task 7 | ✅ |
| FR-011 | Task 5 | ✅ |
| FR-012 | Task 8 | ✅ |
| FR-013 | Task 8 | ✅ |
| FR-014 | Task 8 | ✅ |
| **Total** | **14/14** | **100%** ✅ |
