# Integration Map

_Generated: 2026-09-11_

## SMTP (Email)

- Client: `JavaMailSender` (Spring Boot auto-configured) — `auth/application/MailJobScheduler.kt`
- Protocol: SMTP
- Request: `MimeMessage` (via `MimeMessageHelper`)
- Response: void (success/exception)

## Kafka (Event Publish)

- Client: `KafkaTemplate<String, String>` — `auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka Producer
- Request: key + JSON payload
- Response: `CompletableFuture<SendResult>`

## Kafka (Notification Gateway)

- Client: `KafkaTemplate<String, Any>` — `auth/adapter/out/notification/KafkaNotificationGateway.kt`
- Protocol: Kafka Producer
- Request: topic + JSON payload
- Response: async

## Kafka (Outbox Relay)

- Client: `KafkaTemplate<String, String>` — `auth/adapter/out/event/OutboxPoller.kt`
- Protocol: Kafka Producer (poll DB → publish)
- Request: event_outbox records → Kafka topic
- Response: async

## Redis (Cache / Session)

- Client: `StringRedisTemplate` — multiple services
  - `auth/application/AnonymousSessionDataService.kt`
  - `auth/application/MfaService.kt`
  - `auth/application/TokenBlacklistCacheService.kt`
  - `auth/application/LoginRateLimitService.kt`
  - `auth/application/AnonymousRateLimitService.kt`
  - `auth/application/OtpService.kt`
  - `auth/application/SessionPromotionService.kt`
  - `auth/application/cipher/DecryptionVaultService.kt`
  - `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
  - `auth/adapter/out/cipher/RedisAntiReplayValidator.kt`
  - `shared/filter/IdempotencyFilter.kt`
  - `shared/security/SecurityRuleController.kt`
- Protocol: Redis (Lettuce driver)
- Request: SET/GET/DEL with TTL
- Response: String values

## Redis (Config)

- Client: `RedisTemplate<String, Any>` — `shared/config/RedisConfig.kt`
- Protocol: Redis
- Request: Generic key-value operations
- Response: Any

## HTTP (Captcha)

- Client: `RestTemplate` — `auth/application/CaptchaVerifier.kt`
- Client: `CaptchaClient` (declarative HTTP interface) — `auth/adapter/out/http/CaptchaClient.kt`
- Protocol: HTTP REST
- Request: POST with captcha token
- Response: JSON verification result

## SSO (OAuth2)

- Client: `RestTemplate` — `auth/adapter/out/sso/OAuth2TokenExchanger.kt`
- Protocol: HTTP REST (OAuth2 token exchange)
- Request: POST with auth code / refresh token
- Response: OAuth2 token JSON

## PostgreSQL (JPA)

- Client: JPA repositories (Spring Data JPA)
- Protocol: JDBC → PostgreSQL
- Request: SQL queries (native + JPQL)
- Response: Entity objects

## NOT DETECTED

- gRPC integrations
- WebSocket integrations
- External SMS/Push/OTT provider integrations (future — not yet implemented)
