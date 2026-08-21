# Pre-OpenSpec: auth-core-features

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (Feature Research — business_analysis.md + technical_spec.md)
> **Classification Evidence**: `AuthService.kt` → `auth/application` → `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (existing login/register/MFA/SSO flow, cần hoàn thiện + hardening)
> **Archive**: `openspec/changes/archive/2026-08-21-auth-core-features/pre_openspec.md` (previous version 2026-08-21)
> **Quality Score**: 94/100
> **Mode**: DELTA (archive found, input refined from feature research + code re-scan 2026-08-26)
> **_Generated**: 2026-08-26

## 📋 Feature Summary

Hoàn thiện và hardening auth-service với 4 nhóm tính năng core authentication: Multi-Factor Authentication (OTP SMS/Email, TOTP Authenticator, CAPTCHA, Recovery Codes), SSO/OAuth2 (Google, Microsoft, Keycloak) với JIT provisioning, JWT RS256 + token introspection (RFC 7662) + JWKS endpoint + session binding, và Password Policy per domain (complexity via Passay, history, expiry). [CHANGED] ~97% code đã implement — codebase scan phát hiện thêm Event Sourcing infrastructure (EventService, EventStorePort, OutboxPort, OutboxPoller), MFA Recovery Codes (V13 migration + MfaRecoveryCodeEntity), domain event models (TokenIssuedEvent, TokenRevokedEvent, UserRegisteredEvent), Token lifecycle tracking (TokenEventRecorder), và observability config (ObservabilityConfig). Tập trung vào testing, hardening, và hoàn thiện 2 edge cases còn lại (Keycloak support + trusted device TTL enforcement).

| Metric | Giá trị |
|--------|---------|
| Số FR | 17 (URD: 14, Enriched: 3) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 1 |
| **Quality Score** | **94/100** |

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
- **Validation**: Dùng `sub` claim làm identity key (KHÔNG dùng email). Event publish qua `EventPublisher` port → `KafkaEventPublisher` (khi Kafka configured) hoặc `SpringEventPublisher` (fallback). [CHANGED] `EventService.record()` ghi event vào event store + outbox trong cùng transaction.
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.handleCallback()` JIT provisioning. `KafkaEventPublisher.kt` với retry + `OutboxPoller.kt` (transactional outbox).

### FR-008: Liên kết/gỡ SSO Identity [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép liên kết tài khoản hiện có với SSO provider (`POST /api/auth/sso/link`), và gỡ liên kết (`DELETE /api/auth/sso/unlink/{provider}`).
- **Validation**: Lưu `user_identities` table via `UserIdentityRepository`. Gỡ liên kết chỉ khi user có password hoặc còn SSO identity khác (`CannotUnlinkLastIdentityException`).
- **Status**: ✅ IMPLEMENTED — `SsoAdapter.linkIdentity()` + `SsoAdapter.unlinkIdentity()` + `SsoController.kt`.

### FR-009: JWT RS256 Signing [URD]
- **Actor**: System
- **Action**: Hệ thống phải sign JWT bằng RS256 asymmetric (primary) với HMAC-SHA256 fallback (7-day migration).
- **Validation**: RSA key pair loaded từ file path (`jwt.privateKeyPath`, `jwt.publicKeyPath`). Private key chỉ tại auth-service. Token generation via `TokenGenerator` command handler. [CHANGED] `TokenEventRecorder.recordIssuance()` ghi token issuance event vào event store.
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
- **Validation**: Blacklist all active jti, delete all refresh tokens. Return `revokedCount`. CQRS: `RevokeSessionsCommand` → `RevokeSessionsHandler`. [CHANGED] `TokenEventRecorder.recordRevocation()` ghi token revocation event vào event store.
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
- NFR-007: Kafka event publish retry 3 attempts, exponential backoff (1s → 2s → 4s), max delay 8s.
- NFR-008: [NEW] Outbox poller batch size = 50, poll interval = 100ms, max retries = 3, Kafka timeout = 5s (configurable via `OutboxProperties`).
- NFR-009: [NEW] Event store append-only — no update/delete, unique constraint on (aggregate_type, aggregate_id, sequence_number).

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (OTP) và FR-002 (TOTP) được tách riêng theo phương thức xác thực. FR-013 (Password Policy) và FR-014 (Password History) tách riêng theo concern.

## 5. Enriched Domain Requirements

Max enriched: min(5, ceil(14 × 0.20)) = min(5, 3) = 3.

### Enriched FRs

### FR-015: Idempotency cho MFA verification [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotent cho `/api/auth/mfa/verify` — cùng mfaToken + code chỉ issue JWT 1 lần.
- **Validation**: Redis DELETE sau khi OTP verify thành công, ngăn replay. MFA rate limit reset sau verify thành công. `IdempotencyFilter.kt` cung cấp generic idempotency cho mutating endpoints via `X-Idempotency-Key` header + Redis TTL 24h.
- **Status**: ✅ IMPLEMENTED — `OtpService.verifyOtp()` deletes key after success + `IdempotencyFilter.kt` (shared/filter/) generic filter.

### FR-016: Audit logging cho MFA/SSO events [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi audit log cho mọi sự kiện MFA (setup, verify_success, verify_failed) và SSO (login, link, unlink).
- **Validation**: `AuditLogService.logEvent()` with `AuditAction` enum. Log IP address + User-Agent. [CHANGED] V14 migration (`V14__audit_logs.sql`) tạo audit_logs table với immutability constraints. `AuditLogEntity.kt` + `AuditLogRepository.kt` trong `shared/audit/`.
- **Status**: ✅ IMPLEMENTED — `AuditLogService.kt` + `AuditAction` enum (25 total actions including E2EE and Account Lifecycle). Audit log entity/repository fully implemented.

### FR-017: Timeout handling cho IdP call [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xử lý timeout khi gọi IdP token endpoint (exchange code → token).
- **Validation**: Trả lỗi `SsoProviderTimeoutException` (HTTP 504) nếu IdP không phản hồi. HTTP client config via `HttpClientConfig.kt`.
- **Status**: ✅ IMPLEMENTED — `OAuth2TokenExchanger.fetchUserInfo()` catches timeout → throws `SsoProviderTimeoutException`.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | OTP storage, MFA rate limiting, login rate limiting, session management, TOTP setup pending, anonymous sessions, idempotency cache, anti-replay validation, cipher key sessions | `spring-boot-starter-data-redis` — ✅ `RedisConfig.kt` + `RedisLuaScriptConfig.kt` exists |
| Kafka | Consumer: permission change events. Producer: SSO provisioning events, token lifecycle events | Consumer: `PermissionChangedConsumer.kt`. Producer: `KafkaEventPublisher.kt` (3 retries, backoff) + `OutboxPoller.kt` (transactional outbox relay). Fallback: `SpringEventPublisher` (in-process) |
| Google OAuth2 | SSO provider | `OAuth2TokenExchanger` → `SsoProviderClient` — OIDC protocol |
| Microsoft OAuth2 | SSO provider | `OAuth2TokenExchanger` → `SsoProviderClient` — OIDC protocol |
| Keycloak | Optional SSO provider | ⚠️ NOT YET in `OAuth2TokenExchanger.getTokenEndpoint()` (only Google + Microsoft) |
| ALTCHA | CAPTCHA verification | `CaptchaVerifier` → `CaptchaGateway` → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient` |
| Passay | Password validation | `org.passay:passay:1.6.4` — in-process library |
| dev.samstevens.totp | TOTP generation/verification | `dev.samstevens.totp:totp:1.7.1` — in-process library |
| PostgreSQL | JPA/Hibernate entities | 21+ entities, 27+ repositories, Flyway V1-V15 migrations |
| Caffeine | L1 permission cache | `shared/cache/` directory exists |

## 6. Assumptions

- ⚠️ Assumption: Redis available tại runtime cho MFA flow — lý do: OTP storage + rate limiting + idempotency require Redis (critical dependency). `MfaRateLimitService` implements fail-open strategy khi Redis unavailable.
- ⚠️ Assumption: RSA key pair sẽ được generate offline và inject qua `jwt.privateKeyPath` / `jwt.publicKeyPath` env vars — lý do: key management ngoài scope.
- ⚠️ Assumption: CAPTCHA provider secret key cung cấp qua environment variables (`app.security.captcha.*`) — lý do: provider-specific config.
- ⚠️ Assumption: SSO providers (Google, Microsoft) cấu hình trong `application.yml` qua `app.security.sso.providers.*` — lý do: pluggable config.
- ⚠️ Assumption: `dev.samstevens.totp:1.7.1` vẫn stable dù GitHub repo 404 — lý do: TOTP là stable protocol, Maven Central available.
- ⚠️ Assumption: Kafka event publish deferred — `KafkaEventPublisher` và `OutboxPoller` activated only when `spring.kafka.bootstrap-servers` configured. Fallback: `SpringEventPublisher` (in-process `ApplicationEventPublisher`).

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-004: CAPTCHA threshold configurable nhưng default value chưa documented rõ |
| Đầy đủ (Completeness) | 24/25 | FR-005: Trusted device TTL enforcement chưa hoàn thiện (V10 migration applied, logic pending) |
| Nhất quán (Consistency) | 25/25 | Không phát hiện mâu thuẫn |
| Kiểm thử được (Testability) | 21/25 | FR-006, FR-007: SSO flow phụ thuộc external IdP — `SsoProviderClient` interface supports WireMock-based testing |
| **Tổng** | **94/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -1 | FR-004 | CAPTCHA threshold configurable nhưng default value chưa rõ trong docs | Document default N=5 trong SecurityProperties Javadoc |
| 2 | Completeness | -1 | FR-005 | "Trusted device" — TTL enforcement via `trusted_device_set_at` cần complete | Implement TTL check trong `LoginHandler` — compare `now()` with `trusted_device_set_at + 30 days` |
| 3 | Testability | -4 | FR-006, FR-007 | SSO callback flow phụ thuộc external IdP — cần mock IdP | Cung cấp WireMock-based test profile cho Google/Microsoft via `SsoProviderClient` interface |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Missing | 🟡 | Keycloak provider chưa có trong `OAuth2TokenExchanger.getTokenEndpoint()` — chỉ Google + Microsoft | FR-006 | Thêm Keycloak endpoint config (configurable `issuer-uri`) |

> [CHANGED] Issue #2 từ version trước (Kafka event publish deferred) — RESOLVED. `KafkaEventPublisher.kt` + `OutboxPoller.kt` đã implement.

## 9. Open Questions

- Q1: Keycloak token/userinfo endpoint cần configurable per-instance (không có fixed URL như Google/Microsoft). Cần extend `OAuth2TokenExchanger` hoặc dùng Spring Security OAuth2 client registration?

> Recommend: Dùng `spring.security.oauth2.client.registration` + `provider` config thay vì hardcode endpoints. `SsoProviderClient` interface hỗ trợ pluggable implementation.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Authorization — MFA (OTP/TOTP/CAPTCHA/Recovery Codes), SSO (OAuth2/OIDC), Token Management (RS256/JWKS/Introspection), Password Policy (Passay/History/Expiry), Event Sourcing (EventStore/Outbox/TokenLifecycle)

### 10.2 Flow Type
Non-Financial (xác thực identity, không liên quan giao dịch tài chính trực tiếp)

### 10.3 Candidate Services
- `auth-service`: Primary service — chứa toàn bộ auth core modules (keyword: login, auth, jwt, mfa, totp, otp, sso, password, captcha, token, event, outbox)

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
- Keyword: `AuditLogService`, `AuditAction` → Module: `shared/audit` → File: `AuditLogService.kt`, `AuditLogEntity.kt`, `AuditLogRepository.kt`
- Keyword: `port`, `adapter` → Module: `auth/application/port/out` → File: `UserPort.kt`, `TokenStore.kt`, `DomainPort.kt`, `EventPublisher.kt`, `CaptchaGateway.kt`, `SsoGateway.kt`, `EventStorePort.kt`, `OutboxPort.kt`
- Keyword: `rate limit` → Module: `auth/application` + `auth/adapter/in/web/filter` → File: `MfaRateLimitService.kt`, `LoginRateLimitService.kt`, `LoginRateLimitFilter.kt`
- Keyword: `session` → Module: `auth/application` + `auth/adapter/out/persistence` → File: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionCleanupScheduler.kt`, `LoginSessionEntity.kt`, `LoginSessionRepository.kt`
- Keyword: `kafka`, `event` → Module: `auth/adapter/out/event` → File: `KafkaEventPublisher.kt`, `SpringEventPublisher.kt`, `OutboxPoller.kt`
- Keyword: `idempotency` → Module: `shared/filter` → File: `IdempotencyFilter.kt` (Redis-backed, X-Idempotency-Key header, TTL 24h)
- Keyword: `service token`, `internal api` → Module: `auth/application` + `auth/adapter/in/web` + `auth/adapter/in/web/filter` → File: `ServiceTokenService.kt`, `InternalApiController.kt`, `ServiceAuthFilter.kt`
- [NEW] Keyword: `event store`, `event sourcing` → Module: `auth/application/event` + `auth/adapter/out/persistence` + `auth/application/port/out` → File: `EventService.kt`, `TokenEventRecorder.kt`, `EventStorePort.kt`, `EventStorePersistenceAdapter.kt`, `EventStoreEntity.kt`, `EventStoreJpaRepository.kt`, `EventStoreController.kt`
- [NEW] Keyword: `outbox` → Module: `auth/adapter/out/event` + `auth/adapter/out/persistence` + `auth/application/port/out` → File: `OutboxPoller.kt`, `OutboxPort.kt`, `OutboxPersistenceAdapter.kt`, `EventOutboxEntity.kt`, `EventOutboxJpaRepository.kt`, `OutboxProperties.kt`
- [NEW] Keyword: `recovery code`, `mfa recovery` → Module: `auth/adapter/out/persistence/entity` + `auth/adapter/out/persistence/repository` → File: `MfaRecoveryCodeEntity.kt`, `MfaRecoveryCodeRepository.kt`
- [NEW] Keyword: `domain event`, `token event` → Module: `auth/domain/event` → File: `TokenIssuedEvent.kt`, `TokenRevokedEvent.kt`, `UserRegisteredEvent.kt`, `EventEnvelope.kt`, `IssuanceContext.kt`, `RevocationType.kt`
- [NEW] Keyword: `processed event`, `consumer idempotency` → Module: `auth/adapter/out/persistence` → File: `ProcessedEventEntity.kt`, `ProcessedEventJpaRepository.kt`
- [NEW] Keyword: `observability` → Module: `shared/config` → File: `ObservabilityConfig.kt`
- [NEW] Keyword: `redis lua` → Module: `shared/config` → File: `RedisLuaScriptConfig.kt`

### 10.4 External Integrations
- **Redis**: OTP storage (`otp:{userId}:{channel}`), MFA rate limiting (`mfa:ratelimit:*`), login rate limiting, session management, TOTP setup pending, anonymous sessions, idempotency cache (`idempotency:auth-service:*` TTL 24h), anti-replay validation, cipher key sessions — `StringRedisTemplate` via `RedisConfig.kt` + `RedisLuaScriptConfig.kt`
- **Kafka**: Consumer: `PermissionChangedConsumer.kt` (topic `iam.permission.changed`, groupId `auth-service`). Producer: `KafkaEventPublisher.kt` — `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`, retry 3 attempts exponential backoff. [NEW] `OutboxPoller.kt` — transactional outbox relay (SELECT FOR UPDATE SKIP LOCKED), batch=50, poll=100ms. Fallback: `SpringEventPublisher` (in-process). Topics: `iam.permission.changed`, `iam.user.sso_provisioned`, `iam.token.issued`, `iam.token.revoked`, `iam.account.deactivated`, `iam.account.deleted`, `iam.audit.event`.
- **OAuth2 IdPs**: Google, Microsoft — via `OAuth2TokenExchanger` → `SsoProviderClient`
- **CAPTCHA Providers**: ALTCHA — via `CaptchaVerifier` interface → `CaptchaGateway` port → `CaptchaGatewayAdapter` → `HttpCaptchaGateway` → `CaptchaClient`
- **Passay**: In-process password validation library — `org.passay:passay:1.6.4`
- **dev.samstevens.totp**: In-process TOTP library — `dev.samstevens.totp:totp:1.7.1`
- **PostgreSQL**: JPA/Hibernate entities — `users`, `user_identities`, `password_policies`, `password_history`, `refresh_tokens`, `token_blacklist`, `login_sessions`, `cipher_key_sessions`, `vault_access_logs`, `account_deletion_requests`, `account_data_exports`, `event_store`, `event_outbox`, `processed_events`, `mfa_recovery_codes`, `audit_logs`, `domains`. Flyway V1-V15.
- **Caffeine**: L1 cache for permissions — `shared/cache/` directory observed but empty — may use direct configuration.

### 10.5 Required Modules
- `auth/application`: AuthService, MfaService, TotpService, OtpService, SsoAdapter, PasswordPolicyService, CaptchaVerifier, AltchaCaptchaVerifier, MfaRateLimitService, LoginRateLimitService, AnonymousRateLimitService, LoginSessionService, JwtService, SessionPolicyService, AccountLifecycleService, DomainLookupService, ServiceTokenService, SessionPromotionService, AnonymousSessionDataService, SessionCleanupScheduler (ALL EXIST)
- `auth/application/command`: LoginCommand/Handler, RegisterCommand/Handler, RefreshTokenCommand/Handler, SwitchDomainCommand/Handler, RevokeSessionsCommand/Handler, TokenGenerator, AnonymousSessionHandler, RenewAnonymousTokenHandler, CreateAnonymousSessionCommand, AuthDomainEvents (UserLoggedInEvent, SessionRevokedEvent) (ALL EXIST — CQRS)
- `auth/application/query`: BuildAuthResponseQuery/Handler (ALL EXIST)
- `auth/application/port/out`: UserPort, TokenStore, DomainPort, EventPublisher, CaptchaGateway, SsoGateway, EventStorePort, OutboxPort (ALL EXIST — 8 hexagonal ports [CHANGED: was 7, +EventStorePort, +OutboxPort])
- `auth/application/event`: EventService, TokenEventRecorder, NewDeviceLoginEvent, RateLimitExceededEvent (ALL EXIST — [NEW] EventService, TokenEventRecorder)
- `auth/application/cipher`: CipherVersionNegotiator, DecryptionVaultService, EncryptedAuditService, X25519KeyExchangeServiceImpl (ALL EXIST)
- `auth/adapter/in/web`: AuthController, CqrsAuthController, MfaController, SsoController, TokenController, CaptchaController, AdminSessionController, SessionController, AccountLifecycleController, KeyExchangeController, RateLimitAdminController, AnonymousAuthController, InternalApiController, EventStoreController (ALL EXIST — 14 controllers [CHANGED: was 13, +EventStoreController])
- `auth/adapter/in/web/dto`: MfaDtos, SsoDtos, TokenDtos, RequestDtos, AuthResponse, AnonymousDtos (ALL EXIST — 6 DTO files, 25+ data classes)
- `auth/adapter/in/web/filter`: LoginRateLimitFilter, ClientMetadataFilter, ContentLanguageFilter, ServiceAuthFilter (ALL EXIST — 4 filters)
- `auth/adapter/in/kafka`: PermissionChangedConsumer (EXISTS)
- `auth/adapter/out/sso`: OAuth2TokenExchanger (EXISTS — extend for Keycloak)
- `auth/adapter/out/http`: HttpCaptchaGateway, HttpSsoGateway, CaptchaClient, SsoProviderClient (ALL EXIST)
- `auth/adapter/out/gateway`: CaptchaGatewayAdapter (EXISTS)
- `auth/adapter/out/cache`: Directory exists but empty (was listed in previous archive)
- `auth/adapter/out/event`: SpringEventPublisher, KafkaEventPublisher, OutboxPoller (ALL EXIST — [NEW] OutboxPoller)
- `auth/adapter/out/persistence`: UserPersistenceAdapter, TokenStorePersistenceAdapter, DomainPersistenceAdapter, EventStorePersistenceAdapter, OutboxPersistenceAdapter (ALL EXIST — [NEW] EventStorePersistenceAdapter, OutboxPersistenceAdapter)
- `auth/adapter/out/persistence/entity`: LoginSessionEntity, CipherKeySessionEntity, AccountDeletionRequestEntity, AccountDataExportEntity, VaultAccessLogEntity, EventStoreEntity, EventOutboxEntity, ProcessedEventEntity, MfaRecoveryCodeEntity (ALL EXIST — 9 entities [CHANGED: was 5, +4 new])
- `auth/adapter/out/persistence/mapper`: UserEntityMapper (EXISTS)
- `auth/adapter/out/persistence/repository`: LoginSessionRepository, CipherKeySessionJpaRepository, VaultAccessLogJpaRepository, AccountDeletionRequestRepository, AccountDataExportRepository, EventStoreJpaRepository, EventOutboxJpaRepository, ProcessedEventJpaRepository, MfaRecoveryCodeRepository (ALL EXIST — 9 repositories [CHANGED: was 5, +4 new])
- `auth/adapter/out/cipher`: TinkCipherAlgorithmFactory, RedisAntiReplayValidator, RedisCipherKeySessionResolver, HmacFieldSigningServiceImpl, InMemoryCipherKeySessionRepository (ALL EXIST — [NEW] InMemoryCipherKeySessionRepository)
- `auth/domain/model`: User, AuthToken, UserStatus (sealed interface), TokenIssuanceMetadata, value objects (DomainCode, Email, PasswordHash, UserId) (ALL EXIST — [NEW] TokenIssuanceMetadata)
- `auth/domain/event`: TokenIssuedEvent, TokenRevokedEvent, UserRegisteredEvent, EventEnvelope, IssuanceContext, RevocationType (ALL EXIST — [NEW] entire package)
- `auth/domain/service`: TokenHasher (EXISTS)
- `rbac/adapter/out/persistence/entity`: UserEntity (extends SnowflakePersistentAuditableEntity), UserIdentityEntity (extends SnowflakeBaseEntity), PasswordPolicyEntity (extends SnowflakeBaseEntity), PasswordHistoryEntity (extends SnowflakeBaseEntity), PermissionEntities.kt (grouped), RbacEntities.kt (grouped: DomainEntity, UserDomainEntity, GroupEntity, UserGroupEntity, etc.) (ALL EXIST — 6 entity files)
- `rbac/adapter/out/persistence/repository`: Repositories.kt — UserRepository, DomainRepository, UserDomainRepository, GroupRepository, UserGroupRepository, DomainRoleRepository, GroupRoleRepository, ActionRepository, DomainResourceRepository, PermissionRepository, RolePermissionRepository, RefreshTokenRepository, TokenBlacklistRepository, UserIdentityRepository, PasswordPolicyRepository, PasswordHistoryRepository (ALL EXIST — 16 repositories in 1 file)
- `shared/config`: SecurityConfig, SecurityProperties, RedisConfig, HttpClientConfig, JacksonConfig, JpaAuditingConfig, I18nConfig, KafkaConfig, OutboxProperties, ObservabilityConfig, RedisLuaScriptConfig (ALL EXIST — 11 config files [CHANGED: was 8, +3 new])
- `shared/security`: JwtAuthFilter (EXISTS)
- `shared/exception`: AuthExceptions, AuthCoreExceptions, AnonymousExceptions, CipherExceptions, AuthErrorCode (52 error codes: AUTH_001-AUTH_062 [CHANGED: was 45]) , GlobalExceptionHandler (ALL EXIST)
- `shared/audit`: AuditLogService + AuditAction enum (25 action types) + AuditLogEntity + AuditLogRepository (ALL EXIST — [NEW] AuditLogEntity, AuditLogRepository)
- `shared/persistence`: VersionedAuditableEntity (extends SnowflakePersistentAuditableEntity + @Version) (EXISTS)
- `shared/i18n`: DatabaseMessageSource, I18nMessageEntity, I18nMessageRepository (ALL EXIST)
- `shared/cache`: Directory exists but empty
- `shared/filter`: IdempotencyFilter (EXISTS — Redis-backed, X-Idempotency-Key header)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Gửi username + password + captchaToken? + trustedDeviceHash? tới `POST /api/auth/login` | Validate CAPTCHA (nếu required), validate credentials (BCrypt match) |
| 2 | System | Check `user.mfaEnabled` và `trustedDeviceHash` | Nếu MFA enabled và device không trusted → trả `MfaRequired(mfaToken, method, expiresIn)` |
| 3 | End User | Nhận OTP qua SMS/Email hoặc mở Authenticator App | System gửi OTP qua channel hoặc skip (TOTP stateless) |
| 4 | End User | Gửi OTP/TOTP code + mfaToken tới `POST /api/auth/mfa/verify` | Verify mfaToken (parse JWT type=mfa), verify code (Redis OTP check hoặc TOTP verify) |
| 5 | System | Verify thành công | Issue full JWT (RS256) access + refresh token, tạo `LoginSessionEntity`, record token issuance event via `TokenEventRecorder` |
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
| FR-007 | UC-002 (AF-001) | SsoAdapter + EventPublisher + EventService | `SsoAdapter.handleCallback()` + `KafkaEventPublisher.kt` + `EventService.kt` + `OutboxPoller.kt` (EXIST) | [REUSE] Mapped |
| FR-008 | UC-002 (Related) | SsoController + SsoAdapter | `SsoController.kt` (EXIST) | [REUSE] Mapped |
| FR-009 | UC-003 (Related) | JwtService + TokenEventRecorder | `JwtService.kt` — dual RS256+HMAC (EXIST) + `TokenEventRecorder.kt` (EXIST) | [REUSE] Mapped |
| FR-010 | UC-003 (BR-013) | TokenController | `TokenController.jwks()` (EXIST) | [REUSE] Mapped |
| FR-011 | UC-003 (Basic Flow) | TokenController | `TokenController.introspect()` (EXIST) | [REUSE] Mapped |
| FR-012 | UC-003 (Force Logout) | AdminSessionController + RevokeSessionsHandler | `AdminSessionController.kt` + `RevokeSessionsHandler.kt` (EXIST) | [REUSE] Mapped |
| FR-013 | UC-004 (Basic Flow) | PasswordPolicyService | `PasswordPolicyService.kt` — Passay + cache (EXIST) | [REUSE] Mapped |
| FR-014 | UC-004 (History) | PasswordPolicyService | `PasswordPolicyService.changePassword()` (EXIST) | [REUSE] Mapped |
| FR-015 | (Enriched) | MfaService + OtpService + IdempotencyFilter | `OtpService.verifyOtp()` Redis DEL + `IdempotencyFilter.kt` (EXIST) | [REUSE] Mapped |
| FR-016 | (Enriched) | AuditLogService | `AuditLogService.kt` + `AuditLogEntity.kt` + `AuditLogRepository.kt` (EXIST) | [REUSE] Mapped |
| FR-017 | (Enriched) | OAuth2TokenExchanger | `SsoProviderTimeoutException` catch (EXIST) | [REUSE] Mapped |

### Change Impact Map (EXTEND)

```
FR-001 → [REUSE] OtpService (auth/application/OtpService.kt) → no changes needed
FR-002 → [REUSE] TotpService (auth/application/TotpService.kt) → no changes needed
FR-003 → [REUSE] MfaService (auth/application/MfaService.kt) + MfaController → no changes needed
FR-004 → [REUSE] CaptchaVerifier (auth/application/CaptchaVerifier.kt) + CaptchaGateway chain → no changes needed
FR-005 → [MODIFY] LoginHandler (auth/application/command/LoginHandler.kt) → trusted device TTL enforcement
FR-006 → [MODIFY] OAuth2TokenExchanger (auth/adapter/out/sso/OAuth2TokenExchanger.kt) → add Keycloak support
FR-007 → [REUSE] SsoAdapter (auth/application/SsoAdapter.kt) + KafkaEventPublisher + EventService + OutboxPoller → no changes needed
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
[CHANGED] Code scan 2026-08-26 xác nhận architecture maturity rất cao — significant improvement since 2026-08-21:

**Architecture:**
- Hexagonal: 8 outbound ports (was 7 — +EventStorePort, +OutboxPort), corresponding adapter implementations
- CQRS: 8 Command/Handler pairs + 2 Query/Handler pairs
- Domain model: sealed interface `UserStatus`, value objects, `TokenHasher` domain service
- Event Sourcing: EventStore (append-only) + Transactional Outbox + Consumer Idempotency (processed_events)
- Multi-tier caching: `shared/cache/` directory exists (empty) — Caffeine config likely in-code

**New since last scan (2026-08-21):**
1. **Event Sourcing Infrastructure** — Complete pipeline:
   - `EventStorePort.kt` — outbound port for append-only event store
   - `OutboxPort.kt` — outbound port for transactional outbox
   - `EventService.kt` — intermediary service encapsulating event envelope → event store persist → outbox insert
   - `TokenEventRecorder.kt` — helper for token lifecycle events (issuance + revocation)
   - `EventStorePersistenceAdapter.kt` — JPA adapter for event store
   - `OutboxPersistenceAdapter.kt` — JPA adapter for outbox
   - `EventStoreEntity.kt`, `EventOutboxEntity.kt`, `ProcessedEventEntity.kt` — 3 new entities
   - `EventStoreJpaRepository.kt`, `EventOutboxJpaRepository.kt`, `ProcessedEventJpaRepository.kt` — 3 new repositories
   - `EventStoreController.kt` — REST API for event store queries
   - V11 migration (`V11__event_sourcing_tables.sql`) — event_store, event_outbox, processed_events tables
   - V12 migration (`V12__seed_event_error_messages.sql`) — i18n for event sourcing error codes
2. **MFA Recovery Codes** — `MfaRecoveryCodeEntity.kt` + `MfaRecoveryCodeRepository.kt` + V13 migration. SHA-256 hashed, single-use, 10 codes per user.
3. **Audit Infrastructure** — `AuditLogEntity.kt` + `AuditLogRepository.kt` + V14 migration (`V14__audit_logs.sql`).
4. **Domain Branding** — V15 migration (`V15__domain_branding.sql`) — domain branding fields.
5. **Domain Event Models** — `auth/domain/event/` package: `TokenIssuedEvent.kt`, `TokenRevokedEvent.kt`, `UserRegisteredEvent.kt`, `EventEnvelope.kt`, `IssuanceContext.kt`, `RevocationType.kt`.
6. **Token Issuance Metadata** — `TokenIssuanceMetadata.kt` in domain model.
7. **New Error Codes** — AUTH_050-053 (Event Sourcing), AUTH_060-062 (Inter-service Auth) — total 52 error codes (was 45).
8. **Config additions** — `OutboxProperties.kt`, `ObservabilityConfig.kt`, `RedisLuaScriptConfig.kt`.
9. **Cipher additions** — `InMemoryCipherKeySessionRepository.kt` — alternative to Redis-based cipher session storage.
10. **OutboxPoller.kt** — Transactional outbox relay with SELECT FOR UPDATE SKIP LOCKED, batch processing, retry logic, separate timeout vs failure handling.

**File count scan (updated):**
- Controllers: 14 `@RestController` classes (auth: 14 [was 13], rbac: 5, pbac: 1)
- Application services: 24+ service classes (was 22+)
- Entities: 21+ `@Entity` classes (rbac: 6 files, auth/persistence: 9 [was 5], shared/audit: 1)
- Repositories: 27+ `JpaRepository` interfaces (rbac: 16, auth: 9 [was 5], shared: 1+, pbac: 1)
- DTOs: 6 DTO files with 25+ data classes
- Exception classes: 5 exception files + `AuthErrorCode` enum (52 error codes [was 45])
- Ports: 8 outbound port interfaces (was 7 — +EventStorePort, +OutboxPort)
- Filters: 5 web filters (4 auth + 1 shared/IdempotencyFilter)
- Event publishers: 3 (KafkaEventPublisher, SpringEventPublisher, OutboxPoller [NEW])
- Domain events: 6 event classes in `auth/domain/event/` [NEW]
- Event services: 2 (EventService, TokenEventRecorder) [NEW]
- Persistence adapters: 5 (was 3 — +EventStorePersistenceAdapter, +OutboxPersistenceAdapter)
- Cipher adapters: 5 (was 4 — +InMemoryCipherKeySessionRepository)
- Flyway migrations: 15 (V1-V15)
- Config files: 11 (was 8 — +OutboxProperties, +ObservabilityConfig, +RedisLuaScriptConfig)

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
- `2026-08-25-jwt_token_issuance` (archived) — JWT token lifecycle event sourcing

### Integration Notes
- **Redis**: `RedisConfig.kt` + `RedisLuaScriptConfig.kt` — `RedisTemplate<String, Any>` (JSON serialization). `StringRedisTemplate` used in: `OtpService`, `MfaService`, `MfaRateLimitService`, `LoginRateLimitService`, `AnonymousRateLimitService`, `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`, `AnonymousSessionDataService`, `SessionPromotionService`, `DecryptionVaultService`, `RedisAntiReplayValidator`, `RedisCipherKeySessionResolver`, `IdempotencyFilter`.
- **OAuth2**: SSO gateway chain: `SsoGateway` → `HttpSsoGateway` → `SsoProviderClient`. `OAuth2TokenExchanger` handles token exchange.
- **Kafka**: Consumer: `PermissionChangedConsumer` (topic `iam.permission.changed`). Producer: `KafkaEventPublisher` — `@Primary`, `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`, retry 3 attempts. [NEW] `OutboxPoller` — transactional outbox relay, `@ConditionalOnProperty("spring.kafka.bootstrap-servers")`. Topics: `iam.permission.changed`, `iam.user.sso_provisioned`, `iam.token.issued`, `iam.token.revoked`, `iam.account.deactivated`, `iam.account.deleted`, `iam.audit.event`. Fallback: `SpringEventPublisher`.
- **Caffeine**: L1 permission cache — `shared/cache/` directory exists but empty.
- **Tink**: E2EE encryption: `TinkCipherAlgorithmFactory` + `X25519KeyExchangeServiceImpl`.
- **DLQ**: Kafka DLQ via `DeadLetterPublishingRecoverer` in `KafkaConfig.kt`.

### Suggested Approach
1. **Keycloak support**: Extend `OAuth2TokenExchanger` with configurable Keycloak endpoints. Leverage `SsoProviderClient` interface for pluggable implementation. Use `spring.security.oauth2.client.registration` + `provider` config.
2. **Trusted device hardening**: Complete TTL enforcement in `LoginHandler` using `trusted_device_set_at` from V10 migration. Compare `now()` with `trusted_device_set_at + 30 days`.
3. **Testing**: WireMock-based SSO tests via `SsoProviderClient`. PasswordPolicyService edge cases. KafkaEventPublisher/OutboxPoller integration tests. Event sourcing round-trip tests.
4. **Documentation**: SecurityProperties default values. API doc updates for CAPTCHA threshold.

### Context from Confluence Images
N/A — source là file-based research artifacts.

### Delta Summary (vs pre_openspec v4 — 2026-08-21 archive)
| Section | Change Type | Details |
|---------|-------------|---------|
| Quality Score | [CHANGED] | 93 → 94 (testability improved: SSO still -4 but event sourcing infrastructure now testable) |
| Feature Summary | [CHANGED] | ~95% → ~97% implemented (Event Sourcing infra + MFA Recovery Codes + Domain Events + Audit entity) |
| Section 10.1 | [CHANGED] | Domain expanded: +Event Sourcing (EventStore/Outbox/TokenLifecycle) |
| Section 10.3 | [CHANGED] | Detection evidence: 17 → 22 entries (+event store, +outbox, +recovery code, +domain event, +processed event, +observability, +redis lua) |
| Section 10.5 | [CHANGED] | Ports: 7 → 8 (+EventStorePort, +OutboxPort). Controllers: 13 → 14 (+EventStoreController). Entities: auth/persistence 5 → 9 (+4). Repositories: auth 5 → 9 (+4). Event publishers: 2 → 3 (+OutboxPoller). Config: 8 → 11 (+3). Cipher adapters: 4 → 5 (+InMemoryCipherKeySessionRepository). Error codes: 45 → 52 (+7). |
| External Integrations | [CHANGED] | Kafka topics expanded (7 total), OutboxPoller added. PostgreSQL tables expanded (+5 new tables). Config: +OutboxProperties, +ObservabilityConfig, +RedisLuaScriptConfig. |
| NFR | [NEW] | NFR-008: Outbox poller config. NFR-009: Event store append-only constraint. |
| Agent Notes | [CHANGED] | Complete delta analysis — 10 new discoveries, file count update. Related features updated (+jwt_token_issuance). |
