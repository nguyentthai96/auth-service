# SRS: Auth Core Features

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial

---

## 1. Functional Requirements

### 1.1 MFA — Multi-Factor Authentication

#### FR-001: OTP SMS/Email Verification
- **Input**: `POST /api/auth/mfa/verify` — `{ mfaToken, code }`
- **Process**: Redis GET `otp:{userId}:{channel}` → compare code → check attempts
- **Output**: `AuthResponse` (success) hoặc `MFA_CODE_INVALID` (401)
- **Error codes**: `MFA_CODE_INVALID`, `MFA_TOKEN_EXPIRED`, `MFA_MAX_ATTEMPTS`
- **Business Rules**: TTL=300s, max 3 attempts, Redis DEL on success
- **Constraints**: OTP = 6 digits numeric, generated via `SecureRandom`

#### FR-002: TOTP Authenticator App
- **Input**: Setup: `POST /api/auth/mfa/totp/setup` (JWT auth) → Confirm: `POST /api/auth/mfa/totp/confirm` — `{ code }`
- **Process**: Generate TOTP secret → AES-256 encrypt → store Redis (pending) → on confirm → store DB
- **Output**: Setup: `{ secret, qrCodeUri, issuer }` | Confirm: `{ success }`
- **Error codes**: `TOTP_NOT_SETUP`, `MFA_CODE_INVALID`
- **Business Rules**: Window=30s, drift ±1 step, secret encrypted at rest
- **Library**: `dev.samstevens.totp:totp:1.7.1`

#### FR-003: MFA Settings Management
- **Input**: `PUT /api/auth/mfa/settings` — `{ enabled, method }` (JWT auth)
- **Process**: Toggle MFA on/off, change method (SMS/EMAIL/TOTP)
- **Output**: `{ mfaEnabled, mfaMethod }`
- **Error codes**: `TOTP_NOT_SETUP` (if switching to TOTP without setup)
- **Business Rules**: Disabling requires OTP confirmation. Enabling TOTP requires setup+confirm first.

#### FR-004: CAPTCHA Integration
- **Input**: `LoginRequestDto` extended with `captchaToken?: String`
- **Process**: If `failedLoginCount >= threshold` AND `captchaToken == null` → reject. If token present → verify server-side.
- **Output**: Normal login flow continues
- **Error codes**: `CAPTCHA_REQUIRED`, `CAPTCHA_FAILED`
- **Interface**: `CaptchaVerifier { fun verify(token: String): Boolean }`
- **Adapters**: `TurnstileCaptchaVerifier`, `HCaptchaVerifier`, `RecaptchaVerifier`
- **Config**: `app.security.captcha.provider`, `app.security.captcha.secret-key`

#### FR-005: Trusted Device (Skip MFA)
- **Input**: Login response sets cookie `trusted_device` (SHA-256 hash of device fingerprint)
- **Process**: On login with MFA → check `trustedDeviceHash` on UserEntity → match → skip MFA
- **Output**: Direct `AuthResponse` (no MFA challenge)
- **Business Rules**: TTL=30 days (configurable), hash = SHA-256(userAgent + userId + salt)

### 1.2 SSO — OAuth2 Integration

#### FR-006: OAuth2 SSO Login
- **Input**: `POST /api/auth/sso/callback` — `{ code, provider, redirectUri }`
- **Process**: Exchange code → tokens via Spring OAuth2 → parse ID token → find/create user → issue JWT
- **Output**: `AuthResponse` (internal JWT)
- **Error codes**: `SSO_TOKEN_INVALID`, `SSO_PROVIDER_TIMEOUT`
- **Providers**: Google, Microsoft, Keycloak (config via `spring.security.oauth2.client.registration.*`)
- **Config**: `app.security.sso.enabled`, per-domain `domains.config.sso.autoProvision`

#### FR-007: JIT User Provisioning
- **Input**: SSO callback with user not in DB + domain `autoProvision=true`
- **Process**: Create `UserEntity` (passwordHash=`!SSO_ONLY!`) + `UserIdentityEntity` → Kafka `iam.user.sso_provisioned`
- **Output**: New user created, `AuthResponse` returned
- **Error codes**: `SSO_USER_NOT_PROVISIONED` (when `autoProvision=false`)
- **Business Rules**: Identity linked by `sub` claim (NOT email). SSO users cannot login via password.

#### FR-008: Identity Linking/Unlinking
- **Input**: Link: `POST /api/auth/sso/link` — `{ provider, code }` (JWT auth) | Unlink: `DELETE /api/auth/sso/unlink/{provider}` (JWT auth)
- **Process**: Link → verify OAuth2 code → create `UserIdentityEntity`. Unlink → delete identity.
- **Output**: `{ linked: true/false }`
- **Error codes**: `SSO_IDENTITY_CONFLICT` (email conflict), `CANNOT_UNLINK_LAST_IDENTITY`
- **Business Rules**: Cannot unlink last SSO identity if user has no password.

### 1.3 Token Management — RS256

#### FR-009: JWT RS256 Migration
- **Input**: N/A (internal — JwtService refactor)
- **Process**: `SecretKey` (HMAC) → `KeyPair` (RSA 2048-bit). Load from PEM files via env.
- **Output**: JWT signed with RS256
- **Migration**: 7-day dual-algorithm (try RS256 first, fallback HMAC). After 7 days → remove HMAC.
- **Config**: `app.security.jwt.algorithm=RS256`, `app.security.jwt.private-key-path`, `app.security.jwt.public-key-path`

#### FR-010: JWKS Endpoint
- **Input**: `GET /.well-known/jwks.json`
- **Process**: Expose RSA public key in JWK format with `kid`
- **Output**: `{ keys: [{ kty: "RSA", kid, n, e, alg: "RS256", use: "sig" }] }`
- **Business Rules**: Cacheable (Cache-Control: max-age=86400). Supports multiple kid for rotation.

#### FR-011: Token Introspection (RFC 7662)
- **Input**: `POST /api/auth/introspect` — `{ token }` (internal service-to-service auth)
- **Process**: Parse JWT → check jti blacklist → check user status → return claims
- **Output**: `{ active, sub, roles, permissions, exp, iat, iss }`
- **Business Rules**: Blacklisted jti → `active: false`. Locked/deleted user → `active: false`.

#### FR-012: Force Logout (Session Revocation)
- **Input**: `POST /api/auth/sessions/{userId}/revoke-all` (ADMIN auth)
- **Process**: Blacklist ALL active jti for user → delete all refresh tokens → publish event
- **Output**: `{ revokedCount }`
- **Business Rules**: Immediate effect. Requires `ROLE_ADMIN` or `PERM_USER_MANAGE`.

### 1.4 Password Policy

#### FR-013: Dynamic Password Policy per Domain
- **Input**: Admin: `PUT /api/admin/domains/{id}/password-policy` — policy config body
- **Process**: Validate config → save `PasswordPolicyEntity` → invalidate cached validator
- **Output**: `PasswordPolicyResponse`
- **Enforcement**: Applied on `POST /api/auth/change-password` and registration
- **Library**: `org.passay:passay:1.6.4`
- **Config fields**: minLength, maxLength, requireUppercase, requireLowercase, requireDigit, requireSpecial, minCharacterTypes, historyCount, maxAgeDays

#### FR-014: Password History
- **Input**: `POST /api/auth/change-password` — `{ oldPassword, newPassword }`
- **Process**: BCrypt.matches(newPassword, each of last N hashes) → if match → reject
- **Output**: `200 OK` or `400 PASSWORD_RECENTLY_USED`
- **Business Rules**: historyCount from domain policy (default=5). Prune entries > historyCount.

### 1.5 Enriched Requirements

#### FR-015: MFA Verify Idempotency
- **Process**: Redis DEL after successful verify → same mfaToken+code → no OTP found → fail
- **Business Rules**: Cannot replay successful MFA verification

#### FR-016: Audit Logging for MFA/SSO
- **Process**: Log events to `audit_log` table: MFA_SETUP, MFA_VERIFY_SUCCESS, MFA_VERIFY_FAILED, SSO_LOGIN, SSO_LINK, SSO_UNLINK, PASSWORD_CHANGED, FORCE_LOGOUT
- **Fields**: userId, action, entityType, entityId, ipAddress, userAgent

#### FR-017: IdP Timeout Handling
- **Process**: SsoAdapter → RestTemplate/WebClient timeout = 10s → throw `SsoProviderTimeoutException`
- **Error codes**: `SSO_PROVIDER_TIMEOUT` (504)

---

## 2. Non-Functional Requirements

| ID | Requirement | Target |
|-----|------------|--------|
| NFR-001 | MFA verification latency | ≤ 200ms (Redis-backed) |
| NFR-002 | RS256 signing/verification | < 50ms per token |
| NFR-003 | JWKS endpoint cache | TTL = 24h (client-side) |
| NFR-004 | CAPTCHA verification timeout | ≤ 5s |
| NFR-005 | OTP delivery (SMS/Email) | < 10s (async, fire-and-forget) |
| NFR-006 | Password policy validation | < 100ms (cached Passay validator) |

---

## 3. Error Code Registry

| Code | HTTP | Description | FR |
|------|------|-------------|-----|
| `MFA_REQUIRED` | 200 | Login success but MFA pending | FR-001 |
| `MFA_CODE_INVALID` | 401 | OTP/TOTP code is wrong | FR-001,FR-002 |
| `MFA_TOKEN_EXPIRED` | 401 | MFA session token expired | FR-001 |
| `MFA_MAX_ATTEMPTS` | 403 | Exceeded max OTP attempts | FR-001 |
| `TOTP_NOT_SETUP` | 400 | TOTP not configured for user | FR-002 |
| `CAPTCHA_REQUIRED` | 403 | CAPTCHA needed due to failed logins | FR-004 |
| `CAPTCHA_FAILED` | 403 | CAPTCHA verification failed | FR-004 |
| `SSO_TOKEN_INVALID` | 401 | IdP token exchange failed | FR-006 |
| `SSO_USER_NOT_PROVISIONED` | 403 | SSO user not in DB + autoProvision off | FR-007 |
| `SSO_IDENTITY_CONFLICT` | 409 | IdP identity already linked to another user | FR-008 |
| `CANNOT_UNLINK_LAST_IDENTITY` | 400 | Cannot remove last SSO identity without password | FR-008 |
| `SSO_PROVIDER_TIMEOUT` | 504 | IdP token endpoint timeout | FR-017 |
| `PASSWORD_RECENTLY_USED` | 400 | Password matches recent history | FR-014 |
| `PASSWORD_EXPIRED` | 403 | Password must be changed (maxAgeDays exceeded) | FR-013 |
| `PASSWORD_POLICY_VIOLATION` | 400 | Password complexity check failed | FR-013 |

---

## 4. API Endpoints Summary

| # | Method | Path | Auth | FR | Description |
|---|--------|------|------|-----|-------------|
| 1 | POST | `/api/auth/mfa/verify` | mfaToken | FR-001,FR-002 | Verify OTP/TOTP code |
| 2 | POST | `/api/auth/mfa/totp/setup` | JWT | FR-002 | Get TOTP QR code |
| 3 | POST | `/api/auth/mfa/totp/confirm` | JWT | FR-002 | Confirm TOTP setup |
| 4 | POST | `/api/auth/mfa/resend` | mfaToken | FR-001 | Resend OTP SMS/Email |
| 5 | PUT | `/api/auth/mfa/settings` | JWT | FR-003 | Enable/disable MFA |
| 6 | POST | `/api/auth/sso/callback` | Public | FR-006,FR-007 | OAuth2 callback |
| 7 | GET | `/api/auth/sso/providers` | Public | FR-006 | List SSO providers |
| 8 | POST | `/api/auth/sso/link` | JWT | FR-008 | Link SSO identity |
| 9 | DELETE | `/api/auth/sso/unlink/{provider}` | JWT | FR-008 | Unlink SSO identity |
| 10 | POST | `/api/auth/introspect` | Internal | FR-011 | Token introspection |
| 11 | GET | `/.well-known/jwks.json` | Public | FR-010 | JWKS public key |
| 12 | POST | `/api/auth/sessions/{userId}/revoke-all` | ADMIN | FR-012 | Force logout |
| 13 | POST | `/api/auth/change-password` | JWT | FR-013,FR-014 | Change password |
| 14 | POST | `/api/auth/forgot-password` | Public | FR-013 | Initiate password reset |

---

## 5. Data Model Changes

### 5.1 ALTER TABLE users
```sql
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_method VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN trusted_device_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
```

### 5.2 NEW TABLE user_identities
```sql
CREATE TABLE user_identities (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    provider        VARCHAR(50) NOT NULL,
    provider_sub    VARCHAR(255) NOT NULL,
    provider_email  VARCHAR(255),
    provider_name   VARCHAR(200),
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_sub)
);
CREATE INDEX idx_user_identities_user ON user_identities(user_id);
```

### 5.3 NEW TABLE password_policies
```sql
CREATE TABLE password_policies (
    id                       BIGINT PRIMARY KEY,
    domain_id                BIGINT NOT NULL UNIQUE REFERENCES domains(id),
    min_length               INT NOT NULL DEFAULT 8,
    max_length               INT NOT NULL DEFAULT 128,
    require_uppercase        BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase        BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit            BOOLEAN NOT NULL DEFAULT TRUE,
    require_special          BOOLEAN NOT NULL DEFAULT FALSE,
    min_character_types      INT NOT NULL DEFAULT 3,
    history_count            INT NOT NULL DEFAULT 5,
    max_age_days             INT NOT NULL DEFAULT 90,
    lockout_threshold        INT NOT NULL DEFAULT 5,
    lockout_duration_minutes INT NOT NULL DEFAULT 15,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.4 NEW TABLE password_history
```sql
CREATE TABLE password_history (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_history_user ON password_history(user_id);
```

---

## 6. Open Questions (from brainstorm)

> ⚠️ OPEN QUESTION: Q8 — TOTP secret encryption: Jasypt vs custom EncryptionService?
> Decision needed before FR-002 implementation.

> ⚠️ OPEN QUESTION: Q9 — JWKS key rotation: auto-rotate vs manual?
> Affects FR-010 implementation (single key vs multi-key JWKS).

> ⚠️ OPEN QUESTION: Q10 — SSO auto-provision default domain mapping?
> When JIT provisioning → which domain is assigned? Need rule.
