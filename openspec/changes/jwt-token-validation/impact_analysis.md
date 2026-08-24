# Impact Analysis: jwt-token-validation

_Generated: 2025-01-20_
_Type: MAINTENANCE_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [ValidationFailureReason.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt) | L1-18 (full file) | Enum class — add `ISSUER_MISMATCH` and `CLAIM_VALIDATION_FAILED` values |
| 2 | [JwtAuthFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | L183-188 (mapValidatorToReason), L1-10 (imports), L30-40 (constructor) | Fix mapValidatorToReason bug + inject MeterRegistry + add metric instrumentation |
| 3 | [TokenBlacklistCacheService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt) | L1-10 (imports), L20-30 (constructor), L51-75 (isBlacklisted), L97-104 (addToBlacklist), L120-137 (circuit breaker) | Inject MeterRegistry + add counters/gauge at lookup points and circuit breaker transitions |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### `JwtAuthFilter.mapValidatorToReason()` (L183-188) — BUG FIX

```
⟶ mapValidatorToReason(validatorName: String)
├── "IssuerClaimValidator"? → ISSUER_MISMATCH          // ← NEW: was falling to else
├── "AudienceClaimValidator"? → AUDIENCE_MISMATCH       // unchanged
├── "TokenTypeClaimValidator"? → TYPE_REJECTED           // unchanged
└── else → CLAIM_VALIDATION_FAILED                       // ← NEW: was SIGNATURE_INVALID
```

#### `TokenBlacklistCacheService.isBlacklisted()` — METRICS INSTRUMENTATION

```
⟶ isBlacklisted(jti: String): Boolean
├── caffeineCache.getIfPresent(jti) != null?
│   ├── true → counter("auth.token.blacklist.lookup", tier=l1_caffeine, result=hit).increment()  // ← NEW
│   └── return true
├── counter("auth.token.blacklist.lookup", tier=l1_caffeine, result=miss).increment()             // ← NEW
├── circuitBreakerOpen?
│   ├── true → skip Redis
│   └── false → redisTemplate.hasKey("token:blacklist:{jti}")?
│       ├── true → counter(tier=l2_redis, result=hit).increment()                                 // ← NEW
│       │   └── caffeineCache.put(jti, true) + return true
│       ├── false → counter(tier=l2_redis, result=miss).increment()                               // ← NEW
│       └── exception → incrementFailureCount()
│           └── threshold reached? → counter("circuit_breaker", transition=opened).increment()     // ← NEW
├── tokenBlacklistRepository.existsByTokenJti(jti)?
│   ├── true → counter(tier=l3_db, result=hit).increment()                                        // ← NEW
│   │   └── caffeineCache.put(jti, true) + return true
│   └── false → counter(tier=l3_db, result=miss).increment()                                      // ← NEW
└── return false
```

#### `JwtAuthFilter.doFilterInternal()` — METRICS INSTRUMENTATION

```
⟶ doFilterInternal(request, response, filterChain)
├── extractToken(request) == null?
│   └── filterChain.doFilter() + return                  // no metrics (no token)
├── Timer.Sample startTimer = Timer.start(meterRegistry)  // ← NEW: start timing
├── jwtService.parseToken(token)
│   └── exception? → counter("auth.token.validation", result=failure, reason=signature_invalid)  // ← NEW
│       └── recordValidationFailure() + filterChain.doFilter()
├── tokenBlacklistCacheService.isBlacklisted(jti)?
│   └── true → counter("auth.token.validation", result=failure, reason=blacklisted)              // ← NEW
│       └── recordValidationFailure() + response.sendError(401)
├── claimValidatorChain.validateOrThrow(claims)
│   └── ClaimValidationException? → counter(result=failure, reason=mapValidatorToReason())       // ← NEW
│       └── recordValidationFailure() + filterChain.doFilter()
├── extractAuthorities(claims) → SecurityContext.set()
│   └── counter("auth.token.validation", result=success).increment()                             // ← NEW
│       └── startTimer.stop(timer(result=success))                                               // ← NEW
└── filterChain.doFilter()
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `ValidationFailureReason.kt` | [ValidationFailureReason](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt) | Enum used by `JwtAuthFilter.mapValidatorToReason()`, `TokenValidationFailedEvent.reason`, Kafka event serialization |
| 2 | `JwtAuthFilter.kt` | [JwtAuthFilter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | Spring Security filter chain — every HTTP request. Constructor change (+MeterRegistry) |
| 3 | `TokenBlacklistCacheService.kt` | [TokenBlacklistCacheService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt) | Injected by JwtAuthFilter, TokenController. Constructor change (+MeterRegistry) |

### 🟡 Indirect Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `TokenValidationFailedEvent.kt` | [TokenValidationFailedEvent](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt) | Uses `ValidationFailureReason` — new enum values become valid event reasons |
| 2 | `TokenController.kt` | [TokenController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | Injects `TokenBlacklistCacheService` — constructor change propagates |
| 3 | `SecurityConfig.kt` | [SecurityConfig](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Registers `JwtAuthFilter` — bean creation affected by new constructor param |

### 🟠 Cross-service Impact (1 topic)

| # | Item | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | Kafka topic `iam.token.validation-failed` | N/A (infrastructure) | Kafka | Events may now contain new `ValidationFailureReason` values (`ISSUER_MISMATCH`, `CLAIM_VALIDATION_FAILED`). Consumers should handle unknown enum values gracefully. |

### 🟢 Shared Utilities (2 items)

| # | Item | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `MeterRegistry` | Spring Boot auto-configured | `Counter.builder().register()`, `Timer.builder().register()`, `Gauge.builder().register()` |
| 2 | `SimpleMeterRegistry` | Micrometer test utility | Used in test classes for metric verification |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| MeterRegistry injection pattern | [AnonymousSessionHandler.kt:L33](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt#L33) | 100% | REUSE | 🟢 Low (0 callers affected) | Copy pattern: `private val meterRegistry: MeterRegistry` constructor param |
| SimpleMeterRegistry test pattern | [AnonymousSessionHandlerTest](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandlerTest.kt) | 100% | REUSE | 🟢 Low (0 callers affected) | Copy pattern: `val meterRegistry = SimpleMeterRegistry()` |
| Mockito test pattern | [TokenEventRecorderTest](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorderTest.kt) | 100% | REUSE | 🟢 Low (0 callers affected) | Copy pattern: `@Mock`, `@InjectMocks`, `verify()` |
| mapValidatorToReason fix | N/A (bug fix) | N/A | NEW | 🟢 Low (1 caller: JwtAuthFilter.doFilterInternal) | Fix in place — no extraction needed |
| Enum values | N/A (additive) | N/A | NEW | 🟢 Low (additive change) | Add 2 values to existing enum |

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `MeterRegistry` | Interface (Spring auto-configured) | `counter()`, `timer()`, `gauge()` | `io.micrometer.core.instrument.MeterRegistry` |
| `Counter` | Metric (from MeterRegistry) | `Counter.builder(name).tag(k,v).register(reg).increment()` | Thread-safe, ~ns cost |
| `Timer` | Metric (from MeterRegistry) | `Timer.builder(name).tag(k,v).register(reg)`, `Timer.start(reg)`, `sample.stop(timer)` | Thread-safe |
| `Gauge` | Metric (from MeterRegistry) | `Gauge.builder(name, supplier).register(reg)` | Registered once in constructor |
| `SimpleMeterRegistry` | Test utility | `SimpleMeterRegistry()` | `io.micrometer.core.instrument.simple.SimpleMeterRegistry` |
| `Caffeine Cache<String,Boolean>` | L1 cache | `getIfPresent()`, `put()`, `estimatedSize()` | `com.github.benmanes.caffeine.cache.Cache` |
| `StringRedisTemplate` | L2 cache | `hasKey()`, `opsForValue().set()` | Spring auto-configured |
| `TokenBlacklistRepository` | L3 DB | `existsByTokenJti()` | JPA repository |
| `ClaimValidatorChain` | Chain of Responsibility | `validateOrThrow()`, `validateAll()` | @Component |
| `TokenEventRecorder` | Event recorder | `recordValidationFailure()` | @Component |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|------------|---------|
| `app.security.blacklist.caffeine-ttl-seconds` | SecurityProperties | `30` | TokenBlacklistCacheService |
| `app.security.blacklist.circuit-breaker-threshold` | SecurityProperties | `5` | TokenBlacklistCacheService |
| `app.security.blacklist.circuit-breaker-reset-seconds` | SecurityProperties | `30` | TokenBlacklistCacheService |
| `app.security.jwt.issuer` | SecurityProperties | `auth-service` | IssuerClaimValidator |
| `app.security.jwt.audience` | SecurityProperties | `""` (disabled) | AudienceClaimValidator |

### Error Codes Thrown

No new error codes. Bug fix changes audit event classification only (not error responses).

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| N/A | MAINTENANCE — no new errors | N/A |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| N/A | N/A | N/A | No new DTOs needed — MAINTENANCE only |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `Counter.builder("name").tag("k","v").register(reg)` | `io.micrometer.core.instrument.Counter` | Micrometer 1.x API | ✅ |
| `Timer.start(reg)` / `sample.stop(timer)` | `io.micrometer.core.instrument.Timer` | Micrometer 1.x API | ✅ |
| `Gauge.builder("name", supplier).register(reg)` | `io.micrometer.core.instrument.Gauge` | Micrometer 1.x API | ✅ |
| `caffeineCache.estimatedSize()` | `com.github.benmanes.caffeine.cache.Cache.estimatedSize(): Long` | Caffeine 3.x API | ✅ |
