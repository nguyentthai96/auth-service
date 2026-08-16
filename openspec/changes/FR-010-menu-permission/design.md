# Design: FR-010-menu-permission

> **Change**: FR-010-menu-permission | **Type**: NEWBUILD | **Flow**: Non-Financial

## 1. Component Architecture

### New Components
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

### Modified Components
- SecurityConfig → register new filters
- application.yml → add config block

## 2. API Design

### 2.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/menus/tree` | Get menu tree for current user |
| POST | `/api/v1/admin/menus` | Create menu node |
| PUT | `/api/v1/admin/menus/{id}` | Update menu node |
| DELETE | `/api/v1/admin/menus/{id}` | Delete menu node |
| POST | `/api/v1/admin/menus/permissions` | Assign role permission |
| POST | `/api/v1/admin/menus/permissions/override` | User-level override |
| DELETE | `/api/v1/admin/menus/permissions/cache` | Invalidate cache |

## 3. Data Design

### 3.1 PostgreSQL
Table: `menus, menu_permissions, user_menu_overrides`

### 3.2 Redis
menu:perm:{userId} (Hash, TTL=3600s), menu:tree:{domainId} (String, TTL=3600s)

## 4. Integration Points

- Integrates with existing Spring Security filter chain
- Uses existing Redis infrastructure
- Follows existing JPA/Flyway patterns

## 5. Error Handling

| Error | HTTP Code | Response |
|-------|-----------|----------|
| Not Found | 404 | RESOURCE_NOT_FOUND |
| Validation | 400 | VALIDATION_ERROR |
| Permission Denied | 403 | INSUFFICIENT_PERMISSION |

## Changes

### New Components
- `MenuEntity` — Menu tree node entity
- `MenuPermissionEntity` — Permission mapping entity
- `MenuRepository` — JPA repository for menu tree
- `MenuPermissionRepository` — JPA repository for permissions
- `MenuPermissionService` — Permission resolution logic
- `MenuPermissionCacheService` — Redis cache management
- `MenuController` — Menu CRUD endpoints
- `MenuPermissionController` — Permission management endpoints
- `PermissionAuthorizationFilter` — Per-request permission check

### Modified Components
- SecurityConfig — register new filters/permissions
- application.yml — add configuration block

### Database Changes
- NEW TABLE: `menus, menu_permissions, user_menu_overrides`


## 6. Sequence Diagrams

### 6.1 Create Operation
```
Client → Controller → Service → Repository → DB
                    → CacheService → Redis (invalidate)
                    ← Response (201 Created)
```

### 6.2 Read Operation (Cached)
```
Client → Controller → CacheService → Redis (hit) → Response
                                    → Redis (miss) → Service → Repository → DB
                                                              → Redis (set)
                                                   ← Response
```

## 7. Configuration Schema

```yaml
feature:
  cache:
    ttl-seconds: 3600
    key-prefix: "feature"
  validation:
    max-name-length: 255
    max-description-length: 2000
  scheduler:
    cleanup-cron: "0 0 2 * * ?"
    batch-size: 100
```

## 8. Migration Strategy

### 8.1 Flyway Migration
- Version: V{next}__{feature_name}.sql
- Type: Incremental, non-destructive
- Rollback: DROP TABLE IF EXISTS

### 8.2 Data Migration
- No data migration needed (new tables only)
- Existing data unaffected

## 9. Monitoring & Observability

| Metric | Type | Alert Threshold |
|--------|------|-----------------|
| Cache hit ratio | Gauge | < 80% |
| Write latency | Histogram | p99 > 100ms |
| Error rate | Counter | > 1% |
| Active records | Gauge | Informational |

## 10. Dependencies Matrix

| Component | Depends On | Depended By |
|-----------|-----------|-------------|
| Controller | Service, DTOs | API Gateway |
| Service | Repository, Cache | Controller |
| Repository | Entity, DB | Service |
| CacheService | Redis | Service |
