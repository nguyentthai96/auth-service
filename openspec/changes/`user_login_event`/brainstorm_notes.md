---
type: brainstorm_notes
change: user_login_event
date: 2025-08-22
selected_direction: "Approach A: Mirror RegisterHandler Pattern — EventService.record() Integration with MFA Post-Verification Hook"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: User Login Event — Production-Grade Event Sourcing

## Date
2025-08-22

## Context

The auth-service has a mature Event Sourcing foundation established by the `user_registration_event` feature:
- `EventService.record()` — transactional event recording (event store + outbox)
- `EventEnvelope<T>` — CloudEvents-inspired envelope with correlation ID, schema version
- `OutboxPoller` → Kafka relay with retry and DLT
- `UserRegisteredEvent` — enriched domain event in `auth.domain.event` package

**The Gap**: `LoginHandler` currently does NOT record any domain event to the event store or outbox. It only:
1. Records a login session via `LoginSessionService.recordLogin()` (writes to `login_sessions` table)
2. Logs `log.info("User logged in: ...")` — no structured domain event
3. The existing `UserLoggedInEvent(userId, domainCode)` in `AuthDomainEvents.kt` is **defined but never published** by anyone

This means: no audit trail for logins, no Kafka publishing for downstream consumers, minimal payload context, no correlation ID tracing.

**Source**: Research artifacts (7 files), pre_openspec.md (22 FRs, quality 82/100), codebase analysis of LoginHandler.kt, RegisterHandler.kt, EventService.kt, MfaService.kt, MfaController.kt, CqrsAuthController.kt.

## Questions Asked & Answers

### Q1: What is the exact state of `UserLoggedInEvent` today?

→ A: `UserLoggedInEvent(userId: Long, domainCode: String)` exists in `AuthDomainEvents.kt` with `eventType = "USER_LOGGED_IN"`. It implements `DomainEvent` interface. However, **it is never instantiated, never published, and never consumed anywhere in the codebase**. There are zero references to this class outside its definition. It's dead code.

→ **Evidence**: `grep -rn "UserLoggedInEvent" src/` returns only the definition line in `AuthDomainEvents.kt`. No imports, no instantiations, no listeners.

### Q2: Does LoginHandler currently inject EventService?

→ A: **No.** LoginHandler has 12 constructor parameters: `userPort`, `domainPort`, `tokenStore`, `captchaGateway`, `permissionCache`, `securityProperties`, `tokenGenerator`, `passwordPolicyService`, `loginRateLimitService`, `sessionPolicyService`, `loginSessionService`, `sessionPromotionService`. `EventService` is NOT among them. Adding it will make 13 parameters — acceptable but at the upper limit.

→ **Rationale**: RegisterHandler has 6 params (including EventService). LoginHandler is more complex because it handles rate limiting, CAPTCHA, session policy, MFA checkpoint, and session promotion. 13 params is manageable; if it exceeds 15 in the future, consider extracting a `LoginEventRecorder` helper service.

### Q3: What happens when MFA is required during login?

→ A: Critical flow analysis reveals:
```
LoginHandler.handle(command) {
    ...validate credentials...
    ...check account lock...
    ...CAPTCHA check...
    ...validate password...
    ...reset failed logins...
    ...password expiry check...

    // MFA CHECKPOINT — EARLY RETURN
    if (user.requiresMfa(trustedDeviceHash, ttlDays)) {
        return tokenGenerator.generateMfaResult(user.id.value, user.mfaMethod)
        // ↑ Returns LoginResult.MfaRequired — login NOT complete!
        // ↑ NO session recorded, NO tokens generated, NO event
    }

    ...generate tokens...
    ...record login session...
    ...anonymous session promotion...
    ...return LoginResult.Success...
}
```

When MFA is required, `LoginHandler` returns BEFORE generating tokens and recording sessions. The actual login completion happens in `MfaService.verifyMfa()` → `MfaController.verifyMfa()`:

```kotlin
// MfaController.kt
fun verifyMfa(request: MfaVerifyRequest): ResponseEntity<Any> {
    val response = mfaService.verifyMfa(
        mfaToken = request.mfaToken,
        code = request.code,
        trustDevice = request.trustDevice,
        deviceHash = request.deviceHash,
        authResponseBuilder = { userId -> authService.buildAuthResponseForUser(userId) }
    )
    return ResponseEntity.ok(response)
}
```

`MfaService.verifyMfa()` calls the `authResponseBuilder` lambda (which builds AuthResponse with JWT tokens) but does NOT:
- Record a login session (no `LoginSessionService.recordLogin()` call)
- Publish any domain event
- Have access to `LoginCommand` context (IP, device fingerprint, user-agent)

→ **This is a gap**: MFA-verified logins are "invisible" to the login session tracking and event recording systems.

### Q4: How does `LoginSessionService.recordLogin()` return data that we need for the event?

→ A: `LoginSessionService.recordLogin()` returns a `LoginSessionEntity` which contains:
- `id` (session ID)
- `isNewDevice` (boolean — detected by checking `login_sessions` for matching device fingerprint)
- `deviceType`, `browserName`, `osName` (parsed from User-Agent)

These fields are needed for the enriched `UserLoggedInEvent`. The `recordLogin()` call must happen BEFORE event creation so we can capture these enriched fields.

### Q5: Are there any existing `@EventListener` subscribers for `USER_LOGGED_IN` event type?

→ A: **No.** Since `UserLoggedInEvent` was never published, no listeners exist. The migration from `USER_LOGGED_IN` to `iam.user.logged_in` is risk-free. No backward compatibility needed.

→ **Evidence**: No Kafka consumers subscribe to `USER_LOGGED_IN` topic. No `@EventListener` or `@KafkaListener` references to this event type exist in the codebase.

### Q6: What data is available in LoginCommand vs what we need in the event?

→ A: Data availability mapping:

| Field | Available in LoginCommand? | Available elsewhere? |
|-------|:---:|---|
| username | ✅ `command.username` | — |
| password | ✅ (MUST NOT include) | — |
| domainCode | ✅ `command.domainCode` | Resolved in handler |
| ipAddress | ✅ `command.ipAddress` | — |
| userAgent | ✅ `command.userAgent` | — |
| deviceFingerprint | ✅ `command.deviceFingerprint` | — |
| email | ❌ | ✅ `user.email.value` |
| domainId | ❌ | ✅ `domain?.id` |
| deviceType | ❌ | ✅ `loginSession.deviceType` |
| browserName | ❌ | ✅ `loginSession.browserName` |
| osName | ❌ | ✅ `loginSession.osName` |
| isNewDevice | ❌ | ✅ `loginSession.isNewDevice` |
| mfaMethod | ❌ | ✅ `user.mfaMethod` |
| mfaBypassed | ❌ | ✅ Computed from `user.requiresMfa()` |
| correlationId | ❌ (NEW) | ✅ `X-Correlation-ID` header |
| loginSessionId | ❌ | ✅ `loginSession.id` |

→ **Decision**: LoginCommand needs only `correlationId` added. All other enrichment data is available within LoginHandler scope from `user`, `domain`, and `loginSession` objects.

### Q7: What is the `LoginSessionEntity` return type from `recordLogin()`?

→ A: `recordLogin()` returns `LoginSessionEntity` with JPA-managed ID. After save, `saved.id` is populated. The session entity has: `id`, `userId`, `ipAddress`, `userAgent`, `deviceFingerprint`, `deviceType`, `browserName`, `osName`, `sessionActive`, `isNewDevice`, `loginAt`, `lastActivityAt`.

→ **Issue**: Currently `LoginHandler` does NOT capture the return value from `loginSessionService.recordLogin()`. The call is:
```kotlin
loginSessionService.recordLogin(
    userId = user.id.value,
    ipAddress = command.ipAddress ?: "unknown",
    ...
)
// Return value is IGNORED!
```
We need to capture it: `val loginSession = loginSessionService.recordLogin(...)`

### Q8: How should failed login events handle the transactional boundary?

→ A: **Critical insight**: Failed login events need special handling because the login transaction is ROLLING BACK (throwing an exception). If `EventService.record()` is called within the same `@Transactional` boundary and then an exception is thrown, the event record will be rolled back too.

Two strategies considered:

**Strategy 1: Record event BEFORE throwing exception (same TX)**
```kotlin
// Bad: TX rolls back → event lost
try { eventService.record(failedEvent) } catch { /* ignore */ }
throw InvalidCredentialsException()
// ↑ This causes TX rollback, losing the event
```

**Strategy 2: Use `@Transactional(propagation = REQUIRES_NEW)` for failed events**
```kotlin
// Better: Separate TX for failed event, then throw
failedLoginEventRecorder.recordFailedLogin(failedEvent) // REQUIRES_NEW
throw InvalidCredentialsException()
```

→ **Decision**: Strategy 2 — Use a separate `@Transactional(propagation = REQUIRES_NEW)` method (either on a helper service or self-invoked via proxy) to record failed login events. This ensures the event is persisted even when the main transaction rolls back. The `RegisterHandler` doesn't face this problem because it only records events on success.

→ **Alternative considered**: Using `TransactionSynchronization.afterCompletion()` — rejected because it fires after TX completion regardless of outcome and adds complexity.

→ **Simplest approach**: Create a `LoginEventRecorder` helper @Component with `@Transactional(propagation = REQUIRES_NEW)` that encapsulates both success and failed event recording. LoginHandler delegates to it.

### Q9: What about the `user_registration_event` feature — is it already applied?

→ A: **Yes.** The `user_registration_event` feature has been through the full pipeline (research → pre_openspec → brainstorm → openspec → openspec_apply → archive). The codebase already reflects:
- `UserRegisteredEvent` in `auth.domain.event` package with `eventType = "iam.user.registered"`
- `EventService.record()` called in `RegisterHandler`
- `EventEnvelope`, `EventStorePort`, `OutboxPort`, `OutboxPoller` all exist
- Old `UserRegisteredEvent` removed from `EventPublisher.kt` and `AuthDomainEvents.kt`

→ **Implication**: We follow the EXACT same pattern. The infrastructure is proven and ready.

### Q10: Should the event query API (UC-003) be included in this feature?

→ A: **Deferred.** UC-003 (query login events from event store) is a read-side concern. The event store already has an `idx_event_store_type` index. A generic event query API would benefit ALL event types, not just login events. Including it here would increase scope unnecessarily. The core value is event recording + Kafka publishing. Admin query API can be a separate feature later.

→ **Decision**: UC-003 is OUT OF SCOPE for this iteration. Focus on UC-001 (success event), UC-002 (failed event), UC-004 (Kafka publish via existing OutboxPoller).

## Open Questions Resolution

### OQ-1: MFA verify flow — emit UserLoggedInEvent in MfaService or create separate event?

**Decision: Emit `UserLoggedInEvent` with `loginSource=MFA_COMPLETED` inside MfaController/MfaService after successful MFA verification.**

**Reasoning**:
1. From the user's perspective, MFA verification IS the login completion — this is when they gain access
2. Creating a separate `MfaVerifiedEvent` fragments the login audit trail — downstream consumers would need to correlate two event types to understand "user logged in"
3. The `loginSource` field distinguishes direct logins (`DIRECT`) from MFA-verified logins (`MFA_COMPLETED`)
4. However, `MfaService.verifyMfa()` currently lacks `LoginCommand` context (IP, device, user-agent) — we need to pass these through

**Implementation approach**:
```
MfaController.verifyMfa() 
  → Extract IP, User-Agent, Device-Fingerprint from HttpServletRequest
  → Pass to MfaService.verifyMfa() as additional parameters
  → After successful MFA verification, inject EventService and call record()
  → UserLoggedInEvent with loginSource=MFA_COMPLETED
```

**Trade-off**: MfaService gets 2-3 new parameters + EventService dependency. Acceptable because MFA-completed login IS a login event.

**Alternative rejected**: Emitting only in `LoginHandler` — impossible because LoginHandler returns MfaRequired early and never reaches the event recording point for MFA flows.

### OQ-2: Failed login event aggregateId for non-existent users

**Decision: Use `aggregateId = 0` as a sentinel value for unknown/non-existent users.**

**Reasoning**:
1. `aggregateId = 0` is semantically clear — it means "system-level event, no specific aggregate"
2. `hash(username)` is problematic — it creates pseudo-aggregate IDs that collide with real user IDs (hash could produce any Long value)
3. `aggregateId = -1` violates the BIGINT unsigned convention used throughout the project (all IDs are positive)
4. The `event_store` table uses `BIGINT` for aggregate_id — 0 is a valid sentinel that won't conflict with Snowflake IDs (which start at large positive numbers)
5. Failed login events with `aggregateId = 0` can still be queried by `event_type = 'iam.user.login_failed'` and filtered by `username` in the JSONB payload

**Kafka partition key**: For unknown users, use `username.hashCode().absoluteValue.toString()` as partition key to ensure events for the same username end up on the same partition (ordering guarantee per username).

### OQ-3: Backward compatibility for eve_COMPL.) │       │
│                                      └──────────────┘       │
│                                                              │
│  Path 3: Failed Login                                        │
│  ┌────────────┐   ┌──────────────┐   ┌──────────────┐       │
│  │ LoginCmd   │──▸│ LoginHandler │──▸│ LoginEvent   │       │
│  │            │   │ (catch block) │   │ Recorder     │       │
│  └────────────┘   │              │   │ (REQUIRES_NEW)│       │
│                   │ throw excep. │   │ UserLoginFail│       │
│                   └──────────────┘   └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

**Pros**:
- Consistent with proven RegisterHandler pattern
- Minimal new infrastructure — reuses EventService, EventStorePort, OutboxPort, OutboxPoller
- Clear separation: success events in same TX, failed events in new TX
- MFA-completed logins captured correctly with full context
- `LoginEventRecorder` helper keeps LoginHandler clean (doesn't grow past 13 params)

**Cons**:
- MfaService/MfaController needs additional parameters (IP, user-agent, device context)
- `LoginEventRecorder` adds a new component (small overhead)
- REQUIRES_NEW for failed events means a separate DB connection from the pool

**Estimated effort**: 2-3 developer-days

### Approach B: Event-Only in LoginHandler, Skip MFA Events

**Description**: Only record events for direct (non-MFA) logins in LoginHandler. MFA-completed logins would NOT have events until a future iteration.

**Pros**:
- Simpler — no changes to MfaService/MfaController
- Faster implementation (1-2 days)

**Cons**:
- **Incomplete audit trail** — MFA-verified logins are arguably the MOST important logins to track (they indicate high-security users)
- Downstream consumers get an incomplete view of login activity
- Technical debt — MFA event recording deferred to unknown future

**Rejected because**: Incomplete audit trail is unacceptable for a security-critical feature. MFA-enabled users are often admin/privileged accounts — their login events are MORE important than regular user logins.

### Approach C: Async Event Recording via Spring Events

**Description**: Instead of calling `EventService.record()` directly in LoginHandler, publish a Spring ApplicationEvent and let an `@TransactionalEventListener(phase = AFTER_COMMIT)` handler record to event store + outbox.

**Pros**:
- Decouples event recording from login flow
- Failed event recording doesn't affect login response
- Works naturally with Spring's TX event phases

**Cons**:
- **Breaks the transactional guarantee** — event store + outbox would be in a separate TX from login, causing inconsistency on crash
- Contradicts the Transactional Outbox pattern (the whole point is same-TX guarantee)
- Spring ApplicationEvent is fire-and-forget — event loss on app crash between login TX commit and event listener execution
- RegisterHandler already uses direct `EventService.record()` — inconsistency

**Rejected because**: Transactional guarantee is non-negotiable. The point of the Outbox pattern is atomic event recording within the business transaction.

## Selected Direction

**Approach A: Mirror RegisterHandler Pattern — EventService.record() with MFA Post-Verification Hook**

**Reasoning**:
1. **Proven pattern** — RegisterHandler has been in production with the exact same `EventService.record()` integration. Copy-paste level of confidence.
2. **Complete audit trail** — both direct logins and MFA-completed logins are captured as `UserLoggedInEvent` with different `loginSource` values
3. **Transactional guarantee** — success events in same TX (guaranteed by `@Transactional`), failed events in REQUIRES_NEW (guaranteed by Spring TX propagation)
4. **Minimal scope** — no new frameworks, no new tables, no new infrastructure. Just: new event classes + handler modification + helper service
5. **Existing infra** — EventService, EventStorePort, OutboxPort, OutboxPoller, EventEnvelope all work as-is

## Pre-classifications (preliminary)

- **Feature type**: EXTEND (modifying existing LoginHandler + MfaService to add event recording)
- **Flow type**: Command (LoginCommand → LoginHandler → domain events)
- **Affected modules**:
  - `auth.domain.event` — new `UserLoggedInEvent.kt`, `UserLoginFailedEvent.kt`, `LoginFailureReason.kt`
  - `auth.application.command` — modify `LoginHandler.kt` (inject EventService, record events), modify `LoginCommand.kt` (add correlationId), modify `AuthDomainEvents.kt` (remove old UserLoggedInEvent)
  - `auth.application.event` — new `LoginEventRecorder.kt` (helper with REQUIRES_NEW for failed events)
  - `auth.application` — modify `MfaService.kt` (inject EventService, record MFA-completed login event)
  - `auth.adapter.in.web` — modify `CqrsAuthController.kt` (pass correlationId to LoginCommand), modify `MfaController.kt` (pass context to MfaService)

## Codebase Investigation Findings

### Existing Event Recording Pattern (RegisterHandler — lines 81-101)
```kotlin
eventService.record(
    aggregateType = "User",
    aggregateId = savedUser.id.value,
    event = UserRegisteredEvent(
        userId = savedUser.id.value,
        username = savedUser.username,
        email = command.email,
        fullName = command.fullName,
        phone = command.phone,
        domainCode = command.domainCode,
        domainId = domain.id,
        status = "ACTIVE",
        registrationSource = determineRegistrationSource(command),
        ipAddress = command.ipAddress,
        userAgent = command.userAgent
    ),
    topic = "iam.user.registered",
    partitionKey = savedUser.id.value.toString(),
    correlationId = command.correlationId
)
```
→ Follow this EXACT pattern for LoginHandler.

### LoginHandler Current Flow (critical path analysis)
```
1. findByUsernameAndActive(username) → User or throw
2. Check account lock → throw if locked
3. CAPTCHA check → throw if failed
4. Validate password → throw if invalid
5. Reset failed logins
6. Password expiry check → throw if expired
7. MFA checkpoint → return MfaRequired if needed
8. Determine active domain
9. Load roles for session policy
10. Enforce session policy
11. Generate tokens
12. Record login session    ← loginSession has device context
13. Anonymous session promotion
14. Return LoginResult.Success
```
→ Event recording should happen between step 12 and 13 (after loginSession recorded, before return).

### MfaService.verifyMfa() Current Context
```kotlin
fun verifyMfa(
    mfaToken: String,
    code: String,
    trustDevice: Boolean = false,
    deviceHash: String? = null,
    authResponseBuilder: (Long) -> AuthResponse
): AuthResponse
```
→ No IP/user-agent/device context parameters. Need to add:
- `ipAddress: String?`
- `userAgent: String?`
- `deviceFingerprint: String?`
→ Also need to inject `EventService` + `LoginSessionService` into `MfaService` to record login session + event after MFA verification.

### Key Files Modified

| File | Change | Risk |
|------|--------|------|
| `LoginHandler.kt` | Inject EventService, capture loginSession return, add event recording | LOW — follows RegisterHandler pattern |
| `LoginCommand.kt` | Add `correlationId: String?` field | LOW — additive, optional |
| `AuthDomainEvents.kt` | Remove dead `UserLoggedInEvent` | LOW — never used |
| `CqrsAuthController.kt` | Pass `X-Correlation-ID` header to LoginCommand | LOW — additive |
| `MfaService.kt` | Add IP/UA/deviceFP params to verifyMfa(), inject EventService + LoginSessionService, record event after MFA success | MEDIUM — modifies MFA flow |
| `MfaController.kt` | Extract HttpServletRequest context, pass to MfaService | LOW — additive |
| New: `UserLoggedInEvent.kt` | Create enriched domain event | LOW — new file |
| New: `UserLoginFailedEvent.kt` | Create failed login domain event | LOW — new file |
| New: `LoginEventRecorder.kt` | Helper for event recording (REQUIRES_NEW for failures) | LOW — new file |

### Transaction Boundary Analysis

```
┌─ LoginHandler @Transactional ──────────────────────────────┐
│                                                             │
│  SUCCESS PATH:                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 1. userPort.save(user)        — same TX              │   │
│  │ 2. loginSessionService.recordLogin() — same TX       │   │
│  │ 3. eventService.record()      — same TX (2 INSERTs)  │   │
│  │ 4. return LoginResult.Success                        │   │
│  │                                                      │   │
│  │ All 3 operations commit together or rollback together│   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  FAILED PATH:                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 1. loginEventRecorder.recordFailed()  ← REQUIRES_NEW│   │
│  │    (separate TX — commits independently)             │   │
│  │ 2. throw InvalidCredentialsException()               │   │
│  │    (main TX rolls back — event survives)             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|---|
| LoginHandler grows too large (13+ params) | LOW | LOW | Extract `LoginEventRecorder` helper to encapsulate event logic |
| MfaService.verifyMfa() signature change breaks callers | LOW | MEDIUM | Only 3 callers: MfaController (2 calls), SsoController (1 call). All controller-level — easy to update |
| REQUIRES_NEW TX for failed events uses extra DB connection | LOW | LOW | Failed logins are less frequent than successful ones. Connection pool can handle it. |
| Event store performance under high login frequency | LOW | MEDIUM | Event store append is O(1). Login events are lower volume than reads. OutboxPoller handles batching. |
| Missing loginSession context for MFA-completed logins | MEDIUM | MEDIUM | MfaService needs to call LoginSessionService.recordLogin() after MFA success (currently missing) |

## Design Decisions

### DD-001: LoginEventRecorder Helper Service
Create a `LoginEventRecorder` @Component that encapsulates:
- `recordSuccessfulLogin(event, aggregateId, correlationId)` — delegates to `EventService.record()` (participates in caller's TX)
- `recordFailedLogin(event, aggregateId, correlationId)` — `@Transactional(propagation = REQUIRES_NEW)` (independent TX)

This keeps LoginHandler clean and provides a reusable recording service for MfaService.

### DD-002: MFA Login Session + Event Recording
After MFA verification succeeds in `MfaService.verifyMfa()`:
1. Call `LoginSessionService.recordLogin()` to create session record
2. Call `EventService.record()` with `UserLoggedInEvent(loginSource = "MFA_COMPLETED")`
3. Both in the same `@Transactional` boundary of `verifyMfa()`

This fixes the existing gap where MFA-verified logins don't have session records.

### DD-003: Correlation ID Strategy
- `LoginCommand.correlationId` — extracted from `X-Correlation-ID` header by CqrsAuthController
- If header not present, `EventService.record()` auto-generates UUID (existing behavior in EventService)
- For MFA flows, the original correlation ID from login attempt is NOT available (MFA token doesn't carry it) — generate new UUID

### DD-004: Event Field Enrichment from LoginSession
The `loginSessionService.recordLogin()` return value (currently ignored by LoginHandler) must be captured to extract:
- `isNewDevice` — for anomaly detection
- `deviceType`, `browserName`, `osName` — for analytics
- `id` (session ID) — for cross-referencing

### DD-005: LoginSource Enum Values
```
DIRECT           — Standard login, no MFA required
MFA_COMPLETED    — Login after MFA verification
SSO              — SSO/OAuth login (future)  
ANONYMOUS_PROMOTION — Login from anonymous session promotion
TOKEN_REFRESH    — Login via refresh token (future)
```

## Open Questions for Design Phase

- [RESOLVED] OQ-1: MFA flow → Emit UserLoggedInEvent in MfaService with loginSource=MFA_COMPLETED
- [RESOLVED] OQ-2: Failed login aggregateId → Use 0 as sentinel for unknown users
- [RESOLVED] OQ-3: Backward compatibility → Not needed, old event was never published
- [OPEN] Should UC-003 (event query API) be included? → Deferred to separate feature
- [OPEN] Should MfaService also record a `UserLoginFailedEvent` when MFA code is invalid? → Recommend yes (with `failureReason = MFA_FAILED`), but scope decision for openspec
- [OPEN] Should `SsoController` also record `UserLoggedInEvent` with `loginSource=SSO`? → Out of scope for this feature, but the pattern enables it

## Open Questions for URD Analysis

- No formal URD exists — this feature is idea-driven
- The research artifacts + pre_openspec provide sufficient context
- No URD-specific questions remain
