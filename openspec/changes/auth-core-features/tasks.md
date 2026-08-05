<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "N/A", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Non-Financial" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- self-contained: true -->
# Tasks: Auth Core Features

> **Change**: auth-core-features | **Type**: EXTEND | **Flow**: Non-Financial
> **Direction**: Layered Extension (Bottom-Up) — 4 phases

---

## Phase 1: Infrastructure (Non-Breaking Foundation)

- [x] **Task 1: Add dependencies**
  - File: [build.gradle.kts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | Action: MODIFY
  - FR: ALL — foundation for all features
  - Add: `spring-boot-starter-data-redis`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`, `dev.samstevens.totp:totp:1.7.1`, `org.passay:passay:1.6.4`

- [x] **Task 2: Flyway V2 migration**
  - File: `src/main/resources/db/migration/V2__auth_core_features.sql` | Action: NEW
  - FR: ALL — DDL for new tables and ALTER users
  - Pattern: Follow V1 pattern (Snowflake IDs, TIMESTAMPTZ, partial indexes)
  - DDL: CREATE TABLE `user_identities`, `password_policies`, `password_history` + ALTER TABLE `users` ADD 5 columns (mfa_enabled, mfa_method, totp_secret_encrypted, trusted_device_hash, password_changed_at)

- [x] **Task 3: UserEntity — add MFA/SSO columns**
  - File: [UserEntity.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | Action: MODIFY
  - FR: FR-001, FR-002, FR-005, FR-013
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - Add fields: `mfaEnabled: Boolean`, `mfaMethod: String`, `totpSecretEncrypted: String?`, `trustedDeviceHash: String?`, `passwordChangedAt: Instant?`

- [x] **Task 4: UserIdentityEntity — NEW**
  - File: `rbac/adapter/out/persistence/entity/UserIdentityEntity.kt` | Action: NEW
  - FR: FR-008 — SSO identity linking
  - Base: `SnowflakeBaseEntity` from `com.ntt.basecore.model.id`
  - Fields: userId, provider, providerSub, providerEmail, providerName, linkedAt, active
  - Constraint: UNIQUE(provider, provider_sub)

- [x] **Task 5: PasswordPolicyEntity — NEW**
  - File: `rbac/adapter/out/persistence/entity/PasswordPolicyEntity.kt` | Action: NEW
  - FR: FR-013 — domain password policy
  - Base: `SnowflakeBaseEntity` from `com.ntt.basecore.model.id`
  - Fields: domainId (unique), minLength, maxLength, requireUppercase/Lowercase/Digit/Special, minCharacterTypes, historyCount, maxAgeDays, lockoutThreshold, lockoutDurationMinutes

- [x] **Task 6: PasswordHistoryEntity — NEW**
  - File: `rbac/adapter/out/persistence/entity/PasswordHistoryEntity.kt` | Action: NEW
  - FR: FR-014 — password reuse prevention
  - Base: `SnowflakeBaseEntity` from `com.ntt.basecore.model.id`
  - Fields: userId, passwordHash, createdAt

- [x] **Task 7: New Repositories**
  - File: `rbac/adapter/out/persistence/repository/AuthCoreRepositories.kt` | Action: NEW
  - FR: FR-008, FR-013, FR-014
  - Pattern: JpaRepository interfaces following existing pattern (see UserRepository, DomainRepository)
  - Interfaces: `UserIdentityRepository`, `PasswordPolicyRepository`, `PasswordHistoryRepository`
  - Key queries:
    - `findByProviderAndProviderSub(provider, sub): UserIdentityEntity?`
    - `findAllByUserId(userId): List<UserIdentityEntity>`
    - `findByDomainId(domainId): PasswordPolicyEntity?`
    - `findByUserIdOrderByCreatedAtDesc(userId, pageable): List<PasswordHistoryEntity>`

- [x] **Task 8: SecurityProperties extend**
  - File: [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Action: MODIFY
  - FR: FR-001, FR-004, FR-006, FR-009
  - Add nested classes: `MfaProperties`, `CaptchaProperties`, `SsoProperties`
  - Extend `JwtProperties`: add `algorithm`, `privateKeyPath`, `publicKeyPath`, `keyId`

---

## Phase 2: Domain Services

- [x] **Task 9: JwtService RS256 migration**
  - File: [JwtService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | Action: MODIFY
  - FR: FR-009, FR-010, FR-011
  - Changes:
    - Replace `signingKey: SecretKey` → `keyPair: KeyPair` (RSA 2048-bit from PEM files)
    - Add `legacyKey: SecretKey?` for 7-day migration fallback
    - `signWith(keyPair.private, Jwts.SIG.RS256)` + kid header
    - `parseToken()`: try RS256 first → fallback HMAC → throw
    - NEW: `getJwks()`, `generateMfaToken()`, `parseMfaToken()`
  - Dependencies: `java.security.KeyFactory`, `java.security.spec.PKCS8EncodedKeySpec`, `X509EncodedKeySpec`

- [x] **Task 10: LoginResult sealed class**
  - File: `auth/application/LoginResult.kt` | Action: NEW
  - FR: FR-001
  - Pattern: Kotlin sealed class (Success, MfaRequired)

- [x] **Task 11: OtpService — NEW**
  - File: `auth/application/OtpService.kt` | Action: NEW
  - FR: FR-001
  - Dependencies: `StringRedisTemplate`, `SecurityProperties.mfa`
  - Methods: `generateOtp()`, `verifyOtp()`, `deleteOtp()`
  - Redis keys: `otp:{userId}:{channel}`, `otp:{userId}:{channel}:attempts`

- [x] **Task 12: TotpService — NEW**
  - File: `auth/application/TotpService.kt` | Action: NEW
  - FR: FR-002
  - Dependencies: `dev.samstevens.totp` (SecretGenerator, CodeGenerator, CodeVerifier, QrData)
  - Methods: `generateSecret()`, `generateQrUri()`, `verifyCode()`, `encryptSecret()`, `decryptSecret()`
  - ⚠️ OPEN QUESTION: Q8 — TOTP encryption: custom AES-256-GCM (recommended)

- [x] **Task 13: MfaService — NEW (orchestrator)**
  - File: `auth/application/MfaService.kt` | Action: NEW
  - FR: FR-001, FR-002, FR-003, FR-005, FR-015
  - Dependencies: OtpService, TotpService, JwtService, UserRepository, SecurityProperties
  - Methods: `initiateMfa()`, `verifyMfa()`, `setupTotp()`, `confirmTotp()`, `resendOtp()`, `updateSettings()`

- [x] **Task 14: CaptchaVerifier interface + adapters**
  - File: `auth/application/CaptchaVerifier.kt` | Action: NEW
  - FR: FR-004
  - Interface: `CaptchaVerifier { fun verify(token: String): Boolean }`
  - Adapters: `TurnstileCaptchaVerifier`, `NoopCaptchaVerifier`
  - Factory: `@Bean` based on `app.security.captcha.provider`

- [x] **Task 15: PasswordPolicyService — full Passay impl**
  - File: [PasswordPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Action: MODIFY
  - FR: FR-013, FR-014
  - Dependencies: `org.passay` (LengthRule, CharacterRule, CharacterCharacteristicsRule), `PasswordPolicyRepository`, `PasswordHistoryRepository`
  - State: `ConcurrentHashMap<Long, PasswordValidator>` cache
  - Methods: `validatePasswordStrength()`, `checkPasswordHistory()`, `changePassword()`, `getPolicy()`, `updatePolicy()`

- [x] **Task 16: SsoAdapter — full OAuth2 impl**
  - File: [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Action: MODIFY
  - FR: FR-006, FR-007, FR-008, FR-017
  - Dependencies: `RestTemplate`, `UserIdentityRepository`, `KafkaTemplate`, `SecurityProperties.sso`
  - Methods: `handleCallback()`, `getProviders()`, `linkIdentity()`, `unlinkIdentity()`
  - ⚠️ OPEN QUESTION: Q10 — default domain for JIT provisioning (use `sso.defaultDomainCode`)

---

## Phase 3: API Layer

- [x] **Task 17: AuthService.login() — MFA/CAPTCHA checkpoint**
  - File: [AuthService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Action: MODIFY
  - FR: FR-001, FR-004
  - Changes:
    - Add `captchaVerifier` and `mfaService` as constructor parameters
    - Insert CAPTCHA check before password validation (line ~91)
    - Insert MFA checkpoint after password validation (line ~97)
    - Change return type: `AuthResponse` → `LoginResult`
    - Add `LoginRequest.captchaToken?: String`
  - Source: Impact analysis — 1 caller (AuthController), 🟢 LOW risk

- [x] **Task 18: AuthController — handle LoginResult**
  - File: [AuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt) | Action: MODIFY
  - FR: FR-001, FR-013, FR-014
  - Changes:
    - `login()` response: `when(result) { is Success → 200, is MfaRequired → 200 (mfa_required) }`
    - Add `captchaToken` to `LoginRequestDto`
    - NEW: `POST /api/auth/change-password` endpoint
    - NEW: `POST /api/auth/forgot-password` endpoint

- [x] **Task 19: MFA DTOs**
  - File: `auth/adapter/in/web/dto/MfaDtos.kt` | Action: NEW
  - FR: FR-001, FR-002, FR-003
  - DTOs: `MfaVerifyRequest`, `MfaRequiredResponse`, `TotpSetupResponse`, `TotpConfirmRequest`, `MfaResendRequest`, `MfaSettingsRequest`, `MfaSettingsResponse`

- [x] **Task 20: MfaController — NEW**
  - File: `auth/adapter/in/web/MfaController.kt` | Action: NEW
  - FR: FR-001, FR-002, FR-003
  - Endpoints: 5 (verify, totp/setup, totp/confirm, resend, settings)
  - Auth: mfaToken for verify/resend, JWT for totp/settings

- [x] **Task 21: SSO DTOs**
  - File: `auth/adapter/in/web/dto/SsoDtos.kt` | Action: NEW
  - FR: FR-006, FR-007, FR-008
  - DTOs: `SsoCallbackRequest`, `SsoProviderInfo`, `SsoLinkRequest`

- [x] **Task 22: SsoController — NEW**
  - File: `auth/adapter/in/web/SsoController.kt` | Action: NEW
  - FR: FR-006, FR-007, FR-008
  - Endpoints: 4 (callback, providers, link, unlink)

- [x] **Task 23: Token DTOs**
  - File: `auth/adapter/in/web/dto/TokenDtos.kt` | Action: NEW
  - FR: FR-010, FR-011, FR-012
  - DTOs: `IntrospectionRequest`, `IntrospectionResponse`, `JwksResponse`, `RevokeSessionsResponse`

- [x] **Task 24: TokenController — NEW**
  - File: `auth/adapter/in/web/TokenController.kt` | Action: NEW
  - FR: FR-010, FR-011, FR-012
  - Endpoints: 3 (introspect, jwks, revoke-all)

---

## Phase 4: Integration & Polish

- [x] **Task 25: Auth Exceptions — NEW**
  - File: `shared/exception/AuthCoreExceptions.kt` | Action: NEW
  - FR: ALL
  - Error: 14 new exception classes extending `AuthException`
  - Pattern: Follow existing pattern from [AuthExceptions.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt)

- [x] **Task 26: GlobalExceptionHandler extend**
  - File: `shared/exception/GlobalExceptionHandler.kt` | Action: MODIFY
  - FR: ALL
  - Add `@ExceptionHandler` methods for 14 new exceptions
  - Pattern: RFC 7807 ProblemDetail (follow existing handler pattern)

- [x] **Task 27: SecurityConfig — public endpoints**
  - File: [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Action: MODIFY
  - FR: FR-001, FR-006, FR-010
  - Add permitAll: `/api/auth/mfa/verify`, `/api/auth/mfa/resend`, `/api/auth/sso/callback`, `/api/auth/sso/providers`, `/api/auth/forgot-password`, `/.well-known/jwks.json`

- [x] **Task 28: application.yml — config sections**
  - File: [application.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application.yml) | Action: MODIFY
  - FR: ALL
  - Add: `app.security.mfa.*`, `app.security.captcha.*`, `app.security.sso.*`, `app.security.jwt.algorithm/privateKeyPath/publicKeyPath/keyId`, `spring.data.redis.*`

- [x] **Task 29: JwtAuthFilter — RS256 support**
  - File: [JwtAuthFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | Action: MODIFY
  - FR: FR-009
  - Changes: JwtService.parseToken() handles RS256 internally — filter needs NO code change if JwtService API is backward compatible. Verify: `jwtService.parseToken(token)` returns `Claims` → no change needed.
  - Note: This task may be NO-OP if JwtService migration is transparent.

- [x] **Task 30: Audit logging for MFA/SSO events**
  - File: `shared/audit/AuditLogService.kt` | Action: NEW
  - FR: FR-016 — audit events for security-sensitive actions
  - Pattern: Insert to `audit_log` table (existing V1 schema)
  - Events: `MFA_SETUP`, `MFA_VERIFY_SUCCESS`, `MFA_VERIFY_FAILED`, `SSO_LOGIN`, `SSO_LINK`, `SSO_UNLINK`, `PASSWORD_CHANGED`, `FORCE_LOGOUT`
  - Integration: Called from MfaService, SsoAdapter, PasswordPolicyService, AuthService
  - Fields: userId, action, entityType, entityId, ipAddress (from request), userAgent (from request)

---

## Summary

| Phase | Tasks | Files Modified | Files New |
|-------|-------|---------------|-----------|
| Phase 1: Infrastructure | 8 | 3 (UserEntity, SecurityProperties, build.gradle.kts) | 5 (V2 migration, 3 entities, repositories) |
| Phase 2: Services | 8 | 3 (JwtService, PasswordPolicyService, SsoAdapter) | 5 (LoginResult, OtpService, TotpService, MfaService, CaptchaVerifier) |
| Phase 3: API | 8 | 2 (AuthService, AuthController) | 6 (MfaController, SsoController, TokenController, 3 DTO files) |
| Phase 4: Integration | 6 | 3 (GlobalExceptionHandler, SecurityConfig, application.yml) | 2 (AuthCoreExceptions, AuditLogService) + 1 verify-only |
| **Total** | **30** | **11** | **18** |

FR Traceability: 17/17 ✅ (FR-001→FR-017 all covered)
