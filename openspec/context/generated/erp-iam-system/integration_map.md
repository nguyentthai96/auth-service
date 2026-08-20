# Integration Map

_Generated: 2026-08-05 (refreshed)_

## Redis (Cache + State Store)

- Config: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
  - Protocol: `spring-boot-starter-data-redis`
  - Template: `RedisTemplate<String, Any>` with JSON serialization
  - Serializers: `StringRedisSerializer` (key), `GenericJackson2JsonRedisSerializer` (value)
- Usage (StringRedisTemplate):
  - `AnonymousSessionHandler` — `auth/application/command/AnonymousSessionHandler.kt` — Anonymous session state
  - `RenewAnonymousTokenHandler` — `auth/application/command/RenewAnonymousTokenHandler.kt` — Token renewal verification
  - `AnonymousSessionDataService` — `auth/application/AnonymousSessionDataService.kt` — Anonymous data CRUD (key: `anon:data:{sessionId}:{namespace}:{key}`)
  - `OtpService` — `auth/application/OtpService.kt` — OTP storage + verification (INCR + EXPIRE)
  - `MfaRateLimitService` — `auth/application/MfaRateLimitService.kt` — MFA attempt rate limiting (INCR + conditional check, fail-open)
  - `DecryptionVaultService` — `auth/application/cipher/DecryptionVaultService.kt` — JIT token storage
  - `RedisAntiReplayValidator` — `auth/adapter/out/cipher/RedisAntiReplayValidator.kt` — Nonce replay detection
  - `RedisCipherKeySessionResolver` — `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` — Cipher key session cache
- Cache (Caffeine + Redis multi-tier):
  - `CaffeinePermissionCache` — `auth/adapter/out/cache/CaffeinePermissionCache.kt` — L1 in-process cache (30s TTL)
  - `InMemoryPermissionCache` — `auth/adapter/out/cache/InMemoryPermissionCache.kt` — Fallback cache
  - `MultiTierPermissionCache` — `auth/adapter/out/cache/MultiTierPermissionCache.kt` — L1+L2 orchestrator

## Kafka (Event Streaming)

- Consumer: `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - Topic: `iam.permission.changed`
  - Group: `auth-service`
  - Annotation: `@KafkaListener`
- Publisher: `SpringEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`
  - Currently: Spring ApplicationEvent (local)
  - Future: Kafka publisher (Phase 3 migration comment in code)
- Port: `EventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Interface: defines `publishUserCreated()`, `publishSsoProvisioned()` etc.
  - Topics planned: `iam.user.created`, `iam.user.sso_provisioned`
- Dependency: `compileOnly("org.springframework.kafka:spring-kafka")` — needs upgrade to `implementation`

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
  - Library: Google Tink 1.15.0 + AWS KMS
- Key Exchange: `X25519KeyExchangeServiceImpl` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/X25519KeyExchangeServiceImpl.kt`
  - Protocol: X25519 ECDH
  - Persists to both Redis and JPA
- Decryption: `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
- Version Negotiation: `CipherVersionNegotiator` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/CipherVersionNegotiator.kt`

## PostgreSQL (Persistence)

- ORM: Spring Data JPA + Hibernate
- Migration: Flyway (9 migrations: V1-V9)
- Driver: `org.postgresql:postgresql` (runtimeOnly)
- Persistence Adapters:
  - `UserPersistenceAdapter` — `auth/adapter/out/persistence/UserPersistenceAdapter.kt`
  - `DomainPersistenceAdapter` — `auth/adapter/out/persistence/DomainPersistenceAdapter.kt`
  - `TokenStorePersistenceAdapter` — `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt`

## HTTP Client Config

- Config: `HttpClientConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`

## NOT DETECTED

- RabbitMQ / ActiveMQ
- gRPC
- GraphQL
- WebSocket
- AWS SDK (except KMS via Tink)
- External REST API clients beyond SSO/CAPTCHA
