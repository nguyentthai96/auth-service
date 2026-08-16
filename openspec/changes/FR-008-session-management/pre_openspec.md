# Pre-OpenSpec: FR-008-session-management

> **Type**: NEWBUILD
> **Flow**: Non-Financial
> **Source**: URD (erp-iam-system/pre_openspec.md)
> **Classification Evidence**: FR-008 session management → auth-service → session tracking, concurrent limits, auto-expire
> **Archive**: N/A
> **Quality Score**: 92/100

## 📋 Feature Summary

Quản lý vòng đời session người dùng: track active sessions (Redis + PostgreSQL), enforce max concurrent sessions per role/user, auto-expire inactive sessions, admin monitoring/revocation APIs. Tích hợp chặt với JWT auth flow hiện có.

| Metric | Giá trị |
|--------|---------|
| Số FR | 1 (FR-008) |
| Use Cases | 6 |
| API Endpoints | 4 (admin) + 1 (filter) |
| Issues | 0 |
| **Quality Score** | **92/100** |

---

## 1. Actors

- **System Administrator**: Giám sát sessions, revoke sessions, cấu hình max concurrent
- **System**: Auto-expire, session tracking, activity update
- **End User**: Trigger session creation via login

## 2. Functional Requirements

### FR-008: Quản lý Session [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải track active sessions, cấu hình max concurrent sessions
- **Validation**: Auto-expire session khi inactive

#### Sub-requirements (derived):
- FR-008.1: Track session data (device, IP, user-agent, timestamps) on every login
- FR-008.2: Enforce configurable max concurrent sessions (per-role, per-user override)
- FR-008.3: Auto-expire sessions after configurable idle timeout (default 30 min)
- FR-008.4: Admin API to view, filter, and revoke sessions
- FR-008.5: Bulk session revocation (by user, by criteria)
- FR-008.6: Session cleanup scheduler with distributed locking

## 3. Non-Functional Requirements

| NFR | Requirement | Target |
|-----|------------|--------|
| Performance | Session validation latency | < 5ms (Redis) |
| Performance | Session creation latency | < 20ms |
| Availability | Session store uptime | 99.9% |
| Security | Session ID entropy | UUID v4 (122 bits) |
| Scalability | Concurrent sessions tracked | 100K+ |
| Compliance | Session timeout | Configurable, auditable |

## 4. Constraints

- Must use existing `SnowflakePersistentAuditableEntity` as base entity
- Must integrate with existing JWT flow (sessionId embedded in token claims)
- Must use existing Redis infrastructure (Spring Data Redis)
- Must follow Clean Architecture layer separation
- Flyway migrations must use V-prefix versioning

## 5. Dependencies

| Dependency | Type | Service |
|------------|------|---------|
| UserEntity | Internal | auth-service |
| TokenService | Internal | auth-service |
| Redis | Infrastructure | Shared |
| PostgreSQL | Infrastructure | auth-service DB |

## 6. Data Flow

```
Login Request
    → AuthController.login()
    → AuthService.authenticate()
    → SessionService.createSession()
        → Redis: check concurrent count
        → Redis: create session hash + add to user set
        → PostgreSQL: insert session record
    → TokenService.generateJWT(sessionId)
    → Response: {accessToken, refreshToken}

Authenticated Request
    → SessionValidationFilter
    → Redis: GET session:{userId}:{sessionId}
    → If exists: update lastActivityAt, refresh TTL
    → If not exists: 401 Unauthorized

Cleanup (Scheduled)
    → SessionCleanupScheduler (every 5 min)
    → Acquire distributed lock
    → Query expired sessions
    → Delete from Redis + Update PostgreSQL
```

## 7. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Redis downtime → all sessions lost | Low | High | DB fallback for session validation |
| Race condition on concurrent limit | Medium | Medium | Redis Lua script atomic check |
| Scheduler overlap across instances | Low | Low | Distributed lock (SETNX) |
| Token-session desync | Low | Medium | Session check on every request |

## 8. Acceptance Criteria

- [ ] AC-001: User login creates session in Redis AND PostgreSQL
- [ ] AC-002: Concurrent session count enforced (default 5)
- [ ] AC-003: Oldest session evicted when limit exceeded (FIFO policy)
- [ ] AC-004: Idle sessions auto-expired after 30 min (configurable)
- [ ] AC-005: Admin can view all active sessions with filtering
- [ ] AC-006: Admin can revoke single or bulk sessions
- [ ] AC-007: Revoked session → immediate 401 on next request
- [ ] AC-008: Session cleanup scheduler runs every 5 min with distributed lock
- [ ] AC-009: Session stats API returns correct metrics
- [ ] AC-010: All session events logged in audit trail

## 9. Implementation Priority

**Priority**: P1 (High) — Security foundation
**Estimated Effort**: 3-5 days
**Dependencies**: None (standalone within auth-service)

## 10. Detected Scope

| Service | Confidence | Evidence |
|---------|-----------|---------|
| auth-service | 95% | LoginSessionRepository, SessionCleanupScheduler, AdminSessionController already exist |

### Candidate Files (Existing):
- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
- `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt`
- `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/LoginSessionRepository.kt`
- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- `src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt`
