# Research Brief: Auth Core Features

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Auth Core Features (FR-001 → FR-004) |
| **Ngày tạo** | 2026-08-22 |
| **Input source** | name |
| **Input content** | auth-core-features — MFA, SSO/OAuth2, Token RS256 + Introspection, Password Policy per Domain |
| **Người yêu cầu** | Pipeline (auto) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hệ thống ERP IAM hiện tại cần bảo vệ tài khoản người dùng bằng nhiều lớp xác thực (MFA), hỗ trợ đăng nhập liền mạch qua enterprise IdPs (SSO), sử dụng asymmetric JWT signing (RS256) để resource servers verify token mà không cần shared secret, và enforce password complexity rules linh hoạt theo từng business domain. Đây là 4 functional requirements trọng yếu cho bất kỳ hệ thống auth nào ở cấp enterprise.

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Triển khai MFA (OTP SMS/Email + TOTP Authenticator App + CAPTCHA + Recovery Codes) cho login và sensitive operations
- [x] Objective 2: Tích hợp SSO/OAuth2 với Google, Microsoft, Keycloak — hỗ trợ JIT provisioning + Kafka event publishing
- [x] Objective 3: JWT signing RS256 asymmetric, expose JWKS endpoint, Token Introspection (RFC 7662)
- [x] Objective 4: Xây dựng password policy per domain với Passay library, bao gồm password history và expiry enforcement

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| MFA flow design (OTP + TOTP + CAPTCHA + Recovery Codes) | Biometric/WebAuthn/Passkey (future phase) |
| OAuth2/OIDC integration pattern (Google, Microsoft, Keycloak) | SMS/Email provider integration details — adapter pattern |
| RS256 JWT signing + JWKS endpoint | Email sending infrastructure — event-driven |
| Token introspection (RFC 7662) | External KMS integration |
| Password policy per domain (Passay) | Admin UI for password policy management |
| Password history + expiry checking | Password breach database lookup |
| Session binding + force logout | Session analytics dashboard |
| Pluggable CAPTCHA adapter (Turnstile, ALTCHA, Noop) | CAPTCHA analytics |
| Trusted device skip MFA (with TTL enforcement via V10) | Device management admin panel |
| Recovery codes (single-use, 10 codes per user) | Recovery code admin regeneration UI |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords

- `Spring Boot Multi-Factor Authentication TOTP OTP`
- `Spring Security OAuth2 Keycloak SSO integration`
- `JWT RS256 asymmetric JWKS endpoint Spring Boot`
- `Token introspection RFC 7662`
- `Password policy per tenant domain Passay`

### 3.2 Secondary Keywords

- `dev.samstevens.totp Java library`
- `Spring Boot OAuth2 auto-provision JIT`
- `Passay password validation configurable rules`
- `ALTCHA Turnstile CAPTCHA Spring Boot`
- `Redis OTP storage TTL rate limiting`
- `TOTP secret AES-256-GCM encryption at rest`
- `MFA recovery codes single-use backup`
- `trusted device TTL cookie hash`

### 3.3 Domain-Specific Terms

- `MFA (Multi-Factor Authentication)`: Xác thực đa yếu tố — yêu cầu ≥2 factors (something you know + something you have)
- `TOTP (Time-based One-Time Password)`: OTP dựa trên thời gian, RFC 6238, dùng Authenticator App
- `OTP (One-Time Password)`: Mã xác thực dùng 1 lần, gửi qua SMS/Email
- `JIT Provisioning (Just-In-Time)`: Tự động tạo tài khoản local khi lần đầu đăng nhập qua SSO
- `JWKS (JSON Web Key Set)`: Endpoint expose public keys cho resource servers verify JWT
- `Token Introspection (RFC 7662)`: Endpoint cho resource servers validate token mà không cần parse locally
- `Passay`: Java library cho password validation với composable rules
- `Recovery Codes`: Single-use backup codes cho MFA khi user không có access tới OTP/TOTP device

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Spring Boot 3 MFA TOTP implementation best practices"` | General | High |
| 2 | `"TOTP OTP Java library open source GitHub"` | Open Source | High |
| 3 | `"Spring Security OAuth2 Keycloak JIT provisioning"` | Architecture | High |
| 4 | `"JWT RS256 HMAC migration JWKS endpoint"` | Architecture | Medium |
| 5 | `"Passay dynamic password validation per tenant"` | Open Source | Medium |
| 6 | `"CAPTCHA pluggable adapter Turnstile ALTCHA Spring Boot"` | Comparison | Medium |
| 7 | `"token introspection RFC 7662 Spring Boot implementation"` | Architecture | Medium |
| 8 | `"MFA recovery codes backup codes implementation"` | Architecture | Medium |
| 9 | `"password history table schema best practices"` | Architecture | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JWT Service (RS256 + HMAC fallback) | `auth.application.JwtService` | High | RS256 primary, HMAC-SHA256 fallback (7-day migration). Has `generateMfaToken()`, `parseMfaToken()`, `getJwks()` |
| Auth Service (login/register) | `auth.application.AuthService` | High | Integrate MFA checkpoint, password expiry check, CAPTCHA check |
| MFA Service | `auth.application.MfaService` | High | Orchestrate OTP/TOTP/trusted device + recovery codes (generate/verify/count) |
| OTP Service | `auth.application.OtpService` | High | Redis-backed OTP generation + verification with TTL |
| TOTP Service | `auth.application.TotpService` | High | `dev.samstevens.totp`, AES-256-GCM encryption for secret at rest |
| CAPTCHA Verifier | `auth.application.CaptchaVerifier` | High | Pluggable interface: `TurnstileCaptchaVerifier`, `AltchaCaptchaVerifier`, `NoopCaptchaVerifier` |
| SSO Adapter | `auth.application.SsoAdapter` | High | OAuth2 callback, JIT provisioning, identity link/unlink, `EventPublisher` port for Kafka events |
| Password Policy Service | `auth.application.PasswordPolicyService` | High | Passay dynamic rules, history check, domain-scoped, `ConcurrentHashMap` cache, expiry check |
| Security Config | `shared.config.SecurityConfig` | Medium | Filter chain config — JWT auth filter, rate limit filter |
| MFA Rate Limit Service | `auth.application.MfaRateLimitService` | Medium | Redis-backed multi-type rate limiting (MFA_LOGIN, OTP_VERIFY) |
| Login Session Service | `auth.application.LoginSessionService` | Medium | Session management, max sessions per user |
| Session Policy Service | `auth.application.SessionPolicyService` | Medium | Session policy enforcement |
| JWT Auth Filter | `shared.security.JwtAuthFilter` | Medium | Token validation filter with jti blacklist check |
| RBAC Engine | `rbac.application.RbacEngine` | Medium | Provides roles/permissions for JWT claims |
| Audit Log Service | `shared.audit.AuditLogService` | Low | Immutable audit trail for security events |
| OAuth2 Token Exchanger | `auth.adapter.out.sso.OAuth2TokenExchanger` | High | Config-driven code exchange with Google/Microsoft/Keycloak |
| Event Publisher (Port) | `auth.application.port.out.EventPublisher` | Medium | Port interface for domain events (SSO provisioned) |
| Service Token Service | `auth.application.ServiceTokenService` | Low | Service-to-service token management |

### 4.2 Existing Code Patterns

- **Architecture**: Hexagonal (port/adapter) — `adapter/in/web`, `adapter/out/persistence`, `adapter/out/sso`, `adapter/out/http`, `adapter/out/gateway`, `application`, `domain`
- **Entity Base**: `SnowflakePersistentAuditableEntity` (Snowflake ID + audit fields + soft-delete via `active`) for `UserEntity`; `SnowflakeBaseEntity` for others
- **Config Pattern**: `@ConfigurationProperties(prefix = "app.security")` with nested classes (`SecurityProperties`)
- **Exception**: RFC 7807 ProblemDetail via `GlobalExceptionHandler`
- **Auth-specific exceptions**: `AuthCoreExceptions.kt` — `MfaCodeInvalidException`, `MfaTokenExpiredException`, `MfaMaxAttemptsException`, `TotpNotSetupException`, `SsoUserNotProvisionedException`, `SsoIdentityConflictException`, `SsoTokenInvalidException`, `CannotUnlinkLastIdentityException`, `PasswordPolicyViolationException`, `PasswordRecentlyUsedException`, `PasswordExpiredException`
- **Token Lifecycle**: JWT (access + refresh), refresh token rotation, jti blacklist, mfaToken (short-lived 5min)
- **CQRS Pattern**: Command/Handler pattern (`LoginCommand` → `LoginHandler`, `RegisterCommand` → `RegisterHandler`, `SwitchDomainCommand` → `SwitchDomainHandler`, `RevokeSessionsCommand` → `RevokeSessionsHandler`)
- **Event Pattern**: Domain events via `EventPublisher` port (`SsoProvisionedEvent`)
- **Rate Limiting**: Multi-dimensional Redis-backed with Lua scripts (`MfaRateLimitService`, `RateLimitType` enum)
- **I18n**: Database-backed `MessageSource` via `DatabaseMessageSource`
- **Cipher/E2EE**: `CipherProperties` with AES-GCM encryption, X25519 key exchange (Google Tink)
- **Two-tier Cache**: `AbstractTwoTierCache` for L1 (Caffeine) + L2 (Redis)

### 4.3 Tech Stack Constraints

- Language: Kotlin 1.9+
- Framework: Spring Boot 3.2+ (Spring Security 6+)
- Database: PostgreSQL 17, Flyway migrations (V1..V10)
- JWT: `io.jsonwebtoken:jjwt-api` 0.12+
- TOTP: `dev.samstevens.totp:totp` 1.7.1
- Password: `org.passay:passay` 1.6.4, BCrypt (strength 12)
- Cache: Caffeine L1 (in-memory) + Redis L2
- Redis: Spring Data Redis (OTP, rate limiting, session, recovery codes)
- OAuth2: `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server`
- Kafka: `spring-kafka` (runtime dependency for SSO events)
- E2EE: Google Tink 1.15.0
- Build: Gradle Kotlin DSL
- Base: `com.ntt:platform`, `base-web-starter`, `base-data-starter`, `base-security-starter`, `base-observability-starter`, `common-log`, `eventsourcing-utils`

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| RBAC Engine | Module | `rbac.application.RbacEngine` | `getUserRoles()` + `getEffectivePermissions()` → embedded in JWT claims |
| PBAC Engine | Module | `pbac.application.PolicyEvaluator` | Attribute-based check, separate from MFA |
| Redis | Infrastructure | Spring Data Redis + Lua scripts | OTP storage, MFA rate limits, login rate limits, session management, recovery codes, TOTP setup pending |
| Kafka | Event Bus | `spring-kafka` via `EventPublisher` port | `SsoProvisionedEvent` — notify downstream services on JIT provision |
| OAuth2 Token Exchanger | Adapter | `auth.adapter.out.sso.OAuth2TokenExchanger` | Exchange auth code with IdP (Google, Microsoft, Keycloak) |
| HTTP CAPTCHA Gateway | Adapter | `auth.adapter.out.http.HttpCaptchaGateway` | Alternative CAPTCHA verification via gateway pattern |
| HTTP SSO Gateway | Adapter | `auth.adapter.out.http.HttpSsoGateway` | Alternative SSO flow via gateway pattern |
| Domains Table | Table | `domains` | Multi-domain config — password policy FK, SSO config per domain |
| Users Table | Table | `users` | Core user table — MFA fields, password tracking fields, trusted_device_set_at (V10) |
| UserIdentities Table | Table | `user_identities` | SSO identity linking (provider + sub) |
| PasswordPolicies Table | Table | `password_policies` | Per-domain password rules |
| PasswordHistory Table | Table | `password_history` | Recent password hashes for reuse prevention |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: Thư viện TOTP nào phù hợp nhất cho Spring Boot + Kotlin? → `dev.samstevens.totp` (đã adopt)
- [x] Q2: Cách tổ chức two-phase login flow cho MFA trên Spring Boot 3.x? → Custom mfaToken JWT approach
- [x] Q3: Pattern nào cho JIT provisioning khi SSO login? → `OAuth2TokenExchanger` + domain event via `EventPublisher` port
- [x] Q4: Cách migrate từ HMAC-SHA256 sang RS256 mà không downtime? → Dual-key fallback (RS256 primary, HMAC legacy)
- [x] Q5: Passay có hỗ trợ dynamic rule composition per domain không? → Có, factory pattern + `ConcurrentHashMap` cache
- [x] Q6: CAPTCHA provider nào phù hợp nhất cho ERP context? → Pluggable interface, multiple adapters (Turnstile, ALTCHA, Noop)
- [x] Q7: Password history check — store plaintext hay hash? → Hash only (BCrypt), `matches()` each
- [x] Q8: Recovery codes nên implement thế nào? → SHA-256 hashed, Redis-backed list, single-use (remove after verify)

### 5.2 Assumptions cần verify

- [x] A1: Spring Boot 3.2 không hỗ trợ `@EnableMultiFactorAuthentication` → Confirmed (Spring Security 7+ only)
- [x] A2: `dev.samstevens.totp` vẫn actively maintained → GitHub repo 404, package still available on Maven Central 1.7.1
- [x] A3: Keycloak adapter deprecated từ v21+ → Confirmed (use Spring Security OAuth2 native)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Internet + open source đều đã được survey | ≥ 5 sources |
| Open source options | Đánh giá thư viện cho TOTP, password validation, CAPTCHA | ≥ 3 repos evaluated |
| Gap analysis | Xác định gaps giữa current system và target state | All critical gaps identified |
| Business analysis | Tất cả use cases được document đầy đủ | All 4 FRs covered with 6+ UCs |
| Technical spec | Đặc tả agent-ready — ERD, sequence, API spec, migration scripts | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
