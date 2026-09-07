# Research Brief: JWT OAuth2 Security Redesign

> Tài liệu khởi đầu cho quá trình research — xác định scope, keywords, phân tích hiện trạng hệ thống.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | JWT OAuth2 Security Redesign |
| **Ngày tạo** | 2026-09-07 |
| **Input source** | Idea (mô tả tự do) |
| **Input content** | Thiết kế lại cơ chế JWT OAuth2 bảo mật: blacklist, fingerprint, JTI, device management, mail notification |
| **Người yêu cầu** | User |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hệ thống auth-service hiện tại đã có nền tảng JWT OAuth2 khá hoàn chỉnh:
- RS256 signing + HMAC fallback + key rotation
- Token blacklist 3 tầng (Caffeine L1 → Redis L2 → DB L3)
- Login session tracking + new device detection (basic)
- Session policy enforcement (max sessions per role)
- CQRS command/query handlers
- Claim validation chain (issuer, audience, token type)

**Tuy nhiên còn thiếu/chưa hoàn thiện:**
- ❌ Device fingerprint chỉ là field tùy chọn — không có thuật toán tính toán phía server
- ❌ Chưa có cơ chế binding JWT token với device fingerprint (token binding / DPoP)
- ❌ Chưa có API quản lý device (list, kick out, clear specific device)
- ❌ New device login event chỉ emit nhưng chưa có consumer gửi email thông báo
- ❌ Chưa có mail queue table + job gửi mail template tự động
- ❌ Session revocation chưa đồng bộ với token blacklist (revoke session ≠ revoke access token)

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Hoàn thiện cơ chế JWT token lifecycle (issue → validate → refresh → revoke → blacklist)
- [x] Objective 2: Implement device fingerprinting algorithm phía server-side
- [x] Objective 3: JWT Token Binding — gắn token với device fingerprint (JTI + fingerprint claim)
- [x] Objective 4: Device Management API (list active devices, kick out, clear, reject)
- [x] Objective 5: Email notification khi login device mới (mail queue + template job)
- [x] Objective 6: Đồng bộ session ↔ token ↔ blacklist lifecycle

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| JWT blacklist hoàn chỉnh (expire, revoke, logout) | DPoP (RFC 9449) — quá phức tạp cho phase 1 |
| Device fingerprint algorithm (server-side hash) | Browser-side fingerprint library (FingerprintJS) |
| JTI-based token tracking | mTLS token binding |
| Device management CRUD + kick out | Push notification (chỉ email) |
| Mail queue table + job scheduler | SMS notification |
| Session ↔ Token lifecycle sync | OAuth2 Authorization Server (Keycloak) |
| New device email notification | Marketing email |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `JWT token blacklist Redis`
- `device fingerprint algorithm`
- `JWT JTI token revocation`
- `session management device kick out`
- `email notification queue pattern`

### 3.2 Secondary Keywords
- `JWT token binding`
- `DPoP Demonstrating Proof-of-Possession`
- `refresh token rotation`
- `concurrent session management`
- `transactional outbox email`

### 3.3 Domain-Specific Terms
- `JTI (JWT ID)`: Unique identifier cho từng JWT token, dùng để blacklist/track
- `Device Fingerprint`: Hash SHA-256 từ tổ hợp signals (User-Agent, IP, screen, timezone...)
- `Token Rotation`: Issue new refresh token mỗi lần refresh, revoke old
- `Token Binding`: Gắn token với specific client context (device/browser)
- `Outbox Pattern`: Ghi event/task vào DB trong cùng transaction, poller gửi async

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `JWT token blacklist revoke logout best practices` | General | ✅ Done |
| 2 | `device fingerprinting algorithm JWT token binding` | Architecture | ✅ Done |
| 3 | `JWT JTI token rotation revocation Spring Security` | Implementation | ✅ Done |
| 4 | `active device session management kick out` | Device Mgmt | ✅ Done |
| 5 | `email queue table job scheduler outbox pattern` | Mail Queue | ✅ Done |
| 6 | `DPoP RFC 9449 OAuth2 token binding Spring` | Advanced | ✅ Done |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JWT Service (generate/parse/validate) | `auth.application.JwtService` | **High** | RS256 + HMAC + key rotation, JTI đã có |
| Token Blacklist Cache | `auth.application.TokenBlacklistCacheService` | **High** | 3-tier: Caffeine → Redis → DB |
| Login Session Service | `auth.application.LoginSessionService` | **High** | Record login, detect new device, revoke session |
| Session Policy Service | `auth.application.SessionPolicyService` | **High** | Max sessions per role, strategies |
| JWT Auth Filter | `shared.security.JwtAuthFilter` | **High** | Validate JWT, check blacklist, set SecurityContext |
| Claim Validator Chain | `auth.application.ClaimValidatorChain` | **Medium** | Issuer, Audience, TokenType validators |
| Token Store Port | `auth.application.port.out.TokenStore` | **High** | Save/find/revoke refresh token, blacklist JTI |
| Login Handler (CQRS) | `auth.application.command.LoginHandler` | **High** | Full login flow with rate limit, MFA, session |
| Refresh Token Handler | `auth.application.command.RefreshTokenHandler` | **High** | Token rotation + event recording |
| New Device Login Event | `auth.application.event.NewDeviceLoginEvent` | **High** | Event emitted but NO consumer for email |
| Session Controller | `auth.adapter.in.web.SessionController` | **Medium** | API endpoints cho session management |
| Admin Session Controller | `auth.adapter.in.web.AdminSessionController` | **Medium** | Admin revoke sessions |
| Notification Gateway | `auth.application.port.out.NotificationGateway` | **Medium** | Port for notifications (Kafka/Logging) |
| Event Outbox | `auth.adapter.out.persistence.entity.EventOutboxEntity` | **Medium** | Outbox pattern đã có cho domain events |
| Login Session Entity | `auth.adapter.out.persistence.entity.LoginSessionEntity` | **High** | DB entity tracking sessions |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Notes |
|---------|---------------|-------|
| **Architecture** | Clean Architecture / Hexagonal | Domain → Application → Adapter (in/out) |
| **CQRS** | CommandHandler/QueryHandler | eventsourcing-utils library |
| **Data Access** | JPA Repository | Spring Data JPA + Flyway migrations |
| **API Style** | REST Controllers | Spring MVC |
| **Error Handling** | Exception hierarchy + GlobalExceptionHandler | RFC 7807 ProblemDetail |
| **Caching** | Caffeine L1 + Redis L2 | Programmatic (not Spring Cache) |
| **Event Publishing** | Spring ApplicationEventPublisher | + Kafka + Outbox pattern |
| **Session** | STATELESS (JWT) | No server-side HTTP session |
| **Config** | @ConfigurationProperties | Type-safe, nested |
| **Observability** | Micrometer metrics | Counters, Timers, Gauges |

### 4.3 Tech Stack Constraints

- **Language**: Kotlin (JVM target)
- **Framework**: Spring Boot 4.x + Spring Security 6.x
- **Database**: PostgreSQL + Flyway migrations
- **Cache**: Redis (distributed) + Caffeine (in-process)
- **Build tool**: Gradle Kotlin DSL
- **JWT Library**: io.jsonwebtoken (jjwt-api/impl/jackson)
- **Event**: Spring Kafka + Spring ApplicationEvent
- **Key dependencies**: base-core platform, eventsourcing-utils, Google Tink (E2EE)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtAuthFilter` | Filter chain | `shared.security.JwtAuthFilter` | Thêm fingerprint validation vào đây |
| `TokenStore` port | Interface | `auth.application.port.out.TokenStore` | Thêm methods cho device-bound tokens |
| `LoginSessionEntity` | DB Table | `auth.adapter.out.persistence.entity` | Extend thêm fields cho device mgmt |
| `LoginSessionService` | Service | `auth.application.LoginSessionService` | Enhance device management APIs |
| `LoginHandler` | Command | `auth.application.command.LoginHandler` | Integrate fingerprint vào login flow |
| `NotificationGateway` | Port | `auth.application.port.out.NotificationGateway` | Route new device email |
| `EventOutboxEntity` | Outbox | `auth.adapter.out.persistence.entity` | Reuse pattern cho mail queue |
| `SecurityProperties` | Config | `shared.config.SecurityProperties` | Thêm fingerprint + device config |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Best practice cho JWT token blacklist + revocation lifecycle? → **Redis denylist by JTI + TTL = remaining token lifetime**
- [x] Q2: Thuật toán device fingerprint nào phù hợp cho server-side? → **SHA-256 hash of (User-Agent + IP prefix + Accept-Language + timezone)**
- [x] Q3: Nên dùng DPoP (RFC 9449) hay device fingerprint claim? → **Fingerprint claim cho phase 1, DPoP cho phase 2 nếu cần**
- [x] Q4: Mail queue pattern nào phù hợp? → **Transactional outbox: mail_queue table + @Scheduled poller**
- [x] Q5: Cách đồng bộ session revoke → token blacklist? → **Revoke session = blacklist all JTIs of that session's refresh token chain**

### 5.2 Assumptions cần verify
- [x] A1: Device fingerprint cần stable across requests nhưng unique per device → Confirm bằng User-Agent + IP prefix (subnet /24)
- [x] A2: Mail queue dùng cùng PostgreSQL DB, không cần external message broker riêng → Confirm, reuse outbox pattern
- [x] A3: Frontend sẽ gửi device fingerprint hash qua header `X-Device-Fingerprint` → Cần define header contract

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ sources cho mỗi component | ≥ 5 sources (✅ 15+ sources) |
| Open source options | Evaluate các library/tool liên quan | ≥ 3 repos/tools evaluated |
| Gap analysis | Xác định gaps giữa hiện trạng vs target | All critical gaps identified |
| Business analysis | Use cases cho từng feature component | All UCs documented |
| Technical spec | Agent-ready cho implementation | Detailed enough for coding |

---

> **Next step**: Phase 4 (Deep Analysis) → Phase 5 (Business Analysis) → Phase 6 (Technical Spec)
