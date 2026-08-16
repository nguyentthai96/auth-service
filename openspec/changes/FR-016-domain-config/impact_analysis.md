# Impact Analysis: FR-016-domain-config

> **Change**: FR-016-domain-config | **Type**: NEWBUILD | **Risk**: LOW-MEDIUM

## 1. Change Summary

Hệ thống phải cấu hình đa domain/tenant cho IAM. Domain isolation, config inheritance, runtime reload.

## 2. Affected Components

### 2.1 New Files
| Component | Type | Description |
|-----------|------|-------------|
| `DomainConfigEntity` | Entity | Domain configuration entity |
| `DomainConfigRepository` | Repository | JPA repository |
| `DomainConfigService` | Service | Config resolution with inheritance |
| `DomainConfigCacheService` | Service | Redis cache for configs |
| `DomainConfigController` | Controller | Config management APIs |

### 2.2 Modified Files
| File | Change | Risk |
|------|--------|------|
| SecurityConfig.kt | Register new filters | Medium |
| application.yml | Add config block | Low |

## 3. Database Impact

### New Table: `domain_configs`
- Non-destructive migration (new table only)
- Can be rolled back by dropping table

## 4. Redis Impact

### New Key Patterns
domain:config:{domainId} (Hash, TTL=3600s), domain:config:global (Hash, TTL=3600s)

## 5. API Impact

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/admin/domains` | List domains |
| POST | `/api/v1/admin/domains` | Create domain config |
| PUT | `/api/v1/admin/domains/{id}` | Update domain config |
| GET | `/api/v1/admin/domains/{id}/effective` | Get effective config (inherited) |
| POST | `/api/v1/admin/domains/{id}/reload` | Reload config from DB |

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
