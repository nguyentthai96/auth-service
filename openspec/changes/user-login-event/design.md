# Design: user-login-event

_Generated: 2025-08-22_
_Direction: Mirror-Pattern with Centralized Failure Capture (from brainstorm — Approach 1)_

---

## Locked Profile

| Field | Value |
|-------|-------|
| flow | Command |
| factory | N/A |
| feature_type | EXTEND |
| transaction_flow | Command — LoginHandler is CQRS write-side, records domain events via event store + outbox |

---

## 1. Architecture Overview

EXTEND iteration — adding event recording capability to existing LoginHandler flow. All new files follow existing patterns exactly. Key integration point: `EventService.record()` (already proven with `UserRegisteredEvent`, `TokenIssuedEvent`, `TokenRevokedEvent`, `TokenValidationFailedEvent`).

### Key Design Decisions (from brainstorm — Approach 1)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Recorder pattern | Separate `LoginEventRecorder` (NOT extend TokenEventRecorder) | SRP — login events ≠ token lifecycle events. TokenEventRecorder handles issuance/revocation/validation. Consistent with codebase philosophy. |
| Failure capture | Centralized try-catch wrapping handle() body | All LoginHandler exceptions extend `AuthException`. Single catch type. MFA checkpoint is early return (not exception) — safe. Future exceptions automatically captured. |
| mapToFailureReason location | Private function in LoginHandler | Handler-specific mapping. Keeps recorder generic and reusable. |
| Event ordering | UserLoggedInEvent recorded AFTER session recording + promotion | Captures full context including `isNewDevice` and `sessionPromotionStatus`. |
| correlationId | Add to LoginCommand, extract from HTTP header | End-to-end tracing. Default = null → EventService auto-generates UUID. |
| Old UserLoggedInEvent | Hard break — remove from AuthDomainEvents.kt | Verified zero usages, zero listeners. Safe to delete. |
| MFA scope | Defer — no event for MFA-required checkpoint | MFA is partial login, not failure. Separate scope. |

---

## 2. New Components Design

### 2.1 UserLoggedInEvent (auth.domain.event)

**File**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoggedInEvent.kt`

```kotlin
package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

data class UserLoggedInEvent(
    val userId: Long,
    val username: String,
    val domainCode: String,
    val domainId: Long?,
    val loginMethod: String,           // "PASSWORD" for this scope
    val mfaBypassed: Boolean,          // true if MFA configured but bypassed (trusted device)
    val mfaMethod: String?,            // MFA method if configured, null otherwise
    val isNewDevice: Boolean,          // from LoginSessionService.recordLogin() return
    val ipAddress: String?,
    val userAgent: String?,
    val deviceFingerprint: String?,
    val sessionPromotionStatus: String?, // PromotionResult.status.name or null
    val loggedInAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.user.logged_in"
}
```

**Pattern reference**: `UserRegisteredEvent.kt` — same package, same DomainEvent interface, same enriched data class.

### 2.2 UserLoginFailedEvent (auth.domain.event)

**File**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt`

```kotlin
package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

data class UserLoginFailedEvent(
    val usernameAttempted: String,
    val userId: Long?,                  // null when user not found
    val failureReason: LoginFailureReason,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceFingerprint: String?,
    val failedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.user.login_failed"
}
```

**Pattern reference**: `TokenValidationFailedEvent.kt` — failure event with reason enum.

### 2.3 LoginFailureReason (auth.domain.event)

**File**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt`

```kotlin
package com.ntt.authservice.auth.domain.event

enum class LoginFailureReason {
    /** User not found or password mismatch. */
    INVALID_CREDENTIALS,
    /** Account is locked (temporary or permanent). */
    ACCOUNT_LOCKED,
    /** Account is disabled (reserved — currently filtered by findByUsernameAndActive). */
    ACCOUNT_DISABLED,
    /** CAPTCHA required but not provided. */
    CAPTCHA_REQUIRED,
    /** CAPTCHA verification failed. */
    CAPTCHA_FAILED,
    /** Password has expired. */
    PASSWORD_EXPIRED,
    /** Login rate limit exceeded. */
    RATE_LIMITED,
    /** MFA required (reserved — MFA checkpoint returns early, not exception). */
    MFA_REQUIRED,
    /** Fallback for any unexpected AuthException subclass. */
    UNKNOWN
}
```

**Pattern reference**: `ValidationFailureReason.kt` — enum classifying failures.

### 2.4 LoginEventRecorder (auth.application.event)

**File**: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt`

```kotlin
package com.ntt.authservice.auth.application.event

import com.ntt.authservice.auth.domain.event.UserLoggedInEvent
import com.ntt.authservice.auth.domain.event.UserLoginFailedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoginEventRecorder(
    private val eventService: EventService
) {
    private val log = LoggerFactory.getLogger(LoginEventRecorder::class.java)

    fun recordLoginSuccess(event: UserLoggedInEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.user.logged_in",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Login success event recorded: userId={}, domain={}, isNewDevice={}, correlationId={}",
                userId, event.domainCode, event.isNewDevice, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record login success event: userId={}, error={}",
                userId, e.message, e
            )
        }
    }

    fun recordLoginFailure(event: UserLoginFailedEvent, correlationId: String?) {
        try {
            val aggregateId = event.userId ?: 0L
            eventService.record(
                aggregateType = "User",
                aggregateId = aggregateId,
                event = event,
                topic = "iam.user.login_failed",
                partitionKey = aggregateId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Login failure event recorded: username={}, reason={}, userId={}, correlationId={}",
                event.usernameAttempted, event.failureReason, event.userId, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record login failure event: username={}, reason={}, error={}",
                event.usernameAttempted, event.failureReason, e.message, e
            )
        }
    }
}
```

**Exact mirror** of `TokenEventRecorder.kt` structure: @Component, inject EventService, 2 public methods, try/catch all exceptions, log.debug on success, log.warn on failure.

---

## 3. Modification Design

### 3.1 LoginHandler — Success Path (FR-005)

**File**: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`

**Constructor change**: Add `private val loginEventRecorder: LoginEventRecorder` (13th parameter).

**Success event recording** (after session recording, before return):

```kotlin
// Capture login session return value for isNewDevice
val loginSession = loginSessionService.recordLogin(
    userId = user.id.value,
    ipAddress = command.ipAddress ?: "unknown",
    userAgent = command.userAgent,
    deviceFingerprint = command.deviceFingerprint,
    refreshTokenId = null
)

// ... anonymous session promotion (existing) ...

// NEW: Record login success event (fire-and-forget)
loginEventRecorder.recordLoginSuccess(
    event = UserLoggedInEvent(
        userId = user.id.value,
        username = user.username,
        domainCode = domainCode,
        domainId = domain?.id,
        loginMethod = "PASSWORD",
        mfaBypassed = user.mfaMethod != null && !user.requiresMfa(
            command.trustedDeviceHash,
            securityProperties.mfa.trustedDeviceTtlDays
        ),
        mfaMethod = user.mfaMethod,
        isNewDevice = loginSession.isNewDevice,
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
        deviceFingerprint = command.deviceFingerprint,
        sessionPromotionStatus = promotionResult?.status?.name
    ),
    userId = user.id.value,
    correlationId = command.correlationId
)
```

**Key change**: Capture `LoginSessionEntity` return value from `loginSessionService.recordLogin()` (currently discarded) → provides `isNewDevice` flag.

### 3.2 LoginHandler — Failure Path (FR-006)

**Centralized try-catch wrapping handle() body**:

```kotlin
@Transactional
override fun handle(command: LoginCommand): LoginResult {
    var resolvedUser: com.ntt.authservice.auth.domain.model.User? = null
    try {
        val user = userPort.findByUsernameAndActive(command.username)
            ?: run { /* ... existing rate limit + throw InvalidCredentialsException */ }
        resolvedUser = user
        
        // ... all existing auth checks (unchanged) ...
        // ... MFA checkpoint (returns early, NOT caught) ...
        // ... token generation, session recording, promotion ...
        // ... success event recording (NEW — section 3.1) ...
        
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

**Design notes**:
- `resolvedUser` is `var` (nullable) — tracks whether user was resolved before exception. Used for `userId` in failure event.
- `mapToFailureReason()` is private in LoginHandler — handler-specific mapping. Keeps LoginEventRecorder generic.
- MFA checkpoint returns `LoginResult.MfaRequired` (early return, NOT exception) — correctly NOT caught.
- `RateLimitExceededException` from `loginRateLimitService.recordFailedAttempt()` is caught automatically.

### 3.3 LoginCommand — Add correlationId (FR-014)

**File**: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`

```kotlin
data class LoginCommand(
    val username: String,
    val password: String,
    val domainCode: String? = null,
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val deviceFingerprint: String? = null,
    val anonymousSessionId: String? = null,
    val anonymousTokenJti: String? = null,
    val correlationId: String? = null    // NEW — extracted from X-Correlation-ID header
) : Command<LoginResult>
```

### 3.4 CqrsAuthController — Extract Correlation ID (FR-014)

**File**: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`

**Change in `login()` method**: Extract `X-Correlation-ID` header and pass to LoginCommand:

```kotlin
val correlationId = httpRequest.getHeader("X-Correlation-ID")
    ?: httpRequest.getHeader("X-Request-ID")

val command = LoginCommand(
    // ... existing fields unchanged ...
    correlationId = correlationId  // NEW
)
```

---

## 4. Cleanup Design

### 4.1 AuthDomainEvents — Remove old UserLoggedInEvent (FR-008)

**File**: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`

**Remove**: `UserLoggedInEvent` data class (L12-17). eventType `USER_LOGGED_IN` replaced by new `iam.user.logged_in`.

**Keep**: `SessionRevokedEvent` data class — separate event, still in use.

**After**:
```kotlin
package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.DomainEvent

/**
 * Domain events published by command handlers.
 *
 * Note: UserLoggedInEvent REMOVED — consolidated into
 * com.ntt.authservice.auth.domain.event.UserLoggedInEvent (user-login-event feature).
 */

data class SessionRevokedEvent(
    val userId: Long,
    val revokedCount: Int
) : DomainEvent {
    override val eventType: String = "SESSIONS_REVOKED"
}
```

---

## 5. Documentation & Complementary Notes

### 5.1 Event Complementarity (FR-011)

| Aspect | UserLoggedInEvent | TokenIssuedEvent |
|--------|------------------|-----------------|
| **Focus** | Authentication context | Token context |
| **Fields** | userId, username, domain, loginMethod, MFA status, device info, session promotion | userId, JTI, roles, permissions, token expiry, issuance context |
| **Topic** | `iam.user.logged_in` | `iam.token.issued` |
| **When** | After full login success (step 12) | During token generation (step 9) |
| **Overlap** | userId, ipAddress (for correlation) | userId, ipAddress |

Both events emitted per successful login. Non-redundant — different semantic meaning for different consumers (security vs access control).

### 5.2 Event Ordering in LoginHandler.handle() (revised flow)

```
Step 1:  Validate user exists + active
Step 2:  Check account lock status
Step 3:  CAPTCHA check (if threshold exceeded)
Step 4:  Validate password
Step 5:  Reset failed login count + rate limit
Step 6:  Check password expiry
Step 7:  MFA checkpoint (may return MfaRequired — early exit, no event)
Step 8:  Load roles, enforce session policy
Step 9:  Generate tokens → TokenIssuedEvent (via TokenEventRecorder) ← existing
Step 10: Record login session → LoginSessionEntity (isNewDevice) ← capture return value
Step 11: Anonymous session promotion → PromotionResult ← existing
Step 12: Record UserLoggedInEvent (via LoginEventRecorder) ← NEW
Step 13: Return LoginResult.Success

On any AuthException (steps 1-8):
  → catch → record UserLoginFailedEvent → rethrow original ← NEW
```

---

## 6. Implementation Ordering

```
Phase 1: Domain Events (no dependencies, leaf nodes)
  └── T1: UserLoggedInEvent.kt [NEW]
  └── T2: UserLoginFailedEvent.kt [NEW]
  └── T3: LoginFailureReason.kt [NEW]

Phase 2: Helper Service (depends on Phase 1)
  └── T4: LoginEventRecorder.kt [NEW]

Phase 3: Integration (depends on Phase 2)
  └── T5: LoginHandler.kt [MODIFY] — inject recorder, try-catch, success call, capture session return
  └── T6: LoginCommand.kt [MODIFY] — add correlationId
  └── T6b: CqrsAuthController.kt [MODIFY] — extract correlation ID header

Phase 4: Cleanup (independent)
  └── T7: AuthDomainEvents.kt [MODIFY] — remove old UserLoggedInEvent
```

---

## 7. Concurrency Considerations

All changes are thread-safe:
1. **Domain event data classes**: Immutable Kotlin data classes — no concurrency concern
2. **LoginFailureReason enum**: Immutable — no concurrency concern
3. **LoginEventRecorder**: Stateless @Component (no mutable fields beyond logger) — thread-safe
4. **LoginHandler modifications**: `resolvedUser` is local variable (stack-scoped) — no shared state
5. **EventService.record()**: Operates within caller's @Transactional boundary — each request has isolated DB transaction

---

## 8. Test Strategy

### Unit Tests
| Target | Test File | TCs | Key Scenarios |
|--------|-----------|:---:|---------------|
| LoginEventRecorder | `LoginEventRecorderTest.kt` | ~6 | Success recording, failure recording, fire-and-forget (exception swallowed), correct topic/partitionKey/aggregateType, unknown user aggregateId=0L |
| LoginHandler (event recording) | `LoginHandlerTest.kt` [MODIFY] | +6 | Success event recorded after session, failure event recorded for each exception type, MFA returns early (no event), recording failure doesn't affect login result |

### Integration Tests
| Target | Test File | TCs | Key Scenarios |
|--------|-----------|:---:|---------------|
| Login → Event Store | `LoginEventIntegrationTest.kt` | ~4 | Successful login → event in event_store, failed login → event in event_store, event in outbox, EventEnvelope wrapping correct |
