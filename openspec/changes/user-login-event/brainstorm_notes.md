---
type: brainstorm_notes
change: user-login-event
date: 2025-08-22
selected_direction: "Mirror-Pattern with Centralized Failure Capture"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: User Login Event

## Date
2025-08-22

## Context
The auth-service needs production-grade login event recording. Currently `LoginHandler` does NOT persist any domain events for login actions — only `LoginSessionEntity` (DB record) and a minimal 2-field `UserLoggedInEvent` via Spring's `ApplicationEventPublisher` (in-process, not persisted, not relayed to Kafka). Meanwhile, the event sourcing infrastructure is production-ready (`EventService`, `EventEnvelope`, `OutboxPoller`, `KafkaEventPublisher`) and proven with `UserRegisteredEvent` and `TokenIssuedEvent/TokenRevokedEvent/TokenValidationFailedEvent`. This feature closes the observability gap by adding enriched login success/failure events that flow through the existing event store → outbox → Kafka pipeline.

## Questions Asked & Answers

### Q1: Should we create a new `LoginEventRecorder` or extend `TokenEventRecorder` to also handle login events?
**→ A: Create a NEW `LoginEventRecorder` (separate class).**
Reasoning:
- `TokenEventRecorder` handles token lifecycle (issuance, revocation, validation failure) — conceptually separate from authentication events
- Single Responsibility: login events have different topics, different aggregate semantics
- Pattern precedent: `TokenEventRecorder` is already scoped to token events. Adding login methods would bloat it and violate SRP
- Keeps each recorder focused and independently testable
- Consistent with the codebase philosophy — `RegisterHandler` uses a separate `EventRecordingService` (now `EventService`) call, `TokenGenerator` delegates to `TokenEventRecorder`

### Q2: How should failure events be recorded in LoginHandler — individual calls before each throw, centralized try-catch, or helper function?
**→ A: Centralized try-catch wrapping the handle() body.**

Three approaches analyzed:

```
┌─────────────────────────────────────────────────────────────┐
│  APPROACH A: Centralized try-catch (✅ SELECTED)            │
│                                                             │
│  handle(command) {                                          │
│    var resolvedUser: User? = null                           │
│    try {                                                    │
│      ... existing flow ...                                  │
│      // record success event at the end                     │
│      return LoginResult.Success(...)                        │
│    } catch (e: AuthException) {                             │
│      recordFailure(resolvedUser, command, mapReason(e))     │
│      throw e  // always rethrow                             │
│    }                                                        │
│  }                                                          │
│                                                             │
│  Pros: ✅ Single integration point                          │
│        ✅ Cannot miss a failure path                         │
│        ✅ Minimal code change (wrap existing body)           │
│        ✅ All LoginHandler exceptions are AuthException      │
│        ✅ MFA checkpoint returns early, not caught           │
│  Cons: ⚠️ Slightly less explicit per-exception              │
│        ⚠️ Need local var for resolvedUser (nullable)        │
├─────────────────────────────────────────────────────────────┤
│  APPROACH B: Individual calls before each throw             │
│                                                             │
│  // Before: throw InvalidCredentialsException()             │
│  // After:                                                  │
│  loginEventRecorder.recordLoginFailure(...)                 │
│  throw InvalidCredentialsException()                        │
│                                                             │
│  Pros: ✅ Most explicit per-exception                        │
│  Cons: ❌ 6+ code insertions, clutters handler              │
│        ❌ Easy to forget one when adding new paths           │
│        ❌ RateLimitExceededException from recordFailed       │
│           also needs handling                                │
├─────────────────────────────────────────────────────────────┤
│  APPROACH C: Private helper recordFailureAndThrow()         │
│                                                             │
│  private fun recordFailureAndThrow(                         │
│    reason: LoginFailureReason,                              │
│    exception: AuthException,                                │
│    ...                                                      │
│  ): Nothing { recorder.record(...); throw exception }       │
│                                                             │
│  Pros: ✅ Reduces duplication                                │
│  Cons: ❌ Hides control flow (throws inside helper)         │
│        ❌ Still needs call at each throw site                │
│        ❌ `Nothing` return type may confuse                  │
└─────────────────────────────────────────────────────────────┘
```

**Decision rationale for Approach A:**
1. All exceptions in `LoginHandler.handle()` extend `AuthException` (which extends `BusinessException`). Single catch type.
2. `AuthErrorCode` on each exception provides natural mapping to `LoginFailureReason` (e.g., `AuthErrorCode.INVALID_CREDENTIALS → LoginFailureReason.INVALID_CREDENTIALS`).
3. MFA checkpoint returns `LoginResult.MfaRequired` (early return, NOT exception) — won't trigger the catch block. This is correct behavior: MFA required is not a login failure.
4. `RateLimitExceededException` can bubble up from `loginRateLimitService.recordFailedAttempt()` — centralized catch handles this automatically.
5. Single point of change — future new exceptions automatically captured.

**Implementation sketch:**
```kotlin
@Transactional
override fun handle(command: LoginCommand): LoginResult {
    var resolvedUser: com.ntt.authservice.auth.domain.model.User? = null
    try {
        // === existing flow unchanged ===
        val user = userPort.findByUsernameAndActive(command.username)
            ?: run { /* ... existing rate limit + throw */ }
        resolvedUser = user
        
        // ... all existing auth checks ...
        // ... MFA checkpoint (returns early, not caught) ...
        // ... token generation, session recording ...
        
        // NEW: Record success event (fire-and-forget)
        loginEventRecorder.recordLoginSuccess(
            event = UserLoggedInEvent(/* enriched fields */),
            userId = user.id.value,
            correlationId = command.correlationId
        )
        
        return LoginResult.Success(authResponse, promotionResult)
    } catch (e: AuthException) {
        // NEW: Record failure event (fire-and-forget)
        loginEventRecorder.recordLoginFailure(
            event = UserLoginFailedEvent(
                usernameAttempted = command.username,
                userId = resolvedUser?.id?.value,
                failureReason = mapToFailureReason(e),
                ipAddress = command.ipAddress,
                userAgent = command.userAgent,
                deviceFingerprint = command.deviceFingerprint
            ),
            correlationId = command.correlationId
        )
        throw e  // always rethrow original
    }
}
```

### Q3: What should `isNewDevice` source be for the success event?
**→ A: Use `LoginSessionService.recordLogin()` return value.**
Currently, `LoginHandler` calls `loginSessionService.recordLogin()` which returns `LoginSessionEntity` that has `isNewDevice` field. However, the current LoginHandler code **does NOT capture the return value**. The fix is simple: capture the returned entity.

```
Current:  loginSessionService.recordLogin(userId, ipAddress, ...)
Needed:   val loginSession = loginSessionService.recordLogin(userId, ipAddress, ...)
          // then: isNewDevice = loginSession.isNewDevice
```

### Q4: Should LoginCommand get a `correlationId` field?
**→ A: Yes — add optional `correlationId: String? = null`.**
- `CqrsAuthController` should extract `X-Correlation-ID` header (or `X-Request-ID`) and pass it into `LoginCommand`
- If no header → `EventService` auto-generates UUID (existing behavior)
- Pattern reference: `TokenIssuanceMetadata` already has similar threading
- This is a minor additive change to `LoginCommand` data class

### Q5: How to handle the `mfaBypassed` field in UserLoggedInEvent?
**→ A: Derive from the MFA checkpoint logic.**
In `LoginHandler`, MFA is checked via `user.requiresMfa(trustedDeviceHash, ttlDays)`:
- If `requiresMfa()` returns `true` → LoginHandler returns `MfaRequired` (NO event emitted for non-MFA path — this is correct)
- If `requiresMfa()` returns `false` AND user has MFA configured → `mfaBypassed = true` (trusted device bypass)
- If user doesn't have MFA configured → `mfaBypassed = false` (MFA not applicable)

```
mfaBypassed = user.mfaEnabled && !user.requiresMfa(command.trustedDeviceHash, ttlDays)
```

⚠️ Assumption: Need to verify if `User` entity has `mfaEnabled` or similar flag. If not, approximate via `user.mfaMethod != null`.

### Q6: Should we emit event for MFA-required (partial login)?
**→ A: NO — defer to separate scope.**
MFA-required is a checkpoint, not a failure. The user hasn't failed to authenticate — they need to complete step 2 (MFA code). `UserLoggedInEvent` should ONLY emit after full authentication. This aligns with pre_openspec Issue #1 (🔴 risk).

### Q7: What about the old `UserLoggedInEvent` in `AuthDomainEvents.kt` — migration strategy?
**→ A: Safe to remove (hard break, no migration period).**
Evidence:
- `grep_search` for `UserLoggedInEvent` found ONLY the definition in `AuthDomainEvents.kt` — NO usages anywhere
- `grep_search` for `USER_LOGGED_IN` found ONLY the eventType string in the same file — NO consumers
- The old event was published via `SpringEventPublisher` (in-process) and NO `@EventListener` references exist for it
- Therefore: safe to delete the old class and replace with the new enriched version in `auth.domain.event` package
- `SessionRevokedEvent` in the same file should remain — it's a separate event

### Q8: Exception-to-LoginFailureReason mapping — what's the complete map?
**→ A: Verified against actual code:**

| Exception (in LoginHandler.handle()) | Where thrown | User resolved? | LoginFailureReason |
|---------------------------------------|-------------|:---:|:---:|
| `InvalidCredentialsException` | Line ~60 (user not found) | ❌ userId=null | `INVALID_CREDENTIALS` |
| `AccountLockedException` | Line ~72 (account locked) | ✅ | `ACCOUNT_LOCKED` |
| `CaptchaRequiredException` | Line ~84 (CAPTCHA needed) | ✅ | `CAPTCHA_REQUIRED` |
| `CaptchaFailedException` | Line ~87 (CAPTCHA failed) | ✅ | `CAPTCHA_FAILED` |
| `InvalidCredentialsException` | Line ~95 (wrong password) | ✅ | `INVALID_CREDENTIALS` |
| `RateLimitExceededException` | From `loginRateLimitService.recordFailedAttempt()` (~95) | ❌ or ✅ | `RATE_LIMITED` |
| `PasswordExpiredException` | Line ~120 (password expired) | ✅ | `PASSWORD_EXPIRED` |

Note: `ACCOUNT_DISABLED` and `MFA_REQUIRED` in the enum are NOT currently thrown in LoginHandler:
- `ACCOUNT_DISABLED`: Not a current exception in LoginHandler (active users only via `findByUsernameAndActive`). Keep in enum for future use.
- `MFA_REQUIRED`: MFA checkpoint returns `LoginResult.MfaRequired` (early return, NOT exception). Keep in enum for future use, but currently unused.
- `UNKNOWN`: Fallback for any unexpected `AuthException` subclass.

**Mapping function:**
```kotlin
private fun mapToFailureReason(exception: AuthException): LoginFailureReason = when (exception) {
    is InvalidCredentialsException -> LoginFailureReason.INVALID_CREDENTIALS
    is AccountLockedException -> LoginFailureReason.ACCOUNT_LOCKED
    is CaptchaRequiredException -> LoginFailureReason.CAPTCHA_REQUIRED
    is CaptchaFailedException -> LoginFailureReason.CAPTCHA_FAILED
    is PasswordExpiredException -> LoginFailureReason.PASSWORD_EXPIRED
    is RateLimitExceededException -> LoginFailureReason.RATE_LIMITED
    else -> LoginFailureReason.UNKNOWN
}
```

### Q9: Should the `mapToFailureReason` function live in LoginHandler or LoginEventRecorder?
**→ A: In LoginHandler (private function).**
- The mapping is LoginHandler-specific (depends on which exceptions LoginHandler throws)
- LoginEventRecorder should remain generic — it receives a `UserLoginFailedEvent` with the reason already set
- This keeps LoginEventRecorder reusable if other handlers need to record login failure events in the future

### Q10: Event ordering — should UserLoggedInEvent be recorded before or after LoginSessionEntity?
**→ A: AFTER login session recording.**
Reasoning:
1. `LoginSessionService.recordLogin()` provides `isNewDevice` flag needed by the event
2. The event captures the "complete login context" which includes session info
3. TokenIssuedEvent is recorded during `tokenGenerator.generateAuthResponse()` (step 9 in flow)
4. LoginSessionEntity recording happens at step 10
5. UserLoggedInEvent at step 11 (after session) — captures full context
6. Session promotion at step 12 — but we capture promotion result for the event too

**Revised flow order:**
```
Step 9:  tokenGenerator.generateAuthResponse()     → TokenIssuedEvent recorded internally
Step 10: loginSessionService.recordLogin()          → LoginSessionEntity created, isNewDevice determined
Step 11: sessionPromotionService.promoteSession()   → PromotionResult determined
Step 12: loginEventRecorder.recordLoginSuccess()    → UserLoggedInEvent with full context ← NEW
Step 13: return LoginResult.Success(...)
```

## Approaches Considered

### Approach 1: Full Mirror Pattern (SELECTED)
Create `LoginEventRecorder` as exact mirror of `TokenEventRecorder`. Two methods: `recordLoginSuccess()` and `recordLoginFailure()`. LoginHandler modified with centralized try-catch for failure capture and explicit success recording.

- **Pros**: Follows proven pattern exactly. Minimal innovation risk. Consistent with codebase. Independent testability. Clear separation.
- **Cons**: Adds 13th constructor dependency to LoginHandler (acceptable but trending high).

### Approach 2: Embedded Recording (No LoginEventRecorder)
Inject `EventService` directly into `LoginHandler` and call `eventService.record()` inline.

- **Pros**: One fewer class. Simpler dependency tree.
- **Cons**: Violates established pattern (all other handlers use helper recorder). LoginHandler handles fire-and-forget error handling directly. Duplicates try/catch/log pattern. Inconsistent.

### Approach 3: AOP/Aspect-Based Recording
Use Spring AOP `@AfterReturning` / `@AfterThrowing` on LoginHandler.handle() to automatically record events.

- **Pros**: Zero modification to LoginHandler code. Clean separation.
- **Cons**: Hidden magic — behavior not visible in code. Hard to debug. Context (user, command, promotionResult) hard to access from aspect. Poor fit for enriched event payloads. Team ───────────────────────────────────────────────────┐
    │                    LoginHandler.handle()                     │
    │                     (@Transactional)                         │
    │                                                              │
    │  ┌──────────┐   ┌──────────┐   ┌──────────┐  ┌───────────┐ │
    │  │ Validate │──▶│  Check   │──▶│  CAPTCHA  │──▶│ Password  │ │
    │  │  User    │   │  Lock    │   │  Check    │  │  Verify   │ │
    │  └────┬─────┘   └────┬─────┘   └────┬──────┘  └────┬──────┘ │
    │       │ throw        │ throw        │ throw        │ throw  │
    │       ▼              ▼              ▼              ▼        │
    │  ┌─────────────────────────────────────────────────────────┐│
    │  │ catch (e: AuthException) → recordLoginFailure()        ││
    │  │   mapToFailureReason(e) → UserLoginFailedEvent         ││
    │  │   → LoginEventRecorder → EventService → event_store    ││
    │  │   throw e  // always rethrow original                  ││
    │  └─────────────────────────────────────────────────────────┘│
    │                                                              │
    │  ┌──────────┐   ┌──────────┐   ┌──────────┐                │
    │  │  MFA     │──▶│ Generate │──▶│ Record   │                │
    │  │Checkpoint│   │ Tokens   │   │ Session  │                │
    │  └────┬─────┘   └──────────┘   └────┬─────┘                │
    │  return MfaReq                      │                       │
    │  (no event)                    isNewDevice                  │
    │                                     │                       │
    │                          ┌──────────▼──────────┐            │
    │                          │ Promote Anonymous   │            │
    │                          │ Session             │            │
    │                          └──────────┬──────────┘            │
    │                                     │ promotionResult       │
    │                          ┌──────────▼──────────┐            │
    │                          │ recordLoginSuccess() │ ← NEW     │
    │                          │ UserLoggedInEvent    │            │
    │                          │ → LoginEventRecorder │            │
    │                          │ → EventService       │            │
    │                          └──────────┬──────────┘            │
    │                                     │                       │
    │                          return LoginResult.Success          │
    └──────────────────────────────────────────────────────────────┘
                                     │
                              ┌──────▼──────┐
                              │  event_store │ ──(async)──▶ OutboxPoller
                              │  event_outbox│              ──▶ Kafka
                              └─────────────┘                    │
                                                    ┌────────────┤
                                               ┌────▼────┐  ┌───▼──────┐
                                               │iam.user │  │iam.user  │
                                               │.logged_ │  │.login_   │
                                               │in       │  │failed    │
                                               └─────────┘  └──────────┘
```

## Selected Direction

**Approach 1: Full Mirror Pattern with Centralized Failure Capture** — chosen for its proven reliability and consistency with the existing codebase.

**Summary**: Create a dedicated `LoginEventRecorder` @Component (mirroring `TokenEventRecorder` exactly) with two methods: `recordLoginSuccess(UserLoggedInEvent, userId, correlationId)` and `recordLoginFailure(UserLoginFailedEvent, correlationId)`. Integrate into `LoginHandler.handle()` using a centralized try-catch that catches all `AuthException` subclasses, maps them to `LoginFailureReason` enum values via a private `mapToFailureReason()` function, records the failure event (fire-and-forget), then rethrows the original exception. Success events are recorded after login session recording and anonymous session promotion (step 12 in the flow), capturing full enriched context including `isNewDevice` from `LoginSessionService.recordLogin()` return value and `sessionPromotionStatus` from `SessionPromotionService`.

**Why this approach wins:**
1. **Pattern consistency** — follows the exact same architecture as `TokenEventRecorder` + `TokenIssuedEvent`, `UserRegisteredEvent` + `RegisterHandler`. Zero architectural innovation needed.
2. **Centralized failure capture** — a single `catch (e: AuthException)` wrapping the entire `handle()` body ensures no failure path is missed, including `RateLimitExceededException` that can bubble up from `loginRateLimitService.recordFailedAttempt()`. Future new exceptions are automatically captured.
3. **MFA safety** — MFA checkpoint returns `LoginResult.MfaRequired` (early return, NOT exception), so it correctly does NOT trigger failure event recording.
4. **Minimal code change** — 4 new files (domain events + recorder), 3 modified files (LoginHandler, LoginCommand, AuthDomainEvents cleanup), 1 minor modify (CqrsAuthController for correlationId). No new DB tables, no new endpoints.
5. **Fire-and-forget proven** — `LoginEventRecorder` catches ALL exceptions from `EventService.record()`, logs WARN, never blocks authentication flow. Same resilience pattern used by `TokenEventRecorder` in production.

**Key design decisions embedded in this direction:**
- `LoginEventRecorder` is a SEPARATE class from `TokenEventRecorder` (SRP — login vs token lifecycle)
- Exception-to-reason mapping lives as private function in `LoginHandler` (handler-specific, keeps recorder generic)
- `correlationId` added to `LoginCommand` (extracted from `X-Correlation-ID` HTTP header in controller)
- Old `UserLoggedInEvent` in `AuthDomainEvents.kt` safely removed (verified zero usages, zero listeners)
- Event ordering: TokenIssuedEvent (step 9) → LoginSession (step 10) → SessionPromotion (step 11) → UserLoggedInEvent (step 12) → return Success (step 13)

## Pre-classifications (preliminary)
- Feature type: EXTEND (adding event recording to existing login flow)
- Flow type: Command (LoginHandler is CQRS write-side)
- Affected modules: `auth.domain.event` (3 new files), `auth.application.event` (1 new file), `auth.application.command` (2 modified files), `auth.adapter.in.web` (1 minor modify)

## GitNexus Findings (if explored)
- Not explored via GitNexus — grep-based codebase investigation was sufficient for this feature
- Related processes discovered via grep: LoginHandler flow (12 steps), TokenEventRecorder pattern, EventService.record() pipeline
- Key symbols verified: `LoginHandler.handle()`, `TokenEventRecorder.recordIssuance()`, `EventService.record()`, `UserRegisteredEvent`, `ValidationFailureReason`, `AuthDomainEvents.UserLoggedInEvent`
- Architecture insight: existing event sourcing pipeline is production-grade and fully reusable — no infrastructure work needed

### Files Changed Summary

```
NEW FILES (4):
  auth/domain/event/UserLoggedInEvent.kt       ← 14-field enriched event
  auth/domain/event/UserLoginFailedEvent.kt    ← 7-field failure event
  auth/domain/event/LoginFailureReason.kt      ← 9-value enum
  auth/application/event/LoginEventRecorder.kt ← 2-method helper service

MODIFIED FILES (3):
  auth/application/command/LoginHandler.kt     ← inject recorder, try-catch, success call
  auth/application/command/LoginCommand.kt     ← add correlationId field
  auth/application/command/AuthDomainEvents.kt ← remove old UserLoggedInEvent class

MINOR MODIFY (1):
  auth/adapter/in/web/CqrsAuthController.kt   ← pass X-Correlation-ID to LoginCommand

NO CHANGE (reused as-is):
  auth/application/event/EventService.kt
  auth/adapter/out/event/OutboxPoller.kt
  auth/domain/event/EventEnvelope.kt
```

## Open Questions for Design Phase
- [RESOLVED] Q1: Recorder class strategy → Create LoginEventRecorder (separate from TokenEventRecorder)
- [RESOLVED] Q2: Failure capture strategy → Centralized try-catch in LoginHandler
- [RESOLVED] Q3: isNewDevice source → LoginSessionService.recordLogin() return value
- [RESOLVED] Q4: correlationId → Add to LoginCommand, extract from HTTP header in controller
- [RESOLVED] Q5: mfaBypassed → Derive from user.mfaMethod and requiresMfa() result
- [RESOLVED] Q6: MFA partial login → No event; defer to separate scope
- [RESOLVED] Q7: Old UserLoggedInEvent → Safe to remove (hard break, no listeners)
- [RESOLVED] Q8: Exception mapping → Complete map verified against code
- [RESOLVED] Q9: mapToFailureReason location → Private in LoginHandler
- [RESOLVED] Q10: Event ordering → After session recording (step 12)
- [OPEN] OQ-1 (from pre_openspec): MFA verify flow — should emit event in MfaService? → DEFERRED, separate scope
- [OPEN] OQ-2 (from pre_openspec): Backward compatibility for eventType migration → BREAK safe, verified no consumers

## Open Questions for URD Analysis
- None — pre_openspec.md provides comprehensive URD. All questions resolved during brainstorm.

## Risk Assessment Summary

| Risk | Severity | Mitigation |
|------|:---:|-----------|
| Event recording failure blocks login | 🟢 LOW | LoginEventRecorder catches all exceptions (proven TokenEventRecorder pattern) |
| Missing failure paths | 🟢 LOW | Centralized try-catch captures ALL AuthException subclasses automatically |
| LoginHandler 13th constructor dep | 🟡 MEDIUM | Acceptable; monitor. LoginEventRecorder is thin wrapper, doesn't add logic complexity |
| Old UserLoggedInEvent removal breaks consumers | 🟢 LOW | Verified zero usages in codebase — safe hard break |
| correlationId missing in LoginCommand | 🟢 LOW | Additive data class change, default = null for backward compatibility |
| isNewDevice not captured (LoginHandler ignores return) | 🟢 LOW | Simple fix: capture LoginSessionEntity from recordLogin() return |
| MFA flow emits incomplete event | 🟢 LOW | MFA returns early (not exception) — no event emitted for partial login |
