# Brainstorm Notes: FR-016-domain-config

> Deep thinking notes cho Domain Configuration Management — phân tích hướng thiết kế, trade-offs, và quyết định kiến trúc.

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

1. DomainConfigEntity (Entity)
2. DomainConfigRepository (Repository)
3. DomainConfigService (Service)
4. DomainConfigCacheService (Service)
5. DomainConfigController (Controller)


## 5. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Redis downtime | Medium | Low | DB fallback for reads |
| Data inconsistency | High | Low | Transaction boundaries + cache invalidation |
| Performance at scale | Medium | Medium | Connection pooling, batch operations |
| Schema migration issues | Low | Low | Tested with Testcontainers |

## 6. Open Questions Resolved

| Question | Decision |
|----------|----------|
| Cache strategy? | Cache-Aside with TTL |
| Base entity? | SnowflakePersistentAuditableEntity |
| Error format? | RFC 7807 ProblemDetail |
| Test strategy? | Testcontainers + embedded Redis |
| Audit trail? | Event table with full state snapshots |

## 7. Implementation Notes

- Follow existing patterns in auth-service codebase
- Reuse RedisTemplate configuration from existing services
- Leverage existing SecurityConfig for filter registration
- Use Flyway V-prefix for migration versioning


## 8. Capacity Planning

| Component | Expected Load | Scaling Strategy |
|-----------|--------------|-----------------|
| API requests | 1000 req/s | Horizontal pod scaling |
| Redis cache | 10K keys | Cluster sharding |
| PostgreSQL | 1M rows/year | Partition by date |
| Audit logs | 5M rows/year | Archive after 1 year |
