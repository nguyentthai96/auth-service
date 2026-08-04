# BRD-07: Non-Functional Requirements & Risk

## Non-Functional Requirements

### NFR-01: Performance
| Metric | Target | Measurement |
|:---|:---|:---|
| Permission check (cached) | < 10ms | P99 latency |
| Permission check (uncached) | < 50ms | P99 latency |
| Login response time | < 200ms | P95 latency |
| Batch permission check | < 100ms (10 checks) | P95 latency |
| JWT generation | < 30ms | P95 latency |

### NFR-02: Scalability
| Metric | Target |
|:---|:---|
| Concurrent users | 10,000+ |
| Domains supported | 100+ |
| Roles per domain | 50+ |
| Users per domain | 100,000+ |
| Policies per domain | 500+ |

### NFR-03: Security
- All passwords hashed (BCrypt cost=12)
- JWT signed with RS256 (asymmetric)
- HTTPS only (TLS 1.3)
- SQL injection prevention (parameterized queries)
- Rate limiting on auth endpoints
- OWASP Top 10 compliance
- No sensitive data in JWT (no PII beyond userId)

### NFR-04: Availability
| Metric | Target |
|:---|:---|
| Uptime | 99.9% (43min/month downtime) |
| RTO | < 15 minutes |
| RPO | < 5 minutes |
| Health check endpoint | /actuator/health |

### NFR-05: Maintainability
- Test coverage > 80%
- Clean Architecture compliance
- API documentation (OpenAPI 3.1)
- Database migration versioning (Flyway)
- Structured logging (JSON, SLF4J)

### NFR-06: Data Integrity
- All timestamps in UTC (ISO 8601)
- UUID v7 for primary keys (time-ordered)
- Soft-delete with audit trail
- Referential integrity enforced at DB level
- ACID transactions for permission changes

## Risk Matrix

| # | Risk | Probability | Impact | Severity | Mitigation |
|:--|:-----|:---:|:---:|:---:|:---|
| R-01 | JWT token size exceeds limit | Medium | High | 🟡 | Compact permission encoding, exclude rarely-used permissions |
| R-02 | Role explosion across domains | Low | Medium | 🟢 | Group-based assignment, template roles |
| R-03 | Policy engine performance degradation | Medium | High | 🔴 | Cache policies, limit condition depth, benchmark |
| R-04 | Permission inconsistency after role change | Medium | High | 🔴 | Force JWT refresh on role change, invalidation mechanism |
| R-05 | Database schema migration breaks production | Low | Critical | 🔴 | Backward-compatible migrations, blue-green deploy |
| R-06 | Circular group/role inheritance | Low | Medium | 🟡 | Validation on assignment, prevent cycles |
| R-07 | Unauthorized domain access | Low | Critical | 🔴 | Strict domain isolation, automated testing |
| R-08 | Stale JWT with revoked permissions | Medium | High | 🟡 | Short token TTL (30min), token blacklist for critical revocations |

## Risk Mitigation Plan

### R-03: Policy Engine Performance
1. Index JSONB columns for common queries
2. Cache evaluated policies per user-domain-resource key
3. Limit policy condition depth to 5 levels
4. Benchmark with 1000+ policies per domain
5. Consider async evaluation for non-critical paths

### R-04: Permission Consistency
1. JWT refresh endpoint forces re-evaluation
2. On role/permission change → publish event
3. Client-side: check token version header
4. Maximum token TTL: 30 minutes
