---
type: brainstorm_notes
change: auth-core-features
date: 2026-08-05
selected_direction: "Approach 1: Layered Extension (Bottom-Up)"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Core Features (MFA + SSO + RS256 + Password Policy)

## Date
2026-08-05

## Context

Hệ thống ERP IAM hiện có `auth-service` với login/register/refresh/switchDomain flow đang dùng HMAC-SHA256. Cần mở rộng với 4 tính năng core: MFA (OTP SMS/Email, TOTP, CAPTCHA), SSO/OAuth2 (Keycloak, Google, Microsoft), JWT RS256 migration, và Dynamic Password Policy per domain. Research phase đã hoàn thành với library selection và gap analysis.

**Source context:**
- `pre_openspec.md`: 17 FRs (14 URD + 3 Enriched), quality 88/100
- `technical_spec.md`: Sequence diagrams, ERD, API spec
- `comparison_analysis.md`: Library decisions finalized
- `handoff_summary.md`: BUILD approach, 10/10 gaps addressed

---

## Questions Asked & Answers

### Q1: Login flow nên "wrap" hay "fork" khi thêm MFA?
**→ A: WRAP (decorator pattern trên login flow hiện tại)**

Phân tích `AuthService.login()` (line 74-107):
```
Current flow:
  validate credentials → check lock → verify password → reset failedCount → generateAuthResponse

With MFA:
  validate credentials → check lock → verify password → reset failedCount
  → [NEW] check user.mfaEnabled
    → if false: generateAuthResponse (unchanged)
    → if true: return MfaRequiredResponse (NEW return type)
```

**Key insight**: `AuthService.login()` return type hiện là `AuthResponse`. MFA yêu cầu 2 return types khác nhau:
1. `AuthResponse` (full tokens — no MFA hoặc MFA đã verified)
2. `MfaRequiredResponse` (mfaToken + method — MFA pending)

**Design decision**: Dùng **sealed class** `LoginResult` thay vì thay đổi `AuthResponse`:
```kotlin
sealed class LoginResult {
    data class Success(val response: AuthResponse) : LoginResult()
    data class MfaRequired(val mfaToken: String, val method: String, val expiresIn: Long) : LoginResult()
}
```
Controller sẽ map `LoginResult` → HTTP response tương ứng.

### Q2: MFA token nên lưu Redis hay JWT stateless?
**→ A: JWT stateless (short-lived, type=mfa)**

Trade-offs:
| | Redis-backed | JWT stateless |
|---|---|---|
| Scalability | ⚠️ Redis single point | ✅ Stateless |
| Revocation | ✅ DEL key | ⚠️ Không revoke được (nhưng TTL=5min) |
| Complexity | ⚠️ Thêm Redis round-trip | ✅ Decode tại chỗ |
| Replay protection | ✅ One-time use | ⚠️ Cần counter check |

**Decision**: JWT stateless cho mfaToken VÌ:
- TTL chỉ 5 phút → risk window nhỏ
- Replay protection qua Redis OTP key (DEL sau verify) — mfaToken có thể reuse nhưng OTP code không thể
- Giảm Redis dependency cho authentication flow

**Nhưng OTP code** vẫn lưu Redis: `otp:{userId}:{channel}` với TTL=300s.

### Q3: RS256 migration strategy — big bang hay dual algorithm?
**→ A: Big Bang (invalidate all tokens) VỚI migration window**

Phân tích `JwtService.kt` (line 23-25):
```kotlin
private val signingKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(securityProperties.jwt.secretKey.toByteArray())
}
```
Đây là `SecretKey` (symmetric). RS256 cần `KeyPair` (asymmetric).

**3 approaches xem xét:**

```
Option A: Dual Algorithm (safe but complex)
┌──────────────────────────────────────────┐
│ JwtService                               │
│  ├── signingKey: KeyPair (RS256)         │ ← sign mới
│  └── legacyKey: SecretKey (HMAC)         │ ← verify cũ
│                                          │
│  parseToken():                           │
│    1. Try RS256 verify                   │
│    2. If fail → Try HMAC verify          │
│    3. If both fail → TokenExpired        │
└──────────────────────────────────────────┘
Duration: 2-4 tuần → remove legacy

Option B: Big Bang (simple but breaking)
┌──────────────────────────────────────────┐
│ Deploy plan:                             │
│  1. Generate RSA key pair                │
│  2. Update config: algorithm=RS256       │
│  3. Deploy → ALL existing JWT invalid    │
│  4. All users forced re-login            │
└──────────────────────────────────────────┘
Duration: immediate

Option C: Refresh-based migration
┌──────────────────────────────────────────┐
│ 1. New tokens: RS256                     │
│ 2. Old tokens: still accepted (HMAC)     │
│ 3. On refresh: old → new RS256 token     │
│ 4. After 7 days: remove HMAC support     │
│    (max refresh_token TTL = 7 days)      │
└──────────────────────────────────────────┘
Duration: 7 ngày tự migration
```

**Decision: Option C (Refresh-based)**
- Không breaking (users không bị force logout)
- Tự cleanup sau 7 ngày (= max refresh token TTL)
- Complexity vừa phải (chỉ cần dual verify, remove sau 1 sprint)

### Q4: SecurityProperties nên extend trực tiếp hay tạo nested class mới?
**→ A: Extend with nested classes**

`SecurityProperties` hiện tại chỉ có `jwt` và `password`. Cần thêm:
```kotlin
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val enabled: Boolean = true,
    val jwt: JwtProperties = JwtProperties(),
    val password: PasswordProperties = PasswordProperties(),
    // NEW
    val mfa: MfaProperties = MfaProperties(),
    val captcha: CaptchaProperties = CaptchaProperties(),
    val sso: SsoProperties = SsoProperties()
)
```

**JwtProperties cần thay đổi:**
```kotlin
data class JwtProperties(
    val secretKey: String = "",           // DEPRECATED (HMAC legacy)
    val algorithm: String = "RS256",       // NEW
    val privateKeyPath: String = "",       // NEW
    val publicKeyPath: String = "",        // NEW
    val keyId: String = "default",         // NEW (cho JWKS kid)
    val accessTokenExpirationMs: Long = 1_800_000,
    val refreshTokenExpirationMs: Long = 604_800_000,
    val issuer: String = "auth-service"
)
```

### Q5: SSO — Spring Security OAuth2 Login hay manual code exchange?
**→ A: Hybrid — dùng Spring OAuth2 Client cho token exchange, manual controller cho callback**

**Reasoning:**
- Spring Security OAuth2 Login tự động redirect browser → KHÔNG phù hợp cho SPA/mobile (frontend gọi callback API)
- Spring OAuth2 `OAuth2AuthorizedClientService` vẫn hữu ích để exchange code → token
- Custom `SsoController` xử lý business logic (JIT provisioning, identity linking)

```
Frontend flow:
  1. FE redirect user → IdP authorization URL
  2. IdP redirect → FE callback page (with code)
  3. FE gửi POST /api/auth/sso/callback {code, provider, redirectUri}
  4. Backend exchange code → tokens via Spring OAuth2 RestTemplate
  5. Backend parse id_token → extract claims
  6. Backend JIT provision nếu cần → issue internal JWT
```

### Q6: CAPTCHA — filter level hay service level?
**→ A: Service level (AuthService) — KHÔNG filter level**

**Reasoning:**
- CAPTCHA chỉ áp dụng cho login endpoint, không phải tất cả requests
- Login request cần biết `failedLoginCount` từ DB trước khi quyết định yêu cầu CAPTCHA
- Filter level sẽ force CAPTCHA cho tất cả requests → quá aggressive
- Service level cho phép conditional: chỉ khi `failedLoginCount >= threshold`

```
AuthService.login():
  ...validate credentials...
  if (failedLoginCount >= captchaThreshold && captchaToken == null):
    throw CaptchaRequiredException()
  if (captchaToken != null):
    captchaVerifier.verify(captchaToken)  // pluggable adapter
  ...continue login...
```

### Q7: UserEntity — thêm columns trực tiếp hay tách riêng MFA entity?
**→ A: Thêm trực tiếp vào UserEntity + ALTER TABLE**

**Reasoning:**
- MFA fields (mfa_enabled, mfa_method, totp_secret_encrypted, trusted_device_hash, password_changed_at) thuộc user identity trực tiếp
- Tách entity riêng sẽ tạo N+1 query trên mỗi login
- Sử dụng Flyway migration V2 để ALTER TABLE

```sql
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_method VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN trusted_device_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
```

---

## Approaches Considered

### Approach 1: Layered Extension (Bottom-Up) ⭐ SELECTED

Thêm features theo layers: Migration → Entity → Repository → Service → Controller.

```
Sprint 1 (Foundation):
  ┌─────────────────────────────────────────────┐
  │ V2 Migration → New Entities → Repositories  │
  │ Dependencies (Redis, TOTP, Passay, OAuth2)  │
  │ SecurityProperties extend                    │
  │ JwtService RS256 migration                   │
  │ PasswordPolicyService impl                   │
  └─────────────────────────────────────────────┘

Sprint 2 (MFA):
  ┌─────────────────────────────────────────────┐
  │ OtpService + TotpService + MfaService       │
  │ CaptchaVerifier interface + adapters        │
  │ AuthService.login() MFA checkpoint          │
  │ MfaController (5 endpoints)                  │
  └─────────────────────────────────────────────┘

Sprint 3 (SSO + Token):
  ┌─────────────────────────────────────────────┐
  │ SsoAdapter full impl + SsoController        │
  │ UserIdentityEntity + identity linking       │
  │ TokenController (introspection, JWKS)       │
  │ Force logout (session revocation)           │
  └─────────────────────────────────────────────┘
```

**Pros:**
- Incremental: mỗi sprint là deployable
- Foundation (Sprint 1) không break existing flow
- MFA (Sprint 2) có thể test standalone
- SSO (Sprint 3) phụ thuộc IdP config → deploy sau

**Cons:**
- 3 sprints (chậm hơn)
- Sprint 1 thay đổi JwtService → ảnh hưởng JwtAuthFilter

### Approach 2: Feature-Parallel (Big Bang)

Implement tất cả 4 features đồng thời trong 1 sprint.

```
  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │   MFA    │  │   SSO    │  │  RS256   │  │ Password │
  │ Service  │  │ Adapter  │  │ Migrate  │  │  Policy  │
  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
       │              │              │              │
       └──────────────┴──────────────┴──────────────┘
                         │
                   Single V2 Migration
                   Single Deploy
```

**Pros:**
- 1 migration, 1 deploy
- Tất cả features available cùng lúc

**Cons:**
- ❌ Big change = big risk
- ❌ Khó review, khó test
- ❌ Nếu 1 feature bug → block tất cả

### Approach 3: Feature Flag Toggle

Mỗi feature gắn feature flag, deploy tất cả nhưng toggle ON/OFF.

```
app:
  features:
    mfa-enabled: false
    sso-enabled: false
    rs256-enabled: false
    password-policy-enabled: false
```

**Pros:**
- Deploy sớm, enable dần
- Rollback = toggle OFF
- Canary testing per feature

**Cons:**
- Thêm complexity (if/else scattered)
- Feature flag cleanup debt
- RS256 migration không phù hợp toggle (key type thay đổi)

---

## Selected Direction

**Approach 1: Layered Extension (Bottom-Up)** kết hợp **elements từ Approach 3** (feature flag cho SSO và MFA, KHÔNG cho RS256/Password Policy).

**Reasoning:**
1. RS256 migration là infrastructure change → phải apply trước, không toggle
2. Password Policy là data-level change → apply song song RS256
3. MFA và SSO là user-facing features → CÓ THỂ toggle via config
4. Bottom-up order đảm bảo mỗi layer hoàn chỉnh trước khi build layer trên

**Execution order:**

```
Phase 1: Infrastructure (non-breaking)
  ├── V2 Flyway migration (new tables + ALTER users)
  ├── New entities (UserIdentityEntity, PasswordPolicyEntity, PasswordHistoryEntity)
  ├── New repositories
  ├── SecurityProperties extend (mfa, captcha, sso sections)
  ├── JwtService RS256 migration (with HMAC fallback 7 days)
  └── Dependencies (build.gradle.kts)

Phase 2: Domain Services
  ├── PasswordPolicyService (Passay impl)
  ├── OtpService (Redis OTP generation/verification)
  ├── TotpService (dev.samstevens.totp)
  ├── MfaService (orchestrator)
  ├── CaptchaVerifier interface + TurnstileAdapter
  └── SsoAdapter full impl

Phase 3: API Layer
  ├── AuthService.login() → sealed LoginResult
  ├── AuthController → handle LoginResult
  ├── MfaController (5 endpoints)
  ├── SsoController (4 endpoints)
  ├── TokenController (introspect, JWKS, revoke)
  └── AuthController additions (change-password, forgot/reset)

Phase 4: Integration & Polish
  ├── SecurityConfig → add public endpoints
  ├── AuthExceptions → new exception classes
  ├── GlobalExceptionHandler → new handlers
  └── application.yml → mfa/captcha/sso/rs256 config
```

---

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Non-Financial (OTP verification, không payment)
- Affected modules:
  - `auth/application` (AuthService, JwtService, PasswordPolicyService, SsoAdapter + 3 NEW services)
  - `auth/adapter/in/web` (AuthController + 3 NEW controllers)
  - `rbac/adapter/out/persistence/entity` (UserEntity ALTER + 3 NEW entities)
  - `rbac/adapter/out/persistence/repository` (3 NEW repositories)
  - `shared/config` (SecurityConfig, SecurityProperties)
  - `shared/security` (JwtAuthFilter modify)
  - `shared/exception` (12 NEW exceptions)

---

## Codebase Investigation Findings

### AuthService.login() Impact Analysis

```
AuthService.login() — Line 74-107
  ├── Called by: AuthController.login() (line 34)
  ├── Calls:
  │   ├── userRepository.findByUsernameAndActiveTrue()
  │   ├── passwordEncoder.matches()
  │   ├── handleFailedLogin()
  │   └── generateAuthResponse()
  │       ├── rbacEngine.getUserRoles()
  │       ├── rbacEngine.getEffectivePermissions()
  │       ├── jwtService.generateAccessToken()
  │       ├── jwtService.generateRefreshToken()
  │       └── refreshTokenRepository.save()
  └── Return type: AuthResponse
```

**Modification plan:**
- Insert MFA checkpoint AFTER password validation (line 96-99)
- Insert CAPTCHA checkpoint BEFORE password validation (line 91)
- Change return type từ `AuthResponse` → `LoginResult` (sealed class)
- `generateAuthResponse()` remains unchanged

### JwtService Signing Key Migration

```
Current (HMAC):
  signingKey: SecretKey ← Keys.hmacShaKeyFor(secretKey.toByteArray())
  signWith(signingKey)  ← HMAC-SHA256

Target (RS256):
  keyPair: KeyPair ← load from PEM files
  signWith(keyPair.private, Jwts.SIG.RS256)  ← RSA sign
  verifyWith(keyPair.public)  ← RSA verify
```

**JJWT API compatibility**: JJWT 0.12+ supports both `signWith(SecretKey)` and `signWith(PrivateKey, alg)` — no API breaking change.

### SecurityConfig Public Endpoints

```
Current permitAll:
  /api/auth/login, /api/auth/register, /api/auth/refresh
  /actuator/**

Need to add:
  /api/auth/mfa/verify          ← uses mfaToken (not JWT)
  /api/auth/mfa/resend          ← uses mfaToken (not JWT)
  /api/auth/sso/callback        ← public callback
  /api/auth/sso/providers       ← public list
  /api/auth/forgot-password     ← public
  /api/auth/reset-password      ← public (with reset token)
  /.well-known/jwks.json        ← public JWKS
  /api/auth/introspect          ← internal (service-to-service auth)
```

### UserEntity Column Impact

```
Current columns: 12
New columns: 5 (mfa_enabled, mfa_method, totp_secret_encrypted,
                 trusted_device_hash, password_changed_at)
Total after: 17

passwordHash field stays nullable=false.
SSO users sẽ CÓ passwordHash = "${NO_PASSWORD}" hoặc random unmatched hash.
```

**Design decision**: SSO-only users vẫn có `passwordHash` field nhưng set giá trị không match được (`!SSO_ONLY!`). KHÔNG thay đổi `nullable = false` constraint để tránh breaking existing code.

### Redis Key Namespace Design

```
otp:{userId}:{channel}          → OTP code (TTL 300s)
  Example: otp:123456:SMS       → "482931"

otp:{userId}:{channel}:attempts → Attempt counter (TTL 300s)
  Example: otp:123456:SMS:attempts → "2"

mfa:totp:setup:{userId}         → Pending TOTP secret (TTL 600s)
  Example: mfa:totp:setup:123456 → "JBSWY3DPEHPK3PXP"

session:user:{userId}:jti       → Active JTI set (TTL = accessToken TTL)
  Example: session:user:123456:jti → SET {"jti1", "jti2"}
```

---

## Architecture Diagram

```
                    ┌──────────────────────────────────────────┐
                    │              auth-service                │
                    │                                          │
                    │  ┌──────────────────────────────────┐   │
                    │  │       Controller Layer            │   │
  ┌─────────┐      │  │  ┌────────────┐ ┌────────────┐  │   │
  │  Client  │─────▶│  │  │ AuthCtrl   │ │  MfaCtrl   │  │   │
  │ (SPA/App)│      │  │  └─────┬──────┘ └─────┬──────┘  │   │
  └─────────┘      │  │  ┌─────┴──────┐ ┌─────┴──────┐  │   │
                    │  │  │ SsoCtrl    │ │ TokenCtrl  │  │   │
                    │  │  └─────┬──────┘ └─────┬──────┘  │   │
                    │  └────────┼───────────────┼─────────┘   │
                    │           │               │              │
                    │  ┌────────▼───────────────▼─────────┐   │
                    │  │       Service Layer               │   │
                    │  │                                    │   │
                    │  │  AuthService ──▶ MfaService       │   │
                    │  │       │              │             │   │
                    │  │       │         ┌────┼─────┐      │   │
                    │  │       │         │    │     │      │   │
                    │  │       ▼         ▼    ▼     ▼      │   │
                    │  │  JwtService  OtpSvc TotpSvc CaptchaV │
                    │  │  (RS256)                           │   │
                    │  │       │       SsoAdapter           │   │
                    │  │       │          │                 │   │
                    │  │  PasswordPolicyService             │   │
                    │  └───┬───┼──────────┼────────────────┘   │
                    │      │   │          │                     │
                    │  ┌───▼───▼──────────▼────────────────┐   │
                    │  │       Data Layer                   │   │
                    │  │  UserEntity  UserIdentityEntity    │   │
                    │  │  PasswordPolicyEntity              │   │
                    │  │  PasswordHistoryEntity             │   │
                    │  │  RefreshTokenEntity (existing)     │   │
                    │  │  TokenBlacklistEntity (existing)   │   │
                    │  └───────────────────────────────────┘   │
                    └──────┬───────────┬───────────┬───────────┘
                           │           │           │
                    ┌──────▼──┐ ┌──────▼──┐ ┌──────▼──────┐
                    │PostgreSQL│ │  Redis  │ │   Kafka     │
                    │ (auth_db)│ │(OTP/Ses)│ │(sso_event)  │
                    └─────────┘ └─────────┘ └─────────────┘
                                                    │
                    ┌──────────┐  ┌──────────┐     │
                    │ Google   │  │Keycloak  │     ▼
                    │ OAuth2   │  │ OIDC     │  account-service
                    └──────────┘  └──────────┘
```

---

## Risk Analysis

### Risk 1: RS256 Key Management
**Severity**: 🟡 MEDIUM
**Issue**: RSA private key phải được bảo vệ (không hardcode, không commit Git)
**Mitigation**:
- Environment variable: `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`
- Kubernetes: Secret mount
- Dev: Generate tại startup nếu files không tồn tại (auto-generate mode)

### Risk 2: Redis Dependency — Single Point of Failure
**Severity**: 🟡 MEDIUM
**Issue**: MFA OTP phụ thuộc Redis. Redis down = không login được (MFA users)
**Mitigation**:
- Redis Sentinel cho HA
- Graceful degradation: nếu Redis down, skip MFA check (configurable emergency bypass)
- TOTP không cần Redis (stateless verification)

### Risk 3: TOTP Secret Encryption
**Severity**: 🟡 MEDIUM
**Issue**: TOTP secret phải AES-256 encrypted at rest. Key management?
**Mitigation**:
- Jasypt (`@Encryptable`) cho application.yml
- Hoặc custom `EncryptionService` với AES-256-GCM
- Encryption key qua environment variable: `TOTP_ENCRYPTION_KEY`

### Risk 4: OAuth2 Provider Downtime
**Severity**: 🟢 LOW
**Issue**: External IdP (Google/Keycloak) down → SSO login failed
**Mitigation**:
- SSO users vẫn có thể login bằng password (nếu đã set)
- Timeout = 10s → fail fast
- Error response rõ ràng: `SSO_PROVIDER_TIMEOUT`

---

## Open Questions for Design Phase

- [RESOLVED] Q1: Login return type → sealed class LoginResult ✅
- [RESOLVED] Q2: MFA token storage → JWT stateless ✅
- [RESOLVED] Q3: RS256 migration → Refresh-based (Option C) ✅
- [RESOLVED] Q4: SecurityProperties → extend with nested classes ✅
- [RESOLVED] Q5: SSO approach → Hybrid (Spring OAuth2 Client + manual controller) ✅
- [RESOLVED] Q6: CAPTCHA → Service level ✅
- [RESOLVED] Q7: UserEntity → thêm columns trực tiếp ✅
- [OPEN] Q8: Cần Encryption Service riêng hay dùng Jasypt cho TOTP secret encryption?
- [OPEN] Q9: JWKS key rotation strategy — auto-rotate hay manual?
- [OPEN] Q10: SSO auto-provision default domain mapping rule?

## Open Questions for URD Analysis

- Không có (URD đã được phân tích đầy đủ trong pre_openspec.md)

---

## Design Decisions Summary

| # | Decision | Rationale | Impact |
|---|----------|-----------|--------|
| D1 | Sealed class `LoginResult` cho login return | Type-safe handling MFA vs Success | AuthService, AuthController |
| D2 | MFA token = JWT stateless (5min TTL) | Giảm Redis round-trip, OTP code vẫn in Redis | MfaService |
| D3 | RS256 via refresh-based migration (7 days) | Non-breaking, auto-cleanup | JwtService, JwtAuthFilter |
| D4 | CAPTCHA at service level (not filter) | Conditional check based on failedLoginCount | AuthService |
| D5 | SSO hybrid approach | Spring OAuth2 Client + manual SsoController | SsoAdapter, SsoController |
| D6 | UserEntity columns (not separate entity) | Avoid N+1 on login | UserEntity, V2 migration |
| D7 | Passay factory per domain | ConcurrentHashMap cache, invalidate on policy update | PasswordPolicyService |
| D8 | SSO-only user: passwordHash = "!SSO_ONLY!" | Keep NOT NULL constraint, prevent login via password | AuthService.login() |
| D9 | Redis key namespace: `otp:{userId}:{channel}` | Clean TTL-based cleanup, no manual cleanup needed | OtpService |
| D10 | Feature config via properties (not DB flags) | Simple, env-injectable, no extra DB query | SecurityProperties |
