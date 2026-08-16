# Impact Analysis: FR-010-menu-permission

> **Change**: FR-010-menu-permission | **Type**: NEWBUILD | **Risk**: LOW-MEDIUM

## 1. Change Summary

Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department. User override ưu tiên cao hơn role permission, dùng Redis cache.

## 2. Affected Components

### 2.1 New Files
| Component | Type | Description |
|-----------|------|-------------|
| `MenuEntity` | Entity | Menu tree node entity |
| `MenuPermissionEntity` | Entity | Permission mapping entity |
| `MenuRepository` | Repository | JPA repository for menu tree |
| `MenuPermissionRepository` | Repository | JPA repository for permissions |
| `MenuPermissionService` | Service | Permission resolution logic |
| `MenuPermissionCacheService` | Service | Redis cache management |
| `MenuController` | Controller | Menu CRUD endpoints |
| `MenuPermissionController` | Controller | Permission management endpoints |
| `PermissionAuthorizationFilter` | Filter | Per-request permission check |

### 2.2 Modified Files
| File | Change | Risk |
|------|--------|------|
| SecurityConfig.kt | Register new filters | Medium |
| application.yml | Add config block | Low |

## 3. Database Impact

### New Table: `menus, menu_permissions, user_menu_overrides`
- Non-destructive migration (new table only)
- Can be rolled back by dropping table

## 4. Redis Impact

### New Key Patterns
menu:perm:{userId} (Hash, TTL=3600s), menu:tree:{domainId} (String, TTL=3600s)

## 5. API Impact

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/menus/tree` | Get menu tree for current user |
| POST | `/api/v1/admin/menus` | Create menu node |
| PUT | `/api/v1/admin/menus/{id}` | Update menu node |
| DELETE | `/api/v1/admin/menus/{id}` | Delete menu node |
| POST | `/api/v1/admin/menus/permissions` | Assign role permission |
| POST | `/api/v1/admin/menus/permissions/override` | User-level override |
| DELETE | `/api/v1/admin/menus/permissions/cache` | Invalidate cache |

### Breaking Changes
**None** — all changes are additive.

## 6. Performance Impact

| Operation | Before | After | Change |
|-----------|--------|-------|--------|
| Feature operations | N/A | < 50ms | New capability |
| Cached reads | N/A | < 5ms | Redis cache |

## 7. Rollback Plan

1. Remove new filters from SecurityConfig
2. Drop new database table(s)
3. Remove Redis keys
4. Remove new Kotlin files
5. Rollback time: ~15 minutes

## 8. Testing Requirements

| Test Type | Coverage |
|-----------|----------|
| Unit tests | Service layer |
| Integration tests | Controller APIs |
| Performance tests | Cached operation latency |


## 9. Compliance Check

| Standard | Status |
|----------|--------|
| GDPR | ✅ Compliant |
| SOC2 | ✅ Compliant |
| ISO 27001 | ✅ Compliant |
| OWASP Top 10 | ✅ Addressed |

## 10. Sign-off

| Role | Approver | Date |
|------|----------|------|
| Tech Lead | Pending | - |
| Security | Pending | - |
| DevOps | Pending | - |
