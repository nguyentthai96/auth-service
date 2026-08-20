# Integration Map

_Generated: 2025-01-20_

## Redis (Cache / Session Store)

- Client: `StringRedisTemplate` (auto-configured by Spring Boot)
- Protocol: Redis (Lettuce driver)
- Configuration: `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Usage in anonymous feature:
  - `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — session creation (`opsForHash.putAll`, `expire`)
  - `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — CRUD (`opsForValue.set/get/size`, `execute` with SCAN)
  - `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — rate limit (`opsForValue.increment`, `expire`, `getExpire`)
  - `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — lock (`opsForValue.setIfAbsent`), delete
  - `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — renewal count (`opsForHash.get/put`, `expire`)
- Redis key patterns:
  - `anon:session:{sessionId}` — Session metadata (Hash: deviceFingerprint, ipAddress, createdAt, renewalCount)
  - `anon:data:{sessionId}:{namespace}:{key}` — Session data (String)
  - `anon:rate:{ipAddress}` — Rate limit counter (String, auto-expire)
  - `anon:lock:{sessionId}` — Distributed promotion lock (String, TTL 30s)
  - `user:session_data:{userId}:{namespace}:{key}` — Promoted data destination (String)

## Redis (General — existing)

- Client: `StringRedisTemplate` (shared bean)
- Other users:
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` — login rate limiting
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — MFA rate limiting
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt` — OTP storage
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` — MFA session state
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt` — cipher key cache
  - `RedisAntiReplayValidator` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisAntiReplayValidator.kt` — nonce tracking
  - `RedisCipherKeySessionResolver` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` — key session cache

## PostgreSQL (JPA)

- Client: `TokenBlacklistRepository` (Spring Data JPA)
- Protocol: JDBC / JPA
- Entity: `TokenBlacklistEntity` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`
- Repository: `TokenBlacklistRepository` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`
- Usage: Token blacklisting after promotion (`reason=PROMOTION`) and renewal (`reason=RENEWAL`)
- Method: `existsByTokenJti(jti)` for validation, `save(entity)` for insertion

## CAPTCHA (HTTP)

- Client: `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- Implementation: `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- Adapter: `CaptchaGatewayAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt`
- Protocol: HTTP (via `@HttpExchange`)
- Usage in anonymous: Optional — CAPTCHA triggered when rate limit approaching threshold (AF-002 in BA)

## SSO (HTTP)

- Client: `SsoGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt`
- Implementation: `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- Protocol: HTTP (OAuth2/OIDC token exchange)
- Usage in anonymous: Indirect — SSO login may also trigger session promotion if `anonymousSessionId` provided

## Micrometer (Metrics)

- Client: `MeterRegistry` (auto-configured)
- Protocol: In-process metrics collection
- Usage in anonymous:
  - `AnonymousSessionHandler` — counters: `auth.anonymous.sessions.created`, timer: `auth.anonymous.token.generation.duration`
  - `AnonymousRateLimitService` — counter: `auth.anonymous.rate_limited`
  - `AnonymousSessionDataService` — counters: `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`
  - `SessionPromotionService` — counter: `auth.anonymous.sessions.promoted` (with status tag), timer: `auth.anonymous.promotion.duration`
  - `RenewAnonymousTokenHandler` — counter: `auth.anonymous.sessions.renewed`

## NOT DETECTED

- Kafka Producer (anonymous feature does not produce Kafka events)
- External HTTP API calls from anonymous flow (all interactions are Redis/PostgreSQL)
