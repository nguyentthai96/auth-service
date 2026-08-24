# SRS: Auth Core Features — Trusted Device Hardening & Final Cleanup (v5)

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Approach B — TTL Enforcement + Mapper Gap Fix + Adapter Cleanup + Test Coverage (from brainstorm v5)
> **_Generated**: 2026-08-25 (v5 — delta from v4 2026-08-21)
> **Status**: ~95% implemented — this SRS covers remaining gaps (FR-005 TTL + mapper + adapter) + full FR reference
> **Archive**: `openspec/changes/archive/2026-08-20-auth-core-features/srs.md`

---

## Changes

[CHANGED] v4→v5: Code re-scan 2026-08-25 confirms V10 migration APPLIED, entity field EXISTS. Mapper gap elevated to CRITICAL.
[CHANGED] Gap 1 expanded: FR-005 now includes mapper fix as CRITICAL prerequisite for TTL logic to work.
[CHANGED] Keycloak (Issue #1 in pre_openspec): RESOLVED — OAuth2TokenExchanger is config-driven.
[UNCHANGED] All FR status unchanged from v4 (15 implemented + 1 partial FR-005 + 1 adapter fix FR-012).
[UNCHANGED] No new error codes, no new DTOs, no new migrations needed.

---

## 1. Functional Requirements

### 1.1 MFA — Multi-Factor Authentication

#### FR-001: OTP SMS/Email Verification [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.kt`, `MfaService.kt`
- **Input**: `POST /api/auth/mfa/verify` — `{ mfaToken, code }`
- **Process**: Redis GET `otp:{userId}:{channel}` → constant-time compare → check attempts
- **Output**: `AuthResponse` (success) or `MFA_CODE_INVALID` (401)
- **Error codes**: `MFA_CODE_INVALID` (AUTH_011), `MFA_TOKEN_EXPIRED` (AUTH_012), `MFA_MAX_ATTEMPTS` (AUTH_013)
- **Business Rules**: TTL=300s (configurable via `SecurityProperties.mfa.otpTtlSeconds`), max 3 attempts, Redis DEL on success (idempotency), `SecureRandom` 6-digit code

#### FR-002: TOTP Authenticator App [REUSE]
- **Status**: ✅ IMPLEMENTED — `TotpService.kt`
- **Input**: Setup: `POST /api/auth/mfa/totp/setup` → Confirm: `POST /api/auth/mfa/totp/confirm` — `{ code }`
- **Process**: Generate TOTP secret → AES-256-GCM encrypt → store Redis pending (TTL=10min) → on confirm → store DB (`UserEntity.totpSecretEncrypted`)
- **Output**: Setup: `{ secret, qrCodeUri, issuer }` | Confirm: `{ success }`
- **Error codes**: `TOTP_NOT_SETUP` (AUTH_011 variant), `MFA_CODE_INVALID` (AUTH_011)
- **Business Rules**: Window=30s, drift ±1 step, secret encrypted at rest via AES-256-GCM
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
- **Process**: If `failedLoginCount >= threshold` AND `captchaToken == null` → throw `CaptchaRequiredException`. If token present → verify via `CaptchaVerifier`.
- **Output**: Normal login flow continues
- **Error codes**: `CAPTCHA_REQUIRED` (AUTH_007), `CAPTCHA_FAILED` (AUTH_008)
- **Interface**: `CaptchaVerifier { fun verify(token: String): Boolean }` — pluggable adapter

#### FR-005: Trusted Device (Skip MFA) [MODIFY — TTL Hardening + Mapper Fix]
- **Status**: ⚠️ PARTIAL — Hash persistence works, mapper bridge BROKEN, TTL enforcement missing
- **Input**: `LoginCommand.trustedDeviceHash` (SHA-256 hex, 64 chars — client-generated)
- **Current issues (code-verified 2026-08-25)**:
  1. **CRITICAL: Mapper gap** — `UserEntityMapper.toDomain()` does NOT map `trustedDeviceSetAt` (L28). `UserEntityMapper.toEntity()` does NOT map `trustedDeviceSetAt` (L49). Domain model always sees `null` for this field.
  2. `User.requiresMfa(deviceHash)` — simple hash comparison, no TTL parameter
  3. `MfaService.verifyMfa()` — saves `trustedDeviceHash` but NOT `trustedDeviceSetAt`
  4. `SecurityProperties.mfa.trustedDeviceTtlDays = 30` — exists but UNUSED
  5. `PasswordPolicyService.changePassword()` — does NOT clear trusted device
- **Process (Target)**:
  1. Fix mapper: `UserEntityMapper` maps `trustedDeviceSetAt` in both directions
  2. `User.requiresMfa(deviceHash, ttlDays)` — compare hash AND check `trustedDeviceSetAt + ttlDays > now()`
  3. `MfaService.verifyMfa()` — on trusted device save, set `trustedDeviceSetAt = Instant.now()`
  4. `PasswordPolicyService.changePassword()` — clear `trustedDeviceHash` and `trustedDeviceSetAt`
- **Business Rules**:
  - SHA-256 device fingerprint generated CLIENT-SIDE (server stores + compares hash only)
  - TTL = `SecurityProperties.mfa.trustedDeviceTtlDays` (default 30 days) — will be activated
  - Expired device trust → MFA required on next login
  - Password change → device trust cleared (industry best practice: Google, Microsoft pattern)
  - Null `trustedDeviceSetAt` → treat as expired (safe default — forces MFA re-verification)
  - Single device per user (current design — multi-device is future scope)
- **Error codes**: None (MFA skip is transparent)
- **Database**: `trusted_device_set_at TIMESTAMPTZ` column already exists (V10 migration applied)

### 1.2 SSO — OAuth2 Integration

#### FR-006: OAuth2 SSO Login [REUSE]
- **Status**: ✅ IMPLEMENTED — Google, Microsoft, Keycloak all config-driven
- **Input**: `POST /api/auth/sso/callback` — `{ code, provider, redirectUri }`
- **Output**: `AuthResponse` (internal JWT)
- **Error codes**: `SSO_TOKEN_INVALID` (AUTH_014), `SSO_PROVIDER_TIMEOUT` (AUTH_014 variant, HTTP 504)
- **Config**: `app.security.sso.providers.{name}.token-endpoint`, `.user-info-endpoint`
- [CHANGED] v5: OAuth2TokenExchanger confirmed config-driven — Keycloak = add config only, no code change

#### FR-007: JIT User Provisioning from SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` JIT + `KafkaEventPublisher`
- **Business Rules**: Identity linked by `sub` claim (NOT email). `KafkaEventPublisher` publishes with retry (3 attempts, exponential backoff).
- [CHANGED] v5: KafkaEventPublisher confirmed fully implemented with `@Primary`, `@ConditionalOnProperty`.

#### FR-008: Identity Linking/Unlinking [REUSE]
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()`, `SsoAdapter.unlinkIdentity()`, `SsoController.kt`
- **Input**: Link: `POST /api/auth/sso/link` — `{ provider, code }` | Unlink: `DELETE /api/auth/sso/unlink/{provider}`
- **Error codes**: `SSO_IDENTITY_CONFLICT` (AUTH_016), `CANNOT_UNLINK_LAST_IDENTITY` (AUTH_016 variant)

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
- **Input**: `POST /api/auth/introspect` — `{ token }`
- **Output**: `{ active, sub, username, roles, permissions, exp, iat, iss, jti }`

#### FR-012: Force Logout (Session Revocation) [REUSE]
- **Status**: ✅ IMPLEMENTED — `AuthService.revokeAllSessions()` fully functional
- **Input**: `POST /api/auth/sessions/{userId}/revoke-all` (ADMIN auth)
- **Output**: `{ revokedCount }`
- **Note**: `TokenStorePersistenceAdapter.revokeAllForUser()` still returns 0 (stale TODO) — Gap 2 fix needed for hexagonal consistency. `AuthService.revokeAllSessions()` works correctly by calling repo directly.

### 1.4 Password Policy

#### FR-013: Dynamic Password Policy per Domain [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` with Passay integration
- **Note**: Password change will additionally clear trusted device (FR-005 hardening — D19).

#### FR-014: Password History [REUSE]
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()`, `PasswordPolicyService.pruneHistory()`
- **Error codes**: `PASSWORD_RECENTLY_USED` (AUTH_017 variant, HTTP 400)

### 1.5 Enriched Requirements

#### FR-015: MFA Verify Idempotency [REUSE]
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` Redis DEL after success + `IdempotencyFilter.kt` (shared)

#### FR-016: Audit Logging for MFA/SSO [REUSE]
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum (25 actions including `TRUSTED_DEVICE_SET`)

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
| NFR-006 | Login endpoint P95 latency | < 500ms (including MFA + CAPTCHA + rate limit + TTL check) | ✅ Met |
| NFR-007 | Trusted device TTL check latency | < 1ms (in-memory field comparison, no external call) | Expected to meet |
| NFR-008 | Kafka event publish retry | 3 attempts, exponential backoff (1s → 2s → 4s), max 8s | ✅ Met |

---

## 3. Error Code Registry

| Code | HTTP | Description | FR | Status |
|------|------|-------------|-----|--------|
| `AUTH_001` (INVALID_CREDENTIALS) | 401 | Wrong username/password | Login | ✅ Exists |
| `AUTH_002` (ACCOUNT_LOCKED) | 403 | Account locked after N failed attempts | Login | ✅ Exists |
| `AUTH_003` (TOKEN_EXPIRED) | 401 | JWT expired | Token | ✅ Exists |
| `AUTH_007` (CAPTCHA_REQUIRED) | 428 | CAPTCHA needed | FR-004 | ✅ Exists |
| `AUTH_008` (CAPTCHA_FAILED) | 400 | CAPTCHA verification failed | FR-004 | ✅ Exists |
| `AUTH_011` (MFA_CODE_INVALID) | 401 | OTP/TOTP code wrong | FR-001, FR-002 | ✅ Exists |
| `AUTH_012` (MFA_TOKEN_EXPIRED) | 401 | MFA session expired | FR-001 | ✅ Exists |
| `AUTH_013` (MFA_MAX_ATTEMPTS) | 429 | Exceeded max OTP attempts | FR-001 | ✅ Exists |
| `AUTH_014` (SSO_TOKEN_INVALID) | 401 | IdP token exchange failed | FR-006 | ✅ Exists |
| `AUTH_015` (SSO_NOT_PROVISIONED) | 403 | SSO user not provisioned | FR-007 | ✅ Exists |
| `AUTH_016` (SSO_IDENTITY_CONFLICT) | 409 | SSO identity conflict | FR-008 | ✅ Exists |
| `AUTH_017` (PASSWORD_POLICY_VIOLATION) | 400 | Password doesn't meet policy | FR-013 | ✅ Exists |
| `AUTH_018` (PASSWORD_EXPIRED) | 403 | Password expired | FR-013 | ✅ Exists |
| `AUTH_019` (MFA_RATE_LIMITED) | 429 | MFA rate limit exceeded | FR-001 | ✅ Exists |
| `AUTH_020` (RATE_LIMIT_EXCEEDED) | 429 | Login rate limit exceeded | Login | ✅ Exists |
| `AUTH_021` (SESSION_LIMIT_EXCEEDED) | 409 | Max active sessions reached | Login | ✅ Exists |

**No new error codes needed for v5.** Trusted device TTL expiry is transparent — user simply gets `MfaRequiredResponse` (existing flow).

---

## 4. Gap Specifications

### 4.1 Gap 1a: UserEntityMapper — Fix BROKEN mapper bridge [MODIFY — CRITICAL]

**Current state (code-verified 2026-08-25)**:
- `UserEntityMapper.toDomain()` at L28 maps `trustedDeviceHash` but NOT `trustedDeviceSetAt`
- `UserEntityMapper.toEntity()` at L49 maps `trustedDeviceHash` but NOT `trustedDeviceSetAt`
- `UserPersistenceAdapter.toDomainModel()` also MISSING `trustedDeviceSetAt`

**Impact**: Without this fix, `User.trustedDeviceSetAt` is ALWAYS `null` in domain model — TTL logic would silently fail.

**Target state**:
```kotlin
// UserEntityMapper.kt — toDomain()
trustedDeviceSetAt = this.trustedDeviceSetAt,  // ← ADD after trustedDeviceHash line

// UserEntityMapper.kt — toEntity()
trustedDeviceSetAt = this@toEntity.trustedDeviceSetAt  // ← ADD after trustedDeviceHash line
```

### 4.1b Gap 1b: FR-005 Trusted Device TTL Enforcement [MODIFY]

**Current state**:
- `User.requiresMfa(deviceHash)` at `User.kt:73-76` — compares hash only, no TTL
- `SecurityProperties.mfa.trustedDeviceTtlDays = 30` — exists but UNUSED
- `MfaService.verifyMfa()` — saves hash but NOT `trustedDeviceSetAt`
- `PasswordPolicyService.changePassword()` — does not clear device trust

**Target state**:

```kotlin
// User.kt — domain model
fun requiresMfa(deviceHash: String?, ttlDays: Long = 30): Boolean {
    if (!mfaEnabled || mfaMethod == "NONE") return false
    if (deviceHash == null || deviceHash != trustedDeviceHash) return true
    val setAt = trustedDeviceSetAt ?: return true  // No timestamp → expired
    return setAt.plus(ttlDays, ChronoUnit.DAYS).isBefore(Instant.now())
}

// MfaService.kt — on trusted device save
user.trustedDeviceHash = deviceHash
user.trustedDeviceSetAt = Instant.now()  // ← NEW LINE

// PasswordPolicyService.kt — on password change
user.trustedDeviceHash = null  // ← NEW LINE
user.trustedDeviceSetAt = null  // ← NEW LINE

// LoginHandler.kt — pass TTL config
if (user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
```

### 4.2 Gap 2: TokenStorePersistenceAdapter Fix [MODIFY]

**Current state**: `TokenStorePersistenceAdapter.revokeAllForUser()` returns `0` with TODO comment.

**Target state**:
```kotlin
override fun revokeAllForUser(userId: Long): Int {
    return refreshTokenRepository.revokeAllByUserId(userId)
}
```

**Note**: `refreshTokenRepository.revokeAllByUserId()` already exists in `Repositories.kt`.

---

## 5. FR Traceability

| FR-ID | Status | Gap | Task Required |
|-------|--------|-----|---------------|
| FR-001 | ✅ IMPLEMENTED | — | None |
| FR-002 | ✅ IMPLEMENTED | — | None |
| FR-003 | ✅ IMPLEMENTED | — | None |
| FR-004 | ✅ IMPLEMENTED | — | None |
| FR-005 | ⚠️ PARTIAL | Gap 1a (mapper) + Gap 1b (TTL logic) | Mapper fix, TTL logic, timestamp save, trust clear, tests |
| FR-006 | ✅ IMPLEMENTED | — | None |
| FR-007 | ✅ IMPLEMENTED | — | None |
| FR-008 | ✅ IMPLEMENTED | — | None |
| FR-009 | ✅ IMPLEMENTED | — | None |
| FR-010 | ✅ IMPLEMENTED | — | None |
| FR-011 | ✅ IMPLEMENTED | — | None |
| FR-012 | ✅ IMPLEMENTED | Gap 2: Adapter stale | Adapter fix (hexagonal consistency) |
| FR-013 | ✅ IMPLEMENTED | — | Clear device trust on pwd change |
| FR-014 | ✅ IMPLEMENTED | — | None |
| FR-015 | ✅ IMPLEMENTED | — | None |
| FR-016 | ✅ IMPLEMENTED | — | None |
| FR-017 | ✅ IMPLEMENTED | — | None |

**Coverage**: 17/17 FRs addressed. 15 fully implemented (REUSE). 1 partial (FR-005 TTL + mapper). 1 adapter fix (FR-012 port consistency).

---

## 6. Design Decisions (v5)

| # | Decision | Status | Reasoning |
|---|----------|--------|-----------|
| D1-D17 | Prior decisions | ✅ ALL IMPLEMENTED | See v3 archive |
| D18 | **DB-only `trusted_device_set_at` column (NOT Redis)** | ✅ V10 APPLIED | Simplest approach. User already loaded in login flow. |
| D19 | **Clear trusted device on password change (NOT on force-logout)** | PENDING | Password change = security event → invalidates device trust. Force-logout = session management. Matches Google/Microsoft pattern. |
| D20 | **Fix `TokenStorePersistenceAdapter.revokeAllForUser()`** | PENDING | Hexagonal consistency. Repo method already exists. |
| D21 | **Fix UserEntityMapper trustedDeviceSetAt mapping** | PENDING (CRITICAL) | Mapper bridge BROKEN — field exists in both entity and domain but not mapped. |
| D22 | **Pass `ttlDays` as parameter to `requiresMfa()` (NOT inject config)** | PENDING | Domain model stays pure. LoginHandler has SecurityProperties access. |
| D23 | **MfaService set `trustedDeviceSetAt = now()` on device trust save** | PENDING | Atomic save with hash. Same userRepository.save() call. |
| D24 | **Keep domain model pure (no framework dependencies)** | DECIDED | Pass config as params (D22). |
| D25 | **Keycloak support = config-only** | ✅ RESOLVED | OAuth2TokenExchanger reads from securityProperties.sso.providers. |
| D26 | **Force-logout does NOT clear trusted device** | DECIDED | Only password change does. |
