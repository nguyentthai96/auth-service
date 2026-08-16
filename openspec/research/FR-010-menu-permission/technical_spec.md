# Technical Specification: FR-010-menu-permission

> Detailed technical specification for Menu Permission Management implementation.

## Keywords

Menu Permission Management, Technical Specification, API Design, Database Design, Redis, Spring Boot, Kotlin

## 1. Overview

### 1.1 Feature Description
Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department. User override ưu tiên cao hơn role permission, dùng Redis cache.

### 1.2 Architecture Decision
Implement within auth-service following Clean Architecture:
- **Adapter In (Web)**: Controllers, DTOs, Filters
- **Application**: Services, Schedulers
- **Domain**: Entities, Enums, Value Objects
- **Adapter Out (Persistence)**: Repositories, Redis Stores

## 2. Component Design

### 2.1 New Components
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

### 2.2 Modified Components
| Component | Change |
|-----------|--------|
| SecurityConfig | Register new filters |
| application.yml | Add configuration block |

## 3. Database Design

### 3.1 Tables
Primary table: `menus, menu_permissions, user_menu_overrides`

### 3.2 Indexes
- Composite indexes on frequently queried columns
- Partial indexes for active records

## 4. Redis Design

### 4.1 Key Patterns
menu:perm:{userId} (Hash, TTL=3600s), menu:tree:{domainId} (String, TTL=3600s)

### 4.2 Cache Strategy
- Cache-Aside pattern with configurable TTL
- Cache invalidation on write operations
- Graceful degradation when Redis unavailable

## 5. API Design

### 5.1 Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/menus/tree` | Get menu tree for current user |
| POST | `/api/v1/admin/menus` | Create menu node |
| PUT | `/api/v1/admin/menus/{id}` | Update menu node |
| DELETE | `/api/v1/admin/menus/{id}` | Delete menu node |
| POST | `/api/v1/admin/menus/permissions` | Assign role permission |
| POST | `/api/v1/admin/menus/permissions/override` | User-level override |
| DELETE | `/api/v1/admin/menus/permissions/cache` | Invalidate cache |

### 5.2 Error Handling
| Error | HTTP Code | Error Code |
|-------|-----------|------------|
| Not Found | 404 | RESOURCE_NOT_FOUND |
| Validation Error | 400 | VALIDATION_ERROR |
| Unauthorized | 401 | UNAUTHORIZED |
| Forbidden | 403 | INSUFFICIENT_PERMISSION |
| Conflict | 409 | RESOURCE_CONFLICT |

## 6. Security Considerations

- All admin endpoints require specific permissions
- Rate limiting on public endpoints
- Audit logging for all write operations
- Input validation via Bean Validation annotations

## 7. Performance Requirements

| Operation | Target |
|-----------|--------|
| Cached read | < 5ms |
| Write operation | < 50ms |
| Batch operation | < 500ms |

## 8. Testing Strategy

- Unit tests for service layer (80%+ coverage)
- Integration tests for controller layer
- Redis integration tests with embedded Redis
- Database tests with Testcontainers
