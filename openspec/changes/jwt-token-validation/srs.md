# SRS: jwt-token-validation

_Generated: 2025-01-20_
_Profile: Query | N/A (no factory) | MAINTENANCE_
_Previous Iteration: archive/2025-08-26-jwt_token_validation/ (EXTEND — all 16 FRs implemented)_

---

## 1. System Context

- **Feature Name**: JWT Token Validation — MAINTENANCE Hardening
- **Domain**: Authentication — JWT Token Validation Pipeline
- **Flow**: Query (Token validation is read-only per-request check)
- **Services**: auth-service (MAINTENANCE)
- **Source**: Pre-OpenSpec (quality score 90/100) + Brainstorm (Approach B selected)
- **Scope**: Bug fix + test coverage + observability. NO architectural changes.

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Hệ thống (auth-service) | Xử lý token validation, blacklist check, claim validation | JwtAuthFilter → JwtService → TokenBlacklistCacheService → ClaimValidatorChain |
| Developer / QA | Write and run unit tests, verify metrics | Test execution, metrics dashboard |
| Operations Team | Monitor validation metrics, cache hit ratios, circuit breaker state | Micrometer metrics → Prometheus → Grafana |

---

## 3. Functional Requirements

### 3.1 Bug Fixes

#### BUG-001: Fix `mapValidatorToReason()` incorrect mapping [MAINTENANCE]
- **Actor**: Hệ thống
- **Precondition**: `JwtAuthFilter` catches `ClaimValidationException` from `ClaimValidatorChain.validateOrThrow()` where `validatorName = "IssuerClaimValidator"`.
- **Current Behavior (BUG)**: `mapValidatorToReason("IssuerClaimValidator")` → falls into `else` branch → returns `ValidationFailureReason.SIGNATURE_INVALID`. This is semantically incorrect — an issuer mismatch is NOT a signature problem. This pollutes audit events with wrong failure classification.
- **Expected Behavior**: `mapValidatorToReason("IssuerClaimValidator")` → returns `ValidationFailureReason.ISSUER_MISMATCH`.
- **Action**:
  1. Add `ISSUER_MISMATCH` enum value to `ValidationFailureReason` — represents issuer claim mismatch. WARN level logging.
  2. Add `CLAIM_VALIDATION_FAILED` enum value to `ValidationFailureReason` — generic fallback for future validators. WARN level logging.
  3. Update `mapValidatorToReason()` in `JwtAuthFilter`:
     - `"IssuerClaimValidator"` → `ValidationFailureReason.ISSUER_MISMATCH`
     - `"AudienceClaimValidator"` → `ValidationFailureReason.AUDIENCE_MISMATCH` (unchanged)
     - `"TokenTypeClaimValidator"` → `ValidationFailureReason.TYPE_REJECTED` (unchanged)
     - `else` → `ValidationFailureReason.CLAIM_VALIDATION_FAILED` (generic, future-proof)
- **Validation**: 
  - Enum has 6 values: BLACKLISTED, SIGNATURE_INVALID, ISSUER_MISMATCH, AUDIENCE_MISMATCH, TYPE_REJECTED, CLAIM_VALIDATION_FAILED
  - `mapValidatorToReason("IssuerClaimValidator")` returns `ISSUER_MISMATCH`
  - `mapValidatorToReason("UnknownFutureValidator")` returns `CLAIM_VALIDATION_FAILED`
- **Affected Files**: 
  - `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt` [MODIFY]
  - `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` [MODIFY]
- **Status**: [BUG FIX]

### 3.2 Test Coverage

#### TEST-001: TokenBlacklistCacheService unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `TokenBlacklistCacheServiceTest.kt` with comprehensive Mockito-based unit tests.
- **Test Cases**:
  - TC01: `isBlacklisted()` — L1 Caffeine hit → returns `true`, no Redis/DB call
  - TC02: `isBlacklisted()` — L1 miss, L2 Redis hit → returns `true`, populates L1
  - TC03: `isBlacklisted()` — L1+L2 miss, L3 DB hit → returns `true`, populates L1
  - TC04: `isBlacklisted()` — all tiers miss → returns `false`
  - TC05: `isBlacklisted()` — Redis throws exception → falls through to DB (fail-safe)
  - TC06: `isBlacklisted()` — circuit breaker opens after N consecutive Redis failures
  - TC07: `isBlacklisted()` — circuit breaker open → skips Redis, goes to DB directly
  - TC08: `isBlacklisted()` — circuit breaker resets after cooldown period
  - TC09: `addToBlacklist()` — writes to L1 Caffeine + L2 Redis
  - TC10: `addToBlacklist()` — Redis write fails → retries once with 100ms delay
  - TC11: `addToBlacklist()` — Redis retry also fails → logs warning, continues
  - TC12: `addToBlacklist()` — writes with correct Redis key format and TTL
  - TC13: Metrics counters increment correctly for each tier hit/miss (requires MeterRegistry — OBS-001)
- **Validation**: All 13 TCs pass. Mockito verifies interaction order and arguments.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheServiceTest.kt` [NEW]
- **Dependencies**: Mockito/MockK, SimpleMeterRegistry

#### TEST-002: ClaimValidatorChain unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `ClaimValidatorChainTest.kt`.
- **Test Cases**:
  - TC01: `validateOrThrow()` — all validators pass → no exception thrown
  - TC02: `validateOrThrow()` — first validator fails → throws `ClaimValidationException` (fail-fast, doesn't execute remaining)
  - TC03: `validateAll()` — all validators pass → returns all PASS results
  - TC04: `validateAll()` — mixed pass/fail → returns all results (collects all, doesn't short-circuit)
  - TC05: `validateOrThrow()` and `validateAll()` — empty validator list → no exception, empty results
- **Validation**: All 5 TCs pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChainTest.kt` [NEW]

#### TEST-003: IssuerClaimValidator unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `IssuerClaimValidatorTest.kt`.
- **Test Cases**:
  - TC01: Matching issuer → PASS
  - TC02: Mismatched issuer → FAIL with reason "Issuer mismatch: expected={x}, actual={y}"
  - TC03: Null issuer in claims → FAIL
- **Validation**: All 3 TCs pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/IssuerClaimValidatorTest.kt` [NEW]

#### TEST-004: AudienceClaimValidator unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `AudienceClaimValidatorTest.kt`.
- **Test Cases**:
  - TC01: Disabled (empty audience config) → PASS (skip validation)
  - TC02: Enabled + matching audience → PASS
  - TC03: Enabled + mismatched audience → FAIL
  - TC04: Enabled + null audience claim → FAIL
  - TC05: Enabled + audience list contains expected value → PASS
- **Validation**: All 5 TCs pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/AudienceClaimValidatorTest.kt` [NEW]

#### TEST-005: TokenTypeClaimValidator unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `TokenTypeClaimValidatorTest.kt`.
- **Test Cases**:
  - TC01: null type → PASS (backward compatible)
  - TC02: "access" → PASS
  - TC03: "anonymous" → PASS
  - TC04: "mfa" → FAIL "Token type 'mfa' not allowed as access token"
  - TC05: "refresh" → FAIL "Token type 'refresh' not allowed as access token"
  - TC06: unknown type (e.g., "service") → FAIL
- **Validation**: All 6 TCs pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/TokenTypeClaimValidatorTest.kt` [NEW]

#### TEST-006: JwtAuthFilter unit tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Create `JwtAuthFilterTest.kt` with mocked dependencies.
- **Test Cases**:
  - TC01: No Authorization header → `filterChain.doFilter()` called, no SecurityContext set
  - TC02: Valid token → SecurityContext set with correct authorities
  - TC03: Blacklisted token → returns 401, event recorded with `BLACKLISTED` reason
  - TC04: Signature invalid (JwtException) → event recorded with `SIGNATURE_INVALID`, continue without auth
  - TC05: Issuer mismatch (ClaimValidationException) → event recorded with `ISSUER_MISMATCH` (BUG-001 verified)
  - TC06: Audience mismatch → event recorded with `AUDIENCE_MISMATCH`
  - TC07: Type rejected → event recorded with `TYPE_REJECTED`
  - TC08: Anonymous token → ROLE_ANONYMOUS authority set
  - TC09: Expired token → continue without auth, no event recorded (unless configured)
  - TC10: Exception in event recording → doesn't break filter chain
  - TC11: Metrics counter increments for validation result (requires MeterRegistry — OBS-002)
- **Validation**: All 11 TCs pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/shared/security/JwtAuthFilterTest.kt` [NEW]
- **Dependencies**: Mockito/MockK, SimpleMeterRegistry, MockHttpServletRequest/Response

#### TEST-007: TokenEventRecorder — add `recordValidationFailure()` tests [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Update existing `TokenEventRecorderTest.kt` with 3 new test cases.
- **Test Cases**:
  - TC10: `recordValidationFailure()` delegates to `EventService.record()` with correct topic `iam.token.validation-failed` and aggregateType `Token`
  - TC11: `recordValidationFailure()` with null `tokenJti` → partitionKey = "unknown"
  - TC12: `recordValidationFailure()` swallows exceptions (fail-safe behavior)
- **Validation**: Existing TC1-TC9 still pass. New TC10-TC12 pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorderTest.kt` [MODIFY]

#### TEST-008: TokenIntrospectionIntegrationTest — update for ClaimValidatorChain [MAINTENANCE]
- **Actor**: Developer / QA
- **Action**: Update existing `TokenIntrospectionIntegrationTest.kt` with 3 new test cases.
- **Test Cases**:
  - TC5: Token with wrong issuer → `active=false` (via ClaimValidatorChain.validateAll())
  - TC6: Token with wrong audience (when configured) → `active=false`
  - TC7: MFA token introspected → `active=false` (TokenTypeClaimValidator rejects)
- **Validation**: Existing TC1-TC4 still pass. New TC5-TC7 pass.
- **Test File**: `src/test/kotlin/com/ntt/authservice/auth/integration/TokenIntrospectionIntegrationTest.kt` [MODIFY]

### 3.3 Observability

#### OBS-001: Micrometer metrics in TokenBlacklistCacheService [MAINTENANCE]
- **Actor**: Operations Team
- **Precondition**: `MeterRegistry` bean available (Spring Boot auto-configured).
- **Action**: Inject `MeterRegistry` into `TokenBlacklistCacheService` constructor. Register metrics:
  - **Counter** `auth.token.blacklist.lookup` — tags: `tier`={l1_caffeine|l2_redis|l3_db}, `result`={hit|miss}
    - Increment on each lookup tier with appropriate result
  - **Counter** `auth.token.blacklist.circuit_breaker` — tags: `transition`={opened|reset}
    - Increment when circuit breaker opens (threshold exceeded) or resets (cooldown elapsed)
  - **Gauge** `auth.token.blacklist.caffeine.size` — current L1 cache estimated entry count
    - Registered once in constructor via `Gauge.builder().register()`
- **Validation**:
  - After L1 hit: `auth.token.blacklist.lookup{tier=l1_caffeine,result=hit}` increments by 1
  - After L1 miss + L2 hit: both `{tier=l1_caffeine,result=miss}` and `{tier=l2_redis,result=hit}` increment
  - Circuit breaker open: `{transition=opened}` increments
  - Gauge reports Caffeine `estimatedSize()`
- **Affected File**: `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` [MODIFY]
- **Pattern Reference**: `AnonymousSessionHandler.kt` L33 `private val meterRegistry: MeterRegistry`

#### OBS-002: Micrometer metrics in JwtAuthFilter [MAINTENANCE]
- **Actor**: Operations Team
- **Action**: Inject `MeterRegistry` into `JwtAuthFilter` constructor. Register metrics:
  - **Counter** `auth.token.validation` — tags: `result`={success|failure}, `reason`={blacklisted|signature_invalid|issuer_mismatch|audience_mismatch|type_rejected|expired|claim_validation_failed|no_header}
    - Increment at each validation outcome
  - **Timer** `auth.token.validation.duration` — tags: `result`={success|failure}
    - Wrap token validation logic (parse → blacklist → claims → authorities) in `Timer.record()`
- **Validation**:
  - Successful validation: `{result=success}` counter increments, timer records duration
  - Blacklisted token: `{result=failure,reason=blacklisted}` counter increments
  - No header: `{result=success,reason=no_header}` counter not incremented (skip)
- **Affected File**: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` [MODIFY]
- **Pattern Reference**: Same constructor injection as `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`

---

## 4. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Measurement |
|--------|------|---------|--------|-------------|
| NFR-001 | Performance | Metrics overhead per request | <1μs additional | Micrometer counter increment is ~ns |
| NFR-002 | Testability | Unit test coverage for validation pipeline | 100% method coverage | All public methods of target classes tested |
| NFR-003 | Observability | Cache hit ratio visible in monitoring | L1/L2/L3 breakdowns | `auth.token.blacklist.lookup` counter with tier tags |
| NFR-004 | Observability | Validation P95 latency visible | Timer metric exposed | `auth.token.validation.duration` timer |
| NFR-005 | Backward Compat | No behavioral changes to existing logic | 100% | All tests pass, API unchanged |

---

## 5. Assumptions

- ⚠️ Assumption: `SimpleMeterRegistry` is available as test utility — reason: used in `AnonymousSessionHandlerTest`, `RenewAnonymousTokenHandlerTest` etc.
- ⚠️ Assumption: Mockito/MockK is available as test dependency — reason: used extensively in existing tests (`TokenEventRecorderTest`, `JwtServiceAnonymousTest`)
- ⚠️ Assumption: Adding constructor parameter `MeterRegistry` to `JwtAuthFilter` and `TokenBlacklistCacheService` is backward compatible — reason: Spring autowires `MeterRegistry` automatically (Boot actuator dependency)
- ⚠️ Assumption: Adding enum values `ISSUER_MISMATCH` and `CLAIM_VALIDATION_FAILED` to `ValidationFailureReason` is backward compatible for Kafka consumers — reason: additive enum change, consumers should handle unknown values per Kafka best practices

---

## 6. Open Questions

- ⚠️ OPEN QUESTION: OQ-001 — Should Micrometer metrics include percentile histograms for `auth.token.validation.duration`? Histogram buckets add memory overhead. Decision: defer — standard timer sufficient for initial observability.
- ⚠️ OPEN QUESTION: OQ-002 — Should `TokenBlacklistCacheService` expose `estimatedSize()` method for Caffeine gauge, or register gauge internally in constructor? Decision: register internally in constructor (simpler, no public API change).
- ⚠️ OPEN QUESTION: OQ-003 — Key rotation overlap documentation location. Decision: include in design.md documentation section (no code change needed).

---

## 7. Quality Score Impact

| Tiêu chí | Before | After | Improvement |
|----------|--------|-------|-------------|
| Clarity | 24/25 | 24/25 | No change |
| Completeness | 22/25 | 25/25 | +3 (metrics added) |
| Consistency | 23/25 | 25/25 | +2 (mapValidatorToReason fixed) |
| Testability | 21/25 | 25/25 | +4 (49 test cases added) |
| **Total** | **90/100** | **99/100** | **+9** |
