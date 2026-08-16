# Design: FR-016-domain-config

> **Change**: FR-016-domain-config | **Type**: NEWBUILD | **Flow**: Non-Financial

## 1. Component Architecture

### New Components
| Component | Type | Description |
|-----------|------|-------------|
| `DomainConfigEntity` | Entity | Domain configuration entity |
| `DomainConfigRepository` | Repository | JPA repository |
| `DomainConfigService` | Service | Config resolution with inheritance |
| `DomainConfigCacheService` | Service | Redis cache for configs |
| `DomainConfigController` | Controller | Config management APIs |

### Modified Components
- SecurityConfig → register new filters
- application.yml → add config block

## 2. API Design

### 2.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/admin/domains` | List domains |
| POST | `/api/v1/admin/domains` | Create domain config |
| PUT | `/api/v1/admin/domains/{id}` | Update domain config |
| GET | `/api/v1/admin/domains/{id}/effective` | Get effective config (inherited) |
| POST | `/api/v1/admin/domains/{id}/reload` | Reload config from DB |

## 3. Data Design

### 3.1 PostgreSQL
Table: `domain_configs`

### 3.2 Redis
domain:config:{domainId} (Hash, TTL=3600s), domain:config:global (Hash, TTL=3600s)

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
- `DomainConfigEntity` — Domain configuration entity
- `DomainConfigRepository` — JPA repository
- `DomainConfigService` — Config resolution with inheritance
- `DomainConfigCacheService` — Redis cache for configs
- `DomainConfigController` — Config management APIs

### Modified Components
- SecurityConfig — register new filters/permissions
- application.yml — add configuration block

### Database Changes
- NEW TABLE: `domain_configs`


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
