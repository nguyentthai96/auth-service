# Proposal: jwt-token-issuance

> **Change**: jwt_token_issuance | **Type**: EXTEND | **Flow**: Command
> **Direction**: Unified TokenIssuedEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext (from brainstorm)
> **_Generated**: 2025-08-25_

## Changes

- **auth.domain.event [NEW]**: `TokenIssuedEvent.kt` — enriched domain event (userId, username, domainCode, domainId, issuanceContext, accessTokenJti, refreshTokenHash, roles, permissions, accessTokenExpiresAt, refreshTokenExpiresAt, previousRefreshTokenHash, ipAddress, userAgent, issuedAt) with eventType `iam.token.issued` (FR-001).
- **auth.domain.event [NEW]**: `IssuanceContext.kt` — enum discriminator: LOGIN, REGISTRATION, TOKEN_REFRESH, MFA_COMPLETION, SSO (FR-002).
- **auth.domain.event [NEW]**: `TokenRevokedEvent.kt` — revocation domain event (userId, revocationType, revokedTokenHash, revokedAccessTokenJti, revokedCount, reason, revokedAt) with eventType `iam.token.revoked` (FR-003).
- **auth.domain.event [NEW]**: `RevocationType.kt` — enum: ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE (FR-004).
- **auth.domain.model [NEW]**: `TokenIssuanceMetadata.kt` — contextual data for event enrichment (issuanceContext, ipAddress, userAgent, correlationId, previousRefreshTokenHash) (FR-005).
- **auth.application.event [NEW]**: `TokenEventRecorder.kt` — helper service delegating to `EventService.record()` for token issuance/revocation events (FR-006).
- **auth.application [MODIFY]**: `JwtService.kt` — add optional `jti: String? = null` param to `generateAccessToken()` and `generateRefreshToken()` for JTI pre-generation (FR-007).
- **auth.application.command [MODIFY]**: `TokenGenerator.kt` — inject TokenEventRecorder, add optional `metadata: TokenIssuanceMetadata?` param, pre-generate JTI, record issuance event after token storage (FR-008).
- **auth.application.command [MODIFY]**: `RefreshTokenHandler.kt` — inject TokenEventRecorder, record `TokenRevokedEvent(ROTATION)` after old token revocation, pass metadata to generateAuthResponse() (FR-009).
- **auth.application [MODIFY]**: `AuthService.kt` — inject TokenEventRecorder, record `TokenRevokedEvent(BULK_REVOKE)` in `revokeAllSessions()` (FR-010).
- **auth.application.command [MODIFY]**: `LoginHandler.kt`, `RegisterHandler.kt` — pass `TokenIssuanceMetadata` with appropriate `IssuanceContext` to `TokenGenerator.generateAuthResponse()` (FR-011).

**Total**: ~6 new files, ~5 modified files. 15 FRs. ~3-5 developer-days effort.

---

## 1. Executive Summary

Nâng cấp JWT token lifecycle trong auth-service lên production-grade Event Sourcing. Hiện tại, `TokenGenerator.generateAuthResponse()` — central token factory dùng bởi LoginHandler, RegisterHandler, RefreshTokenHandler — **KHÔNG record domain event nào** khi issue tokens. Tương tự, token revocation (rotation, logout, bulk revoke) cũng không có audit trail. Feature này tạo `TokenIssuedEvent` (unified với `IssuanceContext` discriminator) và `TokenRevokedEvent` (với `RevocationType` discriminator), integrate vào token lifecycle qua `TokenEventRecorder` helper service, và tận dụng hoàn toàn Event Sourcing infrastructure đã thiết lập bởi `user_registration_event` (EventService, EventEnvelope, event_store + outbox, OutboxPoller → Kafka).

### Business Value
- **Complete audit trail**: Mọi token issuance/revocation đều được persist vào event store — đáp ứng SOC2/ISO27001 compliance cho security audit.
- **Token lifecycle visibility**: Downstream consumers (SIEM, analytics) có thể subscribe Kafka topics `iam.token.issued`/`iam.token.revoked` cho real-time monitoring.
- **Rotation tracking**: Mỗi token refresh ghi lại cả revocation (old token) và issuance (new token) với shared correlationId — truy vết rotation chain.
- **Forensic analysis**: Token events capture roles/permissions tại thời điểm issuance — audit "who had access to what, when".
- **Zero new infrastructure**: Tận dụng 100% existing EventService, event_store, event_outbox, OutboxPoller, KafkaTemplate.

### Key Metrics
| Metric | Value |
|--------|-------|
| Functional Requirements | 15 (Idea: 12, Enriched: 3) |
| New Kotlin files | 6 |
| Modified files | 5 |
| New DB tables | 0 (reuse existing event_store + event_outbox) |
| New Kafka topics | 2 (iam.token.issued, iam.token.revoked) |
| Estimated effort | 3-5 developer-days |

---

## 2. Problem Statement

auth-service hiện tại có gap nghiêm trọng trong token lifecycle audit trail:

1. **No token issuance audit**: `TokenGenerator.generateAuthResponse()` generates access + refresh tokens nhưng KHÔNG record domain event. Mọi token issuance — từ login, registration, refresh, MFA completion — đều "silent". Không biết ai được cấp token, khi nào, với permissions gì.

2. **No token revocation audit**: `RefreshTokenHandler` revokes old refresh token khi rotation nhưng KHÔNG record event. `AuthService.revokeAllSessions()` bulk revokes nhưng cũng không record. Không có audit trail cho token lifecycle end.

3. **Security compliance gap**: SOC2 Control CC7.1 yêu cầu "detect unauthorized access" — thiếu token issuance/revocation events khiến impossible trace token usage patterns.

4. **No downstream visibility**: Không có Kafka events cho token lifecycle → SIEM/SOC systems không thể monitor token anomalies (unusual issuance patterns, bulk revocations, rapid rotation).

5. **No correlation**: Không liên kết được token issuance với login event (correlationId missing) — forensic analysis phải correlate manually bằng timestamp.

---

## 3. Scope Definition

### In Scope — This Feature
| FR | Description | Priority |
|----|-------------|----------|
| FR-001 | TokenIssuedEvent domain event | P0 |
| FR-002 | IssuanceContext enum | P0 |
| FR-003 | TokenRevokedEvent domain event | P0 |
| FR-004 | RevocationType enum | P0 |
| FR-005 | TokenIssuanceMetadata data class | P0 |
| FR-006 | TokenEventRecorder helper service | P0 |
| FR-007 | JwtService JTI pre-generation | P1 |
| FR-008 | TokenGenerator integration | P0 |
| FR-009 | RefreshTokenHandler integration | P0 |
| FR-010 | AuthService revocation events | P1 |
| FR-011 | Callers pass IssuanceContext | P1 |
| FR-012 | Kafka topic configuration | P1 |
| FR-013 | Transaction logging | P2 |
| FR-014 | Idempotent recording | P2 |
| FR-015 | Error handling for event recording failure | P1 |

### Out of Scope — Future Features
| Item | Reason |
|------|--------|
| Anonymous token events | Low security value, high volume — defer |
| Service-to-service token events | Infrastructure level — defer |
| Token introspection events | Read-only, separate feature |
| Event store retention/cleanup | Operational concern — separate feature |
| MFA flow full context | Depends on `user_login_event` MFA integration |
| Token event read projections | CQRS query side — separate feature |

---

## 4. Architecture Decision

### Selected: Approach A — Unified TokenIssuedEvent via EventService.record()

**Rationale** (from brainstorm analysis):
1. **Proven pattern**: Follows exact same architecture as `user_registration_event` (EventService.record() in RegisterHandler) and `user_login_event` (EventService.record() in LoginHandler). Copy-paste level of confidence.
2. **Transaction safety**: All callers of TokenGenerator have `@Transactional`. EventService.record() participates in same TX.
3. **Unified events**: Single `TokenIssuedEvent` + `IssuanceContext` discriminator → simple downstream consumption (1 topic).
4. **JTI pre-generation**: Generate UUID before JwtService call, pass as parameter → both JWT and event have same JTI without double-parsing.
5. **Backward compatible**: Optional parameters with defaults. Zero breaking changes.
6. **Minimal scope**: 6 new files, 5 modified files. No new infrastructure.

**Rejected approaches**:
- **Approach B: AOP-Based** — Breaks transaction consistency, loses domain context (IP, correlationId), hidden behavior, inconsistent with existing explicit pattern.
- **Approach C: JwtService level** — Violates SRP (utility + event producer), no domain context, wrong transaction boundary.

---

## 5. Design Highlights

### 5.1 Event Data Architecture

```
┌────────────────────────────────────────────────────────────┐
│ EventEnvelope<TokenIssuedEvent>                            │
│ type: "iam.token.issued" | topic: iam.token.issued         │
│ source: auth-service | correlationId: UUID                 │
│ data:                                                      │
│   userId, username, domainCode, domainId                   │
│   issuanceContext: LOGIN|REGISTRATION|TOKEN_REFRESH|...    │
│   accessTokenJti (UUID) — for correlation                  │
│   refreshTokenHash (SHA-256) — for rotation tracking       │
│   roles[], permissions[] — audit snapshot                  │
│   accessTokenExpiresAt, refreshTokenExpiresAt              │
│   ipAddress?, userAgent?, previousRefreshTokenHash?        │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ EventEnvelope<TokenRevokedEvent>                           │
│ type: "iam.token.revoked" | topic: iam.token.revoked       │
│ data:                                                      │
│   userId, revocationType: ROTATION|LOGOUT|BULK_REVOKE|...  │
│   revokedTokenHash?, revokedAccessTokenJti?                │
│   revokedCount (for bulk), reason?                         │
└────────────────────────────────────────────────────────────┘
```

### 5.2 Transaction Flow — Token Refresh (most complex)

```
┌─ RefreshTokenHandler @Transactional ───────────────────────┐
│                                                             │
│  1. tokenStore.findValidRefreshToken()   — read             │
│  2. tokenStore.revokeToken()             — revoke old       │
│  3. tokenEventRecorder.recordRevocation()  ← NEW            │
│     └── EventService.record() → event_store + outbox        │
│  4. tokenGenerator.generateAuthResponse(metadata) — NEW     │
│     ├── pre-gen JTI                                         │
│     ├── jwtService.generateAccessToken(jti=...)             │
│     ├── jwtService.generateRefreshToken()                   │
│     ├── tokenStore.saveRefreshToken()                       │
│     └── tokenEventRecorder.recordIssuance()  ← NEW          │
│         └── EventService.record() → event_store + outbox    │
│  5. return AuthToken                                        │
│                                                             │
│  All operations commit together or rollback together        │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Backward Compatibility

| Change | Strategy |
|--------|----------|
| `TokenGenerator.generateAuthResponse()` | Add optional `metadata: TokenIssuanceMetadata? = null` — existing callers pass nothing |
| `JwtService.generateAccessToken()` | Add optional `jti: String? = null` — if null, generate UUID internally |
| `JwtService.generateRefreshToken()` | Add optional `jti: String? = null` — same strategy |
| `TokenGenerator` constructor | Add `TokenEventRecorder` param — Spring DI handles automatically |

---

## 6. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|---|
| TokenGenerator constructor size (11 params) | LOW | LOW | TokenEventRecorder encapsulates event logic — only 1 new dependency |
| Event store growth (high token volume) | MEDIUM | MEDIUM | Monitor event_store size; add TTL cleanup in future feature |
| MFA flow partial context | MEDIUM | LOW | Defer full MFA context; record partial event with issuanceContext=MFA_COMPLETION |
| Double event in RefreshTokenHandler | LOW | LOW | 2 INSERTs per refresh is negligible; same TX, same commit |
| AuthService revokeAllSessions scope | LOW | MEDIUM | Only record 1 BULK_REVOKE event with revokedCount — not per-token |

---

## 7. Success Criteria

- [ ] Every `TokenGenerator.generateAuthResponse()` call records a `TokenIssuedEvent` in event_store
- [ ] Every token refresh records both `TokenRevokedEvent(ROTATION)` and `TokenIssuedEvent(TOKEN_REFRESH)`
- [ ] `AuthService.revokeAllSessions()` records `TokenRevokedEvent(BULK_REVOKE)` with count
- [ ] Events relay to Kafka via OutboxPoller (existing infrastructure)
- [ ] All events in same `@Transactional` boundary as token operations
- [ ] Zero breaking changes to existing callers (backward compatible)
- [ ] JTI in JWT = JTI in TokenIssuedEvent (no double-parsing)
- [ ] Token issuance still succeeds even if event recording fails (FR-015)
