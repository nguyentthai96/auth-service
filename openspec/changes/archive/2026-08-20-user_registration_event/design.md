# Design: user-registration-event

_Generated: 2025-08-21_
_Profile: Command | N/A (no factory) | EXTEND_
_Brainstorm Direction: Incremental Event Sourcing — Outbox-First with Deferred Full ES_

---

## 1. Architecture Overview

### 1.1 Pattern
- **Architecture**: Clean Architecture (Hexagonal) — consistent with existing codebase
- **Package Structure**: follows existing `adapter/in/web`, `adapter/out/persistence`, `adapter/out/event`, `application`, `domain/model` + new `domain/event`
- **Event Pattern**: Transactional Outbox (dual-write to event_store + event_outbox in same TX, relay to Kafka via poller)
- **CQRS**: Command-side only (existing pattern — RegisterCommand → RegisterHandler)

### 1.2 Component Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│                      REQUEST FLOW (REGISTRATION)                      │
│                                                                       │
│  Client → POST /api/auth/register                                     │
│      │                                                                │
│      ▼                                                                │
│  ┌─────────────────┐                                                  │
│  │CqrsAuthController│─── ipAddress, userAgent, X-Correlation-ID       │
│  └────────┬────────┘                                                  │
│           │ RegisterCommand (+ ipAddress, userAgent, correlationId)    │
│           ▼                                                           │
│  ┌─────────────────┐                                                  │
│  │ RegisterHandler  │ @Transactional                                  │
│  │ (CommandHandler) │                                                 │
│  │                  │──1. userPort.existsByUsername()                  │
│  │                  │──2. userPort.existsByEmail()                    │
│  │                  │──3. domainPort.findByCodeAndActive()            │
│  │                  │──4. tokenGenerator.encodePassword()             │
│  │                  │──5. userPort.save(user)                         │
│  │                  │──6. eventService.record(event)  ← NEW           │
│  │                  │      ├── EventEnvelope creation                 │
│  │                  │      ├── INSERT event_store (same TX)           │
│  │                  │      └── INSERT event_outbox PENDING (same TX)  │
│  │                  │──7. tokenGenerator.generateAuthResponse()       │
│  │                  │──8. sessionPromotionService (optional)          │
│  └─────────────────┘                                                  │
│                                                                       │
│  ┌──────────────────────────────────────────────────┐                 │
│  │              OUTBOX RELAY (ASYNC)                 │                 │
│  │                                                   │                │
│  │  OutboxPoller (@Scheduled fixedDelay=100ms)       │                │
│  │      │                                            │                │
│  │      ├── SELECT ... WHERE status='PENDING'        │                │
│  │      │   FOR UPDATE SKIP LOCKED                   │      al userId: Long,
    val username: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val domainCode: String,
    val domainId: Long?,
    val status: String,
    val registrationSource: String,
    val ipAddress: String?,
    val userAgent: String?
) : DomainEvent {
    override val eventType: String = "iam.user.registered"
}
```

#### auth.application.event.EventService (DD-003)

```kotlin
package com.ntt.authservice.auth.application.event

/**
 * Intermediary service for transactional event recording.
 * Encapsulates: event envelope creation → event store persist → outbox insert.
 * All within the caller's existing @Transactional boundary.
 */
@Component
class EventService(
    private val eventStorePort: EventStorePort,
    private val outboxPort: OutboxPort,
    private val objectMapper: ObjectMapper,
    @Value("${app.event.source:auth-service}") private val eventSource: String
) {
    fun <T : DomainEvent> record(
        aggregateType: String,
        aggregateId: Long,
        event: T,
        topic: String,
        partitionKey: String,
        correlationId: String? = null
    ) {
        val envelope = EventEnvelope(
            type = event.eventType,
            source = eventSource,
            correlationId = correlationId ?: UUID.randomUUID().toString(),
            data = event
        )
        val nextSeq = eventStorePort.getNextSequenceNumber(aggregateType, aggregateId)
        
        // 1. Persist to event store (same TX)
        eventStorePort.append(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = event.eventType,
            schemaVersion = envelope.schemaVersion,
            sequenceNumber = nextSeq,
            payload = objectMapper.writeValueAsString(envelope),
            metadata = "{}"
        )
        
        // 2. Insert outbox entry (same TX)
        outboxPort.insert(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = event.eventType,
            topic = topic,
            partitionKey = partitionKey,
            payload = objectMapper.writeValueAsString(envelope)
        )
    }
}
```

#### auth.application.port.out.EventStorePort (FR-004)

```kotlin
package com.ntt.authservice.auth.application.port.out

interface EventStorePort {
    fun append(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        schemaVersion: Int,
        sequenceNumber: Long,
        payload: String,
        metadata: String
    )
    
    fun findByAggregate(aggregateType: String, aggregateId: Long): List<Any>
    fun getNextSequenceNumber(aggregateType: String, aggregateId: Long): Long
}
```

#### auth.application.port.out.OutboxPort (FR-007)

```kotlin
package com.ntt.authservice.auth.application.port.out

interface OutboxPort {
    fun insert(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        topic: String,
        partitionKey: String,
        payload: String
    )
    fun findPendingForUpdate(limit: Int): List<Any>
    fun markPublished(id: Long)
    fun markFailed(id: Long)
    fun incrementRetryCount(id: Long)
}
```

#### auth.adapter.out.persistence.entity.EventStoreEntity (FR-004)

```kotlin
package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "event_store")
class EventStoreEntity(
    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = "",
    
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long = 0,
    
    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = "",
    
    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int = 1,
    
    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Long = 0,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    var metadata: String = "{}",
    
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) : SnowflakePersistentAuditableEntity()
```

#### auth.adapter.out.persistence.entity.EventOutboxEntity (FR-007)

```kotlin
@Entity
@Table(name = "event_outbox")
class EventOutboxEntity(
    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = "",
    
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long = 0,
    
    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = "",
    
    @Column(name = "topic", nullable = false, length = 200)
    var topic: String = "",
    
    @Column(name = "partition_key", nullable = false, length = 200)
    var partitionKey: String = "",
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    
    @Column(name = "status", nullable = false, length = 20)
    var status: String = "PENDING",
    
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    
    @Column(name = "published_at")
    var publishedAt: Instant? = null
) : SnowflakePersistentAuditableEntity()
```

#### auth.adapter.out.persistence.entity.ProcessedEventEntity (FR-008)

```kotlin
@Entity
@Table(name = "processed_events")
class ProcessedEventEntity(
    @Column(name = "event_id", nullable = false)
    var eventId: String = "",
    
    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = "",
    
    @Column(name = "consumer_group", nullable = false, length = 100)
    var consumerGroup: String = "",
    
    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
) : SnowflakePersistentAuditableEntity()
```

#### auth.adapter.out.event.OutboxPoller (FR-007, FR-021, FR-022)

```kotlin
@Component
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
class OutboxPoller(
    private val outboxRepository: EventOutboxJpaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("${app.event.outbox.batch-size:50}") private val batchSize: Int,
    @Value("${app.event.outbox.max-retries:3}") private val maxRetries: Int,
    @Value("${app.event.outbox.kafka-timeout-ms:5000}") private val kafkaTimeoutMs: Long
) {
    // @Scheduled(fixedDelayString = "${app.event.outbox.poll-interval-ms:100}")
    // @Transactional
    // Polls PENDING entries → sends to Kafka → marks PUBLISHED
    // Uses SELECT ... FOR UPDATE SKIP LOCKED
    // On failure: increment retry_count, log error
    // retry_count >= maxRetries → status = FAILED
}
```

### 2.3 Modified Classes

#### RegisterCommand.kt — add event metadata fields

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
    val correlationId: String? = null      // NEW — from X-Correlation-ID header
) : Command<RegisterResult>
```

#### RegisterHandler.kt — replace eventPublisher with eventService

```kotlin
// BEFORE (L69-73):
eventPublisher.publish(
    UserRegisteredEvent(userId = savedUser.id.value, username = savedUser.username, domainCode = command.domainCode)
)

// AFTER:
eventService.record(
    aggregateType = "User",
    aggregateId = savedUser.id.value,
    event = com.ntt.authservice.auth.domain.event.UserRegisteredEvent(
        userId = savedUser.id.value,
        username = savedUser.username,
        email = command.email,
        fullName = command.fullName,
        phone = command.phone,
        domainCode = command.domainCode,
        domainId = domain.id,
        status = "ACTIVE",
        registrationSource = determineRegistrationSource(command),
        ipAddress = command.ipAddress,
        userAgent = command.userAgent
    ),
    topic = "iam.user.registered",
    partitionKey = savedUser.id.value.toString(),
    correlationId = command.correlationId
)
```

#### CqrsAuthController.kt — pass ipAddress/userAgent/correlationId

```kotlin
// In register() method:
val command = RegisterCommand(
    // ... existing fields ...
    ipAddress = extractClientIp(request),              // NEW
    userAgent = httpRequest.getHeader("User-Agent"),    // NEW
    correlationId = httpRequest.getHeader("X-Correlation-ID")  // NEW
)
```
Note: `register()` method needs `HttpServletRequest` parameter added.

#### KafkaEventPublisher.kt — fix topic mismatch (FR-010)

```kotlin
// BEFORE (L47):
val topic = event.eventType  // "user.registered" — WRONG

// AFTER:
val topic = if (event.eventType.startsWith("iam.")) {
    event.eventType
} else {
    TOPIC_PREFIX + event.eventType
}
```

#### AuthDomainEvents.kt — remove UserRegisteredEvent (FR-002)

```kotlin
// REMOVE: UserRegisteredEvent data class (L8-13)
// KEEP: UserLoggedInEvent (L15-19), SessionRevokedEvent (L21-26)
```

#### EventPublisher.kt — remove UserRegisteredEvent (FR-002)

```kotlin
// REMOVE: UserRegisteredEvent data class (L19-25)
// KEEP: DomainEvent interface, EventPublisher interface, other event types
```

---

## 3. Database Design

### 3.1 New Tables (V11 Migration)

#### event_store

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK (Snowflake) | Event record ID |
| aggregate_type | VARCHAR(100) | NOT NULL | "User" |
| aggregate_id | BIGINT | NOT NULL | userId |
| event_type | VARCHAR(200) | NOT NULL | "iam.user.registered" |
| schema_version | INT | NOT NULL DEFAULT 1 | Schema version |
| sequence_number | BIGINT | NOT NULL | Per-aggregate ordering |
| payload | JSONB | NOT NULL | EventEnvelope serialized |
| metadata | JSONB | NOT NULL DEFAULT '{}' | Extra context |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Event timestamp |

**Indexes**: `idx_event_store_aggregate(aggregate_type, aggregate_id)`, `idx_event_store_type(event_type)`, `idx_event_store_created(created_at)`, `uq_event_store_agg_seq(aggregate_type, aggregate_id, sequence_number) UNIQUE`

#### event_outbox

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK (Snowflake) | Outbox record ID |
| aggregate_type | VARCHAR(100) | NOT NULL | Routing info |
| aggregate_id | BIGINT | NOT NULL | Routing info |
| event_type | VARCHAR(200) | NOT NULL | Event type |
| topic | VARCHAR(200) | NOT NULL | Kafka topic |
| partition_key | VARCHAR(200) | NOT NULL | Kafka partition key |
| payload | JSONB | NOT NULL | Full EventEnvelope JSON |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | PENDING/PUBLISHED/FAILED |
| retry_count | INT | NOT NULL DEFAULT 0 | Failure count |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Insert time |
| published_at | TIMESTAMPTZ | NULL | Publish time |

**Indexes**: `idx_outbox_status_created(status, created_at)`, `idx_ounges)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
app:
  security:
    cqrs:
      enabled: true  # CqrsAuthController active
```

---

## 6. Transaction Flow (Updated)

| Step | Component | Action | TX Boundary |
|------|-----------|--------|-------------|
| 1 | Client | POST /api/auth/register | — |
| 2 | CqrsAuthController | Create RegisterCommand (+ ipAddress, userAgent, correlationId) | — |
| 3 | RegisterHandler | Validate uniqueness (username, email) | @Transactional START |
| 4 | RegisterHandler | Validate domain exists | Same TX |
| 5 | RegisterHandler | Create User domain model | Same TX |
| 6 | RegisterHandler | `userPort.save(user)` → INSERT users | Same TX |
| 7 | RegisterHandler | `eventService.record(event)` | Same TX |
| 7a | EventService | Create EventEnvelope with metadata | Same TX |
| 7b | EventService | `eventStorePort.append()` → INSERT event_store | Same TX |
| 7c | EventService | `outboxPort.insert()` → INSERT event_outbox (PENDING) | Same TX |
| 8 | RegisterHandler | Generate auth tokens | Same TX |
| 9 | RegisterHandler | Session promotion (optional) | Same TX |
| 10 | — | @Transactional COMMIT (users + event_store + event_outbox atomic) | TX END |
| 11 | OutboxPoller | Poll PENDING entries (async, separate TX) | New TX |
| 12 | OutboxPoller | `kafkaTemplate.send(topic, partitionKey, payload)` | Same TX |
| 13 | OutboxPoller | Mark PUBLISHED | Same TX |
| 14 | Kafka | Deliver to iam.user.registered topic | — |
| 15 | ProfileKafkaListener | Consume → create profile | Consumer TX |

---

## 7. Error Handling

| Scenario | Error Code | HTTP Status | Behavior |
|----------|-----------|-------------|----------|
| Event store write failure | AUTH_050 | 500 | TX rollback — user NOT created, event NOT stored |
| Outbox insert failure | AUTH_050 | 500 | TX rollback — same as above |
| Outbox Kafka send timeout | AUTH_051 | N/A (async) | Entry remains PENDING, retried next cycle |
| Outbox max retries exceeded | AUTH_053 | N/A (async) | Status → FAILED, ERROR log, manual intervention |
| Event query not found | AUTH_052 | 404 | Return empty result |
| Duplicate registration (existing) | AUTH_006 | 409 | Existing behavior unchanged |
| Domain not found (existing) | AUTH_005 | 404 | Existing behavior unchanged |

---

## 8. Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Event class | `<Entity><Action>Event` | `UserRegisteredEvent` |
| Envelope | `EventEnvelope<T>` | Generic wrapper |
| Port | `<Concern>Port` | `EventStorePort`, `OutboxPort` |
| Adapter | `<Impl>PersistenceAdapter` | `EventStorePersistenceAdapter` |
| Entity | `<Name>Entity` | `EventStoreEntity`, `EventOutboxEntity` |
| Repository | `<Name>JpaRepository` | `EventStoreJpaRepository` |
| Scheduler | `<Feature>Poller` | `OutboxPoller` |
| Config | `<Feature>Properties` | `OutboxProperties` |
| Topic | `iam.<aggregate>.<action>` | `iam.user.registered` |
| Error code | `AUTH_0XX` | `AUTH_050` through `AUTH_053` |
