# Integration Map

_Generated: 2026-08-25_

## Redis (Cache L2 + Pub/Sub)

- Client: `StringRedisTemplate` — injected across 12+ services
  - `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - `IdempotencyFilter` — `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
  - `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`
  - `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt`
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt`
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
  - `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt`
  - `RedisAntiReplayValidator` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisAntiReplayValidator.kt`
  - `RedisCipherKeySessionResolver` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
- Config: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
  - `RedisTemplate<String, Any>` with `GenericJackson2JsonRedisSerializer`
- Protocol: Redis Protocol (Lettuce client via spring-boot-starter-data-redis)

## Redis (Cache Manager — base-cache-starter)

- Client: `TwoLevelCacheManager` — `com.ntt.basecore.autoconfigure.cache.TwoLevelCacheManager`
- Config: `SecurityConfig.twoLevelCacheManager()` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` (lines 103-118)
  - Creates `@Primary` `@ConditionalOnMissingBean` TwoLevelCacheManager
  - L1: `ConcurrentMapCacheManager` (should be upgraded to CaffeineCacheManager)
  - L2: `null` (Redis not wired — needs fixing)
- Properties: `com.ntt.basecore.autoconfigure.cache.CacheProperties`
- Invalidation: `CacheInvalidationPublisher` (ObjectProvider — optional)

## Caffeine (In-Process Cache)

- Direct usage (NOT via @Cacheable / CacheManager):
  - `TokenBlacklistCacheService` — Caffeine `Cache<String, Boolean>` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - `DatabaseMessageSource` — Caffeine `Cache<String, String>` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
  - `AltchaCaptchaVerifier` — Caffeine `Cache<String, Boolean>` — `src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt`

## Kafka (Messaging)

- Consumer: `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - Topic: `iam.permission.changed`
  - GroupId: `auth-service`
  - Action: Cache eviction via CacheManager (currently `clear()`, needs targeted `evict(key)`)
  - Gate: `@ConditionalOnProperty(name = ["spring.kafka.bootstrap-servers"])`
- Consumer: `AccountStatusConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/AccountStatusConsumer.kt`
- Config: `KafkaConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
  - Dead letter recovery via `DeadLetterPublishingRecoverer`

## HTTP Clients (External)

- Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - Protocol: HTTP (REST)
  - Response: `CaptchaVerifyResponse`
- Client: `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
  - Protocol: HTTP (REST)
  - Response: `SsoTokenResponse`, `SsoUserInfoResponse`

## Micrometer (Observability)

- Config: `ObservabilityConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/ObservabilityConfig.kt`
- Dependency: `com.ntt:base-observability-starter` + `spring-boot-starter-actuator`
- Protocol: In-process MeterRegistry API

## NOT DETECTED

- gRPC integrations
- GraphQL integrations
- WebSocket integrations
