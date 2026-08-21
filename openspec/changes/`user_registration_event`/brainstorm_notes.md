---
type: brainstorm_notes
change: user_registration_event
date: 2025-08-21
selected_direction: "Approach B: Incremental Event Sourcing — Outbox-First with Deferred Full ES"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: User Registration Event — Production-Grade Event Sourcing

## Date
2025-08-21

## Context

The auth-service has a working CQRS pattern (`RegisterHandler` → `RegisterCommand` → `UserRegisteredEvent` → `KafkaEventPublisher`), but the event infrastructure is immature:

1. **Minimal event payload** — `UserRegisteredEvent(userId, username, domainCode)` missing email, fullName, phone, roles, ipAddress, userAgent
2. **Duplicate event definitions** — `UserRegisteredEvent` exists in BOTH `EventPublisher.kt` (eventType=`"user.registered"`) AND `AuthDomainEvents.kt` (eventType=`"USER_REGISTERED"`)
3. **Fire-and-forget publishing** — `KafkaEventPublisher.publish()` is called after persistence with no transactional guarantee; events can be lost if Kafka is down
4. **No event store** — events are ephemeral (only in Kafka retention window); no replay, no audit trail, no temporal queries
5. **No schema versioning** — no mechanism to evolve event payload without breaking consumers
6. **No idempotent consumers** — `ProfileKafkaListener` in account-service has no deduplication; Kafka retry could cause duplicate profiles
7. **Topic naming inconsistency** — `KafkaEventPublisher` uses `event.eventType` directly as topic (`"user.registered"`), but `ProfileKafkaListener` subscribes to `"iam.user.registered"` → **these don't match!**

**Source**: Research artifacts (6 files), pre_openspec.md (22 FRs, quality 78/100), codebase analysis.

**Critical Discovery — Topic Mismatch Bug** 🔴:
```
KafkaEventPublisher: topic = event.eventType → "user.registered"
ProfileKafkaListener: @KafkaListener(topics = ["iam.user.registered"])
                                                 ^^^^^^^^^^^^^^^^^
These DON'T match! ProfileKafkaListener is currently NOT receiving events.
```

This is a pre-existing production bug — the `TOPIC_PREFIX = "iam."` constant in `KafkaEventPublisher` is defined but never used. The topic is derived from `event.eventType` directly. The `EventPublisher.kt` port defines `eventType = "user.registered"` (no `iam.` prefix), while the `AuthDomainEvents.kt` command version uses `eventType = "USER_REGISTERED"` (SCREAMING_CASE, also no prefix). Meanwhile, `ProfileKafkaListener` subscribes to `"iam.user.registered"`. This mismatch means account-service profile auto-creation is silently not working.

## Questions Asked & Answers

- Q1: **What is the User aggregate boundary for Event Sourcing?**
  → A: For this iteration, the User aggregate should be scoped to **registration-related events only** (`UserRegisteredEvent`). Login/logout/session events are operational events with much higher volume — they should NOT be stored in the same event store initially. Expanding the aggregate to include login/session events would create massive event stores with poor replay performance. The aggregate boundary can be extended later with separate aggregate types.
  → **Rationale**: Registration events are ~100s-1000s per day. Login events are ~10,000s-100,000s per day. Mixing them would overwhelm the event store. Start narrow, expand later.

- Q2: **Should we use CloudEvents SDK or build a CloudEvents-inspired envelope?**
  → A: **CloudEvents-inspired custom envelope** (no SDK dependency). The CloudEvents SDK adds a dependency for what amounts to 6 fields (id, source, type, specversion, time, datacontenttype). A simple Kotlin data class achieves the same interoperability without the library overhead. The project already has minimal external dependencies.
  → **Rationale**: SDK adds `io.cloudevents:cloudevents-core`, `cloudevents-kafka`, `cloudevents-json-jackson` — 3 new transitive dependency trees for a data class pattern. Not worth it. Just follow the spec structurally.

- Q3: **Transactional Outbox vs. Kafka Transactions vs. Spring Modulith?**
  → A: **Custom Transactional Outbox**. Kafka transactions require idempotent producers + read_committed consumers — complex to set up and changes Kafka semantics globally. Spring Modulith's event publication log is elegant but would introduce a new framework dependency and uses `@ApplicationModuleListener` which changes the event dispatch model. A simple outbox table + polling scheduler is the most transparent and aligns with existing `@Scheduled` patterns (see `SessionCleanupScheduler`).
  → **Rationale**: Project already has `SessionCleanupScheduler` as a scheduling pattern. An outbox poller follows the exact same `@Scheduled` + `@Transactional` pattern. No new framework, no learning curve, full control.

- Q4: **Should RegisterHandler directly call EventStorePort and OutboxPort, or use an EventService intermediary?**
  → A: **Use an `EventService` intermediary** that encapsulates event creation → event store persistence → outbox insertion. This keeps `RegisterHandler` focused on domain logic and avoids injecting 3 new dependencies into every command handler. Other handlers (LoginHandler, RevokeSessionsHandler) can reuse `EventService.record(event)` when their events are also event-sourced later.
  → **Rationale**: Clean Architecture — `RegisterHandler` should orchestrate domain flow, not manage event infrastructure. `EventService` follows the same abstraction pattern as `TokenGenerator`.

- Q5: **Kafka topic strategy — single topic or per-event-type topics?**
  → A: **Separate topics per event type** (`iam.user.registered`, `iam.user.logged_in`, etc.). Reasons:
    1. `ProfileKafkaListener` already subscribes to `"iam.user.registered"` — changing to a consolidated topic would break its subscription model
    2. Consumers subscribe only to events they care about — no need for topic-side filtering
    3. Kafka partitioning per topic allows independent scaling (registration vs login volumes are very different)
    4. Dead letter topics are per-source-topic — `iam.user.registered.DLT` is self-explanatory
  → **Decision**: Keep per-event-type topics with `iam.` prefix namespace. Fix the existing topic mismatch by ensuring `KafkaEventPublisher` uses the `TOPIC_PREFIX` properly.

- Q6: **How to handle the duplicate UserRegisteredEvent?**
  → A: **Consolidate into `auth.domain.event` package** with a new enriched `UserRegisteredEvent` (v2). Steps:
    1. Create `UserRegisteredEvent` in `auth.domain.event` package with full payload + `eventType = "iam.user.registered"`
    2. Delete `UserRegisteredEvent` from `AuthDomainEvents.kt` (keep `UserLoggedInEvent`, `SessionRevokedEvent` — they're used by `LoginHandler` and `RevokeSessionsHandler`)
    3. Remove `UserRegisteredEvent` from `EventPublisher.kt` port file (keep `DomainEvent` interface, other event types)
    4. Update `RegisterHandler` import to use new event
    5. Add `domainEvent` package versioning support for future schema evolution

- Q7: **How to handle the `ProfileKafkaListener` contract change?**
  → A: The `ProfileKafkaListener` currently expects `{userId, username, domainCode}`. The enriched event will contain additional fields (email, fullName, phone, ipAddress, etc.). This is **backward-compatible** — Jackson deserialization ignores unknown properties by default (`@JsonIgnoreProperties(ignoreUnknown = true)`). The consumer DTO can be updated later to use new fields. No breaking change.

- Q8: **Should event_store use Snowflake ID or UUID for primary key?**
  → A: **Snowflake ID (BIGINT)** — consistent with all existing entities in the project. The `base-core` module provides Snowflake ID generation automatically for entities extending `BaseEntity`. Using UUID would break the ID convention and require a different base entity. However, the event envelope's `id` field (correlation/tracing ID) should use UUID since it's a logical identifier passed across services, not a database key.

- Q9: **How many Flyway migration files?**
  → A: **Single migration V11** containing `event_store`, `event_outbox`, and `processed_events` tables. These are all part of the same feature and should be deployed atomically. Current migrations go V1-V10.

- Q10: **Should we implement event replay and projections in this iteration?**
  → A: **No — defer to Phase 2**. The core value of this feature is: (1) enriched event payload, (2) reliable publishing (outbox), (3) event persistence (audit trail). Event replay and projections are useful but not critical for the immediate business need. They add significant complexity (snapshot management, projection rebuild logic) with limited short-term ROI. The event store table being in place enables replay later without re-implementing persistence.

## Approaches Considered

### Approach A: Full Event Sourcing (Axon-style)
- **Description**: Implement full Event Sourcing with aggregate roots, event-driven state derivation, snapshots, projections, and event replay. The `User` entity state would be derived entirely from replaying events.
- **Pros**:
  - Complete audit trail and temporal queries
  - True event sourcing — state is a function of events
  - Future-proof for complex aggregate behaviors
  - Snapshots for replay performance
- **Cons**:
  - **Massive scope increase** — 22 FRs already, this would add 10+ more
  - Requires rewriting `UserPersistenceAdapter` to derive state from events
  - Performance risk — aggregate state reconstruction on every read
  - `eventsourcing-utils` library doesn't support aggregate roots or event handlers (only Command/CommandHandler)
  - Would need to build or adopt a full ES framework
  - Existing JPA queries (`UserPort.existsByUsername/Email`, `UserPort.save`) don't work with event-derived state
  - **Estimated effort: 15-20 developer-days** (too much for current scope)

### Approach B: Incremental Event Sourcing — Outbox-First with Deferred Full ES ⭐ SELECTED
- **Description**: Enrich `UserRegisteredEvent` payload, add PostgreSQL event store for persistence (audit trail), implement transactional outbox for reliable publishing. Keep JPA/repository-based persistence for user state (no event-derived state). Event store acts as append-only audit log alongside traditional CRUD persistence. Defer replay, projections, snapshots to a future iteration.
- **Pros**:
  - **Incremental** — builds on existing CQRS infrastructure without breaking anything
  - **High value** — solves the 3 most critical gaps: enriched payload, reliable publishing, event persistence
  - **Low risk** — doesn't change the persistence model; adds new tables alongside existing ones
  - **Pragmatic** — delivers audit trail and reliability without full ES complexity
  - **Pattern alignment** — outbox poller reuses `SessionCleanupScheduler` pattern
  - **Compatible** — works with existing `eventsourcing-utils` Command/CommandHandler
  - **Estimated effort: 5-7 developer-days**
- **Cons**:
  - Not "true" event sourcing — state is still CRUD-based, not event-derived
  - Event store and user table can theoretically diverge (dual-write concern)
  - No replay capability in v1 (deferred)
  - Must maintain both event store and user table (but this is actually simpler in practice)

### Approach C: Spring Modulith Event Publication Log
- **Description**: Adopt Spring Modulith's `@ApplicationModuleListener` and JDBC event publication log for transactional outbox. Use Spring Modulith's Kafka externalization module for automatic event forwarding.
- **Pros**:
  - Native Spring integration — annotation-driven
  - Event publication log manages outbox lifecycle automatically
  - Well-tested by Spring team
  - Supports event completion tracking
- **Cons**:
  - **New framework dependency** — `spring-modulith-starter-jpa`, `spring-modulith-events-kafka`
  - Changes the event dispatch model to `ApplicationEventPublisher` — conflicts with existing `EventPublisher` port interface
  - Modulith assumes bounded contexts within a monolith — auth-service is a microservice
  - No event schema versioning or upcasters
  - Less control over outbox polling behavior
  - Would require refactoring `KafkaEventPublisher` and `SpringEventPublisher` adapters
  - **Estimated effort: 7-10 developer-days** (including framework migration)

## Selected Direction

**Approach B: Incremental Event Sourcing — Outbox-First with Deferred Full ES**

### Reasoning

1. **Risk vs. Value**: Approach B delivers 80% of the value (enriched events, reliability, audit trail) at 30% of the effort/risk of full ES (Approach A). The remaining 20% (replay, projections, snapshots) can be added incrementally in Phase 2 when there's a concrete business need.

2. **Codebase Alignment**: The existing codebase follows hexagonal architecture with ports/adapters. Approach B adds new ports (`EventStorePort`, `OutboxPort`) and adapters (`EventStorePersistenceAdapter`, `OutboxPersistenceAdapter`) following the exact same pattern as `UserPort`/`UserPersistenceAdapter`. Zero architectural deviation.

3. **Incremental Migration**: The outbox pattern replaces fire-and-forget publishing. Once the outbox is proven, other event types (`UserLoggedInEvent`, `SessionRevokedEvent`, `PermissionCha─ Modify CqrsAuthController to pass ipAddress/userAgent/correlationId
  ├── Internal API: GET /internal/events/{aggregateType}/{aggregateId}
  └── processed_events table for consumer deduplication

Phase 2 (future — when needed):
  ├── Event replay service
  ├── UserProjection (read model)
  ├── Snapshot support
  ├── Event upcaster framework
  └── Migrate other events (login, session) to outbox
```

## Pre-classifications (preliminary)
- Feature type: EXTEND (enhancing existing registration flow with event sourcing patterns)
- Flow type: Command (RegisterCommand → RegisterHandler → UserRegisteredEvent → Kafka)
- Affected modules:
  - `auth.domain.event` (NEW — event models)
  - `auth.application.command` (MODIFY — RegisterHandler, RegisterCommand; DELETE duplicate in AuthDomainEvents)
  - `auth.application.port.out` (MODIFY — EventPublisher.kt; ADD EventStorePort, OutboxPort)
  - `auth.application.event` (NEW or EXTEND — EventService)
  - `auth.adapter.out.persistence` (ADD — EventStorePersistenceAdapter, OutboxPersistenceAdapter)
  - `auth.adapter.out.persistence.entity` (ADD — EventStoreEntity, EventOutboxEntity)
  - `auth.adapter.out.persistence.repository` (ADD — EventStoreJpaRepository, EventOutboxJpaRepository)
  - `auth.adapter.out.event` (ADD — OutboxPoller, OutboxCleanupScheduler; MODIFY — KafkaEventPublisher topic fix)
  - `auth.adapter.in.web` (MODIFY — CqrsAuthController; ADD — EventStoreController)
  - `shared.config` (POSSIBLE MODIFY — KafkaConfig if new consumer error handling needed)
  - DB migrations (ADD — V11__event_sourcing_tables.sql)

## Codebase Investigation Findings

### Existing Patterns Verified

| Pattern | File | Observation |
|---------|------|-------------|
| Port/Adapter | `UserPort` → `UserPersistenceAdapter` | Clean hexagonal — new EventStorePort should follow identical pattern |
| Scheduled Job | `SessionCleanupScheduler` | `@Scheduled(cron)` + `@Transactional` — OutboxPoller should follow this pattern but with fixedDelay instead of cron |
| Kafka Producer | `KafkaEventPublisher` | `KafkaTemplate<String, String>` + `ObjectMapper` — OutboxPoller reuses this for actual Kafka send |
| Kafka Consumer | `PermissionChangedConsumer` | `@KafkaListener` + `@ConditionalOnProperty` — consumer dedup pattern applies here |
| Domain Event Interface | `DomainEvent { eventType: String }` | Base marker — EventEnvelope wraps DomainEvent with metadata |
| Event Types | `EventPublisher.kt` port file | 6 event types defined inline — UserRegisteredEvent will move to domain package |
| JPA Entity | `UserEntity`, `LoginSessionEntity` | Extend base entities from `base-core` — EventStoreEntity follows same pattern |
| Flyway | V1-V10 migrations | V11 is next available version number |
| Bean Conditional | `@ConditionalOnProperty` | Used by KafkaEventPublisher, KafkaConfig, CqrsAuthController |

### Critical Bug Found

**Topic mismatch** between producer and consumer:

```kotlin
// KafkaEventPublisher.kt — PRODUCER
override fun publish(event: DomainEvent) {
    val topic = event.eventType  // → "user.registered" (from EventPublisher.kt port)
    // OR → "USER_REGISTERED" (from AuthDomainEvents.kt)
    // Note: TOPIC_PREFIX = "iam." exists but is NEVER USED
    kafkaTemplate.send(topic, payload)
}

// ProfileKafkaListener.kt — CONSUMER (account-service)
@KafkaListener(topics = ["iam.user.registered"], groupId = "account-profile-group")
// Subscribes to "iam.user.registered" — but producer sends to "user.registered"
// → SILENT FAILURE: no messages received
```

**Fix**: Modify `KafkaEventPublisher` to properly prefix topics, OR update `UserRegisteredEvent.eventType` to `"iam.user.registered"`. Since `PermissionChangedEvent` already uses `"iam.permission.changed"` correctly, the issue is specifically with the older `UserRegisteredEvent` and `AuthDomainEvents` events that don't follow the `iam.` convention.

### RegisterHandler Current Flow

```
RegisterHandler.handle(command):
  1. userPort.existsByUsername()     ← validation
  2. userPort.existsByEmail()       ← validation
  3. domainPort.findByCodeAndActive() ← validation
  4. tokenGenerator.encodePassword() ← domain
  5. User(...)                       ← domain model creation
  6. userPort.save(user)            ← persistence (TX boundary)
  7. eventPublisher.publish(UserRegisteredEvent(...)) ← FIRE-AND-FORGET
  8. tokenGenerator.generateAuthResponse() ← token gen
  9. sessionPromotionService.promoteSession() ← optional
```

**After change** (step 7 becomes):
```
  7. eventService.record(             ← NEW: atomic with step 6
       aggregateType = "User",
       aggregateId = savedUser.id.value,
       event = UserRegisteredEvent(enriched payload),
       topic = "iam.user.registered",
       partitionKey = savedUser.id.value.toString()
     )
     // Internally:
     //   7a. Create EventEnvelope with correlationId, metadata
     //   7b. INSERT event_store (same TX)
     //   7c. INSERT event_outbox status=PENDING (same TX)
```

### RegisterCommand Enhancement

Current:
```kotlin
data class RegisterCommand(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
    val domainCode: String,
    val anonymousSessionId: String? = null,
    val anonymousTokenJti: String? = null
) : Command<RegisterResult>
```

Needs:
```kotlin
data class RegisterCommand(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
    val domainCode: String,
    val anonymousSessionId: String? = null,
    val anonymousTokenJti: String? = null,
    val ipAddress: String? = null,         // NEW — from HttpServletRequest
    val userAgent: String? = null,         // NEW — from HttpServletRequest
    val correlationId: String? = null      // NEW — from X-Correlation-ID header or generated
) : Command<RegisterResult>
```

### Event Payload Design (Final)

```kotlin
// auth/domain/event/EventEnvelope.kt
data class EventEnvelope<T>(
    val id: String,                    // UUID v4 — event ID
    val type: String,                  // "iam.user.registered" (with version suffix later)
    val source: String = "auth-service",
    val specversion: String = "1.0",
    val time: Instant,
    val correlationId: String,         // From X-Correlation-ID or auto-generated
    val schemaVersion: Int = 1,        // Event schema version
    val data: T
)

// auth/domain/event/UserRegisteredEvent.kt (replaces both duplicates)
data class UserRegisteredEvent(
    val userId: Long,
    val username: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val domainCode: String,
    val domainId: Long?,
    val status: String,               // "ACTIVE"
    val registrationSource: String,   // "DIRECT" | "SSO" | "ANONYMOUS_PROMOTION"
    val ipAddress: String?,
    val userAgent: String?
) : DomainEvent {
    override val eventType: String = "iam.user.registered"
}
```

### Database Schema Design (Final)

```sql
-- V11__event_sourcing_tables.sql

-- 1. Event Store — append-only immutable event log
CREATE TABLE event_store (
    id               BIGINT PRIMARY KEY,        -- Snowflake ID
    aggregate_type   VARCHAR(100) NOT NULL,      -- "User"
    aggregate_id     BIGINT NOT NULL,            -- userId
    event_type       VARCHAR(200) NOT NULL,      -- "iam.user.registered"
    schema_version   INT NOT NULL DEFAULT 1,     -- Schema version for upcasting
    sequence_number  BIGINT NOT NULL,            -- Per-aggregate ordering
    payload          JSONB NOT NULL,             -- EventEnvelope serialized
    metadata         JSONB NOT NULL DEFAULT '{}',-- Extra context
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_type, aggregate_id);
CREATE INDEX idx_event_store_type ON event_store(event_type);
CREATE INDEX idx_event_store_created ON event_store(created_at);
CREATE UNIQUE INDEX uq_event_store_agg_seq ON event_store(aggregate_type, aggregate_id, sequence_number);

-- 2. Event Outbox — transactional outbox for reliable Kafka publishing
CREATE TABLE event_outbox (
    id               BIGINT PRIMARY KEY,        -- Snowflake ID
    aggregate_type   VARCHAR(100) NOT NULL,
    aggregate_id     BIGINT NOT NULL,
    event_type       VARCHAR(200) NOT NULL,
    topic            VARCHAR(200) NOT NULL,      -- Kafka topic name
    partition_key    VARCHAR(200) NOT NULL,      -- Kafka partition key
    payload          JSONB NOT NULL,             -- Full EventEnvelope JSON
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count      INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created ON event_outbox(status, created_at);
CREATE INDEX idx_outbox_published ON event_outbox(published_at)
    WHERE published_at IS NOT NULL;

-- 3. Processed Events — consumer-side idempotency
CREATE TABLE processed_events (
    id               BIGINT PRIMARY KEY,        -- Snowflake ID
    event_id         UUID NOT NULL,             -- From EventEnvelope.id
    event_type       VARCHAR(200) NOT NULL,
    consumer_group   VARCHAR(100) NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_processed_event_consumer ON processed_events(event_id, consumer_group);
CREATE INDEX idx_processed_at ON processed_events(processed_at);
```

## Open Questions for Design Phase

- [OPEN] Should `EventService` also handle the direct `eventPublisher.publish()` call for non-outbox events (like AuditEvent, PermissionChangedEvent), or should those continue using the fire-and-forget path? **Recommendation**: Keep fire-and-forget for non-critical events initially; migrate them to outbox incrementally.
- [OPEN] Should the outbox poller use `@Scheduled(fixedDelay = 100)` or a more sophisticated approach like polling with `SELECT ... FOR UPDATE SKIP LOCKED` to support multiple service instances? **Recommendation**: Use `SELECT ... FOR UPDATE SKIP LOCKED` for instance-safe polling from the start.
- [OPEN] How to handle the transition period — old consumers (ProfileKafkaListener) expect `{userId, username, domainCode}` but new events will have enriched payload wrapped in `EventEnvelope`. Should the outbox payload be the `EventEnvelope` (with nested `data`) or just the event payload flat?
  - **Recommendation**: Outbox payload should be the full `EventEnvelope`. Update `ProfileKafkaListener` to parse the `data` field. This is a coordinated deployment — both services deploy together (they're in the same repo). Add `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility.
- [RESOLVED] Event store primary key type → Snowflake ID (consistent with project convention)
- [RESOLVED] Kafka topic strategy → per-event-type topics with `iam.` prefix
- [RESOLVED] Aggregate boundary → User registration only (login/session events excluded for now)
- [RESOLVED] CloudEvents → inspired-by (custom data class), no SDK dependency
- [RESOLVED] Phase 1 scope → outbox + event store + enriched payload; defer replay/projections

## Open Questions for URD Analysis

- No formal URD exists — this change originated from a user idea (enriched description). The pre_openspec.md serves as the requirement source.

## Key Design Decisions (auto-captured)

| DD-ID | Decision | Rationale | Impact |
|-------|----------|-----------|--------|
| DD-001 | Separate per-event-type Kafka topics (`iam.user.registered`, etc.) | ProfileKafkaListener already subscribes to per-type topics; independent scaling; clearer DLT routing | Topic naming, KafkaEventPublisher fix |
| DD-002 | Custom CloudEvents-inspired envelope (no SDK) | Avoids 3 new transitive dependencies for a simple data class pattern | EventEnvelope data class |
| DD-003 | EventService intermediary (not direct port injection in handlers) | Keeps handlers focused on domain logic; reusable across all command handlers | New EventService component |
| DD-004 | Snowflake ID for event_store/outbox primary keys | Consistent with project convention (all entities use Snowflake IDs from base-core) | JPA entity design |
| DD-005 | Single V11 migration for all 3 tables | Atomic feature deployment; tables are logically coupled | Flyway migration |
| DD-006 | Outbox poller with `SELECT ... FOR UPDATE SKIP LOCKED` | Instance-safe from day 1; no duplicate publishing across replicas | OutboxPoller implementation |
| DD-007 | User aggregate boundary = registration events only | Performance — login events are 100x volume; start narrow, expand later | event_store scope |
| DD-008 | Keep fire-and-forget for non-critical events initially | AuditEvent, PermissionChangedEvent don't need transactional guarantees for v1 | Migration scope |
| DD-009 | Outbox payload = full EventEnvelope JSON | Standard format; consumers extract data field; forward-compatible | Serialization strategy |
| DD-010 | Fix topic mismatch bug as part of this feature | Critical pre-existing bug; naturally resolved by event consolidation | KafkaEventPublisher, event types |

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Outbox poller adds latency to event delivery | LOW | LOW | 100ms polling interval; upgrade to CDC (Debezium) later if needed |
| Event store table grows unbounded | MED | LOW | Archive strategy for events >90 days; BRIN index on created_at for range queries |
| Dual-write between users table and event_store | LOW | MED | Both writes in same @Transactional; if either fails, entire TX rolls back |
| ProfileKafkaListener breaking change (payload format) | MED | MED | Both services in same repo; coordinated deployment; `@JsonIgnoreProperties(ignoreUnknown = true)` |
| Outbox poller duplicate publishing (multiple instances) | LOW | LOW | `SELECT ... FOR UPDATE SKIP LOCKED`; idempotent consumers with processed_events table |
| Event schema evolution without upcasters | LOW | LOW | v1 is forward-compatible via `@JsonIgnoreProperties`; upcaster framework deferred to Phase 2 |
