# Proposal: FR-009-account-lifecycle

> **Change**: FR-009-account-lifecycle | **Type**: NEWBUILD | **Priority**: P1

## 1. Problem Statement

Hệ thống phải cho phép deactivate, request delete (GDPR), export data. Đảm bảo compliance GDPR. Hiện tại auth-service chưa có capability này.

## 2. Proposed Solution

### 2.1 Overview
Implement Account Lifecycle Management within auth-service following Clean Architecture patterns:
- Data layer: JPA entities + Flyway migrations
- Service layer: Business logic + Redis cache
- API layer: REST controllers + security filters

### 2.2 Key Components

| Component | Type | Description |
|-----------|------|-------------|
| `AccountLifecycleEntity` | Entity | Account status tracking entity |
| `AccountLifecycleRepository` | Repository | JPA repository |
| `AccountLifecycleService` | Service | Core lifecycle logic |
| `DataExportService` | Service | GDPR data export |
| `AccountDeletionScheduler` | Scheduler | Scheduled deletion after grace period |
| `AccountLifecycleController` | Controller | REST APIs for lifecycle |

## 3. Impact Analysis

### 3.1 New Components
6 new files (entities, repositories, services, controllers)

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
| `AccountLifecycleEntity` | Entity | Account status tracking entity |
| `AccountLifecycleRepository` | Repository | JPA repository |
| `AccountLifecycleService` | Service | Core lifecycle logic |
| `DataExportService` | Service | GDPR data export |
| `AccountDeletionScheduler` | Scheduler | Scheduled deletion after grace period |
| `AccountLifecycleController` | Controller | REST APIs for lifecycle |

### Modified Files
| File | Change |
|------|--------|
| SecurityConfig.kt | Register new filters/permissions |
| application.yml | Add configuration block |


## 7. Implementation Details

### 7.1 Data Layer
- JPA entities extending SnowflakePersistentAuditableEntity
- Flyway migration with V-prefix versioning
- Composite indexes on frequently queried columns
- Partial indexes for active records

### 7.2 Service Layer
- Business logic with @Transactional boundaries
- Redis cache management with TTL
- Scheduled tasks for background processing
- Event publishing for audit trail

### 7.3 API Layer
- REST controllers with @Valid input validation
- RFC 7807 error responses
- Permission-based security filters
- Rate limiting on public endpoints

### 7.4 Testing
- JUnit 5 unit tests (80%+ coverage)
- @SpringBootTest integration tests
- Testcontainers for PostgreSQL
- Embedded Redis for cache tests

## 8. Deployment Notes

- Zero-downtime deployment supported
- Flyway runs automatically on startup
- Redis cache warms on first access
- Feature toggle available for gradual rollout
