# Impact Analysis: FR-008 Session Management

> **Change**: FR-008-session-management | **Type**: NEWBUILD | **Risk**: MEDIUM

---

## 1. Change Summary

Thêm session management vào auth-service: tracking, concurrent limit, auto-expire, admin APIs.

## 2. Affected Components

### 2.1 Modified Files

| File | Change | Risk | Description |
|------|--------|------|-------------|
| `AuthService.kt` | MODIFY | Medium | Thêm `SessionService.createSession()` call sau authentication |
| `TokenService.kt` | MODIFY | Low | Embed sessionId vào JWT claims |
| `SecurityConfig.kt` | MODIFY | Medium | Register `SessionValidationFilter` vào filter chain |
| `application.yml` | MODIFY | Low | Thêm session config block |

### 2.2 New Files

| File | Type | Description |
|------|------|-------------|
| `LoginSessionEntity.kt` | Entity | JPA entity cho session tracking |
| `SessionStatus.kt` | Enum | ACTIVE, EXPIRED, REVOKED, LOGGED_OUT, EVICTED |
| `LoginSessionRepository.kt` | Repository | JPA repository + custom queries |
| `RedisSessionStore.kt` | Infrastructure | Redis hash/set operations |
| `SessionService.kt` | Service | Core session business logic |
| `SessionCleanupScheduler.kt` | Scheduler | @Scheduled cleanup task |
| `AdminSessionController.kt` | Controller | Admin session REST APIs |
| `SessionValidationFilter.kt` | Filter | Per-request session check |
| `SessionResponse.kt` | DTO | Response DTO |
| `BulkRevokeRequest.kt` | DTO | Request DTO |
| `V7__session_management.sql` | Migration | DDL for login_sessions table |

### 2.3 Deleted Files
None.

## 3. Database Impact

### 3.1 New Table: `login_sessions`
```sql
CREATE TABLE login_sessions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(36) UNIQUE NOT NULL,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    login_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    expired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    remember_me BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

### 3.2 Indexes
- `idx_login_sessions_user_status(user_id, status)` — primary query pattern
- `idx_login_sessions_session_id(session_id)` — unique lookup
- `idx_login_sessions_last_activity(last_activity_at) WHERE status='ACTIVE'` — cleanup queries

### 3.3 Data Migration
- No data migration needed (new table)
- Non-destructive — can be rolled back by dropping table

## 4. Redis Impact

### 4.1 New Key Patterns
| Pattern | Type | TTL | Expected Volume |
|---------|------|-----|-----------------|
| `session:{userId}:{sessionId}` | Hash | 1800s | 1 per active session |
| `session:user:{userId}:sessions` | Set | 86400s | 1 per active user |
| `session:cleanup:lock` | String | 300s | 1 globally |

### 4.2 Memory Estimate
- Per session: ~200 bytes (Hash) + ~50 bytes (Set member)
- 10K concurrent sessions ≈ 2.5MB additional Redis memory

## 5. API Impact

### 5.1 New Endpoints
| Method | Path | Breaking? |
|--------|------|-----------|
| GET | `/api/v1/admin/sessions` | No (new) |
| GET | `/api/v1/admin/sessions/stats` | No (new) |
| DELETE | `/api/v1/admin/sessions/{id}` | No (new) |
| POST | `/api/v1/admin/sessions/revoke-bulk` | No (new) |

### 5.2 Modified Endpoints
| Method | Path | Change |
|--------|------|--------|
| POST | `/api/v1/auth/login` | Response includes sessionId (backward compatible) |

### 5.3 Breaking Changes
**None** — all changes are additive.

## 6. Security Impact

| Aspect | Change | Risk |
|--------|--------|------|
| Authentication | Session validation on every request | Positive — enhanced security |
| Authorization | New permissions: SESSION_VIEW, SESSION_MANAGE | Neutral — additive |
| Token | SessionId added to JWT claims | Low — backward compatible |
| Rate limiting | Admin revoke APIs rate-limited | Positive — prevent abuse |

## 7. Performance Impact

| Operation | Before | After | Change |
|-----------|--------|-------|--------|
| Login | ~50ms | ~70ms | +20ms (session creation) |
| Authenticated request | ~2ms (JWT only) | ~7ms (JWT + Redis) | +5ms |
| Background cleanup | N/A | Every 5 min, <1s | New scheduled task |

### 7.1 Mitigation
- Redis connection pooling (existing)
- Async DB writes for activity updates
- Batch processing in cleanup scheduler

## 8. Rollback Plan

1. Remove `SessionValidationFilter` from SecurityConfig
2. Remove session creation from AuthService login flow
3. Drop `login_sessions` table (Flyway undo migration)
4. Remove Redis session keys: `SCAN + DEL session:*`
5. Remove new Kotlin files

**Rollback time estimate**: 15 minutes (config changes + deploy)

## 9. Testing Requirements

| Test Type | Coverage |
|-----------|----------|
| Unit tests | SessionService, RedisSessionStore |
| Integration tests | AdminSessionController API tests |
| Performance tests | Session validation latency < 5ms |
| Security tests | Permission enforcement, session fixation |

## 10. Deployment Checklist

- [ ] Flyway migration runs successfully
- [ ] Redis keys created on login
- [ ] Session validation filter active
- [ ] Admin APIs accessible with correct permissions
- [ ] Cleanup scheduler running
- [ ] Monitoring dashboards updated
