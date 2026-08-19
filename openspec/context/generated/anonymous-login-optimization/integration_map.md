# Integration Map: anonymous-login-optimization

> _Generated: 2025-01-20_
> Candidate Service: auth-service (`src/main/kotlin/com/ntt/authservice/`)

---

## 1. Redis Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `RedisConfig` | Configuration | `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt` | Configures `RedisTemplate<String, Any>` with `StringRedisSerializer` (key) + `GenericJackson2JsonRedisSerializer` (value/hash-value) |
| `StringRedisTemplate` | Auto-configured | Spring Boot auto-config | Used by `LoginRateLimitService`, `MfaRateLimitService` for atomic INCR+EXPIRE |
| `LoginRateLimitService` | Rate limiting | `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` | Redis keys: `rate:login:attempts:{dim}:{key}`, `rate:login:lock:{dim}:{key}`. Operations: `opsForValue().increment()`, `expire()`, `hasKey()`, `delete()`. Fail-open on Redis failure. |
| `MfaRateLimitService` | Rate limiting | `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` | Redis keys: `mfa:verify:attempts:{userId}:{type}`, `mfa:verify:lock:{userId}:{type}`. Same INCR+EXPIRE pattern. Fail-open. |

### Redis Key Namespaces (Existing)

| Prefix | Purpose | TTL | Service |
|--------|---------|-----|---------|
| `rate:login:attempts:` | Login rate limit counters | Window-based (60-3600s) | `LoginRateLimitService` |
| `rate:login:lock:` | Login rate limit locks | Lock duration (300-3600s) | `LoginRateLimitService` |
| `mfa:verify:attempts:` | MFA attempt counters | Window-based (900-3600s) | `MfaRateLimitService` |
| `mfa:verify:lock:` | MFA account locks | Lock duration (1800-3600s) | `MfaRateLimitService` |

### Redis Key Namespaces (Planned for Anonymous Feature)

| Prefix | Purpose | TTL | Service |
|--------|---------|-----|---------|
| `anon:session:` | Anonymous session metadata (Hash) | 24h (configurable) | `AnonymousSessionDataService` (ADD) |
| `anon:data:` | Anonymous session data (String) | Same as session TTL | `AnonymousSessionDataService` (ADD) |
| `anon:lock:` | Promotion distributed lock | 30s | `SessionPromotionService` (ADD) |
| `anon:rate:` | Anonymous creation rate limit | 1h window | `AnonymousRateLimitService` (ADD) |

---

## 2. PostgreSQL / JPA Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `TokenBlacklistRepository` | Repository | `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` (line 76) | `JpaRepository<TokenBlacklistEntity, Long>`, method `existsByTokenJti(jti: String): Boolean` |
| `TokenBlacklistEntity` | Entity | `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt` (line 107) | Extends `SnowflakeBaseEntity`, table `token_blacklist`, fields: `tokenJti`, `reason`, `expiresAt` |
| `TokenStore` | Port | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt` | Outbound port for token persistence (refresh token hash storage) |
| `UserPort` | Port | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt` | Outbound port for user CRUD |
| `DomainPort` | Port | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainPort.kt` | Outbound port for domain lookup |

---

## 3. CAPTCHA Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `CaptchaGateway` | Port (interface) | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt` | `verify(token: String): Boolean` |
| `AltchaCaptchaVerifier` | Implementation | `src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt` | ALTCHA proof-of-work CAPTCHA, HMAC-based challenge generation |
| Config | Properties | `SecurityProperties.CaptchaProperties` | `provider`, `secretKey`, `siteKey`, `verifyUrl`, `altcha.*` |

---

## 4. SSO Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `SsoGateway` | Port (interface) | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt` | SSO provider gateway |
| `SsoAdapter` | Service | `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` | SSO integration orchestration |
| `SsoController` | Controller | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt` | SSO callback + provider listing endpoints |

---

## 5. Event / Messaging Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `EventPublisher` | Port (interface) | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt` | Domain event publishing port |
| `ApplicationEventPublisher` | Spring built-in | Used by `MfaRateLimitService` | Publishes `RateLimitExceededEvent` for alerting |
| `RateLimitExceededEvent` | Event | `src/main/kotlin/com/ntt/authservice/auth/application/event/RateLimitExceededEvent.kt` | Rate limit exceeded notification |
| `NewDeviceLoginEvent` | Event | `src/main/kotlin/com/ntt/authservice/auth/application/event/NewDeviceLoginEvent.kt` | New device detected during login |
| Kafka consumer | Adapter | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/` | Inbound Kafka message handling |

---

## 6. Audit Integration

| Component | Type | File Path | Details |
|-----------|------|-----------|---------|
| `AuditLogService` | Service | `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` | Centralized audit logging. Used by `MfaRateLimitService` for lock events. |

---

## 7. External Dependencies (Libraries)

| Library | Package | Usage | Evidence |
|---------|---------|-------|---------|
| `base-core` | `com.ntt.basecore` | `BusinessException`, `ErrorCodeBase`, `BaseControllerAdvice` | `AuthException` extends `BusinessException` |
| `eventsourcing-utils` | `com.ntt.eventsourcingutils` | `CommandHandler<C,R>`, `Command<R>` | `LoginHandler implements CommandHandler<LoginCommand, LoginResult>` |
| JJWT | `io.jsonwebtoken` | JWT generation + validation | `Jwts.builder()`, `Jwts.parser()`, `Jwts.SIG.RS256` |
| Spring Security | `org.springframework.security` | Auth framework | `SecurityFilterChain`, `OncePerRequestFilter` |
| Spring Data Redis | `org.springframework.data.redis` | Redis operations | `StringRedisTemplate`, `RedisTemplate` |
| Spring Data JPA | `org.springframework.data.jpa` | JPA repositories | `JpaRepository<>` |
