# Proposal: Auth Core Features — Trusted Device Hardening & Final Cleanup (v5)

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach B — Trusted Device TTL Enforcement + Mapper Gap Fix + Adapter Cleanup + Test Coverage (from brainstorm v5)
> **_Generated**: 2026-08-25 (v5 — delta from v4 2026-08-21)
> **Status**: ~95% implemented — focused on 2 functional gaps (FR-005 TTL + mapper gap) + 1 stale adapter + tests
> **Archive**: `openspec/changes/archive/2026-08-20-auth-core-features/proposal.md` (previous version)

## Changes

[CHANGED] v4→v5: Pre_openspec + brainstorm refreshed 2026-08-25 with code re-scan. Quality score 92→93 (Kafka issue confirmed resolved). OAuth2TokenExchanger confirmed config-driven (Keycloak = config-only).
[CHANGED] Mapper gap (D21) elevated to CRITICAL — `trustedDeviceSetAt` not mapped in `UserEntityMapper.kt` despite field existing in both entity and domain model.
[CHANGED] V10 migration confirmed APPLIED — `trusted_device_set_at` column exists on `users` table. No new migration needed.
[CHANGED] `KafkaEventPublisher.kt` confirmed implemented with `@Primary`, `@ConditionalOnProperty`, 3-retry exponential backoff.
[CHANGED] `OAuth2TokenExchanger` confirmed config-driven — Keycloak support is config-only (no code change needed).
[UNCHANGED] Gap 1: FR-005 trusted device TTL enforcement — `requiresMfa()` still does simple hash comparison without TTL.
[UNCHANGED] Gap 2: `TokenStorePersistenceAdapter.revokeAllForUser()` still returns 0 — stale TODO.
[UNCHANGED] Gap 3: Missing tests for trusted device TTL flow + adapter fix.

---

## Why

Auth-service has reached ~95% implementation maturity across all 17 FRs. Code re-scan on 2026-08-25 confirms:
- `KafkaEventPublisher.kt` fully implemented (Issue #2 from pre_openspec v3 — RESOLVED)
- `OAuth2TokenExchanger` is config-driven via `securityProperties.sso.providers[provider]` (Issue #1 — RESOLVED as config-only)
- `IdempotencyFilter.kt` (shared/filter) provides generic idempotency for mutating endpoints
- V10 migration applied — `trusted_device_set_at` column + `trustedDeviceSetAt` field exist in entity and domain model

**However, 3 items remain that affect security and code quality:**

1. **🟡 Gap 1 — FR-005 Trusted Device TTL + Mapper Gap** (Security — CRITICAL):
   - `User.requiresMfa()` compares `trustedDeviceHash` without checking when the device was trusted. A stolen device hash grants **indefinite** MFA bypass.
   - `SecurityProperties.mfa.trustedDeviceTtlDays = 30` exists but is UNUSED.
   - **NEW finding (v5)**: `UserEntityMapper.toDomain()` and `toEntity()` do NOT map `trustedDeviceSetAt`. The field exists in both `UserEntity` and `User` domain model, but the mapper bridge is BROKEN — `trustedDeviceSetAt` is always `null` in domain model. Without fixing the mapper, any TTL logic would silently fail.

2. **🟢 Gap 2 — `TokenStorePersistenceAdapter.revokeAllForUser()` stale TODO**: Returns `0` with TODO comment. The repository method `refreshTokenRepository.revokeAllByUserId()` already exists. The adapter should delegate to it for hexagonal port consistency.

3. **🟡 Gap 3 — Missing trusted device TTL tests**: No unit test for TTL expiry in `User.requiresMfa()`. No integration test for "trusted device expires after 30 days" scenario. `MfaLoginFlowIntegrationTest` tests hash save but not skip-MFA-on-next-login-with-TTL.

## Scope

- **Gap 1: Trusted Device TTL enforcement** — Fix mapper bridge (CRITICAL), add TTL check to `User.requiresMfa()` with `ttlDays` parameter, set `trustedDeviceSetAt = Instant.now()` on MFA verify, clear device trust on password change. Leverage existing V10 migration and entity fields. ~7 files modified.
- **Gap 2: TokenStorePersistenceAdapter fix** — Replace `return 0` with `refreshTokenRepository.revokeAllByUserId(userId)`. ~3 lines changed, 1 file.
- **Gap 3: Trusted device TTL tests** — Extend `UserTest` with TTL expiry scenarios. Extend `MfaLoginFlowIntegrationTest` with TTL-aware trusted device test. Add adapter fix test.

**Total**: 8 files modified, 0 new files, ~4h effort.

## Capabilities

### New Capabilities
- `trusted-device-ttl-enforcement`: `User.requiresMfa()` checks `trustedDeviceSetAt + ttlDays` — devices expire after configured period (default 30 days). Activates existing `SecurityProperties.mfa.trustedDeviceTtlDays` config.
- `trusted-device-mapper-fix`: `UserEntityMapper` maps `trustedDeviceSetAt` in both directions — fixes BROKEN mapper bridge that caused field to always be `null` in domain model.
- `trusted-device-clear-on-password-change`: Password change clears `trustedDeviceHash` and `trustedDeviceSetAt` — security best practice (password change invalidates device trust).
- `token-store-adapter-fix`: `TokenStorePersistenceAdapter.revokeAllForUser()` properly delegates to repository — hexagonal port/adapter consistency restored.

### Unchanged Capabilities (REUSE — Fully Implemented)
- `mfa-otp-sms-email` — OtpService + MfaService (FR-001) ✅
- `mfa-totp-authenticator` — TotpService + AES-256-GCM (FR-002) ✅
- `mfa-settings-management` — MfaService.updateSettings() (FR-003) ✅
- `captcha-integration` — CaptchaVerifier + AltchaCaptchaVerifier (FR-004) ✅
- `trusted-device-persistence` — MfaService saves trustedDeviceHash (FR-005) ✅ (TTL is the gap)
- `sso-oauth2-login` — SsoAdapter + OAuth2TokenExchanger config-driven (FR-006) ✅
- `sso-jit-provisioning` — SsoAdapter.handleCallback() + KafkaEventPublisher (FR-007) ✅
- `sso-identity-linking` — SsoAdapter.linkIdentity/unlinkIdentity (FR-008) ✅
- `jwt-rs256-signing` — JwtService dual-key RS256+HMAC (FR-009) ✅
- `jwks-endpoint` — TokenController.jwks() (FR-010) ✅
- `token-introspection` — TokenController.introspect() RFC 7662 (FR-011) ✅
- `force-logout` — AdminSessionController + RevokeSessionsHandler + AuthService.revokeAllSessions() (FR-012) ✅
- `password-policy-passay` — PasswordPolicyService + Passay + cache (FR-013) ✅
- `password-history` — PasswordPolicyService.checkPasswordHistory() (FR-014) ✅
- `mfa-verify-idempotency` — OtpService Redis DEL + IdempotencyFilter (FR-015) ✅
- `audit-logging` — AuditLogService + AuditAction (25 actions) (FR-016) ✅
- `idp-timeout-handling` — SsoProviderTimeoutException (FR-017) ✅

## Impact

### Backend (auth-service)

**MODIFY** (8 existing files):
- `User.kt` — add TTL check in `requiresMfa()` with `ttlDays` parameter (~8 lines)
- `UserEntityMapper.kt` — map `trustedDeviceSetAt` in both `toDomain()` and `toEntity()` (~2 lines)
- `UserPersistenceAdapter.kt` — map `trustedDeviceSetAt` in domain model construction (~1 line)
- `MfaService.kt` — set `trustedDeviceSetAt = Instant.now()` on device trust save (~1 line)
- `LoginHandler.kt` — pass `securityProperties.mfa.trustedDeviceTtlDays` to `requiresMfa()` (~2 lines)
- `PasswordPolicyService.kt` — clear `trustedDeviceHash` + `trustedDeviceSetAt` on password change (~4 lines)
- `TokenStorePersistenceAdapter.kt` — fix `revokeAllForUser()` to delegate to repository (~2 lines)
- Test files — extend UserTest + MfaLoginFlowIntegrationTest (~60 lines total)

**NO NEW FILES** — V10 migration and entity column already exist in codebase.

### Database
- V10 migration already applied — `trusted_device_set_at` column exists on `users` table ✅

### External Systems
- **No changes** — same Redis, OAuth2, PostgreSQL, Kafka integrations

### Cross-Feature Coordination
- `LoginHandler.kt` — MODIFY (pass ttlDays param). No conflict with other features.
- `PasswordPolicyService.kt` — MODIFY (clear device trust). No overlap with archived features.
- Prior features (`anonymous-login-optimization`, `erp-iam-system`, `api-response-i18n-standard`) — ✅ ALL ARCHIVED, no conflicts.
