# SRS: Auth Core Features — Completion & Hardening

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach B — Gap Completion + Hardening + Testing
> **_Generated**: 2026-08-19 (v2 — merged from v1 2026-08-05)
> **Status**: ~90% implemented — FRs below reflect CURRENT codebase state

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
- **Implementation**: `OtpService.generateOtp()`, `OtpService.verifyOtp()`, `MfaRateLimitService` for per-user rate limiting

#### FR-002: TOTP Authenticator App [REUSE]
- **Status**: ✅ IMPLEMENTED — `TotpService.kt`
- **Input**: Setup: `POST /api/auth/mfa/totp/setup` (JWT auth) → Confirm: `POST /api/auth/mfa/totp/confirm` — `{ code }`
- **Process**: Generate TOTP secret → AES-256-GCM encrypt → store Redis pending (TTL=10min) → on confirm → store DB (`UserEntity.totpSecretEncrypted`)
- **Output**: Setup: `{ secret, qrCodeUri, issuer }` | Confirm: `{ success }`
- **Error codes**: `TOTP_NOT_SETUP`, `MFA_CODE_INVALID`
- **Business Rules**: Window=30s, drift ±1 step, secret encrypted at rest via AES-256-GCM (`TOTP_ENCRYPTION_KEY` env var)
- **Library**: `dev.samstevens.totp:totp:1.7.1` — `DefaultSecretGenerator(32)`, `DefaultCodeVerifier`

#### FR-003: MFA Settings Management [REUSE]
- **Status**: ✅ IMPLEMENTED — `MfaService.updateSettings()`, `MfaController.kt`
- **Input**: `PUT /api/auth/mfa/settings` — `{ enabled, method }` (JWT auth)
- **Process**: Toggle MFA on/off, change method (SMS/EMAIL/TOTP)
- **Output**: `{ mfaEnabled, mfaMethod }`
- **Error codes**: `TOTP_NOT_SETUP` (if switching to TOTP without setup)
- **Business Rules**: Enabling TOTP requires setup+confirm first. Method change requires re-verification.

#### FR-004: CAPTCHA Integration [REUSE]
- **Status**: ✅ IMPLEMENTED — `CaptchaVerifier.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaController.kt`
- **Input**: `LoginCommand` extended with `captchaToken?: String`
- **Process**: If `failedLoginCount >= threshold` (from `SecurityProperties`) AND `captchaToken == null` → throw `CaptchaRequiredException`. If token present → verify server-side via `CaptchaVerifier`.
- **Output**: Normal login flow continues
- **Error codes**: `CAPTCHA_REQUIRED`, `CAPTCHA_FAILED`
- **Interface**: `CaptchaVerifier { fun verify(token: String): Boolean }` — pluggable adapter pattern
- **Adapters**: `AltchaCaptchaVerifier` (primary), `NoopCaptchaVerifier` (dev/test)
- **Config**: `app.security.captcha.provider`, `app.security.captcha.secret-key`

#### FR-005: Trusted Device (Skip MFA) [MODIFY]
- **Status**: ⚠️ PARTIAL — hash comparison works, save-on-verify MISSING
- **Current State**:
  - ✅ `User.requiresMfa(deviceHash)` — domain model comparison logic
  - ✅ `LoginCommand.trustedDeviceHash` — client sends hash on login
  - ✅ `LoginHandler` passes `command.trustedDeviceHash` to `user.requiresMfa()`
  - ❌ No endpoint/logic to SET `trustedDeviceHash` after MFA verify success
  - ❌ No TTL management (30-day expiry)
- **Completion Plan** (from brainstorm D12):
  - Add `trustDevice: Boolean` + `deviceHash: String?` to `MfaVerifyRequest` DTO
  - In `MfaService.verifyMfa()`: after successful verify, if `trustDevice=true` → update `user.trustedDeviceHash`
  - TTL deferred (from brainstorm D14): hash comparison works without TTL — never expires until hash changes
- **Business Rules**: SHA-256 device fingerprint provided by client (userAgent + timezone + language + screenRes). Hash = SHA-256(inputs), generated client-side.
- **Error codes**: None (MFA skip is transparent)

### 1.2 SSO — OAuth2 Integration

#### FR-006: OAuth2 SSO Login [MODIFY]
- **Status**: ⚠️ PARTIAL — Google + Microsoft ✅, Keycloak ❌
- **Current State**:
  - ✅ `SsoAdapter.handleCallback()` → full OAuth2 code exchange + JIT provisioning
  - ✅ `OAuth2TokenExchanger.exchange()` → code → tokens → userinfo
  - ❌ `OAuth2TokenExchanger.getTokenEndpoint("keycloak")` → throws "Unsupported SSO provider"
- **Completion Plan** (from brainstorm D11):
  - Make SSO endpoints config-driven via `SecurityProperties.sso.providers` map
  - Each provider: `{ tokenEndpoint, userInfoEndpoint, clientId?, clientSecret?, enabled }`
  - Replace hardcoded Google/Microsoft URLs with config lookup
  - Keycloak endpoints: configurable per-instance via env vars
- **Input**: `POST /api/auth/sso/callback` — `{ code, provider, redirectUri }`
- **Output**: `AuthResponse` (internal JWT)
- **Error codes**: `SSO_TOKEN_INVALID`, `SSO_PROVIDER_TIMEOUT`
- **Config**: `app.security.sso.providers.{name}.token-endpoint`, `app.security.sso.providers.{name}.user-info-endpoint`

#### FR-007: JIT User Provisioning from SSO [MODIFY]
- **Status**: ⚠️ PARTIAL — JIT provisioning ✅, event publish ❌
- **Current State**:
  - ✅ `SsoAdapter.handleCallback()` → creates UserEntity + UserIdentityEntity when new SSO user
  - ✅ `SecurityProperties.sso.defaultDomainCode` → resolved (brainstorm Q10)
  - ❌ Event publish for `iam.user.sso_provisioned` → TODO in code
- **Completion Plan** (from brainstorm D13):
  - `EventPublisher` port already EXISTS at `auth/application/port/out/EventPublisher.kt`
  - `SpringEventPublisher` adapter EXISTS at `auth/adapter/out/event/SpringEventPublisher.kt`
  - Define `SsoProvisionedEvent` data class implementing `DomainEvent`
  - Replace TODO in `SsoAdapter` with `eventPublisher.publish(SsoProvisionedEvent(...))`
  - When Kafka ready: add `KafkaEventPublisher` adapter (future)
- **Business Rules**: Identity linked by `sub` claim (NOT email). SSO-only users: `passwordHash = "!SSO_ONLY!"`

#### FR-008: Identity Linking/Unlinking [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()`, `SsoAdapter.unlinkIdentity()`, `SsoController.kt`
- **Input**: Link: `POST /api/auth/sso/link` — `{ provider, code }` (JWT auth) | Unlink: `DELETE /api/auth/sso/unlink/{provider}` (JWT auth)
- **Output**: `{ linked: true/false }`
- **Error codes**: `SSO_IDENTITY_CONFLICT` (email conflict), `CANNOT_UNLINK_LAST_IDENTITY`
- **Business Rules**: Cannot unlink last SSO identity if user has no password.

### 1.3 Token Management — RS256

#### FR-009: JWT RS256 Signing [REUSE]
- **Status**: ✅ IMPLEMENTED — `JwtService.kt` with dual-key support
- **Process**: RSA 2048-bit key pair from PEM files. RS256 primary, HMAC-SHA256 legacy fallback (7-day migration).
- **Config**: `app.security.jwt.algorithm=RS256`, `app.security.jwt.private-key-path`, `app.security.jwt.public-key-path`, `app.security.jwt.key-id`

#### FR-010: JWKS Endpoint [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.jwks()`
- **Input**: `GET /.well-known/jwks.json`
- **Output**: `{ keys: [{ kty: "RSA", kid, n, e, alg: "RS256", use: "sig" }] }`
- **Business Rules**: Cache-Control: `max-age=86400, public`. Manual key rotation with `kid` support (brainstorm Q9 resolved).

#### FR-011: Token Introspection (RFC 7662) [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.introspect()`
- **Input**: `POST /api/auth/introspect` — `{ token }` (service-to-service)
- **Output**: `{ active, sub, username, roles, permissions, exp, iat, iss, jti }`
- **Business Rules**: Blacklisted jti → `active: false`. Locked/deleted user → `active: false`.

#### FR-012: Force Logout (Session Revocation) [REUSE]
- **Status**: ✅ IMPLEMENTED — `TokenController.revokeAllSessions()` → `RevokeSessionsHandler`
- **Input**: `POST /api/auth/sessions/{userId}/revoke-all` (ADMIN auth)
- **Output**: `{ revokedCount }`
- **Business Rules**: Blacklist all active jti, delete all refresh tokens. Requires `ROLE_ADMIN`.

### 1.4 Password Policy

#### FR-013: Dynamic Password Policy per Domain [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` with Passay integration
- **Input**: Admin: `PUT /api/admin/domains/{id}/password-policy` — policy config body
- **Enforcement**: Applied on `POST /api/auth/change-password` and registration
- **Library**: `org.passay:passay:1.6.4` — dynamic rules via `PasswordValidator`
- **Cache**: `ConcurrentHashMap<Long, PasswordValidator>` per `domainId`, invalidated on policy update
- **Config fields**: minLength, maxLength, requireUppercase, requireLowercase, requireDigit, requireSpecial, minCharacterTypes, historyCount, maxAgeDays

#### FR-014: Password History [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()`, `PasswordPolicyService.pruneHistory()`
- **Input**: `POST /api/auth/change-password` — `{ oldPassword, newPassword }`
- **Process**: `BCrypt.matches(newPassword, each of last N hashes)` → if match → reject
- **Business Rules**: historyCount from domain policy (default=5). Prune entries > historyCount.
- **Error codes**: `PASSWORD_RECENTLY_USED` (400)

### 1.5 Enriched Requirements

#### FR-015: MFA Verify Idempotency [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` Redis DEL after success
- **Process**: Redis DEL after successful verify → same mfaToken+code → no OTP found → fail
- **Business Rules**: Cannot replay successful MFA verification. `MfaRateLimitService.resetCounters()` called on success.

#### FR-016: Audit Logging for MFA/SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum
- **Events**: `MFA_SETUP`, `MFA_VERIFY_SUCCESS`, `MFA_VERIFY_FAILED`, `SSO_LOGIN`, `SSO_LINK`, `SSO_UNLINK`, `PASSWORD_CHANGED`, `FORCE_LOGOUT`
- **Fields**: userId, action, entityType, entityId, ipAddress, userAgent

#### FR-017: IdP Timeout Handling [REUSE]
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout
- **Error codes**: `SSO_PROVIDER_TIMEOUT` (504 via `SsoProviderTimeoutException`)

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
| `PASSWORD_RECENTLY_USED` | 400 | Password matches recent history | FR-014 | ✅ Exists |
| `PASSWORD_EXPIRED` | 403 | Password must be changed (maxAgeDays exceeded) | FR-013 | ✅ Exists |
| `PASSWORD_POLICY_VIOLATION` | 400 | Password complexity check failed | FR-013 | ✅ Exists |

---

## 4. API Endpoints Summary

| # | Method | Path | Auth | FR | Status |
|---|--------|------|------|-----|--------|
| 1 | POST | `/api/auth/mfa/verify` | mfaToken | FR-001,FR-002 | ✅ Exists |
| 2 | POST | `/api/auth/mfa/totp/setup` | JWT | FR-002 | ✅ Exists |
| 3 | POST | `/api/auth/mfa/totp/confirm` | JWT | FR-002 | ✅ Exists |
| 4 | POST | `/api/auth/mfa/resend` | mfaToken | FR-001 | ✅ Exists |
| 5 | PUT | `/api/auth/mfa/settings` | JWT | FR-003 | ✅ Exists |
| 6 | POST | `/api/auth/sso/callback` | Public | FR-006,FR-007 | ✅ Exists |
| 7 | GET | `/api/auth/sso/providers` | Public | FR-006 | ✅ Exists |
| 8 | POST | `/api/auth/sso/link` | JWT | FR-008 | ✅ Exists |
| 9 | DELETE | `/api/auth/sso/unlink/{provider}` | JWT | FR-008 | ✅ Exists |
| 10 | POST | `/api/auth/introspect` | Internal | FR-011 | ✅ Exists |
| 11 | GET | `/.well-known/jwks.json` | Public | FR-010 | ✅ Exists |
| 12 | POST | `/api/auth/sessions/{userId}/revoke-all` | ADMIN | FR-012 | ✅ Exists |
| 13 | POST | `/api/auth/change-password` | JWT | FR-013,FR-014 | ✅ Exists |
| 14 | GET | `/api/captcha/challenge` | Public | FR-004 | ✅ Exists |

---

## 5. Data Model (ALREADY APPLIED — V2 Migration)

[CHANGED] V2 migration already applied. No new DDL needed for this completion phase.

### 5.1 ALTER TABLE users (V2 — APPLIED)
```sql
-- Already in V2__auth_core_features.sql
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_method VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN trusted_device_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
```

### 5.2 user_identities (V2 — APPLIED)
Table exists with: id, user_id, provider, provider_sub, provider_email, provider_name, linked_at, active. UNIQUE(provider, provider_sub).

### 5.3 password_policies (V2 — APPLIED)
Table exists with: id, domain_id (unique), minLength..maxAgeDays, lockout config, timestamps.

### 5.4 password_history (V2 — APPLIED)
Table exists with: id, user_id, password_hash, created_at. Index on user_id.

> ⚠️ OPEN QUESTION: Q11 — `trusted_device_set_at` column for TTL management → deferred to V-next migration (brainstorm D14).

---

## 6. Resolved Open Questions (from brainstorm)

| # | Question | Resolution | Evidence |
|---|----------|------------|----------|
| Q8 | TOTP encryption: Jasypt vs custom | ✅ Custom AES-256-GCM in `TotpService` | `TotpService.encryptSecret()`, `TOTP_ENCRYPTION_KEY` env var |
| Q9 | JWKS key rotation: auto vs manual | ✅ Manual with `kid` support | `SecurityProperties.jwt.keyId = "auth-service-key-1"` |
| Q10 | SSO default domain mapping | ✅ `SecurityProperties.sso.defaultDomainCode` | `SsoAdapter.handleCallback()` L137 |

## 7. Remaining Open Questions

> ⚠️ OPEN QUESTION: Q11 — Trusted device TTL: should `trusted_device_set_at` column be added in V-next migration?
> Recommendation: Defer. Hash comparison works without TTL — device trust never expires until hash changes. TTL is a follow-up enhancement.

> ⚠️ OPEN QUESTION: Q12 — EventPublisher: custom port vs Spring ApplicationEventPublisher?
> Resolution: Custom port ALREADY implemented (`EventPublisher` interface + `SpringEventPublisher` adapter). Kafka can be added as alternative adapter later.
