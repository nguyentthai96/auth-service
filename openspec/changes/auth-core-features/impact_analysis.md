# Impact Analysis: auth-core-features

> Generated: 2026-08-05 | Classification: EXTEND | Flow: Non-Financial

---

## 1. Core Files (affected by this change)

### 1.1 MODIFY (existing files)

| # | File | Path | Action | FR |
|---|------|------|--------|-----|
| M1 | `AuthService.kt` | [AuthService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Add MFA checkpoint in login(), CAPTCHA check, sealed LoginResult return | FR-001,FR-004 |
| M2 | `JwtService.kt` | [JwtService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | Migrate HMAC→RS256, add KeyPair loading, JWKS exposure, dual-algorithm support | FR-009,FR-010 |
| M3 | `AuthController.kt` | [AuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt) | Handle sealed LoginResult, add change-password/forgot-password endpoints | FR-001,FR-013 |
| M4 | `SecurityConfig.kt` | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Add public endpoints for MFA/SSO/JWKS, OAuth2 resource server config | FR-006,FR-010 |
| M5 | `SecurityProperties.kt` | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Add MfaProperties, CaptchaProperties, SsoProperties nested classes | FR-001,FR-004,FR-006 |
| M6 | `UserEntity.kt` | [UserEntity.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | Add mfaEnabled, mfaMethod, totpSecretEncrypted, trustedDeviceHash, passwordChangedAt | FR-001,FR-002,FR-005,FR-013 |
| M7 | `JwtAuthFilter.kt` | [JwtAuthFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | Support RS256 verification (key type change in JwtService) | FR-009 |
| M8 | `SsoAdapter.kt` | [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Full OAuth2 implementation (code exchange, JIT provisioning, identity linking) | FR-006,FR-007,FR-008 |
| M9 | `PasswordPolicyService.kt` | [PasswordPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Full Passay implementation, domain-scoped policy, password history | FR-013,FR-014 |
| M10 | `AuthExceptions.kt` | [AuthExceptions.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt) | Add 12 new exception classes for MFA/SSO/Password | FR-001..FR-014 |
| M11 | `GlobalExceptionHandler.kt` | [GlobalExceptionHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt) | Add handlers for new exceptions | FR-001..FR-014 |
| M12 | `build.gradle.kts` | [build.gradle.kts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | Add Redis, OAuth2, TOTP, Passay dependencies | ALL |
| M13 | `application.yml` | [application.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application.yml) | Add MFA, CAPTCHA, SSO, RS256 config sections | ALL |

### 1.2 NEW (new files to create)

| # | File | Path | Action | FR |
|---|------|------|--------|-----|
| N1 | `MfaService.kt` | `auth/application/MfaService.kt` | MFA orchestrator (initiate, verify, settings) | FR-001,FR-002,FR-003 |
| N2 | `OtpService.kt` | `auth/application/OtpService.kt` | Redis OTP generation/verification (SMS/Email) | FR-001 |
| N3 | `TotpService.kt` | `auth/application/TotpService.kt` | TOTP setup/verify (dev.samstevens.totp) | FR-002 |
| N4 | `CaptchaVerifier.kt` | `auth/application/CaptchaVerifier.kt` | Interface + TurnstileAdapter | FR-004 |
| N5 | `MfaController.kt` | `auth/adapter/in/web/MfaController.kt` | 5 MFA endpoints | FR-001,FR-002,FR-003 |
| N6 | `SsoController.kt` | `auth/adapter/in/web/SsoController.kt` | 4 SSO endpoints | FR-006,FR-007,FR-008 |
| N7 | `TokenController.kt` | `auth/adapter/in/web/TokenController.kt` | Introspect, JWKS, session revoke | FR-010,FR-011,FR-012 |
| N8 | `UserIdentityEntity.kt` | `rbac/adapter/out/persistence/entity/UserIdentityEntity.kt` | SSO identity linking | FR-008 |
| N9 | `PasswordPolicyEntity.kt` | `rbac/adapter/out/persistence/entity/PasswordPolicyEntity.kt` | Domain password policy config | FR-013 |
| N10 | `PasswordHistoryEntity.kt` | `rbac/adapter/out/persistence/entity/PasswordHistoryEntity.kt` | Password reuse prevention | FR-014 |
| N11 | `AuthRepositories.kt` | `auth/adapter/out/persistence/repository/AuthRepositories.kt` | UserIdentityRepository, PasswordPolicyRepository, PasswordHistoryRepository | FR-008,FR-013,FR-014 |
| N12 | `V2__auth_core_features.sql` | `resources/db/migration/V2__auth_core_features.sql` | DDL for new tables + ALTER users | ALL |
| N13 | `LoginResult.kt` | `auth/application/LoginResult.kt` | Sealed class LoginResult | FR-001 |
| N14 | `MfaDtos.kt` | `auth/adapter/in/web/dto/MfaDtos.kt` | MFA request/response DTOs | FR-001,FR-002,FR-003 |
| N15 | `SsoDtos.kt` | `auth/adapter/in/web/dto/SsoDtos.kt` | SSO request/response DTOs | FR-006,FR-007,FR-008 |
| N16 | `TokenDtos.kt` | `auth/adapter/in/web/dto/TokenDtos.kt` | Introspection/JWKS DTOs | FR-010,FR-011 |

---

## 2. Call Tree (affected call chains)

### 2.1 AuthService.login() — PRIMARY IMPACT

```
AuthController.login()  ← entry point
  └── AuthService.login(LoginRequest)  ← MODIFY: return type → LoginResult
        ├── userRepository.findByUsernameAndActiveTrue()
        ├── [NEW] captchaVerifier.verify(captchaToken)  ← FR-004
        ├── passwordEncoder.matches()
        ├── handleFailedLogin()
        ├── [NEW] check user.mfaEnabled  ← FR-001
        │     ├── if true → mfaService.initiateMfa(userId, method)
        │     │     ├── otpService.generateOtp()  ← FR-001
        │     │     └── return LoginResult.MfaRequired
        │     └── if false → generateAuthResponse()
        └── generateAuthResponse()
              ├── rbacEngine.getUserRoles()
              ├── rbacEngine.getEffectivePermissions()
              ├── jwtService.generateAccessToken()  ← MODIFY: RS256 signing
              ├── jwtService.generateRefreshToken()  ← MODIFY: RS256 signing
              └── refreshTokenRepository.save()
```

### 2.2 JwtService — RS256 MIGRATION

```
JwtService.generateAccessToken()
  ├── [MODIFY] signWith(keyPair.private, RS256)  ← was signWith(secretKey)
  └── callers:
      ├── AuthService.generateAuthResponse()
      ├── MfaService.verifyMfa() [NEW]
      └── SsoAdapter.handleCallback() [NEW]

JwtService.parseToken()
  ├── [MODIFY] verifyWith(keyPair.public)  ← was verifyWith(secretKey)
  ├── [NEW] fallback: verifyWith(legacyKey) if RS256 fails  ← 7-day migration
  └── callers:
      ├── JwtAuthFilter.doFilterInternal()
      ├── AuthService.refreshToken()
      └── TokenController.introspect() [NEW]

JwtService [NEW methods]:
  ├── getJwks() → JWKS JSON for /.well-known/jwks.json
  └── generateMfaToken(userId, method) → short-lived MFA JWT
```

---

## 3. Blast Radius

### 3.1 Per-component impact

| Component | Direct (d=1) | Indirect (d=2) | Risk |
|-----------|-------------|----------------|------|
| `AuthService.login()` | 1 caller (AuthController) | 0 | 🟢 LOW |
| `JwtService.signingKey` | 3 methods (generate×2, parse) | 2 callers (AuthService, JwtAuthFilter) | 🟡 MEDIUM |
| `JwtService.parseToken()` | 3 callers | 5+ downstream (all authenticated endpoints) | 🟡 MEDIUM |
| `UserEntity` | 9 callers (all repositories/services using UserEntity) | 15+ (via AuthService, RbacEngine) | 🟡 MEDIUM |
| `SecurityConfig` | 1 (Spring Security chain) | ALL endpoints | 🟡 MEDIUM |
| `AuthExceptions` | 11 callers (all handlers) | GlobalExceptionHandler | 🟢 LOW |

### 3.2 Cross-module impact

| Source Module | Target Module | Mechanism | Impact |
|--------------|--------------|-----------|--------|
| `auth/application` | `shared/security` | JwtService signing key type change | JwtAuthFilter must handle RS256 |
| `auth/application` | `shared/config` | SecurityProperties new nested classes | SecurityConfig reads new props |
| `auth/application` | `rbac/entity` | UserEntity new columns | Flyway migration + entity fields |
| `auth/application` | External (Redis) | New dependency | OtpService, session management |
| `auth/application` | External (OAuth2 IdP) | New dependency | SsoAdapter OAuth2 calls |

---

## 4. Reuse Map

### 4.1 Reuse candidates

| Symbol | Match% | Decision | Reason |
|--------|--------|----------|--------|
| `SnowflakePersistentAuditableEntity` | 100% | **REUSE** | New entities (UserIdentityEntity, etc.) extend this |
| `AuthException` | 100% | **REUSE** | New exceptions extend AuthException |
| `GlobalExceptionHandler` | 100% | **REUSE** (extend) | Add new @ExceptionHandler methods |
| `hashToken()` (AuthService:225) | 80% | **EXTRACT** | Used in AuthService + will be needed in MfaService for device hash |
| `generateAuthResponse()` (AuthService:152) | 100% | **REUSE** | Called from MfaService.verifyMfa() and SsoAdapter.handleCallback() |
| `PasswordPolicyService` | 100% | **REUSE** (rewrite) | Existing stub → full Passay implementation |
| `SsoAdapter` | 100% | **REUSE** (rewrite) | Existing stub → full OAuth2 implementation |

### 4.2 Extract candidates

| Symbol | Source | Target | Callers | Impact |
|--------|--------|--------|---------|--------|
| `hashToken()` | `AuthService` (L225-228) | `TokenUtils.kt` (utility class) | AuthService, MfaService | 🟢 LOW (2 callers) |
| `generateAuthResponse()` | `AuthService` (L152-198) | Keep in AuthService, expose as internal | AuthService, MfaService, SsoAdapter | 🟡 MEDIUM (3 callers) — make it `internal fun` |

### 4.3 New items (no match found)

| Symbol | Reason |
|--------|--------|
| `MfaService` | No existing MFA logic anywhere |
| `OtpService` | No OTP generation/Redis integration |
| `TotpService` | No TOTP library usage |
| `CaptchaVerifier` | No CAPTCHA integration |
| `MfaController` | No MFA API endpoints |
| `SsoController` | No SSO API endpoints |
| `TokenController` | No token introspection/JWKS endpoints |

---

## 5. Context Snapshot

### 5.1 Dependencies (current → after)

| Dependency | Current | After |
|-----------|---------|-------|
| `spring-boot-starter-security` | ✅ | ✅ |
| `jjwt-api/impl/jackson` | ✅ | ✅ (RS256 support native) |
| `spring-boot-starter-data-redis` | ❌ | ✅ NEW |
| `spring-boot-starter-oauth2-resource-server` | ❌ | ✅ NEW |
| `spring-boot-starter-oauth2-client` | ❌ | ✅ NEW |
| `dev.samstevens.totp:totp:1.7.1` | ❌ | ✅ NEW |
| `org.passay:passay:1.6.4` | ❌ | ✅ NEW |

### 5.2 Database (current → after)

| Table | Current | After |
|-------|---------|-------|
| `users` | ✅ 12 columns | ✅ 17 columns (+5: mfa_enabled, mfa_method, totp_secret_encrypted, trusted_device_hash, password_changed_at) |
| `user_identities` | ❌ | ✅ NEW (SSO identity linking) |
| `password_policies` | ❌ | ✅ NEW (domain-scoped policy) |
| `password_history` | ❌ | ✅ NEW (reuse prevention) |

### 5.3 API Endpoints (current → after)

| Current (4) | New (14) | Total |
|------------|----------|-------|
| POST /api/auth/login | POST /api/auth/mfa/verify | 18 |
| POST /api/auth/register | POST /api/auth/mfa/totp/setup | |
| POST /api/auth/refresh | POST /api/auth/mfa/totp/confirm | |
| POST /api/auth/switch-domain | POST /api/auth/mfa/resend | |
| | PUT /api/auth/mfa/settings | |
| | POST /api/auth/sso/callback | |
| | GET /api/auth/sso/providers | |
| | POST /api/auth/sso/link | |
| | DELETE /api/auth/sso/unlink/{provider} | |
| | POST /api/auth/introspect | |
| | GET /.well-known/jwks.json | |
| | POST /api/auth/sessions/{userId}/revoke-all | |
| | POST /api/auth/change-password | |
| | POST /api/auth/forgot-password | |

### 5.4 Configuration (application.yml additions)

```yaml
# NEW sections to add:
app.security.mfa.*           # OTP TTL, max attempts, TOTP window
app.security.captcha.*       # provider, secret-key, site-key
app.security.sso.*           # enabled, providers
app.security.jwt.algorithm   # RS256
app.security.jwt.private-key-path
app.security.jwt.public-key-path
app.security.jwt.key-id
spring.data.redis.*          # Redis connection
spring.security.oauth2.client.registration.*  # OAuth2 providers
```
