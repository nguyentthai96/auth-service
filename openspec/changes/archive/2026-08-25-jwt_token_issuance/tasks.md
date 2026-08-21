# Tasks: jwt-token-issuance

<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Transactional Outbox" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

_Generated: 2025-08-25_
_Profile: Command | N/A (no factory) | EXTEND_

---

## Task Summary

| # | Task | Action | FR | File |
|---|------|--------|----|------|
| 1 | IssuanceContext enum | [NEW] | FR-002 | `auth/domain/event/IssuanceContext.kt` |
| 2 | RevocationType enum | [NEW] | FR-004 | `auth/domain/event/RevocationType.kt` |
| 3 | TokenIssuedEvent domain event | [NEW] | FR-001 | `auth/domain/event/TokenIssuedEvent.kt` |
| 4 | TokenRevokedEvent domain event | [NEW] | FR-003 | `auth/domain/event/TokenRevokedEvent.kt` |
| 5 | TokenIssuanceMetadata data class | [NEW] | FR-005 | `auth/domain/model/TokenIssuanceMetadata.kt` |
| 6 | TokenEventRecorder helper service | [NEW] | FR-006, FR-013, FR-015 | `auth/application/event/TokenEventRecorder.kt` |
| 7 | JwtService JTI pre-generation | [MODIFY] | FR-007 | `auth/application/JwtService.kt` |
| 8 | TokenGenerator integrate TokenEventRecorder | [MODIFY] | FR-008 | `auth/application/command/TokenGenerator.kt` |
| 9 | RefreshTokenHandler integrate token events | [MODIFY] | FR-009 | `auth/application/command/RefreshTokenHandler.kt` |
| 10 | AuthService revocation events | [MODIFY] | FR-010 | `auth/application/AuthService.kt` |
| 11 | LoginHandler pass IssuanceContext | [MODIFY] | FR-011 | `auth/application/command/LoginHandler.kt` |
| 12 | RegisterHandler pass IssuanceContext | [MODIFY] | FR-011 | `auth/application/command/RegisterHandler.kt` |

---

## Tasks

- [x] **Task 1: Create IssuanceContext enum (FR-002)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/IssuanceContext.kt` | Action: [NEW]
  - Context: Discriminator enum for TokenIssuedEvent. Values: LOGIN, REGISTRATION, TOKEN_REFRESH, MFA_COMPLETION, SSO.
  - Package: `com.ntt.authservice.auth.domain.event`
  - Pattern: Simple enum, no dependencies. Used by TokenIssuedEvent and TokenIssuanceMetadata.
  - Design ref: design.md §2.1

- [x] **Task 2: Create RevocationType enum (FR-004)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/RevocationType.kt` | Action: [NEW]
  - Context: Discriminator enum for TokenRevokedEvent. Values: ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE.
  - Package: `com.ntt.authservice.auth.domain.event`
  - Pattern: Simple enum, no dependencies. Used by TokenRevokedEvent.
  - Design ref: design.md §2.1

- [x] **Task 3: Create TokenIssuedEvent domain event (FR-001)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenIssuedEvent.kt` | Action: [NEW]
  - Context: Domain event recorded when JWT tokens are issued. Implements DomainEvent interface (`com.ntt.authservice.auth.application.port.out.DomainEvent`). eventType = "iam.token.issued". Payload: userId, username, domainCode, domainId, issuanceContext, accessTokenJti, refreshTokenHash, roles, permissions, accessTokenExpiresAt, refreshTokenExpiresAt, previousRefreshTokenHash?, ipAddress?, userAgent?, issuedAt.
  - Package: `com.ntt.authservice.auth.domain.event`
  - Pattern: Follows UserRegisteredEvent pattern. Data class implements DomainEvent.
  - Source: `UserRegisteredEvent.kt` — same package, same interface.
  - Security: Store refreshTokenHash (SHA-256) NOT raw token; accessTokenJti (UUID) NOT full JWT.
  - Design ref: design.md §2.1

- [x] **Task 4: Create TokenRevokedEvent domain event (FR-003)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenRevokedEvent.kt` | Action: [NEW]
  - Context: Domain event recorded when tokens are revoked. Implements DomainEvent. eventType = "iam.token.revoked". Payload: userId, revocationType, revokedTokenHash?, revokedAccessTokenJti?, revokedCount (default 1), reason?, revokedAt.
  - Package: `com.ntt.authservice.auth.domain.event`
  - Pattern: Same as TokenIssuedEvent.
  - Design ref: design.md §2.1

- [x] **Task 5: Create TokenIssuanceMetadata data class (FR-005)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/model/TokenIssuanceMetadata.kt` | Action: [NEW]
  - Context: Contextual metadata passed to TokenGenerator.generateAuthResponse(). Fields: issuanceContext (required), ipAddress?, userAgent?, correlationId?, previousRefreshTokenHash?.
  - Package: `com.ntt.authservice.auth.domain.model`
  - Pattern: Simple data class, references IssuanceContext enum.
  - Design ref: design.md §2.2

- [x] **Task 6: Create TokenEventRecorder helper service (FR-006, FR-013, FR-015)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt` | Action: [NEW]
  - Context: @Component helper service delegating to EventService.record(). Two methods: recordIssuance(event, userId, correlationId) and recordRevocation(event, userId, correlationId). Error handling: try-catch around EventService.record() — log WARN, do NOT propagate (FR-015). Structured debug logging for observability (FR-013).
  - Package: `com.ntt.authservice.auth.application.event`
  - Dependency: EventService (same package)
  - Pattern: Helper service pattern. aggregateType = "User". Topics: "iam.token.issued", "iam.token.revoked". partitionKey = userId.toString().
  - Source: `EventService.kt` — verify record() signature.
  - Design ref: design.md §2.3

- [x] **Task 7: Modify JwtService — add optional JTI parameter (FR-007)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Action: [MODIFY]
  - Context: Add optional `jti: String? = null` parameter to `generateAccessToken()` and `generateRefreshToken()`. Use `val resolvedJti = jti ?: UUID.randomUUID().toString()` for backward compatibility.
  - Source: Current JwtService.kt L78-97 (generateAccessToken), L101-113 (generateRefreshToken). Both currently use `.id(UUID.randomUUID().toString())`.
  - Change: Add `jti: String? = null` param. Replace `.id(UUID.randomUUID().toString())` with `.id(resolvedJti)`.
  - Backward compatible: existing callers pass no jti → generates UUID internally.
  - Design ref: design.md §2.4

- [x] **Task 8: Modify TokenGenerator — inject TokenEventRecorder, add metadata, pre-gen JTI (FR-008)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt` | Action: [MODIFY]
  - Context: 
    1. Inject `TokenEventRecorder` dependency (constructor)
    2. Add optional parameter `metadata: TokenIssuanceMetadata? = null` to `generateAuthResponse()`
    3. Pre-generate JTI: `val accessTokenJti = UUID.randomUUID().toString()`
    4. Pass JTI to `jwtService.generateAccessToken(jti = accessTokenJti)`
    5. After tokenStore.saveRefreshToken(), call `tokenEventRecorder.recordIssuance(...)` with full TokenIssuedEvent
  - Source: Current TokenGenerator.kt constructor (L26-37), generateAuthResponse (L40-78).
  - Import: TokenEventRecorder, TokenIssuedEvent, IssuanceContext, TokenIssuanceMetadata
  - Design ref: design.md §2.4

- [x] **Task 9: Modify RefreshTokenHandler — inject TokenEventRecorder, record events (FR-009)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` | Action: [MODIFY]
  - Context:
    1. Inject `TokenEventRecorder` dependency (constructor)
    2. After `tokenStore.revokeToken(tokenHash)` → record `TokenRevokedEvent(ROTATION)`
    3. Create shared `correlationId = UUID.randomUUID().toString()`
    4. Pass `TokenIssuanceMetadata(TOKEN_REFRESH, previousRefreshTokenHash=tokenHash, correlationId=correlationId)` to `tokenGenerator.generateAuthResponse()`
  - Source: Current RefreshTokenHandler.kt constructor (L19-23), handle (L29-47).
  - Import: TokenEventRecorder, TokenRevokedEvent, RevocationType, TokenIssuanceMetadata, IssuanceContext, UUID
  - Design ref: design.md §2.4

- [x] **Task 10: Modify AuthService — inject TokenEventRecorder, record revocation events (FR-010)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` | Action: [MODIFY]
  - Context:
    1. Inject `TokenEventRecorder` dependency (constructor)
    2. In `revokeAllSessions()` → after `refreshTokenRepository.revokeAllByUserId()`, before auditLogService → record `TokenRevokedEvent(BULK_REVOKE, revokedCount=revokedCount)`
  - Source: Current AuthService.kt constructor (L24-37), revokeAllSessions (L272-290).
  - Import: TokenEventRecorder (from auth.application.event), TokenRevokedEvent, RevocationType
  - Design ref: design.md §2.4

- [x] **Task 11: Modify LoginHandler — pass TokenIssuanceMetadata (FR-011)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - Context: Before `tokenGenerator.generateAuthResponse(user, domainCode)`, create `TokenIssuanceMetadata(issuanceContext=LOGIN, ipAddress=command.ipAddress, userAgent=command.userAgent)`. Pass as third argument.
  - Source: Current LoginHandler.kt L146 — `val authToken = tokenGenerator.generateAuthResponse(user, domainCode)`
  - Import: TokenIssuanceMetadata, IssuanceContext
  - Design ref: design.md §2.4

- [x] **Task 12: Modify RegisterHandler — pass TokenIssuanceMetadata (FR-011)**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` | Action: [MODIFY]
  - Context: Before `tokenGenerator.generateAuthResponse(savedUser, command.domainCode)`, create `TokenIssuanceMetadata(issuanceContext=REGISTRATION, ipAddress=command.ipAddress, userAgent=command.userAgent, correlationId=command.correlationId)`. Pass as third argument.
  - Source: Current RegisterHandler.kt L111 — `val authToken = tokenGenerator.generateAuthResponse(savedUser, command.domainCode)`
  - Import: TokenIssuanceMetadata, IssuanceContext
  - Design ref: design.md §2.4
