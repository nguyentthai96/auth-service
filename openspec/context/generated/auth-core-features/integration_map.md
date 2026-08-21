# Integration Map — auth-core-features

> Generated: 2026-08-26
> Scope: `auth-service` (primary candidate service)
> Source: Code scan — grep patterns for REST/Cache/MQ/DB integration points

---

## 1. Redis (Cache / Session Store)

| Class | Path | Protocol | Usage |
|-------|------|----------|-------|
| `RedisConfig` | `shared/config/RedisConfig.kt` | Redis | `RedisTemplate<String, Any>` (JSON serialization) + `StringRedisTemplate` |
| `RedisLuaScriptConfig` | `shared/config/RedisLuaScriptConfig.kt` | Redis | [NEW] Lua script registration for atomic Redis operations |
| `OtpService` | `auth/application/OtpService.kt` | Redis | OTP storage: `otp:{userId}:{channel}`, TTL 300s |
| `MfaService` | `auth/application/MfaService.kt` | Redis | MFA pending setup: `mfa:setup:{userId}`, TOTP secret temp storage |
| `MfaRateLimitService` | `auth/application/MfaRateLimitService.kt` | Redis | Rate limiting: `mfa:ratelimit:*`, sliding window |
| `LoginRateLimitService` | `auth/application/LoginRateLimitService.kt` | Redis | Login rate limiting |
| `AnonymousRateLimitService` | `auth/application/AnonymousRateLimitService.kt` | Redis | Anonymous token rate limiting |
| `AnonymousSessionHandler` | `auth/application/command/AnonymousSessionHandler.kt` | Redis | Anonymous session data storage |
| `RenewAnonymousTokenHandler` | `auth/application/command/RenewAnonymousTokenHandler.kt` | Redis | Anonymous token renewal tracking |
| `AnonymousSessionDataService` | `auth/application/AnonymousSessionDataService.kt` | Redis | Anonymous session data CRUD |
| `SessionPromotionService` | `auth/application/SessionPromotionService.kt` | Redis | Session promotion data transfer |
| `DecryptionVaultService` | `auth/application/cipher/DecryptionVaultService.kt` | Redis | E2EE vault key storage |
| `RedisAntiReplayValidator` | `auth/adapter/out/cipher/RedisAntiReplayValidator.kt` | Redis | Anti-replay nonce tracking |
| `RedisCipherKeySessionResolver` | `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` | Redis | Cipher key session resolution |
| `IdempotencyFilter` | `shared/filter/IdempotencyFilter.kt` | Redis | Idempotency: `idempotency:auth-service:*`, TTL 24h |

## 2. Kafka (Message Queue)

### Consumers

| Class | Path | Topic | Group ID | Notes |
|-------|------|-------|----------|-------|
| `PermissionChangedConsumer` | `auth/adapter/in/kafka/PermissionChangedConsumer.kt` | `iam.permission.changed` | `auth-service` | Cache invalidation |

### Producers

| Class | Path | Condition | Notes |
|-------|------|-----------|-------|
| `KafkaEventPublisher` | `auth/adapter/out/event/KafkaEventPublisher.kt` | `@ConditionalOnProperty("spring.kafka.bootstrap-servers")` | `@Primary`, retry 3 attempts, exponential backoff. Publishes domain events to topic-prefixed channels. |
| `SpringEventPublisher` | `auth/adapter/out/event/SpringEventPublisher.kt` | Fallback when Kafka not configured | In-process `ApplicationEventPublisher` |
| `OutboxPoller` | `auth/adapter/out/event/OutboxPoller.kt` | `@ConditionalOnProperty("spring.kafka.bootstrap-servers")` | [NEW] Transactional outbox relay — SELECT FOR UPDATE SKIP LOCKED, batch=50, poll=100ms. Timeout handling (no retry count increment), confirmed failure handling (retry count increment, max 3). |

### Kafka Topics (Known)

| Topic | Direction | Source/Consumer |
|-------|-----------|----------------|
| `iam.permission.changed` | IN | `PermissionChangedConsumer` |
| `iam.user.sso_provisioned` | OUT | `KafkaEventPublisher` / `OutboxPoller` |
| `iam.token.issued` | OUT | `OutboxPoller` (via `TokenEventRecorder`) |
| `iam.token.revoked` | OUT | `OutboxPoller` (via `TokenEventRecorder`) |
| `iam.account.deactivated` | OUT | `KafkaEventPublisher` |
| `iam.account.deleted` | OUT | `KafkaEventPublisher` |
| `iam.audit.event` | OUT | `KafkaEventPublisher` |

### Kafka Config

| Class | Path | Notes |
|-------|------|-------|
| `KafkaConfig` | `shared/config/KafkaConfig.kt` | DLQ via `DeadLetterPublishingRecoverer`, consumer factory, producer factory |
| `OutboxProperties` | `shared/config/OutboxProperties.kt` | [NEW] Outbox poller config: pollIntervalMs=100, batchSize=50, maxRetries=3, kafkaTimeoutMs=5000 |

## 3. OAuth2 / SSO (HTTP Clients)

| Class | Path | Protocol | Target |
|-------|------|----------|--------|
| `OAuth2TokenExchanger` | `auth/adapter/out/sso/OAuth2TokenExchanger.kt` | HTTPS (OAuth2) | Google + Microsoft token/userinfo endpoints |
| `SsoProviderClient` | `auth/adapter/out/http/SsoProviderClient.kt` | HTTPS | SSO provider HTTP client |
| `HttpSsoGateway` | `auth/adapter/out/http/HttpSsoGateway.kt` | HTTPS | SSO gateway implementation |

## 4. CAPTCHA (HTTP Client)

| Class | Path | Protocol | Target |
|-------|------|----------|--------|
| `CaptchaClient` | `auth/adapter/out/http/CaptchaClient.kt` | HTTPS | ALTCHA verification endpoint |
| `HttpCaptchaGateway` | `auth/adapter/out/http/HttpCaptchaGateway.kt` | HTTPS | CAPTCHA gateway implementation |
| `CaptchaGatewayAdapter` | `auth/adapter/out/gateway/CaptchaGatewayAdapter.kt` | — | Port adapter bridge |

## 5. PostgreSQL (Database / JPA)

| Adapter | Path | Purpose |
|---------|------|---------|
| `UserPersistenceAdapter` | `auth/adapter/out/persistence/UserPersistenceAdapter.kt` | User CRUD via `UserPort` |
| `TokenStorePersistenceAdapter` | `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` | Token store via `TokenStore` |
| `DomainPersistenceAdapter` | `auth/adapter/out/persistence/DomainPersistenceAdapter.kt` | Domain lookup via `DomainPort` |
| `EventStorePersistenceAdapter` | `auth/adapter/out/persistence/EventStorePersistenceAdapter.kt` | [NEW] Event store via `EventStorePort` |
| `OutboxPersistenceAdapter` | `auth/adapter/out/persistence/OutboxPersistenceAdapter.kt` | [NEW] Outbox via `OutboxPort` |

## 6. In-Process Libraries

| Library | Version | Usage | Class |
|---------|---------|-------|-------|
| `dev.samstevens.totp` | 1.7.1 | TOTP generation/verification | `TotpService.kt` |
| `org.passay` | 1.6.4 | Password policy validation | `PasswordPolicyService.kt` |
| Google Tink | — | AES-GCM encryption, X25519 key exchange | `TinkCipherAlgorithmFactory.kt`, `X25519KeyExchangeServiceImpl.kt` |

## 7. HTTP Client Config

| Class | Path | Notes |
|-------|------|-------|
| `HttpClientConfig` | `shared/config/HttpClientConfig.kt` | WebClient/RestTemplate configuration — timeouts, retry, connection pooling |

## 8. Observability

| Class | Path | Notes |
|-------|------|-------|
| `ObservabilityConfig` | `shared/config/ObservabilityConfig.kt` | [NEW] Observability/tracing configuration |
