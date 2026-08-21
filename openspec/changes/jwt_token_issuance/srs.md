# SRS: jwt-token-issuance

_Generated: 2025-08-25_
_Profile: Command | N/A (no factory) | EXTEND_

---

## 1. System Context

- **Feature Name**: JWT Token Issuance Event — Production-Grade Event Sourcing
- **Domain**: Authentication — JWT Token Lifecycle Events
- **Flow**: Command (Token generation within Command handler flows → domain events)
- **Services**: auth-service (EXTEND)
- **Source**: User Idea (enriched) + Brainstorm Analysis + pre_openspec.md

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Client (End User) | Người dùng trigger token issuance qua login/register/refresh | POST /api/auth/login, POST /api/auth/register, POST /api/auth/refresh |
| Hệ thống (auth-service) | Xử lý token generation, record domain events vào event store + outbox | TokenGenerator → EventService → event_store + outbox |
| Admin | Query audit trail cho token issuance/revocation | Event store queries |
| Downstream Consumers (SIEM, analytics) | Nhận `TokenIssuedEvent` / `TokenRevokedEvent` | Kafka: iam.token.issued, iam.token.revoked |
| Infrastructure (Kafka, PostgreSQL) | Event transport và persistence | Outbox relay, event store |

---

## 3. Functional Requirements

### 3.1 Token Issuance Events

#### FR-001: TokenIssuedEvent domain event [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token generation thành công trong TokenGenerator.generateAuthResponse().
- **Action**: Hệ thống tạo `TokenIssuedEvent` data class trong package `auth.domain.event` implements `DomainEvent` interface. Payload: `userId` (Long), `username` (String), `domainCode` (String), `domainId` (Long), `issuanceContext` (IssuanceContext), `accessTokenJti` (String — UUID pre-generated), `refreshTokenHash` (String — SHA-256 hash), `roles` (List<String>), `permissions` (List<String>), `accessTokenExpiresAt` (Instant), `refreshTokenExpiresAt` (Instant), `previousRefreshTokenHash` (String? — for rotation), `ipAddress` (String?), `userAgent` (String?), `issuedAt` (Instant). `eventType = "iam.token.issued"`.
- **Validation Rules**:
  - `userId`, `username`, `domainCode`, `issuanceContext`, `accessTokenJti`, `refreshTokenHash` MUST be non-null
  - `accessTokenJti` MUST be valid UUID format
  - `refreshTokenHash` MUST be SHA-256 hash (64 hex chars)
  - `issuedAt` MUST be UTC
  - `roles` and `permissions` MAY be empty lists but MUST NOT be null
- **Error Codes**: N/A (internal event creation)
- **Existing Code**: No existing TokenIssuedEvent — gap in audit trail
- **Classification**: NEW

#### FR-002: IssuanceContext enum [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo enum `IssuanceContext` trong `auth.domain.event` package: `LOGIN` (password auth), `REGISTRATION` (first token after register), `TOKEN_REFRESH` (token rotation), `MFA_COMPLETION` (after MFA verify), `SSO` (after SSO/OAuth2 flow).
- **Validation Rules**:
  - Callers MUST pass correct context: LoginHandler → LOGIN, RegisterHandler → REGISTRATION, RefreshTokenHandler → TOKEN_REFRESH
  - SSO and MFA_COMPLETION reserved for future handler integration
- **Error Codes**: N/A
- **Classification**: NEW

### 3.2 Token Revocation Events

#### FR-003: TokenRevokedEvent domain event [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token revocation operation completed (revokeToken, revokeAll, blacklist).
- **Action**: Tạo `TokenRevokedEvent` trong `auth.domain.event` implements `DomainEvent`. Payload: `userId` (Long), `revocationType` (RevocationType), `revokedTokenHash` (String? — for single revoke/rotation), `revokedAccessTokenJti` (String? — for blacklist), `revokedCount` (Int — for bulk, default 1), `reason` (String?), `revokedAt` (Instant). `eventType = "iam.token.revoked"`.
- **Validation Rules**:
  - `userId` and `revocationType` MUST be non-null
  - For `ROTATION`: `revokedTokenHash` MUST be non-null (the old token hash)
  - For `BULK_REVOKE`: `revokedCount` MUST be >= 1
  - `revokedAt` MUST be UTC
- **Error Codes**: N/A
- **Classification**: NEW

#### FR-004: RevocationType enum [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo enum `RevocationType` trong `auth.domain.event`: `ROTATION` (old token revoked during refresh), `LOGOUT` (user logout), `ADMIN_REVOKE` (admin-initiated), `BULK_REVOKE` (revokeAllForUser).
- **Validation Rules**: Callers MUST pass correct type per operation context.
- **Error Codes**: N/A
- **Classification**: NEW

### 3.3 Supporting Data Classes

#### FR-005: TokenIssuanceMetadata data class [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo data class `TokenIssuanceMetadata` trong `auth.domain.model`: `issuanceContext` (IssuanceContext — required), `ipAddress` (String? = null), `userAgent` (String? = null), `correlationId` (String? = null), `previousRefreshTokenHash` (String? = null — for rotation tracking).
- **Validation Rules**:
  - `issuanceContext` MUST be non-null
  - `ipAddress` SHOULD be IPv4 or IPv6 format when provided
  - `correlationId` SHOULD be UUID format when provided
- **Error Codes**: N/A
- **Existing Code**: `LoginCommand` already has `ipAddress`, `userAgent`; `RegisterCommand` already has `ipAddress`, `userAgent`
- **Classification**: NEW

### 3.4 Event Recording Infrastructure

#### FR-006: TokenEventRecorder helper service [IDEA]
- **Actor**: Hệ thống
- **Precondition**: `EventService` bean available.
- **Action**: Tạo `TokenEventRecorder` @Component trong `auth.application.event`:
  - `recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?)` — delegates to `EventService.record(aggregateType="User", aggregateId=userId, event=event, topic="iam.token.issued", partitionKey=userId.toString(), correlationId=correlationId)`
  - `recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?)` — delegates to `EventService.record(aggregateType="User", aggregateId=userId, event=event, topic="iam.token.revoked", partitionKey=userId.toString(), correlationId=correlationId)`
  - Error handling: try-catch around EventService.record() — log error, do NOT propagate exception (FR-015)
- **Validation Rules**:
  - MUST delegate to EventService.record() — NO direct persistence
  - Exceptions MUST NOT propagate (token issuance is primary, event recording is secondary)
- **Error Codes**: N/A (exceptions caught and logged)
- **Existing Code**: `EventService.record()` at `auth/application/event/EventService.kt` (L49-87)
- **Classification**: NEW

#### FR-007: JwtService JTI pre-generation support [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Existing `JwtService.generateAccessToken()` generates JTI internally.
- **Action**: Add optional parameter `jti: String? = null` to `JwtService.generateAccessToken()` and `JwtService.generateRefreshToken()`:
  - If `jti != null` → use provided JTI as JWT ID claim
  - If `jti == null` → generate `UUID.randomUUID().toString()` internally (backward compatible)
- **Validation Rules**:
  - Existing callers MUST NOT break (optional param with default null)
  - JTI in generated JWT MUST match passed-in value when provided
  - JTI format MUST be valid UUID
- **Error Codes**: N/A
- **Existing Code**: `JwtService.generateAccessToken()` L78-99 — `.id(UUID.randomUUID().toString())` at L91
- **Classification**: MODIFY

### 3.5 Integration Points

#### FR-008: TokenGenerator integrate TokenEventRecorder [IDEA]
- **Actor**: Hệ thống
- **Precondition**: TokenEventRecorder and JwtService JTI support available.
- **Action**: Modify `TokenGenerator`:
  1. Add `tokenEventRecorder: TokenEventRecorder` constructor dependency
  2. Add optional `metadata: TokenIssuanceMetadata? = null` to `generateAuthResponse()` signature
  3. Pre-generate accessTokenJti = `UUID.randomUUID().toString()` before calling `jwtService.generateAccessToken(jti=accessTokenJti, ...)`
  4. Pre-generate refreshTokenJti = `UUID.randomUUID().toString()` before calling `jwtService.generateRefreshToken(userId, jti=refreshTokenJti)`
  5. After `tokenStore.saveRefreshToken()`: construct `TokenIssuedEvent` with all available data and call `tokenEventRecorder.recordIssuance(event, user.id.value, metadata?.correlationId)`
  6. If metadata == null → create event with `issuanceContext = IssuanceContext.LOGIN` (safe default)
- **Validation Rules**:
  - Event MUST be recorded AFTER tokenStore.saveRefreshToken() and BEFORE return AuthToken
  - Event recording participates in caller's @Transactional boundary
  - JTI in event = JTI in generated JWT
  - Backward compatible: `generateAuthResponse(user, domainCode)` still works (metadata defaults to null)
- **Error Codes**: N/A (TokenEventRecorder handles errors internally)
- **Existing Code**: `TokenGenerator.generateAuthResponse()` at L42-78
- **Classification**: MODIFY

#### FR-009: RefreshTokenHandler integrate token events [IDEA]
- **Actor**: Hệ thống
- **Precondition**: TokenEventRecorder available; TokenGenerator updated (FR-008).
- **Action**: Modify `RefreshTokenHandler`:
  1. Add `tokenEventRecorder: TokenEventRecorder` constructor dependency
  2. After `tokenStore.revokeToken(tokenHash)` (L43): generate correlationId, construct `TokenRevokedEvent(userId=user.id.value, revocationType=RevocationType.ROTATION, revokedTokenHash=tokenHash)` and call `tokenEventRecorder.recordRevocation(event, user.id.value, correlationId)`
  3. Create `TokenIssuanceMetadata(issuanceContext=IssuanceContext.TOKEN_REFRESH, previousRefreshTokenHash=tokenHash, correlationId=correlationId)`
  4. Pass metadata to `tokenGenerator.generateAuthResponse(user, domainCode, metadata)`
  5. TokenGenerator will auto-record issuance event with same correlationId
- **Validation Rules**:
  - Revocation event recorded AFTER tokenStore.revokeToken() and BEFORE generateAuthResponse()
  - Both events (revocation + issuance) share same correlationId
  - Both events in same @Transactional boundary
- **Error Codes**: N/A
- **Existing Code**: `RefreshTokenHandler.handle()` at L29-47 — `@Transactional`
- **Classification**: MODIFY

#### FR-010: AuthService logout/revokeAll integrate revocation events [IDEA]
- **Actor**: Hệ thống
- **Precondition**: TokenEventRecorder available.
- **Action**: Modify `AuthService`:
  1. Add `tokenEventRecorder: TokenEventRecorder` constructor dependency
  2. In `revokeAllSessions(userId)` (L283): after revoke operation, record `TokenRevokedEvent(userId=userId, revocationType=RevocationType.BULK_REVOKE, revokedCount=count)`
- **Validation Rules**:
  - Revocation event recorded AFTER actual revocation
  - Single BULK_REVOKE event with revokedCount (NOT one event per token)
  - In @Transactional boundary
- **Error Codes**: N/A
- **Existing Code**: `AuthService.revokeAllSessions()` at L283
- **Classification**: MODIFY

#### FR-011: Callers pass IssuanceContext [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify callers of `TokenGenerator.generateAuthResponse()`:
  - `LoginHandler`: Create `TokenIssuanceMetadata(issuanceContext=IssuanceContext.LOGIN, ipAddress=command.ipAddress, userAgent=command.userAgent, correlationId=command.correlationId)`, pass to `generateAuthResponse(user, domainCode, metadata)`
  - `RegisterHandler`: Create `TokenIssuanceMetadata(issuanceContext=IssuanceContext.REGISTRATION, ipAddress=command.ipAddress, userAgent=command.userAgent, correlationId=command.correlationId)`, pass to `generateAuthResponse(user, domainCode, metadata)`
  - `RefreshTokenHandler`: Handled in FR-009
  - Other callers (SwitchDomainHandler, etc.): Pass null metadata (backward compatible)
- **Validation Rules**:
  - Each caller MUST pass correct IssuanceContext
  - ipAddress and userAgent extracted from existing command fields (already available)
- **Error Codes**: N/A
- **Existing Code**: `LoginHandler.kt` — calls `tokenGenerator.generateAuthResponse(user, domainCode)`; `RegisterHandler.kt` — calls `tokenGenerator.generateAuthResponse(savedUser, domainCode)`
- **Classification**: MODIFY

#### FR-012: Kafka topic configuration [IDEA]
- **Actor**: Hệ thống
- **Action**: 2 new Kafka topics auto-created via OutboxPoller relay:
  - `iam.token.issued` — all token issuance events
  - `iam.token.revoked` — all token revocation events
- **Validation Rules**:
  - Topic naming follows `iam.{domain}.{action}` convention (consistent with `iam.user.registered`)
  - Partitioned by userId (via partitionKey in outbox record)
- **Error Codes**: N/A
- **Existing Code**: OutboxPoller relays by `topic` field in event_outbox — no code change needed
- **Classification**: REUSE (existing OutboxPoller handles topic routing)

### 3.6 Operational Requirements

#### FR-013: Transaction logging [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Structured debug/info logging in TokenEventRecorder: log event type, userId, issuanceContext/revocationType, correlationId after successful recording.
- **Validation Rules**: Log format consistent with EventService logging pattern
- **Error Codes**: N/A
- **Classification**: NEW (within TokenEventRecorder)

#### FR-014: Idempotent token event recording [ENRICHED]
- **Actor**: Hệ thống
- **Action**: EventEnvelope.id (UUID) provides deduplication key. Event store unique constraint on (aggregate_type, aggregate_id, sequence_number) prevents duplicates.
- **Validation Rules**: Reuse existing EventService idempotency mechanism
- **Error Codes**: N/A
- **Classification**: REUSE

#### FR-015: Error handling for event recording failure [ENRICHED]
- **Actor**: Hệ thống
- **Precondition**: Event recording via EventService.record() fails (DB error, serialization error).
- **Action**: TokenEventRecorder MUST catch all exceptions from EventService.record(), log error with full context (userId, eventType, error message), and NOT propagate the exception. Token issuance/revocation is the primary business operation; event recording is a secondary observability enhancement.
- **Validation Rules**:
  - Token generation MUST succeed even if event recording fails
  - Error logged at WARN level with full context
  - No partial state: if EventService.record() fails, the event is simply not recorded (token store state remains consistent)
- **Error Codes**: N/A (error logged, not thrown)
- **Classification**: NEW (within TokenEventRecorder)

---

## 4. Non-functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Event recording overhead < 5ms per issuance (2 DB INSERTs in same TX); token generation latency increase < 1% |
| Security | Refresh token stored as SHA-256 HASH in events (NOT raw token); access token stored as JTI only (NOT full JWT) |
| Audit | Complete lifecycle: issuance → rotation → revocation → bulk revoke; all events in event_store |
| Reliability | Transactional outbox ensures zero event loss; OutboxPoller handles async Kafka delivery |
| Backward Compatibility | All changes backward compatible; existing callers unaffected |
| Scalability | Events partitioned by userId in Kafka; event_store indexed by aggregate_id |

---

## 5. Data Model

### 5.1 New Domain Events

No new database tables — reuse existing `event_store` and `event_outbox` tables.

### 5.2 Event Payloads (stored in event_store.payload as JSONB)

**TokenIssuedEvent** (in EventEnvelope):
```json
{
  "id": "uuid",
  "type": "iam.token.issued",
  "source": "auth-service",
  "specversion": "1.0",
  "time": "2025-08-25T10:00:00Z",
  "correlationId": "uuid",
  "schemaVersion": 1,
  "data": {
    "userId": 123456789,
    "username": "john.doe",
    "domainCode": "default",
    "domainId": 1,
    "issuanceContext": "LOGIN",
    "accessTokenJti": "uuid",
    "refreshTokenHash": "sha256-hex-64",
    "roles": ["ADMIN", "USER"],
    "permissions": ["READ", "WRITE"],
    "accessTokenExpiresAt": "2025-08-25T10:30:00Z",
    "refreshTokenExpiresAt": "2025-08-26T10:00:00Z",
    "previousRefreshTokenHash": null,
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0...",
    "issuedAt": "2025-08-25T10:00:00Z"
  }
}
```

**TokenRevokedEvent** (in EventEnvelope):
```json
{
  "id": "uuid",
  "type": "iam.token.revoked",
  "source": "auth-service",
  "data": {
    "userId": 123456789,
    "revocationType": "ROTATION",
    "revokedTokenHash": "sha256-hex-64",
    "revokedAccessTokenJti": null,
    "revokedCount": 1,
    "reason": "Token rotation during refresh",
    "revokedAt": "2025-08-25T10:30:00Z"
  }
}
```

---

## 6. Interface Design

### 6.1 API — No new REST endpoints

This feature adds no new REST endpoints. Events are recorded internally during existing token operations.

### 6.2 Kafka Events (via OutboxPoller relay)

| Topic | Event Type | Partition Key | Consumer Pattern |
|-------|-----------|---------------|-----------------|
| `iam.token.issued` | `TokenIssuedEvent` | `userId.toString()` | SIEM, audit service, analytics |
| `iam.token.revoked` | `TokenRevokedEvent` | `userId.toString()` | SIEM, session management, analytics |

---

## 7. Error Handling

| Scenario | Behavior | Error Code |
|----------|----------|------------|
| EventService.record() fails (DB error) | TokenEventRecorder catches exception, logs WARN, continues | N/A (no error propagated) |
| EventService.record() fails (serialization) | TokenEventRecorder catches exception, logs WARN, continues | N/A |
| TokenGenerator.generateAuthResponse() — token generation fails | Exception propagates normally (no event recorded for failed generation) | Existing error codes |
| OutboxPoller relay fails | OutboxPoller retries with existing retry mechanism | Existing outbox error handling |

---

## 8. Testing Strategy

| Test Type | Scope | Key Scenarios |
|-----------|-------|---------------|
| Unit Tests | TokenIssuedEvent, TokenRevokedEvent | Event creation with all fields; eventType correctness |
| Unit Tests | TokenEventRecorder | Delegates to EventService; exception handling (mock EventService throws → no propagation) |
| Unit Tests | TokenGenerator (modified) | JTI pre-generation; event recorded after token store; metadata handling (null vs provided) |
| Unit Tests | RefreshTokenHandler (modified) | Revocation event recorded; issuance event auto-recorded; correlationId shared |
| Integration Tests | End-to-end token flow | Login → TokenIssuedEvent in event_store; Refresh → TokenRevokedEvent + TokenIssuedEvent in event_store; same TX boundary |
| Integration Tests | Error resilience | Mock EventService failure → token still generated successfully |

---

## 9. FR Traceability

| FR-ID | Artifact Section | Implementation Target | Status |
|-------|-----------------|----------------------|--------|
| FR-001 | 3.1 | `auth/domain/event/TokenIssuedEvent.kt` [NEW] | Pending |
| FR-002 | 3.1 | `auth/domain/event/IssuanceContext.kt` [NEW] | Pending |
| FR-003 | 3.2 | `auth/domain/event/TokenRevokedEvent.kt` [NEW] | Pending |
| FR-004 | 3.2 | `auth/domain/event/RevocationType.kt` [NEW] | Pending |
| FR-005 | 3.3 | `auth/domain/model/TokenIssuanceMetadata.kt` [NEW] | Pending |
| FR-006 | 3.4 | `auth/application/event/TokenEventRecorder.kt` [NEW] | Pending |
| FR-007 | 3.4 | `auth/application/JwtService.kt` [MODIFY] | Pending |
| FR-008 | 3.5 | `auth/application/command/TokenGenerator.kt` [MODIFY] | Pending |
| FR-009 | 3.5 | `auth/application/command/RefreshTokenHandler.kt` [MODIFY] | Pending |
| FR-010 | 3.5 | `auth/application/AuthService.kt` [MODIFY] | Pending |
| FR-011 | 3.5 | `LoginHandler.kt`, `RegisterHandler.kt` [MODIFY] | Pending |
| FR-012 | 3.5 | Kafka topics (outbox-driven) [REUSE] | Pending |
| FR-013 | 3.6 | `TokenEventRecorder.kt` [NEW] | Pending |
| FR-014 | 3.6 | `EventService.kt` [REUSE] | Pending |
| FR-015 | 3.6 | `TokenEventRecorder.kt` [NEW] | Pending |
