# Pre-OpenSpec: user-registration-event

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (research artifacts — business_analysis.md + technical_spec.md)
> **Classification Evidence**: keyword `UserRegisteredEvent` → module `auth.domain.event` → file `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`; keyword `RegisterHandler` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`; keyword `EventService` → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`; keyword `LoginEventRecorder` (pattern reference) → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt`
> **Archive**: `openspec/changes/archive/2026-08-20-user_registration_event/pre_openspec.md`
> **Previous Version**: Archived pre_openspec had 22 FRs (Idea: 18, Enriched: 4). This version refines scope based on research findings — narrows to actionable FRs aligned with existing codebase patterns.
> **Quality Score**: 90/100
> **Mode**: DELTA (archive exists + input refined by research)

## 📋 Feature Summary

Nâng cấp hệ thống ghi nhận sự kiện đăng ký người dùng trong auth-service. Hiện tại, `RegisterHandler` gọi trực tiếp `EventService.record()` để ghi `UserRegisteredEvent` — thiếu fire-and-forget error handling (nếu event recording thất bại, toàn bộ registration transaction rollback). Tính năng này bổ sung: (1) `RegistrationEventRecorder` helper service theo pattern `LoginEventRecorder`/`TokenEventRecorder`, (2) `UserRegistrationFailedEvent` domain event cho security monitoring, (3) `RegistrationFailureReason` enum phân loại lý do thất bại. Estimated effort: 2-3 developer-days.

| Metric | Giá trị |
|--------|---------|
| Số FR | 12 (URD: 9, Enriched: 2) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 2 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **End User**: Người dùng đăng ký tài khoản mới qua POST /api/auth/register
- **Hệ thống (RegisterHandler)**: CQRS command handler xử lý registration, tạo user, ghi nhận domain events
- **Hệ thống (RegistrationEventRecorder)**: [NEW] Helper service ghi nhận registration events với fire-and-forget error handling
- **Hệ thống (EventService)**: Central event recording service — persist event store + outbox
- **Hệ thống (OutboxPoller)**: Async relay events từ outbox ra Kafka
- **Downstream Services**: account-service, notification-service, analytics — consume `iam.user.registered` từ Kafka
- **Security Monitor (SIEM)**: Consume `iam.user.registration_failed` từ Kafka
- **Admin**: Query event store qua GET /api/internal/events/User/{id}

## 2. Functional Requirements

### FR-001: RegistrationEventRecorder helper service [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải có `RegistrationEventRecorder` helper service encapsulating event recording logic với fire-and-forget error handling, theo pattern `LoginEventRecorder`/`TokenEventRecorder`
- **Validation**: Class annotated `@Component`; inject `EventService`; mỗi method có try/catch; log.debug on success; log.warn on failure; exception không propagate ra caller
- **Source**: UC-001 (Record Successful Registration Event), UC-002 (Record Failed Registration Event) — business_analysis.md

### FR-002: Fire-and-forget registration success event recording [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải ghi nhận `UserRegisteredEvent` qua `RegistrationEventRecorder.recordRegistrationSuccess()` thay vì gọi trực tiếp `EventService.record()` trong `RegisterHandler` — nếu event recording thất bại, user creation vẫn thành công
- **Validation**: RegisterHandler delegate sang RegistrationEventRecorder; EventService.record() failure không gây rollback user creation; log.warn emitted on failure
- **Source**: UC-001, EF-001 — business_analysis.md

### FR-003: UserRegistrationFailedEvent domain event [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `UserRegistrationFailedEvent` domain event (data class implements `DomainEvent`) ghi nhận registration failures với các fields: usernameAttempted, emailAttempted, domainCode, failureReason (enum), errorMessage, ipAddress, userAgent
- **Validation**: Event implements DomainEvent interface; eventType = "iam.user.registration_failed"; KHÔNG chứa password; tất cả fields đúng type
- **Source**: UC-002 — business_analysis.md, technical_spec.md Section 2.2

### FR-004: RegistrationFailureReason enum [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `RegistrationFailureReason` enum phân loại lý do registration thất bại: DUPLICATE_USERNAME, DUPLICATE_EMAIL, INVALID_DOMAIN, WEAK_PASSWORD, VALIDATION_ERROR, UNKNOWN
- **Validation**: Enum có đúng 6 giá trị; mỗi giá trị có KDoc documentation; theo pattern `LoginFailureReason`
- **Source**: UC-002, BR-006 — business_analysis.md, technical_spec.md Section 2.2

### FR-005: Registration failure event recording [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải ghi nhận `UserRegistrationFailedEvent` qua `RegistrationEventRecorder.recordRegistrationFailure()` khi registration thất bại (DuplicateResourceException, ResourceNotFoundException, etc.) — fire-and-forget, KHÔNG ảnh hưởng error response gốc
- **Validation**: Catch blocks trong RegisterHandler gọi recordRegistrationFailure() trước khi rethrow exception; aggregateId = 0L cho unknown users; original exception vẫn rethrow
- **Source**: UC-002, EF-001, BR-007, BR-008 — business_analysis.md

### FR-006: Failure reason classification mapping [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải map exception types sang RegistrationFailureReason: `DuplicateResourceException("User", "username", ...)` → DUPLICATE_USERNAME; `DuplicateResourceException("User", "email", ...)` → DUPLICATE_EMAIL; `ResourceNotFoundException("Domain", ...)` → INVALID_DOMAIN; other exceptions → UNKNOWN
- **Validation**: Mỗi exception type trong RegisterHandler có mapping tương ứng; UNKNOWN cho unexpected exceptions
- **Source**: UC-002, Data Transformation Rules #4-6 — technical_spec.md Section 3.3

### FR-007: Kafka topic cho registration failed events [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải publish `UserRegistrationFailedEvent` ra Kafka topic `iam.user.registration_failed` qua outbox pattern (partition key = usernameAttempted)
- **Validation**: Outbox entry có topic = "iam.user.registration_failed"; partition key = usernameAttempted; event đi qua OutboxPoller → Kafka
- **Source**: UC-004, FR-007 — business_analysis.md

### FR-008: Password exclusion from events [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải đảm bảo KHÔNG BAO GIỜ bao gồm password hoặc passwordHash trong bất kỳ event payload nào (cả success và failure events)
- **Validation**: Code review: UserRegisteredEvent không có password field; UserRegistrationFailedEvent không có password field; RegisterCommand.password KHÔNG mapped vào event
- **Source**: Security Considerations Section 7.1 — technical_spec.md

### FR-009: Enriched event payload preservation [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải bảo toàn enriched payload hiện tại của `UserRegisteredEvent` (11 fields: userId, username, email, fullName, phone, domainCode, domainId, status, registrationSource, ipAddress, userAgent) khi refactor sang RegistrationEventRecorder
- **Validation**: Tất cả 11 fields vẫn được populate đúng giá trị; eventType = "iam.user.registered"; topic = "iam.user.registered"
- **Source**: FR-001, FR-003 — business_analysis.md, technical_spec.md Section 2.2

### FR-010: Idempotency cho registration [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải đảm bảo idempotency cho event recording — duplicate EventService.record() calls không tạo duplicate events trong event store (leveraging existing EventEnvelope UUID id)
- **Validation**: EventEnvelope.id unique per event; downstream consumers có thể dedup bằng event id

### FR-011: Full transaction logging cho registration [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải log toàn bộ registration lifecycle: REGISTER_START, REGISTER_VALIDATION_PASSED, REGISTER_USER_PERSISTED, REGISTER_EVENT_RECORDED, REGISTER_COMPLETE — với correlationId threading
- **Validation**: Mỗi step có structured log entry; correlationId consistent; log level = debug cho normal flow, warn cho errors

## 3. Non-functional Requirements

- **Performance**: Event recording overhead < 20ms (95th percentile); fire-and-forget pattern đảm bảo 0% impact on registration latency khi event recording failure
- **Reliability**: At-least-once event delivery qua transactional outbox pattern (existing infrastructure)
- **Availability**: Registration KHÔNG blocked bởi event infrastructure failure — fire-and-forget mandatory
- **Security**: Password NEVER included trong event payloads; PII (email, phone, IP) follows existing event_store retention policy
- **Observability**: Structured logging cho success/failure event recording; metrics cho event recording success/failure rate

---

## 4. Deduplicated & Consolidated

[CHANGED] So với archive version (22 FRs), đã loại bỏ các FR đã được implement (event store persistence FR-004, outbox pattern FR-007, CloudEvents envelope FR-006, correlation ID FR-005, Kafka topic design FR-010) — những FR này đã có trong codebase hiện tại (EventService, EventEnvelope, OutboxPoller). Giữ lại và refine các FR liên quan đến RegistrationEventRecorder, UserRegistrationFailedEvent, RegistrationFailureReason — phần còn thiếu.

[REMOVED] FR-009 (event replay), FR-011 (CQRS read-model), FR-018 (event snapshot) — out of scope cho feature này theo research findings.
[REMOVED] FR-002 (consolidate duplicate events) — đã resolve trong codebase hiện tại (chỉ còn 1 UserRegisteredEvent tại auth.domain.event).
[REMOVED] FR-003 (schema versioning), FR-006 (CloudEvents), FR-008 (idempotent consumer), FR-014 (event metadata) — đã implement qua EventEnvelope.
[REMOVED] FR-015 (DLT), FR-016 (cache invalidation), FR-017 (eventsourcing-utils integration) — đã implement hoặc out of scope.

Không phát hiện trùng lặp trong bộ FR mới.

## 5. Enriched Domain Requirements

Enriched FRs: 2 (FR-010, FR-011) — trong giới hạn min(5, ceil(11 × 0.20)) = min(5, 3) = 3

### Enriched FRs

- **FR-010**: Idempotency cho event recording — đảm bảo duplicate calls không tạo duplicate events. EventEnvelope.id (UUID) cung cấp dedup key. Cần thiết vì fire-and-forget pattern có thể gây retry scenarios.
- **FR-011**: Full transaction logging — structured logging cho registration lifecycle. Cần thiết cho production monitoring và troubleshooting. RegisterHandler đã có logging cơ bản — cần ensure consistency khi refactor.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Kafka | Event publishing via outbox | Topics: iam.user.registered (existing), iam.user.registration_failed (new) |
| PostgreSQL | Event store + outbox persistence | Tables: event_store, event_outbox (existing) |
| EventService | Internal event recording pipeline | Existing — EventService.record() → event_store + outbox |
| OutboxPoller | Async Kafka relay | Existing — polls event_outbox → publish to Kafka |

## 6. Assumptions

- ⚠️ Assumption: `RegistrationEventRecorder` sẽ follow pattern CHÍNH XÁC như `LoginEventRecorder` — @Component, inject EventService, try/catch all calls, log.debug/log.warn — Lý do: consistency across event recorders, confirmed by research technical_spec.md
- ⚠️ Assumption: `RegisterHandler` hiện tại gọi trực tiếp `EventService.record()` — refactor sang delegate qua `RegistrationEventRecorder.recordRegistrationSuccess()` — Lý do: verified by reading RegisterHandler.kt line 83-99
- ⚠️ Assumption: Failure events sử dụng `aggregateId = 0L` cho unknown users (user chưa tồn tại khi registration fail) — Lý do: follows LoginEventRecorder pattern cho login failure events
- ⚠️ Assumption: RegistrationFailureReason enum covers 6 failure types dựa trên exception types hiện tại trong RegisterHandler — Lý do: DuplicateResourceException (username/email), ResourceNotFoundException (domain), và fallback UNKNOWN

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-006: "other exceptions" trong failure reason mapping hơi vague — nên list rõ exception types |
| Đầy đủ (Completeness) | 23/25 | FR-005: Chưa specify rõ TẤT CẢ exception types cần catch trong RegisterHandler |
| Nhất quán (Consistency) | 25/25 | Tất cả FRs consistent với existing patterns (LoginEventRecorder, UserLoginFailedEvent, LoginFailureReason) |
| Kiểm thử được (Testability) | 18/25 | FR-002: "fire-and-forget" testability criteria có thể rõ hơn — cần mock EventService to throw and verify user still created |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -1 | FR-006 | "other exceptions → UNKNOWN" — không list rõ exception types ngoài DuplicateResourceException, ResourceNotFoundException | List explicitly: PasswordPolicyViolationException → WEAK_PASSWORD, other → UNKNOWN |
| 2 | Completeness | -2 | FR-005 | Chưa specify rõ tất cả catch blocks cần thêm vào RegisterHandler | Document: catch DuplicateResourceException, ResourceNotFoundException, Exception (fallback) |
| 3 | Testability | -7 | FR-002 | "fire-and-forget" test criteria: cần specify: mock EventService.record() throws → verify user persisted + log.warn emitted | Add test scenarios: 1) EventService throws → user created OK; 2) success → event in store |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | RegisterHandler hiện tại gọi EventService.record() trong @Transactional boundary — refactor sang fire-and-forget cần đảm bảo exception handling không break existing transaction semantics | FR-002 | RegistrationEventRecorder wraps EventService.record() trong try/catch — nếu fail, transaction vẫn commit (user created, event NOT persisted) |
| 2 | Risk | 🟡 | Failure event recording tạo SEPARATE transaction context vì user creation transaction đã rollback khi exception thrown — cần verify RegistrationEventRecorder.recordRegistrationFailure() works outside original transaction | FR-005 | recordRegistrationFailure() gọi trong catch block TRƯỚC rethrow — EventService.record() cần new transaction hoặc fire-and-forget async |

> [CHANGED] vs Archive: Archive có 4 issues (1 🔴, 3 🟡). Issue 🔴 (duplicate UserRegisteredEvent) đã resolved trong codebase hiện tại. 2 issues mới phản ánh refactoring concerns.

## 9. Open Questions

- **OQ-001**: Khi RegisterHandler.handle() throws exception (e.g., DuplicateResourceException), transaction sẽ rollback — lúc đó RegistrationEventRecorder.recordRegistrationFailure() có nên gọi trong cùng transaction hay tạo transaction riêng? → ⚠️ Assumption: gọi trong catch block trước rethrow, EventService.record() trong cùng TX context sẽ bị rollback → cần `@Transactional(propagation = REQUIRES_NEW)` hoặc fire-and-forget async
- **OQ-002**: Có nên thêm geo-location data (country, city) vào UserRegisteredEvent trong tương lai không? (Hiện tại không có geo-IP service) → Deferred: không block current feature

> [CHANGED] vs Archive: Archive có 3 OQs về aggregate boundary, event replay strategy, consumer contract ownership — tất cả resolved hoặc out of scope.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Authorization — User Registration Event Recording (EXTEND existing event infrastructure)

### 10.2 Flow Type

Command — RegisterCommand → RegisterHandler → RegistrationEventRecorder → EventService → event_store + outbox → OutboxPoller → Kafka

### 10.3 Candidate Services
- **auth-service (auth.application.event)**: Pattern reference `LoginEventRecorder`, `TokenEventRecorder` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt`, `TokenEventRecorder.kt`. NEW: `RegistrationEventRecorder`
- **auth-service (auth.domain.event)**: Domain events — existing `UserRegisteredEvent`, pattern refs `UserLoginFailedEvent`, `LoginFailureReason` → NEW: `UserRegistrationFailedEvent`, `RegistrationFailureReason`
- **auth-service (auth.application.command)**: Caller — `RegisterHandler.kt` → MODIFY: replace direct EventService.record() with RegistrationEventRecorder delegation + add failure event recording

### Detection Evidence
- Keyword: `UserRegisteredEvent` → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
- Keyword: `RegisterHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Keyword: `LoginEventRecorder` (pattern) → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt`
- Keyword: `UserLoginFailedEvent` (pattern) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt`
- Keyword: `LoginFailureReason` (pattern) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt`
- Keyword: `TokenEventRecorder` (pattern) → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`

### 10.4 External Integrations

| Integration | Type | Evidence |
|-------------|------|----------|
| Kafka | MQ | `OutboxPoller` → `KafkaTemplate<String, String>` — `src/main/kotlin/.../adapter/out/event/OutboxPoller.kt` |
| PostgreSQL | DB | `EventStorePersistenceAdapter` → JPA — `src/main/kotlin/.../adapter/out/persistence/EventStorePersistenceAdapter.kt` |
| PostgreSQL | DB | `OutboxPersistenceAdapter` → JPA — `src/main/kotlin/.../adapter/out/persistence/OutboxPersistenceAdapter.kt` |

### 10.5 Required Modules

- `auth.application.event` — EventService (existing), RegistrationEventRecorder (NEW)
- `auth.domain.event` — UserRegisteredEvent (existing, no change), UserRegistrationFailedEvent (NEW), RegistrationFailureReason (NEW)
- `auth.application.command` — RegisterHandler (MODIFY), RegisterCommand (existing, no change)
- `auth.application.port.out` — DomainEvent (existing interface), EventStorePort (existing), OutboxPort (existing)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Gửi POST /api/auth/register | CqrsAuthController |
| 2 | CqrsAuthController | Tạo RegisterCommand từ request DTO | CqrsAuthController |
| 3 | RegisterHandler | Validate uniqueness (username, email) | UserPort |
| 4 | RegisterHandler | Validate domain exists | DomainPort |
| 5 | RegisterHandler | Create User domain object, persist via UserPort.save() | UserPersistenceAdapter → PostgreSQL |
| 6 | RegisterHandler | [CHANGED] Delegate event recording sang RegistrationEventRecorder.recordRegistrationSuccess() | RegistrationEventRecorder |
| 7 | RegistrationEventRecorder | [NEW] try/catch: EventService.record(UserRegisteredEvent, topic=iam.user.registered) | EventService → event_store + outbox |
| 8 | RegisterHandler | Generate auth tokens via TokenGenerator | TokenGenerator |
| 9 | RegisterHandler | Anonymous session promotion (best-effort) | SessionPromotionService |
| 10 | RegisterHandler | Return RegisterResult.Success | CqrsAuthController → 201 Created |
| 11 | OutboxPoller | [EXISTING] Async poll outbox → publish to Kafka | OutboxPoller → Kafka |
| EF-1 | RegisterHandler | [NEW] catch DuplicateResourceException → RegistrationEventRecorder.recordRegistrationFailure() → rethrow | RegistrationEventRecorder |
| EF-2 | RegisterHandler | [NEW] catch ResourceNotFoundException → RegistrationEventRecorder.recordRegistrationFailure() → rethrow | RegistrationEventRecorder |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001, UC-002 (business_analysis.md) | Section 9.1 (technical_spec.md) | `RegistrationEventRecorder` (auth.application.event) | [ADD] Mapped |
| FR-002 | UC-001, EF-001 (business_analysis.md) | Section 9.5 (technical_spec.md) | `RegisterHandler` (auth.application.command) | [MODIFY] Mapped |
| FR-003 | UC-002, Section 2.2 (technical_spec.md) | Section 9.4 (technical_spec.md) | `UserRegistrationFailedEvent` (auth.domain.event) | [ADD] Mapped |
| FR-004 | UC-002, BR-006 (business_analysis.md) | Section 2.2 (technical_spec.md) | `RegistrationFailureReason` (auth.domain.event) | [ADD] Mapped |
| FR-005 | UC-002, BR-007 (business_analysis.md) | Section 9.5 (technical_spec.md) | `RegisterHandler` (auth.application.command) | [MODIFY] Mapped |
| FR-006 | Section 3.3 (technical_spec.md) | Section 9.5 (technical_spec.md) | `RegisterHandler` (auth.application.command) | [MODIFY] Mapped |
| FR-007 | UC-004 (business_analysis.md) | Section 6.2 (technical_spec.md) | `RegistrationEventRecorder` (auth.application.event) | [ADD] Mapped |
| FR-008 | Section 7.1 (technical_spec.md) | Section 7.1 (technical_spec.md) | `UserRegistrationFailedEvent` (auth.domain.event) | [ADD] Mapped |
| FR-009 | FR-001, FR-003 (business_analysis.md) | Section 2.2 (technical_spec.md) | `RegisterHandler` (auth.application.command) | [MODIFY] Mapped |
| FR-010 | Enriched | N/A | `EventEnvelope` (auth.domain.event) | [REUSE] Mapped |
| FR-011 | Enriched | N/A | `RegisterHandler` (auth.application.command) | [MODIFY] Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

Tính năng này có **độ phức tạp thấp-trung bình** — chỉ cần tạo 3 classes mới và modify 1 class hiện tại, tất cả theo patterns ĐÃ CÓ trong codebase. So với archive version (22 FRs, high complexity), research phase đã narrow scope significantly dựa trên phân tích codebase thực tế — phần lớn infrastructure đã implement (EventService, EventEnvelope, event_store, outbox, OutboxPoller).

**Key insight từ research**: Codebase đã evolve đáng kể so với thời điểm archive — duplicate `UserRegisteredEvent` đã resolved, EventService + outbox pattern đã implement. Chỉ còn thiếu fire-and-forget wrapper (RegistrationEventRecorder) và failure event (UserRegistrationFailedEvent).

**Rủi ro chính**: Transaction semantics khi recording failure events — RegisterHandler.handle() là `@Transactional`, khi exception thrown thì TX rollback. Failure event recording cần xảy ra TRƯỚC hoặc trong context có thể persist.

### Related Features / Precedents

- **user-login-event** (archive: `openspec/changes/archive/2026-08-24-user-login-event/`): CHÍNH XÁC cùng pattern — LoginEventRecorder + UserLoggedInEvent + UserLoginFailedEvent + LoginFailureReason. ĐÂY LÀ template reference chính.
- **jwt-token-validation** (archive: `openspec/changes/archive/2026-08-26-jwt-token-validation/`): TokenEventRecorder pattern — fire-and-forget event recording
- **Existing code patterns**:
  - `LoginEventRecorder.kt` — `@Component`, `EventService` injection, `try/catch` + `log.debug`/`log.warn`
  - `UserLoginFailedEvent.kt` — `data class` + `DomainEvent` interface + `eventType` override
  - `LoginFailureReason.kt` — `enum class` with KDoc documentation

### Integration Notes

- **EventService.record()**: Existing method — takes `aggregateType`, `aggregateId`, `event`, `topic`, `partitionKey`, `correlationId`. Wraps in `EventEnvelope`, persists to event_store + outbox trong cùng TX.
- **Kafka topics**: `iam.user.registered` (existing) — partition key = userId. `iam.user.registration_failed` (NEW) — partition key = usernameAttempted.
- **No new API endpoints**: Tính năng này chỉ modify internal event recording flow. External API (POST /api/auth/register) KHÔNG thay đổi contract.
- **No database migrations**: event_store + event_outbox tables handle all event types generically via JSON payload.

### Suggested Approach

1. **Tạo `RegistrationFailureReason` enum** — follow `LoginFailureReason` pattern exactly. File: `auth/domain/event/RegistrationFailureReason.kt`
2. **Tạo `UserRegistrationFailedEvent` data class** — follow `UserLoginFailedEvent` pattern. File: `auth/domain/event/UserRegistrationFailedEvent.kt`
3. **Tạo `RegistrationEventRecorder` @Component** — follow `LoginEventRecorder` pattern EXACTLY. File: `auth/application/event/RegistrationEventRecorder.kt`
4. **Modify `RegisterHandler`** — inject `RegistrationEventRecorder`, replace direct EventService.record() call, add catch blocks for failure events

**Recommended pattern references (copy structure, adapt content)**:
- `LoginEventRecorder.kt` → `RegistrationEventRecorder.kt`
- `UserLoginFailedEvent.kt` → `UserRegistrationFailedEvent.kt`
- `LoginFailureReason.kt` → `RegistrationFailureReason.kt`
- `LoginHandler.kt` catch blocks → `RegisterHandler.kt` catch blocks

### Context from Confluence Images

N/A — source là research artifacts (business_analysis.md, technical_spec.md), không có Confluence images.

### Change Impact Map (EXTEND)

```
Change Impact:
  FR-001 → [ADD] RegistrationEventRecorder (auth/application/event/RegistrationEventRecorder.kt) → EventService.record()
  FR-002 → [MODIFY] RegisterHandler (auth/application/command/RegisterHandler.kt) → delegate to RegistrationEventRecorder
  FR-003 → [ADD] UserRegistrationFailedEvent (auth/domain/event/UserRegistrationFailedEvent.kt) → DomainEvent interface
  FR-004 → [ADD] RegistrationFailureReason (auth/domain/event/RegistrationFailureReason.kt) → enum class
  FR-005 → [MODIFY] RegisterHandler (auth/application/command/RegisterHandler.kt) → add catch blocks for failure events
  FR-006 → [MODIFY] RegisterHandler (auth/application/command/RegisterHandler.kt) → add classifyFailureReason() method
  FR-007 → [ADD] RegistrationEventRecorder (auth/application/event/RegistrationEventRecorder.kt) → topic routing
  FR-008 → [ADD] UserRegistrationFailedEvent (auth/domain/event/UserRegistrationFailedEvent.kt) → no password field
  FR-009 → [MODIFY] RegisterHandler (auth/application/command/RegisterHandler.kt) → preserve existing event payload
  FR-010 → [REUSE] EventEnvelope (auth/domain/event/EventEnvelope.kt) → no changes needed
  FR-011 → [MODIFY] RegisterHandler (auth/application/command/RegisterHandler.kt) → preserve existing logging
```
