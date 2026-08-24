# Impact Analysis: user-login-event

_Generated: 2025-08-22_
_Type: EXTEND_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [LoginHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | L35-48 (constructor), L56-180 (handle method) | Inject LoginEventRecorder, add try-catch for failure recording, add success event recording, capture loginSession return value |
| 2 | [LoginCommand.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) | L10-21 (data class) | Add correlationId field |
| 3 | [AuthDomainEvents.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt) | L12-17 (UserLoggedInEvent class) | Remove old UserLoggedInEvent, keep SessionRevokedEvent |
| 4 | [CqrsAuthController.kt](file:///home/nguyentthai96/Desktop/bigException) {                         // ← NEW: centralized catch
│   ├── loginEventRecorder.recordLoginFailure(           // ← NEW: fire-and-forget
│   │       UserLoginFailedEvent(usernameAttempted=command.username,
│   │           userId=resolvedUser?.id?.value,
│   │           failureReason=mapToFailureReason(e),
│   │           ipAddress, userAgent, deviceFingerprint),
│   │       command.correlationId)
│   └── throw e                                          // always rethrow original
└── }

// NEW private function:
⟶ mapToFailureReason(exception: AuthException): LoginFailureReason
├── is InvalidCredentialsException → INVALID_CREDENTIALS
├── is AccountLockedException → ACCOUNT_LOCKED
├── is CaptchaRequiredException → CAPTCHA_REQUIRED
├── is CaptchaFailedException → CAPTCHA_FAILED
├── is PasswordExpiredException → PASSWORD_EXPIRED
├── is RateLimitExceededException → RATE_LIMITED
└── else → UNKNOWN
```

#### `LoginEventRecorder.recordLoginSuccess()` — NEW

```
⟶ recordLoginSuccess(event: UserLoggedInEvent, userId: Long, correlationId: String?)
├── try {
│   ├── eventService.record(                             // delegate to EventService
│   │       aggregateType="User", aggregateId=userId,
│   │       event=event, topic="iam.user.logged_in",
│   │       partitionKey=userId.toString(), correlationId)
│   │   ├── EventEnvelope created (UUID id, schemaVersion=1)
│   │   ├── eventStorePort.append(...)                   // same TX as LoginHandler
│   │   └── outboxPort.insert(...)                       // same TX as LoginHandler
│   └── log.debug("Login success event recorded: userId={}, ...")
└── } catch (e: Exception) {
    └── log.warn("Failed to record login success event: userId={}, error={}", ...)
        // ← fire-and-forget: NO throw
```

#### `LoginEventRecorder.recordLoginFailure()` — NEW

```
⟶ recordLoginFailure(event: UserLoginFailedEvent, correlationId: String?)
├── try {
│   ├── val aggregateId = event.userId ?: 0L             // 0L for unknown users
│   ├── eventService.record(
│   │       aggregateType="User", aggregateId=aggregateId,
│   │       event=event, topic="iam.user.login_failed",
│   │       partitionKey=aggregateId.toString(), correlationId)
│   └── log.debug("Login failure event recorded: username={}, reason={}, ...")
└── } catch (e: Exception) {
    └── log.warn("Failed to record login failure event: username={}, reason={}, error={}", ...)
        // ← fire-and-forget: NO throw
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (4 files modified)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Primary integration — inject LoginEventRecorder, add try-catch, add success recording. Constructor +1 param. 12 existing deps → 13. |
| 2 | `LoginCommand.kt` | [LoginCommand](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) | Add optional `correlationId` field. Additive — default null. All existing callers unaffected. |
| 3 | `AuthDomainEvents.kt` | [AuthDomainEvents](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt) | Remove old `UserLoggedInEvent` class (L12-17). Keep `SessionRevokedEvent`. |
| 4 | `CqrsAuthController.kt` | [CqrsAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | Minor — extract X-Correlation-ID header in login() method, pass to LoginCommand. |

### 🟡 Indirect Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `LoginHandlerTest.kt` | [LoginHandlerTest](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/application/command/LoginHandlerTest.kt) | Test class for LoginHandler — needs update to mock LoginEventRecorder (new constructor param) + add test cases for event recording |
| 2 | `LoginSessionService.kt` | [LoginSessionService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt) | No code change — but LoginHandler now captures its `recordLogin()` return value (`LoginSessionEntity`) instead of discarding it |
| 3 | `EventService.kt` | [EventService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt) | No code change — called by new LoginEventRecorder via existing `record()` method. Already handles any DomainEvent implementation. |

### 🟠 Cross-service Impact (2 Kafka topics)

| # | Topic | Protocol | Consumers | Impact |
|---|-------|----------|-----------|--------|
| 1 | `iam.user.logged_in` | Kafka | Downstream: SIEM, admin-service, analytics | NEW topic — no existing consumers to break. Consumers need to subscribe. |
| 2 | `iam.user.login_failed` | Kafka | Downstream: SIEM, analytics | NEW topic — no existing consumers to break. Consumers need to subscribe. |

### 🟢 Shared Utilities (5 files — read-only, no changes)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `EventService.kt` | [EventService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt) | `record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)` |
| 2 | `EventEnvelope.kt` | [EventEnvelope](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt) | Generic wrapping via EventService |
| 3 | `DomainEvent` (interface) | [EventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | Interface implemented by new events |
| 4 | `OutboxPoller.kt` | [OutboxPoller](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt) | Async relay — polls outbox, publishes to Kafka by topic |
| 5 | `TokenEventRecorder.kt` | [TokenEventRecorder](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt) | Pattern reference only — no code change. LoginEventRecorder mirrors its structure. |

---

## 4. Reuse Map

> Follows `reuse_rules_compact.md` Step 2 Decision Framework.

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Event recording (fire-and-forget) | [TokenEventRecorder:L29-49](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt#L29-L49) | 90% | REUSE pattern | 🟢 Low (0 callers affected) | Create LoginEventRecorder mirroring same pattern — no extraction needed (different aggregate, different topics) |
| Domain event data class | [UserRegisteredEvent:L1-26](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt) | 80% | REUSE pattern | 🟢 Low (0 callers affected) | Create UserLoggedInEvent following same DomainEvent pattern — different fields, same structure |
| Failure reason enum | [ValidationFailureReason:L1-22](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt) | 70% | REUSE pattern | 🟢 Low (0 callers affected) | Create LoginFailureReason following same enum pattern — different values for login domain |
| EventService.record() | [EventService:L47-89](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt#L47-L89) | 100% | REUSE (direct call) | 🟢 Low (0 changes to EventService) | Call existing method — no changes needed |
| Exception handling pattern | [LoginHandler:L56-180](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt#L56-L180) | N/A | NEW (centralized catch) | 🟢 Low (encapsulates existing flow) | Add try-catch around existing body — no extract needed |

**Summary**: All reuse is pattern-level (create new classes following existing patterns). No EXTRACT needed — domain separation justifies separate classes (login events ≠ token events ≠ registration events).

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

> Agent đọc section này → đủ info bắt tay code, KHÔNG cần search thêm.

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `EventService` | @Component (injected into LoginEventRecorder) | `record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)` | Generic — accepts any DomainEvent. Within @Transactional of caller. |
| `LoginEventRecorder` | @Component (injected into LoginHandler) | `recordLoginSuccess(event, userId, correlationId)`, `recordLoginFailure(event, correlationId)` | NEW — fire-and-forget. Mirror of TokenEventRecorder. |
| `LoginSessionService` | @Service (already injected into LoginHandler) | `recordLogin(userId, ipAddress, userAgent, deviceFingerprint, refreshTokenId): LoginSessionEntity` | Returns LoginSessionEntity with `isNewDevice` field — currently discarded, need to capture. |
| `DomainEvent` | Interface (`auth.application.port.out`) | `val eventType: String` | Implemented by UserLoggedInEvent, UserLoginFailedEvent. |
| `EventEnvelope<T>` | Data class (`auth.domain.event`) | Wraps events with id, type, source, specversion, time, correlationId, schemaVersion, data | Created by EventService.record() — no direct usage. |
| `AuthException` | Abstract class (`shared.exception`) | Base class for all auth exceptions | All LoginHandler exceptions extend this — single catch type. |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|------------|---------|
| `securityProperties.mfa.trustedDeviceTtlDays` | `SecurityProperties` | `30` | LoginHandler — `user.requiresMfa(hash, ttlDays)` for mfaBypassed derivation |
| `securityProperties.password.maxFailedAttempts` | `SecurityProperties` | `5` | LoginHandler — CAPTCHA threshold |

### Error Codes Thrown

| Error Code | Exception | LoginFailureReason |
|-----------|-----------|-------------------|
| `AUTH_001` | `InvalidCredentialsException` | `INVALID_CREDENTIALS` |
| `AUTH_002` | `AccountLockedException` | `ACCOUNT_LOCKED` |
| `AUTH_007` | `CaptchaRequiredException` | `CAPTCHA_REQUIRED` |
| `AUTH_008` | `CaptchaFailedException` | `CAPTCHA_FAILED` |
| `AUTH_018` | `PasswordExpiredException` | `PASSWORD_EXPIRED` |
| `AUTH_020` | `RateLimitExceededException` | `RATE_LIMITED` |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| UserLoggedInEvent (domain event) | Old UserLoggedInEvent (AuthDomainEvents) | 15% (only 2/14 fields match) | NEW — enriched version replaces old |
| UserLoginFailedEvent (domain event) | TokenValidationFailedEvent | 40% (similar structure, different domain) | NEW — login-specific failure event |
| LoginFailureReason (enum) | ValidationFailureReason | 30% (different values) | NEW — login-specific failure reasons |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `EventService.record()` | `fun <T : DomainEvent> record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)` | view_file EventService.kt L47 | ✅ |
| `LoginSessionService.recordLogin()` | `fun recordLogin(userId, ipAddress, userAgent, deviceFingerprint, refreshTokenId): LoginSessionEntity` | view_file LoginSessionService.kt L30-36 | ✅ |
| `DomainEvent.eventType` | `interface DomainEvent { val eventType: String }` | view_file EventPublisher.kt L13 | ✅ |
| `User.requiresMfa()` | Method exists on User domain model | grep_search confirmed | ✅ |
| `LoginSessionEntity.isNewDevice` | Field set in LoginSessionService.recordLogin() L50 | view_file LoginSessionService.kt L50 | ✅ |
