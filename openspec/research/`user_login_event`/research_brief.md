# Research Brief: User Login Event

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | User Login Event |
| **Ngày tạo** | 2025-08-22 |
| **Input source** | Idea (enriched description) |
| **Input content** | Build production-grade UserLoginEvent domain event following Event Sourcing + CQRS pattern — enriched payload with device/session/geo context, event store persistence via EventService, transactional outbox for Kafka relay, and downstream consumer integration for audit trail, anomaly detection, and session management |
| **Người yêu cầu** | Pipeline — auth-core-features |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already implements a mature Event Sourcing foundation established by the `user_registration_event` feature:

1. **EventService** — intermediary service wrapping event store persist + outbox insert in caller's `@Transactional` boundary
2. **EventStorePort / OutboxPort** — outbound ports for append-only event log and transactional outbox
3. **EventEnvelope** — CloudEvents-inspired generic envelope with correlation ID, schema version, and metadata
4. **OutboxPoller** — async Kafka relay with retry, DLT, and timeout handling (FR-021)
5. **KafkaEventPublisher** — direct Kafka publisher for non-outbox events (AuditEvent, PermissionChangedEvent)

The `RegisterHandler` already records a rich `UserRegisteredEvent` via `EventService.record()` with topic `iam.user.registered`. However, the `LoginHandler` currently does **NOT** record a domain event to the event store or outbox. It only:

- Records a login session via `LoginSessionService.recordLogin()` (writes to `login_sessions` table)
- Publishes a minimal `UserLoggedInEvent(userId, domainCode)` via `SpringEventPublisher` (in-process only, NOT persisted)

This gap means:
- **No event store record** of login events — breaks audit trail completeness
- **No Kafka publishing** of login events — downstream services (system-admin, analytics, SIEM) cannot react
- **Minimal payload** — missing IP address, device fingerprint, user-agent, MFA status, session promotion context
- **No correlation ID** — cannot trace login flow across services
- **No schema versioning** — UserLoggedInEvent lacks `eventType` following naming convention

This feature closes this gap by creating a production-grade `UserLoggedInEvent` that mirrors the `UserRegisteredEvent` pattern established in the registration flow.

### 2.2 Mục tiêu (Objectives)

- [ ] Objective 1: Design enriched `UserLoggedInEvent` domain event with full authentication context (device, geo, MFA, session info)
- [ ] Objective 2: Integrate with existing `EventService.record()` for transactional event store + outbox persistence
- [ ] Objective 3: Define Kafka topic `iam.user.logged_in` with proper partitioning by userId
- [ ] Objective 4: Support downstream consumers: audit trail, anomaly detection, session analytics, SIEM
- [ ] Objective 5: Ensure backward compatibility with existing `UserLoggedInEvent` consumers (if any)
- [ ] Objective 6: Add `UserLoginFailedEvent` for failed login attempts (security monitoring)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| `UserLoggedInEvent` enriched payload design | Full analytics pipeline for login data |
| `UserLoginFailedEvent` for failed attempts | Real-time anomaly detection ML model |
| EventService integration in LoginHandler | Refactoring LoginHandler's core auth flow |
| Kafka topic `iam.user.logged_in` design | Kafka cluster operations/setup |
| Event store persistence for login events | Event replay/projection infrastructure |
| Correlation ID propagation | Distributed tracing (Zipkin/Jaeger) setup |
| Schema versioning for login events | Event upcaster infrastructure |
| Audit trail downstream contract | Implementing all downstream consumers |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `user login event`
- `UserLoggedInEvent`
- `authentication event sourcing`
- `login domain event CQRS`
- `login audit trail event`

### 3.2 Secondary Keywords
- `login failed event security`
- `authentication event enrichment`
- `device fingerprint login event`
- `login anomaly detection event`
- `session tracking event sourcing`
- `login Kafka topic design`

### 3.3 Domain-Specific Terms
- `UserLoggedInEvent`: Domain event representing a successful user authentication — immutable fact in the event store
- `UserLoginFailedEvent`: Domain event representing a failed authentication attempt — used for security monitoring and rate limiting analytics
- `Login Session`: A record of an active user session with device, IP, and temporal metadata
- `Device Fingerprint`: SHA-256 hash of client device characteristics used for device trust and anomaly detection
- `Geo-IP`: Geographical location derived from client IP address for login location tracking
- `MFA Checkpoint`: The point in login flow where multi-factor authentication is evaluated and either required or bypassed

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"user login event sourcing domain event best practices"` | General | High |
| 2 | `"authentication login event Kafka Spring Boot"` | Architecture | High |
| 3 | `"login audit event enriched payload design"` | Design Pattern | High |
| 4 | `"login failed event security monitoring CQRS"` | Security | Medium |
| 5 | `"user login event vs session tracking comparison"` | Comparison | Medium |
| 6 | `"CloudEvents login event schema"` | Standards | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| LoginHandler (CQRS) | `auth.application.command.LoginHandler` | **Critical** | Main entry point — will be modified to record events |
| UserLoggedInEvent (existing) | `auth.application.command.AuthDomainEvents` | **Critical** | Current minimal event (userId, domainCode only) — needs enrichment |
| UserRegisteredEvent (reference) | `auth.domain.event.UserRegisteredEvent` | **High** | Pattern reference — enriched event with EventService integration |
| EventService | `auth.application.event.EventService` | **Critical** | Reusable service for event store + outbox in same TX |
| LoginSessionService | `auth.application.LoginSessionService` | **High** | Already records login session — source for device/session metadata |
| LoginRateLimitService | `auth.application.LoginRateLimitService` | **Medium** | Records failed attempts — integration point for failed login events |
| AuditLogService | `shared.audit.AuditLogService` | **Medium** | Currently logs LOGIN_SUCCESS/LOGIN_FAILED — potential downstream consumer |
| KafkaEventPublisher | `auth.adapter.out.event.KafkaEventPublisher` | **Medium** | Direct Kafka publish for non-outbox events |
| OutboxPoller | `auth.adapter.out.event.OutboxPoller` | **High** | Async Kafka relay — will relay login events from outbox |
| SessionPromotionService | `auth.application.SessionPromotionService` | **Low** | Anonymous→authenticated session promotion context |

### 4.2 Existing Code Patterns

**Event Recording Pattern (from RegisterHandler):**
```kotlin
eventService.record(
    aggregateType = "User",
    aggregateId = savedUser.id.value,
    event = UserRegisteredEvent(...),
    topic = "iam.user.registered",
    partitionKey = savedUser.id.value.toString(),
    correlationId = command.correlationId
)
```

This is the exact pattern to follow for `UserLoggedInEvent` in `LoginHandler`.

**Existing Event Structure:**
- `DomainEvent` interface with `eventType: String`
- `EventEnvelope<T>` wraps any `DomainEvent` with CloudEvents metadata
- Events stored in `event_store` table (JSONB payload)
- Events relayed via `event_outbox` table → `OutboxPoller` → Kafka

**CQRS Pattern:**
- Commands implement `Command` from `eventsourcingutils.lib.cqrs.command`
- Handlers implement `CommandHandler<C, R>`
- Login flow: `LoginCommand` → `LoginHandler` → `LoginResult`

### 4.3 Tech Stack Constraints
- Language: Kotlin 1.9+
- Framework: Spring Boot 3.x with spring-boot-starter-web
- Database: PostgreSQL with Flyway migrations
- Cache: Redis (session/token blacklist) + Caffeine (local L1)
- Message Queue: Kafka (via spring-kafka)
- Event Sourcing: Custom `eventsourcing-utils` library (CQRS commands/queries)
- Auth: Spring Security + JWT (JJWT) + OAuth2
- Build: Gradle Kotlin DSL
- Base: `com.ntt:base-core` platform with auto-configuration starters

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| EventService.record() | Service | `auth.application.event.EventService` | Append to event store + outbox in caller TX |
| EventStorePort | Port (outbound) | `auth.application.port.out.EventStorePort` | Append-only event persistence |
| OutboxPort | Port (outbound) | `auth.application.port.out.OutboxPort` | Transactional outbox for Kafka relay |
| event_store table | Table | `V11__event_sourcing_tables.sql` | PostgreSQL, JSONB payload, aggregate indexing |
| event_outbox table | Table | `V11__event_sourcing_tables.sql` | PostgreSQL, status-based polling |
| login_sessions table | Table | `V4__create_login_sessions.sql` | Login session tracking (existing) |
| LoginHandler | Handler | `auth.application.command.LoginHandler` | Will be modified to call EventService.record() |
| LoginCommand | Command | `auth.application.command.LoginCommand` | Must carry enriched context fields |
| Kafka topic iam.user.logged_in | Topic | New | Downstream consumers subscribe here |
| Kafka topic iam.user.login_failed | Topic | New | Security monitoring consumers subscribe here |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [ ] Q1: What fields should `UserLoggedInEvent` include for comprehensive downstream consumer needs (audit, anomaly, SIEM)?
- [ ] Q2: Should `UserLoginFailedEvent` share the same enriched structure or be a lighter event?
- [ ] Q3: Should login events be recorded BEFORE or AFTER token generation in LoginHandler?
- [ ] Q4: How to handle MFA checkpoint — should MFA-pending logins emit an event or only successful completions?
- [ ] Q5: What is the recommended Kafka topic naming and partitioning strategy for login events?
- [ ] Q6: How to avoid duplicate event recording if LoginHandler is retried?

### 5.2 Assumptions cần verify

- [ ] A1: LoginCommand already carries ipAddress, userAgent, deviceFingerprint from the controller layer
- [ ] A2: EventService.record() can handle high-frequency login events without performance issues
- [ ] A3: OutboxPoller's current polling interval is sufficient for login event throughput
- [ ] A4: Existing event_store indexes support efficient querying by event_type for login analytics

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive analysis of login event patterns in event sourcing | ≥ 5 sources |
| Open source options | Evaluate frameworks/libraries for event enrichment | ≥ 3 repos evaluated |
| Gap analysis | Identify all gaps between current and target login event system | All critical gaps identified |
| Business analysis | All login event use cases documented | All UCs documented |
| Technical spec | Agent-ready specification for implementing UserLoggedInEvent | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
