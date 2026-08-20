# Proposal: Auth Core Features — Final Completion & Production Readiness

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach B — Remaining Gaps Closure + Testing Validation + Production Readiness (from brainstorm)
> **_Generated**: 2026-08-20 (v3 — merged from v2 2026-08-19)
> **Status**: ~95% implemented — focused on 4 micro-gaps + quality items

## Changes

[CHANGED] Scope reassessed from v2 (3 gaps — GAP-001/002/003 all now IMPLEMENTED) → v3 (4 micro-gaps — Gap A/B/C/D from brainstorm 2026-08-20). D11-D14 design decisions from previous brainstorm fully implemented in code.

---

## Why

Auth-service has reached ~95% implementation maturity across all 17 FRs. The previous brainstorm (2026-08-19) identified 3 major gaps (config-driven SSO, trusted device save, SSO provisioning event) — **all 3 are now IMPLEMENTED**. However, a fresh code scan (2026-08-20) uncovered 4 remaining micro-gaps that affect correctness and consistency:

1. **🔴 Gap A — `revokeAllSessions()` hollow implementation**: `AuthService.revokeAllSessions()` returns `0` and doesn't actually revoke refresh tokens or clean up login sessions. FR-012 (Force Logout) is marked IMPLEMENTED but the implementation is functionally empty — admin clicks "Force Logout" → nothing happens.
2. **🟡 Gap B — `SsoAdapter.getProviders()` hardcoded list**: Returns hardcoded `[google, microsoft, keycloak]` while `OAuth2TokenExchanger` is now config-driven. If Keycloak is NOT in providers config → user sees Keycloak button → clicks → 500 error.
3. **🟢 Gap C — Stale TODO in `SsoAdapter.kt:170`**: `exchangeCodeForUser()` has a TODO comment that's already resolved by `OAuth2TokenExchanger`. Should be removed for code cleanliness.
4. **🟡 Gap D — `domainId` resolution in password change controllers**: `AuthController.kt:67` and `CqrsAuthController.kt:197` have TODO comments for resolving `domainId` from authenticated user's active domain. `PasswordPolicyService` requires `domainId` for domain-specific policy lookup.

Additionally:
5. **TEST — Integration test validation**: 8 integration + 11 unit tests exist. 3 additional test scenarios needed for the gap fixes.
6. **DOC — SecurityProperties documentation**: Default values not documented (quality score deduction).

## Changes

- **Gap A: revokeAllSessions() full implementation** — Complete `AuthService.revokeAllSessions()` with: (1) revoke all refresh tokens via `RefreshTokenRepository`, (2) end all login sessions via `LoginSessionService`, (3) audit log. Accept that access tokens expire naturally (≤15min). Add `revokeAllByUserId()` query to `RefreshTokenRepository`. ~30 lines changed, 3 files.
- **Gap B: SsoAdapter.getProviders() config-driven** — Replace hardcoded provider list with `securityProperties.sso.providers.filter { enabled }.map { ... }`. ~10 lines changed, 1 file.
- **Gap C: Stale TODO cleanup** — Remove resolved TODO comment in `SsoAdapter.kt:170`. 1 line removed, 1 file.
- **Gap D: domainId resolution** — Resolve `domainId` from authenticated user's active domain in `AuthController` and `CqrsAuthController` for password change endpoints. Use `DomainLookupService` or `UserDomainRepository`. ~15 lines changed, 2 files.
- **TEST-003: Gap-specific tests** — 3 new test methods: revokeAllSessions integration test, getProviders config-driven test, domainId password change test.
- **DOC-001: SecurityProperties documentation** — KDoc comments for default values.

**Total**: 7 files modified, 0 files new (test methods added to existing test classes), ~5.5h effort.

## Capabilities

### Fixed Capabilities
- `force-logout-completion`: `AuthService.revokeAllSessions()` actually revokes refresh tokens + cleans login sessions (Gap A)
- `sso-providers-config-consistency`: `SsoAdapter.getProviders()` reads from same config source as `OAuth2TokenExchanger` (Gap B)
- `password-change-domain-resolution`: Password change endpoints resolve `domainId` from authenticated user's active domain (Gap D)

### Unchanged Capabilities (REUSE — Fully Implemented)
- `mfa-otp-sms-email` — OtpService + MfaService (FR-001) ✅
- `mfa-totp-authenticator` — TotpService + AES-256-GCM encryption (FR-002) ✅
- `mfa-settings-management` — MfaService.updateSettings() + MfaController (FR-003) ✅
- `captcha-integration` — CaptchaVerifier + AltchaCaptchaVerifier (FR-004) ✅
- `trusted-device-persistence` — MfaService saves trustedDeviceHash after MFA verify success (FR-005) ✅ [was Gap, now IMPLEMENTED]
- `sso-oauth2-login` — SsoAdapter + OAuth2TokenExchanger config-driven for Google/Microsoft/Keycloak (FR-006) ✅ [was Gap, now IMPLEMENTED]
- `sso-jit-provisioning` — SsoAdapter.handleCallback() + EventPublisher.publish(SsoProvisionedEvent) (FR-007) ✅ [was Gap, now IMPLEMENTED]
- `sso-identity-linking` — SsoAdapter.linkIdentity/unlinkIdentity (FR-008) ✅
- `jwt-rs256-signing` — JwtService dual-key RS256+HMAC fallback (FR-009) ✅
- `jwks-endpoint` — TokenController.jwks() with Cache-Control (FR-010) ✅
- `token-introspection` — TokenController.introspect() RFC 7662 (FR-011) ✅
- `force-logout` — AdminSessionController + RevokeSessionsHandler (FR-012) ⚠️ PARTIAL [Gap A — hollow impl]
- `password-policy-passay` — PasswordPolicyService + Passay + cache (FR-013) ✅
- `password-history` — PasswordPolicyService.checkPasswordHistory() (FR-014) ✅
- `mfa-verify-idempotency` — OtpService Redis DEL after success (FR-015) ✅
- `audit-logging` — AuditLogService + AuditAction enum (25 actions) (FR-016) ✅
- `idp-timeout-handling` — SsoProviderTimeoutException (FR-017) ✅

## Impact

### Backend (auth-service)

**MODIFY** (7 existing files):
- `AuthService.kt` — complete `revokeAllSessions()` with refresh token revocation + session cleanup (~20 lines)
- `TokenStorePersistenceAdapter.kt` — add batch revocation support or delegate to `RefreshTokenRepository` (~5 lines)
- `SsoAdapter.kt` — replace hardcoded `getProviders()` with config lookup + remove stale TODO (~15 lines)
- `AuthController.kt` — resolve `domainId` from user's active domain for password change (~8 lines)
- `CqrsAuthController.kt` — resolve `domainId` from user's active domain for password change (~8 lines)
- `SecurityProperties.kt` — add KDoc documentation for default values (~15 lines comments)
- Existing test classes — add 3 new test methods (~50 lines)

**NEW** (0 files):
- No new production or test files needed — tests added to existing integration test classes

### Database
- **No changes** — all tables and columns already exist from prior migrations

### External Systems
- **No changes** — same Redis, OAuth2, PostgreSQL, Kafka integrations

### Cross-Feature Coordination
- `LoginHandler.kt` — no change needed (FR-005 trusted device uses existing `requiresMfa()`)
- `AuthCoreExceptions.kt` — no change needed (all exceptions exist)
- `SecurityProperties.kt` — documentation-only change, no overlap with other features
- Prior features (`anonymous-login-optimization`, `api-response-i18n-standard`) — ✅ ARCHIVED, no conflicts
