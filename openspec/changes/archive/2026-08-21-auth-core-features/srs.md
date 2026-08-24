# SRS: Auth Core Features — Testing, Hardening & Legacy Cleanup (v6)

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach D — Hybrid: Testing + Hardening + Legacy Cleanup (from brainstorm v6)
> **_Generated**: 2026-08-26 (v6 — delta from v5 2026-08-25)
> **Status**: ~98% implemented — this SRS covers legacy path security fix + comprehensive testing
> **Archive**: `openspec/changes/archive/2026-08-21-auth-core-features/srs.md` (v5)

---

## Changes

[CHANGED] v5→v6: Both v5 gaps (Keycloak + TTL enforcement + mapper + adapter) confirmed RESOLVED in codebase.
[CHANGED] v5 SRS Gap Specifications (Sections 4.1, 4.1b, 4.2) — ALL IMPLEMENTED. Removed from this version.
[NEW] Gap 1: AuthService.kt legacy login path security fix (trusted device TTL bypass).
[NEW] Gap 2: SecurityProperties.kt stale KDoc update.
[NEW] Gap 3: Comprehensive test coverage (~10 new test files for untested subsystems).
[UNCHANGED] All 17 FR status unchanged — all IMPLEMENTED. Only FR-005 has a secondary path (legacy) needing alignment.

---

## 1. Functional Requirements

### 1.1 MFA — Multi-Factor Authentication

#### FR-001: OTP SMS/Email Verification [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.kt`, `MfaService.kt`
- **Input**: `POST /api/auth/mfa/verify` — `{ mfaToken, code }`
- **Process**: Redis GET `otp:{userId}:{channel}` → constant-time compare → check attempts
- **Output**: `AuthResponse` (success) or `MFA_CODE_INVALID` (401)
- **Error codes**: `MFA_CODE_INVALID` (AUTH_011), `MFA_TOKEN_EXPIRED` (AUTH_012), `MFA_MAX_ATTEMPTS` (AUTH_013)
- **Business Rules**: TTL=300s, max 3 attempts, Redis DEL on success (idempotency), `SecureRandom` 6-digit

#### FR-002: TOTP Authenticator App [REUSE]
- **Status**: ✅ IMPLEMENTED — `TotpService.kt`
- **Input**: Setup: `POST /api/auth/mfa/totp/setup` → Confirm: `POST /api/auth/mfa/totp/confirm`
- **Process**: Generate TOTP secret → AES-256-GCM encrypt → Redis pending → confirm → DB store
- **Output**: `{ secret, qrCodeUri, issuer }` | Confirm: `{ success }`
- **Error codes**: `MFA_CODE_INVALID` (AUTH_011)
- **Library**: `dev.samstevens.totp:totp:1.7.1`

#### FR-003: MFA Settings Management [REUSE]
- **Status**: ✅ IMPLEMENTED — `MfaService.updateSettings()`, `MfaController.kt`
- **Input**: `PUT /api/auth/mfa/settings` — `{ enabled, method }`
- **Output**: `{ mfaEnabled, mfaMethod }`

#### FR-004: CAPTCHA Integration [REUSE]
- **Status**: ✅ IMPLEMENTED — `CaptchaVerifier.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaController.kt`
- **Input**: `LoginCommand.captchaToken?: String`
- **Error codes**: `CAPTCHA_REQUIRED` (AUTH_007), `CAPTCHA_FAILED` (AUTH_008)
- **Interface**: `CaptchaVerifier { fun verify(token: String): Boolean }`

#### FR-005: Trusted Device (Skip MFA) [REUSE — CQRS path ✅ | Legacy path ⚠️]
- **Status**: ✅ IMPLEMENTED on CQRS path (`LoginHandler`) | ⚠️ SECURITY GAP on legacy path (`AuthService`)
- **CQRS path** (fully enforced):
  - `LoginHandler.handle()` → `user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)`
  - `User.requiresMfa(deviceHash, ttlDays)` checks hash match AND TTL via `trustedDeviceSetAt + ttlDays > now()`
  - `UserTest.kt` has 13 test cases including TTL expiry edge cases
- **Legacy path** (security gap — Gap 1):
  - `AuthService.login()` (lines 131-137) performs `trustedHash == user.trustedDeviceHash` without TTL check
  - Expired device hash grants indefinite MFA bypass on legacy path
  - Fix: Align to use `User.requiresMfa()` or deprecate legacy path
- **Business Rules**: SHA-256 fingerprint, TTL = 30 days (configurable), null `trustedDeviceSetAt` = expired, password change clears trust

### 1.2 SSO — OAuth2 Integration

#### FR-006: OAuth2 SSO Login [REUSE]
- **Status**: ✅ IMPLEMENTED — Google, Microsoft, Keycloak all config-driven
- **Input**: `POST /api/auth/sso/callback` — `{ code, provider, redirectUri }`
- **Output**: `AuthResponse`
- **Error codes**: `SSO_TOKEN_INVALID` (AUTH_014)
- **Config**: `app.security.sso.providers.{name}.token-endpoint`, `.user-info-endpoint`
- [CHANGED] v5→v6: Keycloak confirmed working. `SsoCallbackIntegrationTest` TC3 passing.

#### FR-007: JIT User Provisioning from SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` JIT + `KafkaEventPublisher` + `EventService` + `OutboxPoller`
- **Business Rules**: Identity linked by `sub` claim. `KafkaEventPublisher` retry 3 attempts. Transactional outbox relay.

#### FR-008: Identity Linking/Unlinking [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()`, `SsoAdapter.unlinkIdentity()`, `SsoController.kt`
- **Error codes**: `SSO_IDENTITY_CONFLICT` (AUTH_016), `CANNOT_UNLINK_LAST_IDENTITY`

### 1.3 Token Management — RS256

#### FR-009: JWT RS256 Signing [REUSE]
- **Status**: ✅ IMPLEMENTED — `JwtService.kt` dual RS256+HMAC, `TokenEventRecorder` records issuance events

#### FR-010: JWKS Endpoint [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.jwks()`, Cache-Control: `max-age=86400, public`

#### FR-011: Token Introspection (RFC 7662) [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.introspect()`, jti blacklist check via `TokenBlacklistRepository`

#### FR-012: Force Logout (Session Revocation) [REUSE]
- **Status**: ✅ IMPLEMENTED — `AdminSessionController.kt` + `RevokeSessionsHandler.kt` + `TokenEventRecorder.recordRevocation()`

### 1.4 Password Policy

#### FR-013: Dynamic Password Policy per Domain [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` with Passay, cache, domain-scoped. Password change clears trusted device.

#### FR-014: Password History [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()` + `pruneHistory()`

### 1.5 Enriched Requirements

#### FR-015: MFA Verify Idempotency [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` Redis DEL + `IdempotencyFilter.kt`

#### FR-016: Audit Logging for MFA/SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt`, `AuditAction` enum (25 actions), `AuditLogEntity` + `AuditLogRepository`

#### FR-017: IdP Timeout Handling [REUSE]
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout → `SsoProviderTimeoutException`

---

## 2. Non-Functional Requirements

| ID | Requirement | Target | Status |
|-----|------------|--------|--------|
| NFR-001 | MFA verification latency | ≤ 200ms | ✅ Met |
| NFR-002 | RS256 signing/verification | < 50ms per token | ✅ Met |
| NFR-003 | JWKS endpoint cache | TTL = 24h | ✅ Met |
| NFR-004 | CAPTCHA verification timeout | ≤ 5s | ✅ Met |
| NFR-005 | Password validator cache hit ratio | > 95% | ✅ Met |
| NFR-006 | Login endpoint P95 latency | < 500ms | ✅ Met |
| NFR-007 | Kafka event publish retry | 3 attempts, exponential backoff | ✅ Met |
| NFR-008 | Outbox poller batch | batch=50, poll=100ms, maxRetries=3 | ✅ Met |
| NFR-009 | Event store append-only | unique constraint on (aggregate_type, aggregate_id, sequence_number) | ✅ Met |
| NFR-010 | [NEW] Test coverage target | ≥80% line coverage on all application services | Target |

---

## 3. Error Code Registry

No new error codes for v6. All 52 error codes (AUTH_001-AUTH_062) already exist and cover all scenarios.

| Code | HTTP | Description | FR | Status |
|------|------|-------------|-----|--------|
| AUTH_001-AUTH_021 | Various | Auth core errors | FR-001 to FR-014 | ✅ Exists |
| AUTH_030-AUTH_039 | Various | E2EE errors | E2EE | ✅ Exists |
| AUTH_040-AUTH_044 | Various | Anonymous session errors | Anonymous | ✅ Exists |
| AUTH_050-AUTH_053 | Various | Event sourcing errors | Event sourcing | ✅ Exists |
| AUTH_060-AUTH_062 | Various | Inter-service auth errors | Service auth | ✅ Exists |

---

## 4. Gap Specifications (v6)

### 4.1 Gap 1: AuthService.kt Legacy Login Path — TTL Security Fix [MODIFY]

**Current state (code-verified 2026-08-26)**:
- `AuthService.login()` lines 131-137 — trusted device check WITHOUT TTL:
  ```kotlin
  if (user.mfaEnabled && user.mfaMethod != "NONE") {
      val trustedHash = request.trustedDeviceHash
      if (trustedHash != null && trustedHash == user.trustedDeviceHash) {
          log.debug("Trusted device matched — skipping MFA")
      } else {
          return mfaService.initiateMfa(user.id!!, user.mfaMethod)
      }
  }
  ```
- This bypasses MFA indefinitely for a known device hash — no TTL expiry check
- The CQRS path (`LoginHandler.handle()`) correctly uses `User.requiresMfa(hash, ttlDays)` with TTL enforcement

**Target state**:
```kotlin
// Option A: Align with domain method (RECOMMENDED)
val domainUser = userEntityMapper.toDomain(user)
if (domainUser.requiresMfa(request.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
    return mfaService.initiateMfa(user.id!!, user.mfaMethod)
}

// Add deprecation notice on login() method:
@Deprecated("Use LoginHandler.handle() via CqrsAuthController. Legacy path retained for backward compatibility.")
```

**Business Rules**:
- Legacy path must enforce same TTL as CQRS path
- `@Deprecated` annotation signals future consolidation
- No behavioral change for non-trusted-device logins
- Existing MFA flow unchanged — only trusted device TTL is aligned

### 4.2 Gap 2: SecurityProperties.kt KDoc Update [MODIFY]

**Current state**:
- Line 14-15: `"TTL enforcement deferred to future migration"` — **STALE**

**Target state**:
```kotlin
// "Trusted device TTL enforcement implemented in User.requiresMfa() via trustedDeviceSetAt column (V10 migration). Default: 30 days."
```

### 4.3 Gap 3: Test Coverage Expansion [NEW — 10+ test files]

**Rationale**: Event sourcing infrastructure, CQRS handlers, admin endpoints, security filters, and MFA recovery codes all have ZERO test coverage. These are critical paths that need validation.

See Section 5 below for detailed test specifications.

---

## 5. Test Specifications (v6 — Comprehensive Coverage)

### 5.1 Unit Tests — Application Services

| # | Test File | Target Class | Priority | Key Scenarios |
|---|-----------|-------------|----------|---------------|
| T1 | `LoginHandlerTest.kt` | `LoginHandler` | HIGH | Login command validation, MFA checkpoint with TTL, password expiry check, session policy enforcement, CAPTCHA gate, token event recording, audit logging |
| T2 | `EventServiceTest.kt` | `EventService` | HIGH | Event envelope creation, event store persist, outbox insert, transactional consistency, error handling |
| T3 | `TokenEventRecorderTest.kt` | `TokenEventRecorder` | HIGH | Record issuance event (with IssuanceContext variants), record revocation event (with RevocationType variants), EventService delegation |
| T4 | `CaptchaVerifierTest.kt` | `CaptchaVerifier` / `AltchaCaptchaVerifier` | MEDIUM | Interface contract, ALTCHA PoW verification, failure scenarios, noop verifier for testing |

### 5.2 Integration Tests — Adapters & Filters

| # | Test File | Target | Priority | Key Scenarios |
|---|-----------|--------|----------|---------------|
| T5 | `OutboxPollerTest.kt` | `OutboxPoller` | HIGH | Batch SELECT FOR UPDATE SKIP LOCKED, Kafka publish success, timeout (no retry increment), failure (retry increment, max 3), batch boundary (0, 1, 50, 51), empty outbox noop |
| T6 | `AdminSessionControllerTest.kt` | `AdminSessionController` | MEDIUM | Force logout (revoke all sessions for user), list active sessions, session statistics, admin auth required, non-admin denied |
| T7 | `IdempotencyFilterTest.kt` | `IdempotencyFilter` | MEDIUM | X-Idempotency-Key header detected, Redis key creation with TTL 24h, duplicate request returns cached response, different keys → independent processing, missing header → passthrough |
| T8 | `MfaRecoveryCodeFlowTest.kt` | MFA recovery code flow | MEDIUM | Generate 10 recovery codes (SHA-256 hashed), verify valid code (single-use → deleted), reject used code, reject invalid code, count remaining codes |
| T9 | `LoginRateLimitFilterTest.kt` | `LoginRateLimitFilter` | MEDIUM | IP-based rate limiting, username-based limiting, rate limit reset after success, Redis sliding window, fail-open when Redis unavailable |
| T10 | `PasswordExpiryLoginTest.kt` | Login with expired password | LOW | Login → password expired → HTTP 403 (`AUTH_018`), login → password not expired → success, password expiry config per domain |

### 5.3 Legacy Path Test

| # | Test File | Target | Priority | Key Scenarios |
|---|-----------|--------|----------|---------------|
| T11 | `LegacyLoginTtlTest.kt` | `AuthService.login()` legacy path | HIGH | After fix: trusted device with valid TTL → skip MFA, expired TTL → require MFA, null setAt → require MFA, no trusted hash → require MFA |

### 5.4 Test Infrastructure

- **Mock strategy (unit tests)**: Mockito/MockK for service dependencies
- **Integration test stack**: `@SpringBootTest` + `@Testcontainers` (PostgreSQL, Redis) + `@AutoConfigureMockMvc`
- **Kafka tests**: `@EmbeddedKafka` for OutboxPoller or mock `KafkaTemplate`
- **WireMock**: Already used in `SsoCallbackIntegrationTest` for SSO provider mocking

---

## 6. FR Traceability

| FR-ID | Status | v6 Gap | Task Required |
|-------|--------|--------|---------------|
| FR-001 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-002 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-003 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-004 | ✅ IMPLEMENTED | Gap 3 (T4) | CaptchaVerifier test |
| FR-005 | ✅ IMPLEMENTED (CQRS) / ⚠️ Legacy | Gap 1, Gap 3 (T1, T11) | Legacy path fix + tests |
| FR-006 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-007 | ✅ IMPLEMENTED | Gap 3 (T2, T3, T5) | Event sourcing tests |
| FR-008 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-009 | ✅ IMPLEMENTED | Gap 3 (T3) | TokenEventRecorder test |
| FR-010 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-011 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-012 | ✅ IMPLEMENTED | Gap 3 (T6) | AdminSessionController test |
| FR-013 | ✅ IMPLEMENTED | Gap 3 (T10) | Password expiry login test |
| FR-014 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-015 | ✅ IMPLEMENTED | Gap 3 (T7) | IdempotencyFilter test |
| FR-016 | ✅ IMPLEMENTED | — | None (tests exist) |
| FR-017 | ✅ IMPLEMENTED | — | None (tests exist) |

**Coverage**: 17/17 FRs addressed. 17 IMPLEMENTED on CQRS path. 1 legacy path security fix (FR-005). 10+ new test files for validation.

---

## 7. Design Decisions (v6)

| # | Decision | Status | Reasoning |
|---|----------|--------|-----------|
| D1-D26 | Prior decisions | ✅ ALL IMPLEMENTED | See v5 archive |
| D27 | **Fix legacy AuthService.login() TTL, deprecate method** | PENDING | Security: legacy path bypasses TTL enforcement. Align with `User.requiresMfa()`. |
| D28 | **Fix SecurityProperties.kt KDoc** | PENDING | Documentation accuracy — "deferred" is now FALSE |
| D29 | **Unit tests mock ports/services; integration tests use Testcontainers** | PENDING | Standard Spring Boot test architecture. Unit tests are fast and isolated. |
| D30 | **OutboxPoller test uses @EmbeddedKafka** | PENDING | Full round-trip validation: outbox → poller → Kafka topic. Alternative: mock KafkaTemplate. |
| D31 | **MFA recovery code endpoints added to MfaController** | ⚠️ OPEN QUESTION Q4 | Entity + repository exist. Controller endpoints not yet found. Recommend adding to `MfaController`. |
| D32 | **Legacy AuthService.login() kept for backward compatibility** | PENDING | `@Deprecated` with fix, not removal. CQRS path (`/api/v2/auth/login`) is preferred. Future consolidation planned. |
