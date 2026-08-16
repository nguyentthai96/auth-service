# Business Analysis: FR-008 Session Management

> Phân tích nghiệp vụ chi tiết cho tính năng quản lý Session trong ERP IAM System

## Keywords
session management, concurrent sessions, idle timeout, session tracking, Redis session store, admin session monitoring, session revocation, GDPR compliance

## 1. Tổng quan nghiệp vụ

### 1.1 Mục đích
Quản lý vòng đời session người dùng từ lúc đăng nhập đến khi session hết hạn hoặc bị revoke. Đảm bảo:
- Mỗi user không vượt quá giới hạn concurrent sessions
- Session không hoạt động tự động hết hạn
- Admin có thể giám sát và can thiệp sessions

### 1.2 Business Context
Trong môi trường enterprise, session management là yêu cầu bảo mật cơ bản:
- **Compliance**: SOC2, ISO27001 yêu cầu session timeout
- **Security**: Giới hạn concurrent sessions ngăn credential sharing
- **Audit**: Track login history phục vụ investigation

## 2. Use Cases

### UC-001: Track Active Session
- **Actor**: System (triggered by login)
- **Precondition**: User đăng nhập thành công
- **Basic Flow**:
  1. AuthController gọi TokenService tạo JWT
  2. System tạo session record trong Redis (primary) + PostgreSQL (history)
  3. Session record chứa: sessionId, userId, deviceInfo, ipAddress, userAgent, loginAt, lastActivityAt
  4. Redis key: `session:{userId}:{sessionId}` với TTL = max_idle_timeout
- **Exception Flow**:
  - E1: Max concurrent sessions reached → evict oldest session (FIFO) hoặc reject login (configurable)
  - E2: Redis unavailable → fallback to DB-only mode, log warning
- **Post-condition**: Session tracked, token issued

### UC-002: Enforce Max Concurrent Sessions
- **Actor**: System (triggered by login)
- **Precondition**: User login thành công, session about to be created
- **Basic Flow**:
  1. Count active sessions for user từ Redis: `SCARD session:user:{userId}:sessions`
  2. So sánh với `max_concurrent_sessions` config (per-role hoặc global)
  3. Nếu <= limit → allow, tạo session mới
  4. Nếu > limit → apply policy (evict oldest hoặc reject)
- **Exception Flow**:
  - E1: Race condition (2 concurrent logins) → Redis WATCH/MULTI hoặc Lua script atomic check-and-create
  - E2: Admin override → bypass limit (flagged in audit)
- **Business Rules**:
  - BR-001: Default max = 5 concurrent sessions
  - BR-002: Admin role = unlimited
  - BR-003: Per-role config overrides global
  - BR-004: Per-user config overrides per-role

### UC-003: Auto-Expire Inactive Sessions
- **Actor**: System (scheduled task)
- **Precondition**: Sessions exist with lastActivityAt
- **Basic Flow**:
  1. SessionCleanupScheduler chạy mỗi 5 phút
  2. Scan sessions có `lastActivityAt` > `idle_timeout` (default 30 min)
  3. Mark session expired trong Redis (delete key)
  4. Update PostgreSQL record: status=EXPIRED, expiredAt=now()
  5. Emit event: `SessionExpiredEvent` (for audit trail)
- **Exception Flow**:
  - E1: Distributed lock fail → skip this run, retry next cycle
  - E2: DB write fail → log error, session already removed from Redis
- **Business Rules**:
  - BR-005: Idle timeout configurable per domain (default 30 min)
  - BR-006: Absolute session timeout = 24h (không thể extend)
  - BR-007: "Remember me" sessions extend idle to 7 days

### UC-004: Admin View Active Sessions
- **Actor**: System Administrator
- **Precondition**: Admin authenticated with SESSION_VIEW permission
- **Basic Flow**:
  1. Admin calls `GET /api/v1/admin/sessions?userId={id}&page=0&size=20`
  2. System queries Redis for active sessions
  3. Return list: sessionId, userId, deviceInfo, ip, loginAt, lastActivity, status
  4. Support filtering by userId, status, dateRange
- **Exception Flow**:
  - E1: User not found → 404
  - E2: No active sessions → empty list (200)

### UC-005: Admin Revoke Session(s)
- **Actor**: System Administrator
- **Precondition**: Admin authenticated with SESSION_MANAGE permission
- **Basic Flow**:
  1. Admin calls `DELETE /api/v1/admin/sessions/{sessionId}` (single) hoặc `POST /api/v1/admin/sessions/revoke-bulk` (bulk)
  2. System removes session from Redis
  3. Update DB record: status=REVOKED, revokedBy=adminId, revokedAt=now()
  4. Emit `SessionRevokedEvent`
  5. Next request with old token → 401 (session not found in Redis)
- **Exception Flow**:
  - E1: Session already expired → 404 hoặc 200 (idempotent)
  - E2: Bulk revoke all for user → confirm action, revoke all active sessions
- **Business Rules**:
  - BR-008: Revoke triggers immediate logout (token invalidation)
  - BR-009: Revoke action logged in audit trail
  - BR-010: Self-revoke not allowed (prevent accidental lockout)

### UC-006: Update Session Activity
- **Actor**: System (triggered by any authenticated request)
- **Precondition**: Valid session exists
- **Basic Flow**:
  1. Authentication filter extracts sessionId from JWT
  2. Update Redis: `session:{userId}:{sessionId}` → set lastActivityAt = now(), refresh TTL
  3. Periodic DB sync (every 5 min) for lastActivityAt
- **Exception Flow**:
  - E1: Session not found in Redis → 401 Unauthorized
  - E2: Redis update fail → log warning, continue (don't block request)

## 3. Data Model

### 3.1 Session Entity
```
LoginSessionEntity:
  - id: Long (Snowflake)
  - userId: Long (FK → User)
  - sessionId: String (UUID)
  - deviceInfo: String (user-agent parsed)
  - ipAddress: String
  - userAgent: String (raw)
  - loginAt: Instant
  - lastActivityAt: Instant
  - expiredAt: Instant?
  - revokedAt: Instant?
  - revokedBy: Long? (admin who revoked)
  - status: SessionStatus (ACTIVE, EXPIRED, REVOKED, LOGGED_OUT)
  - rememberMe: Boolean
  - createdAt: Instant
  - updatedAt: Instant
```

### 3.2 Redis Structure
```
# Active session data (Hash)
session:{userId}:{sessionId} → {deviceInfo, ip, loginAt, lastActivityAt}
TTL = idle_timeout (30 min default)

# User's session set (for counting)
session:user:{userId}:sessions → Set<sessionId>
TTL = max_session_lifetime (24h)

# Session config (per domain)
session:config:{domainId} → {maxConcurrent, idleTimeout, absoluteTimeout}
```

## 4. Traceability Matrix

| FR | Use Case | Business Rule | API Endpoint |
|----|----------|---------------|-------------|
| FR-008 | UC-001 | BR-001..004 | POST /api/v1/auth/login |
| FR-008 | UC-002 | BR-001..004 | POST /api/v1/auth/login |
| FR-008 | UC-003 | BR-005..007 | (Scheduler) |
| FR-008 | UC-004 | — | GET /api/v1/admin/sessions |
| FR-008 | UC-005 | BR-008..010 | DELETE /api/v1/admin/sessions/{id} |
| FR-008 | UC-006 | — | (Filter/Interceptor) |

## 5. Business Rules Summary

| ID | Rule | Source |
|----|------|--------|
| BR-001 | Default max concurrent sessions = 5 | URD |
| BR-002 | Admin role = unlimited sessions | Design Decision |
| BR-003 | Per-role config overrides global | URD |
| BR-004 | Per-user config overrides per-role | URD |
| BR-005 | Idle timeout configurable per domain, default 30 min | URD |
| BR-006 | Absolute session timeout = 24h | Security best practice |
| BR-007 | "Remember me" extends idle to 7 days | Design Decision |
| BR-008 | Revoke triggers immediate logout | URD |
| BR-009 | Revoke action logged in audit trail | Compliance |
| BR-010 | Self-revoke not allowed | Design Decision |
