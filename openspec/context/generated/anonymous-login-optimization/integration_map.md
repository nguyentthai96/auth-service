# Integration Map

_Generated: 2025-07-15_

## Redis (Cache — Primary Integration)

- Client: `StringRedisTemplate` — auto-configured Spring bean
- Protocol: Redis (Lettuce driver, Spring Data Redis)
- Used by:
  - `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — opsForHash().putAll(), expire()
  - `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — opsForValue().set/get(), execute() with SCAN, hasKey(), delete(), getExpire()
  - `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — opsForValue().increment(), expire(), getExpire()
  - `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — opsForValue().setIfAbsent(), delete()
  - `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — hasKey(), opsForHash().get/put(), expire()
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` — same INCR+EXPIRE pattern
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — same StringRedisTemplate usage
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - `DecryptionVaultService` — `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt`
  - `RedisAntiReplayValidator` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisAntiReplayValidator.kt`
  - `RedisCipherKeySessionResolver` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt`
  - `MultiTierPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt`
  - `AbstractTwoTierCache` — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - `IdempotencyFilter` — `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`

### Optimization-Specific Redis APIs (NEW — to be introduced)

- `executePipelined(RedisCallback)` — NOT DETECTED in current codebase. Will be used for:
  - AnonymousSessionHandler: pipeline HSET+EXPIRE
  - AnonymousSessionDataService: pipeline MGET+MSET in transferData()
- `execute(RedisScript<T>, keys, args)` — NOT DETECTED in current codebase. Will be used for:
  - AnonymousRateLimitService: sliding window Lua script
  - SessionPromotionService: safe lock release Lua script
  - AnonymousSessionDataService: atomic check-and-set (TOCTOU fix)
- `DefaultRedisScript<Long>` — NOT DETECTED in current codebase. Will be configured in new RedisLuaScriptConfig.

## PostgreSQL (Database)

- Client: JPA repositories (Spring Data JPA)
- Protocol: JDBC / Hibernate
- Used by:
  - `TokenBlacklistRepository` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` — save() for blacklisting tokens during promotion/renewal
  - `TokenBlacklistEntity` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt` — entity with fields: tokenJti, userId, reason, expiresAt, revokedAt
  - **OPTIMIZATION**: SessionCleanupScheduler to be extended with DELETE FROM token_blacklist WHERE expires_at < NOW()

## CAPTCHA (External HTTP)

- Client: `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
- Implementation: `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- Protocol: HTTP REST
- Not directly related to optimization — used for optional abuse prevention.

## Micrometer (Metrics/Observability)

- Client: `MeterRegistry` — auto-configured Spring bean
- Protocol: In-memory registry → exporters
- Used by: All anonymous services (counters, timers)
- **OPTIMIZATION**: Add `Observation.createNotStarted()` for tracing spans

## NOT DETECTED

- Kafka integration for anonymous services (not used)
- External HTTP calls from anonymous services (except CAPTCHA — optional)
- Message queue integration for anonymous services
