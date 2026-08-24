# Research Brief: User Login Event

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | User Login Event |
| **Ngày tạo** | 2025-08-22 |
| **Input source** | Name (feature name + description) |
| **Input content** | Build production-grade `UserLoggedInEvent` domain event following existing Event Sourcing + CQRS patterns — enriched payload with device/session/geo context, event store persistence via `EventService`, transactional outbox for Kafka relay, plus `UserLoginFailedEvent` for security monitoring |
| **Người yêu cầu** | Pipeline — auth-core-features |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already implements a mature Event Sourcing foundation:

1. **EventService** — intermediary service wrapping event store persist + outbox insert in caller's `@Transactional` boundary (DD-003)
2. **EventStorePort / OutboxPort** — outbound ports for append-only event log and transactional outbox
3. **EventEnvelope** — CloudEvents-inspired generic envelope with correlation ID, schema version, metadata (FR-006)
4. **OutboxPoller** — async Kafka relay with retry, timeout handling, and FAILED state (FR-007, FR-021, FR-022)
5. **TokenEventRecorder** — helper service for token lifecycle events (issuance, revocation, validation failure)
6. **KafkaEventPublisher** — direct Kafka publisher with retry for non-outbox events

**Established domain events:**
- `UserRegisteredEvent` → topic `iam.user.registered` (via EventService + outbox)
- `TokenIssuedEvent` → topic `iam.token.issued` (via TokenEventRecorder + EventService)
- `TokenRevokedEvent` → topic `iam.token.revoked` (via TokenEventRecorder + EventService)
- `TokenValidationFailedEvent` → topic `iam.token.validation-failed` (via TokenEventRecorder + EventService)

**Current gap in LoginHandler:**
The `LoginHandler` currently does **NOT** record a domain event to the event store or outbox for login actions. It only:
- Records a `LoginSessionEntity` via `LoginSessionService.recordLogin()` (DB table `login_sessions`)
- Emits `NewDeviceLoginEvent` via `ApplicationEventPublisher` (in-process Spring event, NOT persisted to event store)
- Records `TokenIssuedEvent` with `IssuanceContext.LOGIN` via `TokenEventRecorder` (covers token issuance but NOT login context)

This means:
- **No event store record** of login success/failure — audit trail incompleteness
- **No Kafka topic** for login events — downstream services (admin-service, analytics, SIEM) cannot react
- **Missing enriched payload** — no geo-location, session promotion status, MFA bypass reason, or login method in a single event
- **No `UserLoginFailedEvent`** — security monitoring lacks a structured event for failed login attempts
- **No correlation ID** threading for login flow

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Design enriched `UserLoggedInEvent` domain event with full authentication context (device, geo, session, MFA status)
- [x] Objective 2: Design `UserLoginFailedEvent` for failed login attempts with failure reason classification
- [x] Objective 3: Integrate with existing `EventService.record()` for transactional event store + outbox persistence
- [x] Objective 4: Define Kafka topics `iam.user.logged_in` and `iam.user.login_failed` with userId partitioning
- [x] Objective 5: Support downstream consumers: audit trail, anomaly detection, session analytics, SIEM integration
- [x] Objective 6: Follow existing patterns (TokenEventRecorder helper, IssuanceContext discriminator, CloudEvents envelope)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| `UserLoggedInEvent` domain event design + implementation | GeoIP lookup service implementation (use placeholder) |
| `UserLoginFailedEvent` domain event design + implementation | Real-time anomaly detection algorithms |
| `LoginEventRecorder` helper service (mirrors TokenEventRecorder) | Downstream consumer implementations (admin-service, SIEM) |
| EventService integration (event store + outbox) | UI dashboard for login analytics |
| Kafka topic design (`iam.user.logged_in`, `iam.user.login_failed`) | Push notification on login (separate feature) |
| LoginHandler integration for success events | OAuth2/SSO login events (handled in SsoAdapter separately) |
| LoginHandler integration for failure events | Historical data migration for existing login_sessions |
| Correlation ID threading from HTTP request to event | Rate limiting changes (already exists in LoginRateLimitService) |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `user login event`
- `authentication event sourcing`
- `login audit trail`
- `domain event login`
- `login event tracking`

### 3.2 Secondary Keywords
- `login failure event security`
- `authentication event driven architecture`
- `CQRS login command event`
- `login event Kafka`
- `session tracking event`
- `CloudEvents authentication`

### 3.3 Domain-Specific Terms
- `UserLoggedInEvent`: Domain event emitted on successful authentication via LoginHandler
- `UserLoginFailedEvent`: Domain event emitted on authentication failure with reason classification
- `LoginEventRecorder`: Helper service wrapping EventService calls for login events (mirrors TokenEventRecorder pattern)
- `IssuanceContext.LOGIN`: Discriminator value for token issuance during login flow
- `EventEnvelope`: CloudEvents-inspired wrapper with correlation ID and schema version
- `Transactional Outbox`: Pattern ensuring event persistence in same DB transaction as state change, relayed to Kafka asynchronously

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"user login event domain driven design best practices"` | General patterns | High |
| 2 | `"authentication event sourcing spring boot Kafka"` | Implementation reference | High |
| 3 | `"login event open source GitHub security monitoring"` | Open Source | High |
| 4 | `"CloudEvents authentication event schema"` | Schema design | Medium |
| 5 | `"login audit trail event driven microservices"` | Architecture | Medium |
| 6 | `"login failure event SIEM integration patterns"` | Security | Medium |
| 7 | `"Keycloak login event model"` | Reference implementation | Medium |
| 8 | `"Auth0 log events schema"` | Industry standard | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| LoginHandler | `auth.application.command` | High | CQRS command handler — main integration point for recording login events |
| LoginSessionService | `auth.application` | High | Already records login sessions with device info — overlapping data that can be sourced for event payload |
| TokenEventRecorder | `auth.application.event` | High | Pattern reference — same helper service pattern to follow for LoginEventRecorder |
| EventService | `auth.application.event` | High | Core event recording service — event store + outbox in single transaction |
| AuditLogService | `shared.audit` | Medium | Already logs LOGIN_SUCCESS/LOGIN_FAILED audit actions — complementary, not replacing |
| NewDeviceLoginEvent | `auth.application.event` | Medium | In-process Spring event for new device detection — emitted by LoginSessionService |
| LoginRateLimitService | `auth.application` | Medium | Records failed attempts for rate limiting — source of failure context data |
| UserRegisteredEvent | `auth.domain.event` | High | Pattern reference — enriched domain event recorded via EventService |
| TokenIssuedEvent | `auth.domain.event` | High | Already records token issuance on login — complementary event (token vs auth context) |
| CqrsAuthController | `auth.adapter.in.web` | Medium | Controller dispatching LoginCommand — source of HTTP request context |

### 4.2 Existing Code Patterns

**Event Recording Pattern (established by UserRegisteredEvent + TokenIssuedEvent):**
1. Domain event data class implements `DomainEvent` interface with `eventType` property
2. Located in `auth.domain.event` package
3. Recorded via helper service (e.g., `TokenEventRecorder`) that delegates to `EventService.record()`
4. Helper service catches exceptions — event recording failure never blocks primary flow
5. `EventService.record()` creates `EventEnvelope`, persists to event store + outbox in same `@Transactional`
6. `OutboxPoller` relays to Kafka asynchronously with retry and timeout handling

**LoginHandler Flow:**
1. Validate credentials → 2. Check account lock → 3. CAPTCHA check → 4. Password verify → 5. Reset failed logins → 6. Password expiry → 7. MFA checkpoint → 8. Session policy → 9. Generate tokens (TokenIssuedEvent recorded here) → 10. Record login session → 11. Anonymous promotion → 12. Return LoginResult.Success

**Key observation:** Step 9 already records `TokenIssuedEvent` with `IssuanceContext.LOGIN`. The new `UserLoggedInEvent` captures the **authentication context** (who logged in, from where, which device, MFA bypassed?, session promotion?) while `TokenIssuedEvent` captures the **token context** (JTI, roles, permissions, expiry). They are complementary.

### 4.3 Tech Stack Constraints
- Language: Kotlin 1.9+
- Framework: Spring Boot 3.x (starter conventions)
- Database: PostgreSQL with Flyway migrations
- Cache: Redis (Caffeine L1 + Redis L2)
- Build tool: Gradle (Kotlin DSL) with convention plugins
- Event store: Custom append-only table (`event_store`)
- Outbox: Custom transactional outbox table (`event_outbox`)
- Message broker: Kafka (via `spring-kafka`)
- Serialization: Jackson (JSON) with Kotlin module
- CQRS: `eventsourcing-utils` library (`CommandHandler<C, R>` interface)
- Base entity: `SnowflakePersistentAuditableEntity` from `base-core`

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `LoginHandler.handle()` | Command Handler | `auth.application.command.LoginHandler` | Insert event recording after successful auth + after failed auth |
| `EventService.record()` | Application Service | `auth.application.event.EventService` | Core event persistence (event store + outbox) |
| `LoginSessionService.recordLogin()` | Application Service | `auth.application.LoginSessionService` | Source of device/session metadata |
| `AuditLogService.logEvent()` | Application Service | `shared.audit.AuditLogService` | Existing audit log — complementary |
| `DomainEvent` interface | Port | `auth.application.port.out.DomainEvent` | Interface for domain events |
| `event_store` table | Database | Flyway migration | Append-only event log |
| `event_outbox` table | Database | Flyway migration | Transactional outbox entries |
| `OutboxPoller` | Infrastructure | `auth.adapter.out.event.OutboxPoller` | Kafka relay for outbox events |
| Kafka topic `iam.user.logged_in` | Message Broker | Kafka | New topic for login success events |
| Kafka topic `iam.user.login_failed` | Message Broker | Kafka | New topic for login failure events |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: What fields should a production-grade login event contain? (device info, geo, MFA status, session promotion?)
- [x] Q2: How do industry leaders (Keycloak, Auth0, AWS Cognito) structure login events?
- [x] Q3: Should login success and failure be separate event types or one event with status discriminator?
- [x] Q4: What failure reason classifications are needed? (INVALID_CREDENTIALS, ACCOUNT_LOCKED, CAPTCHA_FAILED, etc.)
- [x] Q5: How to avoid duplicate event recording with existing TokenIssuedEvent (IssuanceContext.LOGIN)?
- [x] Q6: Should the LoginEventRecorder be a separate class or merged into TokenEventRecorder?
- [x] Q7: What downstream consumers need login events and what payload fields do they require?

### 5.2 Assumptions cần verify
- [x] A1: LoginHandler is the single entry point for password-based login (verified: yes, CqrsAuthController dispatches to LoginHandler)
- [x] A2: EventService.record() handles event store + outbox atomically in same transaction (verified: yes, DD-003 pattern)
- [x] A3: TokenIssuedEvent already records on login success (verified: yes, via TokenEventRecorder in tokenGenerator.generateAuthResponse)
- [x] A4: AuditLogService already logs LOGIN_SUCCESS/LOGIN_FAILED (verified: yes, AuditAction enum has both)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive analysis of login event patterns | ≥ 5 sources analyzed |
| Open source options | Evaluate how open source IDPs handle login events | ≥ 3 projects evaluated |
| Gap analysis | All integration gaps with current system identified | All critical gaps documented |
| Business analysis | All use cases for login events documented | ≥ 3 UCs with flows |
| Technical spec | Agent-ready specification for implementation | Classes, fields, integration points defined |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
