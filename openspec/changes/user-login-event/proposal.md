# Proposal: user-login-event

> **Change**: user-login-event | **Type**: EXTEND | **Flow**: Command
> **Direction**: Mirror-Pattern with Centralized Failure Capture (from brainstorm — Approach 1)
> **Previous Iteration**: N/A (new feature — no prior iteration)
> **_Generated**: 2025-08-22_

## Changes

- **auth.domain.event [NEW]**: `UserLoggedInEvent.kt` — enriched domain event (14 fields) with full login context: userId, username, domainCode, domainId, loginMethod, mfaBypassed, mfaMethod, isNewDevice, ipAddress, userAgent, deviceFingerprint, sessionPromotionStatus, loggedInAt. Implements `DomainEvent`. eventType = `iam.user.logged_in`. (FR-001)
- **auth.domain.event [NEW]**: `UserLoginFailedEvent.kt` — failure domain event (7 fields): usernameAttempted, userId (nullable), failureReason (enum), ipAddress, userAgent, deviceFingerprint, failedAt. Implements `DomainEvent`. eventType = `iam.user.login_failed`. (FR-002)
- **auth.domain.event [NEW]**: `LoginFailureReason.kt` — enum with 9 values: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, CAPTCHA_REQUIRED, CAPTCHA_FAILED, PASSWORD_EXPIRED, RATE_LIMITED, MFA_REQUIRED, UNKNOWN. Maps 1:1 with LoginHandler exceptions. (FR-003)
- **auth.application.event [NEW]**: `LoginEventRecorder.kt` — helper @Component mirroring `TokenEventRecorder`. Injects `EventService`. Two methods: `recordLoginSuccess(event, userId, correlationId)` and `recordLoginFailure(event, correlationId)`. Fire-and-forget error handling. (FR-004, FR-010, FR-013, FR-014)
- **auth.application.command [MODIFY]**: `LoginHandler.kt` — inject `LoginEventRecorder` (13th constructor param). Add centralized `try-catch(AuthException)` wrapping `handle()` body for failure event recording. Add `recordLoginSuccess()` call after session recording + session promotion. Capture `LoginSessionEntity` return value for `isNewDevice`. Add private `mapToFailureReason()` function. (FR-005, FR-006)
- **auth.application.command [MODIFY]**: `LoginCommand.kt` — add optional `correlationId: String? = null` field for end-to-end tracing. (FR-014)
- **auth.application.command [MODIFY]**: `AuthDomainEvents.kt` — remove old `UserLoggedInEvent` class (2 fields, eventType = `USER_LOGGED_IN`). Keep `SessionRevokedEvent`. (FR-008)
- **auth.adapter.in.web [MODIFY]**: `CqrsAuthController.kt` — extract `X-Correlation-ID` header and pass to `LoginCommand.correlationId`. (FR-014)

**Total**: 3 new files (domain events + recorder), 4 modified files (LoginHandler, LoginCommand, AuthDomainEvents, CqrsAuthController), 0 unchanged infrastructure files (EventService, OutboxPoller, EventEnvelope reused as-is).

---

## 1. Executive Summary

Nâng cấp hệ thống sự kiện đăng nhập trong auth-service lên production-grade. Hiện tại `LoginHandler` KHÔNG persist bất kỳ domain event nào vào event store cho login actions — chỉ có minimal 2-field `UserLoggedInEvent` qua Spring `ApplicationEventPublisher` (in-process, không persist, không relay Kafka). Feature này tận dụng existing event sourcing infrastructure (đã proven với `UserRegisteredEvent`, `TokenIssuedEvent`, `TokenRevokedEvent`, `TokenValidationFailedEvent`) để add enriched login success/failure events flowing through event store → outbox → Kafka pipeline.

### Business Value
- **Audit Trail Completeness**: Close observability gap — login events persisted to event store + relayed to Kafka for downstream consumers (SIEM, analytics, admin-service)
- **Security Monitoring**: `UserLoginFailedEvent` with failure reason classification enables brute-force detection, geographic anomaly detection, CAPTCHA bypass monitoring
- **Compliance**: Immutable audit trail cho login actions — required cho SOC 2, ISO 27001
- **Pattern Consistency**: Follow proven architecture — LoginEventRecorder mirrors TokenEventRecorder (production-proven)

### Key Metrics
| Metric | Value |
|--------|-------|
| New files | 3 (domain events + recorder) |
| Modified files | 4 (LoginHandler, LoginCommand, AuthDomainEvents, CqrsAuthController) |
| FRs | 14 (URD: 11, Enriched: 3) |
| New Kafka topics | 2 (iam.user.logged_in, iam.user.login_failed) |
| Risk | LOW (follows proven pattern, fire-and-forget isolation) |
| Estimated effort | 2-3 developer-days |

---

## 2. Problem Statement

`LoginHandler` hiện tại có 3 observability gaps:

1. **No persistent login events**: `UserLoggedInEvent` trong `AuthDomainEvents.kt` chỉ có 2 fields (`userId`, `domainCode`) với eventType `USER_LOGGED_IN` — KHÔNG follow dot-notation convention, KHÔNG persist vào event store, KHÔNG relay qua Kafka. Chỉ publish in-process via `ApplicationEventPublisher` — và verified KHÔNG có `@EventListener` nào consume nó.

2. **No failure event recording**: Login failures (invalid credentials, account locked, CAPTCHA failed, password expired, rate limited) không được record dưới dạng domain events. Downstream consumers (SIEM, admin-service) không thể detect brute-force attacks hoặc anomalous login patterns.

3. **Inconsistent naming convention**: Old `UserLoggedInEvent` dùng `USER_LOGGED_IN` eventType — khác với convention `iam.{aggregate}.{action}` đã established bởi `UserRegisteredEvent` (`iam.user.registered`), `TokenIssuedEvent` (`iam.token.issued`), `TokenRevokedEvent` (`iam.token.revoked`), `TokenValidationFailedEvent` (`iam.token.validation-failed`).

---

## 3. Scope Definition

### In Scope — This Feature
| Item | Description | Priority |
|------|-------------|----------|
| FR-001 | Create `UserLoggedInEvent` enriched domain event (14 fields) | P0 |
| FR-002 | Create `UserLoginFailedEvent` domain event (7 fields) | P0 |
| FR-003 | Create `LoginFailureReason` enum (9 values) | P0 |
| FR-004 | Create `LoginEventRecorder` helper service | P0 |
| FR-005 | Record success event in LoginHandler | P0 |
| FR-006 | Record failure event in LoginHandler | P0 |
| FR-007 | Kafka topics for login events | P0 |
| FR-008 | Migrate UserLoggedInEvent naming convention | P1 |
| FR-009 | Schema versioning for login events | P1 |
| FR-010 | Fire-and-forget error handling | P0 |
| FR-011 | Complementary with TokenIssuedEvent | P1 |
| FR-012 | Idempotency via EventEnvelope UUID | P1 |
| FR-013 | Structured logging in LoginEventRecorder | P1 |
| FR-014 | Correlation ID threading | P1 |

### Out of Scope — Future Features
| Item | Reason |
|------|--------|
| MFA verify flow events | MFA checkpoint returns `MfaRequired` (not failure). MFA verify in `MfaService.verifyTotp()` — separate scope |
| Login event consumers | Downstream services (SIEM, admin-service) consume from Kafka topics independently |
| Event store query API for login events | `EventStoreController` already supports generic `GET /api/internal/events/{aggregateType}/{aggregateId}` |
| New database tables | Reuse existing `event_store` + `event_outbox` tables |

---

## 4. Architecture Decision

### Selected: Approach 1 — Full Mirror Pattern with Centralized Failure Capture (from brainstorm)

**Rationale** (score 9/10):
1. **Pattern consistency** — follows exact architecture of `TokenEventRecorder` + `TokenIssuedEvent` (proven production pattern)
2. **Centralized failure capture** — single `catch (e: AuthException)` wrapping `handle()` body ensures no failure path missed
3. **MFA safety** — MFA checkpoint returns `LoginResult.MfaRequired` (early return, NOT exception) — won't trigger catch block
4. **Fire-and-forget proven** — `LoginEventRecorder` catches ALL exceptions, logs WARN, never blocks authentication flow
5. **Minimal code change** — 3 new files, 4 modified files. No new DB tables, no new endpoints

### Rejected Alternatives
- **Approach 2** (Embedded Recording — inject EventService directly): Score 5/10 — violates established recorder pattern, duplicates fire-and-forget logic, inconsistent
- **Approach 3** (AOP-based Recording): Score 4/10 — hidden magic, hard to debug, context access problems for enriched payloads

---

## 5. Impact Summary

### Production Files Modified (🔴 Direct)
| File | Action | Scope |
|------|--------|-------|
| `auth/domain/event/UserLoggedInEvent.kt` | [NEW] | FR-001 |
| `auth/domain/event/UserLoginFailedEvent.kt` | [NEW] | FR-002 |
| `auth/domain/event/LoginFailureReason.kt` | [NEW] | FR-003 |
| `auth/application/event/LoginEventRecorder.kt` | [NEW] | FR-004, FR-010, FR-013, FR-014 |
| `auth/application/command/LoginHandler.kt` | [MODIFY] | FR-005, FR-006 |
| `auth/application/command/LoginCommand.kt` | [MODIFY] | FR-014 |
| `auth/application/command/AuthDomainEvents.kt` | [MODIFY] | FR-008 |

### Minor Modifications (🟡)
| File | Action | Scope |
|------|--------|-------|
| `auth/adapter/in/web/CqrsAuthController.kt` | [MODIFY] | FR-014 (extract X-Correlation-ID header) |

### Shared Utilities (🟢 — read-only usage, no changes)
| Component | Usage | Change |
|-----------|-------|--------|
| `EventService.record()` | Event persistence + outbox | None |
| `OutboxPoller` | Kafka relay | None (topic-driven by event data) |
| `EventEnvelope<T>` | Event wrapping | None (schemaVersion=1) |
| `DomainEvent` interface | Implemented by new events | None |

---

## 6. Risk Assessment

| # | Risk | Severity | Probability | Mitigation |
|---|------|----------|-------------|------------|
| 1 | Event recording failure blocks login | 🟢 Low | LOW | LoginEventRecorder catches ALL exceptions (proven TokenEventRecorder pattern) |
| 2 | LoginHandler 13th constructor dependency | 🟡 Medium | LOW | Acceptable; LoginEventRecorder is thin wrapper, doesn't add logic complexity |
| 3 | Old UserLoggedInEvent removal breaks consumers | 🟢 Low | LOW | Verified zero usages in codebase — safe hard break |
| 4 | correlationId missing in LoginCommand | 🟢 Low | LOW | Additive data class change, default = null for backward compatibility |
| 5 | MFA flow emits incomplete event | 🟢 Low | LOW | MFA returns early (not exception) — no event emitted for partial login |

---

## 7. Traceability

| FR-ID | Proposal Section | SRS Section | Design Section | Task |
|-------|-----------------|-------------|----------------|------|
| FR-001 | Changes #1 | §3.1 | §2.1 | T1 |
| FR-002 | Changes #2 | §3.2 | §2.2 | T2 |
| FR-003 | Changes #3 | §3.3 | §2.3 | T3 |
| FR-004 | Changes #4 | §3.4 | §2.4 | T4 |
| FR-005 | Changes #5 | §3.5 | §3.1 | T5 |
| FR-006 | Changes #5 | §3.6 | §3.2 | T5 |
| FR-007 | Changes #4 | §3.7 | §2.4 | T4 |
| FR-008 | Changes #7 | §3.8 | §4.1 | T7 |
| FR-009 | Changes #1-2 | §3.9 | §2.1-2.2 | T1-T2 |
| FR-010 | Changes #4 | §3.10 | §2.4 | T4 |
| FR-011 | Scope note | §3.11 | §5.1 | Documentation |
| FR-012 | Scope note | §3.12 | §5.2 | Documentation |
| FR-013 | Changes #4 | §3.13 | §2.4 | T4 |
| FR-014 | Changes #6-8 | §3.14 | §3.3 | T6 |
