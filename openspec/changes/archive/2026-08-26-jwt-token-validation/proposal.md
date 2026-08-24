# Proposal: jwt-token-validation

> **Change**: jwt-token-validation | **Type**: MAINTENANCE | **Flow**: Query
> **Direction**: Test Hardening + Observability + Surgical Bug Fixes (from brainstorm — Approach B)
> **Previous Iteration**: `archive/2025-08-26-jwt_token_validation/` (EXTEND — all 16 FRs implemented)
> **_Generated**: 2025-01-20_

## Changes

- **auth.domain.event [MODIFY]**: `ValidationFailureReason.kt` — add `ISSUER_MISMATCH` and `CLAIM_VALIDATION_FAILED` enum values. Fixes BUG-001: incorrect audit event mapping for IssuerClaimValidator failures. (FR-012 bug fix)
- **shared.security [MODIFY]**: `JwtAuthFilter.kt` — fix `mapValidatorToReason()` to correctly map `IssuerClaimValidator` → `ISSUER_MISMATCH`, add generic `CLAIM_VALIDATION_FAILED` fallback. Inject `MeterRegistry` for validation counters and timer. (FR-012 bug fix, observability)
- **auth.application [MODIFY]**: `TokenBlacklistCacheService.kt` — inject `MeterRegistry` for cache tier hit/miss counters, circuit breaker transition counters, Caffeine size gauge. (observability)
- **test/ [NEW]**: `TokenBlacklistCacheServiceTest.kt` — ~13 unit tests covering L1/L2/L3 lookup, circuit breaker, write-through, retry. (test hardening)
- **test/ [NEW]**: `ClaimValidatorChainTest.kt` — ~5 unit tests covering fail-fast and collect-all modes. (test hardening)
- **test/ [NEW]**: `IssuerClaimValidatorTest.kt` — ~3 unit tests covering match, mismatch, null. (test hardening)
- **test/ [NEW]**: `AudienceClaimValidatorTest.kt` — ~5 unit tests covering enabled/disabled/match/mismatch. (test hardening)
- **test/ [NEW]**: `TokenTypeClaimValidatorTest.kt` — ~6 unit tests covering allowed/rejected types. (test hardening)
- **test/ [NEW]**: `JwtAuthFilterTest.kt` — ~11 unit tests covering full validation flow. (test hardening)
- **test/ [MODIFY]**: `TokenEventRecorderTest.kt` — +3 tests for `recordValidationFailure()` coverage. (test hardening)
- **test/ [MODIFY]**: `TokenIntrospectionIntegrationTest.kt` — +3 tests for ClaimValidatorChain integration. (test hardening)

**Total**: 3 production files modified (additive changes only), 6 new test files, 2 updated test files, ~49 test cases.

---

## 1. Executive Summary

Iteration MAINTENANCE cho JWT token validation pipeline trong auth-service. Previous iteration (archived EXTEND) đã implement đầy đủ 16 FRs production-grade. Iteration này focus 3 areas:

1. **Bug Fix**: `mapValidatorToReason()` trong `JwtAuthFilter.kt` sai mapping `IssuerClaimValidator` → `SIGNATURE_INVALID` (should be `ISSUER_MISMATCH`). Gây incorrect audit events → pollutes SIEM data.
2. **Test Hardening**: 6 critical components có **zero test coverage**: `TokenBlacklistCacheService`, `ClaimValidatorChain`, 3 `ClaimValidator` implementations, `JwtAuthFilter`. Add ~49 unit tests.
3. **Observability**: Inject Micrometer `MeterRegistry` vào `TokenBlacklistCacheService` và `JwtAuthFilter` — add counters cho cache tier hit/miss, circuit breaker transitions, validation result, timer cho validation duration.

### Business Value
- **Data Integrity**: Fix audit event misclassification (ISSUER_MISMATCH ≠ SIGNATURE_INVALID)
- **Quality**: Testability score from 21/25 → 25/25
- **Operations**: Micrometer metrics enable P95 validation time monitoring, cache hit ratio dashboards, circuit breaker alerting
- **Safety**: All production changes are additive — no behavioral changes to existing logic

### Key Metrics
| Metric | Value |
|--------|-------|
| Production files modified | 3 (additive only) |
| New test files | 6 |
| Updated test files | 2 |
| New test cases | ~49 |
| New enum values | 2 (ISSUER_MISMATCH, CLAIM_VALIDATION_FAILED) |
| Risk | LOW (no architectural changes) |
| Estimated effort | 2-3 developer-days |

---

## 2. Problem Statement

Pre_openspec quality score 90/100 identified 3 deduction areas:

1. **Testability (-4 points)**: Zero test coverage for `TokenBlacklistCacheService` (circuit breaker, cache tiers), `ClaimValidatorChain` (fail-fast/collect-all), individual claim validators, `JwtAuthFilter` (full validation pipeline). `TokenEventRecorder.recordValidationFailure()` also uncovered.

2. **Completeness (-3 points)**: No Micrometer metrics for cache hit rate (L1/L2/L3), circuit breaker state, validation pipeline latency. NFR-001 (P95 <5ms), NFR-004 (introspection <50ms), NFR-007 (>10K req/s) are unverifiable without metrics.

3. **Consistency (-2 points)**: `mapValidatorToReason()` maps `IssuerClaimValidator` (and any future validators) to `SIGNATURE_INVALID` — semantically incorrect. Missing `ISSUER_MISMATCH` enum value in `ValidationFailureReason`.

Additionally, `TokenIntrospectionIntegrationTest` is stale — tests pre-EXTEND logic, doesn't validate `ClaimValidatorChain.validateAll()` integration.

---

## 3. Scope Definition

### In Scope — This Feature
| Item | Description | Priority |
|------|-------------|----------|
| BUG-001 | Fix `mapValidatorToReason()` + add `ISSUER_MISMATCH`, `CLAIM_VALIDATION_FAILED` to enum | P0 |
| TEST-001 | Unit tests: `TokenBlacklistCacheServiceTest` (~13 TCs) | P0 |
| TEST-002 | Unit tests: `ClaimValidatorChainTest` (~5 TCs) | P0 |
| TEST-003 | Unit tests: `IssuerClaimValidatorTest` (~3 TCs) | P1 |
| TEST-004 | Unit tests: `AudienceClaimValidatorTest` (~5 TCs) | P1 |
| TEST-005 | Unit tests: `TokenTypeClaimValidatorTest` (~6 TCs) | P1 |
| TEST-006 | Unit tests: `JwtAuthFilterTest` (~11 TCs) | P0 |
| TEST-007 | Update `TokenEventRecorderTest` (+3 TCs) | P1 |
| TEST-008 | Update `TokenIntrospectionIntegrationTest` (+3 TCs) | P1 |
| OBS-001 | Micrometer metrics in `TokenBlacklistCacheService` | P1 |
| OBS-002 | Micrometer metrics in `JwtAuthFilter` | P1 |

### Out of Scope — Future Features
| Item | Reason |
|------|--------|
| Resilience4j circuit breaker migration | MAINTENANCE scope — custom CB works correctly; migration has hot-path risk |
| `Thread.sleep(100)` async retry replacement | Revocation path only, current frequency <<1/s, acceptable |
| Testcontainers Redis integration tests | Deferred — Mockito unit tests first for MAINTENANCE |
| Negative cache (L1 false results) | NFR-006 compliance: <30s acceptance window takes priority |
| Percentile histograms for validation timer | Deferred — standard timer sufficient for initial observability |

---

## 4. Architecture Decision

### Selected: Approach B — Test Hardening + Observability + Surgical Bug Fixes (from brainstorm)

**Rationale** (score 9/10):
1. Highest value-to-risk ratio — all changes are additive or corrective
2. Addresses all 3 quality score deductions
3. Follows MAINTENANCE classification — verify, harden, observe
4. Established patterns — `MeterRegistry` injection identical to `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`, `AnonymousSessionDataService`, `SessionPromotionService`, `AnonymousRateLimitService`
5. `SimpleMeterRegistry` pattern for tests already established in codebase

### Rejected Alternatives
- **Approach A** (Test Only): Score 6/10 — misses observability, leaves NFRs unverifiable
- **Approach C** (Everything + Resilience4j): Score 6/10 — too much scope for MAINTENANCE, AOP proxy risk on hot path
- **Approach D** (Bug Fix Only): Score 3/10 — wastes MAINTENANCE opportunity

---

## 5. Impact Summary

### Production Files Modified (🔴 Direct — additive only)
| File | Action | Scope |
|------|--------|-------|
| `auth/domain/event/ValidationFailureReason.kt` | [MODIFY] +2 enum values | BUG-001 |
| `shared/security/JwtAuthFilter.kt` | [MODIFY] fix `mapValidatorToReason()` + `MeterRegistry` injection | BUG-001, OBS-002 |
| `auth/application/TokenBlacklistCacheService.kt` | [MODIFY] `MeterRegistry` injection | OBS-001 |

### New Test Files
| File | Target | Test Cases |
|------|--------|:----------:|
| `TokenBlacklistCacheServiceTest.kt` | TokenBlacklistCacheService | ~13 |
| `ClaimValidatorChainTest.kt` | ClaimValidatorChain | ~5 |
| `IssuerClaimValidatorTest.kt` | IssuerClaimValidator | ~3 |
| `AudienceClaimValidatorTest.kt` | AudienceClaimValidator | ~5 |
| `TokenTypeClaimValidatorTest.kt` | TokenTypeClaimValidator | ~6 |
| `JwtAuthFilterTest.kt` | JwtAuthFilter | ~11 |

### Updated Test Files
| File | Added TCs |
|------|:---------:|
| `TokenEventRecorderTest.kt` | +3 |
| `TokenIntrospectionIntegrationTest.kt` | +3 |

### Shared Utilities (🟢 — read-only usage)
| Component | Usage | Change |
|-----------|-------|--------|
| `MeterRegistry` (Micrometer) | Counter/timer registration | None (existing bean) |
| `SimpleMeterRegistry` | Test mocking | None (existing test pattern) |
| `Mockito` / `MockK` | Unit test mocking | None (existing dependency) |

---

## 6. Risk Assessment

| # | Risk | Severity | Probability | Mitigation |
|---|------|----------|-------------|------------|
| 1 | MeterRegistry injection changes JwtAuthFilter constructor | 🟡 Medium | LOW | Spring autowires automatically; same pattern as 5+ other services |
| 2 | New enum values break Kafka deserialization | 🟢 Low | LOW | Additive enum change; consumers should handle unknown values |
| 3 | Metrics overhead on validation hot path | 🟢 Low | LOW | Micrometer counters ~nanosecond cost; timer <1μs |
| 4 | Stale test updates break existing TCs | 🟢 Low | LOW | Updates are additive (new TCs only) |

---

## 7. Traceability

| Item | Proposal Section | SRS Section | Design Section | Task |
|------|-----------------|-------------|----------------|------|
| BUG-001 | Changes #1-2 | §3.1 | §2.1 | T1-T2 |
| TEST-001 | Changes #4 | §3.2 | §3.1 | T5 |
| TEST-002 | Changes #5 | §3.2 | §3.2 | T6 |
| TEST-003 | Changes #6 | §3.2 | §3.3 | T7 |
| TEST-004 | Changes #7 | §3.2 | §3.4 | T8 |
| TEST-005 | Changes #8 | §3.2 | §3.5 | T9 |
| TEST-006 | Changes #9 | §3.2 | §3.6 | T10 |
| TEST-007 | Changes #10 | §3.2 | §3.7 | T11 |
| TEST-008 | Changes #11 | §3.2 | §3.8 | T12 |
| OBS-001 | Changes #3 | §3.3 | §4.1 | T3 |
| OBS-002 | Changes #2 | §3.3 | §4.2 | T4 |
