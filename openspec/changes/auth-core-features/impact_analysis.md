# Impact Analysis: auth-core-features — Completion & Hardening

> **Generated**: 2026-08-19 (v2) | **Classification**: EXTEND | **Flow**: Non-Financial
> **Scope**: 3 functional gaps + integration tests + edge case hardening
> **Prior**: v1 (2026-08-05) covered initial build. v2 focuses on remaining ~10%.

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. ~90% codebase KHÔNG cần thay đổi.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [OAuth2TokenExchanger.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt) | L42-52 | Config-driven provider endpoints (replace hardcoded URLs) |
| 2 | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | L(SsoProperties section) | Add `providers: Map<String, ProviderConfig>` + `ProviderConfig` data class |
| 3 | [MfaService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | L(verifyMfa method) | Add `trustDevice`+`deviceHash` params, save hash after successful verify |
| 4 | [MfaController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | L(verify endpoint) | Pass trustDevice+deviceHash from DTO to MfaService |
| 5 | [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | L(handleCallback TODO) | Replace TODO with `eventPublisher.publish(SsoProvisionedEvent(...))` |
| 6 | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | L(add event class) | Add `SsoProvisionedEvent` data class implementing `DomainEvent` |
| 7 | [application.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application.yml) | L(sso section) | Add `app.security.sso.providers.*` config entries |

### 1.2 DTO MODIFY

| # | File | Change |
|---|------|--------|
| 1 | MfaDtos.kt (in `auth/adapter/in/web/dto/`) | Add `trustDevice: Boolean = false`, `deviceHash: String? = null` to `MfaVerifyRequest` |

### 1.3 NEW Files (Tests Only)

| # | File | Purpose |
|---|------|---------|
| T1 | `MfaLoginFlowIntegrationTest.kt` | Full MFA login flow integration |
| T2 | `SsoCallbackIntegrationTest.kt` | SSO callback with WireMock IdP |
| T3 | `TokenIntrospectionIntegrationTest.kt` | Token introspection active/blacklisted |
| T4 | `JwksEndpointIntegrationTest.kt` | JWKS response format + cache headers |
| T5 | `PasswordChangeIntegrationTest.kt` | Password policy + history enforcement |
| T6 | `TotpSetupFlowIntegrationTest.kt` | TOTP setup → confirm → verify flow |

---

## 2. Call Tree — LOGIC CẦN SỬA

### 2.1 OAuth2TokenExchanger.exchange() (Gap 1 — Config-Driven)

```
⟶ OAuth2TokenExchanger.exchange(provider, code, redirectUri, clientId, clientSecret)
├── getTokenEndpoint(provider)  // ← MODIFY: config lookup instead of when()
│   └── BEFORE: when(provider) { "google" → hardcoded, "microsoft" → hardcoded, else → throw }
│   └── AFTER: securityProperties.sso.providers[provider]?.tokenEndpoint ?: throw
├── getUserInfoEndpoint(provider)  // ← MODIFY: same config lookup
│   └── AFTER: securityProperties.sso.providers[provider]?.userInfoEndpoint ?: throw
├── exchangeCode(tokenEndpoint, code, redirectUri, clientId, clientSecret)
│   └── RestTemplate POST → token response
└── fetchUserInfo(userInfoEndpoint, accessToken, provider)
    └── RestTemplate GET → ExchangeResult(sub, email, name)
    └── catch timeout → throw SsoProviderTimeoutException  // ← already exists
```

### 2.2 MfaService.verifyMfa() (Gap 2 — Trusted Device Save)

```
⟶ MfaService.verifyMfa(mfaToken, code, trustDevice?, deviceHash?)
├── jwtService.parseMfaToken(mfaToken) → claims
│   └── Extract: userId, method, exp
├── method == "TOTP"?
│   ├── true → totpService.verifyCode(decryptedSecret, code)
│   └── false → otpService.verifyOtp(userId, channel, code)
├── verification failed?
│   ├── mfaRateLimitService.incrementAttempts(userId)
│   └── throw MfaCodeInvalidException
├── ✅ success:
│   ├── mfaRateLimitService.resetCounters(userId)
│   ├── [NEW] trustDevice && !deviceHash.isNullOrBlank()?  // ← GAP 2 logic
│   │   ├── user = userRepository.findById(userId)
│   │   ├── user.trustedDeviceHash = deviceHash
│   │   ├── userRepository.save(user)
│   │   └── auditLogService.logEvent(TRUSTED_DEVICE_SET)
│   ├── auditLogService.logEvent(MFA_VERIFY_SUCCESS)
│   └── tokenGenerator.generateAuthResponse(userId) → AuthResponse
```

### 2.3 SsoAdapter.handleCallback() (Gap 3 — EventPublisher)

```
⟶ SsoAdapter.handleCallback(code, provider, redirectUri)
├── oAuth2TokenExchanger.exchange(provider, code, redirectUri, clientId, clientSecret)
│   └── ExchangeResult(sub, email, name)
├── userIdentityRepository.findByProviderAndProviderSub(provider, sub)
├── identity found?
│   ├── true → user = userRepository.findById(identity.userId)
│   └── false → autoProvision?
│       ├── true → createUser() + createIdentity()
│       │   ├── [MODIFY] eventPublisher.publish(SsoProvisionedEvent(...))  // ← replace TODO
│       │   └── auditLogService.logEvent(SSO_LOGIN)
│       └── false → throw SsoUserNotProvisionedException
└── tokenGenerator.generateAuthResponse(user.id) → AuthResponse
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (7 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `OAuth2TokenExchanger.kt` | [OAuth2TokenExchanger](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt) | MODIFY: config-driven endpoints. Called by SsoAdapter. |
| 2 | `SecurityProperties.kt` | [SecurityProperties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | MODIFY: add ProviderConfig. Injected by OAuth2TokenExchanger, SsoAdapter, etc. |
| 3 | `MfaService.kt` | [MfaService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | MODIFY: verifyMfa() add trusted device params. Called by MfaController. |
| 4 | `MfaController.kt` | [MfaController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | MODIFY: pass new params. Entry point for MFA verify. |
| 5 | `SsoAdapter.kt` | [SsoAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | MODIFY: replace TODO with eventPublisher.publish(). |
| 6 | `EventPublisher.kt` | [EventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | MODIFY: add SsoProvisionedEvent class. |
| 7 | `application.yml` | [application.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application.yml) | MODIFY: add sso.providers config. |

### 🟡 Indirect Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `MfaDtos.kt` | dto/ | DTO change: MfaVerifyRequest add 2 fields (backward-compatible defaults) |
| 2 | `SsoController.kt` | [SsoController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt) | No change needed — calls SsoAdapter.handleCallback() unchanged signature |
| 3 | `SpringEventPublisher.kt` | [SpringEventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt) | No change needed — publishes any DomainEvent subclass |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. All changes are internal to auth-service. EventPublisher uses Spring ApplicationEvent (in-process), not Kafka yet.

### 🟢 Shared Utilities (0 changes)

No shared utility changes needed. `AuthExceptions.kt`, `GlobalExceptionHandler.kt`, `AuthErrorCode.kt` all already have required exception/error code entries.

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| SSO provider endpoint config | [OAuth2TokenExchanger:L42-52](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt) | 100% | **REUSE** (refactor) | 🟢 LOW (1 caller: SsoAdapter) | Replace hardcoded → config lookup |
| MFA verify logic | [MfaService.verifyMfa()](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | 100% | **REUSE** (extend) | 🟢 LOW (1 caller: MfaController) | Add optional params |
| EventPublisher port | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | 100% | **REUSE** (add event class) | 🟢 LOW (2 callers: SsoAdapter, RegisterHandler) | Add SsoProvisionedEvent |
| DomainEvent interface | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | 100% | **REUSE** | 🟢 LOW | Already generic |
| AuditLogService | [AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | 100% | **REUSE** | 🟢 LOW | May add TRUSTED_DEVICE_SET action |
| UserEntity.trustedDeviceHash | [UserEntity.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt) | 100% | **REUSE** | 🟢 LOW | Field exists, just needs to be set |
| User.requiresMfa() | [User.kt (domain model)](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/) | 100% | **REUSE** | 🟢 LOW | Already handles hash comparison |

### No EXTRACT Candidates

All changes in v2 are small extensions (add params, replace TODO, config refactor). No code duplication detected that warrants extraction.

### No NEW Components

All necessary components exist. Only modifications and additions to existing components.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `SecurityProperties` | `@ConfigurationProperties` (injected) | `.sso.providers`, `.sso.providers[name]?.tokenEndpoint` | Add ProviderConfig data class |
| `EventPublisher` | Interface (injected) | `publish(DomainEvent)` | Port already exists, add SsoProvisionedEvent |
| `SpringEventPublisher` | Adapter (auto-detected) | `publish(DomainEvent)` → Spring `ApplicationEventPublisher.publishEvent()` | No change needed |
| `UserRepository` | JpaRepository (injected) | `findById(Long)`, `save(UserEntity)` | Used in MfaService for trusted device save |
| `AuditLogService` | `@Service` (injected) | `logEvent(userId, action, ...)` | May add `TRUSTED_DEVICE_SET` to AuditAction enum |
| `OAuth2TokenExchanger` | `@Component` (injected in SsoAdapter) | `exchange(provider, code, redirectUri, clientId, clientSecret)` | Refactor internals only |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|------------|---------|
| `app.security.sso.providers.google.token-endpoint` | application.yml | `https://oauth2.googleapis.com/token` | `OAuth2TokenExchanger.getTokenEndpoint()` |
| `app.security.sso.providers.google.user-info-endpoint` | application.yml | `https://openidconnect.googleapis.com/v1/userinfo` | `OAuth2TokenExchanger.getUserInfoEndpoint()` |
| `app.security.sso.providers.keycloak.token-endpoint` | application.yml | `${KEYCLOAK_TOKEN_ENDPOINT}` | `OAuth2TokenExchanger.getTokenEndpoint()` |
| `app.security.sso.providers.keycloak.user-info-endpoint` | application.yml | `${KEYCLOAK_USERINFO_ENDPOINT}` | `OAuth2TokenExchanger.getUserInfoEndpoint()` |

### Error Codes Thrown (No New Codes)

All error codes already exist in `AuthErrorCode.kt` and `AuthCoreExceptions.kt`. No new exceptions needed.

| Error Code | Condition | Status |
|-----------|-----------|--------|
| `SSO_TOKEN_INVALID` | Unknown/unsupported SSO provider | ✅ Exists |
| `SSO_PROVIDER_TIMEOUT` | IdP timeout | ✅ Exists |
| `MFA_CODE_INVALID` | OTP/TOTP code wrong | ✅ Exists |
| `MFA_TOKEN_EXPIRED` | MFA JWT expired | ✅ Exists |

### DTO Changes

| DTO | Change | Match % | Decision |
|-----|--------|---------|----------|
| `MfaVerifyRequest` | Add `trustDevice: Boolean = false`, `deviceHash: String? = null` | EXTEND | Backward-compatible (default values) |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---------|----------------|--------|--------|
| `eventPublisher.publish(DomainEvent)` | `EventPublisher.publish(event: DomainEvent)` | `EventPublisher.kt:7` | ✅ |
| `userRepository.save(UserEntity)` | `JpaRepository.save(S)` | Spring Data JPA | ✅ |
| `securityProperties.sso.providers[name]` | Map<String, ProviderConfig> | Needs to be added | ⚠️ PENDING |
