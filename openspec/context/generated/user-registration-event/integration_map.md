# Integration Map

_Generated: 2025-08-22_

## Kafka (Message Queue)

- Client: `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka (KafkaTemplate<String, String>)
- Topics (existing):
  - `iam.user.registered` — UserRegisteredEvent
  - `iam.user.logged_in` — UserLoggedInEvent
  - `iam.user.login_failed` — UserLoginFailedEvent
  - `iam.token.issued` — TokenIssuedEvent
  - `iam.token.revoked` — TokenRevokedEvent
  - `iam.token.validation-failed` — TokenValidationFailedEvent
- Topics (NEW):
  - `iam.user.registration_failed` — UserRegistrationFailedEvent
- Message format: EventEnvelope<DomainEvent> serialized as JSON

## Outbox Pattern (Event Relay)

- Client: `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
- Protocol: @Scheduled polling + Kafka publish
- Source: event_outbox table (PENDING entries)
- Destination: Kafka topics
- Retry: configurable max retries

## EventService (Internal — Event Recording Pipeline)

- Client: `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Protocol: Method call (within @Transactional boundary)
- Flow: DomainEvent → EventEnvelope wrapping → EventStorePort.append() + OutboxPort.insert()
- Request type: `DomainEvent` interface + metadata params
- Response type: void (synchronous within same TX)

## PostgreSQL — Event Store

- Client: `EventStorePersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/EventStorePersistenceAdapter.kt`
- Protocol: JPA (Spring Data)
- Table: `event_store` (append-only)
- Columns: id, aggregate_type, aggregate_id, event_type, schema_version, sequence_number, payload (JSONB), metadata (JSONB), created_at, updated_at

## PostgreSQL — Event Outbox

- Client: `OutboxPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/OutboxPersistenceAdapter.kt`
- Protocol: JPA (Spring Data)
- Table: `event_outbox`
- Columns: id, aggregate_type, aggregate_id, event_type, topic, partition_key, payload, status, retry_count, created_at

## Spring Events (Local Event Bus)

- Client: `SpringEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
- Protocol: Spring ApplicationEventPublisher
- Purpose: Local event publishing (non-Kafka path)

## NOT DETECTED

- REST client integrations (not relevant for event recording flow)
- Redis integration (not relevant for event recording — cache invalidation handled separately)
- External HTTP API calls (not relevant for this feature)
