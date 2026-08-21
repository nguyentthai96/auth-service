# Pre-OpenSpec: jwt-token-issuance

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD) + Brainstorm Analysis
> **Classification Evidence**: keyword `TokenGenerator` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`; keyword `RefreshTokenHandler` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`; keyword `EventService` → module `auth.application.event` → file `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`; keyword `JwtService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`; keyword `AuthService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`
> **Archive**: N/A
> **Quality Score**: 85/100
> **Brainstorm**: `brainstorm_notes.md` — Selected Direction: "Approach A: Unified TokenIssuanceEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext"

## 📋 Feature Summary

Nâng cấp hệ thống JWT token lifecycle trong auth-service lên production-grade Event Sourcing. Hiện tại, `TokenGenerator.generateAuthResponse()` — central token factory được dùng bởi LoginHandler, RegisterHandler, RefreshTokenHandler — **KHÔNG record bất kỳ domain event nào** khi issue tokens. Tương tự, token revocation (rotation, logout, bulk revoke) cũng không có audit trail. Feature này bao gồm: tạo `TokenIssuedEvent` (unified với `IssuanceContext` discriminator), `TokenRevokedEvent` (với `RevocationType` discriminator), `TokenEventRecorder` helper service, pre-generate JTI strategy, optional `TokenIssuanceMetadata` parameter, và integrate EventService.record() vào TokenGenerator + RefreshTokenHandler + AuthService. Pattern theo đúng kiến trúc đã thiết lập bởi `user_registration_event` và `user_login_event` features.

| Metric | Giá trị |
|--------|---------|
| Số FR | 15 (Idea: 12, Enriched: 3) |
| Issues | 3 (🔴: 0, 🟡: 2, 🟢: 1) |
| Open Questions | 5 |
| **Quality Score** | **85/100** |

---

## 1. Actors

- **Client (End User)**: Gửi request đăng nhập, đăng ký, refresh token → trigger token issuance
- **Hệ thống (auth-service)**: Xử lý token generation, record domain events vào event store + outbox
- **Admin**: Query audit trail cho token issuance/revocation, monitor token patterns
- **Downstream Consumers (system-admin-service, analytics, SIEM)**: Nhận `TokenIssuedEvent` / `TokenRevokedEvent` để audit trail, security monitoring
- **Infrastructure (Kafka, PostgreSQL, Redis)**: Event transport (Kafka), event store persistence (PostgreSQL), token blacklist/sessions (Redis)

## 2. Functional Requirements

### FR-001: TokenIssuedEvent domain event [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `TokenIssuedEvent` data class trong package `auth.domain.event` với payload đầy đủ: `userId` (Long), `username` (String), `domainCode` (String), `domainId` (Long), `issuanceContext` (IssuanceContext enum), `accessTokenJti` (String — UUID), `refreshTokenHash` (String — SHA-256), `roles` (List<String>), `permissions` (List<String>), `accessTokenExpiresAt` (Instant), `refreshTokenExpiresAt` (Instant), `previousRefreshTokenHash` (String? — for rotation), `ipAddress` (String?), `userAgent` (String?), `issuedAt` (Instant). EventType = `iam.token.issued`.
- **Validation**: Required fields userId, username, domainCode, issuanceContext, accessTokenJti, refreshTokenHash phải non-null; issuanceContext phải là valid enum value.
- **Existing**: Không có — gap trong audit trail.
- **Impact**: [NEW] `auth/domain/event/TokenIssuedEvent.kt`

### FR-002: IssuanceContext enum [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo enum `IssuanceContext` trong `auth.domain.event` package: `LOGIN`, `REGISTRATION`, `TOKEN_REFRESH`, `MFA_COMPLETION`, `SSO`.
- **Validation**: Callers phải pass đúng context: LoginHandler → LOGIN, RegisterHandler → REGISTRATION, RefreshTokenHandler → TOKEN_REFRESH.
- **Impact**: [NEW] `auth/domain/event/IssuanceContext.kt`

### FR-003: TokenRevokedEvent domain event [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `TokenRevokedEvent` trong `auth.domain.event` với: `userId` (Long), `revocationType` (RevocationType enum), `revokedTokenHash` (String? — for single revoke), `revokedAccessTokenJti` (String? — for blacklist), `revokedCount` (Int — for bulk), `reason` (String?), `revokedAt` (Instant). EventType = `iam.token.revoked`.
- **Validation**: revocationType phải là valid enum; revokedCount >= 1 cho BULK_REVOKE.
- **Impact**: [NEW] `auth/domain/event/TokenRevokedEvent.kt`

### FR-004: RevocationType enum [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo enum `RevocationType`: `ROTATION`, `LOGOUT`, `ADMIN_REVOKE`, `BULK_REVOKE`.
- **Impact**: [NEW] `auth/domain/event/RevocationType.kt`

### FR-005: TokenIssuanceMetadata data class [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo data class `TokenIssuanceMetadata` trong `auth.domain.model`: `issuanceContext` (IssuanceContext), `ipAddress` (String?), `userAgent` (String?), `correlationId` (String?), `previousRefreshTokenHash` (String? — for rotation tracking).
- **Validation**: issuanceContext phải non-null.
- **Impact**: [NEW] `auth/domain/model/TokenIssuanceMetadata.kt`

### FR-006: TokenEventRecorder helper service [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo `TokenEventRecorder` @Component trong `auth.application.event` với 2 methods: `recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?)` và `recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?)`. Delegate to `EventService.record()` với aggregateType = "User", topic = "iam.token.issued" / "iam.token.revoked".
- **Validation**: Phải delegate EventService.record() — KHÔNG tự implement persistence.
- **Existing**: Pattern: `LoginEventRecorder` (nếu có từ user_login_event). EventService.record() đã có.
- **Impact**: [NEW] `auth/application/event/TokenEventRecorder.kt`

### FR-007: JwtService JTI pre-generation support [IDEA]
- **Actor**: Hệ thống
- **Action**: Thêm optional parameter `jti: String? = null` vào `JwtService.generateAccessToken()` và `JwtService.generateRefreshToken()`. Nếu jti != null → dùng giá trị truyền vào. Nếu null → generate UUID internally (backward compatible).
- **Validation**: Existing callers KHÔNG bị break; JTI trong JWT = JTI passed in.
- **Existing**: `JwtService.generateAccessToken()` hiện generate `UUID.randomUUID().toString()` nội bộ tại L91.
- **Impact**: [MODIFY] `auth/application/JwtService.kt`

### FR-008: TokenGenerator integrate TokenEventRecorder [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `TokenGenerator`:
  1. Inject `TokenEventRecorder` dependency
  2. Add optional parameter `metadata: TokenIssuanceMetadata? = null` vào `generateAuthResponse()`
  3. Pre-generate JTI (`UUID.randomUUID()`) trước khi gọi `jwtService.generateAccessToken(jti=...)` 
  4. Sau `tokenStore.saveRefreshToken()`, gọi `tokenEventRecorder.recordIssuance(...)` với full event data
- **Validation**: Event recorded TRƯỚC return AuthToken; participates trong caller's @Transactional; JTI trong event = JTI trong JWT.
- **Existing**: `TokenGenerator.generateAuthResponse()` (L42-78) — hiện KHÔNG record event.
- **Impact**: [MODIFY] `auth/application/command/TokenGenerator.kt`

### FR-009: RefreshTokenHandler integrate token events [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `RefreshTokenHandler`:
  1. Inject `TokenEventRecorder` dependency
  2. SAU `tokenStore.revokeToken(tokenHash)` → record `TokenRevokedEvent(revocationType=ROTATION)`
  3. TokenGenerator.generateAuthResponse() sẽ auto-record issuance event (FR-008)
  4. Cả 2 events share correlationId
- **Validation**: Revocation event recorded trước issuance event; cùng @Transactional boundary; correlationId linked.
- **Existing**: `RefreshTokenHandler.handle()` (L29-47) — `@Transactional`.
- **Impact**: [MODIFY] `auth/application/command/RefreshTokenHandler.kt`

### FR-010: AuthService logout/revokeAll integrate revocation events [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `AuthService`:
  1. Inject `TokenEventRecorder` dependency
  2. Trong `revokeAllSessions()` → sau revoke → record `TokenRevokedEvent(revocationType=BULK_REVOKE, revokedCount=count)`
  3. Trong logout flow (nếu tồn tại) → record `TokenRevokedEvent(revocationType=LOGOUT)`
- **Validation**: Events recorded trong @Transactional boundary.
- **Existing**: `AuthService.revokeAllSessions()` (L283).
- **Impact**: [MODIFY] `auth/application/AuthService.kt`

### FR-011: Callers pass IssuanceContext [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify callers of `TokenGenerator.generateAuthResponse()` to pass `TokenIssuanceMetadata`:
  - `LoginHandler` → metadata with `issuanceContext=LOGIN`, ipAddress, userAgent from LoginCommand
  - `RegisterHandler` → metadata with `issuanceContext=REGISTRATION`, ipAddress, userAgent from RegisterCommand
  - `RefreshTokenHandler` → metadata with `issuanceContext=TOKEN_REFRESH`, previousRefreshTokenHash
  - Other callers → metadata with null (backward compatible — TokenGenerator handles null metadata)
- **Validation**: Mỗi caller pass đúng IssuanceContext.
- **Existing**: `LoginHandler.kt`, `RegisterHandler.kt` — đã có ipAddress/userAgent trong command.
- **Impact**: [MODIFY] `LoginHandler.kt`, `RegisterHandler.kt`, `RefreshTokenHandler.kt`

### FR-012: Kafka topic configuration [IDEA]
- **Actor**: Hệ thống
- **Action**: 2 new Kafka topics: `iam.token.issued` và `iam.token.revoked`. Follows naming convention `iam.{domain}.{action}`.
- **Validation**: Topics consistent with existing `iam.user.registered`, `iam.user.logged_in`.
- **Impact**: Topic auto-created via outbox relay (existing OutboxPoller handles topic routing)

### FR-013: Transaction logging for token events [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Structured debug logging in TokenEventRecorder for observability.
- **Justification**: Production monitoring — trace event recording success/failure.
- **Impact**: [NEW] Within `TokenEventRecorder.kt`

### FR-014: Idempotent token event recording [ENRICHED]
- **Actor**: Hệ thống
- **Action**: EventEnvelope.id (UUID) provides dedup key. Existing EventService handles idempotency via event store unique constraint.
- **Justification**: Retry safety — EventService pattern already established.
- **Impact**: [REUSE] Existing `EventService.record()` + event store unique ID

### FR-015: Error handling for event recording failure [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Token issuance MUST NOT fail if event recording fails (event is secondary to token generation). Catch exception in TokenEventRecorder, log error, continue.
- **Justification**: Token generation is primary business flow — event recording is observability enhancement.
- **Impact**: [NEW] Try-catch in `TokenEventRecorder.kt`

## 3. Non-functional Requirements

- **Performance**: Event recording overhead < 5ms per token issuance (2 DB INSERTs — event_store + outbox — in same TX).
- **Security**: Refresh token HASH stored in events (NOT raw token); access token JTI only (NOT full JWT string).
- **Audit**: Complete token lifecycle trail: issuance → rotation → revocation → bulk revoke.
- **Architecture**: Clean Architecture — event types in `domain.event`, recorder in `application.event`, adapter layer unchanged.
- **Messaging**: Kafka topics `iam.token.issued`, `iam.token.revoked` via existing OutboxPoller relay.
- **Backward Compatibility**: All changes backward compatible — optional parameters with defaults; existing callers unaffected.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-008 (TokenGenerator) và FR-009 (RefreshTokenHandler) bổ sung cho nhau — FR-008 xử lý issuance, FR-009 xử lý rotation revocation + delegates issuance to FR-008.

## 5. Enriched Domain Requirements

### Enriched FRs

| FR-ID | Tên | Justification |
|-------|-----|---------------|
| FR-013 | Transaction logging | Observability cho token event recording (production monitoring) |
| FR-014 | Idempotent recording | Retry safety — EventService pattern đã có |
| FR-015 | Error handling | Token generation MUST NOT fail due to event recording failure |

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Kafka | Event transport — relay token events từ outbox | Existing: `OutboxPoller`, `KafkaEventPublisher` |
| PostgreSQL | Event store persistence — `event_store` table | Existing: `EventStorePort`, `EventStorePersistenceAdapter` |
| PostgreSQL | Outbox table — `event_outbox` table | Existing: `OutboxPort`, `OutboxPersistenceAdapter` |
| Redis | Token blacklist, session metadata | Existing: không thay đổi |

## 6. Assumptions

- ⚠️ Assumption: `TokenGenerator` không có `@Transactional` nhưng TẤT CẢ callers (LoginHandler, RegisterHandler, RefreshTokenHandler) đều có `@Transactional` → EventService.record() participates trong caller's TX — đã xác nhận qua code.
- ⚠️ Assumption: `EventService.record()` có thể handle thêm event volume từ token issuance/revocation — dựa trên pattern đã hoạt động cho UserRegisteredEvent và UserLoggedInEvent.
- ⚠️ Assumption: MFA flow token issuance sẽ có partial context cho đến khi `user_login_event` MFA integration hoàn tất — defer full MFA context.
- ⚠️ Assumption: Event store indexes đủ hiệu quả cho query `event_type = 'iam.token.issued'` — index đã tồn tại trên `event_type` column.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-010: AuthService logout flow structure chưa rõ ràng (need verification) |
| Đầy đủ (Completeness) | 22/25 | MFA flow token issuance deferred; anonymous token issuance deferred |
| Nhất quán (Consistency) | 22/25 | IssuanceContext enum values consistent với existing eventType naming convention |
| Kiểm thử được (Testability) | 18/25 | Event recording order (revocation before issuance) cần integration test; error handling path cần mock strategy |
| **Tổng** | **85/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -2 | FR-010 | AuthService logout flow — need to verify if logout() exists separately from revokeAllSessions() | Verify AuthService methods |
| 2 | Completeness | -3 | FR-011 | MFA flow (MfaService.verifyMfa()) token issuance missing — deferred | Add FR for MFA issuance context in future |
| 3 | Consistency | -3 | FR-002 | IssuanceContext.SSO not yet triggered by any handler | Add SSO handler integration later |
| 4 | Testability | -4 | FR-009 | Double event recording (revoke + issue) in RefreshTokenHandler — need to verify TX ordering | Add integration test for event order |
| 5 | Testability | -3 | FR-015 | Error handling try-catch in TokenEventRecorder — test requires mock EventService failure | Define mock strategy |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | TokenGenerator gains 1 new dependency (TokenEventRecorder) — total 11 constructor params. Manageable but approaching limit. | FR-008 | Monitor constructor size; consider builder/factory if grows further |
| 2 | Risk | 🟡 | Event store growth: high-traffic token issuance generates many events. Token events are higher volume than registration events. | FR-001 | Add TTL-based cleanup / archiving strategy for token events |
| 3 | Warning | 🟢 | MFA flow gap: MfaService.verifyMfa() calls TokenGenerator but transaction boundary needs verification. | FR-011 | Defer MFA context; record partial event with issuanceContext=MFA_COMPLETION when handler has @Transactional |

## 9. Open Questions

- **OQ-1**: Should `AuthService.logout()` be refactored to use command pattern (LogoutCommand → LogoutHandler) for consistency? Currently it's a direct service method.
- **OQ-2**: Should introspection requests be event-sourced? They're read-only but valuable for security monitoring. Recommend: defer.
- **OQ-3**: Should anonymous token issuance/renewal be included? Recommend: defer (low security value, high volume).
- **OQ-4**: Event store retention policy for token events? Token events are high-volume. May need TTL-based cleanup.
- **OQ-5**: Should `TokenIssuedEvent` include JWT claims snapshot (roles, permissions) for forensic analysis? Included in current design but makes events larger.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication — JWT Token Lifecycle Events (Event Sourcing layer)

### 10.2 Flow Type
Command — Token generation within Command handler flows → domain events (CQRS write-side)

### 10.3 Candidate Services
- **auth-service (auth module)**: Primary — contains TokenGenerator, RefreshTokenHandler, AuthService, JwtService, EventService. Evidence: keyword `TokenGenerator` in `TokenGenerator.kt`, `EventService` in `EventService.kt`, `RefreshTokenHandler` in `RefreshTokenHandler.kt`.
- **auth-service (shared module)**: Supporting — exception classes, error codes, config. Evidence: `SecurityProperties.kt`, `AuthErrorCode.kt`.

### Detection Evidence
- Keyword: `TokenGenerator` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
- Keyword: `RefreshTokenHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
- Keyword: `JwtService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- Keyword: `AuthService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`
- Keyword: `UserRegisteredEvent` (reference pattern) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
- Keyword: `EventEnvelope` (reuse) → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`

### 10.4 External Integrations
- **Kafka**: Outbox relay via `OutboxPoller` → topics `iam.token.issued`, `iam.token.revoked`
- **PostgreSQL**: Event store (`event_store` table), Outbox (`event_outbox` table)
- **Redis**: Không thay đổi — existing token blacklist, session metadata

### 10.5 Required Modules
- `auth.domain.event` — new `TokenIssuedEvent.kt`, `TokenRevokedEvent.kt`, `IssuanceContext.kt`, `RevocationType.kt`
- `auth.domain.model` — new `TokenIssuanceMetadata.kt`
- `auth.application.event` — new `TokenEventRecorder.kt`; reuse `EventService.kt` (no modification)
- `auth.application.command` — modify `TokenGenerator.kt`, `RefreshTokenHandler.kt`
- `auth.application` — modify `JwtService.kt`, `AuthService.kt`
- `auth.adapter.in.web` — no changes needed (metadata passed from command objects)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi `POST /api/auth/login` với credentials | CqrsAuthController → LoginHandler |
| 2 | LoginHandler | Validate credentials, generate tokens via TokenGenerator | LoginHandler @Transactional |
| 3 | TokenGenerator | Pre-generate JTI → JwtService.generateAccessToken(jti=...) → JwtService.generateRefreshToken() → tokenStore.saveRefreshToken() | Same TX |
| 4 | TokenGenerator | NEW: tokenEventRecorder.recordIssuance(TokenIssuedEvent(...)) → EventService.record() → event_store + outbox | Same TX |
| 5 | TokenGenerator | Return AuthToken | Same TX |
| 6 | OutboxPoller | Async: poll event_outbox → relay to Kafka topic `iam.token.issued` | OutboxPoller @Scheduled |

### Token Refresh Flow

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi refresh token | RefreshTokenHandler @Transactional |
| 2 | RefreshTokenHandler | Find + validate old token | tokenStore |
| 3 | RefreshTokenHandler | Revoke old token: tokenStore.revokeToken() | Same TX |
| 4 | RefreshTokenHandler | NEW: tokenEventRecorder.recordRevocation(TokenRevokedEvent(ROTATION)) | Same TX |
| 5 | RefreshTokenHandler | TokenGenerator.generateAuthResponse() → new tokens + issuance event | Same TX |
| 6 | RefreshTokenHandler | Return AuthToken (both events committed) | Same TX |

## 12. Traceability Matrix

| FR-ID | Spec Section | Affected Class | Status |
|-------|-------------|---------------|--------|
| FR-001 | EVT-F01 | `TokenIssuedEvent.kt` (NEW) | Pending |
| FR-002 | EVT-F02 | `IssuanceContext.kt` (NEW) | Pending |
| FR-003 | EVT-F03 | `TokenRevokedEvent.kt` (NEW) | Pending |
| FR-004 | EVT-F04 | `RevocationType.kt` (NEW) | Pending |
| FR-005 | EVT-F05 | `TokenIssuanceMetadata.kt` (NEW) | Pending |
| FR-006 | EVT-F06 | `TokenEventRecorder.kt` (NEW) | Pending |
| FR-007 | EVT-F07 | `JwtService.kt` [MODIFY] | Pending |
| FR-008 | EVT-F08 | `TokenGenerator.kt` [MODIFY] | Pending |
| FR-009 | EVT-F09 | `RefreshTokenHandler.kt` [MODIFY] | Pending |
| FR-010 | EVT-F10 | `AuthService.kt` [MODIFY] | Pending |
| FR-011 | EVT-F11 | `LoginHandler.kt`, `RegisterHandler.kt` [MODIFY] | Pending |
| FR-012 | EVT-F12 | Kafka topics (outbox-driven) | Pending |
| FR-013 | EVT-F13 | `TokenEventRecorder.kt` (NEW) | Pending |
| FR-014 | EVT-F14 | `EventService.kt` [REUSE] | Pending |
| FR-015 | EVT-F15 | `TokenEventRecorder.kt` (NEW) | Pending |

### Change Impact Map (EXTEND)

```
FR-001 → [ADD] TokenIssuedEvent.kt (auth/domain/event/) → NEW domain event
FR-002 → [ADD] IssuanceContext.kt (auth/domain/event/) → NEW enum
FR-003 → [ADD] TokenRevokedEvent.kt (auth/domain/event/) → NEW domain event
FR-004 → [ADD] RevocationType.kt (auth/domain/event/) → NEW enum
FR-005 → [ADD] TokenIssuanceMetadata.kt (auth/domain/model/) → NEW data class
FR-006 → [ADD] TokenEventRecorder.kt (auth/application/event/) → NEW helper
FR-007 → [MODIFY] JwtService.kt (auth/application/) → add optional jti param
FR-008 → [MODIFY] TokenGenerator.kt (auth/application/command/) → inject TokenEventRecorder, add metadata, pre-gen JTI
FR-009 → [MODIFY] RefreshTokenHandler.kt (auth/application/command/) → inject TokenEventRecorder, record events
FR-010 → [MODIFY] AuthService.kt (auth/application/) → inject TokenEventRecorder, record revocation events
FR-011 → [MODIFY] LoginHandler.kt, RegisterHandler.kt (auth/application/command/) → pass metadata
```

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này là **extension pattern** của `user_registration_event` (đã implement thành công) và `user_login_event`. Pattern: enriched domain event → EventService.record() → event store + outbox → Kafka. Complexity: MEDIUM.
- `TokenGenerator` hiện có 10 constructor dependencies — thêm `TokenEventRecorder` sẽ là 11. Acceptable nhưng nên monitor.
- **Critical gap**: `TokenGenerator.generateAuthResponse()` hiện KHÔNG record bất kỳ domain event nào. Mọi token issuance (login, register, refresh, MFA) đều "silent" — gap lớn trong security audit trail.
- JTI pre-generation strategy (generate UUID externally, pass to JwtService) là efficient và least invasive — avoid double-parsing JWT.
- `TokenEventRecorder` follows helper service pattern — keeps TokenGenerator focused on token logic.

### Related Features / Precedents
- `user_registration_event` (archived: `2026-08-20-user_registration_event/`) — **PRIMARY reference**. EventService.record() integration in RegisterHandler. Exact same pattern.
- `user_login_event` (active: changes/`user_login_event`/) — login event recording. Same pattern, same EventService.
- `auth-core-features` (archived: `2026-08-21-auth-core-features/`) — LoginHandler, TokenGenerator extraction patterns.

### Integration Notes
- **EventService** (reuse): Không cần modify. `record()` method generic, accept any `DomainEvent`. Established pattern.
- **OutboxPoller** (reuse): Không cần modify. Polls `event_outbox` table, relay to Kafka by topic field. Topics auto-routed.
- **EventEnvelope** (reuse): Wraps domain events with CloudEvents metadata. No modification needed.
- **Backward Compatibility**: `generateAuthResponse(user, domainCode)` → add `metadata: TokenIssuanceMetadata? = null` → existing callers unchanged.

### Suggested Approach
1. **Phase 1 (Domain Events, ~1 day)**: Create `TokenIssuedEvent`, `TokenRevokedEvent`, `IssuanceContext`, `RevocationType`, `TokenIssuanceMetadata`.
2. **Phase 2 (Infrastructure, ~1 day)**: Create `TokenEventRecorder`. Modify `JwtService` (optional jti param).
3. **Phase 3 (Integration, ~1-2 days)**: Modify `TokenGenerator` (inject recorder, metadata, JTI pre-gen). Modify `RefreshTokenHandler` (revocation + issuance events). Modify `AuthService` (revocation events in logout/revokeAll).
4. **Phase 4 (Caller Updates, ~0.5 day)**: Modify `LoginHandler`, `RegisterHandler` to pass `TokenIssuanceMetadata`.
5. **Phase 5 (Tests, ~1 day)**: Unit tests for event creation, integration tests for TX boundary.

### Brainstorm Integration
- **Selected Direction**: "Approach A: Unified TokenIssuedEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext"
- **Key Decisions**:
  - Unified events (single `TokenIssuedEvent` + `IssuanceContext` discriminator) over event proliferation
  - Helper service pattern (`TokenEventRecorder`) over direct EventService injection
  - JTI pre-generation over double-parsing
  - Backward compatible optional parameters over breaking changes
