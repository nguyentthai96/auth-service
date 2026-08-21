# Integration Map

_Generated: 2026-08-26_

## Redis (Distributed Cache)

- Client: `StringRedisTemplate` (Spring Data Redis) — injected in 12+ services
  - `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
  - `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
  - `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt`
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt`
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - `IdempotencyFilter` — `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - `RedisAntiReplayValidator` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisAntiReplayValidator.kt`
  - `RedisCipherKeySessionResolver` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
- Protocol: Redis (Lettuce driver via Spring Data Redis)
- Configuration: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Key patterns: `otp:{userId}:{channel}`, `session:*`, `rate_limit:*`, `cipher:*`

## Kafka (Event Bus)

- Client: `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka (spring-kafka)
- Configuration: `KafkaConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
- Topics: `iam.token.issued`, `iam.token.revoked`, `iam.user.registered`, `iam.user.logged_in`
- Pattern: Transactional outbox → `OutboxPoller` polls outbox table → publishes to Kafka

## PostgreSQL (Database)

- Client: JPA/Hibernate via Spring Data JPA
- Entities: `EventStoreEntity`, `EventOutboxEntity`, `TokenBlacklistEntity`, `RefreshTokenEntity`, `LoginSessionEntity`, `UserEntity`, etc.
- Repositories: `TokenBlacklistRepository`, `RefreshTokenRepository`, `LoginSessionRepository`, `EventOutboxJpaRepository`, `ProcessedEventJpaRepository`
- Migration: Flyway
- Configuration: via `base-data-starter` (base-core module)

## HTTP Clients (External)

- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
  - Protocol: HTTP
  - Purpose: SSO token exchange with external providers (Google, Keycloak)
  - Client: `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
  - Request/Response: provider-specific OAuth2 token/userinfo

- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - Protocol: HTTP
  - Purpose: CAPTCHA verification
  - Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`

## base-core Module (Library)

- `base-web-starter`: Web configuration, BaseControllerAdvice
- `base-data-starter`: JPA/Flyway auto-configuration
- `base-security-starter`: Security base configuration
- `base-cache-starter`: TwoLevelCacheManager, CacheInvalidationPublisher, CacheProperties
- `com.ntt.basecore.exception.BusinessException`: Base exception class
- `com.ntt.basecore.exception.base.ErrorCodeBase`: Error code interface
- `com.ntt.basecore.domain.web.BaseControllerAdvice`: Base controller advice
- Configuration: `SecurityConfig.twoLevelCacheManager()` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` (L106-116)

## NOT DETECTED

- gRPC integrations
- GraphQL integrations
- SOAP/XML integrations
- WebSocket integrations
