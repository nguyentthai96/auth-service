# Research Brief: Auth Core Features (FR-001 → FR-004)

## 1. Feature Identification

| ID | Feature | Actor | Priority |
|---|---|---|---|
| FR-001 | Multi-Factor Authentication (OTP SMS, Email, TOTP, CAPTCHA) | End User | HIGH |
| FR-002 | SSO & OAuth2 (Google, Microsoft, Keycloak) | End User | HIGH |
| FR-003 | Token Management RS256 + Introspection + Session Binding | System | HIGH |
| FR-004 | Password Policy per Domain (complexity, history, expiry) | System Administrator | MEDIUM |

## 2. Keywords & Search Queries

- `Spring Boot Multi-Factor Authentication TOTP OTP`
- `Spring Security 7 @EnableMultiFactorAuthentication`
- `dev.samstevens.totp Java library`
- `Spring Boot OAuth2 Keycloak auto-provision JIT`
- `JWT RS256 asymmetric JWKS endpoint Spring Boot`
- `Token introspection session binding jti blacklist`
- `Passay password validation configurable per domain`
- `Password history management per tenant`
- `hCaptcha Turnstile reCAPTCHA v3 Spring Boot server-side`

## 3. Research Scope

### In scope
- MFA flow design (OTP + TOTP + CAPTCHA) cho login và sensitive operations
- OAuth2/OIDC integration pattern với Keycloak (optional downstream)
- Migration từ HMAC-SHA256 sang RS256 cho JWT signing
- JWKS endpoint expose cho resource servers
- Password policy table schema cho multi-domain config
- Passay library integration cho dynamic rules
- CAPTCHA pluggable adapter pattern

### Out of scope
- Biometric/WebAuthn/Passkey (future phase)
- SMS provider integration details (Twilio, etc.) — sẽ dùng adapter pattern
- Email sending infrastructure — sẽ dùng event-driven pattern

## 4. Current System Analysis

### 4.1 Related Features (Existing)

| Component | File | Status |
|---|---|---|
| JWT Service | [JwtService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | ⚠️ Dùng HMAC-SHA256 (symmetric) — cần migrate sang RS256 |
| JWT Auth Filter | [JwtAuthFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | ✅ Có token blacklist (jti) — cần thêm session binding |
| Auth Service | [AuthService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | ✅ Login/Register/Refresh/SwitchDomain — **THIẾU MFA step** |
| Security Config | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | ✅ Basic filter chain — **THIẾU OAuth2 login/SSO** |
| Security Properties | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | ⚠️ Có password props (bcryptStrength, maxFailedAttempts) — **THIẾU password policy per domain** |
| User Entity | [UserEntity.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | ✅ Có failedLoginCount, lockedUntilAt — **THIẾU mfaEnabled, totpSecret** |
| Auth Controller | [AuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt) | ✅ Có /login, /register, /refresh — **THIẾU /mfa/verify, /sso/callback** |
| SsoAdapter (stub) | [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | ⚠️ Stub only — cần implement OAuth2 flow |
| PasswordPolicyService (stub) | [PasswordPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | ⚠️ Stub only — cần implement Passay + DB-backed config |

### 4.2 Existing Patterns

- **Architecture**: Hexagonal (port/adapter) — `adapter/in/web`, `adapter/out/persistence`, `application`
- **Entity Base**: `SnowflakePersistentAuditableEntity` (Snowflake ID + audit fields)
- **Config Pattern**: `@ConfigurationProperties` với prefix `app.security`
- **Exception**: RFC 7807 ProblemDetail via `GlobalExceptionHandler`
- **Token Lifecycle**: JWT (access + refresh), refresh token rotation, jti blacklist

### 4.3 Tech Stack Constraints

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9+ |
| Framework | Spring Boot 3.2+ (Spring Security 6+) |
| DB | PostgreSQL 17, Flyway |
| JWT | `io.jsonwebtoken:jjwt-api` 0.12+ |
| Password | BCrypt (strength 12) |
| Build | Gradle Kotlin DSL |
| Base | `com.ntt:base-web-starter`, `base-data-starter`, `common-log` |

### 4.4 Database Schema (Relevant)

- `users` — core user table (already has `password_hash`, `failed_login_count`, `locked_until_at`)
- `refresh_tokens` — hashed refresh tokens with expiry
- `token_blacklist` — jti-based blacklist
- `domains` — multi-domain config (JSONB config field có thể dùng cho password policy)
- `audit_log` — immutable audit trail

### 4.5 Integration Points

| Module | Interaction |
|---|---|
| RBAC Engine | `RbacEngine.getUserRoles()` + `getEffectivePermissions()` → embedded in JWT claims |
| PBAC Engine | `PolicyEvaluator` — attribute-based check, separate from MFA |
| Kafka (planned) | `iam.user.sso_provisioned` topic — notify account-service on JIT provisioning |
| Redis (planned) | Session binding, TOTP rate limiting, OTP storage |
