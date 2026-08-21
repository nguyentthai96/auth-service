# Research Brief: User Registration Event

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | User Registration Event |
| **Ngày tạo** | 2025-08-21 |
| **Input source** | Idea (enriched description) |
| **Input content** | Build production-grade Authentication Service based on Event Sourcing — focusing on the UserRegisteredEvent domain event, its lifecycle, downstream consumers, and integration with CQRS command/query handlers |
| **Người yêu cầu** | Pipeline — auth-core-features |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already implements a CQRS pattern with `RegisterHandler` → `RegisterCommand` → `UserRegisteredEvent`. However, the current `UserRegisteredEvent` is minimal (userId, username, domainCode) and published via dual adapters (SpringEventPublisher for logging-only, KafkaEventPublisher for distributed messaging). The event lacks:

1. **Rich event payload** — missing email, roles, IP address, user-agent metadata for downstream consumers
2. **Event schema versioning** — no mechanism for backward-compatible evolution of event payloads
3. **Event store persistence** — events are fire-and-forget via Kafka; no event store for replay/projection rebuilding
4. **Idempotent consumer guarantees** — no deduplication key or correlation ID for consumers
5. **Downstream orchestration** — after registration, profile creation (account-service), welcome email, default role assignment, and audit trail need coordinated event-driven handling

This research focuses on designing a production-grade `UserRegisteredEvent` within the Event Sourcing architecture, ensuring the event carries sufficient context, is versioned, stored immutably, and consumed reliably by downstream services.

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Design rich `UserRegisteredEvent` schema with all required fields for downstream consumers
- [x] Objective 2: Define event schema versioning strategy (backward/forward compatibility)
- [x] Objective 3: Evaluate event store options (Kafka as event store vs. dedicated event store vs. RDBMS event table)
- [x] Objective 4: Design idempotent consumer patterns with correlation IDs and deduplication
- [x] Objective 5: Map all downstream consumers and their data requirements from the registration event
- [x] Objective 6: Ensure alignment with existing `eventsourcing-utils` library and CQRS patterns in the codebase

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| UserRegisteredEvent payload design | Full event sourcing for all aggregates (login, session, etc.) |
| Event versioning and schema evolution | Event replay/projection rebuild infrastructure |
| Kafka topic design and partitioning | Kafka cluster setup/operations |
| Downstream consumer contracts | Implementing all downstream consumers |
| Integration with existing RegisterHandler | Refactoring AuthService (legacy non-CQRS path) |
| Event store table design (PostgreSQL) | Dedicated event store database (EventStoreDB) |
| Idempotent consumer patterns | Saga/Process Manager implementation |
| Audit trail via event | Full audit subsystem redesign |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `user registration event`
- `UserRegisteredEvent`
- `event sourcing domain event`
- `CQRS registration command handler`
- `domain event design patterns`

### 3.2 Secondary Keywords
- `event schema versioning`
- `Kafka domain event publishing`
- `event store PostgreSQL`
- `idempotent consumer pattern`
- `event-driven user provisioning`
- `CloudEvents specification`

### 3.3 Domain-Specific Terms
- `Domain Event`: An immutable record of something that happened in the domain — past tense, represents a fact
- `Event Store`: Append-only storage of domain events, serving as the system of record for aggregate state
- `Command Handler`: CQRS write-side handler that validates commands and produces domain events
- `Projection`: Read-model built by replaying events — optimized for query patterns
- `Correlation ID`: UUID linking related events across an asynchronous flow for tracing
- `Event Upcaster`: Mechanism to transform old event versions to current schema during replay

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"user registration event sourcing domain event best practices"` | General | High |
| 2 | `"event sourcing authentication service open source GitHub"` | Open Source | High |
| 3 | `"domain event schema versioning Kafka"` | Architecture | High |
| 4 | `"CloudEvents specification domain event envelope"` | Standards | Medium |
| 5 | `"Axon Framework event sourcing Spring Boot CQRS"` | Framework | Medium |
| 6 | `"event store PostgreSQL vs EventStoreDB vs Kafka"` | Comparison | Medium |
| 7 | `"idempotent consumer pattern Kafka Spring Boot"` | Integration | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| RegisterHandler (CQRS) | `auth.application.command.RegisterHandler` | High | Publishes UserRegisteredEvent after successful registration |
| AuthDomainEvents | `auth.application.command.AuthDomainEvents` | High | Contains UserRegisteredEvent, UserLoggedInEvent, SessionRevokedEvent data classes |
| EventPublisher port | `auth.application.port.out.EventPublisher` | High | Outbound port interface + DomainEvent marker + multiple event types |
| KafkaEventPublisher | `auth.adapter.out.event.KafkaEventPublisher` | High | Primary adapter — publishes to Kafka with retry (3 attempts, exponential backoff) |
| SpringEventPublisher | `auth.adapter.out.event.SpringEventPublisher` | Medium | Fallback adapter — logs events only (Phase 1 placeholder) |
| PermissionChangedConsumer | `auth.adapter.in.kafka.PermissionChangedConsumer` | Medium | Example Kafka consumer — invalidates permission cache |
| AuditLogService | `shared.audit.AuditLogService` | Medium | Publishes AuditEvent via EventPublisher for audit trail persistence |
| AuthService.register() | `auth.application.AuthService` | Medium | Legacy (non-CQRS) registration path — does NOT publish events |
| CqrsAuthController | `auth.adapter.in.web.CqrsAuthController` | Medium | Dispatches RegisterCommand to RegisterHandler |
| eventsourcing-utils | External library | High | Provides Command, CommandHandler, Query, QueryHandler base types |

### 4.2 Existing Code Patterns

**CQRS Command/Handler Pattern:**
- Commands implement `com.ntt.eventsourcingutils.lib.cqrs.command.Command<R>`
- Handlers implement `com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler<C, R>`
- Controller → Handler → Port → Adapter (Hexagonal Architecture)

**Event Publishing Pattern:**
- `DomainEvent` interface with `eventType: String` property
- `EventPublisher.publish(event: DomainEvent)` outbound port
- Dual adapters: `KafkaEventPublisher` (primary, `@Primary`, `@ConditionalOnProperty`) and `SpringEventPublisher` (fallback)
- Events published AFTER persistence (not transactional outbox — fire-and-forget)
- Kafka topic derived from `event.eventType` (e.g., `"user.registered"` → Kafka topic `"user.registered"`)

**Existing Event Types (in `EventPublisher.kt` port):**
- `UserRegisteredEvent(userId, username, domainCode)` — eventType: `"user.registered"`
- `PermissionChangedEvent(userId?, domainId?)` — eventType: `"iam.permission.changed"`
- `SsoProvisionedEvent(userId, provider, email?, domainCode)` — eventType: `"iam.user.sso_provisioned"`
- `AccountDeactivatedEvent(userId, reason?)` — eventType: `"iam.account.deactivated"`
- `AccountDeletedEvent(userId)` — eventType: `"iam.account.deleted"`
- `AuditEvent(userId?, action, entityType?, entityId?, ipAddress?, userAgent?, details?)` — eventType: `"iam.audit.event"`

**Duplicate Event Definitions (in `AuthDomainEvents.kt`):**
- `UserRegisteredEvent(userId, username, domainCode)` — eventType: `"USER_REGISTERED"` (different naming from port definition)
- `UserLoggedInEvent(userId, domainCode)` — eventType: `"USER_LOGGED_IN"`
- `SessionRevokedEvent(userId, revokedCount)` — eventType: `"SESSIONS_REVOKED"`

⚠️ **Assumption**: There are TWO definitions of `UserRegisteredEvent` — one in `port.out.EventPublisher.kt` (eventType = `"user.registered"`) and another in `command.AuthDomainEvents.kt` (eventType = `"USER_REGISTERED"`). The `RegisterHandler` imports from `command.AuthDomainEvents`. This duplication needs to be resolved.

### 4.3 Tech Stack Constraints

- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.x with Spring Security, Spring Data JPA, Spring Kafka
- **Database**: PostgreSQL 17+ (Flyway migrations, Snowflake ID generation via `base-core`)
- **Cache**: Redis (distributed) + Caffeine (local L1)
- **Messaging**: Kafka (domain events, permission change events)
- **Build tool**: Gradle Kotlin DSL with `ntt.spring-app-conventions` plugin
- **Base module**: `base-core` (platform BOM: `com.ntt:platform`, starters: `base-web-starter`, `base-data-starter`, `base-security-starter`, `base-observability-starter`)
- **CQRS library**: `com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT`
- **ID generation**: Snowflake IDs (BIGINT, configured via `base-core.snowflake`)
- **Serialization**: Jackson (ObjectMapper for Kafka payloads)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `RegisterHandler` | Command Handler | `auth.application.command.RegisterHandler` | Produces UserRegisteredEvent after saving user |
| `EventPublisher` | Outbound Port | `auth.application.port.out.EventPublisher` | Interface for publishing domain events |
| `KafkaEventPublisher` | Adapter | `auth.adapter.out.event.KafkaEventPublisher` | Kafka adapter with retry policy |
| `KafkaTemplate<String, String>` | Infrastructure | Spring Kafka | JSON string payloads via ObjectMapper |
| `PermissionChangedConsumer` | Kafka Consumer | `auth.adapter.in.kafka.PermissionChangedConsumer` | Example of Kafka consumer pattern |
| `AuditLogService` | Service | `shared.audit.AuditLogService` | Publishes AuditEvent for audit trail |
| `account-service` | External Service | `account-service/` (sibling module) | Consumes user events for profile/device/preference creation |
| `ProfileKafkaListener` | Kafka Consumer | `account-service/.../ProfileKafkaListener` | Account-service Kafka listener for profile sync |
| `UserEntity` (JPA) | Persistence Entity | `rbac.adapter.out.persistence.entity.UserEntity` | Users table — JPA entity for user records |
| `UserPort` / `User` | Domain Model | `auth.application.port.out.UserPort`, `auth.domain.model.User` | Clean domain model (no JPA imports) |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: What should the enriched `UserRegisteredEvent` payload contain to satisfy all downstream consumers?
- [x] Q2: How to version event schemas for backward/forward compatibility (upcasters, CloudEvents envelope)?
- [x] Q3: Should events be stored in a PostgreSQL event_store table (append-only) or rely solely on Kafka retention?
- [x] Q4: How to resolve the duplicate `UserRegisteredEvent` definitions (port vs command package)?
- [x] Q5: What event naming convention to use (dot-notation `user.registered` vs snake_case `USER_REGISTERED`)?
- [x] Q6: How to ensure exactly-once publishing (transactional outbox vs. Kafka transactions)?
- [x] Q7: What correlation ID / deduplication strategy to use for idempotent consumers?

### 5.2 Assumptions cần verify

- [x] A1: `eventsourcing-utils` library provides only Command/Query base types — no event store implementation
- [x] A2: Kafka is the primary event transport (confirmed by `KafkaEventPublisher`)
- [x] A3: `account-service` `ProfileKafkaListener` is intended to consume `UserRegisteredEvent`
- [x] A4: Events are currently fire-and-forget (no transactional outbox)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Thorough analysis of event sourcing patterns for user registration | ≥ 5 sources |
| Open source options | Evaluated frameworks/libraries for event sourcing on JVM | ≥ 3 repos evaluated |
| Gap analysis | Identified gaps between current implementation and production-grade requirements | All critical gaps identified |
| Business analysis | All use cases around user registration event documented | All UCs documented |
| Technical spec | Agent-ready specification for implementation | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
