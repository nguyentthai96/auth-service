# Integration Map

_Generated: 2026-09-22_

## Redis (Cache + Rate Limit + Lockout)

- Client: `RedisTemplate<String, Any>` — `shared/config/RedisConfig.kt`
- Protocol: Spring Data Redis (Lettuce)
- Usages:
  - `RedisLockoutAdapter` — `auth/adapter/out/cache/RedisLockoutAdapter.kt` — lockout counter
  - `RedisCipherKeySessionResolver` — `auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` — cipher key cache
  - `RedisAntiReplayValidator` — `auth/adapter/out/cipher/RedisAntiReplayValidator.kt` — nonce validation
  - `TokenBlacklistCacheService` — `auth/application/TokenBlacklistCacheService.kt` — L1 Caffeine + L2 Redis
  - `LoginRateLimitService` — `auth/application/LoginRateLimitService.kt` — rate limiting
  - `AnonymousRateLimitService` — `auth/application/AnonymousRateLimitService.kt` — anonymous rate limit
  - `MfaRateLimitService` — `auth/application/MfaRateLimitService.kt` — MFA rate limit
  - `SecurityRuleCacheConfig` — `shared/security/SecurityRuleCacheConfig.kt` — Redis pub/sub listener

## REST (Captcha Verification)

- Client: `RestTemplate` — `auth/application/CaptchaVerifier.kt`
- Protocol: HTTP REST
- Request: Captcha verification request (inline)
- Response: Captcha verification response (inline)

## Notification Service

- Client: `NotificationPort` (interface, external lib) — `com.ntt.notification.client.NotificationPort`
- Protocol: SDK client (notification-client:0.0.1-SNAPSHOT)
- Usages:
  - `NewDeviceMailHandler` — `auth/application/NewDeviceMailHandler.kt`
  - `AccountLockoutService` — `auth/application/AccountLockoutService.kt`

## Kafka

- Protocol: Spring Kafka
- Usage: Event publishing (adapter/out/event/, adapter/in/kafka/)

## PostgreSQL

- Client: Spring Data JPA (JpaRepository interfaces)
- Protocol: JDBC via HikariCP
- Migration: Flyway

## NOT DETECTED

- FeignClient (not used)
- WebClient (not used — uses RestTemplate)
- gRPC (not used)
