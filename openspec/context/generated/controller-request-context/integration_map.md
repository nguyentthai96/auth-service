# Integration Map

_Generated: 2026-09-21_

## Redis (Cache + Rate Limiting)

- Client: `StringRedisTemplate` — `auth/application/MfaService.kt` (line 31)
- Protocol: Redis INCR + EXPIRE (sliding window rate limit)
- Purpose: MFA OTP storage, rate limiting, token blacklist

- Client: `StringRedisTemplate` — `auth/application/LoginRateLimitService.kt` (line 20)
- Protocol: Redis INCR + EXPIRE
- Purpose: Login rate limiting (fail-open strategy)

- Client: `StringRedisTemplate` — `auth/application/AnonymousRateLimitService.kt` (line 26)
- Protocol: Redis Lua script (sliding window)
- Purpose: Anonymous endpoint rate limiting

- Client: `StringRedisTemplate` — `auth/application/TokenBlacklistCacheService.kt`
- Protocol: Redis SET with TTL
- Purpose: JWT blacklist cache

## REST (External HTTP Calls)

- Client: `RestTemplate` — `auth/application/CaptchaVerifier.kt` (line 22)
- Protocol: HTTP POST
- Purpose: Google reCAPTCHA verification

## Notification Gateway (Internal Service)

- Client: `NotificationGateway` (port) — `auth/application/port/out/NotificationGateway.kt`
- Implementation: `NotificationGatewayAdapter` — `auth/adapter/out/NotificationGatewayAdapter.kt`
- Protocol: HTTP (internal service call to notification-service)
- Purpose: Send OTP, password reset links, new device alerts

## Kafka (Event Streaming)

- Consumer: `AccountStatusConsumer` — `auth/adapter/in/kafka/AccountStatusConsumer.kt`
- Topic: `iam.account.status-changed`
- Group: `auth-service`
- Purpose: Consume account status change events from account-service

## Caffeine Cache (In-Memory)

- Client: `Cache<String, String>` (Caffeine) — `auth/application/ImageCaptchaStrategy.kt` (line 42)
- Purpose: Image CAPTCHA challenge store (short TTL)

- Client: `Cache<String, String>` (Caffeine) — `auth/application/AltchaCaptchaVerifier.kt`
- Purpose: ALTCHA challenge store

## Spring Cache (L1/L2)

- `@Cacheable(cacheNames = ["roles"])` — `rbac/application/query/GetUserRolesHandler.kt` (line 25)
- `@Cacheable(cacheNames = ["permissions"])` — `rbac/application/query/GetPermissionsHandler.kt` (line 33)
- KeyGenerator: `baseCacheKeyGenerator` (from base-core)

## NOT DETECTED

- WebClient (reactive HTTP client)
- FeignClient (declarative REST client)
- RabbitMQ
- gRPC
