# SRS: user-registration-event

_Generated: 2025-08-21_
_Profile: Command | N/A (no factory) | EXTEND_

---

## 1. System Context

- **Feature Name**: User Registration Event — Production-Grade Event Sourcing
- **Domain**: Authentication & Authorization — Event Sourcing cho User Registration flow
- **Flow**: Command (RegisterCommand → RegisterHandler → UserRegisteredEvent → Kafka)
- **Services**: auth-service (EXTEND)
- **Source**: User Idea (enriched) + Research artifacts + pre_openspec.md

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Client (End User) | Người dùng gửi request đăng ký | POST /api/auth/register |
| Hệ thống (auth-service) | Xử lý registration, persist events, publish via outbox | RegisterHandler → EventService → Outbox |
| Admin | Quản lý event replay, audit trail, monitoring | GET /internal/events/* |
| Downstream Consumers | account-service — tạo profile, gán roles | Kafka consumer: iam.user.registered |
| Infrastructure | PostgreSQL, Kafka, Redis | Event store, MQ, Cache |

---

## 3. Functional Requirements

### 3.1 Event Consolidation & Enrichment

#### FR-001: Enriched UserRegisteredEvent payload [IDEA]
- **Actor**: Hệ thống
- **Precondition**: User registration thành công, user đã persist vào DB.
- **Action**: Hệ thống tạo `UserRegisteredEvent` với payload đầy đủ: `userId` (Long), `username` (String), `email` (String), `fullName` (String), `phone` (String?), `domainCode` (String), `domainId` (Long?), `status` (String = "ACTIVE"), `registrationSource` (String = "DIRECT"/"SSO"/"ANONYMOUS_PROMOTION"), `ipAddress` (String?), `userAgent` (String?).
- **Validation Rules**:
  - `userId`, `username`, `email`, `fullName`, `domainCode` MUST be non-null
  - `email` MUST be valid format (validated at command level via `@Email`)
  - `registrationSource` MUST be one of: `DIRECT`, `SSO`, `ANONYMOUS_PROMOTION`
- **Error Codes**: N/A (internal event creation, no user-facing error)
- **Existing Code**: `EventPublisher.kt` (L19-25 — old UserRegisteredEvent), `AuthDomainEvents.kt` (L8-13 — duplicate)
- **Classification**: EXTEND — enrich existing minimal payload

#### FR-002: Hợp nhất duplicate UserRegisteredEvent [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Codebase có 2 `UserRegisteredEvent` definitions.
- **Action**:
  1. Tạo `UserRegisteredEvent` mới trong package `auth.domain.event` với enriched payload + `eventType = "iam.user.registered"`
  2. Xóa `UserRegisteredEvent` từ `AuthDomainEvents.kt` (giữ `UserLoggedInEvent`, `SessionRevokedEvent`)
  3. Xóa `UserRegisteredEvent` từ `EventPublisher.kt` (giữ `DomainEvent` interface, other events)
  4. Update imports trong `RegisterHandler.kt`
- **Validation Rules**:
  - Chỉ còn 1 `UserRegisteredEvent` definition sau merge
  - Tất cả references updated — compile thành công
  - `eventType` = `"iam.user.registered"` (dot-notation, iam prefix)
- **Error Codes**: N/A (compile-time validation)
- **Existing Code**: `EventPublisher.kt` (L19-25), `AuthDomainEvents.kt` (L8-13), `RegisterHandler.kt` (L69-73)
- **Classification**: MODIFY

### 3.2 Event Infrastructure

#### FR-003: Event schema versioning [IDEA]
- **Actor**: Hệ thống
- **Action**: Mỗi domain event có trường `schemaVersion: Int` (default = 1). `EventEnvelope` wrapper bao gồm `schemaVersion`.
- **Validation Rules**:
  - `schemaVersion` >= 1 (positive integer)
  - `schemaVersion` present trong mọi persisted event
  - Upcaster mechanism (Phase 2) — framework placeholder only
- **Error Codes**: N/A
- **Classification**: NEW

#### FR-004: Event store persistence [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Domain event created in `EventService.record()`.
- **Action**: Persist event vào `event_store` table (PostgreSQL) dưới dạng append-only:
  - `id` (BIGINT — Snowflake ID)
  - `aggregate_type` (VARCHAR(100)) — e.g., "User"
  - `aggregate_id` (BIGINT) — e.g., userId
  - `event_type` (VARCHAR(200)) — e.g., "iam.user.registered"
  - `schema_version` (INT)
  - `sequence_number` (BIGINT) — per-aggregate ordering
  - `payload` (JSONB) — full EventEnvelope serialized
  - `metadata` (JSONB) — extra context
  - `created_at` (TIMESTAMPTZ)
- **Validation Rules**:
  - `id` unique (Snowflake ID generation)
  - `event_store` table append-only — NO UPDATE/DELETE (enforced by application layer)
  - Unique constraint: `(aggregate_type, aggregate_id, sequence_number)`
  - `created_at` auto-set to UTC NOW()
- **Error Codes**: `AUTH_050` (EVENT_STORE_PERSIST_FAILED) — 500 INTERNAL_SERVER_ERROR
- **Existing Code**: N/A (new table)
- **Classification**: NEW

#### FR-005: Correlation ID cho event tracing [IDEA]
- **Actor**: Hệ thống
- **Action**: Gắn `correlationId` (UUID string) vào `EventEnvelope`:
  - Source: `X-Correlation-ID` HTTP header (if present) → else UUID.randomUUID()
  - Passed from controller → command → EventService → EventEnvelope
- **Validation Rules**:
  - `correlationId` format: UUID v4 string
  - Present trong mọi EventEnvelope
  - Traceable cross-service qua Kafka message headers
- **Error Codes**: N/A
- **Existing Code**: N/A
- **Classification**: NEW

#### FR-006: CloudEvents-inspired event envelope [IDEA]
- **Actor**: Hệ thống
- **Action**: `EventEnvelope<T>` data class wrapping domain events:
  ```
  id: String           // UUID v4 — event ID
  type: String         // "iam.user.registered"
  source: String       // "auth-service" (default)
  specversion: String  // "1.0" (default)
  time: Instant        // event creation timestamp (UTC)
  correlationId: String // from X-Correlation-ID or generated
  schemaVersion: Int   // event schema version (default 1)
  data: T              // actual domain event payload
  ```
- **Validation Rules**:
  - Conforms to CloudEvents 1.0 spec structurally (no SDK dependency)
  - `source` = "auth-service" (configurable via property)
  - `specversion` = "1.0"
  - `time` in UTC
- **Error Codes**: N/A
- **Classification**: NEW

#### FR-007: Transactional outbox pattern [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Event persisted in event_store within same @Transactional.
- **Action**:
  1. `EventService.record()` inserts into `event_outbox` table in same DB transaction as user persistence + event_store insert
  2. `OutboxPoller` (`@Scheduled(fixedDelay = 100)`) polls for PENDING entries
  3. Uses `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety
  4. Publishes each entry to Kafka via `KafkaTemplate`
  5. Marks as PUBLISHED with `published_at` timestamp
  6. On failure: increments `retry_count`, remains PENDING
- **Outbox table schema**:
  - `id` (BIGINT — Snowflake ID)
  - `aggregate_type`, `aggregate_id`, `event_type` (routing info)
  - `topic` (VARCHAR(200)) — Kafka topic
  - `partition_key` (VARCHAR(200)) — Kafka partition key
  - `payload` (JSONB) — full EventEnvelope JSON
  - `status` (VARCHAR(20)) — PENDING / PUBLISHED / FAILED
  - `retry_count` (INT, default 0)
  - `created_at`, `published_at` (TIMESTAMPTZ)
- **Validation Rules**:
  - Outbox insert MUST be in same @Transactional as aggregate persistence
  - PENDING entries processed in `created_at` order (FIFO per aggregate)
  - Max retry: 3 (configurable) — exceeding → status = FAILED
  - Kafka unavailable → event remains PENDING, retried on next poll cycle
- **Error Codes**: `AUTH_051` (OUTBOX_PUBLISH_FAILED) — logged only, not user-facing
- **Classification**: NEW

#### FR-008: Idempotent consumer support [IDEA]
- **Actor**: Downstream consumers
- **Action**: `EventEnvelope.id` (UUID) serves as deduplication key. `processed_events` table:
  - `id` (BIGINT — Snowflake ID)
  - `event_id` (UUID) — from EventEnvelope.id
  - `event_type` (VARCHAR(200))
  - `consumer_group` (VARCHAR(100))
  - `processed_at` (TIMESTAMPTZ)
  - Unique constraint: `(event_id, consumer_group)`
- **Validation Rules**:
  - Consumer checks `processed_events` before processing
  - Duplicate → skip silently (idempotent)
  - `event_id` + `consumer_group` uniqueness enforced by DB constraint
- **Error Codes**: N/A (consumer-side)
- **Classification**: NEW

### 3.3 Kafka & Topic Standardization

#### FR-009: Event replay (basic) [IDEA]
- **Actor**: Admin / Hệ thống
- **Precondition**: Events persisted in event_store.
- **Action**: Provide API to query events from event_store by aggregate:
  - `EventStorePort.findByAggregate(aggregateType, aggregateId): List<EventStoreEntity>`
  - Internal API endpoint: `GET /internal/events/{aggregateType}/{aggregateId}`
- **Validation Rules**:
  - Returns events ordered by `sequence_number` ASC
  - Response is read-only (no state mutation)
- **Error Codes**: `AUTH_052` (EVENT_NOT_FOUND) — 404 NOT_FOUND
- **Classification**: NEW (basic — full replay engine deferred to Phase 2)

#### FR-010: Kafka topic standardization [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Events ready to publish.
- **Action**:
  - All event topics follow convention: `iam.<aggregate>.<action>` (e.g., `iam.user.registered`)
  - Fix `KafkaEventPublisher.publish()`: use `TOPIC_PREFIX + event.eventType` OR ensure event types already contain prefix
  - Partition key = `userId.toString()` for ordering per user
- **Validation Rules**:
  - Topic name matches `iam.<aggregate>.<action>` pattern
  - Partition key = userId → same user events go to same partition
  - `ProfileKafkaListener` (account-service) successfully receives events on `iam.user.registered`
- **Error Codes**: N/A
- **Existing Code**: `KafkaEventPublisher.kt` (L47 — topic = event.eventType, bug), `PermissionChangedConsumer.kt` (topic = `iam.permission.changed`, correct)
- **Classification**: MODIFY (bug fix + enhancement)

#### FR-014: Event metadata enrichment [IDEA]
- **Actor**: Hệ thống
- **Action**: `EventEnvelope` metadata auto-populated:
  - `time`: `Instant.now()` (UTC)
  - `source`: "auth-service" (from property `app.event.source`)
  - `type`: event's `eventType` field
  - `id`: UUID.randomUUID().toString()
  - `correlationId`: from command or generated
- **Validation Rules**: All metadata fields non-null in every EventEnvelope
- **Error Codes**: N/A
- **Classification**: NEW (part of EventEnvelope construction)

### 3.4 Reliability & Observability

#### FR-019: Idempotency cho registration command [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `IdempotencyFilter` (existing) ensures duplicate POST /api/auth/register with same `X-Idempotency-Key` returns cached response.
- **Validation Rules**:
  - Duplicate request → 200 with cached response (not 409)
  - Idempotency key TTL = 24h (existing Redis config)
- **Error Codes**: N/A (handled by existing filter)
- **Existing Code**: `IdempotencyFilter.kt` — already implemented
- **Classification**: REUSE

#### FR-020: Full transaction logging [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Structured logging for registration flow:
  1. `REGISTER_COMMAND_RECEIVED` — command params (sans password)
  2. `REGISTER_VALIDATION_PASSED` — uniqueness checks ok
  3. `REGISTER_USER_CREATED` — userId, username
  4. `REGISTER_EVENT_STORED` — eventId, aggregateId
  5. `REGISTER_OUTBOX_INSERTED` — outboxId
  6. `REGISTER_COMPLETED` — total duration, correlationId
- **Validation Rules**:
  - Each step has structured log entry with correlationId
  - Password NEVER logged
  - Duration tracked via `Instant.now()` diff
- **Error Codes**: N/A
- **Existing Code**: `RegisterHandler.kt` (L76 — basic log.info)
- **Classification**: EXTEND

#### FR-021: Timeout handling cho Kafka publish (outbox relay) [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `OutboxPoller` handles Kafka timeout:
  - Kafka send timeout: 5s (configurable via `app.event.outbox.kafka-timeout-ms`)
  - On timeout: event remains PENDING in outbox, retry_count NOT incremented (timeout ≠ failure)
  - On confirmed failure: increment retry_count
- **Validation Rules**:
  - Kafka timeout does not cause event loss
  - Timeout events retried on next poll cycle
- **Error Codes**: `AUTH_051` (OUTBOX_PUBLISH_FAILED) — logged only
- **Classification**: NEW

#### FR-022: Retry mechanism cho failed event delivery [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `OutboxPoller` retry logic:
  - Max retries: 3 (configurable via `app.event.outbox.max-retries`)
  - After max retries: status = FAILED
  - Failed entries logged at ERROR level with full context
  - ⚠️ Assumption: DLT routing for failed outbox entries deferred to Phase 2 — existing `KafkaConfig.kt` DLQ handles consumer-side failures, not producer-side outbox failures.
- **Validation Rules**:
  - retry_count incremented on each confirmed failure
  - After 3 failures: status changes from PENDING to FAILED
  - FAILED entries remain in outbox for manual intervention / alerting
- **Error Codes**: `AUTH_053` (OUTBOX_MAX_RETRIES_EXCEEDED) — logged only
- **Classification**: NEW

---

## 4. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | Outbox poller latency (PENDING → Kafka) | < 200ms (95th percentile) |
| Performance | Event store write latency (additional to registration) | < 5ms (95th percentile) |
| Reliability | Event loss rate | 0% (transactional outbox guarantee) |
| Scalability | Event store capacity | > 10M events |
| Scalability | Outbox poller multi-instance safe | Yes (SELECT FOR UPDATE SKIP LOCKED) |
| Observability | Structured logging per lifecycle step | Yes (correlationId traceable) |
| Security | PII in event store | Stored as-is (encryption at rest via DB-level — Phase 2) |
| Consistency | Event ordering per aggregate | Guaranteed (partition key = userId) |
| Backward Compatibility | Existing consumers (ProfileKafkaListener) | No breaking change (@JsonIgnoreProperties) |

---

## 5. Error Codes (New)

| Code | Name | HTTP Status | Message Key | Description |
|------|------|-------------|-------------|-------------|
| AUTH_050 | EVENT_STORE_PERSIST_FAILED | 500 | auth.event_store_persist_failed | Event store write failure during registration |
| AUTH_051 | OUTBOX_PUBLISH_FAILED | 500 | auth.outbox_publish_failed | Outbox Kafka publish failure (logged, not user-facing) |
| AUTH_052 | EVENT_NOT_FOUND | 404 | auth.event_not_found | Event query returned no results |
| AUTH_053 | OUTBOX_MAX_RETRIES_EXCEEDED | 500 | auth.outbox_max_retries | Outbox entry exceeded max retry attempts |

---

## 6. External Integration Points

| System | Direction | Protocol | Topic/Endpoint | Contract |
|--------|-----------|----------|----------------|----------|
| Kafka | OUT | Kafka Producer | iam.user.registered | EventEnvelope<UserRegisteredEvent> JSON |
| PostgreSQL | OUT | JDBC/JPA | event_store, event_outbox, processed_events | Append-only, JSONB payload |
| account-service | OUT (via Kafka) | Kafka Consumer | iam.user.registered | Backward-compatible — new fields added, existing fields unchanged |
| Redis | IN/OUT | Redis | idempotency:auth-service:* | Existing IdempotencyFilter (REUSE) |

---

## 7. Assumptions

- ⚠️ Assumption: `eventsourcing-utils` library provides only Command/Query base types — no event store implementation — custom event store built on PostgreSQL
- ⚠️ Assumption: `account-service` uses `@JsonIgnoreProperties(ignoreUnknown = true)` — enriched payload backward-compatible
- ⚠️ Assumption: Kafka configured via `spring.kafka.bootstrap-servers` — KafkaEventPublisher active when property present
- ⚠️ Assumption: Event store table uses Snowflake ID from base-core (BIGINT) — consistent with existing ID strategy
- ⚠️ Assumption: `V11` is next available Flyway migration version (V1-V10 exist)
- ⚠️ Assumption: Outbox payload is full EventEnvelope JSON — consumers parse `data` field for domain event

---

## 8. FR Traceability

| FR-ID | Source | Artifact Section | Status |
|-------|--------|-----------------|--------|
| FR-001 | Idea | §3.1 | ✅ Specified |
| FR-002 | Idea | §3.1 | ✅ Specified |
| FR-003 | Idea | §3.2 | ✅ Specified |
| FR-004 | Idea | §3.2 | ✅ Specified |
| FR-005 | Idea | §3.2 | ✅ Specified |
| FR-006 | Idea | §3.2 | ✅ Specified |
| FR-007 | Idea | §3.2 | ✅ Specified |
| FR-008 | Idea | §3.2 | ✅ Specified |
| FR-009 | Idea | §3.3 | ✅ Specified (basic) |
| FR-010 | Idea | §3.3 | ✅ Specified |
| FR-011 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-012 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-013 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-014 | Idea | §3.3 | ✅ Specified |
| FR-015 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-016 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-017 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-018 | Idea | Deferred Phase 2 | ⏸️ Deferred |
| FR-019 | Enriched | §3.4 | ✅ Specified (REUSE) |
| FR-020 | Enriched | §3.4 | ✅ Specified |
| FR-021 | Enriched | §3.4 | ✅ Specified |
| FR-022 | Enriched | §3.4 | ✅ Specified |
