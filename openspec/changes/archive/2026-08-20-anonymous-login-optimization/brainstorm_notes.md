---
type: brainstorm_notes
change: anonymous-login-optimization
date: 2025-01-20
selected_direction: "Approach D — Post-Implementation Hardening (Bug Fixes + Tests + Observability)"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Anonymous Login Optimization

## Date
2025-01-20

## Context
The anonymous login optimization feature was originally classified as **EXTEND** in `pre_openspec.md`, targeting the addition of anonymous/guest sessions to the auth-service. However, the Phase B code scan in pre_openspec revealed a **CRITICAL finding**: the feature is **FULLY IMPLEMENTED** in the codebase. All 12 planned ADD classes exist, and all 10 planned MODIFY classes are already modified.

This brainstorm re-evaluates the feature from a **post-implementation** perspective — identifying bugs, gaps, and optimization opportunities in the existing implementation rather than designing a new feature.

### Input Sources
- `wf_feature_research`: 8 research artifacts, 100% gap coverage, all 5 validation checks PASS
- `wf_pre_openspec`: 13 FRs, quality score 82/100, Phase B scan = FULLY IMPLEMENTED
- Direct code review of all 22 implementation files (12 ADD + 10 MODIFY)

## Questions Asked & Answers

- Q1: **What is the actual optimization opportunity if the feature is fully implemented?**
  - A: **Bug fixes, missing test coverage, observability gaps, and code hardening.** The implementation is functionally complete but has several issues that range from correctness bugs (JTI blacklisting) to architectural smells (ThreadLocal for cross-component communication) to operational gaps (zero metrics, zero tests).

- Q2: **Should the feature type be reassessed from EXTEND to MAINTENANCE?**
  - A: **Yes, reassess to MAINTENANCE.** The pre_openspec already flagged this in section 13 (Implementation Status): *"the feature type should be reassessed as MAINTENANCE (optimization/refinement of existing implementation)"*. No new domain capabilities are being added — all optimization work is hardening existing implementation.

- Q3: **What is the severity ranking of identified issues?**
  - A: After systematic code review, issues are ranked:

    | # | Issue | Severity | Category |
    |---|-------|:--------:|----------|
    | 1 | JTI placeholder bug — `anonymousJti = ""` passed to SessionPromotionService | 🔴 BUG | Correctness |
    | 2 | Zero test coverage for entire anonymous feature | 🔴 GAP | Quality |
    | 3 | RegisterHandler ThreadLocal for promotion result | 🟡 SMELL | Architecture |
    | 4 | No observability (metrics/counters) for anonymous lifecycle | 🟡 GAP | Operations |
    | 5 | SCAN-per-write in getSessionDataSize() — O(n) per store operation | 🟡 PERF | Performance |
    | 6 | Data transfer without Redis pipelining | 🟢 PERF | Performance |
    | 7 | promotedDataTtlSeconds has no consuming service defined | 🟢 DESIGN | Clarity |
    | 8 | Rate limit lockSeconds=0 (no lockout vs LoginRateLimitService) | 🟢 INCONSISTENCY | Consistency |

- Q4: **How critical is the JTI placeholder bug?**
  - A: **Critical for security correctness.** The bug exists in both `LoginHandler` (L160) and `RegisterHandler` (L94):
    ```kotlin
    anonymousJti = "" // JTI is extracted at controller level when available
    ```
    This means `SessionPromotionService.promoteSession()` receives an empty string for `anonymousJti`, and inserts a `TokenBlacklistEntity` with `tokenJti = ""` into the `token_blacklist` table. Consequences:
    1. The actual anonymous token JTI is **NOT blacklisted** — the token can be reused after promotion
    2. A useless empty-JTI record is created in the database
    3. The FR-005 requirement ("blacklist anonymous token JTI after promotion") is **NOT satisfied**

    **Root cause**: The `LoginCommand` and `RegisterCommand` do not carry the anonymous token JTI. The controller layer has the Authorization header, but the CQRS command doesn't propagate it. The comment "JTI is extracted at controller level when available" suggests the developer intended to fix this but didn't.

    **Fix**: Add `anonymousTokenJti: String? = null` to `LoginCommand` and `RegisterCommand`. Extract JTI from the anonymous token at the controller level (if the client sends an `X-Anonymous-Token` header or includes it in the request body) and pass it through the command.

- Q5: **How should the RegisterHandler ThreadLocal smell be fixed?**
  - A: **Return a composite result type instead of using ThreadLocal.** The current pattern:
    ```kotlin
    // RegisterHandler
    private val promotionResultHolder = ThreadLocal<PromotionResult?>()
    var lastPromotionResult: PromotionResult?
    
    // CqrsAuthController reads it after handle()
    val promotionResult = registerHandler.lastPromotionResult
    ```
    This is fragile because:
    1. It violates single-responsibility — the handler stores state outside its return value
    2. ThreadLocal can leak in pooled threads / virtual threads (Project Loom)
    3. It creates temporal coupling — controller must read before the next handle() call
    
    **Fix**: Change `RegisterHandler` to return a sealed class `RegisterResult` (similar to `LoginResult`):
    ```kotlin
    sealed class RegisterResult {
        data class Success(val authToken: AuthToken, val promotionResult: PromotionResult? = null) : RegisterResult()
    }
    ```
    This matches how `LoginHandler` returns `LoginResult.Success(response, promotionResult)`.

- Q6: **How severe is the O(n) SCAN-per-write in getSessionDataSize()?**
  - A: **Moderate — impacts write latency at scale.** Every call to `storeData()` triggers `getSessionDataSize()` which does a full `SCAN` of `anon:data:{sessionId}:*` and calls `size()` on each key. For a session with 50 keys, that's 50 Redis round-trips per write.

    **Fix**: Track cumulative size in the session metadata hash (`anon:session:{sessionId}` → add `dataSizeBytes` field). Increment on write, decrement on delete. This reduces size-check from O(n) to O(1). The full SCAN can be a fallback for consistency verification.

- Q7: **What metrics should be added for observability?**
  - A: **Micrometer counters and gauges aligned with the state machine transitions:**
    - `auth.anonymous.sessions.created` (counter) — total sessions created
    - `auth.anonymous.sessions.renewed` (counter) — total token renewals
    - `auth.anonymous.sessions.promoted` (counter, tagged by status: SUCCESS/PARTIAL/FAILED/CONFLICT)
    - `auth.anonymous.rate_limited` (counter) — rate limit rejections
    - `auth.anonymous.data.stored` (counter) — data store operations
    - `auth.anonymous.data.size_exceeded` (counter) — data limit rejections
    - `auth.anonymous.promotion.duration` (timer) — promotion latency
    - `auth.anonymous.token.generation.duration` (timer) — token generation latency

- Q8: **Should Redis pipelining be used for data transfer during promotion?**
  - A: **Yes, for sessions with many keys.** The current implementation does individual `GET` + `SET` per key in the `transferData()` loop. Redis pipelining (`executePipelined`) batches these operations, reducing network round-trips from 2n to 2 (one pipeline for GETs, one for SETs). For small sessions (< 5 keys), the overhead is negligible, but for larger sessions (50+ keys), pipelining provides measurable latency improvement.

    **Priority**: 🟢 LOW — the current implementation is correct and works within the <50ms P95 target for small sessions. Pipelining is a nice-to-have optimization.

- Q9: **What about the missing lockSeconds=0 in anonymous rate limit config?**
  - A: **Acceptable difference from LoginRateLimitService.** The anonymous rate limit uses `lockSeconds = 0` which means no lockout period — the rate limit resets after the window expires. This is intentional: anonymous users have no persistent identity, so a lockout would just force them to switch IP or wait. The fixed window with `maxAttempts=5` per hour is sufficient. However, the default should be documented in the configuration to make it explicit.

- Q10: **Are there any missing endpoints or behaviors from the spec?**
  - A: **Two minor gaps identified:**
    1. **AF-001 in UC-001** (existing anonymous token check): The spec says if a client already has a valid anonymous token, the endpoint should return the existing token info (HTTP 200) instead of creating a new one. The current implementation always creates a new session. This is actually fine for v1 — the client can simply not call the endpoint if they already have a token.
    2. **Retry-After header**: The `AnonymousRateLimitedException` stores `retryAfterSeconds` but the controller doesn't explicitly set a `Retry-After` response header. The `GlobalExceptionHandler` may or may not handle this. Should verify.

## Approaches Considered

### Approach A: Bug Fixes Only (Minimal Scope)
Fix only the critical JTI placeholder bug and the ThreadLocal smell. ~1-2 developer-days.

- **Pros:**
  - Addresses the most critical correctness issue
  - Minimal risk of regression
  - Fast turnaround
- **Cons:**
  - Leaves test gap entirely unaddressed
  - No observability improvement
  - Performance issues remain

### Approach B: Bug Fixes + Tests (Medium Scope)
Fix critical bugs + add comprehensive test coverage. ~3-4 developer-days.

- **Pros:**
  - Addresses correctness AND quality gaps
  - Tests provide regression safety net
  - Validates the entire feature end-to-end
- **Cons:**
  - Still no observability
  - Performance issues remain
  - Moderate effort

### Approach C: Comprehensive Hardening (Large Scope)
Fix all bugs + tests + observability metrics + performance optimizations. ~5-7 developer-days.

- **Pros:**
  - Complete feature hardening
  - Production-ready with full observability
  - All known issues addressed
- **Cons:**
  - Large scope may delay other features
  - Performance optimizations may be premature
  - Risk of scope creep

### Approach D: Post-Implementation Hardening (Bug Fixes + Tests + Observability) — SELECTED
Fix critical bugs + add tests + add observability metrics. Skip performance optimizations for now (they're micro-optimizations within acceptable bounds). ~4-5 developer-days.

- **Pros:**
  - Addresses all 🔴 and 🟡 severity issues
  - Tests provide regression safety
  - Observability enables production monitoring
  - Performance optimizations deferred (currently within spec: <100ms P95, <50ms promotion overhead)
  - Balanced scope/risk ratio
- **Cons:**
  - O(n) SCAN-per-write remains (acceptable for max 64KB sessions)
  - No Redis pipelining (acceptable for typical session sizes)

## Selected Direction

**Approach D — Post-Implementation Hardening (Bug Fixes + Tests + Observability)** selected for the following reasons:

1. **Correctness first**: The JTI placeholder bug is a security issue — anonymous tokens remain valid after promotion, violating FR-005. Must fix.
2. **Quality gate**: Zero test coverage is unacceptable for a security-critical feature. Integration tests validate the entire flow.
3. **Operational readiness**: Without metrics, the operations team cannot monitor anonymous session health, detect abuse patterns, or alert on resource exhaustion.
4. **Pragmatic scope**: Performance optimizations (pipelining, size tracking counter) are deferred because current implementation meets spec targets. They can be addressed in a future micro-optimization pass.

### Detailed Change Plan

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CHANGE PLAN (Approach D)                         │
├──────────────────────┬──────────────────────────────────────────────┤
│  FIX-001 (🔴 BUG)   │  JTI Blacklisting Fix                       │
│                      │  • Add anonymousTokenJti to Login/Register   │
│                      │    Command data classes                      │
│                      │  • Extract JTI from anonymous token at       │
│                      │    controller level (LoginRequestDto /       │
│                      │    RegisterRequestDto → add anonymousToken   │
│                      │    field, or use X-Anonymous-Token header)    │
│                      │  • Pass JTI through to SessionPromotionSvc   │
│                      │  • Remove empty-string placeholder            │
│                      │  Files: LoginCommand, RegisterCommand,       │
│                      │    LoginHandler, RegisterHandler,             │
│                      │    CqrsAuthController, LoginRequestDto,      │
│                      │    RegisterRequestDto                         │
├──────────────────────┼──────────────────────────────────────────────┤
│  FIX-002 (🟡 SMELL) │  Remove ThreadLocal from RegisterHandler      │
│                      │  • Create RegisterResult sealed class        │
│                      │    (mirrors LoginResult pattern)              │
│                      │  • RegisterHandler returns RegisterResult     │
│                      │    instead of AuthToken                       │
│                      │  • Update CqrsAuthController.register() to   │
│                      │    use RegisterResult                         │
│                      │  • Remove ThreadLocal + lastPromotionResult   │
│                      │  Files: RegisterResult.kt (new),             │
│                      │    RegisterHandler, CqrsAuthController        │
├──────────────────────┼──────────────────────────────────────────────┤
│  FIX-003 (🟡 GAP)   │  Add Observability Metrics                    │
│                      │  • Micrometer counters in all anonymous       │
│                      │    services (session created/renewed/promoted/ │
│                      │    rate-limited/data-stored/data-exceeded)    │
│                      │  • Timer for promotion duration               │
│                      │  • Timer for token generation                 │
│                      │  Files: AnonymousSessionHandler,              │
│                      │    RenewAnonymousTokenHandler,                │
│                      │    SessionPromotionService,                   │
│                      │    AnonymousRateLimitService,                 │
│                      │    AnonymousSessionDataService                │
├──────────────────────┼──────────────────────────────────────────────┤
│  TEST-001 (🔴 GAP)  │  Integration Test Suite                       │
│                      │  • 18 test cases from technical spec §9.6     │
│                      │  • Covers: create session, rate limiting,     │
│                      │    store/read/delete data, renew token,       │
│                      │    promotion on login/register, concurrent    │
│                      │    promotion, expired session, token reuse,   │
│                      │    JwtAuthFilter ROLE_ANONYMOUS               │
│                      │  Files: AnonymousSessionIntegrationTest.kt,  │
│                      │    SessionPromotionIntegrationTest.kt,        │
│                      │    AnonymousRateLimitServiceTest.kt           │
├──────────────────────┼──────────────────────────────────────────────┤
│  TEST-002 (🔴 GAP)  │  Unit Test Suite                              │
│                      │  • JwtService.generateAnonymousToken/parse    │
│                      │  • AnonymousSessionDataService                │
│                      │  • SessionPromotionService                    │
│                      │  • AnonymousRateLimitService                  │
│                      │  Files: JwtServiceAnonymousTest.kt,           │
│                      │    AnonymousSessionDataServiceTest.kt,        │
│                      │    SessionPromotionServiceTest.kt             │
└──────────────────────┴──────────────────────────────────────────────┘
```

## Pre-classifications (preliminary)
- Feature type: **MAINTENANCE** — hardening existing implementation (bug fixes, tests, observability)
- Flow type: **Non-Financial** — no change to business flow, only correctness and quality improvements
- Affected modules:
  - `auth.application.command` — LoginCommand, RegisterCommand, LoginHandler, RegisterHandler (JTI fix + RegisterResult)
  - `auth.application` — SessionPromotionService, AnonymousSessionDataService, AnonymousRateLimitService (metrics)
  - `auth.adapter.in.web` — CqrsAuthController (JTI extraction + RegisterResult handling)
  - `auth.adapter.in.web.dto` — LoginRequestDto, RegisterRequestDto (anonymousToken field)
  - `test/` — New test files (unit + integration)

## Codebase Investigation Findings

### Critical Bug Analysis — JTI Placeholder

```
CURRENT FLOW (BROKEN):
═══════════════════════

  Client                     CqrsAuthController              LoginHandler            SessionPromotionService
    |                               |                              |                          |
    |  POST /login                  |                              |                          |
    |  { username, password,        |                              |                          |
    |    anonymousSessionId }       |                              |                          |
    |  Authorization: Bearer <anon> |                              |                          |
    |   ─────────────────────────→  |                              |                          |
    |                               |  LoginCommand(               |                          |
    |                               |    anonymousSessionId=...,   |                          |
    |                               |    // ⚠️ NO JTI FIELD !!    |                          |
    |                               |  ) ──────────────────────→   |                          |
    |                               |                              |  promoteSession(         |
    |                               |                              |    sessionId=...,        |
    |                               |                              |    userId=...,           |
    |                               |                              |    anonymousJti="" ← 🔴  |
    |                               |                              |  ) ─────────────────────→|
    |                               |                              |                          |
    |                               |                              |        BlacklistEntity(  |
    |                               |                              |          tokenJti=""  🔴 |
    |                               |                              |        )                 |
    |                               |                              |         ↓                |
    |                               |                              |   [DB: empty JTI saved]  |
    |                               |                              |   [Real anon token       |
    |                               |                              |    NOT blacklisted! 🔴]  |


FIXED FLOW:
═══════════

  Client                     CqrsAuthController              LoginHandler            SessionPromotionService
    |                               |                              |                          |
    |  POST /login                  |                              |                          |
    |  { username, password,        |                              |                          |
    |    anonymousSessionId,        |                              |                          |
    |    anonymousToken: "eyJ..." } |                              |                          |
    |   ─────────────────────────→  |                              |                          |
    |                               |  // Extract JTI from         |                          |
    |                               |  // anonymousToken field     |                          |
    |                               |  jti = jwtSvc.parse(token).id|                          |
    |                               |                              |                          |
    |                               |  LoginCommand(               |                          |
    |                               |    anonymousSessionId=...,   |                          |
    |                               |    anonymousTokenJti=jti ✅  |                          |
    |                               |  ) ──────────────────────→   |                          |
    |                               |                              |  promoteSession(         |
    |                               |                              |    sessionId=...,        |
    |                               |                              |    userId=...,           |
    |                               |                              |    anonymousJti=jti  ✅  |
    |                               |                              |  ) ─────────────────────→|
```

### RegisterHandler ThreadLocal Fix — Before/After

```
  RegisterHandler                                CqrsAuthController
  ┌─────────────────────────────────┐            ┌──────────────────────────┐
  │                                 │            │                          │
  │  handle() returns               │            │  val result =            │
  │    RegisterResult.Success(      │            │    registerHandler       │
  │      authToken,                 │  ────────→ │      .handle(command)    │
  │      promotionResult  ✅       │            │                          │
  │    )                            │            │  result.authToken        │
  │                                 │            │  result.promotionResult  │
  │  No ThreadLocal ✅             │            │                          │
  │  No temporal coupling ✅       │            │  // Clean, explicit ✅   │
  │  Thread-safe ✅                │            │                          │
  └─────────────────────────────────┘            └──────────────────────────┘
```

### Implementation Status Summary

```
FEATURE IMPLEMENTATION SCORECARD
═════════════════════════════════

  Functional Completeness     ████████████████████  100%  ✅
  Test Coverage               ░░░░░░░░░░░░░░░░░░░░    0%  🔴
  Bug-Free                    ███████████████░░░░░   75%  🔴 (JTI bug)
  Architecture Clean          ██████████████████░░   90%  🟡 (ThreadLocal)
  Observability               ░░░░░░░░░░░░░░░░░░░░    0%  🟡
  Performance                 ██████████████████░░   90%  🟢 (within spec)
  Security                    █████████████████░░░   85%  🔴 (token not blacklisted)
  Configuration               ████████████████████  100%  ✅
  Error Handling              ████████████████████  100%  ✅
  ─────────────────────────────────────────────────
  OVERALL PRODUCTION READINESS                      71%  🟡

  After Approach D:
  OVERALL PRODUCTION READINESS                      95%  ✅
```

## Design Decisions Captured

### DD-011: Feature type reassessed to MAINTENANCE
- **Decision**: Change feature type from EXTEND to MAINTENANCE
- **Rationale**: All functional code is implemented. Remaining work is bug fixes, test coverage, and observability — maintenance activities on existing code.
- **Impact**: Downstream `wf_openspec` should generate maintenance-scope tasks, not new feature tasks.

### DD-012: JTI fix via request body field (not header)
- **Decision**: Add `anonymousToken: String? = null` to `LoginRequestDto` and `RegisterRequestDto` rather than using a custom header
- **Rationale**: The anonymous token is contextual to the specific login/register operation and should travel with the request body. Custom headers are less discoverable and harder to document. The controller extracts the JTI from the token and passes it through the command.
- **Alternative considered**: `X-Anonymous-Token` header — rejected because it creates API discoverability issues and is inconsistent with the existing `anonymousSessionId` field being in the body.

### DD-013: RegisterResult sealed class (mirrors LoginResult)
- **Decision**: Create `RegisterResult` sealed class to replace ThreadLocal-based promotion result passing
- **Rationale**: Follows the established `LoginResult` pattern. Thread-safe. Explicit. Self-documenting.
- **Impact**: `RegisterHandler` changes return type from `AuthToken` to `RegisterResult`. `CqrsAuthController.register()` updated to unwrap.

### DD-014: Micrometer counters (not custom metrics)
- **Decision**: Use Micrometer `Counter` and `Timer` for observability
- **Rationale**: Spring Boot Actuator already includes Micrometer. The existing auth-service likely uses Actuator (given `requestMatchers("/actuator/**").permitAll()`). Using standard Micrometer APIs enables Prometheus/Grafana integration without custom tooling.
- **Impact**: Add `MeterRegistry` as dependency to anonymous service classes. Define standard metric names with `auth.anonymous.*` prefix.

## Risk Analysis

| Risk | Probability | Impact | Mitigation | Status |
|------|:-:|:-:|-----------|--------|
| JTI bug allows token reuse after promotion | ACTIVE | HIGH | FIX-001 resolves this directly | Active risk → Fix |
| ThreadLocal leak in virtual threads | LOW | MEDIUM | FIX-002 removes ThreadLocal entirely | Deferred risk → Fix |
| Anonymous feature not tested, regressions undetected | MEDIUM | HIGH | TEST-001 + TEST-002 provide coverage | Active risk → Fix |
| No production monitoring for anonymous session abuse | MEDIUM | MEDIUM | FIX-003 adds Micrometer metrics | Active risk → Fix |
| RegisterHandler return type change breaks CommandHandler contract | LOW | MEDIUM | CommandHandler<RegisterCommand, RegisterResult> — verify eventsourcing-utils supports sealed return types | Technical risk → Validate |

## Open Questions for Design Phase
- [OPEN] Should the `anonymousToken` field in LoginRequestDto be the full JWT string (and the controller parses it for JTI), or should the client extract and send only the JTI?
  - Recommendation: Full JWT — the server should validate the token before trusting the JTI. Sending only JTI allows spoofing.
- [OPEN] Should `RegisterHandler` implement `CommandHandler<RegisterCommand, RegisterResult>` or should we use a different pattern to return promotion data?
  - Recommendation: Change to `RegisterResult` sealed class. Verify `eventsourcing-utils` `CommandHandler<C, R>` type parameter accepts sealed classes. If not, use `Pair<AuthToken, PromotionResult?>` or a simple data class wrapper.
- [OPEN] What Grafana dashboard panels should be created for anonymous session monitoring?
  - Recommendation: Out of scope for this change — but define metric names consistently so dashboards can be created later.

## Open Questions for URD Analysis
- Not applicable (MAINTENANCE scope — no new URD needed)
