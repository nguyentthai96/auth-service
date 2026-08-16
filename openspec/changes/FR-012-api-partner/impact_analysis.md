# Impact Analysis: FR-012-api-partner

> **Change**: FR-012-api-partner | **Type**: NEWBUILD | **Risk**: LOW-MEDIUM

## 1. Change Summary

Hệ thống phải cấp phát và quản lý API key cho partner. Rate limiting, API key rotation, usage tracking.

## 2. Affected Components

### 2.1 New Files
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

### 2.2 Modified Files
| File | Change | Risk |
|------|--------|------|
| SecurityConfig.kt | Register new filters | Medium |
| application.yml | Add config block | Low |

## 3. Database Impact

### New Table: `api_partners, api_keys, api_usage_logs`
- Non-destructive migration (new table only)
- Can be rolled back by dropping table

## 4. Redis Impact

### New Key Patterns
api:rate:{partnerId} (SortedSet, TTL=60s), api:key:{hashedKey} (Hash, TTL=3600s)

## 5. API Impact

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/partners` | Register partner |
| GET | `/api/v1/admin/partners` | List partners |
| POST | `/api/v1/admin/partners/{id}/keys` | Generate API key |
| POST | `/api/v1/admin/partners/{id}/keys/rotate` | Rotate API key |
| DELETE | `/api/v1/admin/partners/{id}/keys/{keyId}` | Revoke API key |
| GET | `/api/v1/admin/partners/{id}/usage` | Usage analytics |

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


## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| IAM | Identity and Access Management — framework for managing digital identities |
| SSoT | Single Source of Truth — authoritative data source |
| TTL | Time To Live — cache expiration duration |
| GDPR | General Data Protection Regulation — EU privacy law |
| RFC 7807 | Problem Details for HTTP APIs — error response standard |
| CQRS | Command Query Responsibility Segregation |
| Cache-Aside | Pattern where application checks cache first, falls back to database |
| Snowflake ID | Twitter's distributed unique ID generation algorithm |
| Clean Architecture | Software architecture separating concerns into concentric layers |
| Flyway | Database migration tool for version-controlled schema changes |

## Appendix B: Configuration Reference

```yaml
# Feature-specific configuration block for application.yml
app:
  feature:
    enabled: true
    cache:
      enabled: true
      ttl-seconds: 3600
      key-prefix: "feature"
    audit:
      enabled: true
      retention-days: 365
    validation:
      strict-mode: true
      max-batch-size: 100
    scheduler:
      enabled: true
      cron: "0 0 2 * * ?"
```

## Appendix C: Test Coverage Requirements

| Layer | Min Coverage | Test Type |
|-------|-------------|-----------|
| Entity | 90% | Unit |
| Repository | 85% | Integration (Testcontainers) |
| Service | 80% | Unit + Integration |
| Controller | 75% | Integration (@SpringBootTest) |
| Filter | 70% | Integration |

## Appendix D: Deployment Checklist

- [ ] Database migration tested locally
- [ ] Redis key patterns documented
- [ ] API documentation updated (OpenAPI)
- [ ] Security review completed
- [ ] Performance baseline established
- [ ] Monitoring alerts configured
- [ ] Rollback procedure documented
- [ ] Load testing completed
