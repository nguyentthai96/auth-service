# Integration Map

_Generated: 2026-08-11_

## Spring MessageSource (i18n — NEW)

- Client: `MessageSource` interface (Spring built-in)
- Protocol: In-process (classpath resource loading)
- Request: `getMessage(code, args, locale)`
- Response: `String` (resolved message)

> Currently NOT DETECTED in codebase. To be added.

## Redis (Rate Limiting — Existing)

- Client: `StringRedisTemplate` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
- Protocol: Redis INCR + EXPIRE
- Purpose: Login rate limit tracking

## Cache (Permission — Existing)

- Client: `CaffeinePermissionCache` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/CaffeinePermissionCache.kt`
- Protocol: In-memory Caffeine cache
- Purpose: Permission caching (L1)

## REST (CAPTCHA — Existing)

- Client: `RestTemplate` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
- Protocol: HTTP POST
- Purpose: ALTCHA CAPTCHA verification

## NOT DETECTED

- MQ (Kafka/RabbitMQ) — not used in this scope
- External API clients — not relevant to i18n feature
