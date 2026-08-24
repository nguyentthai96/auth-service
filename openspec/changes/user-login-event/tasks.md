<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Command" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: user-login-event

_Generated: 2025-08-22_
_Profile: Command | N/A | EXTEND_
_Direction: Mirror-Pattern with Centralized Failure Capture (from brainstorm)_
_Scope: 3 new files (domain events + recorder), 4 modified files (LoginHandler, LoginCommand, AuthDomainEvents, CqrsAuthController)_

---

## Changes

[EXTEND] Adding production-grade login event recording to existing LoginHandler flow. Follows proven pattern of TokenEventRecorder + TokenIssuedEvent.

**New Domain Events (3 files)**: `UserLoggedInEvent.kt` — enriched 14-field event; `UserLoginFailedEvent.kt` — failure event with reason enum; `LoginFailureReason.kt` — 9-value enum.
**New Helper Service (1 file)**: `LoginEventRecorder.kt` — @Component mirroring TokenEventRecorder. 2 methods (recordLoginSuccess, recordLoginFailure). Fire-and-forget error handling.
**Modified Files (4)**: `LoginHandler.kt` — inject LoginEventRecorder, centralized try-catch, success recording, capture loginSession return. `LoginCommand.kt` — add correlationId. `AuthDomainEvents.kt` — remove old UserLoggedInEvent. `CqrsAuthController.kt` — extract X-Correlation-ID header.

**Total**: 4 new files, 4 modified files, 0 infrastructure changes.

---

## Task Summary

| # | Task | Action | File | FR |
|---|------|--------|------|----|
| 1 | UserLoggedInEvent | NEW | `UserLoggedInEvent.kt` | FR-001, FR-009, FR-011 |
| 2 | UserLoginFailedEvent | NEW | `UserLoginFailedEvent.kt` | FR-002, FR-009 |
| 3 | LoginFailureReason | NEW | `LoginFailureReason.kt` | FR-003 |
| 4 | LoginEventRecorder | NEW | `LoginEventRecorder.kt` | FR-004, FR-007, FR-010, FR-012, FR-013, FR-014 |
| 5 | LoginHandler — integrate event recording | MODIFY | `LoginHandler.kt` | FR-005, FR-006 |
| 6 | LoginCommand + CqrsAuthController — correlationId | MODIFY | `LoginCommand.kt`, `CqrsAuthController.kt` | FR-014 |
| 7 | AuthDomainEvents — remove old UserLoggedInEvent | MODIFY | `AuthDomainEvents.kt` | FR-008 |

---

## Phase 1: Domain Events (no dependencies, leaf nodes)

- [x] **Task 1: UserLoggedInEvent — enriched domain event**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoggedInEvent.kt` | Action: [NEW]
  - Base: `DomainEvent` interface from `com.ntt.authservice.auth.application.port.out.EventPublisher`
  - FR: FR-001 — enriched payload with full login context
  - Pattern: `UserRegisteredEvent.kt` — same package, same interface, data class with eventType override
  - Dependencies: `DomainEvent` interface, `java.time.Instant`
  - Detail:
    - Data class with 13 fields + `loggedInAt: Instant = Instant.now()`
    - Fields: userId (Long), username (String), domainCode (String), domainId (Long?), loginMethod (String = "PASSWORD"), mfaBypassed (Boolean), mfaMethod (String?), isNewDevice (Boolean), ipAddress (String?), userAgent (String?), deviceFingerprint (String?), sessionPromotionStatus (String?), loggedInAt (Instant)
    - `override val eventType: String = "iam.user.logged_in"` — dot-notation convention
    - Package: `com.ntt.authservice.auth.domain.event`

- [x] **Task 2: UserLoginFailedEvent — failure domain event**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt` | Action: [NEW]
  - Base: `DomainEvent` interface from `com.ntt.authservice.auth.application.port.out.EventPublisher`
  - FR: FR-002 — failure event with reason classification
  - Pattern: `TokenValidationFailedEvent.kt` — failure event with reason enum, same package
  - Dependencies: `DomainEvent` interface, `LoginFailureReason` enum, `java.time.Instant`
  - Detail:
    - Data class with 6 fields + `failedAt: Instant = Instant.now()`
    - Fields: usernameAttempted (String), userId (Long? — null when user not found), failureReason (LoginFailureReason), ipAddress (String?), userAgent (String?), deviceFingerprint (String?)
    - `override val eventType: String = "iam.user.login_failed"`

- [x] **Task 3: LoginFailureReason — enum**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt` | Action: [NEW]
  - FR: FR-003 — failure reason classification mapped to LoginHandler exceptions
  - Pattern: `ValidationFailureReason.kt` — enum with KDoc per value
  - Detail:
    - 9 values: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED (reserved), CAPTCHA_REQUIRED, CAPTCHA_FAILED, PASSWORD_EXPIRED, RATE_LIMITED, MFA_REQUIRED (reserved), UNKNOWN (fallback)
    - Each value has KDoc comment explaining when used
    - Package: `com.ntt.authservice.auth.domain.event`

---

## Phase 2: Helper Service (depends on Phase 1)

- [x] **Task 4: LoginEventRecorder — helper service**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt` | Action: [NEW]
  - FR: FR-004 — helper service; FR-010 — fire-and-forget; FR-013 — structured logging; FR-014 — correlationId threading
  - Pattern: `TokenEventRecorder.kt` — EXACT mirror: @Component, inject EventService, try/catch all, log.debug success, log.warn failure
  - Dependencies: `EventService` (injected via constructor), `UserLoggedInEvent`, `UserLoginFailedEvent`, `org.slf4j.LoggerFactory`
  - Detail:
    - `@Component` annotation
    - Constructor: `private val eventService: EventService`
    - Method 1: `recordLoginSuccess(event: UserLoggedInEvent, userId: Long, correlationId: String?)`
      - `eventService.record(aggregateType="User", aggregateId=userId, event=event, topic="iam.user.logged_in", partitionKey=userId.toString(), correlationId=correlationId)`
      - `log.debug("Login success event recorded: userId={}, domain={}, isNewDevice={}, correlationId={}", ...)`
      - Catch: `log.warn("Failed to record login success event: userId={}, error={}", ...)`
    - Method 2: `recordLoginFailure(event: UserLoginFailedEvent, correlationId: String?)`
      - `val aggregateId = event.userId ?: 0L`
      - `eventService.record(aggregateType="User", aggregateId=aggregateId, event=event, topic="iam.user.login_failed", partitionKey=aggregateId.toString(), correlationId=correlationId)`
      - `log.debug("Login failure event recorded: username={}, reason={}, userId={}, correlationId={}", ...)`
      - Catch: `log.warn("Failed to record login failure event: username={}, reason={}, error={}", ...)`

---

## Phase 3: Integration (depends on Phase 2)

- [x] **Task 5: LoginHandler — integrate event recording**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - FR: FR-005 — record success event; FR-006 — record failure event
  - Pattern: LoginHandler is CQRS CommandHandler with @Transactional
  - Dependencies: `LoginEventRecorder` (NEW inject), `UserLoggedInEvent`, `UserLoginFailedEvent`, `LoginFailureReason`, existing auth exceptions
  - Detail — Constructor:
    - Add parameter: `private val loginEventRecorder: LoginEventRecorder` (13th param, after sessionPromotionService)
  - Detail — handle() body restructure:
    - Add `var resolvedUser: User? = null` before try block
    - Wrap entire handle() body in `try { ... } catch (e: AuthException) { ... }`
    - After `val user = userPort.findByUsernameAndActive(...)` succeeds: `resolvedUser = user`
    - **Capture login session return**: Change `loginSessionService.recordLogin(...)` to `val loginSession = loginSessionService.recordLogin(...)` (was discarded)
    - **Success event** (after session promotion, before return): Call `loginEventRecorder.recordLoginSuccess(UserLoggedInEvent(...), user.id.value, command.correlationId)`
      - `mfaBypassed = user.mfaMethod != null && !user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)`
      - `isNewDevice = loginSession.isNewDevice`
      - `sessionPromotionStatus = promotionResult?.status?.name`
    - **Failure catch**: `catch (e: AuthException) { loginEventRecorder.recordLoginFailure(UserLoginFailedEvent(command.username, resolvedUser?.id?.value, mapToFailureReason(e), command.ipAddress, command.userAgent, command.deviceFingerprint), command.correlationId); throw e }`
  - Detail — new private function:
    - `private fun mapToFailureReason(exception: AuthException): LoginFailureReason`
    - `when` expression mapping: InvalidCredentialsException→INVALID_CREDENTIALS, AccountLockedException→ACCOUNT_LOCKED, CaptchaRequiredException→CAPTCHA_REQUIRED, CaptchaFailedException→CAPTCHA_FAILED, PasswordExpiredException→PASSWORD_EXPIRED, RateLimitExceededException→RATE_LIMITED, else→UNKNOWN
  - Detail — imports to add:
    - `import com.ntt.authservice.auth.application.event.LoginEventRecorder`
    - `import com.ntt.authservice.auth.domain.event.UserLoggedInEvent`
    - `import com.ntt.authservice.auth.domain.event.UserLoginFailedEvent`
    - `import com.ntt.authservice.auth.domain.event.LoginFailureReason`
    - `import com.ntt.authservice.shared.exception.AuthException`

- [x] **Task 6: LoginCommand + CqrsAuthController — correlationId threading**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-014 — correlation ID threading for end-to-end tracing
  - Detail — LoginCommand.kt:
    - Add field: `val correlationId: String? = null` (after anonymousTokenJti, before closing parenthesis)
    - Additive change — default null, all existing callers unaffected
  - Detail — CqrsAuthController.kt login() method:
    - Before `LoginCommand(...)`: `val correlationId = httpRequest.getHeader("X-Correlation-ID") ?: httpRequest.getHeader("X-Request-ID")`
    - Add to LoginCommand construction: `correlationId = correlationId`

---

## Phase 4: Cleanup (independent of Phase 3)

- [x] **Task 7: AuthDomainEvents — remove old UserLoggedInEvent**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt` | Action: [MODIFY]
  - FR: FR-008 — migrate naming convention
  - Detail:
    - Remove `data class UserLoggedInEvent(val userId: Long, val domainCode: String) : DomainEvent` (L12-17)
    - Remove its `override val eventType: String = "USER_LOGGED_IN"`
    - Keep `SessionRevokedEvent` intact
    - Update file KDoc: "UserLoggedInEvent REMOVED — consolidated into com.ntt.authservice.auth.domain.event.UserLoggedInEvent (user-login-event feature)"
    - Verified: zero usages of old class in codebase (grep confirmed)

---

## FR Traceability

| FR-ID | Task | Status |
|-------|------|--------|
| FR-001 | T1 (UserLoggedInEvent) | Pending |
| FR-002 | T2 (UserLoginFailedEvent) | Pending |
| FR-003 | T3 (LoginFailureReason) | Pending |
| FR-004 | T4 (LoginEventRecorder) | Pending |
| FR-005 | T5 (LoginHandler — success path) | Pending |
| FR-006 | T5 (LoginHandler — failure path) | Pending |
| FR-007 | T4 (LoginEventRecorder — topics) | Pending |
| FR-008 | T7 (AuthDomainEvents cleanup) | Pending |
| FR-009 | T1, T2 (EventEnvelope wrapping — reuse) | Pending |
| FR-010 | T4 (LoginEventRecorder — fire-and-forget) | Pending |
| FR-011 | T1, T5 (complementary documentation — design) | Pending |
| FR-012 | T4 (EventEnvelope.id UUID — reuse) | Pending |
| FR-013 | T4 (LoginEventRecorder — structured logging) | Pending |
| FR-014 | T4, T5, T6 (correlationId threading) | Pending |

**Coverage**: 14/14 FRs mapped to tasks. No missing FRs.
