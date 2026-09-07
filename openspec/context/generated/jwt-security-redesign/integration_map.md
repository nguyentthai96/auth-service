# Integration Map

_Generated: 2026-09-07_

## Redis (Cache — L2 Blacklist)

- Client: `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
- Protocol: `StringRedisTemplate` (Spring Data Redis)
- Key pattern: `token:blacklist:{jti}` with TTL = remaining token lifetime
- Configurable: `SecurityProperties.BlacklistCacheProperties` (redisTimeoutMs, redisKeyPrefix, circuitBreakerThreshold)

## Caffeine (Cache — L1 Blacklist)

- Client: `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
- Protocol: Caffeine programmatic cache (in-process)
- Configuration: `caffeineTtlSeconds=30`, `caffeineMaxSize=10000`

## Kafka (Event Publishing)

- Client: `KafkaNotificationGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/notification/KafkaNotificationGateway.kt`
- Protocol: `KafkaTemplate<String, String>` (Spring Kafka)
- Topics: notification topics for OTP, password reset
- Conditional: `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`
- Outbox relay: `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`

## PostgreSQL (Primary Data Store)

- Client: JPA Repositories (Spring Data JPA)
- Key repositories:
  - `LoginSessionRepository` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/LoginSessionRepository.kt`
  - `EventOutboxJpaRepository` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/EventOutboxJpaRepository.kt`
- Pattern: `FOR UPDATE SKIP LOCKED` for multi-instance safety

## SMTP (Email — NEW)

- Client: `JavaMailSender` (Spring Boot starter-mail) — TO BE ADDED
- Protocol: SMTP
- Configuration: `spring.mail.*` properties
- Note: Not yet integrated — required for MailJobScheduler

## SSO Providers (OAuth2/OIDC)

- Client: `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- Protocol: REST (WebClient)
- Request: OAuth2 token exchange
- Response: `SsoTokenResponse`, `SsoUserInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`

## CAPTCHA (Verification)

- Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- Protocol: REST
- Response: `CaptchaVerifyResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`

## NOT DETECTED

- External message queue (RabbitMQ)
- External file storage
- gRPC services
