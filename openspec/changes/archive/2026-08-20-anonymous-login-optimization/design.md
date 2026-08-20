## Context

[CHANGED] Auth-service anonymous login feature đã FULLY IMPLEMENTED. Tất cả 12 classes mới và 10 classes sửa đã tồn tại trong codebase. Tuy nhiên, Phase B code scan phát hiện 4 issues cần khắc phục: JTI placeholder bug (🔴), zero test coverage (🔴), ThreadLocal smell (🟡), và zero observability (🟡).

See `proposal.md` — Why for full motivation. See `brainstorm_notes.md` for direction selection.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (`CommandHandler<C, R>`), Spring Boot + Kotlin
- Anonymous feature: All 12 ADD classes exist, all 10 MODIFY classes already modified
- JwtService: `generateAnonymousToken()` + `parseAnonymousToken()` already at L154+
- RegisterHandler: Uses `ThreadLocal<PromotionResult?>` for promotion result passing
- Login/Register: Both pass `anonymousJti = ""` to SessionPromotionService

### Selected Design Direction (from brainstorm_notes.md)
**Approach D — Post-Implementation Hardening (Bug Fixes + Tests + Observability)**: Fix critical bugs + add tests + add observability metrics. Skip performance optimizations (currently within spec targets).

## Goals / Non-Goals

**Goals:**
- Fix JTI blacklisting bug — anonymous tokens correctly blacklisted after promotion
- Remove ThreadLocal from RegisterHandler — thread-safe, explicit result passing
- Add Micrometer observability metrics — production monitoring ready
- Comprehensive test coverage — unit + integration tests for all anonymous components

**Non-Goals:**
- Redis pipelining for data transfer (performance within spec, deferred)
- O(n) SCAN-per-write optimization (acceptable for max 64KB sessions, deferred)
- Admin UI for anonymous session management (Phase 2)
- Cross-service event notification on promotion (future)

## Decisions

### DD-011: Feature type reassessed to MAINTENANCE [NEW]
- **Decision**: Change feature type from EXTEND to MAINTENANCE
- **Rationale**: All functional code is implemented. Remaining work is bug fixes, test coverage, and observability — maintenance activities on existing code.
- **Impact**: Tasks generate maintenance-scope items, not new feature tasks.

### DD-012: JTI fix via request body field (not header) [NEW]
- **Decision**: Add `anonymousToken: String? = null` to `LoginRequestDto` and `RegisterRequestDto` rather than using a custom header
- **Rationale**: The anonymous token is contextual to the specific login/register operation and should travel with the request body. Custom headers are less discoverable. The controller extracts the JTI from the token and passes it through the command.
- **Alternative rejected**: `X-Anonymous-Token` header — inconsistent with existing `anonymousSessionId` field being in the body.

### DD-013: RegisterResult sealed class (mirrors LoginResult) [NEW]
- **Decision**: Create `RegisterResult` sealed class to replace ThreadLocal-based promotion result passing
- **Rationale**: Follows the established `LoginResult` pattern. Thread-safe. Explicit. Self-documenting.
- **Impact**: `RegisterHandler` changes return type from `AuthToken` to `RegisterResult`. `CqrsAuthController.register()` updated to unwrap.

### DD-014: Micrometer counters (not custom metrics) [NEW]
- **Decision**: Use Micrometer `Counter` and `Timer` for observability
- **Rationale**: Spring Boot Actuator already includes Micrometer. The existing auth-service uses Actuator (given `requestMatchers("/actuator/**").permitAll()`). Using standard Micrometer APIs enables Prometheus/Grafana integration without custom tooling.
- **Impact**: Add `MeterRegistry` as dependency to anonymous service classes. Define standard metric names with `auth.anonymous.*` prefix.

### DD-001 through DD-010 [UNCHANGED]
All previous design decisions remain valid. They describe the original feature design which is already implemented.

## Component Mapping

### Modified Components (MAINTENANCE fixes)

| Component | File | Action | Fix |
|-----------|------|--------|-----|
| `LoginCommand` | `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt` | [MODIFY] | FIX-001 |
| `LoginHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | [MODIFY] | FIX-001 |
| `RegisterCommand` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt` | [MODIFY] | FIX-001 |
| `RegisterHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` | [MODIFY] | FIX-001, FIX-002 |
| `CqrsAuthController` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | [MODIFY] | FIX-001, FIX-002 |
| `LoginRequestDto` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` | [MODIFY] | FIX-001 |
| `RegisterRequestDto` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` | [MODIFY] | FIX-001 |
| `AnonymousSessionHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | [MODIFY] | FIX-003 |
| `RenewAnonymousTokenHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` | [MODIFY] | FIX-003 |
| `SessionPromotionService` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | [MODIFY] | FIX-003 |
| `AnonymousRateLimitService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | [MODIFY] | FIX-003 |
| `AnonymousSessionDataService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | [MODIFY] | FIX-003 |

### New Components

| Component | File | Fix |
|-----------|------|-----|
| `RegisterResult` | `src/main/kotlin/com/ntt/authservice/auth/application/RegisterResult.kt` | FIX-002 |
| `AnonymousSessionIntegrationTest` | `src/test/kotlin/com/ntt/authservice/auth/AnonymousSessionIntegrationTest.kt` | TEST-001 |
| `SessionPromotionIntegrationTest` | `src/test/kotlin/com/ntt/authservice/auth/SessionPromotionIntegrationTest.kt` | TEST-001 |
| `JwtServiceAnonymousTest` | `src/test/kotlin/com/ntt/authservice/auth/application/JwtServiceAnonymousTest.kt` | TEST-002 |
| `AnonymousSessionDataServiceTest` | `src/test/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataServiceTest.kt` | TEST-002 |
| `SessionPromotionServiceTest` | `src/test/kotlin/com/ntt/authservice/auth/application/SessionPromotionServiceTest.kt` | TEST-002 |
| `AnonymousRateLimitServiceTest` | `src/test/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitServiceTest.kt` | TEST-002 |

### Reused Components (no changes needed)

| Component | File | Usage |
|-----------|------|-------|
| `JwtService.parseAnonymousToken()` | `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Called from CqrsAuthController for JTI extraction — exists, no changes |
| `SessionPromotionService.promoteSession()` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | Receives correct JTI now — method signature unchanged |
| `TokenBlacklistRepository` | `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` | `save()`, `existsByTokenJti()` — unchanged |
| `MeterRegistry` | Spring Boot Actuator auto-configured | Injected into service classes |
| All other anonymous classes | Various | Functionally correct — only metrics added |

## Sequence Diagrams

### FIX-001: Login with Correct JTI Blacklisting (FIXED flow)

```
Client                CqrsAuthController    LoginHandler         SessionPromotionService          DB
  │                         │                    │                       │                          │
  │ POST /api/auth/login    │                    │                       │                          │
  │ { user, pass,           │                    │                       │                          │
  │   anonymousSessionId,   │                    │                       │                          │
  │   anonymousToken: "ey…" │  ← ✅ NEW FIELD   │                       │                          │
  │ }                       │                    │                       │                          │
  │──────────────────────→ │                    │                       │                          │
  │                         │                    │                       │                          │
  │                         │ // Extract JTI     │                       │                          │
  │                         │ claims = jwtSvc    │                       │                          │
  │                         │   .parseAnonymous  │                       │                          │
  │                         │   Token(req.token) │                       │                          │
  │                         │ jti = claims.id    │                       │                          │
  │                         │    ← ✅ REAL JTI   │                       │                          │
  │                         │                    │                       │                          │
  │                         │ LoginCommand(      │                       │                          │
  │                         │   ...,             │                       │                          │
  │                         │   anonymousSession │                       │                          │
  │                         │     Id = ...,      │                       │                          │
  │                         │   anonymousToken   │                       │                          │
  │                         │     Jti = jti  ✅  │                       │                          │
  │                         │ )                  │                       │                          │
  │                         │──────────────────→ │                       │                          │
  │                         │                    │ ...standard auth...   │                          │
  │                         │                    │ promoteSession(       │                          │
  │                         │                    │   sessionId,          │                          │
  │                         │                    │   userId,             │                          │
  │                         │                    │   jti = cmd.token     │                          │
  │                         │                    │     Jti  ← ✅ REAL   │                          │
  │                         │                    │ )                     │                          │
  │                         │                    │─────────────────────→ │                          │
  │                         │                    │                       │ blacklist(jti=REAL) ── ✅│
  │                         │                    │                       │─────────────────────────→│
  │                         │                    │                       │                          │
  │ 200 {tokens, promoted}  │                    │                       │                          │
  │←──────────────────────  │                    │                       │                          │
```

### FIX-002: Register with RegisterResult (ThreadLocal removed)

```
Client                CqrsAuthController         RegisterHandler                  
  │                         │                          │                          
  │ POST /api/auth/register │                          │                          
  │ { user, email, pass,    │                          │                          
  │   anonymousSessionId,   │                          │                          
  │   anonymousToken: "ey…" │                          │                          
  │ }                       │                          │                          
  │──────────────────────→ │                          │                          
  │                         │ RegisterCommand(         │                          
  │                         │   ..., tokenJti)         │                          
  │                         │────────────────────────→ │                          
  │                         │                          │ ...create user...        
  │                         │                          │ ...promote session...    
  │                         │                          │                          
  │                         │ RegisterResult.Success(  │  ← ✅ no ThreadLocal    
  │                         │   authToken,             │      No changes
[PostgreSQL: token_blacklist] ← No changes (correct JTI stored now)
       │
TEST FILES (NEW):
[⚡ AnonymousSessionIntegrationTest.kt]
[⚡ SessionPromotionIntegrationTest.kt]
[⚡ JwtServiceAnonymousTest.kt]
[⚡ AnonymousSessionDataServiceTest.kt]
[⚡ SessionPromotionServiceTest.kt]
[⚡ AnonymousRateLimitServiceTest.kt]
```

## Implementation Scorecard

```
BEFORE MAINTENANCE (current state):
═══════════════════════════════════
  Functional Completeness     ████████████████████  100%  ✅
  Test Coverage               ░░░░░░░░░░░░░░░░░░░░    0%  🔴
  Bug-Free                    ███████████████░░░░░   75%  🔴 (JTI bug)
  Architecture Clean          ██████████████████░░   90%  🟡 (ThreadLocal)
  Observability               ░░░░░░░░░░░░░░░░░░░░    0%  🟡
  Performance                 ██████████████████░░   90%  🟢 (within spec)
  Security                    █████████████████░░░   85%  🔴 (token not blacklisted)
  OVERALL PRODUCTION READINESS                      71%  🟡

AFTER MAINTENANCE (target state):
═══════════════════════════════════
  Functional Completeness     ████████████████████  100%  ✅
  Test Coverage               ████████████████████   85%  ✅
  Bug-Free                    ████████████████████  100%  ✅
  Architecture Clean          ████████████████████  100%  ✅
  Observability               ████████████████████   95%  ✅
  Performance                 ██████████████████░░   90%  🟢 (unchanged)
  Security                    ████████████████████  100%  ✅
  OVERALL PRODUCTION READINESS                      95%  ✅
```
