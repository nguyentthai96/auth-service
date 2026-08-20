# Integration Map

_Generated: 2026-08-20_

## Redis (Cache/Storage)

- Client: `StringRedisTemplate` — Spring Data Redis
- Protocol: Redis protocol via `spring-boot-starter-data-redis`
- Config: `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Serialization: `StringRedisSerializer` (key) + `GenericJackson2JsonRedisSerializer` (value)
- Consumers:
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt` — OTP storage (`otp:{userId}:{channel}`, TTL=300s)
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — MFA rate limiting (INCR + EXPIRE, fail-open strategy)
  - `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — anonymous session metadata
  - `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — session existence check
  - `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — session data CRUD (`anon:data:{sessionId}:{namespace}:{key}`)
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt` — JIT token storage

## Kafka (Message Queue)

- Consumer: `PermissionChangedConsumer` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
  - Topic: `iam.permission.changed`
  - GroupId: `auth-service`
  - Annotation: `@KafkaListener`
- Producer (deferred): `EventPublisher` port → `SpringEventPublisher` adapter
  - Port: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Adapter: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt` — uses in-process `ApplicationEventPublisher`
  - Planned topic: `iam.user.sso_provisioned`
  - Status: Phase 3 — will be replaced by Kafka publisher

## OAuth2 IdPs (External — SSO)

- Gateway Port: `SsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt`
- HTTP Gateway: `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- HTTP Client: `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt` (interface)
- Token Exchanger: `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
- Providers:
  - Google: `https://oauth2.googleapis.com/token` (token endpoint)
  - Microsoft: `https://login.microsoftonline.com/common/oauth2/v2.0/token` (token endpoint)
  - Keycloak: NOT YET IMPLEMENTED (configurable issuer-uri needed)
- Protocol: HTTPS, OIDC (authorization code flow)

## CAPTCHA Providers (External)

- Verifier Interface: `CaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
- Implementation: `AltchaCaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt`
- Gateway Port: `CaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- Gateway Adapter: `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
- HTTP Gateway: `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- HTTP Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt` (interface)
- Protocol: HTTPS REST API (server-side verification)

## PostgreSQL (Database)

- Protocol: JPA/Hibernate
- Config: `JpaAuditingConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/JpaAuditingConfig.kt`
- Entities: 15+ @Entity classes across rbac and auth modules
- Repositories: 21+ JpaRepository interfaces
- ID Strategy: Snowflake ID (via base-core)
- Migrations: Flyway V1-V9

## Caffeine (L1 Cache)

- `CaffeinePermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/CaffeinePermissionCache.kt`
- `InMemoryPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/InMemoryPermissionCache.kt`
- `MultiTierPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
- Port: `PermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt`
- Purpose: L1 permission cache, reducing Redis/DB lookups

## HTTP Client Configuration

- Config: `HttpClientConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`
- Purpose: Configures HTTP client for external API calls (SSO, CAPTCHA)

## NOT DETECTED

- gRPC integrations
- GraphQL integrations
- LDAP/Active Directory direct integration
- SMTP/SMS provider direct integration (adapter pattern deferred)
