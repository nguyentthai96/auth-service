---
type: brainstorm_notes
change: jwt-token-validation
date: 2025-01-20
selected_direction: "Approach B: Test Hardening + Observability + Surgical Bug Fixes"
pre_flow: "Query"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: JWT Token Validation — MAINTENANCE Hardening

## Date
2025-01-20

## Context

The JWT token validation pipeline in auth-service is **fully implemented production-grade** (all 16 FRs verified in code via pre_openspec.md). The previous iteration (archived at `archive/2025-08-26-jwt_token_validation/`) was classified as EXTEND — that work is complete. This iteration is reclassified as **MAINTENANCE**: verify correctness, harden edge cases, improve test coverage, add observability.

**Key implementation files verified**:
- `TokenBlacklistCacheService.kt` (157 LOC) — Caffeine L1 + Redis L2 + DB fallback + custom circuit breaker
- `ClaimValidatorChain.kt` (42 LOC) — Chain of Responsibility: fail-fast + collect-all modes
- `IssuerClaimValidator.kt` (34 LOC), `AudienceClaimValidator.kt` (43 LOC), `TokenTypeClaimValidator.kt` (36 LOC)
- `JwtAuthFilter.kt` (190 LOC) — PEP filter integrating cache service + validator chain + event recording
- `JwtService.kt` (353 LOC) — RS256 current → RS256 previous → HMAC fallback, clock skew 60s
- `TokenController.kt` (114 LOC) — RFC 7662 introspection + JWKS with ETag
- `TokenEventRecorder.kt` (105 LOC) — Validation failure event recording (Kafka outbox)
- `TokenValidationFailedEvent.kt` (24 LOC), `ValidationFailureReason.kt` (18 LOC)

**Source**: pre_openspec.md (quality score 90/100), research artifacts (`openspec/research/jwt-token-validation/`), codebase scan of all relevant source files.

---

## Questions Asked & Answers

### Q1: What are the actual test coverage gaps?

**Analysis** — exhaustive scan of `src/test/`:
- `JwtServiceAnonymousTest.kt` — covers anonymous token generate/parse only (not parseToken fallback, not key rotation)
- `TokenIntrospectionIntegrationTest.kt` — mock-based, simulates controller logic (not real Spring MVC test)
- `TokenEventRecorderTest.kt` — covers issuance + revocation events, but **NOT** `recordValidationFailure()`
- **ZERO** test files for: `TokenBlacklistCacheService`, `ClaimValidatorChain`, `IssuerClaimValidator`, `AudienceClaimValidator`, `TokenTypeClaimValidator`, `JwtAuthFilter`
- No Testcontainers Redis tests for blacklist cache — existing project uses Testcontainers in `CacheEncryptionIntegrationTest.kt`

**A**: Critical gap. The most complex components (`TokenBlacklistCacheService` with circuit breaker, `ClaimValidatorChain`, `JwtAuthFilter` full flow) have zero test coverage. Unit tests are the highest priority MAINTENANCE item.

### Q2: Are there any bugs or correctness issues in the current implementation?

**Analysis** — line-by-line review of key files:

**Bug #1 — `mapValidatorToReason()` in JwtAuthFilter.kt (L183-188)**:
```kotlin
private fun mapValidatorToReason(validatorName: String): ValidationFailureReason {
    return when (validatorName) {
        "AudienceClaimValidator" -> ValidationFailureReason.AUDIENCE_MISMATCH
        "TokenTypeClaimValidator" -> ValidationFailureReason.TYPE_REJECTED
        else -> ValidationFailureReason.SIGNATURE_INVALID  // ← BUG
    }
}
```
The `else` branch maps `IssuerClaimValidator` failures to `SIGNATURE_INVALID`. This is **semantically incorrect** — an issuer mismatch is NOT a signature problem. The `ValidationFailureReason` enum is missing an `ISSUER_MISMATCH` value. This causes **incorrect audit events** for issuer validation failures.

**Severity**: 🟡 MEDIUM — affects audit data correctness, not runtime behavior.
**Fix**: Add `ISSUER_MISMATCH` to `ValidationFailureReason` enum, add case in `mapValidatorToReason()`.

**Bug #2 — `TokenBlacklistCacheService.isBlacklisted()` negative cache gap (L51-75)**:
When a JTI is NOT in any tier (not blacklisted), the method returns `false` but does NOT cache the negative result in L1. This means every request with the same non-blacklisted token will hit Redis (or DB if circuit breaker is open) on every request after Caffeine TTL expiry. For non-blacklisted tokens (99%+ of traffic), this means L1 cache provides NO benefit beyond the first 30s window per token.

**Analysis**: This is actually by-design — caching `false` (not blacklisted) creates a race condition where a token revoked during the Caffeine TTL window would still pass validation. The 30s TTL IS the inconsistency window. However, the current implementation only caches `true` (blacklisted) results in Caffeine, so for the 99% non-blacklisted path, the L1 cache provides no hot-path benefit — every validation STILL goes to Redis.

**Severity**: 🟡 MEDIUM — performance concern, not correctness bug. The L1 cache only helps after a token IS blacklisted (populate-on-hit). For non-blacklisted tokens (dominant case), it's L2 Redis on every call.

**A**: Actually this IS the correct trade-off. Caching negatives would create a 30s window where revoked tokens still pass. The pre_openspec's NFR-006 specifies "<30 seconds max acceptance window" — caching negative results would extend this to 60s (L1 TTL + event propagation). **Keep as-is** but document the design decision.

⚠️ Assumption: The current negative-cache-skip design is intentional — NFR-006 compliance takes priority over L1 hit rate for non-blacklisted tokens.

**Non-bug observation — `Thread.sleep(100)` in addToBlacklist() retry (L96-104)**:
Blocking the request thread with `Thread.sleep(100)` on the revocation path. This is on the write path (token revocation), NOT on the hot read path (token validation). At current revocation frequency (<< 1/s), this is acceptable. The previous brainstorm (archived) already analyzed this — acceptable trade-off.

### Q3: Should the custom circuit breaker be migrated to Resilience4j?

**Analysis**:
- Resilience4j IS a project dependency (`io.github.resilience4j:resilience4j-spring-boot3:2.2.0` in `build.gradle.kts`)
- Resilience4j IS already used in the codebase: `HttpCaptchaGateway.kt` (`@CircuitBreaker(name = "captchaProvider")`), `HttpSsoGateway.kt` (`@CircuitBreaker(name = "ssoProvider")`)
- Custom circuit breaker in `TokenBlacklistCacheService` uses `AtomicInteger` + `AtomicLong` — functional but non-standard
- Resilience4j provides: metrics (via Micrometer integration), health indicators, configurable via YAML, state transitions (CLOSED→OPEN→HALF_OPEN)

**Trade-offs**:

```
┌──────────────────────┬─────────────────────────┬─────────────────────────┐
│ Factor               │ Custom (current)        │ Resilience4j            │
├──────────────────────┼─────────────────────────┼─────────────────────────┤
│ Consistency          │ ❌ Inconsistent with    │ ✅ Matches captcha,     │
│                      │   rest of codebase      │   SSO gateway patterns  │
├──────────────────────┼─────────────────────────┼─────────────────────────┤
│ Metrics integration  │ ❌ No Micrometer        │ ✅ Auto-registers       │
│                      │   metrics exposed       │   circuit_breaker_*     │
├──────────────────────┼─────────────────────────┼─────────────────────                                                      │
│  GAUGES:                                                               │
│  ┌─────────────────────────────────────────────────┐                  │
│  │ auth.token.blacklist.caffeine.size              │                  │
│  │   (current L1 cache entry count)                │                  │
│  └─────────────────────────────────────────────────┘                  │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

**A**: Add Micrometer metrics following existing project patterns. `TokenBlacklistCacheService` and `JwtAuthFilter` should inject `MeterRegistry`. This is the second-highest priority MAINTENANCE item after test coverage.

### Q5: Should introspection tests be upgraded to use ClaimValidatorChain and TokenBlacklistCacheService?

**Analysis**:
- Current `TokenIntrospectionIntegrationTest.kt` mocks `JwtService` and `TokenBlacklistRepository` directly
- But production `TokenController` uses `TokenBlacklistCacheService` and `ClaimValidatorChain`
- The test simulates the old controller logic (pre-EXTEND), not the current flow
- The `introspect()` helper in the test does NOT call `claimValidatorChain.validateAll()` — it skips claim validation entirely

**A**: The test is **stale** — it tests the pre-EXTEND logic. Needs update to test actual `TokenController` behavior with `ClaimValidatorChain.validateAll()` integration. Add new test cases for:
- TC5: Token with wrong issuer → active=false (via ClaimValidatorChain)
- TC6: Token with wrong audience (when configured) → active=false
- TC7: MFA token introspected → active=false (TokenTypeClaimValidator rejects)

### Q6: What is the key rotation overlap period recommendation?

**Analysis**:
- `JwtService` loads `previousKeyPair` from `previousPublicKeyPath` — verification only, no signing
- JWKS endpoint serves both keys during overlap
- Overlap period should be ≥ max token lifetime to avoid rejecting valid tokens signed with the old key
- Access token TTL: 900,000ms (15 minutes, from `SecurityProperties.jwt.accessTokenExpirationMs`)
- Refresh token TTL: 604,800,000ms (7 days, from `SecurityProperties.jwt.refreshTokenExpirationMs`)
- Therefore: overlap period ≥ 7 days (max refresh token lifetime)
- Pre_openspec OQ-003 asks about this — not yet documented

**A**: Key rotation overlap period should be **≥ 7 days** (max refresh token lifetime). Procedure:
1. Generate new RSA key pair
2. Set `previous-public-key-path` = current public key, `previous-key-id` = current kid
3. Set new key as primary (private + public key paths, new kid)
4. Deploy — JWKS serves both keys, parseToken tries both
5. Wait ≥ 7 days (all old refresh tokens expire naturally)
6. Remove previous key config, deploy
Document this in a key rotation runbook section.

### Q7: Is there a missing `ISSUER_MISMATCH` case in the filter's event recording?

**Analysis** — tracing the exact call flow:

```
JwtAuthFilter.doFilterInternal()
  └── claimValidatorChain.validateOrThrow(claims)
       ├── IssuerClaimValidator.validate()  → could FAIL
       ├── AudienceClaimValidator.validate() → could FAIL
       └── TokenTypeClaimValidator.validate() → could FAIL
  
  If FAIL → catch ClaimValidationException(validatorName, message)
       └── mapValidatorToReason(validatorName)
            ├── "AudienceClaimValidator" → AUDIENCE_MISMATCH ✅
            ├── "TokenTypeClaimValidator" → TYPE_REJECTED ✅
            └── else → SIGNATURE_INVALID ❌ (should be ISSUER_MISMATCH)
```

The `else` branch catches `IssuerClaimValidator` (and any future validators) and maps them to `SIGNATURE_INVALID` — a semantically wrong reason. This pollutes audit data.

**A**: **Must fix.**
1. Add `ISSUER_MISMATCH` to `ValidationFailureReason` enum
2. Add `"IssuerClaimValidator" -> ValidationFailureReason.ISSUER_MISMATCH` case in `mapValidatorToReason()`
3. Change `else` to `CLAIM_VALIDATION_FAILED` (generic fallback for future validators)
4. This also requires adding `CLAIM_VALIDATION_FAILED` to the enum

### Q8: Should `TokenEventRecorderTest` be updated to cover `recordValidationFailure()`?

**Analysis**:
- `TokenEventRecorderTest.kt` has 11 test cases but ALL test `recordIssuance()` and `recordRevocation()`
- `recordValidationFailure()` (added in the EXTEND iteration) has **zero test coverage**
- The method is structurally identical to the other two (try/catch delegation to EventService.record())
- It uses `aggregateType = "Token"`, `aggregateId = 0L`, topic = `"iam.token.validation-failed"` — needs verification

**A**: Add test cases TC10-TC12 for `recordValidationFailure()`:
- TC10: recordValidationFailure() delegates to EventService.record() with correct topic and aggregateType
- TC11: recordValidationFailure() with null tokenJti → partitionKey = "unknown"
- TC12: recordValidationFailure() swallows exceptions (fail-safe)

---

## Approaches Considered

### Approach A: Test Hardening Only (Minimal Scope)

**Description**: Add unit tests for all untested components. Fix the `mapValidatorToReason` bug. No metrics, no refactoring.

**Scope**:
- Unit tests: `TokenBlacklistCacheServiceTest`, `ClaimValidatorChainTest`, `IssuerClaimValidatorTest`, `AudienceClaimValidatorTest`, `TokenTypeClaimValidatorTest`, `JwtAuthFilterTest`
- Update `TokenEventRecorderTest` with `recordValidationFailure()` tests
- Update `TokenIntrospectionIntegrationTest` to test actual controller logic
- Fix `mapValidatorToReason()` bug + add `ISSUER_MISMATCH` enum value
- Fix `else` branch → add `CLAIM_VALIDATION_FAILED` generic reason

**Pros**:
- Smallest code change surface
- Addresses the most critical quality gap (testability score was 21/25)
- Low risk — tests don't change production behavior

**Cons**:
- Misses observability (no metrics for production monitoring)
- Leaves NFR-001/NFR-004/NFR-007 unverifiable without metrics
- Doesn't address documentation gaps (key rotation procedure)

**Score**: 6/10

### Approach B: Test Hardening + Observability + Surgical Bug Fixes ✅ SELECTED

**Description**: Comprehensive MAINTENANCE combining test coverage, Micrometer metrics, bug fixes, and documentation. No architectural changes (no Resilience4j migration, no cache strategy changes).

**Scope**:
1. **Bug Fixes** (2 items):
   - Add `ISSUER_MISMATCH` and `CLAIM_VALIDATION_FAILED` to `ValidationFailureReason` enum
   - Fix `mapValidatorToReason()` in `JwtAuthFilter` to correctly map all validators

2. **Unit Tests** (6 new test files):
   - `TokenBlacklistCacheServiceTest.kt` — L1/L2/L3 lookup, circuit breaker open/close/reset, write-through, retry
   - `ClaimValidatorChainTest.kt` — fail-fast mode, collect-all mode, empty chain, mixed results
   - `IssuerClaimValidatorTest.kt` — match, mismatch, null issuer
   - `AudienceClaimValidatorTest.kt` — match, mismatch, disabled (empty), null audience claim
   - `TokenTypeClaimValidatorTest.kt` — allowed types (null, "access", "anonymous"), rejected ("mfa", "refresh")
   - `JwtAuthFilterTest.kt` — full flow with valid/invalid/blacklisted/claim-failed tokens

3. **Test Updates** (2 existing files):
   - `TokenEventRecorderTest.kt` — add `recordValidationFailure()` coverage (TC10-TC12)
   - `TokenIntrospectionIntegrationTest.kt` — update to test ClaimValidatorChain integration

4. **Observability** (Micrometer metrics):
   - `TokenBlacklistCacheService` — inject `MeterRts                         │
│  │ NEW: IssuerClaimValidatorTest   │ Unit tests                         │
│  │ NEW: AudienceClaimValidatorTest │ Unit tests                         │
│  │ NEW: TokenTypeClaimValidatorTest│ Unit tests                         │
│  │ NEW: JwtAuthFilterTest          │ Unit tests (MockMvc or Mockito)    │
│  │ UPD: TokenEventRecorderTest    │ + recordValidationFailure TC10-12   │
│  │ UPD: TokenIntrospectionIntTest  │ + ClaimValidatorChain integration  │
│  └──────────────────────────────────┘                                   │
│                                                                         │
│  TOTAL: 3 files modified (production), 6 new + 2 updated (test)         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Pros**:
- Addresses all 3 quality score deductions: testability (-4), completeness (-3), consistency (-2)
- Bug fix corrects audit data integrity (ISSUER_MISMATCH vs SIGNATURE_INVALID)
- Metrics enable NFR verification (P95 validation time, cache hit ratio)
- No architectural risk — production code changes are additive (enum values, metric counters)
- Follows established patterns (MeterRegistry injection same as AnonymousSessionHandler etc.)

**Cons**:
- Larger scope than Approach A (but all changes are low-risk)
- Metrics add minor runtime overhead (Micrometer counters are ~nanosecond cost)
- Does not migrate to Resilience4j (deferred)

**Score**: 9/10

### Approach C: Full Hardening (Everything Including Resilience4j)

**Description**: Approach B + migrate custom circuit breaker to Resilience4j + replace `Thread.sleep(100)` with async retry + add Testcontainers Redis integration tests.

**Additional scope vs Approach B**:
- Resilience4j `CircuitBreaker` replacing custom `AtomicInteger`/`AtomicLong` logic
- `@CircuitBreaker(name = "tokenBlacklistRedis")` annotation
- Resilience4j YAML configuration for thresholds
- Replace `Thread.sleep(100)` with Kotlin coroutine delay or Resilience4j retry
- Testcontainers Redis for integration tests

**Pros**:
- Most comprehensive — addresses every identified issue
- Resilience4j consistency with captcha/SSO gateways
- Real Redis integration tests

**Cons**:
- Resilience4j migration changes hot-path invocation model (Spring AOP proxy)
- Need to verify `OncePerRequestFilter` → `@Service` with AOP proxy works correctly
- `Thread.sleep` replacement requires either coroutines (new pattern) or Resilience4j retry (additional config)
- Testcontainers adds test infrastructure dependency + CI time
- Scope creep risk — MAINTENANCE should be conservative

**Score**: 6/10 — too much scope for MAINTENANCE. Better suited for a future EXTEND iteration.

### Approach D: Surgical Bug Fix Only (Ultra-Minimal)

**Description**: Fix `mapValidatorToReason()` bug + add `ISSUER_MISMATCH` enum. Nothing else.

**Pros**: Absolute minimum change.
**Cons**: Wastes the MAINTENANCE opportunity. Test coverage stays at 0. No metrics. Quality score stays at 90.

**Score**: 3/10

---

## Selected Direction

**Approach B: Test Hardening + Observability + Surgical Bug Fixes**

Selected because:
1. **Highest value-to-risk ratio** (9/10) — all changes are additive or corrective, no architectural modifications
2. **Addresses all 3 quality score deductions** from pre_openspec: testability (-4 → fixed with 6 new test files + 2 updates), completeness (-3 → fixed with Micrometer metrics), consistency (-2 → fixed with correct `mapValidatorToReason()`)
3. **Follows MAINTENANCE classification** — verify correctness (bug fix), harden edge cases (tests), ensure observability (metrics)
4. **Conservative scope** — no circuit breaker migration, no cache strategy changes, no async retry refactoring
5. **Established patterns** — `MeterRegistry` injection, `SimpleMeterRegistry` in tests, Mockito unit tests all have precedent in codebase

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bug fix: mapValidatorToReason | Add ISSUER_MISMATCH + CLAIM_VALIDATION_FAILED enum values | Correct audit data; future-proof for new validators |
| Test approach | Mockito unit tests (not Testcontainers integration) | Consistent with existing test patterns (JwtServiceAnonymousTest, TokenEventRecorderTest); integration tests deferred |
| Metrics framework | Micrometer MeterRegistry (direct injection) | Same pattern as AnonymousSessionHandler, RenewAnonymousTokenHandler etc. |
| Metric naming | `auth.token.validation.*`, `auth.token.blacklist.*` | Follows hierarchical naming; `auth` prefix for service, `token` for domain |
| Circuit breaker migration | Deferred to future iteration | MAINTENANCE scope; custom CB works correctly; migration has hot-path risk |
| Negative cache (L1 false results) | Keep current behavior (don't cache) | NFR-006 compliance: <30s acceptance window for revoked tokens |
| Key rotation docs | In-code documentation + design.md section | Not a code change; operational procedure recommendation |
| Thread.sleep(100) retry | Keep as-is | Revocation path only; current frequency << 1/s; async requires new patterns |

---

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE (all FRs implemented; scope = verify + harden + observe)
- Flow type: Query (token validation is read-only per-request check; introspection is query endpoint)
- Affected modules:
  - `auth.domain.event` — ValidationFailureReason enum (MODIFY: +2 enum values)
  - `shared.security` — JwtAuthFilter (MODIFY: fix mapValidatorToReason + add MeterRegistry)
  - `auth.application` — TokenBlacklistCacheService (MODIFY: add MeterRegistry)
  - `test/**` — 6 new test files + 2 updated test files

## GitNexus Findings (if explored)

GitNexus was not available for this session. Codebase exploration was done via `grep_search`, `view_file`, and `glob_search`.

### Key Symbols Analyzed
- `TokenBlacklistCacheService` — 157 LOC, `auth.application`. Caffeine L1 + Redis L2 + DB fallback. Custom circuit breaker (AtomicInteger/AtomicLong). **Zero test coverage.**
- `ClaimValidatorChain` — 42 LOC, `auth.application`. Two modes: validateOrThrow (fail-fast), validateAll (collect-all). **Zero test coverage.**
- `IssuerClaimValidator` — 34 LOC. Compares `claims.issuer` vs `securityProperties.jwt.issuer`. **Zero test coverage.**
- `AudienceClaimValidator` — 43 LOC. Feature-flagged (empty audience = skip). **Zero test coverage.**
- `TokenTypeClaimValidator` — 36 LOC. ALLOWED_TYPES = {null, "access", "anonymous"}. **Zero test coverage.**
- `JwtAuthFilter` — 190 LOC, `shared.security`. Full validation pipeline. **Zero test coverage.** Bug in `mapValidatorToReason()`.
- `TokenEventRecorder.recordValidationFailure()` — L84-105. Delegates to EventService.record(). **Zero test coverage** (issuance/revocation covered, validation-failure NOT covered).
- `ValidationFailureReason` — 18 LOC. 4 enum values: BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED. Missing: ISSUER_MISMATCH, CLAIM_VALIDATION_FAILED.
- `TokenController.introspect()` — L39-71. Uses `claimValidatorChain.validateAll()` + `tokenBlacklistCacheService.isBlacklisted()`. **Test is stale** (tests pre-EXTEND logic).

### Architecture Insights
- Micrometer `MeterRegistry` is the standard observability pattern — used in 5+ services
- `SimpleMeterRegistry` is used in all test classes that need MeterRegistry mocking
- Resilience4j `@CircuitBreaker` annotation is used in HTTP gateways (CaptchaGateway, SsoGateway) but NOT in application services
- `TokenBlacklistCacheService` custom circuit breaker is the only non-Resilience4j circuit breaker in the codebase

---

## Codebase Bug Report

### BUG-001: Incorrect ValidationFailureReason for IssuerClaimValidator

**File**: `src/main/kotlin/com/ntt/authservice/shared/securit                                                           │
│  TokenBlacklistCacheService (inject MeterRegistry):                     │
│                                                                         │
│  isBlacklisted():                                                       │
│    L1 hit  → counter("auth.token.blacklist.lookup",                     │
│                       "tier", "l1_caffeine", "result", "hit")           │
│    L1 miss → counter("auth.token.blacklist.lookup",                     │
│                       "tier", "l1_caffeine", "result", "miss")          │
│    L2 hit  → counter("auth.token.blacklist.lookup",                     │
│                       "tier", "l2_redis", "result", "hit")              │
│    L2 miss → counter(..., "tier", "l2_redis", "result", "miss")        │
│    L3 hit  → counter(..., "tier", "l3_db", "result", "hit")            │
│    L3 miss → counter(..., "tier", "l3_db", "result", "miss")           │
│                                                                         │
│  isCircuitBreakerOpen():                                                │
│    opened → counter("auth.token.blacklist.circuit_breaker",             │
│                      "transition", "opened")                            │
│    reset  → counter("auth.token.blacklist.circuit_breaker",             │
│                      "transition", "reset")                             │
│                                                                         │
│  init block:                                                            │
│    gauge("auth.token.blacklist.caffeine.size",                          │
│          caffeineCache, { it.estimatedSize().toDouble() })              │
│                                                                         │
│  ─────────────────────────────────────────────────────────────────      │
│                                                                         │
│  JwtAuthFilter (inject MeterRegistry):                                  │
│                                                                         │
│  doFilterInternal():                                                    │
│    success → counter("auth.token.validation",                           │
│                       "result", "success")                              │
│    failure → counter("auth.token.validation",                           │
│                       "result", "failure",                              │
│                       "reason", "<failure_reason>")                     │
│    timer  → timer("auth.token.validation.duration",                     │
│                    "result", "<result>")                                 │
│              .record(duration)                                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Open Questions for Design Phase

- [OPEN] OQ-001: Should Micrometer metrics include percentile histograms for `auth.token.validation.duration`? Histogram buckets add memory overhead. Existing project uses timers in `AnonymousSessionHandler` — need to check if percentile config is standardized.
- [OPEN] OQ-002: Should `TokenBlacklistCacheService` expose an `estimatedSize()` method for the Caffeine gauge, or register the gauge internally?
- [OPEN] OQ-003: Key rotation overlap documentation — should this be in `SecurityProperties` KDoc, a separate operations  │ Create  │ ~5 tests         │
  │ IssuerClaimValidatorTest.kt (NEW)        │ Create  │ ~3 tests         │
  │ AudienceClaimValidatorTest.kt (NEW)      │ Create  │ ~5 tests         │
  │ TokenTypeClaimValidatorTest.kt (NEW)     │ Create  │ ~6 tests         │
  │ JwtAuthFilterTest.kt (NEW)              │ Create  │ ~11 tests        │
  │ TokenEventRecorderTest.kt (UPDATE)       │ +3 TCs  │ +3 tests         │
  │ TokenIntrospectionIntegrationTest (UPD)  │ +3 TCs  │ +3 tests         │
  └──────────────────────────────────────────┴─────────┴──────────────────┘
  Total: ~46 new test cases
```

---

## Risk Analysis

```
┌─────────────────────────────────────────────────────────────────┐
│                    RISK HEAT MAP                                │
│                                                                 │
│  Impact ▲                                                       │
│    HIGH │                                                       │
│         │                                                       │
│  MEDIUM │                        ○MeterRegistry                │
│         │                         injection breaks              │
│         │                         filter chain                  │
│    LOW  │  ○Metric naming         ○Enum addition               │
│         │   inconsistency          backward compat              │
│         └──────────────────────────────────────────────────▶    │
│           LOW          MEDIUM         HIGH      Probability     │
│                                                                 │
│  All risks are LOW probability, LOW-MEDIUM impact               │
│  ● = Mitigated by design   ○ = Residual (acceptable)          │
└─────────────────────────────────────────────────────────────────┘
```

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| MeterRegistry injection changes JwtAuthFilter constructor | LOW | MEDIUM | Constructor injection is standard; Spring autowires MeterRegistry automatically. Existing services use same pattern. Test with SimpleMeterRegistry. |
| New enum values break deserialization | LOW | LOW | Additive enum change. Kafka consumers should handle unknown enum values (standard practice). |
| Metrics overhead on hot path | LOW | LOW | Micrometer counters are ~nanosecond cost. Timer.record() adds <1μs. Negligible vs validation pipeline ~1-5ms. |
| Stale test update breaks existing TCs | LOW | LOW | Updates are additive (new TC5-TC7). Existing TC1-TC4 structure unchanged. |
