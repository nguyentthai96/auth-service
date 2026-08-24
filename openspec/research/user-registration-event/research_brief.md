# Research Brief: User Registration Event

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | User Registration Event |
| **Ngày tạo** | 2025-08-22 |
| **Input source** | Name (feature name + description) |
| **Input content** | Production-grade `UserRegisteredEvent` domain event following existing Event Sourcing + CQRS patterns — enriched payload with full registration context (identity, domain, source, device info), event store persistence via `EventService`, transactional outbox for Kafka relay, and a dedicated `RegistrationEventRecorder` helper service for fire-and-forget resilience |
| **Người yêu cầu** | Pipeline — auth-core-features |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already has a mature Event Sourcing + CQRS foundation:

1. **EventService** — intermediary service wrapping event store persist + outbox insert in caller's `@Transactional` boundary (DD-003)
2. **EventStorePort / OutboxPort** — outbound ports for append-only event log and transactional outbox
3. **EventEnvelope** — CloudEvents-inspired generic envelope with correlation ID, schema version, metadata (FR-006)
4. **OutboxPoller** — async Kafka relay with retry, timeout handling, and FAILED state (FR-007, FR-021, FR-022)
5. **TokenEventRecorder / LoginEventRecorder** — helper services for event recording with fire-and-forget pattern
6. **KafkaEventPublisher** — direct Kafka publisher with retry for non-outbox events

**Existing domain events:**
- `UserRegisteredEvent` → topic `iam.user.registered` (via EventService + outbox in RegisterHandler)
- `UserLoggedInEvent` → topic `iam.user.logged_in` (via LoginEventRecorder + EventService)
- `UserLoginFailedEvent` → topic `iam.user.login_failed` (via LoginEventRecorder + EventService)
- `TokenIssuedEvent` → topic `iam.token.issued` (via TokenEventRecorder + EventService)
- `TokenRevokedEvent` → topic `iam.token.revoked` (via TokenEventRecorder + EventService)
- `TokenValidationFailedEvent` → topic `iam.token.validation-failed` (via TokenEventRecorder + EventService)

**Current state of UserRegisteredEvent:**
The `UserRegisteredEvent` already exists as a data class in `auth.domain.event` package with enriched payload (FR-001, FR-002). It is recorded directly in `RegisterHandler.handle()` via `EventService.record()`. However, there are several improvements to consider:

1. **No dedicated RegistrationEventRecorder** — unlike LoginHandler (which uses LoginEventRecorder), RegisterHandler calls EventService.record() directly without fire-and-forget protection. If event recording fails, the entire registration transaction will fail.
2. **No UserRegistrationFailedEvent** — unlike login (which has UserLoginFailedEvent), registration has no structured failure event for monitoring.
3. **Event payload validation** — current UserRegisteredEvent has 11 fields but lacks schema evolution strategy documentation.
4. **Consumer documentation** — which downstream services should consume `iam.user.registered` is not explicitly documented.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Research best practices for user registration domain events in Event Sourcing + CQRS architecture
- [x] Objective 2: Evaluate the existing `UserRegisteredEvent` implementation against industry standards (CloudEvents, OCSF, Keycloak, Auth0)
- [x] Objective 3: Identify gaps in the current registration event flow (missing RegistrationEventRecorder, no failure event, consumer docs)
- [x] Objective 4: Research open source projects implementing similar user registration event patterns
- [x] Objective 5: Provide technical recommendations for improving the registration event infrastructure

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| UserRegisteredEvent domain event enrichment | User registration API/form UI design |
| RegistrationEventRecorder helper service | Email verification flow implementation |
| UserRegistrationFailedEvent for monitoring | OAuth2/SSO registration flows |
| Event store + outbox persistence pattern | GDPR data subject access request handling |
| Kafka topic `iam.user.registered` consumer patterns | Real-time notification delivery mechanisms |
| Schema versioning and evolution strategy | Database migration for event_store table changes |
| Downstream consumer documentation | Cross-service saga orchestration |
| Fire-and-forget resilience pattern | User profile enrichment after registration |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `user registration event`
- `UserRegisteredEvent`
- `domain event sourcing registration`
- `transactional outbox pattern`
- `CloudEvents user registration`

### 3.2 Secondary Keywords
- `CQRS registration handler event recording`
- `event-driven user onboarding`
- `registration failure event monitoring`
- `event schema versioning`
- `fire-and-forget event recording pattern`

### 3.3 Domain-Specific Terms
- `Transactional Outbox`: Pattern ensuring atomicity between database write and event publish by writing both in the same transaction
- `Event Envelope (CloudEvents)`: Standardized metadata wrapper for domain events (id, type, source, specversion, time, data)
- `Fire-and-forget`: Pattern where event recording failure does not affect the main business flow
- `Event Store`: Append-only log of domain events used for audit trail and event sourcing replay
- `IssuanceContext.REGISTRATION`: Discriminator value used in TokenIssuedEvent to identify registration-triggered token issuance

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"user registration event sourcing domain event best practices"` | General patterns | High |
| 2 | `"UserRegisteredEvent CQRS Spring Boot Kotlin"` | Implementation reference | High |
| 3 | `"transactional outbox user registration microservices"` | Architecture | High |
| 4 | `"CloudEvents user registration event schema"` | Schema design | Medium |
| 5 | `"Keycloak user registration event listener SPI"` | Open source reference | Medium |
| 6 | `"Auth0 post user registration hook action"` | Commercial reference | Medium |
| 7 | `"event sourcing registration failure event monitoring"` | Failure handling | Medium |
| 8 | `"Debezium outbox event sourcing registration Kafka"` | Infrastructure | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| UserRegisteredEvent | `auth.domain.event` | High | Existing domain event class with 11 enriched fields |
| RegisterHandler | `auth.application.command` | High | CQRS command handler that creates user + records event |
| RegisterCommand | `auth.application.command` | High | CQRS command with registration context (ipAddress, userAgent, correlationId) |
| EventService | `auth.application.event` | High | Central event recording service (event store + outbox, same TX) |
| LoginEventRecorder | `auth.application.event` | High | Pattern reference — fire-and-forget helper for login events |
| TokenEventRecorder | `auth.application.event` | High | Pattern reference — fire-and-forget helper for token events |
| EventEnvelope | `auth.domain.event` | Medium | CloudEvents-inspired wrapper with correlation ID and schema version |
| OutboxPoller | `auth.adapter.out.event` | Medium | Async Kafka relay with retry and FAILED state handling |
| KafkaEventPublisher | `auth.adapter.out.event` | Medium | Direct Kafka publisher for non-outbox events |
| TokenIssuedEvent (REGISTRATION context) | `auth.domain.event` | Medium | Complementary event for registration-triggered token issuance |
| CqrsAuthController.register() | `auth.adapter.in.web` | Medium | REST endpoint triggering RegisterHandler |
| SessionPromotionService | `auth.application` | Low | Anonymous session promotion during registration (DD-006) |

### 4.2 Existing Code Patterns

**Event Recording Pattern (established by LoginEventRecorder + TokenEventRecorder):**
```
1. @Component helper service injecting EventService
2. Public method accepting domain event + userId + correlationId
3. try/catch wrapper around EventService.record()
4. log.debug on success, log.warn on failure
5. Fire-and-forget — exception swallowed, main flow continues
```

**Domain Event Pattern (established by UserLoggedInEvent, TokenIssuedEvent):**
```
1. data class in auth.domain.event package
2. Implements DomainEvent interface (override val eventType: String)
3. Dot-notation eventType: "iam.{aggregate}.{action}" (e.g., iam.user.registered)
4. Enriched payload with full context (identity, device, session, timestamps)
5. Wrapped in EventEnvelope by EventService.record()
```

**RegisterHandler direct EventService call (CURRENT — not following helper pattern):**
```kotlin
// Current: EventService.record() called directly in RegisterHandler
// If EventService.record() throws, the @Transactional will rollback
// This is DIFFERENT from LoginHandler which uses fire-and-forget via LoginEventRecorder
eventService.record(
    aggregateType = "User",
    aggregateId = savedUser.id.value,
    event = UserRegisteredEvent(...),
    topic = "iam.user.registered",
    partitionKey = savedUser.id.value.toString(),
    correlationId = command.correlationId
)
```

### 4.3 Tech Stack Constraints
- Language: Kotlin 1.9+
- Framework: Spring Boot 4.x (Spring Framework 6.x)
- Database: PostgreSQL (event_store table, event_outbox table)
- Build tool: Gradle (Kotlin DSL)
- Event Store: Custom append-only table (EventStoreEntity)
- Message Queue: Apache Kafka (via spring-kafka)
- Outbox: Custom transactional outbox (OutboxPoller + EventOutboxEntity)
- CQRS: Custom eventsourcing-utils library (Command, CommandHandler interfaces)
- Event Envelope: CloudEvents-inspired custom EventEnvelope (specversion 1.0)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| EventService.record() | Service | `auth.application.event.EventService` | Central event recording — wraps event store + outbox |
| EventStorePort.append() | Port | `auth.application.port.out.EventStorePort` | Event store persistence |
| OutboxPort.insert() | Port | `auth.application.port.out.OutboxPort` | Outbox entry for async Kafka relay |
| DomainEvent interface | Interface | `auth.application.port.out.EventPublisher` | Base marker for all domain events |
| Kafka topic `iam.user.registered` | Topic | Kafka | Published via OutboxPoller |
| RegisterHandler | Command Handler | `auth.application.command.RegisterHandler` | Caller of event recording |
| CqrsAuthController | API | `auth.adapter.in.web.CqrsAuthController` | POST /api/auth/register endpoint |
| event_store table | DB Table | `auth.adapter.out.persistence.entity.EventStoreEntity` | Append-only event log |
| event_outbox table | DB Table | `auth.adapter.out.persistence.entity.EventOutboxEntity` | Transactional outbox |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Should RegisterHandler use a dedicated RegistrationEventRecorder (fire-and-forget) like LoginHandler, or is the current transactional approach correct for registration?
- [x] Q2: Should there be a `UserRegistrationFailedEvent` for monitoring failed registration attempts (duplicate username, invalid domain, etc.)?
- [x] Q3: What fields should a production-grade user registration event contain beyond the current 11 fields?
- [x] Q4: How do industry solutions (Keycloak, Auth0, OCSF) model user registration events?
- [x] Q5: What downstream consumers should react to `iam.user.registered` (notification, analytics, profile provisioning, audit)?
- [x] Q6: How should schema versioning work for UserRegisteredEvent when fields are added in the future?

### 5.2 Assumptions cần verify
- [x] A1: The current EventService.record() is called within RegisterHandler's @Transactional boundary — verified: yes, it is
- [x] A2: If EventService.record() fails, the user creation rolls back — verified: yes, because it's same transaction
- [x] A3: LoginEventRecorder follows fire-and-forget pattern — verified: yes, try/catch swallows exceptions

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive analysis of registration event patterns | ≥ 5 sources |
| Open source options | Evaluation of relevant projects | ≥ 3 repos evaluated |
| Gap analysis | All gaps in current implementation identified | All critical gaps identified |
| Business analysis | All use cases documented | All UCs documented |
| Technical spec | Agent-ready for implementation | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
