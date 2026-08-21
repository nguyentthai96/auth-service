# Integration Map

_Generated: 2025-08-21 | Feature: user-registration-event_

## Kafka (Event Bus)

### Producer
- Client: `KafkaEventPublisher.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka
- Topic derivation: `event.eventType` used directly as topic name
- Current topics: `user.registered`, `iam.permission.changed`, `iam.user.sso_provisioned`, `iam.account.deactivated`, `iam.account.deleted`, `iam.audit.event`
- Implements: `EventPublisher` port — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
- Activation: `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`
- `@Primary` — overrides `SpringEventPublisher` when Kafka available
- Retry: `@Retryable` — 3 attempts, exponential backoff (1s, 2s, 4s), max 8s
- Serialization: `ObjectMapper.writeValueAsString(event)` → JSON string
- Key: Not set — no partition key currently (FR-010 will add userId as partition key)

### Producer (Fallback)
- Client: `SpringEventPublisher.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
- Protocol: In-process (log only, no actual publishing)
- Activation: `@Component` (active when Kafka not configured)
- Note: Fallback — logs events only, does not actually publish to external bus

### Consumer
- Client: `PermissionChangedConsumer.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
- Protocol: Kafka
- Topic: `iam.permission.changed`
- Group ID: `auth-service`
- Action: Invalidates all permission caches (`permissionCache.invalidateAll()`)
- Activation: `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`

### DLQ Configuration
- Config: `KafkaConfig.kt` — `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
- Dead Letter: `DeadLetterPublishingRecoverer` → `<original-topic>.DLT`
- Backoff: `ExponentialBackOff` (1s initial, 2x multiplier, 8s max, 30s total elapsed)
- Error handler: `DefaultErrorHandler` with retry listeners (log warnings)

## Redis (Cache)

- Config: `RedisConfig.kt` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Client: `RedisTemplate<String, Any>` with Jackson JSON serialization
- Serialization: `StringRedisSerializer` (key), `GenericJackson2JsonRedisSerializer` (value)
- Usage for this feature:
  - `MultiTierPermissionCache.kt` — L2 permission cache (invalidate on registration event)
  - `AbstractTwoTierCache.kt` — Two-tier cache base class (L1 Caffeine + L2 Redis)
  - `IdempotencyFilter.kt` — Idempotency check for registration command (`idempotency:auth-service:*`, TTL 24h)

## Caffeine (In-Memory Cache — L1)

- Client: `CaffeinePermissionCache.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/CaffeinePermissionCache.kt`
- Protocol: In-process (L1 cache)
- Wrapper: `MultiTierPermissionCache.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
- Base: `AbstractTwoTierCache<K, V>` — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - Abstract methods: `toKeyString()`, `deserializeFromRedis()`, `loadFromSource()`
- Port: `PermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt`

## PostgreSQL (JPA/Hibernate)

- Protocol: JDBC/JPA
- Driver: PostgreSQL
- Migrations: Flyway V1-V10 (V11 to be added for event_store)
  - V1: `V1__init_auth_rbac_pbac.sql`
  - V2: `V2__auth_core_features.sql`
  - V3: `V3__add_version_column.sql`
  - V4: `V4__create_login_sessions.sql`
  - V5: `V5__create_i18n_messages.sql`
  - V6: `V6__seed_i18n_messages.sql`
  - V7: `V7__create_cipher_key_session.sql`
  - V8: `V8__create_e2ee_audit_tables.sql`
  - V9: `V9__account_lifecycle.sql`
  - V10: `V10__trusted_device_ttl.sql`
- Relevant adapters:
  - `UserPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt`
  - `DomainPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/DomainPersistenceAdapter.kt`
- Relevant entities: `UserEntity`, `UserIdentityEntity`
- New tables needed: `event_store` (append-only), `outbox` (transactional outbox), `snapshots`

## base-core Module (Auto-Configuration)

- Library: base-core starters (`base-web-starter`, `base-data-starter`, `base-security-starter`)
- Provides:
  - `SnowflakePersistentAuditableEntity` — base entity with Snowflake ID generation
  - `BaseControllerAdvice` — base exception handler
  - `ErrorCodeBase` — error code interface
  - `BusinessException` — base business exception
- Conditional annotations: `@ConditionalOnProperty`, `@ConditionalOnMissingBean`
- Config: `build.gradle.kts` dependency

## eventsourcing-utils Library

- Library: `com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT`
- Provides:
  - `Command` — base command type
  - `CommandHandler<C, R>` — base command handler type
  - `Query` / `QueryHandler` — base query types
- Usage:
  - `RegisterCommand` extends `Command`
  - `RegisterHandler` extends `CommandHandler<RegisterCommand, RegisterResult>`
  - `LoginCommand` extends `Command`
  - `LoginHandler` extends `CommandHandler<LoginCommand, LoginResult>`

## NOT DETECTED

- WebClient (not used — HTTP calls via declarative `@HttpExchange` or `RestTemplate`)
- FeignClient (not used)
- gRPC (not used)
- Direct REST calls to account-service (no client found — communication via Kafka events only)
