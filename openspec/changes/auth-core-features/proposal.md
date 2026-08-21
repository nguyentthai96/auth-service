# Proposal: Auth Core Features — Testing, Hardening & Legacy Cleanup (v6)

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach D — Hybrid: Testing + Hardening + Legacy Cleanup (from brainstorm v6)
> **_Generated**: 2026-08-26 (v6 — delta from v5 2026-08-25)
> **Status**: ~98% implemented — both v5 gaps (Keycloak + TTL) RESOLVED in code. Focus: testing validation, legacy path security fix, and documentation updates
> **Archive**: `openspec/changes/archive/2026-08-21-auth-core-features/proposal.md` (v5)

## Changes

[CHANGED] v5→v6: Codebase re-scan 2026-08-26 reveals BOTH v5 gaps are already resolved:
  - Gap 1 (Keycloak support): `OAuth2TokenExchanger` is config-driven. Keycloak configured in `application-security.yml`. Integration test passing.
  - Gap 2 (Trusted device TTL): `User.requiresMfa(deviceHash, ttlDays)` implemented with full TTL enforcement. `UserTest.kt` has 13 test cases.
[CHANGED] v5 tasks (mapper fix, TTL logic, adapter fix) — ALL ALREADY DONE in codebase. v5 tasks are obsolete.
[NEW] Discovery: `AuthService.kt` (legacy login path) has security gap — trusted device check WITHOUT TTL enforcement (line 131-137).
[NEW] Discovery: `SecurityProperties.kt` KDoc stale — says "TTL enforcement deferred to future migration" but TTL is implemented.
[NEW] Discovery: Event Sourcing pipeline (EventService, TokenEventRecorder, OutboxPoller) has ZERO test coverage.
[NEW] Focus shift: Testing (10+ new test files), legacy path security fix, documentation cleanup.

---

## Why

Auth-service has reached ~98% implementation maturity across all 17 FRs. The v5 archival tasks (mapper fix, TTL enforcement, adapter fix) were completed between v5 and v6. Codebase re-scan on 2026-08-26 confirms:

1. **v5 Gap 1 (Keycloak) — RESOLVED**: `OAuth2TokenExchanger` is fully config-driven via `securityProperties.sso.providers[provider]`. Keycloak endpoint configured in `application-security.yml`. `SsoCallbackIntegrationTest` TC3 verifies Keycloak flow and is passing.

2. **v5 Gap 2 (Trusted Device TTL) — RESOLVED**: `User.requiresMfa(deviceHash, ttlDays)` domain method implements full TTL enforcement. `LoginHandler` passes `securityProperties.mfa.trustedDeviceTtlDays`. `UserTest.kt` has 13 test cases including TTL edge cases.

3. **v5 Gap 3 (Mapper + Adapter) — RESOLVED**: All mapper and adapter fixes from v5 tasks completed.

**However, 3 new items require attention:**

1. **🟡 New Gap 1 — AuthService.kt Legacy Login Path Security Gap** (Security — MEDIUM):
   - `AuthService.login()` (legacy path at `/api/auth/login`) performs trusted device check WITHOUT TTL enforcement
   - `LoginHandler.handle()` (CQRS path at `/api/v2/auth/login`) has full TTL enforcement via `User.requiresMfa()`
   - Both paths exist simultaneously — the legacy path is a security bypass
   - Fix: Align `AuthService.login()` to use `User.requiresMfa()` with TTL, or deprecate legacy path

2. **🟢 New Gap 2 — Stale Documentation** (Quality — LOW):
   - `SecurityProperties.kt` KDoc says "TTL enforcement deferred to future migration" — this is now FALSE
   - Update to reflect current reality: TTL implemented via `User.requiresMfa()` + V10 migration

3. **🔴 New Gap 3 — Comprehensive Test Coverage** (Reliability — HIGH):
   - Event Sourcing pipeline (EventService, TokenEventRecorder, OutboxPoller) — ZERO tests
   - LoginHandler (CQRS command handler) — no unit tests (only integration)
   - AdminSessionController — untested
   - CaptchaVerifier / AltchaCaptchaVerifier — untested
   - IdempotencyFilter — untested
   - MFA Recovery Code flow — untested
   - LoginRateLimitFilter — untested
   - Password expiry on login — untested

## Scope

- **Gap 1: Legacy path TTL fix** — Align `AuthService.login()` to use `User.requiresMfa()` for trusted device TTL enforcement. Add `@Deprecated` annotation with migration note. ~1 file modified, ~10 lines changed.
- **Gap 2: Documentation update** — Fix stale KDoc in `SecurityProperties.kt`. ~1 file, ~3 lines.
- **Gap 3: Test coverage expansion** — Create ~10 new test files covering all untested areas. ~10 new files, ~2000+ lines of test code.

**Total**: 2 production files modified, ~10 new test files, 0 new production files. ~4-5 developer-days effort.

## Capabilities

### New Capabilities
- `legacy-login-ttl-alignment`: `AuthService.login()` aligns with CQRS `LoginHandler` for trusted device TTL enforcement — eliminates security bypass on legacy path
- `comprehensive-test-suite`: 10+ new test files covering event sourcing, CQRS commands, admin endpoints, CAPTCHA, idempotency, MFA recovery codes, rate limiting, password expiry

### Changed Capabilities
- `security-properties-documentation`: Updated KDoc reflects current TTL enforcement reality (no longer "deferred")

### Unchanged Capabilities (REUSE — Fully Implemented)
- `mfa-otp-sms-email` — OtpService + MfaService (FR-001) ✅
- `mfa-totp-authenticator` — TotpService + AES-256-GCM (FR-002) ✅
- `mfa-settings-management` — MfaService.updateSettings() (FR-003) ✅
- `captcha-integration` — CaptchaVerifier + AltchaCaptchaVerifier (FR-004) ✅
- `trusted-device-ttl` — User.requiresMfa(hash, ttlDays) (FR-005) ✅ (CQRS path fully enforced)
- `sso-oauth2-login` — SsoAdapter + OAuth2TokenExchanger config-driven (FR-006) ✅
- `sso-jit-provisioning` — SsoAdapter.handleCallback() + KafkaEventPublisher + EventService (FR-007) ✅
- `sso-identity-linking` — SsoAdapter.linkIdentity/unlinkIdentity (FR-008) ✅
- `jwt-rs256-signing` — JwtService dual-key RS256+HMAC (FR-009) ✅
- `jwks-endpoint` — TokenController.jwks() (FR-010) ✅
- `token-introspection` — TokenController.introspect() RFC 7662 (FR-011) ✅
- `force-logout` — AdminSessionController + RevokeSessionsHandler (FR-012) ✅
- `password-policy-passay` — PasswordPolicyService + Passay + cache (FR-013) ✅
- `password-history` — PasswordPolicyService.checkPasswordHistory() (FR-014) ✅
- `mfa-verify-idempotency` — OtpService Redis DEL + IdempotencyFilter (FR-015) ✅
- `audit-logging` — AuditLogService + AuditAction (25 actions) (FR-016) ✅
- `idp-timeout-handling` — SsoProviderTimeoutException (FR-017) ✅

## Impact

### Backend (auth-service)

**MODIFY** (2 existing production files):
- `AuthService.kt` — Align legacy login path to use `User.requiresMfa()` with TTL. Add `@Deprecated` annotation on `login()` method. (~10 lines)
- `SecurityProperties.kt` — Update stale KDoc on trusted device TTL. (~3 lines)

**NEW** (~10 test files):
- `EventServiceTest.kt` — Event envelope creation, event store + outbox recording
- `TokenEventRecorderTest.kt` — Token issuance and revocation event recording
- `OutboxPollerTest.kt` — Batch processing, retry logic, Kafka publish, edge cases
- `LoginHandlerTest.kt` — CQRS login command: MFA checkpoint, TTL, password expiry, session policy
- `AdminSessionControllerTest.kt` — Admin force logout, list sessions, session stats
- `CaptchaVerifierTest.kt` — CAPTCHA chain verification, noop provider, ALTCHA PoW
- `IdempotencyFilterTest.kt` — Redis key management, TTL 24h, duplicate detection
- `MfaRecoveryCodeFlowTest.kt` — Generate, verify, single-use, count endpoints
- `LoginRateLimitFilterTest.kt` — IP/username/device rate limiting
- `PasswordExpiryLoginTest.kt` — Login with expired password → force change

**NO NEW PRODUCTION FILES** — all code exists, this change focuses on validation and hardening.

### Database
- No changes — all migrations (V1-V15) already applied ✅

### External Systems
- **No changes** — same Redis, Kafka, OAuth2, PostgreSQL integrations

### Cross-Feature Coordination
- `AuthService.kt` — MODIFY (legacy path fix). No conflict with active features.
- Prior features (`anonymous-login-optimization`, `api-response-i18n-standard`, `erp-iam-system`, `jwt_token_issuance`) — ALL ARCHIVED, no conflicts.
