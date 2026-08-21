# Design: jwt-token-validation

_Generated: 2025-08-26_

---

## Locked Profile

| Field | Value |
|-------|-------|
| flow | Query |
| factory | N/A |
| feature_type | EXTEND |
| transaction_flow | Query — token validation is read-only per-request check |

---

## 1. Architecture Overview

This feature extends the existing JWT validation pipeline in auth-service following Clean Architecture (hexagonal ports/adapters). All changes are additive or modify existing components — no new modules or packages beyond what's already established.

### Key Design Decisions (from brainstorm)
- **Custom TokenBlacklistCacheService** with direct Caffeine + StringRedisTemplate (NOT TwoLevelCacheManager)
- **Key-value Redis** with per-JTI TTL (NOT Redis SET/SISMEMBER — enables auto-eviction)
- **Fail-fast + collect-all** dual-mode ClaimValidatorChain
- **Feature-flagged audience validation** (disabled by default)
- **Selective event recording** (suspicious patterns only, not routine expiration)

---

## 2. Blacklist Cache Layer

### 2.1 TokenBlacklistCacheService (NEW)

**Package**: `auth.application`
**Dependencies**: `Caffeine<String, Boolean>`, `StringRedisTemplate`, `TokenBlacklistRepository`
**Pattern**: Three-tier lookup with circuit breaker

```
isBlacklisted(jti) → L1 Caffeine → L2 Redis EXISTS → DB fallback
```

- L1 Caffeine: TTL 30s, max 10K entries (configurable)
- L2 Redis: key `token:blacklist:{jti}`, TTL = remaining token lifetime
- DB: `tokenBlacklistRepository.existsByTokenJti(jti)` (existing)
- Circuit breaker: 5 consecutive Redis failures → skip Redis for 30s

### 2.2 Write-through (TokenStorePersistenceAdapter modification)

On `blacklistToken()`: DB save (existing) → Redis SET with TTL → Caffeine put
- Redis write retry: 1 retry with 100ms delay
- Redis failure: log warning, continue (DB is source of truth)

### 2.3 JwtAuthFilter Migration

Replace `TokenBlacklistRepository` → `TokenBlacklistCacheService` in constructor injection.

---

## 3. Claim Validation Pipeline

### 3.1 ClaimValidator Interface

```kotlin
interface ClaimValidator {
    val name: String
    fun validate(claims: Claims): ClaimValidationResult
}
```

### 3.2 ClaimValidationResult

```kotlin
data class ClaimValidationResult(
    val validatorName: String,
    val status: ClaimValidationStatus,
    val reason: String? = null
)

enum class ClaimValidationStatus { PASS, FAIL }
```

### 3.3 ClaimValidationException

```kotlin
class ClaimValidationException(
    val validatorName: String,
    override val message: String
) : RuntimeException(message)
```

### 3.4 Validators

| Validator | Checks | Failure Condition |
|-----------|--------|-------------------|
| IssuerClaimValidator | `iss == SecurityProperties.jwt.issuer` | Mismatch |
| AudienceClaimValidator | `aud` contains configured audience (when non-empty) | Missing/mismatch |
| TokenTypeClaimValidator | `type` ∈ {null, "access", "anonymous"} | "mfa", "refresh" rejected |

### 3.5 ClaimValidatorChain

- `validateOrThrow(claims)`: fail-fast for JwtAuthFilter hot path
- `validateAll(claims)`: collect-all for introspection diagnostic path

---

## 4. JWT Configuration Enhancements

### 4.1 Clock Skew

`JwtService.parseToken()` → `clockSkewSeconds(securityProperties.jwt.clockSkewSeconds)` applied to both RS256 and HMAC parsers.

### 4.2 Key Rotation

- New config: `previousPublicKeyPath`, `previousKeyId`
- `parseToken()`: RS256(current) → RS256(previous) → HMAC(legacy)
- `getJwks()`: returns 1 or 2 keys depending on config

### 4.3 Audience

- Config: `app.security.jwt.audience` (empty = disabled)
- `generateAccessToken()`: adds `aud` claim when configured
- `AudienceClaimValidator`: validates when configured

---

## 5. Introspection & JWKS Enhancements

### 5.1 RFC 7662 Introspection

- Use `TokenBlacklistCacheService` instead of `TokenBlacklistRepository`
- Validate claims via `ClaimValidatorChain.validateAll()`
- Add `tokenType`, `scope`, `clientId` to IntrospectionResponse

### 5.2 JWKS ETag

- ETag based on SHA-256 of kid(s)
- Conditional request support (If-None-Match → 304)

---

## 6. Audit & Logging

### 6.1 TokenValidationFailedEvent

```kotlin
data class TokenValidationFailedEvent(
    val reason: ValidationFailureReason,
    val tokenJti: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val validatorName: String?,
    val failedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.validation-failed"
}

enum class ValidationFailureReason {
    BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED
}
```

### 6.2 Structured Logging

- DEBUG: expired, clock_skew, issuer_mismatch
- WARN: blacklisted, signature_invalid, audience_mismatch, type_rejected
- NEVER log raw token string
