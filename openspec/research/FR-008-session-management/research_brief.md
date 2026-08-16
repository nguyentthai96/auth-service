# Research Brief: FR-008 Session Management

> Tài liệu nghiên cứu tính năng Session Management cho ERP IAM System

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | FR-008: Quản lý Session |
| **Ngày tạo** | 2026-08-16 |
| **Input source** | URD (pre_openspec.md) |
| **Feature type** | NEWBUILD |
| **Service** | auth-service |
| **Technology** | Spring Boot, Kotlin, Redis, PostgreSQL, JWT |

## 2. Mô tả tính năng

### 2.1 Bối cảnh
Hệ thống auth-service hiện có login/register cơ bản với JWT token nhưng **không track active sessions**, không giới hạn concurrent sessions, và không auto-expire inactive sessions. Đây là yêu cầu bắt buộc cho enterprise IAM.

### 2.2 Mục tiêu
- Track tất cả active sessions per user (device, IP, user-agent, login time)
- Cấu hình max concurrent sessions per role/user
- Auto-expire sessions khi inactive (configurable timeout)
- Admin có thể xem và revoke sessions
- Session cleanup scheduler chạy nền

### 2.3 Phạm vi

| In Scope | Out of Scope |
|----------|-------------|
| Active session tracking (Redis + DB) | Biometric session binding |
| Max concurrent sessions enforcement | Geo-fencing |
| Auto-expire inactive sessions | Session replay protection |
| Admin session dashboard API | Frontend session UI |
| Bulk session revocation | Cross-service session federation |
| Session cleanup scheduler | WebSocket session management |

## Keywords

## 3. Keywords & Search Queries

### 3.1 Primary Keywords
- session management, session tracking, concurrent session control
- session expiration, idle timeout, session store
- Redis session, Spring Session, session clustering

### 3.2 Secondary Keywords
- session fixation prevention, session hijacking protection
- distributed session, session replication
- admin session monitoring, session audit

### 3.3 Search Queries
1. "Spring Boot session management Redis best practices"
2. "enterprise session tracking concurrent limit implementation"
3. "session auto-expiration idle timeout Spring Security"
4. "admin session monitoring dashboard API design"
5. "Redis session store clustering replication patterns"

## 4. Current System Analysis

### 4.1 Related Features (Existing)
- `LoginSessionRepository.kt` — JPA repository cho session persistence
- `CipherKeySessionEntity.kt` — Entity session cho cipher key management
- `RedisCipherKeySessionResolver.kt` — Redis-based session resolver

### 4.2 Existing Patterns
- **Architecture**: Clean Architecture (adapter/application/domain layers)
- **Base Entity**: `SnowflakePersistentAuditableEntity` (Long? ID, Instant? timestamps)
- **Session Store**: Hybrid — Redis cho active sessions, PostgreSQL cho history
- **Token**: JWT RS256 với refresh token flow
- **Cache**: Redis với TTL-based expiration

### 4.3 Tech Stack Constraints
- Kotlin + Spring Boot 3.x
- PostgreSQL (primary), Redis (cache/session)
- Flyway migrations (V-prefixed)
- Gradle Kotlin DSL
- JPA/Hibernate for persistence

### 4.4 Integration Points
- `AuthController` — login flow triggers session creation
- `TokenService` — JWT generation linked to session
- `UserEntity` — session owner reference
- Redis — active session storage with TTL
- `AuditEntity` — session events logging

## 5. Research Scope Assessment

### 5.1 Complexity Score: **Medium** (6/10)
- Standard session patterns (well-documented)
- Redis integration already exists
- Main complexity: concurrent session enforcement + admin APIs

### 5.2 Risk Assessment
| Risk | Level | Mitigation |
|------|-------|------------|
| Performance impact on high-traffic login | Medium | Redis-first, DB write-behind |
| Session state consistency across nodes | Low | Redis as single source of truth |
| Cleanup scheduler thundering herd | Low | Distributed lock with Redis |
| Max concurrent session race condition | Medium | Redis atomic operations (SETNX) |

### 5.3 Recommendation
**BUILD** — Triển khai custom vì:
1. Session logic tightly coupled với existing auth flow
2. Spring Session quá generic, không fit custom JWT flow
3. Redis integration đã sẵn sàng
4. Business rules đặc thù (per-role limits, admin revocation)
