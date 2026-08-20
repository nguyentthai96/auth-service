# SRS: Auth Core Features — Final Completion & Production Readiness

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach B — Gap Completion + Hardening + Testing (from brainstorm)
> **_Generated**: 2026-08-20 (v3 — merged from v2 2026-08-19)
> **Status**: ~95% implemented — this SRS covers remaining gaps + full FR reference

---

## Changes

[CHANGED] D11-D14 from previous brainstorm now IMPLEMENTED. SRS updated to reflect current codebase state. New gaps A-D identified in brainstorm 2026-08-20.
[CHANGED] FR-005 (Trusted Device): ✅ IMPLEMENTED (was PARTIAL in v2)
[CHANGED] FR-006 (SSO Login): ✅ IMPLEMENTED — Keycloak config-driven now works
[CHANGED] FR-007 (JIT Provision): ✅ IMPLEMENTED — EventPublisher.publish(SsoProvisionedEvent) in code
[NEW] Gap A: FR-012 reclassified — revokeAllSessions() is hollow (returns 0)
[NEW] Gap B: FR-006 supplementary — getProviders() hardcoded vs config-driven inconsistency
[NEW] Gap D: FR-013 supplementary — domainId resolution in password change controllers

---

## 1. Functional Requirements

### 1.1 MFA — Multi-Factor Authentication

#### FR-001: OTP SMS/Email Verification [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.kt`, `MfaService.kt`
- **Input**: `POST /api/auth/mfa/verify` — `{ mfaToken, code }`
- **Process**: Redis GET `otp:{userId}:{channel}` → constant-time compare → check attempts
- **Output**: `AuthResponse` (success) hoặc `MFA_CODE_INVALID` (401)
- **Error codes**: `MFA_CODE_INVALID`, `MFA_TOKEN_EXPIRED`, `MFA_MAX_ATTEMPTS`
- **Business Rules**: TTL=300s (configurable via `SecurityProperties.mfa.otpTtlSeconds`), max 3 attempts, Redis DEL on success (idempotency), `SecureRandom` 6-digit code

#### FR-002: TOTP Authenticator App [REUSE]
- **Status**: ✅ IMPLEMENTED — `TotpService.kt`
- **Input**: Setup: `POST /api/auth/mfa/totp/setup` (JWT auth) → Confirm: `POST /api/auth/mfa/totp/confirm` — `{ code }`
- **Process**: Generate TOTP secret → AES-256-GCM encrypt → store Redis pending (TTL=10min) → on confirm → store DB (`UserEntity.totpSecretEncrypted`)
- **Output**: Setup: `{ secret, qrCodeUri, issuer }` | Confirm: `{ success }`
- **Error codes**: `TOTP_NOT_SETUP`, `MFA_CODE_INVALID`
- **Business Rules**: Window=30s, drift ±1 step, secret encrypted at rest via AES-256-GCM (`TOTP_ENCRYPTION_KEY` env var)
- **Library**: `dev.samstevens.totp:totp:1.7.1`

#### FR-003: MFA Settings Management [REUSE]
- **Status**: ✅ IMPLEMENTED — `MfaService.updateSettings()`, `MfaController.kt`
- **Input**: `PUT /api/auth/mfa/settings` — `{ enabled, method }` (JWT auth)
- **Process**: Toggle MFA on/off, change method (SMS/EMAIL/TOTP)
- **Output**: `{ mfaEnabled, mfaMethod }`
- **Error codes**: `TOTP_NOT_SETUP` (if switching to TOTP without setup)

#### FR-004: CAPTCHA Integration [REUSE]
- **Status**: ✅ IMPLEMENTED — `CaptchaVerifier.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaController.kt`
- **Input**: `LoginCommand` extended with `captchaToken?: String`
- **Process**: If `failedLoginCount >= threshold` AND `captchaToken == null` → throw `CaptchaRequiredException`. If token present → verify server-side via `CaptchaVerifier`.
- **Output**: Normal login flow continues
- **Error codes**: `CAPTCHA_REQUIRED`, `CAPTCHA_FAILED`
- **Interface**: `CaptchaVerifier { fun verify(token: String): Boolean }` — pluggable adapter pattern

#### FR-005: Trusted Device (Skip MFA) [REUSE]
- **Status**: ✅ IMPLEMENTED — `MfaService.verifyMfa()` saves `trustedDeviceHash` on success
- [CHANGED] Was PARTIAL in v2 — now fully implemented (brainstorm D12)
- **Process**: Client sends SHA-256 device fingerprint. `LoginHandler` passes to `User.requiresMfa(deviceHash)`. On MFA verify with `trustDevice=true`, `MfaService` saves hash to `UserEntity.trustedDeviceHash`.
- **Business Rules**: SHA-256 device fingerprint provided by client. Hash comparison in domain model. No TTL (deferred — brainstorm D14).
- **Error codes**: None (MFA skip is transparent)

### 1.2 SSO — OAuth2 Integration

#### FR-006: OAuth2 SSO Login [REUSE + MODIFY]
- **Status**: ✅ IMPLEMENTED — Google, Microsoft, Keycloak all config-driven
- [CHANGED] Was PARTIAL in v2 — Keycloak now works via `SecurityProperties.sso.providers` map (brainstorm D11)
- **Input**: `POST /api/auth/sso/callback` — `{ code, provider, redirectUri }`
- **Output**: `AuthResponse` (internal JWT)
- **Error codes**: `SSO_TOKEN_INVALID`, `SSO_PROVIDER_TIMEOUT`
- **Config**: `app.security.sso.providers.{name}.token-endpoint`, `app.security.sso.providers.{name}.user-info-endpoint`
- ⚠️ **Gap B**: `SsoAdapter.getProviders()` still returns hardcoded list — INCONSISTENT with config-driven `OAuth2TokenExchanger`. Needs fix to read from `securityProperties.sso.providers`.

#### FR-007: JIT User Provisioning from SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` JIT + `eventPublisher.publish(SsoProvisionedEvent(...))`
- [CHANGED] Was PARTIAL in v2 — EventPublisher now used (brainstorm D13)
- **Business Rules**: Identity linked by `sub` claim (NOT email). SSO-only users: `passwordHash = "!SSO_ONLY!"`

#### FR-008: Identity Linking/Unlinking [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()`, `SsoAdapter.unlinkIdentity()`, `SsoController.kt`
- **Input**: Link: `POST /api/auth/sso/link` — `{ provider, code }` | Unlink: `DELETE /api/auth/sso/unlink/{provider}`
- **Output**: `{ linked: true/false }`
- **Error codes**: `SSO_IDENTITY_CONFLICT`, `CANNOT_UNLINK_LAST_IDENTITY`

### 1.3 Token Management — RS256

#### FR-009: JWT RS256 Signing [REUSE]
- **Status**: ✅ IMPLEMENTED — `JwtService.kt` with dual-key support
- **Process**: RSA 2048-bit key pair from PEM files. RS256 primary, HMAC-SHA256 legacy fallback.

#### FR-010: JWKS Endpoint [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.jwks()`
- **Input**: `GET /.well-known/jwks.json`
- **Output**: `{ keys: [{ kty: "RSA", kid, n, e, alg: "RS256", use: "sig" }] }`
- **Business Rules**: Cache-Control: `max-age=86400, public`.

#### FR-011: Token Introspection (RFC 7662) [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.introspect()`
- **Input**: `POST /api/auth/introspect` — `{ token }` (service-to-service)
- **Output**: `{ active, sub, username, roles, permissions, exp, iat, iss, jti }`

#### FR-012: Force Logout (Session Revocation) [MODIFY]
- **Status**: ⚠️ PARTIAL — `AdminSessionController + RevokeSessionsHandler` exist but `AuthService.revokeAllSessions()` is HOLLOW
- [CHANGED] Reclassified from REUSE → MODIFY. Implementation returns `0` without revoking anything.
- **Input**: `POST /api/auth/sessions/{userId}/revoke-all` (ADMIN auth)
- **Output**: `{ revokedCount }` — currently always returns 0
- **Completion Plan** (Gap A from brainstorm):
  - Revoke all refresh tokens via `RefreshTokenRepository.revokeAllByUserId(userId)` — `@Modifying @Query("UPDATE RefreshTokenEntity SET revoked = true WHERE userId = :userId AND revoked = false")`
  - End all login sessions via `LoginSessionService.endAllSessions(userId)` or `LoginSessionRepository.deleteAllByUserId(userId)`
  - Audit log via `AuditLogService.logEvent(userId, AuditAction.SESSION_REVOKED, ...)`
  - Access tokens expire naturally (≤15min) — industry-standard for stateless JWTs
  - Return count of revoked refresh tokens
- **Error codes**: `ResourceNotFoundException` (user not found)

### 1.4 Password Policy

#### FR-013: Dynamic Password Policy per Domain [REUSE + MODIFY]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` with Passay integration
- ⚠️ **Gap D**: Controllers have TODO for `domainId` resolution. `PasswordPolicyService.validatePasswordStrength(password, domainId)` requires `domainId`, but `AuthController.kt:67` and `CqrsAuthController.kt:197` don't resolve it from authenticated user.
- **Completion Plan** (Gap D from brainstorm):
  - Get `domainId` from authenticated user's active domain via `DomainLookupService` or JWT claims
  - Pass to `PasswordPolicyService` for domain-specific policy lookup

#### FR-014: Password History [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()`, `PasswordPolicyService.pruneHistory()`
- **Error codes**: `PASSWORD_RECENTLY_USED` (400)

### 1.5 Enriched Requirements

#### FR-015: MFA Verify Idempotency [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` Redis DEL after success

#### FR-016: Audit Logging for MFA/SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum (25 actions)

#### FR-017: IdP Timeout Handling [REUSE]
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout → `SsoProviderTimeoutException`

---

## 2. Non-Functional Requirements

| ID | Requirement | Target | Status |
|-----|------------|--------|--------|
| NFR-001 | MFA verification latency | ≤ 200ms (Redis-backed, constant-time compare) | ✅ Met |
| NFR-002 | RS256 signing/verification | < 50ms per token (RSA 2048-bit) | ✅ Met |
| NFR-003 | JWKS endpoint cache | TTL = 24h (Cache-Control: max-age=86400, public) | ✅ Met |
| NFR-004 | CAPTCHA verification timeout | ≤ 5s (pluggable adapter) | ✅ Met |
| NFR-005 | Password validator cache hit ratio | > 95% (ConcurrentHashMap per domainId) | ✅ Met |
| NFR-006 | Login endpoint P95 latency | < 500ms (including MFA + CAPTCHA + rate limit) | 🔶 Needs testing |

---

## 3. Error Code Registry

| Code | HTTP | Description | FR | Status |
|------|------|-------------|-----|--------|
| `MFA_REQUIRED` | 200 | Login success but MFA pending | FR-001 | ✅ Exists |
| `MFA_CODE_INVALID` | 401 | OTP/TOTP code is wrong | FR-001,FR-002 | ✅ Exists |
| `MFA_TOKEN_EXPIRED` | 401 | MFA session token expired | FR-001 | ✅ Exists |
| `MFA_MAX_ATTEMPTS` | 429 | Exceeded max OTP attempts | FR-001 | ✅ Exists |
| `TOTP_NOT_SETUP` | 400 | TOTP not configured for user | FR-002 | ✅ Exists |
| `CAPTCHA_REQUIRED` | 403 | CAPTCHA needed due to failed logins | FR-004 | ✅ Exists |
| `CAPTCHA_FAILED` | 403 | CAPTCHA verification failed | FR-004 | ✅ Exists |
| `SSO_TOKEN_INVALID` | 401 | IdP token exchange failed | FR-006 | ✅ Exists |
| `SSO_USER_NOT_PROVISIONED` | 403 | SSO user not in DB + autoProvision off | FR-007 | ✅ Exists |
| `SSO_IDENTITY_CONFLICT` | 409 | IdP identity already linked to another user | FR-008 | ✅ Exists |
| `CANNOT_UNLINK_LAST_IDENTITY` | 400 | Cannot remove last SSO identity without password | FR-008 | ✅ Exists |
| `SSO_PROVIDER_TIMEOUT` | 504 | IdP token endpoint timeout | FR-017 | ✅ Exists |
 DEFERRED to Phase 2 | Brainstorm D14 |
| Q12 | EventPublisher approach | ✅ Custom port implemented | `EventPublisher.kt` + `SpringEventPublisher.kt` |
| Q13 | revokeAllSessions — blacklist JTIs? | ✅ Revoke refresh tokens only | Access tokens expire naturally (≤15min) |
| Q14 | getProviders() displayName | ✅ Derive from ID | `id.replaceFirstChar { it.uppercase() }` |

## 7. Remaining Open Questions

> ⚠️ OPEN QUESTION: Q11 — Trusted device TTL: `trusted_device_set_at` column deferred to V-next migration.
