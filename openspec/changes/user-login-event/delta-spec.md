# Delta Spec: user-login-event

_Generated: 2025-08-22_
_Type: EXTEND_

---

## Summary

Added production-grade login event recording to existing LoginHandler flow. All changes are additive — no existing behavior removed or altered.

---

## Behavior Delta

### LoginHandler.handle()

| Aspect | Before | After |
|--------|--------|-------|
| Success event | No domain event recorded | `UserLoggedInEvent` (13 fields) recorded via `LoginEventRecorder` → EventService → event store + outbox → Kafka |
| Failure event | No domain event recorded | `UserLoginFailedEvent` (6 fields) recorded via centralized `catch (e: AuthException)` → `LoginEventRecorder` |
| loginSessionService.recordLogin() return | Discarded | Captured as `val loginSession` — provides `isNewDevice` for event enrichment |
| Exception handling | Individual throws | Same throws wrapped in try-catch; original exceptions still propagated unchanged |
| Constructor params | 12 params | 13 params (+loginEventRecorder) |
| MFA checkpoint | Returns MfaRequired (early return) | Unchanged — early return before try-catch, NOT caught as failure |

### LoginCommand

| Aspect | Before | After |
|--------|--------|-------|
| Fields | 10 fields | 11 fields (+correlationId: String? = null) |
| Breaking change | N/A | None — default null, all existing callers unaffected |

### CqrsAuthController.login()

| Aspect | Before | After |
|--------|--------|-------|
| Correlation ID | Not extracted | Extracts `X-Correlation-ID` or `X-Request-ID` header, passes to LoginCommand |

### AuthDomainEvents.kt

| Aspect | Before | After |
|--------|--------|-------|
| UserLoggedInEvent | Minimal 2-field class (userId, domainCode), eventType="USER_LOGGED_IN" | REMOVED — replaced by enriched `auth.domain.event.UserLoggedInEvent` (13 fields, eventType="iam.user.logged_in") |
| SessionRevokedEvent | Present | Unchanged |

---

## New Kafka Topics

| Topic | Partition Key | Content |
|-------|---------------|---------|
| `iam.user.logged_in` | userId | EventEnvelope wrapping UserLoggedInEvent (13 fields) |
| `iam.user.login_failed` | userId (or 0 if unknown) | EventEnvelope wrapping UserLoginFailedEvent (6 fields) |

---

## Risk Assessment

- **Login flow integrity**: PRESERVED — LoginEventRecorder uses fire-and-forget pattern. EventService failure → logged WARN, login proceeds normally.
- **Transaction boundary**: Event recording happens within LoginHandler's existing `@Transactional` boundary via EventService → same TX as auth logic.
- **Backward compatibility**: All changes additive. Old UserLoggedInEvent had zero usages (verified via grep). LoginCommand default null for correlationId.
