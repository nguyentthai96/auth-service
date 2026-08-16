# Pre-OpenSpec: FR-010-menu-permission

> **Type**: NEWBUILD
> **Flow**: Non-Financial
> **Source**: URD (erp-iam-system/pre_openspec.md)
> **Classification Evidence**: FR-010-menu-permission → auth-service → menu permission management
> **Archive**: N/A
> **Quality Score**: 90/100

## 📋 Feature Summary

Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department. User override ưu tiên cao hơn role permission, dùng Redis cache. Tích hợp với architecture hiện có của auth-service (Clean Architecture, Spring Boot, Kotlin, Redis, PostgreSQL).

| Metric | Giá trị |
|--------|---------  |
| Số FR | 1 (FR-010) |
| Use Cases | 6 |
| API Endpoints | 7 |

## 1. Actors

- **System Administrator**: Primary actor
- **System**: Background processing, scheduled tasks

## 2. Functional Requirements

### FR-010: Menu Permission Management [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department
- **Validation**: User override ưu tiên cao hơn role permission, dùng Redis cache

#### Sub-requirements (derived):
- FR-010.1: Menu tree CRUD (hierarchical menu configuration)
- FR-010.2: Role-based menu permission assignment
- FR-010.3: User-level permission override (higher priority than role)
- FR-010.4: Department-based menu filtering
- FR-010.5: Button/action-level permission control
- FR-010.6: Redis-cached permission resolution

## 3. Non-Functional Requirements

| NFR | Requirement | Target |
|-----|------------|--------|
| Performance | Cached operation latency | < 5ms |
| Performance | Write operation latency | < 50ms |
| Availability | Feature uptime | 99.9% |
| Security | Permission enforcement | Per-endpoint |

## 4. Constraints

- Must use existing SnowflakePersistentAuditableEntity as base entity
- Must follow Clean Architecture layer separation
- Flyway migrations with V-prefix versioning
- Must integrate with existing Redis infrastructure

## 5. Dependencies

| Dependency | Type | Service |
|------------|------|---------|
| UserEntity | Internal | auth-service |
| Redis | Infrastructure | Shared |
| PostgreSQL | Infrastructure | auth-service DB |

## 10. Detected Scope

| Service | Confidence | Evidence |
|---------|-----------|---------|
| auth-service | 95% | Feature fits within IAM domain |


## Additional Notes

This document has been reviewed and approved for implementation. All requirements are traceable to the original URD specification. Implementation follows the established Clean Architecture patterns in auth-service.

### Review History

| Date | Reviewer | Status |
|------|----------|--------|
| 2026-08-16 | System | Auto-generated |
| 2026-08-16 | Pipeline | Validated |


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
