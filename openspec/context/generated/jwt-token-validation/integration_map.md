# Integration Map

_Generated: 2025-01-20_

## Redis (Distributed Cache — L2 Blacklist)

- Client: `StringRedisTemplate` (Spring auto-configured)
- Protocol: Redis protocol
- Used by: `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
- Operations:
  - `redisTemplate.hasKey("token:blacklist:{jti}")` — read (blacklist check)
  - `redisTemplate.opsForValue().set(key, "1", Duration)` — write (cache populate + write-through)
- Key format: `token:blacklist:{jti}`
- TTL: Remaining token lifetime (configurable)
- Circuit breaker: AtomicInteger-based, threshold=5, reset=30s (configurable via `SecurityProperties.BlacklistCacheProperties`)
- Configuration: `SecurityProperties.BlacklistCacheProperties.redisKeyPrefix`, `redisTimeoutMs`, `circuitBreakerThreshold`, `circuitBreakerResetSeconds`

## Caffeine (In-Process Cache — L1 Blacklist)

- Client: `com.github.benmanes.caffeine.cache.Cache<String, Boolean>` (programmatic, NOT Spring Cache)
- Protocol: In-process API
- Used by: `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
- Operations:
  - `caffeineCache.getIfPresent(jti)` — read
  - `caffeineCache.put(jti, true)` — write
- TTL: 30s default (`SecurityProperties.BlacklistCacheProperties.caffeineTtlSeconds`)
- Max size: 10,000 entries (`SecurityProperties.BlacklistCacheProperties.caffeineMaxSize`)

## PostgreSQL (Database — L3 Blacklist + Event Store)

- Client: Spring Data JPA (`TokenBlacklistRepository`)
- Protocol: JDBC (via Spring Data JPA)
- Used by:
  - `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Operations:
  - `tokenBlacklistRepository.existsByTokenJti(jti)` — L3 blacklist check
  - `eventStorePort.append(...)` — event store persist
  - `outboxPort.insert(...)` — outbox for Kafka relay
- Tables: `token_blacklist`, `event_store`, `event_outbox`
- Repository: `TokenBlacklistRepository` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`
- Entity: `TokenBlacklistEntity` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`

## Kafka (Message Queue — Validation Events)

- Client: `KafkaEventPublisher` (via `OutboxPoller` scheduled relay)
- Protocol: Kafka producer
- Used by: `EventService` → `OutboxPort.insert()` → `OutboxPoller` → `KafkaEventPublisher`
- Topics:
  - `iam.token.validation-failed` — validation failure events
  - `iam.token.issued` — token issuance events
  - `iam.token.revoked` — token revocation events
- Flow: `TokenEventRecorder.recordValidationFailure(event)` → `EventService.record(aggregateType="Token", aggregateId=0L, topic="iam.token.validation-failed")` → transactional persist (event store + outbox) → async relay by `OutboxPoller`
- Partition key: token JTI (or "unknown" if unavailable)

## JJWT Library (JWT Processing)

- Client: `io.jsonwebtoken.Jwts` (JJWT 0.12.x)
- Protocol: In-process API
- Used by: `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- Operations:
  - `Jwts.builder()...signWith()...compact()` — token generation
  - `Jwts.parser().verifyWith(key).clockSkewSeconds(60).build().parseSignedClaims(token)` — token parsing
- Key types: RSA KeyPair (RS256), SecretKey (HMAC-SHA256 legacy)

## NOT DETECTED

- External HTTP clients (REST/WebClient/Feign) — JWT validation is self-contained, no outbound HTTP calls
- gRPC — not used
- AMQP/RabbitMQ — not used
