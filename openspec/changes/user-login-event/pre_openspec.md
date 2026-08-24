# Pre-OpenSpec: user-login-event

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (Research Artifacts — `openspec/research/user-login-event/`)
> **Classification Evidence**: keyword `UserLoggedInEvent` → module `auth.application.command.AuthDomainEvents` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`; keyword `LoginHandler` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`; keyword `EventService.record()` → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`; keyword `TokenEventRecorder` (pattern reference) → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Nâng cấp hệ thống sự kiện đăng nhập người dùng trong auth-service lên production-grade theo kiến trúc Event Sourcing + CQRS đã có. Hiện tại, `LoginHandler` chỉ publish `UserLoggedInEvent` tối giản (2 fields: `userId`, `domainCode`) qua `SpringEventPublisher` (in-process, KHÔNG persist vào event store, KHÔNG relay qua Kafka). Feature này bao gồm: (1) tạo `UserLoggedInEvent` enriched với đầy đủ context (device, IP, MFA status, session info) trong `auth.domain.event` package; (2) tạo `UserLoginFailedEvent` với failure reason classification; (3) tạo `LoginEventRecorder` helper service (mirror `TokenEventRecorder` pattern); (4) integrate vào `LoginHandler` success + failure paths; (5) tận dụng `EventService.record()` cho event store + transactional outbox → Kafka relay qua `OutboxPoller`.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (URD: 11, Enriched: 3) |
| Issues | 3 (🔴: 1, 🟡: 2) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Client (End User)**: Gửi request đăng nhập qua REST API (`POST /api/auth/login`), trigger login command
- **Hệ thống (LoginHandler)**: CQRS command handler — xác thực user, generate tokens, record domain events vào event store + outbox
- **LoginEventRecorder**: Helper service — wrap EventService.record() cho login events, error isolation (fire-and-forget)
- **EventService**: Intermediary service — create EventEnvelope, persist event store + outbox trong cùng @Transactional
- **OutboxPoller**: Async relay — poll event_outbox table, publish to Kafka topics
- **Downstream Consumers (Admin Service, SIEM, Analytics)**: Consume login events từ Kafka cho audit trail, anomaly detection, compliance

## 2. Functional Requirements

### FR-001: Tạo UserLoggedInEvent enriched domain event [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `UserLoggedInEvent` data class trong package `auth.domain.event` implement `DomainEvent` interface với enriched payload gồm: userId, username, domainCode, domainId, loginMethod, mfaBypassed, mfaMethod, isNewDevice, ipAddress, userAgent, deviceFingerprint, sessionPromotionStatus, loggedInAt
- **Validation**: Class nằm trong `auth.domain.event` package; implement DomainEvent interface; eventType = `iam.user.logged_in`; tất cả required fields có giá trị non-null

### FR-002: Tạo UserLoginFailedEvent domain event [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `UserLoginFailedEvent` data class trong package `auth.domain.event` implement `DomainEvent` interface với payload gồm: usernameAttempted, userId (nullable — null khi user không tồn tại), failureReason (LoginFailureReason enum), ipAddress, userAgent, deviceFingerprint, failedAt
- **Validation**: Class nằm trong `auth.domain.event` package; eventType = `iam.user.login_failed`; failureReason là enum value

### FR-003: Tạo LoginFailureReason enum [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `LoginFailureReason` enum class trong `auth.domain.event` package với các giá trị: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, CAPTCHA_REQUIRED, CAPTCHA_FAILED, PASSWORD_EXPIRED, RATE_LIMITED, MFA_REQUIRED, UNKNOWN. Map 1:1 với exceptions trong LoginHandler
- **Validation**: Enum nằm trong `auth.domain.event` package; mỗi exception type trong LoginHandler có corresponding enum value

### FR-004: Tạo LoginEventRecorder helper service [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `LoginEventRecorder` @Component trong `auth.application.event` package, inject `EventService`, cung cấp 2 methods: `recordLoginSuccess(event, userId, correlationId)` và `recordLoginFailure(event, correlationId)`. Follows `TokenEventRecorder` pattern exactly — delegate to EventService.record(), catch all exceptions
- **Validation**: Class nằm trong `auth.application.event` package; inject EventService; 2 public methods; aggregateType = "User"; topics = `iam.user.logged_in` / `iam.user.login_failed`; partitionKey = userId.toString()

### FR-005: Record UserLoggedInEvent khi login thành công [URD]
- **Actor**: Hệ thống (LoginHandler)
- **Action**: Hệ thống phải gọi `loginEventRecorder.recordLoginSuccess()` trong `LoginHandler.handle()` SAU khi generate auth tokens và record login session — tương tự cách RegisterHandler record UserRegisteredEvent. LoginEventRecorder dependency phải được inject vào LoginHandler constructor
- **Validation**: LoginHandler gọi `loginEventRecorder.recordLoginSuccess()` trước khi return `LoginResult.Success`; event record trong cùng `@Transactional`; loginEventRecorder injected via constructor

### FR-006: Record UserLoginFailedEvent khi login thất bại [URD]
- **Actor**: Hệ thống (LoginHandler)
- **Action**: Hệ thống phải record `UserLoginFailedEvent` khi login thất bại — tại mỗi failure point trong LoginHandler. Map exceptions: InvalidCredentialsException → INVALID_CREDENTIALS, AccountLockedException → ACCOUNT_LOCKED, CaptchaRequiredException → CAPTCHA_REQUIRED, CaptchaFailedException → CAPTCHA_FAILED, PasswordExpiredException → PASSWORD_EXPIRED. Gọi loginEventRecorder.recordLoginFailure() trước khi rethrow exception
- **Validation**: Failed login event recorded trước khi exception throw; original exception propagation không bị affect; mỗi exception type map đúng LoginFailureReason enum value

### FR-007: Kafka topics cho login events [URD]
- **Actor**: OutboxPoller
- **Action**: Login events phải được relay qua Kafka qua existing OutboxPoller mechanism. Topics: `iam.user.logged_in` (success), `iam.user.login_failed` (failure). Partition key = userId.toString(). Không cần thay đổi OutboxPoller — topic driven by event data trong outbox entry
- **Validation**: Events xuất hiện trên đúng Kafka topics; partitioned by userId; message format = EventEnvelope JSON

### FR-008: Migrate UserLoggedInEvent naming convention [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải migrate `UserLoggedInEvent` từ `auth.application.command.AuthDomainEvents` sang enriched version trong `auth.domain.event`. Old class (eventType = `USER_LOGGED_IN`, 2 fields) phải bị remove. New class follow dot-notation convention: eventType = `iam.user.logged_in`. Cập nhật imports tại các usage sites
- **Validation**: Old `UserLoggedInEvent` removed từ `AuthDomainEvents.kt`; new class trong `auth.domain.event`; eventType = `iam.user.logged_in`

### FR-009: Schema versioning cho login events [URD]
- **Actor**: Hệ thống
- **Action**: Login events phải support versioning — mỗi event có `schemaVersion` (integer) được wrap bởi `EventEnvelope` (existing). Initial version = 1 cho cả `UserLoggedInEvent` và `UserLoginFailedEvent`. Backward-compatible khi add new optional fields
- **Validation**: schemaVersion = 1 trong EventEnvelope; EventEnvelope wrapping hoạt động với new event classes

### FR-010: Fire-and-forget error handling pattern [URD]
- **Actor**: LoginEventRecorder
- **Action**: LoginEventRecorder phải catch ALL exceptions từ EventService.record() — event recording failure KHÔNG ĐƯỢC throw exception ra caller. Login flow PHẢI succeed/fail based purely on authentication logic. Log WARN level khi recording fails với context (userId, eventType, error message)
- **Validation**: Unit test: EventService.record() throw RuntimeException → login vẫn succeed; no exception propagation

### FR-011: Complementary với TokenIssuedEvent [URD]
- **Actor**: Hệ thống
- **Action**: `UserLoggedInEvent` capture authentication context (who, where, how). `TokenIssuedEvent` (existing, via TokenEventRecorder) capture token context (JTI, roles, permissions, expiry). Hai events complementary, KHÔNG redundant. Cả hai đều emit khi login success — UserLoggedInEvent sau TokenIssuedEvent (TokenIssuedEvent recorded trong tokenGenerator.generateAuthResponse())
- **Validation**: No overlapping fields giữa 2 events (ngoại trừ userId, ipAddress dùng cho correlation); cả 2 events recorded per successful login

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Measurement |
|--------|------|---------|--------|-------------|
| NFR-001 | Performance | Event recording latency | < 5ms additional to login P95 | APM tracing on LoginEventRecorder |
| NFR-002 | Reliability | Failure isolation | 100% login success regardless of event store status | Integration test |
| NFR-003 | Security | No sensitive data in events | Password NEVER in event payload | Code review |
| NFR-004 | Throughput | Handle attack traffic spikes | 1000+ failure events/min | Load test |
| NFR-005 | Reliability | At-least-once Kafka delivery | Events retried via OutboxPoller until published | OutboxPoller monitoring |
| NFR-006 | Performance | Event store write throughput | > 200 events/sec | DB monitoring |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-005 và FR-006 khác nhau rõ ràng (success path vs failure path). FR-004 (LoginEventRecorder creation) và FR-010 (fire-and-forget behavior) bổ sung cho nhau — FR-004 là class creation, FR-010 là error handling specification. FR-007 (Kafka topics) và FR-009 (schema versioning) bổ sung — FR-007 là transport, FR-009 là format.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-012** [ENRICHED]: Idempotency cho login event recording — EventEnvelope.id (UUID) cung cấp deduplication key cho downstream consumers. Pattern tương tự TokenIssuedEvent đã hoạt động.
- **FR-013** [ENRICHED]: Structured logging cho event recording — log.debug on success, log.warn on failure với context (userId, eventType, correlationId). Follow TokenEventRecorder logging pattern exactly.
- **FR-014** [ENRICHED]: Correlation ID threading — LoginEventRecorder phải accept correlationId parameter, pass to EventService.record() cho end-to-end tracing. Nếu null → EventService tự generate UUID (existing behavior).

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Kafka | Event transport — relay login events từ outbox | Existing: `OutboxPoller` → `KafkaEventPublisher` |
| PostgreSQL | Event store persistence — `event_store` table | Existing: `EventStorePort` → `EventStorePersistenceAdapter` |
| PostgreSQL | Outbox table — `event_outbox` table | Existing: `OutboxPort` → `OutboxPersistenceAdapter` |

## 6. Assumptions

- ⚠️ Assumption: `LoginHandler.handle()` chạy trong `@Transactional` boundary → `EventService.record()` sẽ participate trong cùng transaction — xác nhận qua code: `@Transactional` annotation trên `LoginHandler.handle()` method
- ⚠️ Assumption: `EventService.record()` đủ hiệu quả cho login throughput — dựa trên pattern đã production-proven với `RegisterHandler` và `TokenEventRecorder`
- ⚠️ Assumption: `OutboxPoller` polling interval đủ cho login event throughput — cần monitor sau deploy
- ⚠️ Assumption: Existing `event_store` indexes đủ cho query by `event_type = 'iam.user.logged_in'` — cần verify

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-006: failure point enumeration có thể miss edge cases ngoài LoginHandler |
| Đầy đủ (Completeness) | 22/25 | FR-005: MFA verify handler (`MfaService.verifyTotp()`) chưa được address — event chỉ emit ở LoginHandler |
| Nhất quán (Consistency) | 23/25 | FR-008: eventType migration từ `USER_LOGGED_IN` → `iam.user.logged_in` cần verify no existing listeners |
| Kiểm thử được (Testability) | 20/25 | FR-010: timeout simulation cần mock; nhiều FR cần rõ acceptance criteria hơn |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-006 | "Record UserLoginFailedEvent tại mỗi failure point" — danh sách exceptions có thể không exhaustive (e.g., RateLimitExceededException) | Liệt kê ALL exception types từ LoginHandler code |
| 2 | Completeness | -3 | FR-005 | MFA flow: LoginHandler return MfaRequired trước login hoàn tất. UserLoggedInEvent chỉ emit sau password auth — MFA verify success path (MfaService) chưa covered | Xác định rõ scope: ban đầu chỉ cover password auth path |
| 3 | Consistency | -2 | FR-008 | Migration eventType `USER_LOGGED_IN` → `iam.user.logged_in` có thể break existing Spring @EventListener | Check existing listeners; SpringEventPublisher emit khác DomainEvent |
| 4 | Testability | -3 | FR-010 | "Event recording failure KHÔNG throw" — cần rõ mock strategy cho EventService.record() throws | Thêm unit test case: mock EventService.record() throw → verify login succeed |
| 5 | Testability | -2 | Multiple | Thiếu measurable acceptance criteria cho throughput/latency targets | Thêm "khi X thì Y trong Z ms" format |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | MFA flow path: `LoginHandler` return `LoginResult.MfaRequired` trước login hoàn tất → `UserLoggedInEvent` chỉ nên emit sau MFA verify thành công. Nhưng MFA verify xử lý trong `MfaService.verifyTotp()` / `MfaController` — không phải `LoginHandler`. Scope ban đầu: chỉ cover password-only login (non-MFA). MFA login event cần separate feature/FR. | FR-005 | Scope limitation: ban đầu chỉ emit event cho non-MFA login. MFA login event → separate follow-up |
| 2 | Risk | 🟡 | Failed login event aggregateId: khi username không tồn tại, userId unknown → aggregateId = 0L. EventStore query by aggregate sẽ không tìm thấy events cho unknown users — chấp nhận vì failed events vẫn queryable qua eventType filter và Kafka topic. | FR-006 | Dùng aggregateId = 0L cho unknown users; thêm usernameAttempted field cho querying |
| 3 | Risk | 🟡 | LoginHandler hiện có 12 constructor params — thêm LoginEventRecorder sẽ là 13. Vẫn acceptable nhưng monitor. | FR-005 | Inject LoginEventRecorder (không inject EventService trực tiếp) — giữ handler lean |

## 9. Open Questions

- **OQ-1**: MFA verify flow — nên emit `UserLoggedInEvent` trong `MfaService.verifyTotp()` hay tạo separate `MfaVerifiedEvent`? Decision: DEFERRED — ban đầu chỉ cover non-MFA path. MFA login event là separate scope.
- **OQ-2**: Có cần backward compatibility period cho eventType migration `USER_LOGGED_IN` → `iam.user.logged_in` hay break existing consumers? Decision: BREAK — old event chỉ dùng in-process Spring event (ApplicationEventPublisher), không persist. Cần verify không có @EventListener references.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication — Login/Authentication Events (Event Sourcing layer)

### 10.2 Flow Type
Command — Login command mutates state via domain events (CQRS write-side)

### 10.3 Candidate Services
- **auth-service (auth module)**: Primary — contains LoginHandler, AuthDomainEvents (UserLoggedInEvent), EventService, TokenEventRecorder (pattern reference), LoginSessionService, LoginCommand, CqrsAuthController login endpoint. Evidence: keyword `UserLoggedInEvent` in `AuthDomainEvents.kt`, `LoginHandler.kt`, `EventService.kt`
- **auth-service (shared module)**: Supporting — exception classes (InvalidCredentialsException, AccountLockedException, CaptchaRequiredException, CaptchaFailedException, PasswordExpiredException), error codes, config. Evidence: `AuthErrorCode.kt`, `AuthExceptions.kt`, `AuthCoreExceptions.kt`

### Detection Evidence
- Keyword: `UserLoggedInEvent` → Module: `auth.application.command.AuthDomainEvents` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
- Keyword: `LoginHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Keyword: `TokenEventRecorder` (pattern reference) → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
- Keyword: `UserRegisteredEvent` (pattern reference) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
- Keyword: `LoginCommand` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`
- Keyword: `DomainEvent` → Module: `auth.application.port.out` → File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
- Keyword: `EventEnvelope` → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
- Keyword: `ValidationFailureReason` (enum pattern reference) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`
- Keyword: `LoginSessionService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`

### 10.4 External Integrations
- **Kafka**: Outbox relay via `OutboxPoller` → topics `iam.user.logged_in`, `iam.user.login_failed`
- **PostgreSQL**: Event store (`event_store` table), Outbox (`event_outbox` table)

### 10.5 Required Modules
- `auth.domain.event` — NEW: `UserLoggedInEvent.kt`, `UserLoginFailedEvent.kt`, `LoginFailureReason.kt`
- `auth.application.event` — NEW: `LoginEventRecorder.kt`; REUSE: `EventService.kt` (no modification)
- `auth.application.command` — MODIFY: `LoginHandler.kt` (inject LoginEventRecorder, record events); CLEANUP: `AuthDomainEvents.kt` (remove old UserLoggedInEvent)
- `auth.adapter.out.event` — REUSE: `OutboxPoller.kt` (no modification needed)
- `auth.adapter.in.web` — NO CHANGE to `CqrsAuthController.kt` (login endpoint unchanged externally)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi `POST /api/auth/login` với credentials + device info | CqrsAuthController |
| 2 | CqrsAuthController | Extract IP, User-Agent, X-Device-Fingerprint → build LoginCommand | CqrsAuthController |
| 3 | LoginHandler | Validate credentials, check lock, CAPTCHA, password verify, password expiry | LoginHandler (@Transactional) |
| 3a (fail) | LoginHandler | Catch auth exception → LoginEventRecorder.recordLoginFailure(UserLoginFailedEvent) → re-throw | LoginEventRecorder → EventService |
| 4 | LoginHandler | MFA checkpoint — if MFA required → return MfaRequired (NO event emitted) | LoginHandler |
| 5 | LoginHandler | Generate tokens → TokenIssuedEvent recorded via TokenEventRecorder | TokenGenerator → TokenEventRecorder |
| 6 | LoginHandler | Record login session → LoginSessionService.recordLogin() → returns LoginSessionEntity with isNewDevice | LoginSessionService |
| 7 | LoginHandler | Anonymous session promotion (best-effort) | SessionPromotionService |
| 8 | LoginHandler | LoginEventRecorder.recordLoginSuccess(UserLoggedInEvent) — fire-and-forget | LoginEventRecorder → EventService |
| 9 | EventService | Create EventEnvelope → persist event_store + event_outbox (same TX) | EventStorePort, OutboxPort |
| 10 | LoginHandler | Return LoginResult.Success | → CqrsAuthController → Client |
| 11 | OutboxPoller | Async: poll event_outbox → relay to Kafka topic | OutboxPoller → KafkaEventPublisher |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 (enriched payload) | TBD | `UserLoggedInEvent.kt` [ADD] | Pending |
| FR-002 | UC-002 (failed event) | TBD | `UserLoginFailedEvent.kt` [ADD] | Pending |
| FR-003 | UC-002 (failure reason) | TBD | `LoginFailureReason.kt` [ADD] | Pending |
| FR-004 | UC-001/UC-002 (helper service) | TBD | `LoginEventRecorder.kt` [ADD] | Pending |
| FR-005 | UC-001 (record success) | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-006 | UC-002 (record failure) | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-007 | UC-004 (Kafka topics) | TBD | `OutboxPoller.kt` [REUSE] | Pending |
| FR-008 | UC-001 (naming convention) | TBD | `AuthDomainEvents.kt` [MODIFY], `UserLoggedInEvent.kt` [ADD] | Pending |
| FR-009 | UC-001/UC-002 (schema version) | TBD | `EventEnvelope.kt` [REUSE] | Pending |
| FR-010 | BR-001 (fire-and-forget) | TBD | `LoginEventRecorder.kt` [ADD] | Pending |
| FR-011 | BR-004 (complementary) | TBD | Documentation | Pending |
| FR-012 | Enriched (idempotency) | TBD | `EventEnvelope.kt` [REUSE] | Pending |
| FR-013 | Enriched (logging) | TBD | `LoginEventRecorder.kt` [ADD] | Pending |
| FR-014 | Enriched (correlation ID) | TBD | `LoginEventRecorder.kt` [ADD] | Pending |

### Change Impact Map (EXTEND)

```
FR-001 → [ADD] UserLoggedInEvent.kt (src/main/kotlin/.../auth/domain/event/) → NEW enriched domain event
FR-002 → [ADD] UserLoginFailedEvent.kt (src/main/kotlin/.../auth/domain/event/) → NEW domain event
FR-003 → [ADD] LoginFailureReason.kt (src/main/kotlin/.../auth/domain/event/) → NEW enum
FR-004 → [ADD] LoginEventRecorder.kt (src/main/kotlin/.../auth/application/event/) → NEW helper service
FR-005 → [MODIFY] LoginHandler.kt (src/main/kotlin/.../auth/application/command/) → inject LoginEventRecorder, call recordLoginSuccess()
FR-006 → [MODIFY] LoginHandler.kt (src/main/kotlin/.../auth/application/command/) → add failure event recording at catch points
FR-007 → [REUSE] OutboxPoller.kt → no changes needed (topic driven by event data)
FR-008 → [MODIFY] AuthDomainEvents.kt (src/main/kotlin/.../auth/application/command/) → remove old UserLoggedInEvent
FR-009 → [REUSE] EventEnvelope.kt → no changes needed
FR-010 → [ADD] LoginEventRecorder.kt → fire-and-forget error handling
FR-011 → [REUSE] TokenEventRecorder.kt → no changes needed (complementary event)
FR-012 → [REUSE] EventEnvelope.id (UUID) → deduplication key
FR-013 → [ADD] LoginEventRecorder.kt → structured logging
FR-014 → [ADD] LoginEventRecorder.kt → correlationId threading
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
- Feature này là **mirror pattern** của `user_registration_event` (đã implement thành công, archived `2026-08-20-user_registration_event/`). Pattern: enriched domain event → helper recorder → EventService.record() → event store + outbox → Kafka. Complexity: MEDIUM.
- **Key design decision**: Sử dụng `LoginEventRecorder` helper service (mirroring `TokenEventRecorder`) thay vì inject EventService trực tiếp vào LoginHandler. Lý do: (1) encapsulate topic routing logic, (2) centralize error handling (fire-and-forget), (3) keep LoginHandler focused on auth logic, (4) consistent với established pattern.
- **Critical gap discovered**: `LoginHandler` hiện tại KHÔNG record bất kỳ domain event nào vào event store cho login actions. Chỉ có: (a) `LoginSessionEntity` qua `LoginSessionService.recordLogin()` (DB table `login_sessions`), (b) minimal `UserLoggedInEvent` qua `ApplicationEventPublisher` (in-process Spring event, NOT persisted), (c) `TokenIssuedEvent` via `TokenEventRecorder` (covers token issuance, NOT login context). Đây là gap lớn trong audit trail completeness.
- Existing `UserLoggedInEvent` trong `AuthDomainEvents.kt` chỉ có 2 fields (userId, domainCode) và eventType = `USER_LOGGED_IN` — KHÔNG follow dot-notation convention (`iam.{aggregate}.{action}`) đã thiết lập bởi `UserRegisteredEvent` (`iam.user.registered`), `TokenIssuedEvent` (`iam.token.issued`).
- MFA flow là complexity point: LoginHandler return `LoginResult.MfaRequired` (partial login) → MFA verify xảy ra trong `MfaService.verifyTotp()` / `MfaController.verifyMfa()`. Decision: DEFER MFA login events to separate scope — ban đầu chỉ cover non-MFA successful login.
- LoginHandler hiện có 12 constructor dependencies — thêm LoginEventRecorder sẽ là 13. Vẫn acceptable cho CQRS handler.

### Related Features / Precedents
- `user_registration_event` (archived: `2026-08-20-user_registration_event/`) — **PRIMARY reference**. Enriched `UserRegisteredEvent`, EventService integration trong RegisterHandler, outbox + Kafka relay. Exact same pattern cần follow.
- `jwt-token-validation` (archived: `2026-08-26-jwt-token-validation/`) — `TokenValidationFailedEvent`, `ValidationFailureReason` enum, fire-and-forget recording. Same failure event + enum pattern.
- `auth-core-features` (archived: `2026-08-21-auth-core-features/`) — LoginHandler CQRS extraction, LoginSessionService, LoginRateLimitService, MFA integration.
- `anonymous-login-optimization` (archived: `2026-08-20-anonymous-login-optimization/`) — Session promotion flow, PromotionResult status.

### Integration Notes
- **LoginEventRecorder** (NEW): Mirrors `TokenEventRecorder` exactly — @Component, inject EventService, 2 public methods (recordLoginSuccess, recordLoginFailure), try/catch all exceptions, log.warn on failure, log.debug on success.
- **EventService** (REUSE): Không cần modify. `record()` method generic, accept any `DomainEvent`. Call pattern: `eventService.record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)`.
- **OutboxPoller** (REUSE): Không cần modify. Polls `event_outbox` table, relay to Kafka by topic field.
- **LoginSessionService** (READ reference): `recordLogin()` returns `LoginSessionEntity` which has `isNewDevice` flag — source for event enrichment.

### Suggested Approach
1. **Phase 1**: Create `UserLoggedInEvent.kt`, `UserLoginFailedEvent.kt`, `LoginFailureReason.kt` trong `auth.domain.event` package — follow `UserRegisteredEvent` + `ValidationFailureReason` patterns.
2. **Phase 2**: Create `LoginEventRecorder.kt` trong `auth.application.event` package — mirror `TokenEventRecorder` exactly.
3. **Phase 3**: Modify `LoginHandler.kt` — inject `LoginEventRecorder`, call `recordLoginSuccess()` after token generation + session recording, call `recordLoginFailure()` at each exception throw site.
4. **Phase 4**: Clean up `AuthDomainEvents.kt` — remove old `UserLoggedInEvent` class (keep `SessionRevokedEvent`).
5. **Phase 5**: Tests — unit tests cho LoginEventRecorder (fire-and-forget), unit tests cho LoginHandler event recording integration, verify no exception propagation.

### Context from Confluence Images
N/A
