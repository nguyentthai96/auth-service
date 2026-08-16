# Brainstorm Notes: FR-012-api-partner

> Deep thinking notes cho API Partner Management — phân tích hướng thiết kế, trade-offs, và quyết định kiến trúc.

## 1. Problem Decomposition

### 1.1 Core Challenges
- Track state changes with full audit trail
- Cache frequently accessed data in Redis
- Ensure consistency between Redis and PostgreSQL
- Permission-based access control per endpoint

### 1.2 Design Options

#### Option A: Simple CRUD
- **Pro**: Quick implementation
- **Con**: Missing audit trail, no caching
- **Verdict**: ❌ Rejected — insufficient for enterprise requirements

#### Option B: Event-Sourced with Redis Cache
- **Pro**: Full audit trail, cached reads, event replay
- **Con**: Complex implementation, eventual consistency
- **Verdict**: ✅ Selected — best fit for enterprise compliance

## Selected Direction

### Architecture: Clean Architecture with Redis Cache
- **Write path**: Controller → Service → Repository (synchronous) + Redis invalidation
- **Read path**: Controller → Redis Cache → Fallback to DB
- **Audit**: Event logging for all state changes

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Base entity | SnowflakePersistentAuditableEntity | Existing pattern |
| Caching | Redis Cache-Aside | Per-request performance |
| Audit | Event table | Compliance requirement |
| Error handling | RFC 7807 ProblemDetail | Existing standard |

## 3. Trade-offs

### 3.1 Consistency vs Performance
- Write-through for critical data
- Cache-aside for read-heavy data
- TTL-based expiration as safety net

### 3.2 Complexity vs Completeness
- Full CRUD + audit + cache = more code
- But enterprise compliance requires it
- Reuse existing patterns to minimize effort

## 4. Implementation Sequence

1. ApiPartnerEntity (Entity)
2. ApiKeyEntity (Entity)
3. ApiPartnerRepository (Repository)
4. ApiKeyRepository (Repository)
5. ApiPartnerService (Service)
6. ApiKeyService (Service)
7. ApiRateLimitService (Service)
8. ApiUsageTracker (Service)
9. ApiPartnerController (Controller)
10. ApiKeyAuthFilter (Filter)


## 8. Capacity Planning

| Component | Expected Load | Scaling Strategy |
|-----------|--------------|-----------------|
| API requests | 1000 req/s | Horizontal pod scaling |
| Redis cache | 10K keys | Cluster sharding |
| PostgreSQL | 1M rows/year | Partition by date |
| Audit logs | 5M rows/year | Archive after 1 year |


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
