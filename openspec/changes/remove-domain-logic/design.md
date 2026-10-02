# Design: remove-domain-logic

> **Approach**: Full Removal + Global RBAC (brainstorm-confirmed)
> **Classification**: MAINTENANCE | Command
> **Strategy**: Bottom-up (DB → Entity → Service → Controller → Frontend)

---

## 1. Design Decisions

### DD-001: Removal Strategy — Bottom-Up
- **Decision**: Remove from DB layer upward: Schema → Entity → Repository → Port → Service → Handler → Controller → Frontend
- **Rationale**: Compiler sẽ catch tất cả missing references khi entity bị xóa → safe incremental removal
- **Trade-off**: Mỗi step sẽ break compile → cần fix liên tục. Nhưng đây là cách an toàn nhất

### DD-002: Table Rename vs Drop+Recreate
- **Decision**: RENAME `domain_resources` → `resources`, `domain_roles` → `roles` (giữ data), DROP column `domain_id`
- **Rationale**: Giữ existing data (roles, resources) → không cần re-seed
- **Trade-off**: Rename có thể gây issue với Hibernate schema validation → cần cập nhật `@Table(name=)` annotation

### DD-003: Password Policy — Single Global
- **Decision**: 1 row global policy, bỏ `domainId` column
- **Rationale**: YAGNI — chưa cần per-role/group policy. Giảm complexity là priority (brainstorm Q1 resolved)
- **Extend path**: Thêm `scope_type` + `scope_id` nếu sau này cần granular
- **Implementation**: `PasswordPolicyService.getPolicy()` → no params, `validatorCache` → single `@Volatile var`

### DD-004: JWT Token — Remove Domain Claims
- **Decision**: Bỏ hoàn toàn `domains`, `active_domain` claims. Chỉ giữ `roles`, `permissions`
- **Rationale**: Domain concept không tồn tại → claims không có ý nghĩa
- **Impact**: Old tokens invalid → cut-over deploy invalidates all

### DD-005: RBAC API Path Flattening
- **Decision**: `/admin/domains/{domainId}/roles` → `/admin/roles`. Tất cả RBAC entities trở thành global
- **Rationale**: Không còn domain scope → path không cần `{domainId}`
- **Impact**: Frontend admin cần cập nhật API calls

### DD-006: Event Schema — Remove domainId
- **Decision**: Remove `domainId`/`domainCode` từ tất cả domain events (TokenIssued, UserRegistered, UserLoggedIn)
- **Rationale**: Downstream consumers (notification, account) đã verify KHÔNG dùng domainId (brainstorm Q4)
- **Impact**: Event schema breaking change → nhưng OK vì cut-over

---

## 2. Entity Model (After)

```
┌──────────┐
│  User    │ (giữ nguyên)
└────┬─────┘
     │ user_groups (M:N)
┌────▼─────┐
│  Group   │ (bỏ domain_id)
└────┬─────┘
     │ group_roles (M:N)
┌────▼─────┐
│   Role   │ (renamed from domain_roles, bỏ domain_id)
└────┬─────┘
     │ role_permissions (M:N)
┌────▼──────────┐
│  Permission   │ (giữ nguyên)
└────┬──────────┘
     │
┌────▼─────┐
│ Resource  │ (renamed from domain_resources, bỏ domain_id)
└──────────┘

┌────────────────┐
│PasswordPolicy  │ (single global row, bỏ domain_id)
└────────────────┘

DELETED: DomainEntity, UserDomainEntity
```

## 3. Component Mapping

| Component | Before | After |
|-----------|--------|-------|
| `DomainEntity` | Entity with `code`, `name`, `description` | **DELETED** |
| `UserDomainEntity` | Maps User ↔ Domain | **DELETED** |
| `DomainResourceEntity` | Resource scoped by domain | `ResourceEntity` (global) |
| `DomainRoleEntity` | Role scoped by domain | `RoleEntity` (global) |
| `GroupEntity.domainId` | Group belongs to domain | `GroupEntity` (no domainId) |
| `PasswordPolicyEntity.domainId` | Policy per domain | `PasswordPolicyEntity` (global, single) |
| `DomainPort` | Port interface | **DELETED** |
| `DomainPersistenceAdapter` | JPA adapter | **DELETED** |
| `DomainLookupService` | Resolve primary domain | **DELETED** |
| `SwitchDomainHandler` | CQRS handler | **DELETED** |
| `TokenGenerator.generateAuthResponse()` | Accepts `domainCode` | No domain params |
| `RbacEngine.hasPermission()` | Accepts `domainId` | No domain params |
| `PasswordPolicyService.getPolicy()` | Accepts `domainId` | No params (global) |
| `PolicyEnforcementStep` | Looks up domain policy | Looks up global policy |

## 4. Migration SQL Design

```sql
-- Phase 1: Drop domain tables
DROP TABLE IF EXISTS user_domains CASCADE;
DROP TABLE IF EXISTS domains CASCADE;

-- Phase 2: Rename domain-scoped tables
ALTER TABLE domain_resources RENAME TO resources;
ALTER TABLE resources DROP COLUMN IF EXISTS domain_id;

ALTER TABLE domain_roles RENAME TO roles;
ALTER TABLE roles DROP COLUMN IF EXISTS domain_id;

-- Phase 3: Remove domain_id from other tables
ALTER TABLE groups DROP COLUMN IF EXISTS domain_id;
ALTER TABLE password_policies DROP COLUMN IF EXISTS domain_id;

-- Phase 4: Update unique constraints (if any referenced domain_id)
-- TBD: check existing constraints
```
