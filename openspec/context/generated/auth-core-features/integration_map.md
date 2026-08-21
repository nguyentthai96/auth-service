# Integration Map

_Generated: 2026-08-25_

## Redis (Cache/Session/RateLimit)

- Client: `StringRedisTemplate` — Spring Data Redis (auto-configured)
- Protocol: Redis (TCP, default port 6379)
- Config: `RedisConfig.kt` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
  - `RedisTemplate<String, Any>` with Jackson JSON serialization
  - `StringRedisTemplate` auto-configured
- Usage locations:
  - `OtpService.kt` — OTP storage (`otp:{userId}:{channel}`, TTL 300s)
  - `MfaService.kt` — MFA session state
  - `MfaRateLimitService.kt` — MFA rate limiting (`mfa:ratelimit:*`, Lua scripts)
  - `LoginRateLimitService.kt` — Login rate limiting
  - `AnonymousRateLimitService.kt` — Anonymous rate limiting
  - `AnonymousSessionHandler.kt` — Anonymous session management
  - `RenewAnonymousTokenHandler.kt` — Anonymous token renewal
  - `AnonymousSessionDataService.kt` — Session data storage
  - `SessionPromotionService.kt` — Session promotion
  - `DecryptionVaultService.kt` — Vault decryption state
  - `RedisAntiReplayValidator.kt` — Anti-replay nonce tracking
  - `RedisCipherKeySessionResolver.kt` — Cipher key session resolution
  - `MultiTierPermissionCache.kt` — L2 permission cache
  - `AbstractTwoTierCache.kt` — Two-tier cache base
  - `IdempotencyFilter.kt` — Idempotency cache (`idempotency:auth-service:*`, TTL 24h)

## Kafka (Event Bus)

### Consumer
- Client: `PermissionChangedConsumer.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
- Protocol: Kafka
- Topic: `iam.permission.changed`
- Group ID: `auth-service`
- Config: `KafkaConfig.kt` — `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
  - DLQ via `DeadLetterPublishingRecoverer`

### Producer
- Client: `KafkaEventPublisher.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Protocol: Kafka
- Topic prefix: `iam.`
- Implements: `EventPublisher` port
- Activation: `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`
- `@Primary` — overrides `SpringEventPublisher` when Kafka available
- Retry: 3 attempts, exponential backoff (1s, 2s, 4s), max 8s
- Fallback: `SpringEventPublisher.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt` (in-process `ApplicationEventPublisher`)

## OAuth2 IdPs (REST/OIDC)

### Google
- Client chain: `SsoGateway` → `HttpSsoGateway` → `SsoProviderClient`
  - `HttpSsoGateway.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
  - `SsoProviderClient.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- Token exchange: `OAuth2TokenExchanger.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
- Protocol: OAuth2/OIDC authorization code flow
- Config: `app.security.sso.providers.google.*`

### Microsoft
- Same client chain as Google
- Config: `app.security.sso.providers.microsoft.*`

### Keycloak
- ⚠️ NOT YET in `OAuth2TokenExchanger.getTokenEndpoint()` — only Google + Microsoft endpoints hardcoded
- `SsoProviderClient` interface supports pluggable implementation

## CAPTCHA (REST)

- Port: `CaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- Adapter chain: `CaptchaVerifier` → `CaptchaGateway` → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient`
  - `CaptchaVerifier.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
  - `AltchaCaptchaVerifier.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt`
  - `CaptchaGatewayAdapter.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
  - `HttpCaptchaGateway.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - `CaptchaClient.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- Protocol: REST (HTTP)
- Provider: ALTCHA

## PostgreSQL (JPA/Hibernate)

- Protocol: JDBC/JPA
- Driver: PostgreSQL 17
- Migrations: Flyway V1-V10
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
- Tables: `users`, `user_identities`, `password_policies`, `password_history`, `refresh_tokens`, `token_blacklist`, `login_sessions`, `cipher_key_sessions`, `vault_access_logs`, `account_deletion_requests`, `account_data_exports`, `domains`, `user_domains`, `groups`, `user_groups`, `domain_roles`, `group_roles`, `actions`, `domain_resources`, `permissions`, `role_permissions`, `policies`, `policy_conditions`, `i18n_messages`

## Caffeine (In-Memory Cache)

- Client: `CaffeinePermissionCache.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/CaffeinePermissionCache.kt`
- Protocol: In-process (L1 cache)
- Wrapper: `MultiTierPermissionCache.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
- Port: `PermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt`

## Google Tink (E2EE)

- Client: `TinkCipherAlgorithmFactory.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/TinkCipherAlgorithmFactory.kt`
- Protocol: In-process library
- Usage: AES-GCM encryption, X25519 key exchange
- Key exchange: `X25519KeyExchangeServiceImpl.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/X25519KeyExchangeServiceImpl.kt`

## NOT DETECTED

- WebClient (not used — HTTP calls via `RestTemplate` or custom clients)
- FeignClient (not used)
- gRPC (not used)
