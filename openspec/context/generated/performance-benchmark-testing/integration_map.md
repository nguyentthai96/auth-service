# Integration Map

_Generated: 2026-09-08_

## PostgreSQL (JDBC/JPA)

- Client: Spring Data JPA via HikariCP connection pool
- Protocol: JDBC (`jdbc:postgresql://localhost:5432/auth_db`)
- Config: `src/main/resources/application-db.yml`
- Entities: `auth/adapter/out/persistence/entity/`
- Repositories: `auth/adapter/out/persistence/repository/`

## Redis (Lettuce)

- Client: StringRedisTemplate, RedisTemplate<String, Any>
- Protocol: Redis protocol (`redis://localhost:6379`)
- Config: `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Usage:
  - OtpService — `auth/application/OtpService.kt`
  - LoginRateLimitService — `auth/application/LoginRateLimitService.kt`
  - MfaRateLimitService — `auth/application/MfaRateLimitService.kt`
  - AnonymousRateLimitService — `auth/application/AnonymousRateLimitService.kt`
  - TokenBlacklistCacheService — `auth/application/TokenBlacklistCacheService.kt`
  - IdempotencyFilter — `shared/filter/IdempotencyFilter.kt`
  - DecryptionVaultService — `auth/application/cipher/DecryptionVaultService.kt`
  - RedisCipherKeySessionResolver — `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
  - RedisAntiReplayValidator — `auth/adapter/out/cipher/RedisAntiReplayValidator.kt`
  - AnonymousSessionDataService — `auth/application/AnonymousSessionDataService.kt`
  - SessionPromotionService — `auth/application/SessionPromotionService.kt`

## SSO Provider (HTTP)

- Client: SsoProviderClient — `auth/adapter/out/http/SsoProviderClient.kt`
- Protocol: REST (@HttpExchange)
- Gateway: HttpSsoGateway — `auth/adapter/out/http/HttpSsoGateway.kt`
- Response: SsoTokenResponse, SsoUserInfoResponse — `SsoProviderClient.kt`

## Captcha Provider (HTTP)

- Client: CaptchaClient — `auth/adapter/out/http/CaptchaClient.kt`
- Protocol: REST (@HttpExchange)
- Gateway: HttpCaptchaGateway — `auth/adapter/out/http/HttpCaptchaGateway.kt`
- Response: CaptchaVerifyResponse — `CaptchaClient.kt`

## Kafka (Event)

- Consumer: `auth/adapter/in/kafka/`
- Producer: `auth/adapter/out/event/`
- Protocol: Kafka

## Prometheus (NEW — to be added)

- NOT DETECTED — needs integration

## Grafana (NEW — to be added)

- NOT DETECTED — needs integration

## NOT DETECTED

- gRPC integrations
- GraphQL integrations
- SOAP services
