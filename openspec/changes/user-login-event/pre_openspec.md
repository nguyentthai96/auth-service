# Pre-OpenSpec: user-login-event

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: keyword `UserLoggedInEvent` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`; keyword `LoginHandler` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`; keyword `EventService` → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`; keyword `LoginSessionService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
> **Archive**: N/A
> **Quality Score**: 82/100

## 📋 Feature Summary

Nâng cấp hệ thống sự kiện đăng nhập người dùng (`UserLoggedInEvent`) trong auth-service lên mức production-grade theo kiến trúc Event Sourcing. Hiện tại, `LoginHandler` chỉ publish một `UserLoggedInEvent` tối giản (chỉ có `userId`, `domainCode`) qua `SpringEventPublisher` (in-process, KHÔNG persist vào event store, KHÔNG relay qua Kafka). Tính năng này bao gồm: enrichment payload event với đầy đủ context (device, IP, MFA status, session info), event store persistence qua `EventService.record()`, transactional outbox cho Kafka relay, thêm `UserLoginFailedEvent` cho failed login attempts, schema versioning, và downstream consumer contracts cho audit trail, anomaly detection, session analytics. Hệ thống tận dụng pattern đã được thiết lập bởi `user_registration_event` feature — `EventService`, `EventEnvelope`, `EventStorePort`, `OutboxPort`, `OutboxPoller`.

| Metric | Giá trị |
|--------|---------|
| Số FR | 22 (Idea: 18, Enriched: 4) |
| Issues | 4 (🔴: 1, 🟡: 3) |
| Open Questions | 3 |
| **Quality Score** | **82/100** |

---

## 1. Actors

- **Client (End User)**: Gửi request đăng nhập qua REST API (`POST /api/auth/login`)
- **Hệ thống (auth-service)**: Xử lý login command, xác thực user, generate tokens, record domain events vào event store + outbox
- **Admin**: Query audit trail, monitor login patterns, view login history
- **Downstream Consumers (system-admin-service, analytics)**: Nhận `UserLoggedInEvent` / `UserLoginFailedEvent` để audit trail, anomaly detection, SIEM integration
- **Infrastructure (Kafka, PostgreSQL, Redis)**: Event transport (Kafka), event store persistence (PostgreSQL), cache (Redis — token blacklist, session metadata)

## 2. Functional Requirements

### FR-001: Enriched UserLoggedInEvent payload [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải mở rộng payload của `UserLoggedInEvent` để bao gồm: userId, username, domainCode, domainId, ipAddress, userAgent, deviceFingerprint, deviceType, browserName, osName, isNewDevice, mfaUsed (boolean), mfaMethod (TOTP/SMS/null), loginMethod (PASSWORD/SSO/TOKEN_REFRESH), sessionPromoted (boolean), anonymousSessionId (nullable), loginAt (ISO-8601 timestamp)
- **Validation**: Tất cả required fields có giá trị non-null; loginAt theo UTC ISO-8601; deviceType là MOBILE/TABLET/DESKTOP/null

### FR-002: Hợp nhất UserLoggedInEvent với naming convention [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải migrate `UserLoggedInEvent` từ `auth.application.command.AuthDomainEvents` sang `auth.domain.event.UserLoggedInEvent` với eventType theo dot-notation convention: `iam.user.logged_in` (thay vì `USER_LOGGED_IN` hiện tại)
- **Validation**: eventType = `iam.user.logged_in`; file nằm trong `auth/domain/event/`; old eventType `USER_LOGGED_IN` không còn tồn tại

### FR-003: Event store persistence cho login events [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải persist `UserLoggedInEvent` vào bảng `evải support versioning cho login event schema — mỗi event có `schemaVersion` (integer) được wrap bởi `EventEnvelope` (đã có). Initial version = 1 cho cả `UserLoggedInEvent` và `UserLoginFailedEvent`
- **Validation**: schemaVersion = 1 trong `EventEnvelope`; backward-compatible nếu add new optional fields

### FR-010: Integrate EventService.record() trong LoginHandler [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải gọi `eventService.record()` trong `LoginHandler.handle()` SAU khi generate auth tokens thành công — tương tự RegisterHandler pattern. EventService dependency phải được inject vào LoginHandler
- **Validation**: LoginHandler gọi `eventService.record()` trước khi return `LoginResult.Success`; event record trong cùng `@Transactional`

### FR-011: Integrate EventService.record() cho failed logins trong LoginHandler [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải record `UserLoginFailedEvent` khi login thất bại — tại mỗi catch point trong LoginHandler (InvalidCredentialsException, AccountLockedException, CaptchaFailedException, etc.). Sử dụng try-catch wrapper hoặc separate method để không block exception flow
- **Validation**: Failed login event recorded trước khi exception throw; exception propagation không bị affect

### FR-012: LoginCommand mở rộng correlationId [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải thêm `correlationId: String?` vào `LoginCommand` data class. `CqrsAuthController` phải extract từ `X-Correlation-ID` header và pass vào command
- **Validation**: LoginCommand có field correlationId; CqrsAuthController truyền header value

### FR-013: Downstream consumer contract cho audit trail [IDEA]
- **Actor**: Downstream consumers (system-admin-service)
- **Action**: Hệ thống phải document rõ ràng consumer contract cho `UserLoggedInEvent` và `UserLoginFailedEvent`, bao gồm required fields, optional fields, Kafka topic, và partition strategy
- **Validation**: Consumer contracts documented; tương thích với existing `AuditLogService` pattern

### FR-014: Idempotent consumer support [IDEA]
- **Actor**: Downstream consumers
- **Action**: Hệ thống phải cung cấp deduplication key (`eventId` trong `EventEnvelope`) trong mỗi login event để downstream consumers thực hiện idempotent processing
- **Validation**: EventEnvelope.id (UUID) unique cho mỗi event; consumers có thể skip duplicate events

### FR-015: New device login event enrichment [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải enrich `UserLoggedInEvent` với `isNewDevice` flag (từ `LoginSessionService.detectNewDe

Không phát hiện trùng lặp giữa các FR. FR-003 và FR-004 bổ sung cho nhau (event store vs outbox) — không trùng. FR-010 và FR-011 khác nhau rõ ràng (success vs failed login path).

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-019** [ENRICHED]: Idempotency cho login event recording — đảm bảo retry safety, pattern chuẩn Event Sourcing
- **FR-020** [ENRICHED]: Full transaction logging — observability requirement cơ bản cho production
- **FR-021** [ENRICHED]: Timeout handling cho EventService — resilience pattern, login flow không bị block
- **FR-022** [ENRICHED]: Retry mechanism — leverage existing OutboxPoller retry, đảm bảo eventual consistency

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Kafka | Event transport — relay login events từ outbox | Existing: `OutboxPoller`, `KafkaEventPublisher` |
| PostgreSQL | Event store persistence — `event_store` table | Existing: `EventStorePort`, `EventStorePersistenceAdapter` |
| PostgreSQL | Outbox table — `event_outbox` table | Existing: `OutboxPort`, `OutboxPersistenceAdapter` |
| Redis | Token blacklist, session metadata | Existing: không thay đổi |

## 6. Assumptions

- ⚠️ Assumption: `LoginHandler` đang chạy trong `@Transactional` boundary → `EventService.record()` sẽ participate trong cùng transaction — đã xác nhận qua code (`@Transactional` annotation trên `handle()` method)
- ⚠️ Assumption: `EventService.record()` có thể handle high-frequency login events mà không gây performance bottleneck — dựa trên pattern tương tự đã hoạt động cho `RegisterHandler`
- ⚠️ Assumption: `OutboxPoller` polling interval (100ms default) đủ cho login event throughput — cần monitor sau deploy
- ⚠️ Assumption: Existing `event_store` indexes đủ hiệu quả cho query by `event_type = 'iam.user.logged_in'` — cần verify index strategy

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-016: MFA checkpoint timing ("chỉ emit cho final successful login") cần clarify cho MFA verify flow path |
| Đầy đủ (Completeness) | 21/25 | FR-007: UserLoginFailedEvent aggregateId strategy chưa rõ (userId unknown cho non-existent users); FR-016: MFA verify handler chưa được address |
| Nhất quán (Consistency) | 22/25 | FR-002: eventType naming `iam.user.logged_in` vs existing `USER_LOGGED_IN` — migration path cần document |
| Kiểm thử được (Testability) | 17/25 | FR-019: Idempotency testing khó verify; FR-021: Timeout simulation cần mock; nhiều FR thiếu exact acceptance criteria |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -3 | FR-016 | "Chỉ emit UserLoggedInEvent cho final successful login" — cần clarify MFA verify handler nào sẽ emit event | Specify MfaVerifyHandler hoặc post-MFA callback |
| 2 | Completeness | -2 | FR-007 | UserLoginFailedEvent cần aggregateId nhưng userId unknown cho non-existent users | Dùng hash(username) hoặc 0 cho unknown users |
| 3 | Completeness | -2 | FR-016 | MFA verify handler (`MfaService.verifyTotp()`) chưa được address — event phải emit sau MFA verify success | Thêm FR hoặc mở rộng FR-016 scope |
| 4 | Consistency | -3 | FR-002 | Migration từ `USER_LOGGED_IN` → `iam.user.logged_in` cần migration plan cho existing consumers | Document migration path rõ ràng |
| 5 | Testability | -3 | FR-019 | Idempotency test scenario chưa rõ — cần define test case "retry same login" | Define acceptance test cases cụ thể |
| 6 | Testability | -3 | FR-021 | Timeout scenario khó reproduce — cần mock strategy | Document mock approach cho integration test |
| 7 | Testability | -2 | Multiple | Nhiều FR thiếu measurable acceptance criteria | Thêm "khi X thì Y trong Z ms" format |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | MFA flow path: `LoginHandler` return `MfaRequired` trước khi login hoàn tất → `UserLoggedInEvent` chỉ nên emit sau MFA verify thành công. Nhưng MFA verify xử lý trong `MfaService.verifyTotp()` / `MfaController` — không phải `LoginHandler`. Cần extend scope hoặc add separate event recording point. | FR-016 | Thêm `eventService.record()` trong MFA verify success path (MfaService hoặc MfaController) |
| 2 | Risk | 🟡 | Failed login event aggregateId: khi username không tồn tại, userId = unknown → aggregateId strategy? Dùng 0 hoặc hash(username) có thể gây issues cho event store querying. | FR-007 | Dùng aggregateId = 0 cho unknown users, thêm username field cho querying |
| 3 | Risk | 🟡 | LoginHandler hiện tại KHÔNG inject EventService — cần modify constructor. Đây là EXTEND change trên existing handler có nhiều dependencies (12 constructor params). | FR-010 | Inject EventService, keep constructor manageable (13 params max cho handler) |
| 4 | Warning | 🟡 | eventType migration `USER_LOGGED_IN` → `iam.user.logged_in`: nếu có existing Spring `@EventListener` subscribers listening trên old eventType → cần backward compatibility period hoặc dual-emit. | FR-002 | Check existing listeners trước migration; keep `UserLoggedInEvent` class backward-compatible |

## 9. Open Questions

- **OQ-1**: MFA verify flow — nên emit `UserLoggedInEvent` trong `MfaService.verifyTotp()` hay tạo separate `MfaVerifiedEvent`? (ảnh hưởng FR-016)
- **OQ-2**: Failed login event cho non-existent users — aggregateId strategy? (0, -1, hash(username)?) (ảnh hưởng FR-007)
- **OQ-3**: Có cần backward compatibility period cho eventType migration `USER_LOGGED_IN` → `iam.user.logged_in` hay break existing consumers? (ảnh hưởng FR-002)

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication — Login/Authentication Events (Event Sourcing layer)

### 10.2 Flow Type
Command — Login command mutates state via domain events (CQRS write-side)

### 10.3 Candidate Services
- **auth-service (auth module)**: Primary — contains LoginHandler, AuthDomainEvents (UserLoggedInEvent), EventService, LoginSessionService, LoginCommand, CqrsAuthController login endpoint. Evidence: keyword `UserLoggedInEvent` in `AuthDomainEvents.kt`, `LoginHandler.kt`, `EventService.kt`
- **auth-service (shared module)**: Supporting — exception classes, error codes, config. Evidence: `AuthErrorCode.kt`, `GlobalExceptionHandler.kt`

### Detection Evidence
- Keyword: `UserLoggedInEvent` → Module: `auth.application.command.AuthDomainEvents` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
- Keyword: `LoginHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Keyword: `LoginSessionService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
- Keyword: `UserRegisteredEvent` (reference pattern) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
- Keyword: `LoginCommand` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`

### 10.4 External Integrations
- **Kafka**: Outbox relay via `OutboxPoller` → topics `iam.user.logged_in`, `iam.user.login_failed`
- **PostgreSQL**: Event store (`event_store` table), Outbox (`event_outbox` table)
- **Redis**: Không thay đổi — existing token blacklist, session metadata

### 10.5 Required Modules
- `auth.domain.event` — new `UserLoggedInEvent.kt`, `UserLoginFailedEvent.kt` domain event classes
- `auth.application.command` — modify `LoginHandler.kt` (inject EventService, record events), modify `LoginCommand.kt` (add correlationId)
- `auth.application.event` — reuse existing `EventService.kt` (no modification needed)
- `auth.adapter.in.web` — modify `CqrsAuthController.kt` (pass correlationId to LoginCommand)
- `auth.adapter.out.event` — reuse existing `OutboxPoller.kt` (no modification needed)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi `POST /api/auth/login` với credentials + device info | CqrsAuthController |
| 2 | CqrsAuthController | Extract IP, User-Agent, Device-Fingerprint, X-Correlation-ID → build LoginCommand | CqrsAuthController |
| 3 | LoginHandler | Validate credentials, rate limit, CAPTCHA, password expiry, MFA check | LoginHandler |
| 4a (success) | LoginHandler | Generate auth tokens → call `eventService.record(UserLoggedInEvent)` → return LoginResult.Success | EventService, EventStorePort, OutboxPort |
| 4b (fail) | LoginHandler | Catch exception → call `eventService.record(UserLoginFailedEvent)` → re-throw exception | EventService |
| 5 | OutboxPoller | Async: poll `event_outbox` → relay to Kafka topic `iam.user.logged_in` / `iam.user.login_failed` | OutboxPoller, KafkaTemplate |
| 6 | Downstream | Consume events from Kafka topics → audit trail, anomaly detection, session analytics | system-admin-service |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Enriched payload | TBD | `UserLoggedInEvent.kt` (NEW) | Pending |
| FR-002 | Naming convention | TBD | `AuthDomainEvents.kt` [MODIFY], `UserLoggedInEvent.kt` (NEW) | Pending |
| FR-003 | Event store persistence | TBD | `LoginHandler.kt` [MODIFY], `EventService.kt` [REUSE] | Pending |
| FR-004 | Transactional outbox | TBD | `LoginHandler.kt` [MODIFY], `EventService.kt` [REUSE] | Pending |
| FR-005 | Kafka topic | TBD | `OutboxPoller.kt` [REUSE] | Pending |
| FR-006 | Correlation ID | TBD | `LoginCommand.kt` [MODIFY], `CqrsAuthController.kt` [MODIFY] | Pending |
| FR-007 | Failed login event | TBD | `UserLoginFailedEvent.kt` (NEW), `LoginHandler.kt` [MODIFY] | Pending |
| FR-008 | Failed event Kafka | TBD | `OutboxPoller.kt` [REUSE] | Pending |
| FR-009 | Schema versioning | TBD | `EventEnvelope.kt` [REUSE] | Pending |
| FR-010 | EventService integration | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-011 | Failed event recording | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-012 | LoginCommand extend | TBD | `LoginCommand.kt` [MODIFY], `CqrsAuthController.kt` [MODIFY] | Pending |
| FR-013 | Consumer contract | TBD | Documentation only | Pending |
| FR-014 | Idempotent consumer | TBD | `EventEnvelope.kt` [REUSE] | Pending |
| FR-015 | New device enrichment | TBD | `UserLoggedInEvent.kt` (NEW), `LoginHandler.kt` [MODIFY] | Pending |
| FR-016 | MFA context | TBD | `UserLoggedInEvent.kt` (NEW), `LoginHandler.kt` [MODIFY] | Pending |
| FR-017 | Session promotion | TBD | `UserLoggedInEvent.kt` (NEW), `LoginHandler.kt` [MODIFY] | Pending |
| FR-018 | Metadata enrichment | TBD | `EventEnvelope.kt` [REUSE] | Pending |
| FR-019 | Idempotency | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-020 | Transaction logging | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-021 | Timeout handling | TBD | `LoginHandler.kt` [MODIFY] | Pending |
| FR-022 | Retry mechanism | TBD | `OutboxPoller.kt` [REUSE] | Pending |

### Change Impact Map (EXTEND)

```
FR-001 → [ADD] UserLoggedInEvent.kt (src/main/kotlin/com/ntt/authservice/auth/domain/event/) → NEW domain event
FR-002 → [MODIFY] AuthDomainEvents.kt (src/main/kotlin/.../auth/application/command/) → remove old UserLoggedInEvent
FR-003 → [MODIFY] LoginHandler.kt (src/main/kotlin/.../auth/application/command/) → add eventService.record()
FR-004 → [REUSE] EventService.kt, OutboxPort → no changes needed
FR-005 → [REUSE] OutboxPoller.kt → no changes needed (topic driven by event data)
FR-006 → [MODIFY] LoginCommand.kt (src/main/kotlin/.../auth/application/command/) → add correlationId field
FR-006 → [MODIFY] CqrsAuthController.kt (src/main/kotlin/.../auth/adapter/in/web/) → pass X-Correlation-ID
FR-007 → [ADD] UserLoginFailedEvent.kt (src/main/kotlin/com/ntt/authservice/auth/domain/event/) → NEW domain event
FR-010 → [MODIFY] LoginHandler.kt → inject EventService dependency
FR-011 → [MODIFY] LoginHandler.kt → add failed event recording
FR-012 → [MODIFY] LoginCommand.kt → add correlationId field
FR-015 → [MODIFY] LoginHandler.kt → extract isNewDevice from LoginSessionService
FR-016 → [MODIFY] LoginHandler.kt → include MFA context
FR-017 → [MODIFY] LoginHandler.kt → include promotion context
FR-019 → [MODIFY] LoginHandler.kt → add dedup guard
FR-020 → [MODIFY] LoginHandler.kt → add structured logging
FR-021 → [MODIFY] LoginHandler.kt → add try-catch around eventService.record()
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
- Feature này là **mirror pattern** của `user_registration_event` (đã implement thành công, archived `2026-08-20-user_registration_event/`). Pattern: enriched domain event → EventService.record() → event store + outbox → Kafka. Complexity: MEDIUM.
- `LoginHandler` hiện có 12 constructor dependencies — thêm `EventService` sẽ là 13. Vẫn acceptable cho CQRS handler nhưng nên monitor.
- **Critical gap**: `LoginHandler` hiện tại KHÔNG record bất kỳ domain event nào vào event store. Chỉ có `LoginSessionService.recordLogin()` (writes to `login_sessions` table) và minimal `UserLoggedInEvent` qua Spring ApplicationEvent (in-process only). Đây là gap lớn trong audit trail completeness.
- `UserLoggedInEvent` hiện tại trong `AuthDomainEvents.kt` chỉ có 2 fields (userId, domainCode) và eventType = `USER_LOGGED_IN` — không follow dot-notation convention như `UserRegisteredEvent` (`iam.user.registered`).
- MFA flow là complexity point: LoginHandler return `MfaRequired` result (partial login) → MFA verify xảy ra trong `MfaService.verifyTotp()` / `MfaController.verifyMfa()`. Event phải emit ở MFA verify success point, không phải LoginHandler.

### Related Features / Precedents
- `user_registration_event` (archived: `2026-08-20-user_registration_event/`) — **PRIMARY reference**. Enriched `UserRegisteredEvent`, EventService integration trong RegisterHandler, outbox + Kafka relay. Exact same pattern cần follow.
- `auth-core-features` (archived: `2026-08-21-auth-core-features/`) — LoginHandler CQRS extraction, LoginSessionService, LoginRateLimitService.
- `auth-login-admin` (archived: `2026-08-11-auth-login-admin/`) — Login + admin session patterns.
- `anonymous-login-optimization` (archived: `2026-08-20-anonymous-login-optimization/`) — Anonymous session + Redis pattern, session promotion flow.

### Integration Notes
- **EventService** (reuse): Không cần modify. `record()` method generic, accept any `DomainEvent`. Call pattern: `eventService.record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)`.
- **OutboxPoller** (reuse): Không cần modify. Polls `event_outbox` table, relay to Kafka by topic field.
- **LoginSessionService** (read-only): Cần call `detectNewDevice()` hoặc get `isNewDevice` from recorded session để enrich event.
- **KafkaConfig** (no change): Topic auto-create enabled by default in dev, manual creation in prod.

### Suggested Approach
1. **Phase 1**: Create `UserLoggedInEvent.kt` và `UserLoginFailedEvent.kt` trong `auth.domain.event` package — follow `UserRegisteredEvent` pattern exactly.
2. **Phase 2**: Modify `LoginCommand.kt` — add `correlationId: String?` field.
3. **Phase 3**: Modify `CqrsAuthController.kt` — extract `X-Correlation-ID` header, pass to LoginCommand.
4. **Phase 4**: Modify `LoginHandler.kt` — inject `EventService`, record `UserLoggedInEvent` after token generation, record `UserLoginFailedEvent` in catch blocks.
5. **Phase 5**: Clean up `AuthDomainEvents.kt` — remove old `UserLoggedInEvent` definition (keep `SessionRevokedEvent`).
6. **Phase 6**: Address MFA flow — add event recording in MFA verify success path (scope extension).
7. **Phase 7**: Tests — unit tests cho event recording, integration tests cho outbox relay.

### Context from Confluence Images
N/A
