# Pre-OpenSpec: auth-core-features

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (Feature Research — business_analysis.md + technical_spec.md)
> **Classification Evidence**: `AuthService.kt` → `auth/application` → `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (existing login/register/MFA/SSO flow, cần hoàn thiện + hardening)
> **Archive**: `openspec/changes/archive/2026-08-20-auth-core-features/pre_openspec.md` (previous version 2026-08-20)
> **Quality Score**: 93/100
> **Mode**: DELTA (archive found, input refined from feature research + code re-scan 2026-08-25)
> **_Generated**: 2026-08-25

## 📋 Feature Summary

Hoàn thiện và hardening auth-service với 4 nhóm tính năng core authentication: Multi-Factor Authentication (OTP SMS/Email, TOTP Authenticator, CAPTCHA, Recovery Codes), SSO/OAuth2 (Google, Microsoft, Keycloak) với JIT provisioning, JWT RS256 + token introspection (RFC 7662) + JWKS endpoint + session binding, và Password Policy per domain (complexity via Passay, history, expiry). ~95% code đã implement — tập trung vào testing, hardening, và hoàn thiện edge cases. Code scan xác nhận: hexagonal architecture (port/adapter), CQRS pattern (Command/Handler), Snowflake IDs, full entity/repository layer. [CHANGED] KafkaEventPublisher đã implement — SSO provisioning events có thể publish qua Kafka.

| Metric | Giá trị |
|--------|---------|
| Số FR | 17 (URD: 14, Enriched: 3) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 1 |
| **Quality Score** | **93/100** |

---

## 1. Actors

- **End User**: Người dùng hệ thống — đăng nhập, quản lý MFA, đổi mật khẩu, liên kết SSO.
- **System Administrator**: Quản trị viên — cấu hình password policy, force logout, quản lý rate limits.
- **System (Resource Server)**: Service downstream validate token qua introspection/JWKS.
- **External IdP**: Google, Microsoft, Keycloak — OAuth2/OIDC protocol.

## 2. Functional Requirements

### FR-001: Xác thực OTP SMS/Email [URD]
- **Actor**: End User
- **Action**: Hệ thống phải gửi mã OTP (6 chữ số) qua SMS hoặc Email khi user login và MFA enabled cho phương thức SMS/EMAIL.
- **Validation**: OTP TTL = 5 phút (300s), max 3 lần nhập sai per mfaToken, lưu Redis (`otp:{userId}:{channel}`), constant-time comparison.
- **Status**: ✅ IMPLEMENTED — `OtpService.kt` (`auth/application/OtpService.kt`) + Redis-backed storage via `StringRedisTemplate`.

### FR-002: Xác thực TOTP (Authenticator App) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ setup và verify TOTP dùng `dev.samstevens.totp`.
- **Validation**: Window = 30s, drift tolerance ±1 step, secret AES-256 encrypted (`totp_secret_encrypted`).
- **Status**: ✅ IMPLEMENTED — `TotpService.kt` (`auth/application/TotpService.kt`) + AES-256 encryption.

### FR-003: Bật/tắt MFA và chọn phương thức [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép user bật/tắt MFA và chọn phương thức qua `PUT /api/auth/mfa/settings`.
- **Validation**: Khi bật TOTP cần secret đã setup. Khi tắt → `mfaMethod = "NONE"`.
- **Status**: ✅ IMPLEMENTED — `MfaService.updateSettings()` + `MfaController.kt`.

### FR-004: CAPTCHA cho login form [URD]
- **Actor**: End User
- **Action**: Hệ thống phải yêu cầu CAPTCHA khi user vượt quá N lần login sai (N cấu hình per domain qua `SecurityProperties`).
- **Validation**: Pluggable adapter: `CaptchaVerifier` interface → `CaptchaGateway` port → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient`.
- **Status**: ✅ IMPLEMENTED — `CaptchaVerifier.kt` + `AltchaCaptchaVerifier.kt` + full gateway chain.

### FR-005: Trusted Device (Skip MFA) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép đánh dấu thiết bị tin cậy để skip MFA trong 30 ngày.
- **Validation**: SHA-256 device fingerprint hash, lưu `trusted_device_hash` trên `UserEntity`. V10 migration (`V10__trusted_device_ttl.sql`) thêm `trusted_device_set_at` column.
- **Status**: ⚠️ PARTIAL — Field exists, `LoginRequestDto.trustedDeviceHash` exists, V10 migration applied. Logic cần harden (TTL enforcement via `trusted_device_set_at`).

### FR-006: OAuth2 SSO Login [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ đăng nhập qua OAuth2 (Google, Microsoft, Keycloak).
- **Validation**: `OAuth2TokenExchanger` exchange code → token. SSO chain: `SsoGateway` → `HttpSsoGateway` → `SsoProviderClient`.
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.kt` + `SsoController.kt` + `OAuth2TokenExchanger.kt` + gateway chain.

### FR-007: JIT User Provisioning từ SSO [URD]
- **Actor**: System
- **Action**: Hệ thống phải tự động tạo `UserEntity` khi user đăng nhập SSO lần đầu (nếu auto-provision enabled).
- **Validation**: Dùng `sub` claim làm identity key (KHÔNG dùng email). Event publish qua `EventPublisher` port → `KafkaEventPublisher` (khi Kafka configured) hoặc `SpringEventPublisher` (fallback).
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` JIT provisioning. [CHANGED] `KafkaEventPublisher.kt` đã implement với retry (3 attempts, exponential backoff), `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`.

### FR-008: Liên kết/gỡ SSO Identity [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép liên kết tài khoản hiện có với SSO provider (`POST /api/auth/sso/link`), và gỡ liên kết (`DELETE /api/auth/sso/unlink/{provider}`).
- **Validation**: Lưu `user_identities` table via `UserIdentityRepository`. Gỡ liên kết chỉ khi user có password hoặc còn SSO identity khác (`CannotUnlinkLastIdentityException`).
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()` + `SsoAdapter.unlinkIdentity()` + `SsoController.kt`.

### FR-009: JWT RS256 Signing [URD]
- **Actor**: System
- **Action**: Hệ thống phải sign JWT bằng RS256 asymmetric (primary) với HMAC-SHA256 fallback (7-day migration).
- **Validation**: RSA key pair loaded từ file path (`jwt.privateKeyPath`, `jwt.publicKeyPath`). Private key chỉ tại auth-service. Token generation via `TokenGenerator` command handler.
- **Status**: ✅ IMPLEMENTED — `JwtService.kt` với dual-key support (RS256 primary, HMAC legacy fallback).

### FR-010: JWKS Endpoint [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải expose `GET /.well-known/jwks.json` để resource servers fetch public key tự động.
- **Validation**: RSA public key exposed qua `JwtService.getJwks()`. Cache-Control: `max-age=86400, public`.
- **Status**: ✅ IMPLEMENTED — `TokenController.jwks()` endpoint.

### FR-011: Token Introspection (RFC 7662) [URD]
- **Actor**: System (Resource Server)
- **Action**: Hệ thống phải cung cấp `POST /api/auth/introspect` để validate token server-side.
- **Validation**: Trả `active`, `sub`, `username`, `roles`, `permissions`, `exp`, `iat`, `iss`, `jti`. Check jti blacklist via `TokenBlacklistRepository`. Token store via `TokenStore` port → `TokenStorePersistenceAdapter`.
- **Status**: ✅ IMPLEMENTED — `TokenController.introspect()` endpoint.

### FR-012: Force Logout (Session Revocation) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép admin revoke toàn bộ session của user qua admin session management endpoints.
- **Validation**: Blacklist all active jti, delete all refresh tokens. Return `revokedCount`. CQRS: `RevokeSessionsCommand` → `RevokeSessionsHandler`.
- **Status**: ✅ IMPLEMENTED — `AdminSessionController.kt` + `RevokeSessionsHandler.kt` → `AuthService.revokeAllSessions()`.

### FR-013: Password Policy per Domain [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép cấu hình password policy riêng cho từng domain (complexity, history, expiry).
- **Validation**: Dùng Passay library — dynamic rules via `PasswordValidator`. Lưu `password_policies` table via `PasswordPolicyRepository`. Default policy nếu chưa cấu hình. Cached per `domainId`.
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.kt` với Passay integration, cache, domain-scoped policies.

### FR-014: Password History [URD]
- **Actor**: System
- **Action**: Hệ thống phải chặn reuse N passwords gần nhất khi đổi mật khẩu.
- **Validation**: Lưu `password_history` table via `PasswordHistoryRepository`. `BCrypt.matches()` kiểm tra mỗi hash. Prune entries cũ vượt `historyCount`.
- **Status**: ✅ IMPLEMENTED — `PasswordPolicyService.checkPasswordHistory()` + `PasswordPolicyService.pruneHistory()`.

## 3. Non-functional Requirements

- NFR-001: OTP/MFA verification latency ≤ 200ms (Redis-backed, constant-time compare).
- NFR-002: RS256 signing/verification < 50ms per token (RSA 2048-bit key pair).
- NFR-003: JWKS endpoint cache TTL = 24 giờ (`Cache-Control: max-age=86400, public`).
- NFR-004: CAPTCHA verification timeout = 5 giây (max), pluggable adapter.
- NFR-005: Password validator cache hit ratio > 95% (`ConcurrentHashMap` per domainId).
- NFR-006: Login endpoint P95 latency < 500ms (including MFA check + CAPTCHA + rate limit).
- NFR-007: [NEW] Kafka event publish retry 3 attempts, exponential backoff (1s → 2s → 4s), max delay 8s.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (OTP) và FR-002 (TOTP) được tách riêng theo phương thức xác thực. FR-013 (Password Policy) và FR-014 (Password History) tách riêng theo concern.

## 5. Enriched Domain Requirements

Max enriched: min(5, ceil(14 × 0.20)) = min(5, 3) = 3.

### Enriched FRs

### FR-015: Idempotency cho MFA verification [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotent cho `/api/auth/mfa/verify` — cùng mfaToken + code chỉ issue JWT 1 lần.
- **Validation**: Redis DELETE sau khi OTP verify thành công, ngăn replay. MFA rate limit reset sau verify thành công. [CHANGED] `IdempotencyFilter.kt` cung cấp generic idempotency cho mutating endpoints via `X-Idempotency-Key` header + Redis TTL 24h.
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` deletes key after success + `IdempotencyFilter.kt` (shared/filter/) generic filter.

### FR-016: Audit logging cho MFA/SSO events [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi audit log cho mọi sự kiện MFA (setup, verify_success, verify_failed) và SSO (login, link, unlink).
- **Validation**: `AuditLogService.logEvent()` with `AuditAction` enum. Log IP address + User-Agent.
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum (25 total actions including E2EE and Account Lifecycle).

### FR-017: Timeout handling cho IdP call [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xử lý timeout khi gọi IdP token endpoint (exchange code → token).
- **Validation**: Trả lỗi `SsoProviderTimeoutException` (HTTP 504) nếu IdP không phản hồi. HTTP client config via `HttpClientConfig.kt`.
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout → throws `SsoProviderTimeoutException`.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | OTP storage, MFA rate limiting, login rate limiting, session management, TOTP setup pending, anonymous sessions, idempotency cache | `spring-boot-starter-data-redis` — ✅ `RedisConfig.kt` exists |
| Kafka | Consumer: permission change events. Producer: SSO provisioning events | Consumer: `PermissionChangedConsumer.kt`. [CHANGED] Producer: `KafkaEventPublisher.kt` (3 retries, backoff) — activated via `spring.kafka.bootstrap-servers`. Fallback: `SpringEventPublisher` (in-process) |
| Google OAuth2 | SSO provider | `OAuth2TokenExchanger` → `SsoProviderClient` — OIDC protocol |
| Microsoft OAuth2 | SSO provider | `OAuth2TokenExchanger` → `SsoProviderClient` — OIDC protocol |
| Keycloak | Optional SSO provider | ⚠️ NOT YET in `OAuth2TokenExchanger.getTokenEndpoint()` (only Google + Microsoft) |
| ALTCHA | CAPTCHA verification | `CaptchaVerifier` → `CaptchaGateway` → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient` |
| Passay | Password validation | `org.passay:passay:1.6.4` — in-process library |
| dev.samstevens.totp | TOTP generation/verification | `dev.samstevens.totp:totp:1.7.1` — in-process library |
| PostgreSQL | JPA/Hibernate entities | 15+ entities, 23+ repositories, Flyway V1-V10 migrations |
| Caffeine | L1 permission cache | `CaffeinePermissionCache` → `MultiTierPermissionCache` |

## 6. Assumptions

- ⚠️ Assumption: Redis available tại runtime cho MFA flow — lý do: OTP storage + rate limiting + idempotency require Redis (critical dependency). `MfaRateLimitService` implements fail-open strategy khi Redis unavailable.
- ⚠️ Assumption: RSA key pair sẽ được generate offline và inject qua `jwt.privateKeyPath` / `jwt.publicKeyPath` env vars — lý do: key management ngoài scope.
- ⚠️ Assumption: CAPTCHA provider secret key cung cấp qua environment variables (`app.security.captcha.*`) — lý do: provider-specific config.
- ⚠️ Assumption: SSO providers (Google, Microsoft) cấu hình trong `application.yml` qua `app.security.sso.providers.*` — lý do: pluggable config.
- ⚠️ Assumption: `dev.samstevens.totp:1.7.1` vẫn stable dù GitHub repo 404 — lý do: TOTP là stable protocol, Maven Central available.
- ⚠️ Assumption: Kafka event publish deferred — `KafkaEventPublisher` activated only when `spring.kafka.bootstrap-servers` configured. Fallback: `SpringEventPublisher` (in-process `ApplicationEventPublisher`).

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-004: CAPTCHA threshold configurable nhưng default value chưa documented rõ |
| Đầy đủ (Completeness) | 24/25 | FR-005: Trusted device TTL enforcement chưa hoàn thiện (V10 migration applied, logic pending) |
| Nhất quán (Consistency) | 25/25 | Không phát hiện mâu thuẫn |
| Kiểm thử được (Testability) | 20/25 | FR-006, FR-007: SSO flow phụ thuộc external IdP — `SsoProviderClient` interface supports WireMock-based testing |
| **Tổng** | **93/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -1 | FR-004 | CAPTCHA threshold configurable nhưng default value chưa rõ trong docs | Document default N=5 trong SecurityProperties Javadoc |
| 2 | Completeness | -1 | FR-005 | "Trusted device" — TTL enforcement via `trusted_device_set_at` cần complete | Implement TTL check trong `LoginHandler` — compare `now()` with `trusted_device_set_at + 30 days` |
| 3 | Testability | -5 | FR-006, FR-007 | SSO callback flow phụ thuộc external IdP — cần mock IdP | Cung cấp WireMock-based test profile cho Google/Microsoft via `SsoProviderClient` interface |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Missing | 🟡 | Keycloak provider chưa có trong `OAuth2TokenExchanger.getTokenEndpoint()` — chỉ Google + Microsoft | FR-006 | Thêm Keycloak endpoint config (configurable `issuer-uri`) |

> [CHANGED] Issue #2 từ version trước (Kafka event publish deferred) — RESOLVED. `KafkaEventPublisher.kt` đã implement.

## 9. Open Questions

- Q1: Keycloak token/userinfo endpoint cần configurable per-instance (không có fixed URL như Google/Microsoft). Cần extend `OAuth2TokenExchanger` hoặc dùng Spring Security OAuth2 client registration?

> Recommend: Dùng `spring.security.oauth2.client.registration` + `provider` config thay vì hardcode endpoints. `SsoProviderClient` interface hỗ trợ pluggable implementation.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Authorization — MFA (OTP/TOTP/CAPTCHA/Recovery Codes), SSO (OAuth2/OIDC), Token Management (RS256/JWKS/Introspection), Password Policy (Passay/History/Expiry)

### 10.2 Flow Type
Non-Financial (xác thực identity, không liên quan giao dịch tài chính trực tiếp)

### 10.3 Candidate Services
- `auth-service`: Primary service — chứa toàn bộ auth core modules (keyword: login, auth, jwt, mfa, totp, otp, sso, password, captcha, token)

### Detection Evidence
- Keyword: `login`, `auth`, `mfa` → Module: `auth/application` → File: `AuthService.kt`, `MfaService.kt`, `LoginHandler.kt`
- Keyword: `jwt`, `token`, `rs256` → Module: `auth/application` → File: `JwtService.kt`, `TokenGenerator.kt`
- Keyword: `sso`, `oauth2` → Module: `auth/application` + `auth/adapter/out/sso` + `auth/adapter/out/http` → File: `SsoAdapter.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`, `SsoProviderClient.kt`
- Keyword: `password`, `policy`, `passay` → Module: `auth/application` → File: `PasswordPolicyService.kt`
- Keyword: `captcha` → Module: `auth/application` + `auth/adapter/out/gateway` + `auth/adapter/out/http` → File: `CaptchaVerifier.kt`, `AltchaCaptchaVerifier.kt`, `CaptchaGatewayAdapter.kt`, `HttpCaptchaGateway.kt`, `CaptchaClient.kt`
- Keyword: `otp`, `totp` → Module: `auth/application` → File: `OtpService.kt`, `TotpService.kt`
- Keyword: `UserEntity`, `UserIdentityEntity` → Module: `rbac/adapter/out/persistence/entity` → File: `UserEntity.kt`, `UserIdentityEntity.kt`
- Keyword: `PasswordPolicy`, `PasswordHistory` → Module: `rbac/adapter/out/persistence/entity` → File: `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`
- Keyword: `SecurityConfig`, `JwtAuthFilter` → Module: `shared/config`, `shared/security` → File: `SecurityConfig.kt`, `JwtAuthFilter.kt`
- Keyword: `AuthException`, `AuthErrorCode` → Module: `shared/exception` → File: `AuthExceptions.kt`, `AuthCoreExceptions.kt`, `AuthErrorCode.kt`
- Keyword: `AuditLogService`, `AuditAction` → Module: `shared/audit` → File: `AuditLogService.kt`
- Keyword: `port`, `adapter` → Module: `auth/application/port/out` → File: `UserPort.kt`, `TokenStore.kt`, `DomainPort.kt`, `EventPublisher.kt`, `CaptchaGateway.kt`, `SsoGateway.kt`, `PermissionCache.kt`
- Keyword: `rate limit` → Module: `auth/application` + `auth/adapter/in/web/filter` → File: `MfaRateLimitService.kt`, `LoginRateLimitService.kt`, `LoginRateLimitFilter.kt`
- Keyword: `session` → Module: `auth/application` + `auth/adapter/out/persistence` → File: `LoginSessionService.kt`, `SessionPolicyService.kt`, `LoginSessionEntity.kt`, `LoginSessionRepository.kt`
- [NEW] Keyword: `kafka`, `event` → Module: `auth/adapter/out/event` → File: `KafkaEventPublisher.kt`, `SpringEventPublisher.kt`
- [NEW] Keyword: `idempotency` → Module: `shared/filter` → File: `IdempotencyFilter.kt` (Redis-backed, X-Idempotency-Key header, TTL 24h)
- [NEW] Keyword: `service token`, `internal api` → Module: `auth/application` + `auth/adapter/in/web` + `auth/adapter/in/web/filter` → File: `ServiceTokenService.kt`, `InternalApiController.kt`, `ServiceAuthFilter.kt`

### 10.4 External Integrations
- **Redis**: OTP storage (`otp:{userId}:{channel}`), MFA rate limiting (`mfa:ratelimit:*`), login rate limiting, session management, TOTP setup pending, anonymous sessions, idempotency cache (`idempotency:auth-service:*` TTL 24h) — `StringRedisTemplate` via `RedisConfig.kt`
- **Kafka**: Consumer: `PermissionChangedConsumer.kt` (topic `iam.permission.changed`, groupId `auth-service`). [CHANGED] Producer: `KafkaEventPublisher.kt` — `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`, retry 3 attempts exponential backoff. Fallback: `SpringEventPublisher` (in-process).
- **OAuth2 IdPs**: Google, Microsoft — via `OAuth2TokenExchanger` → `SsoProviderClient`
- **CAPTCHA Providers**: ALTCHA — via `CaptchaVerifier` interface → `CaptchaGateway` port → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient`
- **Passay**: In-process password validation library — `org.passay:passay:1.6.4`
- **dev.samstevens.totp**: In-process TOTP library — `dev.samstevens.totp:totp:1.7.1`
- **PostgreSQL**: JPA/Hibernate entities — `users`, `user_identities`, `password_policies`, `password_history`, `refresh_tokens`, `token_blacklist`, `login_sessions`, `cipher_key_sessions`, `vault_access_logs`, `account_deletion_requests`, `account_data_exports`. Flyway V1-V10.
- **Caffeine**: L1 cache for permissions — `CaffeinePermissionCache.kt`, `MultiTierPermissionCache.kt`

### 10.5 Required Modules
- `auth/application`: AuthService, MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService, CaptchaVerifier, AltchaCaptchaVerifier, MfaRateLimitService, LoginRateLimitService, AnonymousRateLimitService, LoginSessionService, JwtService, SessionPolicyService, AccountLifecycleService, DomainLookupService, ServiceTokenService, SessionPromotionService, AnonymousSessionDataService (ALL EXIST)
- `auth/application/command`: LoginCommand/Handler, RegisterCommand/Handler, RefreshTokenCommand/Handler, SwitchDomainCommand/Handler, RevokeSessionsCommand/Handler, TokenGenerator, AnonymousSessionHandler, RenewAnonymousTokenHandler (ALL EXIST — CQRS)
- `auth/application/query`: BuildAuthResponseQuery/Handler (ALL EXIST)
- `auth/application/port/out`: UserPort, TokenStore, DomainPort, EventPublisher, CaptchaGateway, SsoGateway, PermissionCache (ALL EXIST — 7 hexagonal ports)
- `auth/application/event`: NewDeviceLoginEvent, RateLimitExceededEvent (ALL EXIST)
- `auth/application/cipher`: CipherVersionNegotiator, DecryptionVaultService, EncryptedAuditService, X25519KeyExchangeServiceImpl (ALL EXIST)
- `auth/adapter/in/web`: AuthController, CqrsAuthController, MfaController, SsoController, TokenController, CaptchaController, AdminSessionController, SessionController, AccountLifecycleController, KeyExchangeController, RateLimitAdminController, AnonymousAuthController, InternalApiController (ALL EXIST — 13 controllers)
- `auth/adapter/in/web/dto`: MfaDtos, SsoDtos, TokenDtos, RequestDtos, AuthResponse, AnonymousDtos (ALL EXIST — 25+ data classes)
- `auth/adapter/in/web/filter`: LoginRateLimitFilter, ClientMetadataFilter, ContentLanguageFilter, ServiceAuthFilter (ALL EXIST — 4 filters)
- `auth/adapter/in/kafka`: PermissionChangedConsumer (EXISTS)
- `auth/adapter/out/sso`: OAuth2TokenExchanger (EXISTS — extend for Keycloak)
- `auth/adapter/out/http`: HttpCaptchaGateway, HttpSsoGateway, CaptchaClient, SsoProviderClient (ALL EXIST)
- `auth/adapter/out/gateway`: CaptchaGatewayAdapter (EXISTS)
- `auth/adapter/out/cache`: CaffeinePermissionCache, InMemoryPermissionCache, MultiTierPermissionCache (ALL EXIST)
- `auth/adapter/out/event`: SpringEventPublisher, KafkaEventPublisher (ALL EXIST — [CHANGED] KafkaEventPublisher is NEW)
- `auth/adapter/out/persistence`: UserPersistenceAdapter, TokenStorePersistenceAdapter, DomainPersistenceAdapter (ALL EXIST)
- `auth/adapter/out/persistence/entity`: LoginSessionEntity, CipherKeySessionEntity, AccountDeletionRequestEntity, AccountDataExportEntity, VaultAccessLogEntity (ALL EXIST)
- `auth/adapter/out/persistence/mapper`: UserEntityMapper (EXISTS)
- `auth/adapter/out/persistence/repository`: LoginSessionRepository, CipherKeySessionJpaRepository, VaultAccessLogJpaRepository, AccountDeletionRequestRepository, AccountDataExportRepository (ALL EXIST)
- `auth/adapter/out/cipher`: TinkCipherAlgorithmFactory, RedisAntiReplayValidator, RedisCipherKeySessionResolver, HmacFieldSigningServiceImpl (ALL EXIST)
- `auth/domain/model`: User, AuthToken, UserStatus (sealed interface), value objects (DomainCode, Email, PasswordHash, UserId) (ALL EXIST)
- `auth/domain/service`: TokenHasher (EXISTS)
- `rbac/adapter/out/persistence/entity`: UserEntity (extends SnowflakePersistentAuditableEntity), UserIdentityEntity (extends SnowflakeBaseEntity), PasswordPolicyEntity (extends SnowflakeBaseEntity), PasswordHistoryEntity (extends SnowflakeBaseEntity), RefreshTokenEntity (extends SnowflakeBaseEntity), TokenBlacklistEntity (extends SnowflakeBaseEntity), DomainEntity, UserDomainEntity, GroupEntity, UserGroupEntity, DomainRoleEntity, GroupRoleEntity, RolePermissionEntity, DomainResourceEntity, ActionEntity, PermissionEntity (ALL EXIST — 16 entities)
- `rbac/adapter/out/persistence/repository`: Repositories.kt — UserRepository, DomainRepository, UserDomainRepository, GroupRepository, UserGroupRepository, DomainRoleRepository, GroupRoleRepository, ActionRepository, DomainResourceRepository, PermissionRepository, RolePermissionRepository, RefreshTokenRepository, TokenBlacklistRepository, UserIdentityRepository, PasswordPolicyRepository, PasswordHistoryRepository (ALL EXIST — 16 repositories)
- `shared/config`: SecurityConfig, SecurityProperties, RedisConfig, HttpClientConfig, JacksonConfig, JpaAuditingConfig, I18nConfig, KafkaConfig (ALL EXIST)
- `shared/security`: JwtAuthFilter (EXISTS)
- `shared/exception`: AuthExceptions, AuthCoreExceptions, AnonymousExceptions, CipherExceptions, AuthErrorCode (45 error codes: AUTH_001-AUTH_044), GlobalExceptionHandler (ALL EXIST)
- `shared/audit`: AuditLogService + AuditAction enum (25 action types) (ALL EXIST)
- `shared/persistence`: VersionedAuditableEntity (extends SnowflakePersistentAuditableEntity + @Version) (EXISTS)
- `shared/i18n`: DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository (ALL EXIST)
- `shared/cache`: AbstractTwoTierCache (EXISTS — abstract base for L1 Caffeine + L2 Redis)
- `shared/filter`: IdempotencyFilter (EXISTS — [NEW] Redis-backed, X-Idempotency-Key header)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Gửi username + password + captchaToken? + trustedDeviceHash? tới `POST /api/auth/login` | Validate CAPTCHA (nếu required), validate credentials (BCrypt match) |
| 2 | System | Check `user.mfaEnabled` và `trustedDeviceHash` | Nếu MFA enabled và device không trusted → trả `MfaRequired(mfaToken, method, expiresIn)` |
| 3 | End User | Nhận OTP qua SMS/Email hoặc mở Authenticator App | System gửi OTP qua channel hoặc skip (TOTP stateless) |
| 4 | End User | Gửi OTP/TOTP code + mfaToken tới `POST /api/auth/mfa/verify` | Verify mfaToken (parse JWT type=mfa), verify code (Redis OTP check hoặc TOTP verify) |
| 5 | System | Verify thành công | Issue full JWT (RS256) access + refresh token, tạo `LoginSessionEntity` |
| 6 | System | — | Ghi audit log (`AuditAction.MFA_VERIFY_SUCCESS` hoặc `LOGIN_SUCCESS`) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 (Basic Flow) | MfaService + OtpService | `OtpService.kt` (EXIST) | [REUSE] Mapped |
| FR-002 | UC-001 (Basic Flow) | MfaService + TotpService | `TotpService.kt` (EXIST) | [REUSE] Mapped |
| FR-003 | UC-001 (BR) | MfaController + MfaService | `MfaController.kt` + `MfaService.updateSettings()` (EXIST) | [REUSE] Mapped |
| FR-004 | UC-001 (EF-005) | CaptchaVerifier | `CaptchaVerifier.kt` + `CaptchaGatewayAdapter.kt` (EXIST) | [REUSE] Mapped |
| FR-005 | UC-001 (AF-002) | LoginHandler | `LoginHandler.kt` + `UserEntity.trustedDeviceHash` (PARTIAL) | [MODIFY] Mapped |
| FR-006 | UC-002 (Basic Flow) | SsoAdapter + OAuth2TokenExchanger | `SsoAdapter.kt` + `OAuth2TokenExchanger.kt` + `HttpSsoGateway.kt` + `SsoProviderClient.kt` (EXIST) | [MODIFY] Extend for Keycloak |
| FR-007 | UC-002 (AF-001) | SsoAdapter + EventPublisher | `SsoAdapter.handleCallback()` + `KafkaEventPublisher.kt` (EXIST) | [REUSE] Mapped |
| FR-008 | UC-002 (Related) | SsoController + SsoAdapter | `SsoController.kt` (EXIST) | [REUSE] Mapped |
| FR-009 | UC-003 (Related) | JwtService | `JwtService.kt` — dual RS256+HMAC (EXIST) | [REUSE] Mapped |
| FR-010 | UC-003 (BR-013) | TokenController | `TokenController.jwks()` (EXIST) | [REUSE] Mapped |
| FR-011 | UC-003 (Basic Flow) | TokenController | `TokenController.introspect()` (EXIST) | [REUSE] Mapped |
| FR-012 | UC-003 (Force Logout) | AdminSessionController + RevokeSessionsHandler | `AdminSessionController.kt` + `RevokeSessionsHandler.kt` (EXIST) | [REUSE] Mapped |
| FR-013 | UC-004 (Basic Flow) | PasswordPolicyService | `PasswordPolicyService.kt` — Passay + cache (EXIST) | [REUSE] Mapped |
| FR-014 | UC-004 (History) | PasswordPolicyService | `PasswordPolicyService.changePassword()` (EXIST) | [REUSE] Mapped |
| FR-015 | (Enriched) | MfaService + OtpService + IdempotencyFilter | `OtpService.verifyOtp()` Redis DEL + `IdempotencyFilter.kt` (EXIST) | [REUSE] Mapped |
| FR-016 | (Enriched) | AuditLogService | `AuditLogService.kt` + `AuditAction` enum — 25 action types (EXIST) | [REUSE] Mapped |
| FR-017 | (Enriched) | OAuth2TokenExchanger | `SsoProviderTimeoutException` catch (EXIST) | [REUSE] Mapped |

### Change Impact Map (EXTEND)

```
FR-001 → [REUSE] OtpService (auth/application/OtpService.kt) → no changes needed
FR-002 → [REUSE] TotpService (auth/application/TotpService.kt) → no changes needed
FR-003 → [REUSE] MfaService (auth/application/MfaService.kt) + MfaController → no changes needed
FR-004 → [REUSE] CaptchaVerifier (auth/application/CaptchaVerifier.kt) + CaptchaGateway chain → no changes needed
FR-005 → [MODIFY] LoginHandler (auth/application/command/LoginHandler.kt) → trusted device TTL enforcement
FR-006 → [MODIFY] OAuth2TokenExchanger (auth/adapter/out/sso/OAuth2TokenExchanger.kt) → add Keycloak support
FR-007 → [REUSE] SsoAdapter (auth/application/SsoAdapter.kt) + KafkaEventPublisher (DONE) → no changes needed
FR-008 → [REUSE] SsoController (auth/adapter/in/web/SsoController.kt) → no changes needed
FR-009 → [REUSE] JwtService (auth/application/JwtService.kt) → RS256 already primary
FR-010 → [REUSE] TokenController (auth/adapter/in/web/TokenController.kt) → JWKS endpoint exists
FR-011 → [REUSE] TokenController → introspect endpoint exists
FR-012 → [REUSE] AdminSessionController + RevokeSessionsHandler → revoke-all endpoint exists
FR-013 → [REUSE] PasswordPolicyService (auth/application/PasswordPolicyService.kt) → fully implemented
FR-014 → [REUSE] PasswordPolicyService → history check fully implemented
FR-015 → [REUSE] OtpService → idempotent via Redis DEL + IdempotencyFilter
FR-016 → [REUSE] AuditLogService → audit events cover all MFA/SSO actions (25 action types)
FR-017 → [REUSE] OAuth2TokenExchanger → timeout exception handling exists
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
[CHANGED] Code scan 2026-08-25 xác nhận architecture maturity cao — improvement since last scan:

**Architecture:**
- Hexagonal: 7 outbound ports, corresponding adapter implementations
- CQRS: 8 Command/Handler pairs + 2 Query/Handler pairs
- Domain model: sealed interface `UserStatus`, value objects, `TokenHasher` domain service
- Multi-tier caching: `AbstractTwoTierCache` abstract base → `CaffeinePermissionCache` + `MultiTierPermissionCache`

**New since last scan (2026-08-20):**
1. `KafkaEventPublisher.kt` — fully implemented with retry + exponential backoff. Resolves Issue #2 from previous version.
2. `IdempotencyFilter.kt` — generic idempotency filter for mutating HTTP endpoints (POST/PUT/PATCH). Redis-backed with `X-Idempotency-Key` header and 24h TTL.
3. `ServiceTokenService.kt` + `ServiceAuthFilter.kt` + `InternalApiController.kt` — service-to-service authentication.
4. Flyway V10 migration (`V10__trusted_device_ttl.sql`) — adds `trusted_device_set_at` for TTL enforcement.

**File count scan (updated):**
- Controllers: 13 `@RestController` classes (auth: 13, rbac: 5, pbac: 1)
- Application services: 22+ service classes
- Entities: 16+ `@Entity` classes (rbac: 16, auth/persistence: 5)
- Repositories: 23+ `JpaRepository` interfaces (rbac: 16, auth: 5, shared: 1, pbac: 1)
- DTOs: 6 DTO files with 25+ data classes
- Exception classes: 5 exception files + `AuthErrorCode` enum (45 error codes: AUTH_001-AUTH_044)
- Ports: 7 outbound port interfaces
- Filters: 5 web filters (4 auth + 1 shared/IdempotencyFilter)
- Event publishers: 2 (KafkaEventPublisher + SpringEventPublisher)
- Cipher adapters: 4 (TinkCipherAlgorithmFactory, RedisAntiReplayValidator, RedisCipherKeySessionResolver, HmacFieldSigningServiceImpl)

**Remaining gaps (2 items — LOW complexity):**
1. Keycloak support trong `OAuth2TokenExchanger` (add configurable endpoint)
2. Trusted device flow hardening (TTL enforcement via `trusted_device_set_at`)

### Related Features / Precedents
- `2026-08-05-architecture-optimization` (archived) — base architecture patterns
- `2026-08-11-auth-login-admin` (archived) — login flow + admin endpoints
- `2026-08-15-e2ee-performance-compliance` (archived) — E2EE layer (cipher/* packages)
- `2026-08-20-anonymous-login-optimization` (archived) — anonymous session + promotion flow
- `2026-08-22-api-response-i18n-standard` (archived) — i18n error messages
- `2026-08-24-erp-iam-system` (archived) — overall IAM architecture scaffold

### Integration Notes
- **Redis**: `RedisConfig.kt` — `RedisTemplate<String, Any>` (JSON serialization). `StringRedisTemplate` used in: `OtpService`, `MfaService`, `MfaRateLimitService`, `LoginRateLimitService`, `AnonymousRateLimitService`, `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`, `AnonymousSessionDataService`, `SessionPromotionService`, `DecryptionVaultService`, `RedisAntiReplayValidator`, `RedisCipherKeySessionResolver`, `MultiTierPermissionCache`, `AbstractTwoTierCache`, `IdempotencyFilter`.
- **OAuth2**: SSO gateway chain: `SsoGateway` → `HttpSsoGateway` → `SsoProviderClient`. `OAuth2TokenExchanger` handles token exchange.
- **Kafka**: Consumer: `PermissionChangedConsumer` (topic `iam.permission.changed`). [CHANGED] Producer: `KafkaEventPublisher` — `@Primary`, `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`, retry 3 attempts. Topic prefix `iam.`. Fallback: `SpringEventPublisher`.
- **Caffeine**: L1 permission cache: `CaffeinePermissionCache` → `MultiTierPermissionCache` implementing `PermissionCache` port.
- **Tink**: E2EE encryption: `TinkCipherAlgorithmFactory` + `X25519KeyExchangeServiceImpl`.
- **DLQ**: Kafka DLQ via `DeadLetterPublishingRecoverer` in `KafkaConfig.kt`.

### Suggested Approach
1. **Keycloak support**: Extend `OAuth2TokenExchanger` with configurable Keycloak endpoints. Leverage `SsoProviderClient` interface for pluggable implementation. Use `spring.security.oauth2.client.registration` + `provider` config.
2. **Trusted device hardening**: Complete TTL enforcement in `LoginHandler` using `trusted_device_set_at` from V10 migration. Compare `now()` with `trusted_device_set_at + 30 days`.
3. **Testing**: WireMock-based SSO tests via `SsoProviderClient`. PasswordPolicyService edge cases. KafkaEventPublisher integration tests.
4. **Documentation**: SecurityProperties default values. API doc updates for CAPTCHA threshold.

### Context from Confluence Images
N/A — source là file-based research artifacts.

### Delta Summary (vs pre_openspec v3 — 2026-08-20 archive)
| Section | Change Type | Details |
|---------|-------------|---------|
| Quality Score | [CHANGED] | 92 → 93 (Kafka issue resolved, completeness improved) |
| Feature Summary | [CHANGED] | ~90% → ~95% implemented (KafkaEventPublisher + IdempotencyFilter) |
| Issues | [CHANGED] | 2 → 1 issue (🟡 Kafka event RESOLVED — KafkaEventPublisher.kt implemented) |
| FR-007 | [CHANGED] | Status updated: Kafka publisher implemented with retry + backoff |
| FR-015 | [CHANGED] | IdempotencyFilter.kt added as additional idempotency mechanism |
| External Integrations | [CHANGED] | Kafka producer: `KafkaEventPublisher.kt` (NEW), Idempotency cache (NEW) |
| Section 10.3 | [CHANGED] | Detection evidence: 14 → 17 entries (Kafka events, idempotency, service tokens) |
| Section 10.5 | [CHANGED] | Added: KafkaEventPublisher, IdempotencyFilter, ServiceTokenService, ServiceAuthFilter, InternalApiController, KafkaConfig, cipher adapters |
| NFR | [NEW] | NFR-007: Kafka event publish retry policy |
| Agent Notes | [CHANGED] | File count update, new discoveries, gap list reduced from 3 to 2 |
