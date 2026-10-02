<!-- self-contained: true -->
# Tasks: remove-domain-logic

> **Type**: MAINTENANCE | Command
> **Profile**: Full Removal + Global RBAC
> **Order**: Bottom-up (DB → Entity → Repository → Port → Service → Handler → Controller → Frontend)
> **FRs**: 14 (Idea: 11, Enriched: 3)

---

## Phase 1: Database Migration

- [ ] **Task 1: Tạo Flyway migration script**
  - File: `src/main/resources/db/migration/V{next}__remove_domain_logic.sql` | Action: [NEW]
  - FR: FR-012 — Database migration loại bỏ domain tables
  - Pattern: Flyway versioned migration (`V{timestamp}__description.sql`)
  - SQL steps:
    1. `DROP TABLE IF EXISTS user_domains CASCADE`
    2. `DROP TABLE IF EXISTS domains CASCADE`
    3. `ALTER TABLE domain_resources RENAME TO resources; DROP COLUMN domain_id`
    4. `ALTER TABLE domain_roles RENAME TO roles; DROP COLUMN domain_id`
    5. `ALTER TABLE groups DROP COLUMN domain_id`
    6. `ALTER TABLE password_policies DROP COLUMN domain_id`
    7. Update unique constraints nếu reference `domain_id`

---

## Phase 2: Entity Layer (RBAC entities)

- [ ] **Task 2: Xóa DomainEntity + UserDomainEntity**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt` | Action: [MODIFY]
  - FR: FR-001 — Xóa entity domain khỏi hệ thống
  - Pattern: Xóa `class DomainEntity` + `class UserDomainEntity` khỏi file
  - Dependencies: Task 1 (DB migration) phải chạy trước

- [ ] **Task 3: Rename DomainRoleEntity → RoleEntity**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt` | Action: [MODIFY]
  - FR: FR-002 — Rename entity domain-scoped thành global
  - Changes: Class name `DomainRoleEntity` → `RoleEntity`, `@Table(name = "domain_roles")` → `@Table(name = "roles")`, remove `domainId` field

- [ ] **Task 4: Rename DomainResourceEntity → ResourceEntity**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt` | Action: [MODIFY]
  - FR: FR-002 — Rename entity domain-scoped thành global
  - Changes: Class name `DomainResourceEntity` → `ResourceEntity`, `@Table(name = "domain_resources")` → `@Table(name = "resources")`, remove `domainId` field

- [ ] **Task 5: Remove domainId từ GroupEntity**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt` | Action: [MODIFY]
  - FR: FR-003 — Xóa domain scope khỏi GroupEntity
  - Changes: Remove `@Column(name = "domain_id") var domainId: Long`

- [ ] **Task 6: Chuyển PasswordPolicyEntity sang global**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PasswordPolicyEntity.kt` | Action: [MODIFY]
  - FR: FR-007 — Chuyển PasswordPolicy sang global
  - Changes: Remove `domainId` field + unique constraint. Single global row pattern.

---

## Phase 3: Repository Layer

- [ ] **Task 7: Xóa/sửa domain repositories**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` | Action: [MODIFY]
  - FR: FR-013 — Xóa domain repositories
  - Changes:
    1. DELETE `DomainRepository` interface
    2. DELETE `UserDomainRepository` interface
    3. RENAME `DomainRoleRepository` → `RoleRepository`, remove `findByDomainIdAndCodeAndActiveTrue()`
    4. RENAME `DomainResourceRepository` → `ResourceRepository`, remove `findByDomainIdAndCodeAndActiveTrue()`
    5. Sửa `PasswordPolicyRepository.findByDomainId()` → `findFirst()` hoặc `findGlobalPolicy()`
    6. Update `GroupRepository` nếu có method dùng `domainId`

---

## Phase 4: Port + Adapter Layer (DELETE files)

- [ ] **Task 8: Xóa DomainPort interface**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainPort.kt` | Action: [DELETE]
  - FR: FR-008 — Xóa DomainPort và adapter

- [ ] **Task 9: Xóa DomainPersistenceAdapter**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/DomainPersistenceAdapter.kt` | Action: [DELETE]
  - FR: FR-008 — Xóa DomainPort và adapter

- [ ] **Task 10: Xóa DomainLookupService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt` | Action: [DELETE]
  - FR: FR-008 — Xóa DomainPort và adapter

- [ ] **Task 11: Xóa DomainLookupServiceTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/DomainLookupServiceTest.kt` | Action: [DELETE]
  - FR: FR-008 — Xóa DomainPort và adapter

---

## Phase 5: Application Layer (Core logic refactor)

- [ ] **Task 12: Xóa SwitchDomainHandler + Command**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` | Action: [DELETE]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainCommand.kt` | Action: [DELETE]
  - FR: FR-004 — Xóa SwitchDomain handler và command

- [ ] **Task 13: Refactor TokenGenerator — bỏ domain logic**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt` | Action: [MODIFY]
  - FR: FR-005 — Loại bỏ domain khỏi token generation
  - Base: Service injected into `CqrsAuthController`, `BuildAuthResponseHandler`
  - Changes:
    1. Remove constructor params: `domainPort`, `domainLookupService`
    2. `generateAuthResponse()` — bỏ param `domainCode`
    3. Remove domain lookup logic in method body
    4. Remove domain claims injection vào JWT

- [ ] **Task 14: Refactor RegisterHandler — bỏ domain validation**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` | Action: [MODIFY]
  - FR: FR-005 — Loại bỏ domain khỏi auth flow
  - Changes:
    1. Remove `domainPort` dependency
    2. Remove domain validation logic
    3. Remove domain membership creation (`UserDomainEntity`)
    4. Remove `domainId`/`domainCode` from event publishing

- [ ] **Task 15: Refactor RbacEngine — bỏ domainId**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt` | Action: [MODIFY]
  - FR: FR-006 — Chuyển RBAC sang global scope
  - Changes:
    1. `hasPermission(userId, permCode)` — bỏ `domainId` param
    2. `getUserRoles(userId)` — bỏ `domainId` param, query không filter domain
    3. `getEffectivePermissions(userId)` — bỏ `domainId` param
  - Dependencies: Task 3-4 (entity renames) phải xong trước

- [ ] **Task 16: Refactor PasswordPolicyService — global policy**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt` | Action: [MODIFY]
  - FR: FR-007 — Chuyển PasswordPolicy sang global
  - Changes:
    1. `validatePasswordStrength(password)` — bỏ `domainId` param
    2. `changePassword(userId, oldPw, newPw)` — bỏ `domainId` param
    3. `getPolicy()` — bỏ `domainId` param, return global policy
    4. `updatePolicy(policy)` — bỏ `domainId` param
    5. `isPasswordExpired(userId)` — bỏ `domainId` param
    6. `validatorCache` → `@Volatile var cachedValidator: PasswordValidator?`

- [ ] **Task 17: Refactor PolicyEnforcementStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/PolicyEnforcementStep.kt` | Action: [MODIFY]
  - FR: FR-007 — Password policy global
  - Changes: Remove `domainPort` dependency, call `passwordPolicyService.getPolicy()` without domainId

- [ ] **Task 18: Refactor BuildAuthResponseHandler**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt` | Action: [MODIFY]
  - FR: FR-005 — Token generation without domain
  - Changes: Update call to `tokenGenerator.generateAuthResponse()` — remove domainCode param

---

## Phase 6: Domain Events

- [ ] **Task 19: Remove domainId từ events**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/*.kt` | Action: [MODIFY]
  - FR: FR-009 — Bỏ domain khỏi events
  - Changes: Remove `domainId`/`domainCode` fields from:
    1. `TokenIssuedEvent`
    2. `UserRegisteredEvent`
    3. `UserLoggedInEvent`

- [ ] **Task 20: Update event publishers**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt` | Action: [MODIFY]
  - FR: FR-009 — Bỏ domain khỏi events
  - Changes: Remove `domainId` field from event payload construction

- [ ] **Task 21: Update PermissionChangedConsumer cache key**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt` | Action: [MODIFY]
  - FR: FR-006 — RBAC global
  - Changes: Remove `domainId` from cache key pattern

---

## Phase 7: Controller + DTO Layer

- [ ] **Task 22: Xóa DomainController + flatten RBAC APIs**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` | Action: [MODIFY]
  - FR: FR-010 — Xóa DomainController khỏi RBAC
  - Changes:
    1. DELETE entire `DomainController` class
    2. Other controllers: remove `{domainId}` path variables
    3. Update DI: remove `DomainRepository` from constructors

- [ ] **Task 23: Update RolePermissionController**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt` | Action: [MODIFY]
  - FR: FR-010 — Flatten APIs
  - Changes: Remove domain references from constructor + methods

- [ ] **Task 24: Update PolicyController — bỏ domainId path**
  - File: `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt` | Action: [MODIFY]
  - FR: FR-014 — PBAC policies global
  - Changes: Bỏ `{domainId}` path variable, policies trở thành global

- [ ] **Task 25: Update CqrsAuthController — bỏ switch-domain endpoint**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-004 + FR-005
  - Changes: Remove switch-domain endpoint, update tokenGenerator calls

- [ ] **Task 26: Update InternalApiController — bỏ domainId param**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt` | Action: [MODIFY]
  - FR: FR-006
  - Changes: `getUserRoles(userId)` — bỏ `domainId` param

- [ ] **Task 27: Xóa SwitchDomainRequestDto + update DTOs**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` | Action: [MODIFY]
  - FR: FR-004 + FR-005
  - Changes: DELETE `SwitchDomainRequestDto`

- [ ] **Task 28: Update ResponseDtos — bỏ domainId**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/ResponseDtos.kt` | Action: [MODIFY]
  - FR: FR-005
  - Changes: Remove `domainId` field from response DTOs

- [ ] **Task 29: Update AuthToken model**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/model/AuthToken.kt` | Action: [MODIFY]
  - FR: FR-005
  - Changes: Remove `activeDomain` field

- [ ] **Task 30: Update JwtService — bỏ domain claims**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Action: [MODIFY]
  - FR: FR-005
  - Changes: Remove `domains`, `active_domain` claims from token generation + parsing

- [ ] **Task 31: Update AuthService + AccountLifecycleService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt` | Action: [MODIFY]
  - FR: FR-006
  - Changes: Remove `domainId` from RbacEngine calls

- [ ] **Task 32: Update LoginHandler events**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - FR: FR-009
  - Changes: Remove `domainCode` from event publishing

---

## Phase 8: Frontend (admindashboard)

- [ ] **Task 33: Update auth types**
  - File: `src/types/auth.types.ts` | Action: [MODIFY]
  - FR: FR-011 — Frontend bỏ domain references
  - Changes: Remove `SwitchDomainRequest`, `domainCode` fields from login/register types

- [ ] **Task 34: Update authService**
  - File: `src/services/authService.ts` | Action: [MODIFY]
  - FR: FR-011
  - Changes: Remove `domainCode` from login, remove `authSwitchDomain()`, remove `activeDomain` from response handling

- [ ] **Task 35: Update account types**
  - File: `src/types/account.types.ts` | Action: [MODIFY]
  - FR: FR-011
  - Changes: Remove `activeDomain` field

- [ ] **Task 36: Update admin-user types/service**
  - File: `src/types/admin-user.types.ts` | Action: [MODIFY]
  - File: `src/services/admin-user.service.ts` | Action: [MODIFY]
  - FR: FR-011
  - Changes: Remove `domainCode` from types + mock data

- [ ] **Task 37: Update ProfilePage**
  - File: `src/pages/ProfilePage.tsx` | Action: [MODIFY]
  - FR: FR-011
  - Changes: Remove `activeDomain` display

- [ ] **Task 38: Update mock services**
  - Files: `mfa.service.ts`, `sso.service.ts`, `account.service.ts`, `authApi.ts` | Action: [MODIFY]
  - FR: FR-011
  - Changes: Remove `activeDomain` hardcoded values from mock data

---

## Phase 9: Tests

- [ ] **Task 39: Update/remove domain-related tests**
  - Files: `src/test/kotlin/...` | Action: [MODIFY]
  - FR: All FRs
  - Changes:
    1. DELETE test files for deleted classes (SwitchDomainHandler, DomainLookupService)
    2. UPDATE test files that reference `domainId` params
    3. Verify all tests pass: `./gradlew test`

- [ ] **Task 40: Verify full build**
  - Action: Run `./gradlew clean build` + frontend `npm run build`
  - FR: All FRs
  - Acceptance: Both pass with 0 errors
