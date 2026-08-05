# Pre-OpenSpec: auth-core-features

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (Feature Research — business_analysis.md)
> **Classification Evidence**: `AuthService.kt` → `auth/application` → `auth-service/src/main/kotlin/.../auth/application/AuthService.kt` (existing login/register flow, cần mở rộng MFA/SSO/RS256/PasswordPolicy)
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Mở rộng auth-service hiện có với 4 tính năng core authentication: Multi-Factor Authentication (OTP SMS/Email, TOTP, CAPTCHA), SSO/OAuth2 (Keycloak, Google, Microsoft), JWT RS256 với token introspection/session binding, và Password Policy per domain (complexity, history, expiry). Các tính năng này bổ sung vào flow login/register đã có sẵn.

| Metric | Giá trị |
|--------|---------|
| Số FR | 17 (URD: 14, Enriched: 3) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 1 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- End User: Người dùng hệ thống (nhân viên, quản lý) đăng nhập, quản lý MFA, đổi mật khẩu.
- System Administrator: Quản trị viên cấu hình password policy, SSO providers, force logout.
- System (Resource Server): Service bên ngoài cần validate token thông qua introspection endpoint.
- External IdP: Keycloak, Google, Microsoft — cung cấp identity thông qua OAuth2/OIDC.

## 2. Functional Requirements

### FR-001: Xác thực OTP SMS/Email [URD]
- **Actor**: End User
- **Action**: Hệ thống phải gửi mã OTP (6 chữ số) qua SMS hoặc Email khi user login và MFA enabled.
- **Validation**: OTP TTL = 5 phút, max 3 lần nhập sai, lưu trữ Redis.

### FR-002: Xác thực TOTP (Authenticator App) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ setup và verify TOTP (Google Authenticator, Authy) dùng dev.samstevens.totp.
- **Validation**: Window = 30 giây, drift tolerance ±1 step, secret phải AES-256 encrypted tại DB.

### FR-003: Bật/tắt MFA và chọn phương thức [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép user bật/tắt MFA và chọn phương thức (SMS, Email, TOTP).
- **Validation**: Khi tắt MFA cần xác minh OTP lần cuối. Khi bật TOTP cần confirm bằng code.

### FR-004: CAPTCHA cho login form [URD]
- **Actor**: End User
- **Action**: Hệ thống phải yêu cầu CAPTCHA khi user vượt quá N lần login sai (cấu hình per domain).
- **Validation**: Pluggable adapter pattern (Turnstile, hCaptcha, reCAPTCHA v3). Server-side verification.

### FR-005: Trusted Device (Skip MFA) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép đánh dấu thiết bị tin cậy để skip MFA trong 30 ngày.
- **Validation**: Cookie-based fingerprint hash, cấu hình TTL per domain.

### FR-006: OAuth2 SSO Login [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ đăng nhập qua OAuth2 (Google, Microsoft, Keycloak) dùng Spring Security native.
- **Validation**: Dùng `spring-boot-starter-oauth2-client`, issuer-uri config. KHÔNG dùng Keycloak adapter (deprecated).

### FR-007: JIT User Provisioning từ SSO [URD]
- **Actor**: System
- **Action**: Hệ thống phải tự động tạo UserEntity khi user đăng nhập SSO lần đầu (nếu auto-provision enabled cho domain).
- **Validation**: Dùng `sub` claim làm identity key (KHÔNG dùng email). Publish Kafka event `iam.user.sso_provisioned`.

### FR-008: Liên kết/gỡ SSO Identity [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép liên kết tài khoản hiện có với SSO provider, và gỡ liên kết.
- **Validation**: Lưu `user_identities` table. Gỡ liên kết chỉ khi user có password hoặc còn SSO identity khác.

### FR-009: Migrate JWT sang RS256 [URD]
- **Actor**: System
- **Action**: Hệ thống phải chuyển từ HMAC-SHA256 sang RS256 asymmetric signing cho JWT.
- **Validation**: RSA 2048-bit key pair. Private key chỉ tại auth-service. JWKS endpoint expose public key.

### FR-010: JWKS Endpoint [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải expose `/.well-known/jwks.json` để resource servers fetch public key tự động.
- **Validation**: Hỗ trợ multiple key IDs (`kid`), key rotation không downtime.

### FR-011: Token Introspection (RFC 7662) [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải cung cấp endpoint `/api/auth/introspect` để validate token server-side.
- **Validation**: Trả `active`, `sub`, `roles`, `permissions`, `exp`. Check jti blacklist + user status.

### FR-012: Force Logout (Session Revocation) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép admin revoke toàn bộ session của user ngay lập tức.
- **Validation**: Blacklist all active jti, delete all refresh tokens, publish revocation event.

### FR-013: Password Policy per Domain [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép cấu hình password policy riêng cho từng domain (complexity, history, expiry).
- **Validation**: Dùng Passay library. Lưu `password_policies` table. Default policy nếu chưa cấu hình.

### FR-014: Password History [URD]
- **Actor**: System
- **Action**: Hệ thống phải chặn reuse N passwords gần nhất khi đổi mật khẩu.
- **Validation**: Lưu `password_history` table. BCrypt.matches() kiểm tra. Prune entries cũ vượt historyCount.

## 3. Non-functional Requirements

- NFR-001: OTP/MFA verification latency ≤ 200ms (Redis-backed).
- NFR-002: RS256 signing/verification < 50ms per token.
- NFR-003: JWKS endpoint cache TTL = 24 giờ (resource server side).
- NFR-004: CAPTCHA verification timeout = 5 giây (max).

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (OTP) và FR-002 (TOTP) được tách riêng theo phương thức xác thực.

## 5. Enriched Domain Requirements

Max enriched: min(5, ceil(14 × 0.20)) = min(5, 3) = 3.

### Enriched FRs

### FR-015: Idempotency cho MFA verification [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotent cho `/api/auth/mfa/verify` — cùng mfaToken + code chỉ issue JWT 1 lần.
- **Validation**: Redis DELETE sau khi verify thành công, ngăn replay.

### FR-016: Audit logging cho MFA/SSO events [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi audit log cho mọi sự kiện MFA (setup, verify, failed) và SSO (login, link, unlink).
- **Validation**: Ghi vào `audit_log` table với action, entity_type, ip_address.

### FR-017: Timeout handling cho IdP call [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xử lý timeout khi gọi IdP token endpoint (exchange code → token).
- **Validation**: Timeout = 10 giây, trả lỗi `SSO_PROVIDER_TIMEOUT` nếu vượt quá.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | OTP storage, session binding, rate limiting | `spring-boot-starter-data-redis` |
| Kafka | User provisioning event | Topic: `iam.user.sso_provisioned` |
| Keycloak | Optional IdP | `issuer-uri` config |
| Google OAuth2 | SSO provider | Standard OIDC |
| Microsoft OAuth2 | SSO provider | Standard OIDC |
| Turnstile/hCaptcha | CAPTCHA verification | REST API call |

## 6. Assumptions

- Redis available tại `localhost:6379` cho dev environment.
- Kafka available tại `localhost:9092` cho dev environment.
- RSA key pair sẽ được generate offline và inject qua environment variables.
- CAPTCHA provider secret key được cung cấp qua environment variables.
- SSO providers (Google, Microsoft) sẽ được cấu hình sau trong `application.yml` — code chỉ cần support pluggable config.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-004: CAPTCHA "N lần login sai" — N chưa xác định cụ thể |
| Đầy đủ (Completeness) | 22/25 | FR-005: Trusted device fingerprint algorithm chưa chi tiết |
| Nhất quán (Consistency) | 25/25 | Không phát hiện mâu thuẫn |
| Kiểm thử được (Testability) | 18/25 | FR-006, FR-007: SSO flow khó test tự động (cần mock IdP) |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -2 | FR-004 | "sau N lần login sai" — N là giá trị nào? | Xác định default value (e.g., N=5) |
| 2 | Completeness | -3 | FR-005 | "Cookie-based fingerprint hash" — algorithm chưa rõ | Chỉ định SHA-256(userAgent + screenRes + ...) |
| 3 | Testability | -7 | FR-006, FR-007 | SSO callback flow phụ thuộc external IdP | Cung cấp mock IdP profile cho testing |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Missing | 🟡 | TOTP secret encryption key management chưa rõ (dùng gì để encrypt AES-256?) | FR-002 | Dùng Spring Cloud Config encryption hoặc Jasypt |
| 2 | Risk | 🟡 | RS256 migration breaking change — tất cả existing JWT sẽ invalid | FR-009 | Plan migration window, support dual algorithm temporarily |

> Không phát hiện vấn đề critical (🔴).

## 9. Open Questions

- Q1: Khi migrate sang RS256, có cần hỗ trợ dual-algorithm (HMAC + RS256) trong giai đoạn chuyển đổi không? Nếu có, cần thêm logic detect algorithm từ JWT header.

> Nếu không cần dual → đơn giản hóa, chấp nhận invalidate tất cả existing tokens.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Authorization — MFA, SSO, Token Management, Password Policy

### 10.2 Flow Type
Non-Financial (xác thực OTP, không liên quan giao dịch tài chính)

### 10.3 Candidate Services
- `auth-service`: Primary service — chứa AuthService, JwtService, SecurityConfig, UserEntity (keyword: login, auth, jwt, password, token)
- `base-core` (partial): base-security-starter, base-model — cung cấp MfaFilter, TreeEntity (keyword: security, filter)

### Detection Evidence
- Keyword: `login`, `auth`, `jwt`, `password` → Module: `auth/application` → File: `AuthService.kt`
- Keyword: `SecurityConfig`, `JwtAuthFilter` → Module: `shared/config`, `shared/security` → File: `SecurityConfig.kt`, `JwtAuthFilter.kt`
- Keyword: `UserEntity`, `RefreshTokenEntity` → Module: `rbac/adapter/out/persistence/entity` → File: `UserEntity.kt`
- Keyword: `TokenBlacklistRepository` → Module: `rbac/adapter/out/persistence/repository` → File: `Repositories.kt`

### 10.4 External Integrations
- Redis: OTP storage, session management, rate limiting
- Kafka: User provisioning event (`iam.user.sso_provisioned`)
- OAuth2 IdPs: Keycloak, Google, Microsoft (OIDC protocol)
- CAPTCHA: Turnstile/hCaptcha/reCAPTCHA (REST API)

### 10.5 Required Modules
- `auth/application`: MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService (extend)
- `auth/adapter/in/web`: MfaController, SsoController, TokenController (new controllers)
- `auth/adapter/out/persistence/entity`: UserIdentityEntity, PasswordPolicyEntity, PasswordHistoryEntity (new entities)
- `shared/config`: SecurityConfig (modify — add OAuth2, JWKS), SecurityProperties (modify — add MFA/SSO props)
- `shared/security`: CaptchaVerifier interface + adapters (new)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Gửi username + password | Validate credentials |
| 2 | System | Check mfaEnabled | Nếu true → trả MFA_REQUIRED + mfaToken |
| 3 | End User | Nhận OTP / Mở Authenticator App | Gửi OTP qua channel hoặc skip nếu TOTP |
| 4 | End User | Gửi OTP + mfaToken | Verify OTP (Redis check) |
| 5 | System | Verify thành công | Issue JWT (RS256) access + refresh token |
| 6 | System | — | Ghi audit log |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 (Basic Flow 5-7) | MfaService | OtpService (NEW) | Mapped |
| FR-002 | UC-001 (Basic Flow 5-7) | MfaService | TotpService (NEW) | Mapped |
| FR-003 | UC-001 (Exception E3) | MfaController | MfaController (NEW) | Mapped |
| FR-004 | UC-001 (Exception E4) | CaptchaVerifier | CaptchaVerifier (NEW) | Mapped |
| FR-005 | UC-001 (BR-004) | MfaService | UserEntity (MODIFY) | Mapped |
| FR-006 | UC-002 (Basic Flow 1-4) | SsoAdapter | SsoAdapter (MODIFY) | Mapped |
| FR-007 | UC-002 (Basic Flow 7) | SsoAdapter | SsoAdapter + Kafka (MODIFY) | Mapped |
| FR-008 | UC-002 (Related) | SsoController | SsoController (NEW) | Mapped |
| FR-009 | UC-003 (Related) | JwtService | JwtService (MODIFY) | Mapped |
| FR-010 | UC-003 (BR-010) | JwksController | JwksController (NEW) | Mapped |
| FR-011 | UC-003 (Basic Flow 1-3) | TokenController | TokenController (NEW) | Mapped |
| FR-012 | UC-003 (Force Logout) | AuthService | AuthService (MODIFY) | Mapped |
| FR-013 | UC-004 (Basic Flow 1-4) | PasswordPolicyService | PasswordPolicyService (MODIFY) | Mapped |
| FR-014 | UC-004 (Enforcement 4-5) | PasswordPolicyService | PasswordHistoryEntity (NEW) | Mapped |
| FR-015 | (Enriched) | MfaService | Redis DEL | Mapped |
| FR-016 | (Enriched) | AuditLogAspect | audit_log table | Mapped |
| FR-017 | (Enriched) | SsoAdapter | RestTemplate timeout | Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
Feature này là EXTEND lớn — ảnh hưởng 6+ classes hiện có và tạo mới 10+ classes. Rủi ro chính là RS256 migration (breaking change) và OAuth2 config complexity. Recommend chia thành 2 sprint: Sprint 1 (MFA + Password Policy), Sprint 2 (SSO + RS256).

### Related Features / Precedents
- `erp-iam-system` (archived) — đã scaffold overall IAM architecture, tasks.md có plan cho các feature này nhưng ở mức stub.
- Existing `AuthService.kt` đã có login/register/refresh flow — MFA sẽ EXTEND login flow, không rewrite.

### Integration Notes
- **Redis**: Cần `spring-boot-starter-data-redis` — hiện chưa có trong `build.gradle.kts`. Cần thêm dependency.
- **OAuth2**: Cần `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server` — hiện chưa có.
- **Kafka**: Cần `spring-kafka` — hiện chưa có. `SsoAdapter.kt` stub đã inject `KafkaTemplate`.
- **TOTP Library**: Cần `dev.samstevens.totp:totp:1.7.1`.
- **Passay**: Cần `org.passay:passay:1.6.4`.

### Suggested Approach
1. **Thêm dependencies** vào `build.gradle.kts` trước.
2. **Migration V2**: Tạo SQL migration cho `user_identities`, `password_policies`, `password_history` + ALTER `users`.
3. **Bottom-up**: Entity → Repository → Service → Controller.
4. **JwtService modify**: Đổi signing key type từ `SecretKey` sang `KeyPair` (RSA). Expose JWKS.
5. **AuthService modify**: Thêm MFA checkpoint sau password validation.
6. **New services**: `MfaService`, `TotpService`, `OtpService`, `CaptchaVerifier`.

### Context from Confluence Images
N/A — source là file-based business analysis.
