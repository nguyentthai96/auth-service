# SRS: FR-008 Session Management

> **Change**: FR-008-session-management | **Type**: NEWBUILD | **Version**: 1.0

---

## 1. Introduction

### 1.1 Purpose
Quản lý vòng đời session người dùng trong auth-service, bao gồm tracking, concurrent limiting, auto-expiration, và admin monitoring.

### 1.2 Scope
| In Scope | Out of Scope |
|----------|-------------|
| Active session tracking (Redis + DB) | Biometric session binding |
| Max concurrent sessions enforcement | Geo-fencing |
| Auto-expire inactive sessions | Cross-service session federation |
| Admin session monitoring APIs | WebSocket session management |
| Session cleanup scheduler | Frontend session UI |

### 1.3 References
- `pre_openspec.md` — Feature specification
- `design.md` — Technical design
- `research_brief.md` — Research findings

## 2. Functional Requirements

### FR-008.1: Session Creation
- **Priority**: P1
- **Description**: Khi user đăng nhập thành công, hệ thống phải tạo session record chứa: sessionId (UUID v4), userId, deviceInfo, ipAddress, userAgent, loginAt, lastActivityAt
- **Acceptance Criteria**:
  - Session tạo đồng thời trong Redis (TTL=30min) và PostgreSQL
  - SessionId embedded trong JWT claims
  - Response time < 20ms

### FR-008.2: Concurrent Session Enforcement
- **Priority**: P1
- **Description**: Hệ thống phải giới hạn số concurrent sessions per user theo cấu hình
- **Acceptance Criteria**:
  - Default max = 5 sessions
  - Per-role override hỗ trợ
  - Khi vượt limit → evict oldest session (FIFO)
  - Atomic check via Redis Lua script

### FR-008.3: Auto-Expiration
- **Priority**: P1
- **Description**: Sessions không hoạt động phải tự động expire sau idle timeout
- **Acceptance Criteria**:
  - Default idle timeout = 30 phút (configurable)
  - Absolute timeout = 24h
  - "Remember me" extends idle to 7 days
  - Cleanup scheduler chạy mỗi 5 phút với distributed lock

### FR-008.4: Admin Session View
- **Priority**: P2
- **Description**: Admin có thể xem tất cả active sessions với filtering
- **Acceptance Criteria**:
  - Filter by userId, status, dateRange
  - Paginated response (max 100 per page)
  - Requires SESSION_VIEW permission

### FR-008.5: Admin Session Revocation
- **Priority**: P2
- **Description**: Admin có thể revoke single hoặc multiple sessions
- **Acceptance Criteria**:
  - Single: DELETE /api/v1/admin/sessions/{id}
  - Bulk: POST /api/v1/admin/sessions/revoke-bulk
  - Revoked session → immediate 401 on next request
  - All revocations logged in audit trail
  - Self-revoke not allowed

### FR-008.6: Session Activity Tracking
- **Priority**: P1
- **Description**: Mỗi authenticated request phải update lastActivityAt và refresh Redis TTL
- **Acceptance Criteria**:
  - SessionValidationFilter chạy per-request
  - Redis TTL refreshed on each activity
  - Latency < 5ms per request

## 3. Non-Functional Requirements

### NFR-001: Performance
| Metric | Target |
|--------|--------|
| Session validation latency | < 5ms |
| Session creation latency | < 20ms |
| Cleanup batch processing | < 1s per 100 sessions |

### NFR-002: Scalability
- Support 100K+ concurrent sessions
- Horizontal scaling via Redis cluster

### NFR-003: Availability
- Session store uptime: 99.9%
- Graceful degradation when Redis unavailable

### NFR-004: Security
- Session ID: UUID v4 (122 bits entropy)
- Session fixation prevention
- Admin APIs require specific permissions

## 4. Data Requirements

### 4.1 Entity: LoginSession
| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, Snowflake |
| userId | Long | FK → User, NOT NULL |
| sessionId | String(36) | UNIQUE, NOT NULL |
| deviceInfo | String(500) | nullable |
| ipAddress | String(45) | nullable |
| userAgent | String(1000) | nullable |
| loginAt | Instant | NOT NULL |
| lastActivityAt | Instant | NOT NULL |
| expiredAt | Instant | nullable |
| revokedAt | Instant | nullable |
| revokedBy | Long | nullable, FK → User |
| status | SessionStatus | NOT NULL, default ACTIVE |
| rememberMe | Boolean | NOT NULL, default false |

## 5. Interface Requirements

### 5.1 REST APIs
| Method | Path | Auth | Rate Limit |
|--------|------|------|------------|
| GET | /api/v1/admin/sessions | SESSION_VIEW | 100/min |
| GET | /api/v1/admin/sessions/stats | SESSION_VIEW | 60/min |
| DELETE | /api/v1/admin/sessions/{id} | SESSION_MANAGE | 30/min |
| POST | /api/v1/admin/sessions/revoke-bulk | SESSION_MANAGE | 10/min |
