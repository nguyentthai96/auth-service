# Design: jwt-token-validation

_Generated: 2025-01-20_
_Previous Iteration: archive/2025-08-26-jwt_token_validation/ (EXTEND)_

---

## Locked Profile

| Field | Value |
|-------|-------|
| flow | Query |
| factory | N/A |
| feature_type | MAINTENANCE |
| transaction_flow | Query — token validation is read-only per-request check |

---

## 1. Architecture Overview

MAINTENANCE iteration — no architectural changes. All production code modifications are **additive only**:
- +2 enum values in `ValidationFailureReason`
- Fix 1 method body (`mapValidatorToReason`)
- +1 constructor parameter (`MeterRegistry`) in 2 existing classes
- +metric instrumentation lines in 2 existing classes

### Key Design Decisions (from brainstorm — Approach B)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bug fix: mapValidatorToReason | Add `ISSUER_MISMATCH` + `CLAIM_VALIDATION_FAILED` enum values | Correct audit data; future-proof for new validators |
| Test approach | Mockito unit tests (not Testcontainers integration) | Consistent with existing test patterns; integration tests deferred |
| Metrics framework | Micrometer `MeterRegistry` (constructor injection) | Same pattern as `AnonymousSessionHandler`, `RenewAnonymousTokenHandler` etc. |
| Metric naming | `auth.token.validation.*`, `auth.token.blacklist.*` | Hierarchical naming; `auth` prefix for service, `token` for domain |
| Circuit breaker migration | Deferred to future iteration | MAINTENANCE scope; custom CB works correctly |
| Negative cache | Keep current behavior (don't cache `false`) | NFR-006: <30s acceptance window for revoked tokens |

---

## 2. Bug Fix Design

### 2.1 ValidationFailureReason Enum Extension

**File**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`

**Current** (4 values):
```kotlin
enum class ValidationFailureReason {
    BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED
}
```

**After** (6 values):
```kotlin
enum class ValidationFailureReason {
    BLACKLISTED,
    SIGNATURE_INVALID,
    ISSUER_MISMATCH,        // NEW — issuer claim does not match configured value
    AUDIENCE_MISMATCH,
    TYPE_REJECTED,
    CLAIM_VALIDATION_FAILED  // NEW — generic fallback for future validators
}
```

**Backward Compatibility**: Additive enum. Existing Kafka consumers receiving `BLACKLISTED`, `SIGNATURE_INVALID`, `AUDIENCE_MISMATCH`, `TYPE_REJECTED` unaffected. New values only appear for previously-misclassified events.

### 2.2 mapValidatorToReason Fix

**File**: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` L183-188

**Current** (BUG):
```kotlin
private fun mapValidatorToReason(validatorName: String): ValidationFailureReason {
    return when (validatorName) {
        "AudienceClaimValidator" -> ValidationFailureReason.AUDIENCE_MISMATCH
        "TokenTypeClaimValidator" -> ValidationFailureReason.TYPE_REJECTED
        else -> ValidationFailureReason.SIGNATURE_INVALID  // ← BUG: IssuerClaimValidator → SIGNATURE_INVALID
    }
}
```

**After** (FIXED):
```kotlin
private fun mapValidatorToReason(validatorName: String): ValidationFailureReason {
    return when (validatorName) {
        "IssuerClaimValidator" -> ValidationFailureReason.ISSUER_MISMATCH           // NEW
        "AudienceClaimValidator" -> ValidationFailureReason.AUDIENCE_MISMATCH
        "TokenTypeClaimValidator" -> ValidationFailureReason.TYPE_REJECTED
        else -> ValidationFailureReason.CLAIM_VALIDATION_FAILED                     // NEW (generic)
    }
}
```

---

## 3. Test Design

### 3.1 TokenBlacklistCacheServiceTest

**Target**: `auth/application/TokenBlacklistCacheService.kt` (157 LOC)
**Pattern**: Mockito unit test with `SimpleMeterRegistry`
**Dependencies to mock**: `Caffeine Cache<String, Boolean>`, `StringRedisTemplate`, `TokenBlacklistRepository`, `SecurityProperties`

**Key scenarios**:
- Three-tier lookup (L1 → L2 → L3) with tier promotion
- Circuit breaker state machine: CLOSED → OPEN (after threshold) → CLOSED (after cooldown)
- Write-through: `addToBlacklist()` writes to L1 + L2 with correct key format and TTL
- Retry logic: 1 retry with 100ms delay on Redis write failure
- Fail-safe: Redis exceptions don't propagate, fall through to DB
- Metrics verification: counter increments for each tier and circuit breaker transitions

### 3.2 ClaimValidatorChainTest

**Target**: `auth/application/ClaimValidatorChain.kt` (42 LOC)
**Pattern**: Mockito unit test with mock ClaimValidator instances
**Dependencies to mock**: `List<ClaimValidator>`

**Key scenarios**:
- `validateOrThrow()`: all pass → no exception
- `validateOrThrow()`: fail-fast on first FAIL validator
- `validateAll()`: returns all results including mixed PASS/FAIL
- Empty validator list → no exception, empty/no-op

### 3.3 IssuerClaimValidatorTest

**Target**: `auth/application/IssuerClaimValidator.kt` (34 LOC)
**Dependencies to mock**: `SecurityProperties`, JJWT `Claims`

**Key scenarios**: Match → PASS, mismatch → FAIL with descriptive reason, null issuer → FAIL

### 3.4 AudienceClaimValidatorTest

**Target**: `auth/application/AudienceClaimValidator.kt` (43 LOC)
**Dependencies to mock**: `SecurityProperties`, JJWT `Claims`

**Key scenarios**: Disabled (empty config) → PASS, enabled + match → PASS, enabled + mismatch → FAIL, null audience → FAIL, list contains expected → PASS

### 3.5 TokenTypeClaimValidatorTest

**Target**: `auth/application/TokenTypeClaimValidator.kt` (36 LOC)
**Dependencies to mock**: JJWT `Claims`

**Key scenarios**: null → PASS, "access" → PASS, "anonymous" → PASS, "mfa" → FAIL, "refresh" → FAIL, unknown → FAIL

### 3.6 JwtAuthFilterTest

**Target**: `shared/security/JwtAuthFilter.kt` (190 LOC)
**Pattern**: Mockito unit test with `MockHttpServletRequest`, `MockHttpServletResponse`, `MockFilterChain`
**Dependencies to mock**: `JwtService`, `TokenBlacklistCacheService`, `ClaimValidatorChain`, `TokenEventRecorder`, `MeterRegistry` (use `SimpleMeterRegistry`)

**Key scenarios**:
- No auth header → filter chain continues, no SecurityContext
- Valid token → SecurityContext set, metrics timer records, success counter
- Blacklisted → 401, event recorded (BLACKLISTED), failure counter
- Signature invalid → event recorded (SIGNATURE_INVALID), filter chain continues
- Issuer mismatch → event recorded (ISSUER_MISMATCH) — verifies BUG-001 fix
- Audience mismatch → event recorded (AUDIENCE_MISMATCH)
- Type rejected → event recorded (TYPE_REJECTED)
- Anonymous token → ROLE_ANONYMOUS
- Expired → no event (unless configured), filter chain continues
- Event recording exception → swallowed, filter chain continues
- Metrics incremented correctly

### 3.7 TokenEventRecorderTest Updates

**Target**: `auth/application/event/TokenEventRecorder.kt` (105 LOC)
**Pattern**: Extend existing test class with 3 new TCs

**New TCs**:
- TC10: `recordValidationFailure()` calls `eventService.record()` with aggregateType="Token", aggregateId=0L, topic="iam.token.validation-failed"
- TC11: null tokenJti → partitionKey = "unknown"
- TC12: Exception thrown by eventService → caught and logged (fail-safe)

### 3.8 TokenIntrospectionIntegrationTest Updates

**Target**: `auth/adapter/in/web/TokenController.kt` (114 LOC)
**Pattern**: Extend existing test class with 3 new TCs

**New TCs**:
- TC5: Token with wrong issuer → `active=false` via `ClaimValidatorChain.validateAll()`
- TC6: Token with wrong audience (when configured) → `active=false`
- TC7: MFA token → `active=false` via `TokenTypeClaimValidator`

---

## 4. Observability Design

### 4.1 TokenBlacklistCacheService Metrics

**File**: `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`

**Constructor change**: Add `private val meterRegistry: MeterRegistry` parameter.

**Metric registration** (in init block or constructor body):
```kotlin
// Gauge for Caffeine cache size
Gauge.builder("auth.token.blacklist.caffeine.size") { caffeineCache.estimatedSize().toDouble() }
    .description("Current number of entries in L1 Caffeine blacklist cache")
    .register(meterRegistry)
```

**Instrumentation points**:

| Location | Metric | Tags |
|----------|--------|------|
| `isBlacklisted()` L1 hit | `auth.token.blacklist.lookup` counter | tier=l1_caffeine, result=hit |
| `isBlacklisted()` L1 miss | `auth.token.blacklist.lookup` counter | tier=l1_caffeine, result=miss |
| `isBlacklisted()` L2 hit | `auth.token.blacklist.lookup` counter | tier=l2_redis, result=hit |
| `isBlacklisted()` L2 miss | `auth.token.blacklist.lookup` counter | tier=l2_redis, result=miss |
| `isBlacklisted()` L3 hit | `auth.token.blacklist.lookup` counter | tier=l3_db, result=hit |
| `isBlacklisted()` L3 miss | `auth.token.blacklist.lookup` counter | tier=l3_db, result=miss |
| Circuit breaker opens | `auth.token.blacklist.circuit_breaker` counter | transition=opened |
| Circuit breaker resets | `auth.token.blacklist.circuit_breaker` counter | transition=reset |

### 4.2 JwtAuthFilter Metrics

**File**: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`

**Constructor change**: Add `private val meterRegistry: MeterRegistry` parameter.

**Instrumentation points**:

| Location | Metric | Tags |
|----------|--------|------|
| Successful validation | `auth.token.validation` counter | result=success |
| Blacklisted token | `auth.token.validation` counter | result=failure, reason=blacklisted |
| Signature invalid | `auth.token.validation` counter | result=failure, reason=signature_invalid |
| Issuer mismatch | `auth.token.validation` counter | result=failure, reason=issuer_mismatch |
| Audience mismatch | `auth.token.validation` counter | result=failure, reason=audience_mismatch |
| Type rejected | `auth.token.validation` counter | result=failure, reason=type_rejected |
| Expired | `auth.token.validation` counter | result=failure, reason=expired |
| Claim validation failed | `auth.token.validation` counter | result=failure, reason=claim_validation_failed |
| Full validation pipeline | `auth.token.validation.duration` timer | result=success/failure |

**Timer wrapping**: Wrap from token extraction through SecurityContext setting (or failure) in `Timer.builder("auth.token.validation.duration").tag("result", result).register(meterRegistry).record { ... }`.

---

## 5. Documentation

### 5.1 Key Rotation Procedure (OQ-003 Resolution)

Key rotation overlap period should be **≥ 7 days** (max refresh token lifetime = `604,800,000ms`).

**Procedure**:
1. Generate new RSA key pair
2. Set `app.security.jwt.previous-public-key-path` = current public key path
3. Set `app.security.jwt.previous-key-id` = current kid
4. Set new key pair as primary (private + public key paths, new kid)
5. Deploy — JWKS serves both keys, `parseToken()` tries current → previous → HMAC
6. Wait ≥ 7 days (all refresh tokens signed with old key expire naturally)
7. Remove `previous-*` config entries, deploy

### 5.2 Negative Cache Design Decision

`TokenBlacklistCacheService.isBlacklisted()` does NOT cache `false` (not-blacklisted) results in L1 Caffeine. This is intentional:
- Caching `false` would create a 30-second window where a newly revoked token could still pass validation (Caffeine TTL)
- NFR-006 specifies "<30 seconds max acceptance window" — this refers to the existing 30s TTL on `true` (blacklisted) entries
- Trade-off: every request for non-blacklisted tokens hits Redis (or DB if circuit breaker open). Acceptable because Redis lookup is <1ms (NFR-003) and Redis can handle >100K ops/s

---

## 6. Implementation Ordering

```
Phase 1: Bug Fix (prerequisite — corrects audit data)
  └── T1: ValidationFailureReason.kt (+ISSUER_MISMATCH, +CLAIM_VALIDATION_FAILED)
  └── T2: JwtAuthFilter.kt (fix mapValidatorToReason)

Phase 2: Unit Tests — Validators (no production changes needed)
  └── T7: IssuerClaimValidatorTest.kt
  └── T8: AudienceClaimValidatorTest.kt
  └── T9: TokenTypeClaimValidatorTest.kt
  └── T6: ClaimValidatorChainTest.kt

Phase 3: Observability (production changes — additive)
  └── T3: TokenBlacklistCacheService.kt (+MeterRegistry + counters + gauge)
  └── T4: JwtAuthFilter.kt (+MeterRegistry + counters + timer)

Phase 4: Unit Tests — Cache + Filter + Events (depends on Phase 1+3)
  └── T5: TokenBlacklistCacheServiceTest.kt
  └── T10: JwtAuthFilterTest.kt
  └── T11: TokenEventRecorderTest.kt (+recordValidationFailure TCs)

Phase 5: Integration Test Update (depends on Phase 1)
  └── T12: TokenIntrospectionIntegrationTest.kt (+ClaimValidatorChain TCs)
```

---

## 7. Concurrency Considerations

All changes are thread-safe:
1. **Micrometer counters/timers**: Thread-safe by design (atomic operations internally)
2. **Enum additions**: Immutable, no concurrency concern
3. **mapValidatorToReason fix**: Pure function, no shared state
4. **Caffeine gauge registration**: One-time in constructor; `Caffeine.estimatedSize()` is thread-safe
