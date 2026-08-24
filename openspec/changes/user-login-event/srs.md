# SRS: user-login-event

_Generated: 2025-08-22_
_Profile: Command | N/A (no factory) | EXTEND_

---

## 1. System Context

- **Feature Name**: User Login Event — Production-Grade Event Recording
- **Domain**: Authentication — Login/Authentication Events (Event Sourcing layer)
- **Flow**: Command (LoginHandler is CQRS write-side, mutates state via domain events)
- **Services**: auth-service (EXTEND)
- **Source**: Pre-OpenSpec (quality score 88/100) + Brainstorm (Approach 1 — Mirror-Pattern with Centralized Failure Capture)
- **Scope**: Add enriched login success/failure domain events to existing event sourcing pipeline. 3 new files, 4 modified files. No new DB tables, no new endpoints.

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Client (End User) | Gửi request đăng nhập qua REST API (`POST /api/auth/login`), trigger login command | CqrsAuthController → LoginHandler |
| Hệ thống (LoginHandler) | CQRS command handler — xác thực user, generate tokens, record domain events | LoginHandler → LoginEventRecorder → EventService |
| LoginEventRecorder | Helper service — wrap EventService.record() cho login events, fire-and-forget error isolation | LoginEventRecorder → EventService |
| EventService | Intermediary service — create EventEnvelope, persist event store + outbox trong cùng @Transactional | EventService → EventStorePort, OutboxPort |
| OutboxPoller | Async relay — poll event_outbox table, publish to Kafka topics | OutboxPoller → KafkaEventPublisher |
| Downstream Consumers | Consume login events từ Kafka cho audit trail, anomaly detection, compliance | Kafka topics → SIEM, Analytics, Admin Service |

---

## 3. Functional Requirements

### 3.1 FR-001: Tạo UserLoggedInEvent enriched domain event [URD]
- **Actor**: Hệ thống
- **Precondition**: Login thành công (password auth, non-MFA hoặc MFA bypassed via trusted device).
- **Action**: Hệ thống phải tạo `UserLoggedInEvent` data class trong package `auth.domain.event` implement `DomainEvent` interface với enriched payload:
  - `userId: Long` — authenticated user ID
  - `username: String` — username used for login
  - `domainCode: String` — active domain code
  - `domainId: Long?` — domain ID (nullable if domain not resolved)
  - `loginMethod: String` — always `"PASSWORD"` for this scope (future: SSO, MFA)
  - `mfaBypassed: Boolean` — true if user has MFA configured but bypassed via trusted device
  - `mfaMethod: String?` — MFA method configured (null if MFA not configured)
  - `isNewDevice: Boolean` — derived from `LoginSessionService.recordLogin()` return value
  - `ipAddress: String?` — client IP extracted by CqrsAuthController
  - `userAgent: String?` — User-Agent header
  - `deviceFingerprint: String?` — X-Device-Fingerprint header
  - `sessionPromotionStatus: String?` — anonymous session promotion result status (SUCCESS/FAILED/null)
  - `loggedInAt: Instant` — timestamp of successful login (default = `Instant.now()`)
- **Validation**: 
  - Class in `auth.domain.event` package
  - Implements `DomainEvent` interface
  - eventType = `iam.user.logged_in`
  - All required fields (userId, username, domainCode, loginMethod, isNewDevice, loggedInAt) non-null
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoggedInEvent.kt` [NEW]
- **Pattern Reference**: `UserRegisteredEvent.kt` — same package, same DomainEvent interface, same enriched data class pattern

### 3.2 FR-002: Tạo UserLoginFailedEvent domain event [URD]
- **Actor**: Hệ thống
- **Precondition**: Login thất bại (any `AuthException` thrown in LoginHandler.handle()).
- **Action**: Hệ thống phải tạo `UserLoginFailedEvent` data class trong package `auth.domain.event` implement `DomainEvent` interface:
  - `usernameAttempted: String` — username that was attempted
  - `userId: Long?` — nullable (null when user not found/not resolved before exception)
  - `failureReason: LoginFailureReason` — enum classifying the failure
  - `ipAddress: String?` — client IP
  - `userAgent: String?` — User-Agent header
  - `deviceFingerprint: String?` — X-Device-Fingerprint header
  - `failedAt: Instant` — timestamp (default = `Instant.now()`)
- **Validation**:
  - Class in `auth.domain.event` package
  - Implements `DomainEvent` interface
  - eventType = `iam.user.login_failed`
  - failureReason is valid enum value
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt` [NEW]
- **Pattern Reference**: `TokenValidationFailedEvent.kt` — failure event with reason enum

### 3.3 FR-003: Tạo LoginFailureReason enum [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `LoginFailureReason` enum class trong `auth.domain.event` package:
  - `INVALID_CREDENTIALS` — user not found OR password mismatch (`InvalidCredentialsException`)
  - `ACCOUNT_LOCKED` — account is locked (`AccountLockedException`)
  - `ACCOUNT_DISABLED` — reserved for future use (currently filtered by `findByUsernameAndActive`)
  - `CAPTCHA_REQUIRED` — CAPTCHA needed but not provided (`CaptchaRequiredException`)
  - `CAPTCHA_FAILED` — CAPTCHA verification failed (`CaptchaFailedException`)
  - `PASSWORD_EXPIRED` — password has expired (`PasswordExpiredException`)
  - `RATE_LIMITED` — login rate limit exceeded (`RateLimitExceededException`)
  - `MFA_REQUIRED` — reserved for future use (MFA checkpoint returns early, not exception)
  - `UNKNOWN` — fallback for any unexpected `AuthException` subclass
- **Validation**: 
  - Enum in `auth.domain.event` package
  - Each exception type in LoginHandler has corresponding enum value
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt` [NEW]
- **Pattern Reference**: `ValidationFailureReason.kt` — enum classifying validation failures

### 3.4 FR-004: Tạo LoginEventRecorder helper service [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `LoginEventRecorder` `@Component` trong `auth.application.event` package:
  - Inject `EventService` via constructor
  - Method 1: `recordLoginSuccess(event: UserLoggedInEvent, userId: Long, correlationId: String?)`
    - Calls `eventService.record(aggregateType="User", aggregateId=userId, event=event, topic="iam.user.logged_in", partitionKey=userId.toString(), correlationId=correlationId)`
    - `log.debug` on success with context (userId, eventType, correlationId)
  - Method 2: `recordLoginFailure(event: UserLoginFailedEvent, correlationId: String?)`
    - aggregateId = `event.userId ?: 0L` (0L for unknown users)
    - topic = `"iam.user.login_failed"`
    - partitionKey = `(event.userId ?: 0L).toString()`
    - `log.debug` on success with context
  - Both methods: `try/catch(Exception)` wrapping entire body — `log.warn` on failure with context (userId, eventType, error message)
- **Validation**:
  - Class in `auth.application.event` package
  - `@Component` annotation
  - Injects EventService via constructor
  - 2 public methods
  - aggregateType = `"User"` for both
  - Fire-and-forget: no exception propagation
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt` [NEW]
- **Pattern Reference**: `TokenEventRecorder.kt` — exact same structure

### 3.5 FR-005: Record UserLoggedInEvent khi login thành công [URD]
- **Actor**: Hệ thống (LoginHandler)
- **Precondition**: User authenticated, tokens generated, login session recorded, anonymous session promotion attempted.
- **Action**: Hệ thống phải gọi `loginEventRecorder.recordLoginSuccess()` trong `LoginHandler.handle()` AFTER:
  1. Token generation (`tokenGenerator.generateAuthResponse()`)
  2. Login session recording (`loginSessionService.recordLogin()`) — capture return value for `isNewDevice`
  3. Anonymous session promotion (`sessionPromotionService.promoteSession()`)
  
  `LoginEventRecorder` dependency injected via LoginHandler constructor (13th parameter).
- **Validation**:
  - LoginHandler has `loginEventRecorder: LoginEventRecorder` constructor parameter
  - `recordLoginSuccess()` called before `return LoginResult.Success`
  - Event recorded within same `@Transactional` boundary
  - `isNewDevice` sourced from `LoginSessionEntity.isNewDevice`
  - `sessionPromotionStatus` sourced from `PromotionResult.status.name`
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` [MODIFY]

### 3.6 FR-006: Record UserLoginFailedEvent khi login thất bại [URD]
- **Actor**: Hệ thống (LoginHandler)
- **Precondition**: Any `AuthException` thrown during `LoginHandler.handle()`.
- **Action**: Centralized `try-catch(e: AuthException)` wrapping the `handle()` body:
  1. Catch ALL `AuthException` subclasses
  2. Map exception to `LoginFailureReason` via private `mapToFailureReason(e: AuthException)` function
  3. Construct `UserLoginFailedEvent` with available context
  4. Call `loginEventRecorder.recordLoginFailure(event, correlationId)`
  5. Rethrow original exception (`throw e`)
  
  Exception-to-reason mapping:
  - `InvalidCredentialsException` → `INVALID_CREDENTIALS`
  - `AccountLockedException` → `ACCOUNT_LOCKED`
  - `CaptchaRequiredException` → `CAPTCHA_REQUIRED`
  - `CaptchaFailedException` → `CAPTCHA_FAILED`
  - `PasswordExpiredException` → `PASSWORD_EXPIRED`
  - `RateLimitExceededException` → `RATE_LIMITED`
  - `else` (any other AuthException) → `UNKNOWN`
  
  Local variable `resolvedUser: User? = null` tracks user resolution state for constructing failure event with correct userId (null if user never resolved).
  
  MFA checkpoint: `LoginResult.MfaRequired` is returned (early return, NOT exception) — correctly does NOT trigger catch block.
- **Validation**:
  - Centralized try-catch wraps entire handle() body
  - `mapToFailureReason()` covers all LoginHandler exception types
  - Original exception always rethrown after event recording
  - Event recording failure does NOT affect exception propagation
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` [MODIFY]

### 3.7 FR-007: Kafka topics cho login events [URD]
- **Actor**: OutboxPoller
- **Action**: Login events relayed via existing OutboxPoller mechanism. Topics: `iam.user.logged_in` (success), `iam.user.login_failed` (failure). Partition key = `userId.toString()`. No changes needed to OutboxPoller — topic driven by event data in outbox entry.
- **Validation**: Events appear on correct Kafka topics; partitioned by userId; message format = EventEnvelope JSON
- **Affected Files**: None — reuse existing OutboxPoller

### 3.8 FR-008: Migrate UserLoggedInEvent naming convention [URD]
- **Actor**: Hệ thống
- **Action**: Remove old `UserLoggedInEvent` from `AuthDomainEvents.kt` (2 fields, eventType = `USER_LOGGED_IN`). New enriched version in `auth.domain.event` package follows dot-notation: eventType = `iam.user.logged_in`. Keep `SessionRevokedEvent` in `AuthDomainEvents.kt`.
- **Validation**: Old `UserLoggedInEvent` removed; new class in `auth.domain.event`; eventType = `iam.user.logged_in`; `SessionRevokedEvent` unchanged
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt` [MODIFY]

### 3.9 FR-009: Schema versioning cho login events [URD]
- **Actor**: Hệ thống
- **Action**: Login events wrapped by `EventEnvelope` (existing). Initial `schemaVersion = 1` for both `UserLoggedInEvent` and `UserLoginFailedEvent`. Backward-compatible when adding new optional fields in future.
- **Validation**: schemaVersion = 1 in EventEnvelope; wrapping works with new event classes
- **Affected Files**: None — reuse existing EventEnvelope

### 3.10 FR-010: Fire-and-forget error handling pattern [URD]
- **Actor**: LoginEventRecorder
- **Action**: LoginEventRecorder catches ALL exceptions from EventService.record(). Event recording failure MUST NOT throw exception to caller. Login flow succeeds/fails based purely on authentication logic. Log WARN level on recording failure with context (userId, eventType, error message).
- **Validation**:
  - Unit test: `EventService.record()` throws RuntimeException → login still succeeds
  - No exception propagation from LoginEventRecorder methods
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt` [NEW]

### 3.11 FR-011: Complementary với TokenIssuedEvent [URD]
- **Actor**: Hệ thống
- **Action**: `UserLoggedInEvent` captures authentication context (who, where, how). `TokenIssuedEvent` (existing, via TokenEventRecorder) captures token context (JTI, roles, permissions, expiry). Two events complementary, NOT redundant. Both emitted during successful login.
- **Validation**: No overlapping fields beyond userId and ipAddress (used for correlation)
- **Affected Files**: None — documentation only

### 3.12 FR-012: Idempotency cho login event recording [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `EventEnvelope.id` (UUID) provides deduplication key for downstream consumers. Pattern same as `TokenIssuedEvent` already operating.
- **Validation**: Each event has unique UUID in EventEnvelope.id
- **Affected Files**: None — reuse existing EventEnvelope

### 3.13 FR-013: Structured logging cho event recording [ENRICHED]
- **Actor**: LoginEventRecorder
- **Action**: `log.debug` on success, `log.warn` on failure with context (userId, eventType, correlationId). Follow TokenEventRecorder logging pattern exactly.
- **Validation**: Log output includes all context fields
- **Affected Files**: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt` [NEW]

### 3.14 FR-014: Correlation ID threading [ENRICHED]
- **Actor**: CqrsAuthController, LoginHandler, LoginEventRecorder
- **Action**: 
  - CqrsAuthController extracts `X-Correlation-ID` (or `X-Request-ID`) HTTP header
  - Passes to `LoginCommand.correlationId` (new optional field, default = null)
  - LoginHandler passes to `loginEventRecorder.recordLoginSuccess()` / `recordLoginFailure()`
  - LoginEventRecorder passes to `EventService.record()` → EventEnvelope.correlationId
  - If null → EventService auto-generates UUID (existing behavior)
- **Validation**: 
  - LoginCommand has `correlationId: String? = null` field
  - CqrsAuthController extracts header and passes to LoginCommand
  - End-to-end correlation possible via correlationId
- **Affected Files**: 
  - `LoginCommand.kt` [MODIFY] — add field
  - `CqrsAuthController.kt` [MODIFY] — extract header
  - `LoginEventRecorder.kt` [NEW] — accept and pass correlationId

---

## 4. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Measurement |
|--------|------|---------|--------|-------------|
| NFR-001 | Performance | Event recording latency | < 5ms additional to login P95 | APM tracing on LoginEventRecorder |
| NFR-002 | Reliability | Failure isolation | 100% login success regardless of event store status | Integration test |
| NFR-003 | Security | No sensitive data in events | Password NEVER in event payload | Code review |
| NFR-004 | Throughput | Handle attack traffic spikes | 1000+ failure events/min | Load test |
| NFR-005 | Reliability | At-least-once Kafka delivery | Events retried via OutboxPoller until published | OutboxPoller monitoring |
| NFR-006 | Performance | Event store write throughput | > 200 events/sec | DB monitoring |

---

## 5. Assumptions

- ⚠️ Assumption: `LoginHandler.handle()` chạy trong `@Transactional` boundary → `EventService.record()` sẽ participate trong cùng transaction — xác nhận qua code: `@Transactional` annotation trên `LoginHandler.handle()` method
- ⚠️ Assumption: `EventService.record()` đủ hiệu quả cho login throughput — dựa trên pattern đã production-proven với `RegisterHandler` và `TokenEventRecorder`
- ⚠️ Assumption: `OutboxPoller` polling interval đủ cho login event throughput — cần monitor sau deploy
- ⚠️ Assumption: Existing `event_store` indexes đủ cho query by `event_type = 'iam.user.logged_in'` — cần verify
- ⚠️ Assumption: `User` entity has `mfaMethod` field accessible — used for `mfaBypassed` derivation. If not, approximate via domain-level check.

---

## 6. Open Questions

- ⚠️ OPEN QUESTION: OQ-1 — MFA verify flow: should emit `UserLoggedInEvent` in `MfaService.verifyTotp()` or create separate `MfaVerifiedEvent`? Decision: DEFERRED — ban đầu chỉ cover non-MFA path. MFA login event là separate scope.
- ⚠️ OPEN QUESTION: OQ-2 — Backward compatibility for eventType migration `USER_LOGGED_IN` → `iam.user.logged_in`? Decision: BREAK safe — old event only published via in-process Spring event, no listeners found. Verified via grep search.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Clarity | 23/25 | FR-006: exception list may not be exhaustive if new exception types added |
| Completeness | 22/25 | MFA verify path not covered (deferred scope) |
| Consistency | 23/25 | eventType migration safe but needs verification |
| Testability | 20/25 | Fire-and-forget pattern needs mock strategy |
| **Total** | **88/100** | |
