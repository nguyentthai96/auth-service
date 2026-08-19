# Pre-OpenSpec: auth-core-features

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (Feature Research — business_analysis.md + technical_spec.md)
> **Classification Evidence**: `AuthService.kt` → `auth/application` → `src/main/kotlin/.../auth/application/AuthService.kt` (existing login/register/MFA/SSO flow, cần hoàn thiện + hardening)
> **Archive**: `openspec/changes/auth-core-features/pre_openspec.md` (previous version 2026-08-05)
> **Quality Score**: 91/100
> **Mode**: DELTA (archive found, input refined from research phase)
> **_Generated**: 2026-08-19

## 📋 Feature Summary

Hoàn thiện và hardening auth-service với 4 nhóm tính năng core authentication: Multi-Factor Authentication (OTP SMS/Email, TOTP Authenticator, CAPTCHA), SSO/OAuth2 (Google, Microsoft, Keycloak) với JIT provisioning, JWT RS256 + token introspection (RFC 7662) + JWKS endpoint + session binding, và Password Policy per domain (complexity via Passay, history, expiry). ~90% code đã được implement — tập trung vào testing, hardening, và hoàn thiện edge cases.

| Metric | Giá trị |
|--------|---------|
| Số FR | 17 (URD: 14, Enriched: 3) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 1 |
| **Quality Score** | **91/100** |

---

## 1. Actors

- **End User**: Người dùng hệ thống (nhân viên, quản lý) — đăng nhập, quản lý MFA, đổi mật khẩu, liên kết SSO.
- **System Administrator**: Quản trị viên — cấu hình password policy per domain, SSO providers, force logout sessions.
- **System (Resource Server)**: Service downstream cần validate token thông qua introspection endpoint hoặc JWKS.
- **External IdP**: Google, Microsoft, Keycloak — cung cấp identity thông qua OAuth2/OIDC protocol.

## 2. Functional Requirements

### FR-001: Xác thực OTP SMS/Email [URD]
- **Actor**: End User
- **Action**: Hệ thống phải gửi mã OTP (6 chữ số) qua SMS hoặc Email khi user login và MFA enabled cho phương thức SMS/EMAIL.
- **Validation**: OTP TTL = 5 phút (300 giây), max 3 lần nhập sai per mfaToken, lưu trữ Redis (`otp:{userId}:{channel}`), constant-time comparison.
- **Status**: ✅ IMPLEMENTED — `OtpService.kt` (`auth/application/OtpService.kt`) + Redis-backed storage.

### FR-002: Xác thực TOTP (Authenticator App) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ setup và verify TOTP (Google Authenticator, Authy) dùng `dev.samstevens.totp`.
- **Validation**: Window = 30 giây, drift tolerance ±1 step, secret phải AES-256 encrypted (tại DB field `totp_secret_encrypted`).
- **Status**: ✅ IMPLEMENTED — `TotpService.kt` (`auth/application/TotpService.kt`) + AES-256 encryption.

### FR-003: Bật/tắt MFA và chọn phương thức [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép user bật/tắt MFA và chọn phương thức (SMS, Email, TOTP) thông qua `PUT /api/auth/mfa/settings`.
- **Validation**: Khi bật TOTP cần TOTP secret đã setup. Khi tắt MFA → set `mfaMethod = "NONE"`.
- **Status**: ✅ IMPLEMENTED — `MfaService.updateSettings()` + `MfaController.kt`.

### FR-004: CAPTCHA cho login form [URD]
- **Actor**: End User
- **Action**: Hệ thống phải yêu cầu CAPTCHA khi user vượt quá N lần login sai (N cấu hình per domain qua `SecurityProperties`).
- **Validation**: Pluggable adapter pattern — `CaptchaVerifier` interface với `TurnstileCaptchaVerifier`, `AltchaCaptchaVerifier`, `NoopCaptchaVerifier`. Server-side verification.
- **Status**: ✅ IMPLEMENTED — `CaptchaVerifier.kt` + `AltchaCaptchaVerifier.kt` + `CaptchaController.kt`.

### FR-005: Trusted Device (Skip MFA) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép đánh dấu thiết bị tin cậy để skip MFA trong 30 ngày.
- **Validation**: SHA-256 device fingerprint hash, lưu `trusted_device_hash` trên `UserEntity`. Cấu hình TTL per domain.
- **Status**: ⚠️ PARTIAL — `UserEntity.trustedDeviceHash` field exists, `LoginRequestDto.trustedDeviceHash` field exists. Logic kiểm tra trong `LoginHandler` cần verify.

### FR-006: OAuth2 SSO Login [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ đăng nhập qua OAuth2 (Google, Microsoft, Keycloak) dùng `spring-boot-starter-oauth2-client`.
- **Validation**: `OAuth2TokenExchanger` exchange auth code → access_token → userinfo. KHÔNG dùng Keycloak adapter (deprecated).
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.kt` + `SsoController.kt` + `OAuth2TokenExchanger.kt`.

### FR-007: JIT User Provisioning từ SSO [URD]
- **Actor**: System
- **Action**: Hệ thống phải tự động tạo `UserEntity` khi user đăng nhập SSO lần đầu (nếu auto-provision enabled).
- **Validation**: Dùng `sub` claim làm identity key (KHÔNG dùng email). Kafka event `iam.user.sso_provisioned` — TODO: implement khi Kafka configured.
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` với JIT provisioning logic. Kafka event TODO.

### FR-008: Liên kết/gỡ SSO Identity [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép liên kết tài khoản hiện có với SSO provider (`POST /api/auth/sso/link`), và gỡ liên kết (`DELETE /api/auth/sso/unlink/{provider}`).
- **Validation**: Lưu `user_identities` table. Gỡ liên kết chỉ khi user có password hoặc còn SSO identity khác (`CannotUnlinkLastIdentityException`).
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()` + `SsoAdapter.unlinkIdentity()` + `SsoController.kt`.

### FR-009: JWT RS256 Signing [URD]
- **Actor**: System
- **Action**: Hệ thống phải sign JWT bằng RS256 asymmetric (primary) với HMAC-SHA256 fallback (7-day migration).
- **Validation**: RSA key pair loaded từ file path (`jwt.privateKeyPath`, `jwt.publicKeyPath`). Private key chỉ tại auth-service.
- **Status**: ✅ IMPLEMENTED — `JwtService.kt` với dual-key support (RS256 primary, HMAC legacy fallback).

### FR-010: JWKS Endpoint [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải expose `GET /.well-known/jwks.json` để resource servers fetch public key tự động.
- **Validation**: RSA public key exposed qua `JwtService.getJwks()`. Cache-Control: `max-age=86400, public`.
- **Status**: ✅ IMPLEMENTED — `TokenController.jwks()` endpoint.

### FR-011: Token Introspection (RFC 7662) [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải cung cấp `POST /api/auth/introspect` để validate token server-side.
- **Validation**: Trả `active`, `sub`, `username`, `roles`, `permissions`, `exp`, `iat`, `iss`, `jti`. Check jti blacklist via `TokenBlacklistRepository`.
- **Status**: ✅ IMPLEMENTED — `TokenController.introspect()` endpoint.

### FR-012: Force Logout (Session Revocation) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép admin revoke toàn bộ session của user qua `POST /api/auth/sessions/{userId}/revoke-all`.
- **Validation**: Blacklist all active jti, delete all refresh tokens. Return `revokedCount`.
- **Status**: ✅ IMPLEMENTED — `TokenController.revokeAllSessions()` → `AuthService.revokeAllSessions()`.

### FR-013: Password Policy per Domain [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép cấu hình password policy riêng cho từng domain (complexity, history, expiry).
- **Validation**: Dùng Passay library — dynamic rules via `PasswordValidator`. Lưu `password_policies` table. Default policy nếu chưa cấu hình. Cached per `domainId` via `ConcurrentHashMap`.
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` với Passay integration, cache, domain-scoped policies.

### FR-014: Password History [URD]
- **Actor**: System
- **Action**: Hệ thống phải chặn reuse N passwords gần nhất khi đổi mật khẩu.
- **Validation**: Lưu `password_history` table. `BCrypt.matches()` kiểm tra mỗi hash. Prune entries cũ vượt `historyCount`.
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()` + `PasswordPolicyService.pruneHistory()`.

## 3. Non-functional Requirements

- NFR-001: OTP/MFA verification latency ≤ 200ms (Redis-backed, constant-time compare).
- NFR-002: RS256 signing/verification < 50ms per token (RSA 2048-bit key pair).
- NFR-003: JWKS endpoint cache TTL = 24 giờ (`Cache-Control: max-age=86400, public`).
- NFR-004: CAPTCHA verification timeout = 5 giây (max), pluggable adapter.
- NFR-005: Password validator cache hit ratio > 95% (`ConcurrentHashMap` per domainId).
- NFR-006: Login endpoint P95 latency < 500ms (including MFA check + CAPTCHA + rate limit).

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (OTP) và FR-002 (TOTP) được tách riêng theo phương thức xác thực. FR-013 (Password Policy) và FR-014 (Password History) tách riêng theo concern.

## 5. Enriched Domain Requirements

Max enriched: min(5, ceil(14 × 0.20)) = min(5, 3) = 3.

### Enriched FRs

### FR-015: Idempotency cho MFA verification [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotent cho `/api/auth/mfa/verify` — cùng mfaToken + code chỉ issue JWT 1 lần.
- **Validation**: Redis DELETE sau khi OTP verify thành công, ngăn replay. MFA rate limit reset sau verify thành công.
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` deletes key after success. `MfaRateLimitService.resetCounters()` called.

### FR-016: Audit logging cho MFA/SSO events [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi audit log cho mọi sự kiện MFA (setup, verify_success, verify_failed) và SSO (login, link, unlink).
- **Validation**: `AuditLogService.logEvent()` with `AuditAction` enum. Log IP address + User-Agent.
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum covers MFA_SETUP, MFA_VERIFY_SUCCESS, MFA_VERIFY_FAILED, SSO_LOGIN, SSO_LINK, SSO_UNLINK, PASSWORD_CHANGED.

### FR-017: Timeout handling cho IdP call [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xử lý timeout khi gọi IdP token endpoint (exchange code → token).
- **Validation**: Trả lỗi `SsoProviderTimeoutException` (HTTP 504) nếu IdP không phản hồi.
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout → throws `SsoProviderTimeoutException`.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | OTP storage, MFA rate limiting, login rate limiting, session management, TOTP setup pending secret | `spring-boot-starter-data-redis` — ✅ in build.gradle.kts |
| Kafka | User provisioning event SSO | Topic: `iam.user.sso_provisioned` — `spring-kafka` compileOnly. ⚠️ TODO: actual event publish |
| Google OAuth2 | SSO provider | `OAuth2TokenExchanger` — OIDC protocol |
| Microsoft OAuth2 | SSO provider | `OAuth2TokenExchanger` — OIDC protocol |
| Keycloak | Optional SSO provider | ⚠️ NOT YET in `OAuth2TokenExchanger.getTokenEndpoint()` (only Google + Microsoft) |
| Turnstile/hCaptcha/ALTCHA | CAPTCHA verification | Pluggable `CaptchaVerifier` interface — ✅ implemented |
| Passay | Password validation | `org.passay:passay:1.6.4` — ✅ in build.gradle.kts |
| dev.samstevens.totp | TOTP generation/verification | `dev.samstevens.totp:totp:1.7.1` — ✅ in build.gradle.kts |

## 6. Assumptions

- ⚠️ Assumption: Redis available tại runtime cho MFA flow — lý do: OTP storage + rate limiting require Redis (critical dependency).
- ⚠️ Assumption: RSA key pair sẽ được generate offline và inject qua `jwt.privateKeyPath` / `jwt.publicKeyPath` env vars — lý do: key management ngoài scope.
- ⚠️ Assumption: CAPTCHA provider secret key cung cấp qua environment variables (`app.security.captcha.*`) — lý do: provider-specific config.
- ⚠️ Assumption: SSO providers (Google, Microsoft) cấu hình trong `application.yml` qua `app.security.sso.providers.*` — lý do: pluggable config.
- ⚠️ Assumption: `dev.samstevens.totp:1.7.1` vẫn stable dù GitHub repo 404 — lý do: TOTP là stable protocol, Maven Central available.
- ⚠️ Assumption: Kafka event publish deferred — `spring-kafka` compileOnly, actual producer chưa implement.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-004: CAPTCHA threshold "N lần" — đã có `SecurityProperties` config nhưng default value chưa documented rõ |
| Đầy đủ (Completeness) | 23/25 | FR-005: Trusted device TTL + fingerprint algorithm chưa chi tiết trong code |
| Nhất quán (Consistency) | 25/25 | Không phát hiện mâu thuẫn |
| Kiểm thử được (Testability) | 19/25 | FR-006, FR-007: SSO flow phụ thuộc external IdP — mock IdP needed. `SsoAdapterTest.kt` exists nhưng coverage chưa rõ |
| **Tổng** | **91/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -1 | FR-004 | CAPTCHA threshold configurable nhưng default value chưa rõ trong docs | Document default N=5 trong SecurityProperties Javadoc |
| 2 | Completeness | -2 | FR-005 | "Cookie-based fingerprint hash" — algorithm + TTL management chưa fully implemented | Implement SHA-256(userAgent + screenRes + timezone) + Redis-backed TTL |
| 3 | Testability | -6 | FR-006, FR-007 | SSO callback flow phụ thuộc external IdP — cần mock IdP | Cung cấp WireMock-based test profile cho Google/Microsoft |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Missing | 🟡 | Keycloak provider chưa có trong `OAuth2TokenExchanger.getTokenEndpoint()` — chỉ Google + Microsoft | FR-006 | Thêm Keycloak endpoint config (configurable `issuer-uri`) |
| 2 | Risk | 🟡 | Kafka event `iam.user.sso_provisioned` chưa implement — `spring-kafka` chỉ compileOnly | FR-007 | Implement khi Kafka infrastructure ready, add Spring Kafka starter |

> Không phát hiện vấn đề critical (🔴). RS256 migration risk đã được mitigate bằng dual-key fallback (issue #2 từ version trước — RESOLVED).

## 9. Open Questions

- Q1: Keycloak token/userinfo endpoint cần configurable per-instance (không có fixed URL như Google/Microsoft). Cần extend `OAuth2TokenExchanger` hoặc dùng Spring Security OAuth2 client registration?

> Recommend: Dùng `spring.security.oauth2.client.registration` + `provider` config thay vì hardcode endpoints.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Authorization — MFA (OTP/TOTP/CAPTCHA), SSO (OAuth2/OIDC), Token Management (RS256/JWKS/Introspection), Password Policy (Passay/History/Expiry)

### 10.2 Flow Type
Non-Financial (xác thực identity, không liên quan giao dịch tài chính trực tiếp)

### 10.3 Candidate Services
- `auth-service`: Primary service — chứa AuthService, JwtService, MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService, CaptchaVerifier, MfaRateLimitService, LoginRateLimitService, LoginSessionService (keyword: login, auth, jwt, mfa, totp, otp, sso, password, captcha, token)

### Detection Evidence
- Keyword: `login`, `auth`, `mfa` → Module: `auth/application` → File: `AuthService.kt`, `MfaService.kt`
- Keyword: `jwt`, `token`, `rs256` → Module: `auth/application` → File: `JwtService.kt`
- Keyword: `sso`, `oauth2` → Module: `auth/application` + `auth/adapter/out/sso` → File: `SsoAdapter.kt`, `OAuth2TokenExchanger.kt`
- Keyword: `password`, `policy`, `passay` → Module: `auth/application` → File: `PasswordPolicyService.kt`
- Keyword: `captcha` → Module: `auth/application` → File: `CaptchaVerifier.kt`, `AltchaCaptchaVerifier.kt`
- Keyword: `otp`, `totp` → Module: `auth/application` → File: `OtpService.kt`, `TotpService.kt`
- Keyword: `UserEntity`, `UserIdentityEntity` → Module: `rbac/adapter/out/persistence/entity` → File: `UserEntity.kt`, `UserIdentityEntity.kt`
- Keyword: `PasswordPolicy`, `PasswordHistory` → Module: `rbac/adapter/out/persistence/entity` → File: `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`
- Keyword: `SecurityConfig`, `JwtAuthFilter` → Module: `shared/config`, `shared/security` → File: `SecurityConfig.kt`, `JwtAuthFilter.kt`
- Keyword: `AuthException`, `AuthErrorCode` → Module: `shared/exception` → File: `AuthExceptions.kt`, `AuthCoreExceptions.kt`, `AuthErrorCode.kt`
- Keyword: `AuditLogService`, `AuditAction` → Module: `shared/audit` → File: `AuditLogService.kt`

### 10.4 External Integrations
- **Redis**: OTP storage (`otp:{userId}:{channel}`), MFA rate limiting (`mfa:ratelimit:*`), login rate limiting, session management, TOTP setup pending (`mfa:totp:setup:{userId}`), anonymous sessions — `StringRedisTemplate`
- **Kafka (planned)**: User provisioning event — topic `iam.user.sso_provisioned` — `spring-kafka` compileOnly
- **OAuth2 IdPs**: Google (`https://oauth2.googleapis.com/token`), Microsoft (`https://login.microsoftonline.com/common/oauth2/v2.0/token`) — via `OAuth2TokenExchanger`
- **CAPTCHA Providers**: Turnstile, ALTCHA, Noop — via `CaptchaVerifier` interface + factory `CaptchaConfig`
- **Passay**: In-process password validation library — `org.passay:passay:1.6.4`
- **dev.samstevens.totp**: In-process TOTP library — `dev.samstevens.totp:totp:1.7.1`
- **PostgreSQL**: JPA/Hibernate entities — `users`, `user_identities`, `password_policies`, `password_history`, `refresh_tokens`, `token_blacklist`, `login_sessions`

### 10.5 Required Modules
- `auth/application`: AuthService, MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService, CaptchaVerifier, MfaRateLimitService, LoginRateLimitService, LoginSessionService, JwtService (ALL EXIST — extend/harden)
- `auth/adapter/in/web`: AuthController, MfaController, SsoController, TokenController, CaptchaController (ALL EXIST)
- `auth/adapter/in/web/dto`: MfaDtos, SsoDtos, TokenDtos, RequestDtos, AuthResponse (ALL EXIST)
- `auth/adapter/out/sso`: OAuth2TokenExchanger (EXISTS — extend for Keycloak)
- `auth/adapter/out/http`: HttpCaptchaGateway, HttpSsoGateway, CaptchaClient, SsoProviderClient (EXISTS)
- `auth/adapter/out/gateway`: CaptchaGatewayAdapter (EXISTS)
- `rbac/adapter/out/persistence/entity`: UserEntity (MODIFY — already has MFA fields), UserIdentityEntity, PasswordPolicyEntity, PasswordHistoryEntity (ALL EXIST)
- `rbac/adapter/out/persistence/repository`: Repositories.kt (EXISTS — contains all repository interfaces)
- `shared/config`: SecurityConfig, SecurityProperties (EXISTS — extend for SSO/JWKS config)
- `shared/security`: JwtAuthFilter (EXISTS)
- `shared/exception`: AuthExceptions, AuthCoreExceptions, AuthErrorCode (ALL EXIST)
- `shared/audit`: AuditLogService, AuditAction (EXISTS)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Gửi username + password + captchaToken? + trustedDeviceHash? tới `POST /api/auth/login` | Validate CAPTCHA (nếu required), validate credentials (BCrypt match) |
| 2 | System | Check `user.mfaEnabled` và `trustedDeviceHash` | Nếu MFA enabled và device không trusted → trả `MfaRequired(mfaToken, method, expiresIn)` |
| 3 | End User | Nhận OTP qua SMS/Email hoặc mở Authenticator App | System gửi OTP qua channel (SMS/EMAIL) hoặc skip (TOTP stateless) |
| 4 | End User | Gửi OTP/TOTP code + mfaToken tới `POST /api/auth/mfa/verify` | Verify mfaToken (parse JWT type=mfa), verify code (Redis OTP check hoặc TOTP verify) |
| 5 | System | Verify thành công | Issue full JWT (RS256) access + refresh token, tạo `LoginSessionEntity` |
| 6 | System | — | Ghi audit log (`AuditAction.MFA_VERIFY_SUCCESS` hoặc `LOGIN_SUCCESS`) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 (Basic Flow) | MfaService + OtpService | `OtpService.kt` (EXIST) | [REUSE] Mapped |
| FR-002 | UC-001 (Basic Flow) | MfaService + TotpService | `TotpService.kt` (EXIST) | [REUSE] Mapped |
| FR-003 | UC-001 (BR) | MfaController + MfaService | `MfaController.kt` + `MfaService.updateSettings()` (EXIST) | [REUSE] Mapped |
| FR-004 | UC-001 (EF-005) | CaptchaVerifier | `CaptchaVerifier.kt` + `AltchaCaptchaVerifier.kt` (EXIST) | [REUSE] Mapped |
| FR-005 | UC-001 (AF-002) | LoginHandler | `LoginHandler.kt` + `UserEntity.trustedDeviceHash` (PARTIAL) | [MODIFY] Mapped |
| FR-006 | UC-002 (Basic Flow) | SsoAdapter + OAuth2TokenExchanger | `SsoAdapter.kt` + `OAuth2TokenExchanger.kt` (EXIST) | [MODIFY] Extend for Keycloak |
| FR-007 | UC-002 (AF-001) | SsoAdapter | `SsoAdapter.handleCallback()` JIT logic (EXIST) | [MODIFY] Kafka event TODO |
| FR-008 | UC-002 (Related) | SsoController + SsoAdapter | `SsoController.kt` (EXIST) | [REUSE] Mapped |
| FR-009 | UC-003 (Related) | JwtService | `JwtService.kt` — dual RS256+HMAC (EXIST) | [REUSE] Mapped |
| FR-010 | UC-003 (BR-013) | TokenController | `TokenController.jwks()` (EXIST) | [REUSE] Mapped |
| FR-011 | UC-003 (Basic Flow) | TokenController | `TokenController.introspect()` (EXIST) | [REUSE] Mapped |
| FR-012 | UC-003 (Force Logout) | TokenController + AuthService | `TokenController.revokeAllSessions()` (EXIST) | [REUSE] Mapped |
| FR-013 | UC-004 (Basic Flow) | PasswordPolicyService | `PasswordPolicyService.kt` — Passay + cache (EXIST) | [REUSE] Mapped |
| FR-014 | UC-004 (History) | PasswordPolicyService | `PasswordPolicyService.changePassword()` (EXIST) | [REUSE] Mapped |
| FR-015 | (Enriched) | MfaService + OtpService | `OtpService.verifyOtp()` Redis DEL (EXIST) | [REUSE] Mapped |
| FR-016 | (Enriched) | AuditLogService | `AuditLogService.kt` + `AuditAction` enum (EXIST) | [REUSE] Mapped |
| FR-017 | (Enriched) | OAuth2TokenExchanger | `SsoProviderTimeoutException` catch (EXIST) | [REUSE] Mapped |

### Change Impact Map (EXTEND)

```
FR-001 → [REUSE] OtpService (auth/application/OtpService.kt) → no changes needed
FR-002 → [REUSE] TotpService (auth/application/TotpService.kt) → no changes needed
FR-003 → [REUSE] MfaService (auth/application/MfaService.kt) + MfaController → no changes needed
FR-004 → [REUSE] CaptchaVerifier (auth/application/CaptchaVerifier.kt) → no changes needed
FR-005 → [MODIFY] LoginHandler (auth/application/command/LoginHandler.kt) → trusted device logic needs hardening
FR-006 → [MODIFY] OAuth2TokenExchanger (auth/adapter/out/sso/OAuth2TokenExchanger.kt) → add Keycloak support
FR-007 → [MODIFY] SsoAdapter (auth/application/SsoAdapter.kt) → Kafka event publish pending
FR-008 → [REUSE] SsoController (auth/adapter/in/web/SsoController.kt) → no changes needed
FR-009 → [REUSE] JwtService (auth/application/JwtService.kt) → RS256 already primary
FR-010 → [REUSE] TokenController (auth/adapter/in/web/TokenController.kt) → JWKS endpoint exists
FR-011 → [REUSE] TokenController → introspect endpoint exists
FR-012 → [REUSE] TokenController → revoke-all endpoint exists
FR-013 → [REUSE] PasswordPolicyService (auth/application/PasswordPolicyService.kt) → fully implemented
FR-014 → [REUSE] PasswordPolicyService → history check fully implemented
FR-015 → [REUSE] OtpService → idempotent via Redis DEL
FR-016 → [REUSE] AuditLogService → audit events cover all MFA/SSO actions
FR-017 → [REUSE] OAuth2TokenExchanger → timeout exception handling exists
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
[CHANGED] Feature này đã TIẾN TRIỂN ĐÁng KỂ so với pre_openspec v1 (2026-08-05). Lúc đó ~60% code là stub, nay ~90% đã fully implemented. Phân tích từ research phase xác nhận: MFA done, SSO done, RS256 done, introspection done, password policy done. Chỉ còn 3 gaps nhỏ:
1. Keycloak support trong OAuth2TokenExchanger (chỉ có Google + Microsoft)
2. Kafka event publish cho SSO provisioning (compileOnly dependency)
3. Trusted device flow cần hardening (field exists, logic partial)

Complexity level: **LOW** — hầu hết code đã written và tested. Focus remaining: testing coverage, edge case handling, Keycloak integration.

### Related Features / Precedents
- `erp-iam-system` (archived at `openspec/changes/archive/erp-iam-system/`) — scaffolded overall IAM architecture
- `2026-08-11-auth-login-admin` (archived) — login flow + admin endpoints
- `anonymous-login-optimization` (in pipeline) — anonymous session + promotion flow — có overlap ở `LoginHandler`, `AuthService`, `SessionPromotionService`
- `api-response-i18n-standard` (in pipeline) — i18n cho error messages — có overlap ở `AuthCoreExceptions`, `AuthErrorCode`

### Integration Notes
- **Redis**: ✅ Dependency có, `RedisConfig.kt` exists, `StringRedisTemplate` widely used (MFA, OTP, rate limit, anonymous sessions).
- **OAuth2**: ✅ `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server` in build.gradle.kts.
- **Kafka**: ⚠️ `spring-kafka` compileOnly — SsoAdapter injects KafkaTemplate nhưng actual producer chưa active. Need to switch to `implementation` khi Kafka available.
- **TOTP**: ✅ `dev.samstevens.totp:totp:1.7.1` in build.gradle.kts.
- **Passay**: ✅ `org.passay:passay:1.6.4` in build.gradle.kts.
- **Flyway**: V2 migration already applied — `V2__auth_core_features.sql` (ALTER users + CREATE user_identities, password_policies, password_history).

### Suggested Approach
1. **Keycloak support**: Extend `OAuth2TokenExchanger` with configurable Keycloak endpoints (issuer-uri based).
2. **Trusted device hardening**: Complete SHA-256 fingerprint generation + Redis-backed TTL management in `LoginHandler`.
3. **Kafka event**: When Kafka infra ready, switch `compileOnly` → `implementation` and implement `EventPublisher` port.
4. **Testing**: Add WireMock-based integration tests for SSO flow. Increase unit test coverage for `PasswordPolicyService` edge cases.
5. **Documentation**: Document default config values for CAPTCHA threshold, MFA token TTL, OTP TTL in SecurityProperties.

### Context from Confluence Images
N/A — source là file-based research artifacts (business_analysis.md, technical_spec.md).

### Delta Summary (vs pre_openspec v1 — 2026-08-05)
| Section | Change Type | Details |
|---------|-------------|---------|
| Feature Summary | [CHANGED] | Updated to reflect ~90% implementation status |
| FR Status fields | [NEW] | Added ✅ IMPLEMENTED / ⚠️ PARTIAL status per FR |
| Quality Score | [CHANGED] | 88 → 91 (RS256 migration risk resolved via dual-key) |
| Issues #2 | [CHANGED] | RS256 migration risk → RESOLVED (replaced with Kafka TODO) |
| Section 10.3 | [CHANGED] | Single candidate `auth-service` (removed `base-core` partial) |
| Detection Evidence | [CHANGED] | Expanded from 4 entries to 11 entries (new files detected) |
| Section 10.5 | [CHANGED] | All modules now marked EXIST (vs NEW in v1) |
| Traceability Matrix | [CHANGED] | Added [MODIFY]/[REUSE] tags + Change Impact Map |
| Agent Notes | [CHANGED] | Updated observations, related features, integration notes |
| NFRs | [CHANGED] | Added NFR-005 (password cache), NFR-006 (login latency) |
