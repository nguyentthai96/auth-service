# Proposal: FR-010-menu-permission

> **Change**: FR-010-menu-permission | **Type**: NEWBUILD | **Priority**: P1

## 1. Problem Statement

Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department. User override ưu tiên cao hơn role permission, dùng Redis cache. Hiện tại auth-service chưa có capability này.

## 2. Proposed Solution

### 2.1 Overview
Implement Menu Permission Management within auth-service following Clean Architecture patterns:
- Data layer: JPA entities + Flyway migrations
- Service layer: Business logic + Redis cache
- API layer: REST controllers + security filters

### 2.2 Key Components

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

## 3. Impact Analysis

### 3.1 New Components
9 new files (entities, repositories, services, controllers)

### 3.2 Modified Components
- SecurityConfig: Register new filters/permissions
- application.yml: Add configuration block

### 3.3 Breaking Changes
**None** — all changes are additive.

## 4. Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Redis downtime | Medium | DB fallback mode |
| Performance regression | Low | Cache warming, connection pooling |
| Migration failure | Low | Tested with Testcontainers |

## 5. Effort Estimation

| Phase | Estimate |
|-------|----------|
| Data Layer | 0.5 day |
| Service Layer | 1.5 days |
| API Layer | 1 day |
| Testing | 1 day |
| **Total** | **4 days** |

## 6. Recommendation

**APPROVE** — Straightforward implementation using existing patterns. No breaking changes.

## Changes

### New Files
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

### Modified Files
| File | Change |
|------|--------|
| SecurityConfig.kt | Register new filters/permissions |
| application.yml | Add configuration block |
