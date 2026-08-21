# Integration Map

_Generated: 2025-08-21_

## Kafka (Message Queue)

- type: MQ
  - class: KafkaEventPublisher — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - protocol: Kafka (KafkaTemplate<String, String>)
  - purpose: Publish domain events to Kafka topics with retry
  - topics: `iam.user.registered`, `iam.user.logged_in` (planned), `iam.user.login_failed` (planned)

- type: MQ
  - class: OutboxPoller — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
  - protocol: Kafka (KafkaTemplate<String, String>)
  - purpose: Asynchronous outbox relay — polls `event_outbox` table, publishes to Kafka
  - scheduling: Configurable interval via `OutboxProperties`
  - timeout: Kafka timeout does NOT increment retry_count (FR-021)

- type: MQ
  - class: PermissionChangedConsumer — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - protocol: Kafka (@KafkaListener)
  - topic: `iam.permission.changed`
  - purpose: Invalidate permission cache on changes

- type: MQ
  - class: KafkaConfig — `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
  - protocol: Kafka dead-letter queue configuration
  - purpose: Retry + DLQ for failed messages

## Redis (Cache)

- type: Cache
  - class: RedisConfig — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
  - protocol: Redis (RedisTemplate<String, Any>)
  - purpose: Distributed cache — token blacklist, session metadata, rate limiting

- type: Cache
  - class: RedisLuaScriptConfig — `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt`
  - protocol: Redis Lua scripts
  - scripts: slidingWindowRateLimit, safeLockRelease, atomicDataStore
  - purpose: Atomic Redis operations for rate limiting, lock management

- type: Cache
  - class: AbstractTwoTierCache — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - protocol: L1 (Caffeine) → L2 (Redis)
  - purpose: Two-tier cache infrastructure

## PostgreSQL (Database)

- type: DB
  - class: EventStorePort (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventStorePort.kt`
  - protocol: JPA (via persistence adapter)
  - tables: `event_store` — immutable event log
  - purpose: Event sourcing event persistence

- type: DB
  - class: OutboxPort (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/OutboxPort.kt`
  - protocol: JPA (via persistence adapter)
  - tables: `event_outbox` — transactional outbox for Kafka relay
  - purpose: Reliable event delivery pattern

- type: DB
  - entity: EventOutboxEntity — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/EventOutboxEntity.kt`
  - purpose: JPA entity for outbox table

## Spring Application Events (In-Process)

- type: In-Process
  - class: SpringEventPublisher — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
  - protocol: Spring ApplicationEventPublisher
  - purpose: Legacy in-process event publishing (Phase 3 planned: replace with Kafka)

## NOT DETECTED

- REST client / HTTP outbound — NOT DETECTED (no RestTemplate, WebClient, FeignClient in auth module)
- gRPC — NOT DETECTED
- AMQP / RabbitMQ — NOT DETECTED
