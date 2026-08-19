# Research Brief: Auth Core Features

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Auth Core Features (FR-001 → FR-004) |
| **Ngày tạo** | 2026-08-19 |
| **Input source** | name |
| **Input content** | auth-core-features — MFA, SSO/OAuth2, Token RS256 + Introspection, Password Policy per Domain |
| **Người yêu cầu** | Pipeline (auto) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hệ thống ERP IAM hiện tại chỉ hỗ trợ xác thực đơn lớp (username/password) với JWT ký bằng HMAC-SHA256 (symmetric key). Điều này tạo ra nhiều rủi ro bảo mật:
- **Không có MFA**: Nếu mật khẩu bị lộ, kẻ tấn công có toàn quyền truy cập.
- **Không có SSO**: Người dùng doanh nghiệp phải quản lý thêm một bộ credentials riêng.
- **Symmetric JWT**: Tất cả resource servers cần biết secret key → vi phạm principle of least privilege.
- **Password policy cứng nhắc**: Một chính sách duy nhất cho mọi domain, domain "payment" (tài chính) cùng quy tắc với "booking" (đặt phòng).

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Triển khai MFA (OTP SMS/Email + TOTP Authenticator App + CAPTCHA) cho login và sensitive operations
- [x] Objective 2: Tích hợp SSO/OAuth2 với Google, Microsoft, Keycloak — hỗ trợ JIT provisioning
- [x] Objective 3: Migrate JWT signing từ HMAC-SHA256 sang RS256 asymmetric, expose JWKS endpoint
- [x] Objective 4: Xây dựng password policy per domain với Passay library, bao gồm password history và expiry

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| MFA flow design (OTP + TOTP + CAPTCHA) | Biometric/WebAuthn/Passkey (future phase) |
| OAuth2/OIDC integration pattern (Google, Microsoft, Keycloak) | SMS provider integration details (Twilio, etc.) — adapter pattern |
| RS256 JWT signing + JWKS endpoint | Email sending infrastructure — event-driven |
| Token introspection (RFC 7662) | Key management service (KMS) integration |
| Password policy per domain (Passay) | Admin UI for password policy management |
| Password history + expiry checking | Biometric authentication |
| Session binding + force logout | Real-time notification push |
| Pluggable CAPTCHA adapter | CAPTCHA analytics dashboard |

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
- `hCaptcha Turnstile reCAPTCHA v3 Spring Boot`
- `Redis OTP storage TTL rate limiting`
- `TOTP secret AES-256-GCM encryption at rest`

### 3.3 Domain-Specific Terms

- `MFA (Multi-Factor Authentication)`: Xác thực đa yếu tố — yêu cầu ≥2 factors (something you know + something you have)
- `TOTP (Time-based One-Time Password)`: OTP dựa trên thời gian, RFC 6238, dùng Authenticator App
- `OTP (One-Time Password)`: Mã xác thực dùng 1 lần, gửi qua SMS/Email
- `JIT Provisioning (Just-In-Time)`: Tự động tạo tài khoản local khi lần đầu đăng nhập qua SSO
- `JWKS (JSON Web Key Set)`: Endpoint expose public keys cho resource servers verify JWT
- `Token Introspection (RFC 7662)`: Endpoint cho resource servers validate token mà không cần parse locally
- `Passay`: Java library cho password validation với composable rules

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Spring Boot 3 MFA TOTP implementation best practices"` | General | High |
| 2 | `"TOTP OTP Java library open source GitHub"` | Open Source | High |
| 3 | `"Spring Security OAuth2 Keycloak JIT provisioning"` | Architecture | High |
| 4 | `"JWT RS256 HMAC migration JWKS endpoint"` | Architecture | Medium |
| 5 | `"Passay dynamic password validation per tenant"` | Open Source | Medium |
| 6 | `"CAPTCHA pluggable adapter Turnstile hCaptcha Spring Boot"` | Comparison | Medium |
| 7 | `"token introspection RFC 7662 Spring Boot implementation"` | Architecture | Medium |
| 8 | `"password history table schema best practices"` | Architecture | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JWT Service (HMAC-SHA256) | `auth.application.JwtService` | High | Đã hỗ trợ RS256 (primary) + HMAC fallback (migration). Có `generateMfaToken()`, `getJwks()` |
| Auth Service (login/register) | `auth.application.AuthService` | High | Đã integrate MFA checkpoint, password expiry check, CAPTCHA check |
| MFA Service | `auth.application.MfaService` | High | Đã implement — orchestrate OTP/TOTP/trusted device |
| OTP Service | `auth.application.OtpService` | High | Đã implement — Redis-backed OTP generation + verification |
| TOTP Service | `auth.application.TotpService` | High | Đã implement — `dev.samstevens.totp`, AES-256-GCM encryption |
| CAPTCHA Verifier | `auth.application.CaptchaVerifier` | High | Đã implement — pluggable interface: Turnstile, hCaptcha, reCAPTCHA, ALTCHA, Noop |
| SSO Adapter | `auth.application.SsoAdapter` | High | Đã implement — OAuth2 callback, JIT provisioning, identity link/unlink |
| Password Policy Service | `auth.application.PasswordPolicyService` | High | Đã implement — Passay dynamic rules, history check, domain-scoped |
| Security Config | `shared.config.SecurityConfig` | Medium | Filter chain config — JWT auth filter, rate limit filter |
| MFA Rate Limit Service | `auth.application.MfaRateLimitService` | Medium | Redis-backed rate limiting for MFA attempts |
| Login Session Service | `auth.application.LoginSessionService` | Medium | Session management, max sessions per user |
| JWT Auth Filter | `shared.security.JwtAuthFilter` | Medium | Token validation filter with jti blacklist check |
| RBAC Engine | `rbac.application.RbacEngine` | Medium | Provides roles/permissions for JWT claims |
| Audit Log Service | `shared.audit.AuditLogService` | Low | Immutable audit trail for security events |

### 4.2 Existing Code Patterns

- **Architecture**: Hexagonal (port/adapter) — `adapter/in/web`, `adapter/out/persistence`, `application`, `domain`
- **Entity Base**: `SnowflakePersistentAuditableEntity` (Snowflake ID + audit fields + soft-delete via `active`)
- **Config Pattern**: `@ConfigurationProperties(prefix = "app.security")` with nested classes
- **Exception**: RFC 7807 ProblemDetail via `GlobalExceptionHandler`
- **Token Lifecycle**: JWT (access + refresh), refresh token rotation, jti blacklist
- **CQRS Pattern**: Command/Handler pattern (`LoginCommand` → `LoginHandler`, `RegisterCommand` → `RegisterHandler`)
- **Event Sourcing**: `eventsourcing-utils` dependency present (Phase 2)
- **I18n**: Database-backed `MessageSource` via `DatabaseMessageSource`
- **Rate Limiting**: Multi-dimensional Redis-backed (IP, username, device)
- **Cipher/E2EE**: `CipherProperties` with AES-GCM encryption, X25519 key exchange

### 4.3 Tech Stack Constraints

- Language: Kotlin 1.9+
- Framework: Spring Boot 3.2+ (Spring Security 6+)
- Database: PostgreSQL 17, Flyway migrations (V1..V9)
- JWT: `io.jsonwebtoken:jjwt-api` 0.12+
- TOTP: `dev.samstevens.totp:totp` 1.7.1
- Password: `org.passay:passay` 1.6.4, BCrypt (strength 12)
- Cache: Caffeine L1 (in-memory)
- Redis: Spring Data Redis (OTP, rate limiting, session)
- OAuth2: `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server`
- E2EE: Google Tink 1.15.0
- Build: Gradle Kotlin DSL
- Base: `com.ntt:base-web-starter`, `base-data-starter`, `common-log`

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| RBAC Engine | Module | `rbac.application.RbacEngine` | `getUserRoles()` + `getEffectivePermissions()` → embedded in JWT claims |
| PBAC Engine | Module | `pbac.application.PolicyEvaluator` | Attribute-based check, separate from MFA |
| Redis | Infrastructure | Spring Data Redis | OTP storage, MFA rate limits, login rate limits, session management |
| Kafka (planned) | Event Bus | `spring-kafka` (compileOnly) | `iam.user.sso_provisioned` topic — notify downstream services |
| OAuth2 Token Exchanger | Adapter | `auth.adapter.out.sso.OAuth2TokenExchanger` | Exchange auth code with IdP (Google, Microsoft, Keycloak) |
| Domains Table | Table | `domains` | Multi-domain config — password policy FK, SSO config per domain |
| Users Table | Table | `users` | Core user table — MFA fields, password tracking fields already added (V2 migration) |
| UserIdentities Table | Table | `user_identities` | SSO identity linking (provider + sub) — already created (V2 migration) |
| PasswordPolicies Table | Table | `password_policies` | Per-domain password rules — already created (V2 migration) |
| PasswordHistory Table | Table | `password_history` | Recent password hashes for reuse prevention — already created (V2 migration) |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: Thư viện TOTP nào phù hợp nhất cho Spring Boot + Kotlin? → `dev.samstevens.totp` (đã adopt)
- [x] Q2: Cách tổ chức two-phase login flow cho MFA trên Spring Boot 3.x (chưa có Spring Security 7 native MFA)? → Custom mfaToken JWT approach
- [x] Q3: Pattern nào cho JIT provisioning khi SSO login? → `OidcUserService` pattern with manual DB check
- [x] Q4: Cách migrate từ HMAC-SHA256 sang RS256 mà không downtime? → Dual-key fallback (RS256 primary, HMAC legacy)
- [x] Q5: Passay có hỗ trợ dynamic rule composition per domain không? → Có, factory pattern + `ConcurrentHashMap` cache
- [x] Q6: CAPTCHA provider nào phù hợp nhất cho ERP context? → Pluggable interface, default Cloudflare Turnstile
- [x] Q7: Password history check — store plaintext hay hash? → Hash only (BCrypt), matches() each

### 5.2 Assumptions cần verify

- [x] A1: Spring Boot 3.2 không hỗ trợ `@EnableMultiFactorAuthentication` → Confirmed (Spring Security 7+ only)
- [x] A2: `dev.samstevens.totp` vẫn actively maintained → Needs verification (GitHub 404 — package still available on Maven Central)
- [x] A3: Keycloak adapter deprecated từ v21+ → Confirmed (use Spring Security OAuth2 native)

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Internet + open source đều đã được survey | ≥ 5 sources |
| Open source options | Đánh giá thư viện cho TOTP, password validation, CAPTCHA | ≥ 3 repos evaluated |
| Gap analysis | Xác định gaps giữa current system và target state | All critical gaps identified |
| Business analysis | Tất cả use cases được document đầy đủ | All 4 FRs covered |
| Technical spec | Đặc tả agent-ready — ERD, sequence, API spec, migration scripts | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
