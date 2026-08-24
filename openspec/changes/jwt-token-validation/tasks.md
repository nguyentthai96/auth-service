<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Query", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "Query" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: jwt-token-validation

_Generated: 2025-01-20_
_Profile: Query | N/A | MAINTENANCE_
_Previous Iteration: archive/2025-08-26-jwt_token_validation/ (EXTEND — all 16 FRs implemented)_
_Scope: BUG-001 fix + 49 test cases + Micrometer observability_

---

## Task Summary

| # | Task | Action | File | Scope |
|---|------|--------|------|-------|
| 1 | ValidationFailureReason — add ISSUER_MISMATCH + CLAIM_VALIDATION_FAILED | MODIFY | `ValidationFailureReason.kt` | BUG-001 |
| 2 | JwtAuthFilter — fix mapValidatorToReason | MODIFY | `JwtAuthFilter.kt` | BUG-001 |
| 3 | TokenBlacklistCacheService — add MeterRegistry + metrics | MODIFY | `TokenBlacklistCacheService.kt` | OBS-001 |
| 4 | JwtAuthFilter — add MeterRegistry + metrics | MODIFY | `JwtAuthFilter.kt` | OBS-002 |
| 5 | TokenBlacklistCacheServiceTest — new unit tests | NEW | `TokenBlacklistCacheServiceTest.kt` | TEST-001 |
| 6 | ClaimValidatorChainTest — new unit tests | NEW | `ClaimValidatorChainTest.kt` | TEST-002 |
| 7 | IssuerClaimValidatorTest — new unit tests | NEW | `IssuerClaimValidatorTest.kt` | TEST-003 |
| 8 | AudienceClaimValidatorTest — new unit tests | NEW | `AudienceClaimValidatorTest.kt` | TEST-004 |
| 9 | TokenTypeClaimValidatorTest — new unit tests | NEW | `TokenTypeClaimValidatorTest.kt` | TEST-005 |
| 10 | JwtAuthFilterTest — new unit tests | NEW | `JwtAuthFilterTest.kt` | TEST-006 |
| 11 | TokenEventRecorderTest — add recordValidationFailure tests | MODIFY | `TokenEventRecorderTest.kt` | TEST-007 |
| 12 | TokenIntrospectionIntegrationTest — add ClaimValidatorChain tests | MODIFY | `TokenIntrospectionIntegrationTest.kt` | TEST-008 |

---

## Phase 1: Bug Fix (prerequisite — corrects audit data)

- [ ] **Task 1: ValidationFailureReason — add ISSUER_MISMATCH + CLAIM_VALIDATION_FAILED**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt` | Action: [MODIFY]
  - FR: FR-012 (bug fix — incorrect audit event mapping)
  - Pattern: Kotlin enum class, one value per line with KDoc comment
  - Dependencies: None (leaf enum)
  - Detail: Add two new enum values after existing ones:
    - `ISSUER_MISMATCH` — issuer claim does not match configured value. WARN level.
    - `CLAIM_VALIDATION_FAILED` — generic fallback for future validators. WARN level.
  - Final enum order: `BLACKLISTED`, `SIGNATURE_INVALID`, `ISSUER_MISMATCH`, `AUDIENCE_MISMATCH`, `TYPE_REJECTED`, `CLAIM_VALIDATION_FAILED`
  - Backward compat: Additive enum change. Existing Kafka consumers unaffected.

- [ ] **Task 2: JwtAuthFilter — fix mapValidatorToReason**
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [MODIFY]
  - FR: FR-012 (bug fix)
  - Pattern: Kotlin `when` expression
  - Dependencies: Task 1 (new enum values must exist)
  - Detail: Replace `mapValidatorToReason()` method body (L183-188):
    - Add case: `"IssuerClaimValidator" -> ValidationFailureReason.ISSUER_MISMATCH`
    - Change else: `else -> ValidationFailureReason.CLAIM_VALIDATION_FAILED`
  - Existing cases unchanged: `"AudienceClaimValidator"` → `AUDIENCE_MISMATCH`, `"TokenTypeClaimValidator"` → `TYPE_REJECTED`

---

## Phase 2: Unit Tests — Validators (no production changes needed)

- [ ] **Task 7: IssuerClaimValidatorTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/IssuerClaimValidatorTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK unit test, `@ExtendWith(MockitoExtension::class)` or MockK equivalent
  - Dependencies: `IssuerClaimValidator`, mock `SecurityProperties`, mock JJWT `Claims`
  - Test Cases (3):
    - TC01: Matching issuer `claims.issuer == securityProperties.jwt.issuer` → returns `ClaimValidationResult(status=PASS)`
    - TC02: Mismatched issuer → returns `ClaimValidationResult(status=FAIL, reason="Issuer mismatch: expected=auth-service, actual=other-service")`
    - TC03: Null issuer in claims → returns `ClaimValidationResult(status=FAIL)`

- [ ] **Task 8: AudienceClaimValidatorTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/AudienceClaimValidatorTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK unit test
  - Dependencies: `AudienceClaimValidator`, mock `SecurityProperties`, mock JJWT `Claims`
  - Test Cases (5):
    - TC01: Disabled (audience config empty `""`) → returns PASS (skip validation)
    - TC02: Enabled + matching audience → returns PASS
    - TC03: Enabled + mismatched audience → returns FAIL
    - TC04: Enabled + null audience claim in token → returns FAIL
    - TC05: Enabled + audience list in token contains expected value → returns PASS

- [ ] **Task 9: TokenTypeClaimValidatorTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/TokenTypeClaimValidatorTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK unit test
  - Dependencies: `TokenTypeClaimValidator`, mock JJWT `Claims`
  - Test Cases (6):
    - TC01: null type claim → PASS (backward compatible — no type = access token)
    - TC02: `"access"` → PASS
    - TC03: `"anonymous"` → PASS
    - TC04: `"mfa"` → FAIL `"Token type 'mfa' not allowed as access token"`
    - TC05: `"refresh"` → FAIL `"Token type 'refresh' not allowed as access token"`
    - TC06: `"service"` (unknown) → FAIL

- [ ] **Task 6: ClaimValidatorChainTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChainTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK unit test with mock `ClaimValidator` instances
  - Dependencies: `ClaimValidatorChain`, `ClaimValidator` interface, `ClaimValidationResult`, `ClaimValidationException`
  - Test Cases (5):
    - TC01: `validateOrThrow()` — all validators return PASS → no exception thrown
    - TC02: `validateOrThrow()` — first validator returns FAIL → throws `ClaimValidationException` immediately (fail-fast), remaining validators NOT called
    - TC03: `validateAll()` — all validators return PASS → returns list of all PASS results
    - TC04: `validateAll()` — mixed PASS/FAIL → returns ALL results (collects all, doesn't short-circuit)
    - TC05: Empty validators list → `validateOrThrow()` no exception, `validateAll()` returns empty list

---

## Phase 3: Observability (production changes — additive)

- [ ] **Task 3: TokenBlacklistCacheService — add MeterRegistry + metrics**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` | Action: [MODIFY]
  - Pattern: Constructor injection `private val meterRegistry: MeterRegistry` — same as `AnonymousSessionHandler.kt` L33
  - Dependencies: `io.micrometer.core.instrument.MeterRegistry`, `Counter`, `Gauge`
  - Detail:
    1. Add import: `import io.micrometer.core.instrument.MeterRegistry`, `import io.micrometer.core.instrument.Counter`, `import io.micrometer.core.instrument.Gauge`
    2. Add constructor parameter: `private val meterRegistry: MeterRegistry`
    3. In init/constructor: register Caffeine size gauge:
       ```kotlin
       Gauge.builder("auth.token.blacklist.caffeine.size") { caffeineCache.estimatedSize().toDouble() }
           .description("Current number of entries in L1 Caffeine blacklist cache")
           .register(meterRegistry)
       ```
    4. In `isBlacklisted()`: add counter increments at each lookup result:
       - L1 hit: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l1_caffeine", "result", "hit").increment()`
       - L1 miss: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l1_caffeine", "result", "miss").increment()`
       - L2 hit: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l2_redis", "result", "hit").increment()`
       - L2 miss: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l2_redis", "result", "miss").increment()`
       - L3 hit: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l3_db", "result", "hit").increment()`
       - L3 miss: `meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l3_db", "result", "miss").increment()`
    5. In circuit breaker logic: add counter increments:
       - Opens: `meterRegistry.counter("auth.token.blacklist.circuit_breaker", "transition", "opened").increment()`
       - Resets: `meterRegistry.counter("auth.token.blacklist.circuit_breaker", "transition", "reset").increment()`

- [ ] **Task 4: JwtAuthFilter — add MeterRegistry + metrics**
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [MODIFY]
  - Pattern: Constructor injection — same as Task 3
  - Dependencies: `io.micrometer.core.instrument.MeterRegistry`, `Counter`, `Timer`
  - Detail:
    1. Add import: `import io.micrometer.core.instrument.MeterRegistry`, `import io.micrometer.core.instrument.Timer`
    2. Add constructor parameter: `private val meterRegistry: MeterRegistry`
    3. In `doFilterInternal()`: wrap validation logic with timer:
       ```kotlin
       val sample = Timer.start(meterRegistry)
       // ... existing validation logic ...
       // On success:
       meterRegistry.counter("auth.token.validation", "result", "success").increment()
       sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "success").register(meterRegistry))
       // On failure:
       meterRegistry.counter("auth.token.validation", "result", "failure", "reason", reasonString).increment()
       sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
       ```
    4. Failure reason strings: `"blacklisted"`, `"signature_invalid"`, `"issuer_mismatch"`, `"audience_mismatch"`, `"type_rejected"`, `"expired"`, `"claim_validation_failed"`

---

## Phase 4: Unit Tests — Cache Service + Filter + Events (depends on Phase 1+3)

- [ ] **Task 5: TokenBlacklistCacheServiceTest**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheServiceTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK + `SimpleMeterRegistry` for metric verification
  - Dependencies: mock `Cache<String, Boolean>` (Caffeine), mock `StringRedisTemplate`, mock `TokenBlacklistRepository`, `SimpleMeterRegistry`, `SecurityProperties` (real or mock with defaults)
  - Test Cases (13):
    - TC01: `isBlacklisted()` — L1 Caffeine hit → returns true, Redis NOT called, DB NOT called
    - TC02: `isBlacklisted()` — L1 miss, L2 Redis hit → returns true, populates L1 via `caffeineCache.put(jti, true)`
    - TC03: `isBlacklisted()` — L1+L2 miss, L3 DB hit → returns true, populates L1
    - TC04: `isBlacklisted()` — all tiers miss → returns false
    - TC05: `isBlacklisted()` — Redis throws exception → falls through to DB (fail-safe)
    - TC06: `isBlacklisted()` — after N consecutive Redis failures (configurable, default 5) → circuit breaker opens
    - TC07: `isBlacklisted()` — circuit breaker open → skips Redis, goes to DB directly
    - TC08: `isBlacklisted()` — circuit breaker resets after cooldown period (default 30s)
    - TC09: `addToBlacklist()` — writes to L1 Caffeine + L2 Redis with key `token:blacklist:{jti}` and TTL
    - TC10: `addToBlacklist()` — Redis write fails → retries once with delay
    - TC11: `addToBlacklist()` — Redis retry also fails → logs warning, continues (DB already written)
    - TC12: `addToBlacklist()` — correct Redis key format `token:blacklist:{jti}` and TTL = remainingSeconds
    - TC13: Metrics — `SimpleMeterRegistry` verifies counter increments for L1 hit, L2 miss, circuit_breaker opened etc.

- [ ] **Task 10: JwtAuthFilterTest**
  - File: `src/test/kotlin/com/ntt/authservice/shared/security/JwtAuthFilterTest.kt` | Action: [NEW]
  - Pattern: Mockito/MockK + `MockHttpServletRequest` + `MockHttpServletResponse` + `MockFilterChain` + `SimpleMeterRegistry`
  - Dependencies: mock `JwtService`, mock `TokenBlacklistCacheService`, mock `ClaimValidatorChain`, mock `TokenEventRecorder`, `SimpleMeterRegistry`, `SecurityProperties`
  - Test Cases (11):
    - TC01: No Authorization header → `filterChain.doFilter()` called, SecurityContext empty
    - TC02: Valid token → SecurityContext set with correct `UsernamePasswordAuthenticationToken`, authorities contain roles + permissions
    - TC03: Blacklisted token → `response.sendError(401)`, `tokenEventRecorder.recordValidationFailure()` called with `BLACKLISTED`
    - TC04: Signature invalid (`JwtException` from `jwtService.parseToken()`) → event recorded `SIGNATURE_INVALID`, filterChain continues
    - TC05: Issuer mismatch (`ClaimValidationException("IssuerClaimValidator", ...)`) → event recorded `ISSUER_MISMATCH` — **verifies BUG-001 fix**
    - TC06: Audience mismatch (`ClaimValidationException("AudienceClaimValidator", ...)`) → event recorded `AUDIENCE_MISMATCH`
    - TC07: Type rejected (`ClaimValidationException("TokenTypeClaimValidator", ...)`) → event recorded `TYPE_REJECTED`
    - TC08: Anonymous token (type="anonymous") → authorities contain `ROLE_ANONYMOUS`
    - TC09: Expired token → filterChain continues without auth, no event recorded (default behavior)
    - TC10: Event recording throws exception → swallowed, filterChain continues normally
    - TC11: Metrics — `SimpleMeterRegistry` verifies `auth.token.validation` counter increments and `auth.token.validation.duration` timer records

- [ ] **Task 11: TokenEventRecorderTest — add recordValidationFailure tests**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorderTest.kt` | Action: [MODIFY]
  - Pattern: Follow existing test structure (TC1-TC9 already exist)
  - Dependencies: Existing mocks (`EventService`, etc.)
  - Test Cases (+3):
    - TC10: `recordValidationFailure(event, correlationId)` → delegates to `eventService.record()` with `aggregateType="Token"`, `aggregateId=0L`, `topic="iam.token.validation-failed"`, `partitionKey=event.tokenJti`
    - TC11: `recordValidationFailure()` with `event.tokenJti = null` → `partitionKey = "unknown"`
    - TC12: `recordValidationFailure()` — `eventService.record()` throws exception → caught and logged (fail-safe, no exception propagated)

---

## Phase 5: Integration Test Update (depends on Phase 1)

- [ ] **Task 12: TokenIntrospectionIntegrationTest — add ClaimValidatorChain tests**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/TokenIntrospectionIntegrationTest.kt` | Action: [MODIFY]
  - Pattern: Follow existing test structure (TC1-TC4 already exist), mock-based
  - Dependencies: Existing mocks + `ClaimValidatorChain`, `TokenBlacklistCacheService`
  - Test Cases (+3):
    - TC5: Token with wrong issuer → `introspect()` returns `active=false` (via `ClaimValidatorChain.validateAll()` returning FAIL for IssuerClaimValidator)
    - TC6: Token with wrong audience (when `SecurityProperties.jwt.audience` is configured) → `introspect()` returns `active=false`
    - TC7: MFA token (type="mfa") → `introspect()` returns `active=false` (TokenTypeClaimValidator rejects)

---

## Post-Completion Checklist

- [ ] All 12 tasks completed
- [ ] `./gradlew test` passes (all existing + new tests)
- [ ] `./gradlew compileKotlin` succeeds (no compilation errors from constructor changes)
- [ ] Verify `ValidationFailureReason` has exactly 6 values
- [ ] Verify `mapValidatorToReason("IssuerClaimValidator")` returns `ISSUER_MISMATCH` (not `SIGNATURE_INVALID`)
- [ ] Verify metrics appear in `/actuator/metrics` endpoint (if Spring Boot Actuator enabled)
- [ ] No raw token strings in any log statements (security review)
