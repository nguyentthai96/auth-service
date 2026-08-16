# Design: FR-009-account-lifecycle

> **Change**: FR-009-account-lifecycle | **Type**: NEWBUILD | **Flow**: Non-Financial

## 1. Component Architecture

### New Components
| Component | Type | Description |
|-----------|------|-------------|
| `AccountLifecycleEntity` | Entity | Account status tracking entity |
| `AccountLifecycleRepository` | Repository | JPA repository |
| `AccountLifecycleService` | Service | Core lifecycle logic |
| `DataExportService` | Service | GDPR data export |
| `AccountDeletionScheduler` | Scheduler | Scheduled deletion after grace period |
| `AccountLifecycleController` | Controller | REST APIs for lifecycle |

### Modified Components
- SecurityConfig → register new filters
- application.yml → add config block

## 2. API Design

### 2.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/account/deactivate` | Deactivate account |
| POST | `/api/v1/account/reactivate` | Reactivate within grace period |
| POST | `/api/v1/account/delete-request` | Request GDPR deletion |
| GET | `/api/v1/account/export` | Export personal data |
| GET | `/api/v1/admin/accounts/lifecycle` | Admin view lifecycle events |

## 3. Data Design

### 3.1 PostgreSQL
Table: `account_lifecycle_events`

### 3.2 Redis
account:deletion:{userId} (String, TTL=30d)

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
- `AccountLifecycleEntity` — Account status tracking entity
- `AccountLifecycleRepository` — JPA repository
- `AccountLifecycleService` — Core lifecycle logic
- `DataExportService` — GDPR data export
- `AccountDeletionScheduler` — Scheduled deletion after grace period
- `AccountLifecycleController` — REST APIs for lifecycle

### Modified Components
- SecurityConfig — register new filters/permissions
- application.yml — add configuration block

### Database Changes
- NEW TABLE: `account_lifecycle_events`


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
