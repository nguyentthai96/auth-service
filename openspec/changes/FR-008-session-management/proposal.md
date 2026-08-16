# Proposal: FR-008 Session Management

> **Change**: FR-008-session-management | **Type**: NEWBUILD | **Priority**: P1

---

## 1. Problem Statement

Hệ thống auth-service hiện có login/register cơ bản với JWT token nhưng:
- **Không track active sessions** — không biết user đang login từ bao nhiêu thiết bị
- **Không giới hạn concurrent sessions** — user có thể share credentials vô hạn
- **Không auto-expire inactive sessions** — security risk khi user quên logout
- **Không có admin monitoring** — không thể can thiệp khi phát hiện anomaly

Đây là yêu cầu bảo mật enterprise bắt buộc (SOC2, ISO27001).

## 2. Proposed Solution

### 2.1 Overview
Triển khai Hybrid Session Store (Redis + PostgreSQL) tích hợp với JWT auth flow hiện có:
- **Redis**: Active session tracking, real-time validation, TTL-based expiration
- **PostgreSQL**: Session history, audit trail, analytics

### 2.2 Key Components

| Component | Type | Description |
|-----------|------|-------------|
| `LoginSessionEntity` | Entity | JPA entity tracking session data |
| `LoginSessionRepository` | Repository | JPA queries + custom methods |
| `RedisSessionStore` | Infrastructure | Redis hash/set operations |
| `SessionService` | Service | Core business logic |
| `SessionCleanupScheduler` | Scheduler | Background cleanup task |
| `AdminSessionController` | Controller | Admin REST APIs |
| `SessionValidationFilter` | Filter | Per-request session check |

### 2.3 Architecture Decision
- **Custom implementation** thay vì Spring Session vì:
  1. Spring Session tied to HttpSession — không fit JWT flow
  2. Cần custom business rules (per-role limits, admin revocation)
  3. Redis integration đã sẵn sàng trong project
  4. Full control over session lifecycle

## 3. Impact Analysis

### 3.1 Affected Components
| Component | Change Type | Risk Level |
|-----------|------------|------------|
| AuthService (login flow) | MODIFY | Medium — thêm session creation |
| TokenService | MODIFY | Low — thêm sessionId trong JWT claims |
| Spring Security filter chain | MODIFY | Medium — thêm SessionValidationFilter |
| application.yml | MODIFY | Low — thêm session config block |

### 3.2 New Components
- 7 new files (entity, repository, redis store, service, scheduler, controller, filter)
- 1 new Flyway migration (V7__session_management.sql)
- 4 new DTOs

### 3.3 Breaking Changes
- **None** — backward compatible
- Existing tokens without sessionId sẽ bypass session check (grace period)

## 4. Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Redis downtime → login fails | High | DB fallback mode for validation |
| Performance regression (per-request Redis) | Medium | Connection pooling, sub-5ms target |
| Concurrent session race condition | Medium | Redis Lua script atomic operations |
| Migration on production DB | Low | Non-destructive, new table only |

## 5. Effort Estimation

| Phase | Tasks | Estimate |
|-------|-------|----------|
| Data Layer | Entity, Repository, Migration | 0.5 day |
| Service Layer | RedisStore, Service, Scheduler | 1.5 days |
| API Layer | Controller, DTOs, Filter | 1 day |
| Integration | Auth flow integration, config | 0.5 day |
| Testing | Unit + Integration tests | 1 day |
| **Total** | | **4.5 days** |

## 6. Acceptance Criteria

- [ ] User login creates session in Redis AND PostgreSQL
- [ ] Max concurrent sessions enforced (configurable, default 5)
- [ ] Idle sessions auto-expired (configurable, default 30 min)
- [ ] Admin can view and revoke sessions via REST API
- [ ] Session validation on every request (< 5ms)
- [ ] All session events in audit trail

## 7. Recommendation

**APPROVE** — This is a foundational security feature required for enterprise compliance.
Implementation is straightforward with existing infrastructure (Redis, PostgreSQL, Spring Security).
No breaking changes. Risk is manageable with fallback strategies.
## Changes

### New Files
| File | Type | Description |
|------|------|-------------|
| `LoginSessionEntity.kt` | Entity | Session tracking entity |
| `LoginSessionRepository.kt` | Repository | JPA repository |
| `RedisSessionStore.kt` | Infrastructure | Redis session operations |
| `SessionService.kt` | Service | Core session logic |
| `SessionCleanupScheduler.kt` | Scheduler | Auto-expire cleanup |
| `AdminSessionController.kt` | Controller | Admin REST APIs |
| `SessionValidationFilter.kt` | Filter | Per-request check |
| `V7__session_management.sql` | Migration | DDL for login_sessions |

### Modified Files
| File | Change |
|------|--------|
| `AuthService.kt` | Add session creation on login |
| `TokenService.kt` | Embed sessionId in JWT |
| `SecurityConfig.kt` | Register SessionValidationFilter |
| `application.yml` | Add session config block |
