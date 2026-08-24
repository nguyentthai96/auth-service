# Integration Map

_Generated: 2025-07-15 (refreshed)_

## Redis (Cache + State Store)

- Config: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
  - Protocol: `spring-boot-starter-data-redis`
  - Template: `RedisTemplate<String, Any>` with JSON serialization
  - Serializers: `StringRedisSerializer` (key), `GenericJackson2JsonRedisSerializer` (value)
- Usage in auth-service (StringRedisTemplate):
  - `AnonymousSessionHandler` — `auth/application/command/AnonymousSessionHandler.kt` — Anonymous session state
  - `RenewAnonymousTokenHandler` — `auth/application/command/RenewAnonymousTokenHandler.kt` — Token renewal verification
  - `AnonymousSessionDataService` — `auth/application/AnonymousSessionDataService.kt` — Anonymous data CRUD (key: `anon:data:{sessionId}:{namespace}:{key}`)
  - `OtpService` — `auth/application/OtpService.kt` — OTP storage + verification (INCR + EXPIRE)
  - `MfaRateLimitService` — `auth/application/MfaRateLimitService.kt` — MFA attempt rate limiting (INCR + conditional check, fail-open)
  - `LoginRateLimitService` — `auth/application/LoginRateLimitService.kt` — Login attempt rate limiting
  - `AnonymousRateLimitService` — `auth/application/AnonymousRateLimitService.kt` — Anonymous session rate limiting
  - `MfaService` — `auth/application/MfaService.kt` — MFA state/partial session
  - `SessionPromotionService` — `auth/application/SessionPromotionService.kt` — Session promotion state
  - `TokenBlacklistCacheService` — `auth/application/TokenBlacklistCacheService.kt` — Blacklisted token tracking
  - `DecryptionVaultService` — `auth/application/cipher/DecryptionVaultService.kt` — JIT token storage
  - `RedisAntiReplayValidator` — `auth/adapter/out/cipher/RedisAntiReplayValidator.kt` — Nonce replay detection
  - `RedisCipherKeySessionResolver` — `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` — Cipher key session cache
- Cache (Caffeine + Redis multi-tier):
  - `CaffeinePermissionCache` — `auth/adapter/out/cache/CaffeinePermissionCache.kt` — L1 in-process cache
  - `InMemoryPermissionCache` — `auth/adapter/out/cache/InMemoryPermissionCache.kt` — Fallback cache
  - `MultiTierPermissionCache` — `auth/adapter/out/cache/MultiTierPermissionCache.kt` — L1+L2 orchestrator

## Kafka (Event Streaming)

- Consumer (auth-service): `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - Topic: `iam.permission.changed`
  - Group: `auth-service`
  - Annotation: `@KafkaListener`
- Consumer (account-service): `ProfileKafkaListener` — `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/kafka/ProfileKafkaListener.kt`
  - Listens to profile-related events from auth-service
- Publisher (auth-service): `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - Uses `KafkaTemplate<String, String>`
  - Publishes domain events via Kafka
- Publisher (local): `SpringEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
  - Local Spring ApplicationEvent publishing
- Outbox: `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
  - Uses `KafkaTemplate<String, String>`
  - Polls outbox table and publishes to Kafka (transactional outbox pattern)
- Notification: `KafkaNotificationGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/notification/KafkaNotificationGateway.kt`
  - Uses `KafkaTemplate<String, Any>`
  - Sends notification events
- Port: `EventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Interface defining event publishing contract
  - Topics: `iam.user.created`, `iam.user.sso_provisioned`, `iam.permission.changed`, `iam.audit.log`

## OAuth2 / SSO (External IdP)

- Token Exchanger: `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
  - Protocol: OAuth2/OIDC
  - Dependencies: `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`
- SSO Gateway: `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
  - Protocol: HTTP REST
  - Client: `SsoProviderClient` — `auth/adapter/out/http/SsoProviderClient.kt`
- Application: `SsoAdapter` — `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`

## CAPTCHA (External Service)

- Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - Protocol: HTTP REST
- Gateway: `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- Adapter: `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
- Verifier: `AltchaCaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt`
- Interface: `CaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`

## E2EE (End-to-End Encryption)

- Cipher Factory: `TinkCipherAlgorithmFactory` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/TinkCipherAlgorithmFactory.kt`
  - Library: Google Tink + AWS KMS
- Key Exchange: `X25519KeyExchangeServiceImpl` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/X25519KeyExchangeServiceImpl.kt`
  - Protocol: X25519 ECDH
- Decryption: `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
- Version Negotiation: `CipherVersionNegotiator` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/CipherVersionNegotiator.kt`
- Encrypted Audit: `EncryptedAuditService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/EncryptedAuditService.kt`

## PostgreSQL (Persistence)

- ORM: Spring Data JPA + Hibernate
- Migration: Flyway
- Driver: `org.postgresql:postgresql` (runtimeOnly)
- Separate DB per service
- auth-service Persistence Adapters:
  - `UserPersistenceAdapter` — `auth/adapter/out/persistence/UserPersistenceAdapter.kt`
  - `DomainPersistenceAdapter` — `auth/adapter/out/persistence/DomainPersistenceAdapter.kt`
  - `TokenStorePersistenceAdapter` — `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt`
  - `EventStorePersistenceAdapter` — `auth/adapter/out/persistence/EventStorePersistenceAdapter.kt`
  - `OutboxPersistenceAdapter` — `auth/adapter/out/persistence/OutboxPersistenceAdapter.kt`

## HTTP Client Config

- Config: `HttpClientConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`

## NOT DETECTED

- RabbitMQ / ActiveMQ
- gRPC
- GraphQL
- WebSocket
- WebClient (uses RestTemplate / HTTP client approach)
- AWS SDK (except KMS via Tink)
- External REST API clients beyond SSO/CAPTCHA
- Redis usage in account-service (NOT DETECTED — may inherit from base)
- Redis usage in system-admin-service (NOT DETECTED — may use AbstractTwoTierCache from base-core)
