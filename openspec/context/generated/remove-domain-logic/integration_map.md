# Integration Map

_Generated: 2026-10-02_

## Redis (Cache/Session)

- Client: `StringRedisTemplate` (Spring Data Redis)
- Protocol: Redis protocol (Lettuce driver)
- Classes using Redis:
  - `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
  - `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt`
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - `RedisCipherKeySessionResolver` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
  - `RedisLockoutAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/RedisLockoutAdapter.kt`
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
- Domain-related: `PermissionChangedConsumer` uses `CacheManager` with possible domain-scoped keys

## Kafka (Event Streaming)

- Client: `KafkaTemplate<String, String>`, `KafkaTemplate<String, Any>`
- Protocol: Apache Kafka
- Producers:
  - `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - `KafkaNotificationGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/notification/KafkaNotificationGateway.kt`
  - `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
- Consumers:
  - `AccountStatusConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/AccountStatusConsumer.kt`
    - Topic: `iam.account.status-changed`
  - `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
    - **Domain-related: cache key may contain domainId → needs review**

## PostgreSQL (Primary Database)

- Client: JPA (Hibernate)
- Protocol: JDBC
- Domain-related repositories:
  - `DomainRepository` — `Repositories.kt` → **DELETE**
  - `UserDomainRepository` — `Repositories.kt` → **DELETE**
  - `PasswordPolicyRepository.findByDomainId()` — `Repositories.kt` → **MODIFY**

## Two-Level Cache

- Client: `TwoLevelCacheManager` (base-core)
- Config: `SecurityConfig.kt` — L1 in-memory (ConcurrentMap), L2 null

## NOT DETECTED

- REST/HTTP client (no outbound REST calls)
- gRPC
- RabbitMQ
