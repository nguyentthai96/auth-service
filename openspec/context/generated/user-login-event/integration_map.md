# Integration Map

_Generated: 2025-08-22_

## EventService (Event Store + Outbox)

- Client: `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Protocol: In-process method call (same @Transactional boundary)
- API: `record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)`
- Request: Any `DomainEvent` implementation
- Response: void (throws on failure)
- Note: Intermediary service — creates EventEnvelope, persists to event_store + event_outbox atomically

## EventStorePort (Database — Event Store)

- Client: `EventStorePersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/EventStorePersistenceAdapter.kt`
- Protocol: JPA / Spring Data
- API: `append(aggregateType, aggregateId, eventType, schemaVersion, sequenceNumber, payload, metadata)`
- Database table: `event_store` (PostgreSQL)
- Note: Append-only event log

## OutboxPort (Database — Outbox)

- Client: `OutboxPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/OutboxPersistenceAdapter.kt`
- Protocol: JPA / Spring Data
- API: `insert(aggregateType, aggregateId, eventType, topic, partitionKey, payload)`
- Database table: `event_outbox` (PostgreSQL)
- Note: Transactional outbox entries with PENDING status

## Kafka (Message Queue)

- Client: `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
- Protocol: Kafka (via `KafkaTemplate<String, String>`)
- Topics: `iam.user.logged_in` (NEW), `iam.user.login_failed` (NEW)
- Partition key: userId.toString()
- Note: Async relay from outbox to Kafka. 100ms polling interval. Retry with timeout handling.

- Client: `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka (via `KafkaTemplate<String, String>`)
- Note: Direct Kafka publisher for non-outbox events

## SpringEventPublisher (In-Process Events)

- Client: `SpringEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
- Protocol: Spring ApplicationEventPublisher
- Note: In-process events ONLY. Used by LoginSessionService for NewDeviceLoginEvent. NOT persisted to event store.

## CAPTCHA Gateway

- Client: `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- Protocol: External HTTP
- Note: Used by LoginHandler for CAPTCHA verification. Implementation: AltchaCaptchaVerifier

## NOT DETECTED

- REST client integrations (no RestTemplate/WebClient/FeignClient in auth module for login flow)
- Redis integration in login flow (Redis used elsewhere for token blacklist, not directly in login event recording)
