<!-- self-contained: true -->
# Tasks: user-registration-event

_Generated: 2025-08-21_
_Profile: Command | N/A (no factory) | EXTEND_
_Direction: Incremental Event Sourcing — Outbox-First_

---

## Changes

- **auth.domain.event [NEW]**: `UserRegisteredEvent.kt` — enriched event payload (userId, username, email, fullName, phone, domainCode, domainId, status, registrationSource, ipAddress, userAgent) with eventType `iam.user.registered`, consolidating duplicate definitions from `EventPublisher.kt` and `AuthDomainEvents.kt` (FR-001, FR-002).
- **auth.domain.event [NEW]**: `EventEnvelope.kt` — CloudEvents-inspired generic envelope (`id`, `type`, `source`, `specversion`, `time`, `correlationId`, `schemaVersion`, `data`) wrapping all domain events (FR-005, FR-006, FR-014).
- **auth.application.event [NEW]**: `EventService.kt` — intermediary service encapsulating event creation → event store persistence → outbox insertion in single transaction (DD-003). Reusable across all command handlers.
- **auth.application.port.out [NEW]**: `EventStorePort.kt` — outbound port for event store persistence. `OutboxPort.kt` — outbound port for transactional outbox.
- **auth.application.port.out [MODIFY]**: `EventPublisher.kt` — remove `UserRegisteredEvent` class (moved to domain.event package); keep `DomainEvent` interface, other event types unchanged.
- **auth.application.command [MODIFY]**: `RegisterCommand.kt` — add `ipAddress`, `userAgent`, `correlationId` fields for event enrichment.
- **auth.application.command [MODIFY]**: `RegisterHandler.kt` — replace direct `eventPublisher.publish()` with `eventService.record()` for transactional event sourcing.
- **auth.application.command [DELETE]**: `AuthDomainEvents.kt` — remove duplicate `UserRegisteredEvent`; keep `UserLoggedInEvent`, `SessionRevokedEvent` (used by LoginHandler, RevokeSessionsHandler).
- **auth.adapter.out.persistence [NEW]**: `EventStorePersistenceAdapter.kt` — JPA adapter for event store. `OutboxPersistenceAdapter.kt` — JPA adapter for outbox table.
- **auth.adapter.out.persistence.entity [NEW]**: `EventStoreEntity.kt` — append-only event log entity. `EventOutboxEntity.kt` — transactional outbox entity. `ProcessedEventEntity.kt` — consumer-side idempotency entity.
- **auth.adapter.out.persistence.repository [NEW]**: `EventStoreJpaRepository.kt`, `EventOutboxJpaRepository.kt`, `ProcessedEventJpaRepository.kt`.
- **auth.adapter.out.event [NEW]**: `OutboxPoller.kt` — `@Scheduled` polling for pending outbox entries, publishes to Kafka via `KafkaTemplate`, uses `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety.
- **auth.adapter.out.event [MODIFY]**: `KafkaEventPublisher.kt` — fix topic mismatch bug; use `TOPIC_PREFIX` for proper `iam.` prefixed topics; add partition key support (userId).
- **auth.adapter.in.web [MODIFY]**: `CqrsAuthController.kt` — pass `ipAddress`, `userAgent`, `correlationId` (from `X-Correlation-ID` header) to `RegisterCommand`.
- **auth.adapter.in.web [NEW]**: `EventStoreController.kt` — internal API for querying events from event store by aggregate.
- **shared.config [NEW]**: `OutboxProperties.kt` — configurable outbox poller interval, batch size, max retry count.
- **shared.exception [MODIFY]**: `AuthErrorCode` — new error codes AUTH_050–AUTH_053 for event store operations.
- **DB migration [NEW]**: `V11__event_sourcing_tables.sql` — `event_store`, `event_outbox`, `processed_events` tables with indexes.
- **DB migration [NEW]**: `V12__seed_event_error_messages.sql` — i18n messages for new error codes.

**Total**: ~15 new files, ~5 modified files, ~1 deleted file. 22 FRs (15 Phase 1, 7 Phase 2 deferred). ~5-7 developer-days effort.

---

## Phase 1: Foundation — Event Model & Database Schema

- [ ] **Task 1: Create Flyway migration V11 — event sourcing tables** `[NEW]`
  - File: `src/main/resources/db/migration/V11__event_sourcing_tables.sql`
  - Action: [NEW]
  - FR: FR-004 — Event store persistence; FR-007 — Transactional outbox; FR-008 — Idempotent consumer support
  - Pattern: Follow existing V1-V10 naming convention; single migration for atomic feature deployment (DD-005)
  - Dependencies: PostgreSQL, Flyway
  - SQL content:
    ```sql
    -- 1. event_store — append-only immutable event log
    CREATE TABLE event_store (
        id               BIGINT PRIMARY KEY,
        aggregate_type   VARCHAR(100) NOT NULL,
        aggregate_id     BIGINT NOT NULL,
        event_type       VARCHAR(200) NOT NULL,
        schema_version   INT NOT NULL DEFAULT 1,
        sequence_number  BIGINT NOT NULL,
        payload          JSONB NOT NULL,
        metadata         JSONB NOT NULL DEFAULT '{}',
        created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_type, aggregate_id);
    CREATE INDEX idx_event_store_type ON event_store(event_type);
    CREATE INDEX idx_event_store_created ON event_store(created_at);
    CREATE UNIQUE INDEX uq_event_store_agg_seq ON event_store(aggregate_type, aggregate_id, sequence_number);

    -- 2. event_outbox — transactional outbox for reliable Kafka publishing
    CREATE TABLE event_outbox (
        id               BIGINT PRIMARY KEY,
        aggregate_type   VARCHAR(100) NOT NULL,
        aggregate_id     BIGINT NOT NULL,
        event_type       VARCHAR(200) NOT NULL,
        topic            VARCHAR(200) NOT NULL,
        partition_key    VARCHAR(200) NOT NULL,
        payload          JSONB NOT NULL,
        status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        retry_count      INT NOT NULL DEFAULT 0,
        created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        published_at     TIMESTAMPTZ
    );
    CREATE INDEX idx_outbox_status_created ON event_outbox(status, created_at);
    CREATE INDEX idx_outbox_published ON event_outbox(published_at) WHERE published_at IS NOT NULL;

    -- 3. processed_events — consumer-side idempotency
    CREATE TABLE processed_events (
        id               BIGINT PRIMARY KEY,
        event_id         UUID NOT NULL,
        event_type       VARCHAR(200) NOT NULL,
        consumer_group   VARCHAR(100) NOT NULL,
        processed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE UNIQUE INDEX uq_processed_event_consumer ON processed_events(event_id, consumer_group);
    CREATE INDEX idx_processed_at ON processed_events(processed_at);
    ```

- [ ] **Task 2: Create EventEnvelope data class** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - Action: [NEW]
  - FR: FR-006 — CloudEvents-inspired envelope; FR-003 — Schema versioning; FR-005 — Correlation ID; FR-014 — Metadata enrichment
  - Pattern: Kotlin `data class`, generic `<T>`, immutable — follows CloudEvents 1.0 structure without SDK (DD-002)
  - Fields: `id: String` (UUID), `type: String`, `source: String` (default "auth-service"), `specversion: String` (default "1.0"), `time: Instant` (UTC), `correlationId: String`, `schemaVersion: Int` (default 1), `data: T`

- [ ] **Task 3: Create enriched UserRegisteredEvent** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
  - Action: [NEW]
  - FR: FR-001 — Enriched payload; FR-002 — Consolidate duplicates
  - Base: Implements `DomainEvent` from `com.ntt.authservice.auth.application.port.out`
  - Pattern: Kotlin `data class`, override `eventType = "iam.user.registered"`
  - Fields: `userId: Long`, `username: String`, `email: String`, `fullName: String`, `phone: String?`, `domainCode: String`, `domainId: Long?`, `status: String`, `registrationSource: String`, `ipAddress: String?`, `userAgent: String?`

- [ ] **Task 4: Add new error codes AUTH_050–053** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - Action: [MODIFY]
  - FR: FR-004, FR-007, FR-009
  - Pattern: Follow existing `AUTH_NNN` pattern with `errorCode`, `msgCode`, `description`, `httpStatus`
  - Add:
    - `EVENT_STORE_PERSIST_FAILED("AUTH_050", "auth.event_store_persist_failed", "Event store write failure", INTERNAL_SERVER_ERROR)`
    - `OUTBOX_PUBLISH_FAILED("AUTH_051", "auth.outbox_publish_failed", "Outbox Kafka publish failure", INTERNAL_SERVER_ERROR)`
    - `EVENT_NOT_FOUND("AUTH_052", "auth.event_not_found", "Event not found", NOT_FOUND)`
    - `OUTBOX_MAX_RETRIES_EXCEEDED("AUTH_053", "auth.outbox_max_retries", "Outbox max retries exceeded", INTERNAL_SERVER_ERROR)`

- [ ] **Task 5: Add event-related exception classes** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - Action: [MODIFY]
  - Pattern: Follow existing `AuthException` pattern — extends `AuthException(authError, message, httpStatus)`
  - Add:
    - `EventStorePersistException(message: String)` — AUTH_050
    - `EventNotFoundException(aggregateType: String, aggregateId: Long)` — AUTH_052

---

## Phase 2: Ports & Adapters — Event Infrastructure

- [ ] **Task 6: Create EventStorePort** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventStorePort.kt`
  - Action: [NEW]
  - FR: FR-004 — Event store persistence; FR-009 — Event replay (basic)
  - Pattern: Interface — follows existing port pattern (`UserPort`, `DomainPort`)
  - Methods: `append(aggregateType, aggregateId, eventType, schemaVersion, sequenceNumber, payload, metadata)`, `findByAggregate(aggregateType, aggregateId): List<EventStoreEntity>`, `getNextSequenceNumber(aggregateType, aggregateId): Long`

- [ ] **Task 7: Create OutboxPort** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/OutboxPort.kt`
  - Action: [NEW]
  - FR: FR-007 — Transactional outbox
  - Pattern: Interface — same port pattern
  - Methods: `insert(aggregateType, aggregateId, eventType, topic, partitionKey, payload)`, `findPendingForUpdate(limit): List<EventOutboxEntity>`, `markPublished(id)`, `markFailed(id)`, `incrementRetryCount(id)`

- [ ] **Task 8: Create EventStoreEntity** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/EventStoreEntity.kt`
  - Action: [NEW]
  - FR: FR-004
  - Base: `SnowflakePersistentAuditableEntity()` from `com.ntt.basecore.model.id`
  - Pattern: JPA `@Entity`, `@Table(name = "event_store")`, JSONB columns via `@JdbcTypeCode(SqlTypes.JSON)`
  - Note: NOT using `VersionedAuditableEntity` — event store is append-only, no optimistic locking needed

- [ ] **Task 9: Create EventOutboxEntity** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/EventOutboxEntity.kt`
  - Action: [NEW]
  - FR: FR-007
  - Base: `SnowflakePersistentAuditableEntity()` from `com.ntt.basecore.model.id`
  - Pattern: JPA `@Entity`, `@Table(name = "event_outbox")`, JSONB payload

- [ ] **Task 10: Create ProcessedEventEntity** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/ProcessedEventEntity.kt`
  - Action: [NEW]
  - FR: FR-008
  - Base: `SnowflakePersistentAuditableEntity()` from `com.ntt.basecore.model.id`
  - Pattern: JPA `@Entity`, `@Table(name = "processed_events")`, unique constraint on `(event_id, consumer_group)`

- [ ] **Task 11: Create JPA Repositories** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/EventStoreJpaRepository.kt`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/EventOutboxJpaRepository.kt`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/ProcessedEventJpaRepository.kt`
  - Action: [NEW]
  - Pattern: `JpaRepository<Entity, Long>` — follows existing repo pattern
  - EventStoreJpaRepository: `findByAggregateTypeAndAggregateIdOrderBySequenceNumberAsc()`, `findMaxSequenceNumber(aggregateType, aggregateId): Long?`
  - EventOutboxJpaRepository: `@Query("SELECT ... WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED") findPendingForUpdate(limit): List<EventOutboxEntity>`
  - ProcessedEventJpaRepository: `existsByEventIdAndConsumerGroup(eventId, consumerGroup): Boolean`

- [ ] **Task 12: Create EventStorePersistenceAdapter** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/EventStorePersistenceAdapter.kt`
  - Action: [NEW]
  - FR: FR-004
  - Pattern: `@Component` implements `EventStorePort` — follows `UserPersistenceAdapter` pattern
  - Dependencies: `EventStoreJpaRepository`

- [ ] **Task 13: Create OutboxPersistenceAdapter** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/OutboxPersistenceAdapter.kt`
  - Action: [NEW]
  - FR: FR-007
  - Pattern: `@Component` implements `OutboxPort`
  - Dependencies: `EventOutboxJpaRepository`

---

## Phase 3: Core Logic — EventService & Handler Integration

- [ ] **Task 14: Create EventService** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - Action: [NEW]
  - FR: FR-004, FR-005, FR-006, FR-007, FR-014
  - Pattern: `@Component` — intermediary (DD-003). Reusable across all command handlers.
  - Dependencies: `EventStorePort`, `OutboxPort`, `ObjectMapper`
  - Method: `fun <T : DomainEvent> record(aggregateType, aggregateId, event, topic, partitionKey, correlationId?)`
  - Logic:
    1. Create `EventEnvelope` with auto-generated id (UUID), time (Instant.now()), source (from property), correlationId (from param or generated)
    2. Get next sequence number via `eventStorePort.getNextSequenceNumber()`
    3. `eventStorePort.append()` — persist to event_store
    4. `outboxPort.insert()` — insert to outbox with PENDING status
  - Config: `@Value("${app.event.source:auth-service}")` for source field

- [ ] **Task 15: Remove duplicate UserRegisteredEvent from EventPublisher.kt** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Action: [MODIFY]
  - FR: FR-002
  - Remove: `UserRegisteredEvent` data class (L19-25)
  - Keep: `DomainEvent` interface (L12-14), `EventPublisher` interface (L6-8), `PermissionChangedEvent`, `SsoProvisionedEvent`, `AccountDeactivatedEvent`, `AccountDeletedEvent`, `AuditEvent`

- [ ] **Task 16: Remove duplicate UserRegisteredEvent from AuthDomainEvents.kt** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
  - Action: [MODIFY]
  - FR: FR-002
  - Remove: `UserRegisteredEvent` data class (L8-13)
  - Keep: `UserLoggedInEvent` (L15-19), `SessionRevokedEvent` (L21-26)

- [ ] **Task 17: Enhance RegisterCommand with event metadata** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt`
  - Action: [MODIFY]
  - FR: FR-001, FR-005
  - Add fields: `val ipAddress: String? = null`, `val userAgent: String? = null`, `val correlationId: String? = null`
  - Base: `Command<RegisterResult>` from `com.ntt.eventsourcingutils.lib.cqrs.command`

- [ ] **Task 18: Modify RegisterHandler — replace fire-and-forget with EventService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - Action: [MODIFY]
  - FR: FR-001, FR-004, FR-007, FR-020
  - Changes:
    1. Add `EventService` to constructor injection (keep `eventPublisher` for backward compat)
    2. Replace L69-73 (`eventPublisher.publish(UserRegisteredEvent(...))`) with:
       ```kotlin
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
    3. Add private helper: `determineRegistrationSource(command)` → "DIRECT" (default), "ANONYMOUS_PROMOTION" (if anonymousSessionId present)
    4. Add structured logging per transaction step (FR-020)
  - Dependencies: Add `EventService` injection; update `UserRegisteredEvent` import to `auth.domain.event`

- [ ] **Task 19: Modify CqrsAuthController — pass event metadata** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Action: [MODIFY]
  - FR: FR-001, FR-005
  - Changes:
    1. Add `httpRequest: HttpServletRequest` parameter to `register()` method
    2. Pass `ipAddress = extractClientIp(httpRequest)` to RegisterCommand
    3. Pass `userAgent = httpRequest.getHeader("User-Agent")` to RegisterCommand
    4. Pass `correlationId = httpRequest.getHeader("X-Correlation-ID")` to RegisterCommand
  - Note: `extractClientIp()` private method already exists (L249-254) — reuse

---

## Phase 4: Outbox Relay & Topic Fix

- [ ] **Task 20: Create OutboxProperties config** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/OutboxProperties.kt`
  - Action: [NEW]
  - FR: FR-007, FR-021, FR-022
  - Pattern: `@ConfigurationProperties(prefix = "app.event.outbox")` — follows Spring Boot convention
  - Fields: `pollIntervalMs: Long = 100`, `batchSize: Int = 50`, `maxRetries: Int = 3`, `kafkaTimeoutMs: Long = 5000`

- [ ] **Task 21: Create OutboxPoller** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
  - Action: [NEW]
  - FR: FR-007 — Transactional outbox; FR-021 — Timeout handling; FR-022 — Retry mechanism
  - Pattern: `@Component`, `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`, `@Scheduled(fixedDelayString = "${app.event.outbox.poll-interval-ms:100}")`
  - Dependencies: `EventOutboxJpaRepository`, `KafkaTemplate<String, String>`
  - Logic:
    1. `@Scheduled` polls every 100ms (configurable)
    2. `@Transactional` — `SELECT ... FOR UPDATE SKIP LOCKED` (DD-006)
    3. For each PENDING entry: `kafkaTemplate.send(topic, partitionKey, payload)`
    4. On success: `markPublished(id)` with `publishedAt = Instant.now()`
    5. On Kafka timeout (5s): entry remains PENDING, NOT incrementing retry_count (FR-021)
    6. On confirmed failure: `incrementRetryCount(id)`. If `retryCount >= maxRetries` → `markFailed(id)`, log ERROR (FR-022)
  - Reference: `SessionCleanupScheduler.kt` — same `@Scheduled` + `@Transactional` pattern

- [ ] **Task 22: Fix KafkaEventPublisher topic mismatch** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - Action: [MODIFY]
  - FR: FR-010 — Kafka topic standardization
  - Changes:
    1. Fix L47: `val topic = event.eventType` → ensure `iam.` prefix:
       ```kotlin
       val topic = if (event.eventType.startsWith(TOPIC_PREFIX)) {
           event.eventType
       } else {
           TOPIC_PREFIX + event.eventType
       }
       ```
    2. This fixes: `"user.registered"` → `"iam.user.registered"` (matching `ProfileKafkaListener`)
    3. `PermissionChangedEvent` already has `"iam.permission.changed"` → no double prefix
  - Note: KafkaEventPublisher still used for non-outbox events (AuditEvent, PermissionChangedEvent, etc.). Only UserRegisteredEvent goes through outbox path.

- [ ] **Task 23: Add i18n messages for new error codes** `[NEW]`
  - File: `src/main/resources/db/migration/V12__seed_event_error_messages.sql` (or manually insert)
  - Action: [NEW]
  - Pattern: Follow V6__seed_i18n_messages.sql pattern
  - Messages:
    - `auth.event_store_persist_failed` → "Event store write failure"
    - `auth.outbox_publish_failed` → "Event publish failed"
    - `auth.event_not_found` → "Event not found"
    - `auth.outbox_max_retries` → "Event publish max retries exceeded"

---

## Phase 5: Internal API & Observability

- [ ] **Task 24: Create EventStoreController (internal API)** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/EventStoreController.kt`
  - Action: [NEW]
  - FR: FR-009 — Event replay (basic query)
  - Pattern: `@RestController`, `@RequestMapping("/internal/events")` — follows `InternalApiController.kt` pattern
  - Endpoints:
    - `GET /internal/events/{aggregateType}/{aggregateId}` → returns list of events ordered by sequence_number
  - Dependencies: `EventStorePort`
  - Security: Internal endpoint — protected by `ServiceAuthFilter` (existing)

- [ ] **Task 25: Add application.yml config for event outbox** `[MODIFY]`
  - File: `src/main/resources/application.yml`
  - Action: [MODIFY]
  - Add:
    ```yaml
    app:
      event:
        source: auth-service
        outbox:
          poll-interval-ms: 100
          batch-size: 50
          max-retries: 3
          kafka-timeout-ms: 5000
    ```

---

## Verification Checklist

- [ ] Compile: `./gradlew compileKotlin` passes with no errors
- [ ] Flyway: V11 migration applies cleanly to dev DB
- [ ] Event consolidation: Only 1 `UserRegisteredEvent` definition exists (in `auth.domain.event`)
- [ ] Topic fix: `KafkaEventPublisher` sends to `iam.user.registered` (not `user.registered`)
- [ ] Transactional: `event_store` + `event_outbox` inserts in same TX as `users` insert
- [ ] Outbox poller: polls PENDING → sends to Kafka → marks PUBLISHED
- [ ] Multi-instance: OutboxPoller uses `SELECT ... FOR UPDATE SKIP LOCKED`
- [ ] EventEnvelope: Contains `id`, `type`, `source`, `specversion`, `time`, `correlationId`, `schemaVersion`, `data`
- [ ] Backward-compatible: `ProfileKafkaListener` can deserialize enriched payload (Jackson ignores unknown)
- [ ] Error codes: AUTH_050–053 added to `AuthErrorCode` enum
- [ ] FR coverage: 15/22 FRs covered in Phase 1; 7 deferred to Phase 2

---

## FR Traceability

| FR | Task(s) | Status |
|----|---------|--------|
| FR-001 | T3, T17, T18 | ✅ Covered |
| FR-002 | T3, T15, T16 | ✅ Covered |
| FR-003 | T2 | ✅ Covered |
| FR-004 | T1, T6, T8, T11, T12, T14 | ✅ Covered |
| FR-005 | T2, T14, T17, T19 | ✅ Covered |
| FR-006 | T2 | ✅ Covered |
| FR-007 | T1, T7, T9, T11, T13, T14, T21 | ✅ Covered |
| FR-008 | T1, T10, T11 | ✅ Covered |
| FR-009 | T24 | ✅ Covered (basic) |
| FR-010 | T22 | ✅ Covered |
| FR-011 | — | ⏸️ Deferred Phase 2 |
| FR-012 | — | ⏸️ Deferred Phase 2 |
| FR-013 | — | ⏸️ Deferred Phase 2 |
| FR-014 | T2, T14 | ✅ Covered |
| FR-015 | — | ⏸️ Deferred Phase 2 |
| FR-016 | — | ⏸️ Deferred Phase 2 |
| FR-017 | — | ⏸️ Deferred Phase 2 |
| FR-018 | — | ⏸️ Deferred Phase 2 |
| FR-019 | — | ✅ REUSE (IdempotencyFilter) |
| FR-020 | T18 | ✅ Covered |
| FR-021 | T21 | ✅ Covered |
| FR-022 | T21 | ✅ Covered |
