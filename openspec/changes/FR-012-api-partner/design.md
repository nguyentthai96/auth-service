# Design: FR-012-api-partner

> **Change**: FR-012-api-partner | **Type**: NEWBUILD | **Flow**: Non-Financial

## 1. Component Architecture

### New Components
| Component | Type | Description |
|-----------|------|-------------|
| `ApiPartnerEntity` | Entity | Partner registration entity |
| `ApiKeyEntity` | Entity | API key tracking entity |
| `ApiPartnerRepository` | Repository | JPA repository |
| `ApiKeyRepository` | Repository | JPA repository for keys |
| `ApiPartnerService` | Service | Partner management logic |
| `ApiKeyService` | Service | Key generation/rotation logic |
| `ApiRateLimitService` | Service | Redis rate limiting |
| `ApiUsageTracker` | Service | Usage tracking |
| `ApiPartnerController` | Controller | Partner management APIs |
| `ApiKeyAuthFilter` | Filter | API key authentication filter |

### Modified Components
- SecurityConfig → register new filters
- application.yml → add config block

## 2. API Design

### 2.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/partners` | Register partner |
| GET | `/api/v1/admin/partners` | List partners |
| POST | `/api/v1/admin/partners/{id}/keys` | Generate API key |
| POST | `/api/v1/admin/partners/{id}/keys/rotate` | Rotate API key |
| DELETE | `/api/v1/admin/partners/{id}/keys/{keyId}` | Revoke API key |
| GET | `/api/v1/admin/partners/{id}/usage` | Usage analytics |

## 3. Data Design

### 3.1 PostgreSQL
Table: `api_partners, api_keys, api_usage_logs`

### 3.2 Redis
api:rate:{partnerId} (SortedSet, TTL=60s), api:key:{hashedKey} (Hash, TTL=3600s)

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
- `ApiPartnerEntity` — Partner registration entity
- `ApiKeyEntity` — API key tracking entity
- `ApiPartnerRepository` — JPA repository
- `ApiKeyRepository` — JPA repository for keys
- `ApiPartnerService` — Partner management logic
- `ApiKeyService` — Key generation/rotation logic
- `ApiRateLimitService` — Redis rate limiting
- `ApiUsageTracker` — Usage tracking
- `ApiPartnerController` — Partner management APIs
- `ApiKeyAuthFilter` — API key authentication filter

### Modified Components
- SecurityConfig — register new filters/permissions
- application.yml — add configuration block

### Database Changes
- NEW TABLE: `api_partners, api_keys, api_usage_logs`


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
