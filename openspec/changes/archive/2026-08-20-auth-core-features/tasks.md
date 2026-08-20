<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: auth-core-features

> **Type**: EXTEND | **Flow**: Non-Financial | **FRs**: 17 (14 implemented, 3 partial — completion + testing)
> **Direction**: Approach B — Gap Completion + Hardening + Testing (from brainstorm)

## Changes

[CHANGED] Scope reassessed from v1 (30 tasks — full build) → v2 (17 tasks — completion + testing). Feature is ~90% implemented. This tasks.md covers remaining gaps + integration test coverage:

**GAP-001** (🟡 Gap): Config-driven SSO providers — 3 files modified (SecurityProperties, OAuth2TokenExchanger, application.yml)
**GAP-002** (🟡 Gap): Trusted device save on MFA verify — 3 files modified (MfaDtos, MfaService, MfaController)
**GAP-003** (🟡 Gap): SSO provisioning event — 2 files modified (EventPublisher, SsoAdapter)
**TEST-001** (🔴 Gap): Integration tests — 6 new test files
**TEST-002** (🟡 Gap): Edge case tests — 1 new test file
**DOC-001** (🟡 Gap): SecurityProperties documentation — 1 file modified

**Total**: 8 files modified, 7 files new (0 production + 7 test), ~20h effort

---

## Phase 1: GAP-001 — Config-Driven SSO Providers (~2h)

- [x] **Task 1: SecurityProperties — Add ProviderConfig**
  - File: [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Action: [MODIFY]
  - FR: FR-006 — OAuth2 SSO Login (Keycloak support)
  - Base: `@ConfigurationProperties(prefix = "app.security")` — existing
  - Pattern: Nested data class inside SsoProperties
  - Dependencies: None (pure config)
  - Changes:
    - Add `providers: Map<String, ProviderConfig> = emptyMap()` to `SsoProperties`
    - Add nested `data class ProviderConfig(val tokenEndpoint: String, val userInfoEndpoint: String, val clientId: String = "", val clientSecret: String = "", val enabled: Boolean = true)`
  - Ref: `impact_analysis.md` — 🟢 LOW impact (config extension, backward-compatible default)

- [x] **Task 2: OAuth2TokenExchanger — Config-Driven Endpoints**
  - File: [OAuth2TokenExchanger.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt) | Action: [MODIFY]
  - FR: FR-006 — OAuth2 SSO Login (Keycloak support)
  - Dependencies: `SecurityProperties` (already injected)
  - Changes:
    - Replace `getTokenEndpoint()` when-expression (L42-46) → `securityProperties.sso.providers[provider]?.tokenEndpoint ?: throw SsoTokenInvalidException("Unknown SSO provider: $provider")`
    - Replace `getUserInfoEndpoint()` when-expression (L48-52) → same config lookup pattern
    - ~20 lines changed, no public API change
  - Error: `SSO_TOKEN_INVALID` for unknown provider (already exists in `AuthCoreExceptions.kt`)

- [x] **Task 3: application.yml — SSO Provider Config**
  - File: [application.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application.yml) | Action: [MODIFY]
  - FR: FR-006 — OAuth2 SSO Login
  - Changes: Add `app.security.sso.providers` section with Google, Microsoft, Keycloak endpoints
  - Pattern: Spring Boot relaxed binding with env var fallback for Keycloak
  - Config:
    ```yaml
    app.security.sso.providers:
      google:
        token-endpoint: https://oauth2.googleapis.com/token
        user-info-endpoint: https://openidconnect.googleapis.com/v1/userinfo
      microsoft:
        token-endpoint: https://login.microsoftonline.com/common/oauth2/v2.0/token
        user-info-endpoint: https://graph.microsoft.com/oidc/userinfo
      keycloak:
        token-endpoint: ${KEYCLOAK_TOKEN_ENDPOINT:http://localhost:8080/realms/master/protocol/openid-connect/token}
        user-info-endpoint: ${KEYCLOAK_USERINFO_ENDPOINT:http://localhost:8080/realms/master/protocol/openid-connect/userinfo}
    ```

---

## Phase 2: GAP-002 — Trusted Device Save on MFA Verify (~2h)

- [x] **Task 4: MfaVerifyRequest DTO — Add Trusted Device Fields**
  - File: `auth/adapter/in/web/dto/MfaDtos.kt` | Action: [MODIFY]
  - FR: FR-005 — Trusted Device (Skip MFA)
  - Pattern: Backward-compatible defaults (`false`, `null`)
  - Changes:
    - Add `val trustDevice: Boolean = false` to `MfaVerifyRequest`
    - Add `val deviceHash: String? = null` to `MfaVerifyRequest`
  - Dependencies: None (pure DTO)

- [x] **Task 5: MfaService — Save Trusted Device Hash**
  - File: [MfaService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device (Skip MFA)
  - Dependencies: `UserRepository` (already injected), `AuditLogService` (already injected)
  - Changes:
    - `verifyMfa()` signature: add `trustDevice: Boolean = false, deviceHash: String? = null`
    - After successful verify, before return:
      ```kotlin
      if (trustDevice && !deviceHash.isNullOrBlank()) {
          val user = userRepository.findById(userId).orElseThrow()
          user.trustedDeviceHash = deviceHash
          userRepository.save(user)
          log.info("Trusted device set for userId={}", userId)
      }
      ```
    - `User.requiresMfa(deviceHash)` in `LoginHandler` already handles comparison — no change needed there
  - Ref: `impact_analysis.md` — 🟢 LOW impact (1 caller: MfaController)

- [x] **Task 6: MfaController — Pass Trusted Device Params**
  - File: [MfaController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device (Skip MFA)
  - Dependencies: `MfaService` (already injected)
  - Changes:
    - In verify endpoint: pass `request.trustDevice` and `request.deviceHash` to `mfaService.verifyMfa()`

---

## Phase 3: GAP-003 — SSO Provisioning Event (~1h)

- [x] **Task 7: SsoProvisionedEvent — Add Domain Event**
  - File: [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | Action: [MODIFY]
  - FR: FR-007 — JIT User Provisioning
  - Pattern: Follow existing `UserRegisteredEvent`, `PermissionChangedEvent` pattern
  - Changes:
    - Add data class:
      ```kotlin
      data class SsoProvisionedEvent(
          val userId: Long,
          val provider: String,
          val email: String?,
          val domainCode: String
      ) : DomainEvent {
          override val eventType: String = "iam.user.sso_provisioned"
      }
      ```

- [x] **Task 8: SsoAdapter — Publish SSO Provisioning Event**
  - File: [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Action: [MODIFY]
  - FR: FR-007 — JIT User Provisioning
  - Dependencies: `EventPublisher` (already injected — verify), `SsoProvisionedEvent` (from Task 7)
  - Changes:
    - Replace TODO comment with:
      ```kotlin
      eventPublisher.publish(SsoProvisionedEvent(
          userId = user.id,
          provider = provider,
          email = exchangeResult.email,
          domainCode = domainCode
      ))
      ```
    - `SpringEventPublisher` adapter handles this via `ApplicationEventPublisher.publishEvent()` — no adapter change needed
  - Ref: `impact_analysis.md` — 🟢 LOW impact (in-process event, no external dependency)

---

## Phase 4: TEST-001 — Integration Tests (~12h)

- [x] **Task 9: MFA Login Flow Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/MfaLoginFlowIntegrationTest.kt` | Action: [NEW]
  - FR: FR-001, FR-002, FR-005, FR-015 — Full MFA flow
  - Pattern: `@SpringBootTest` + `@Testcontainers` (Redis) + `@AutoConfigureMockMvc`
  - Test cases:
    1. Login with MFA enabled → 200 MfaRequired → verify OTP → 200 AuthResponse
    2. Login with MFA enabled + trusted device → 200 AuthResponse (MFA skipped)
    3. Login → MFA → verify with trustDevice=true → subsequent login skips MFA
    4. Verify with expired mfaToken → 401 MFA_TOKEN_EXPIRED
    5. Verify with wrong code 3x → 429 MFA_MAX_ATTEMPTS
    6. Verify same mfaToken+code twice → second fails (idempotency via Redis DEL)

- [x] **Task 10: SSO Callback Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/SsoCallbackIntegrationTest.kt` | Action: [NEW]
  - FR: FR-006, FR-007, FR-017 — SSO OAuth2 flow
  - Pattern: `@SpringBootTest` + WireMock (`@WireMockTest`) for IdP mock
  - Dependencies: WireMock for Google/Microsoft/Keycloak token+userinfo endpoints
  - Test cases:
    1. SSO callback (Google) → existing user → 200 AuthResponse
    2. SSO callback (Microsoft) → new user + autoProvision=true → JIT provision + 200
    3. SSO callback (Keycloak) → config-driven endpoints → 200 (after Task 2)
    4. SSO callback → IdP timeout → 504 SSO_PROVIDER_TIMEOUT
    5. SSO callback → new user + autoProvision=false → 403 SSO_USER_NOT_PROVISIONED
    6. SSO callback → revoked auth code → 401 SSO_TOKEN_INVALID

- [x] **Task 11: Token Introspection Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/TokenIntrospectionIntegrationTest.kt` | Action: [NEW]
  - FR: FR-011 — Token introspection (RFC 7662)
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc`
  - Test cases:
    1. Valid token → 200 `{ active: true, sub, roles, permissions, exp, iat }`
    2. Expired token → 200 `{ active: false }`
    3. Blacklisted jti → 200 `{ active: false }`
    4. Malformed token → 200 `{ active: false }`

- [x] **Task 12: JWKS Endpoint Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/JwksEndpointIntegrationTest.kt` | Action: [NEW]
  - FR: FR-010 — JWKS endpoint
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc`
  - Test cases:
    1. GET `/.well-known/jwks.json` → 200 with `{ keys: [{ kty: "RSA", kid, n, e, alg: "RS256", use: "sig" }] }`
    2. Response has `Cache-Control: max-age=86400, public` header
    3. Response key matches JWT signing key (verify a signed token with JWKS key)

- [x] **Task 13: Password Change Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/PasswordChangeIntegrationTest.kt` | Action: [NEW]
  - FR: FR-013, FR-014 — Password policy + history
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc`
  - Test cases:
    1. Change password with valid policy → 200 + password_history entry created
    2. Change to recently used password → 400 PASSWORD_RECENTLY_USED
    3. Change with weak password → 400 PASSWORD_POLICY_VIOLATION (Passay rules)
    4. Change with wrong old password → 401 INVALID_CREDENTIALS
    5. Default policy applied when no domain-specific policy exists

- [x] **Task 14: TOTP Setup Flow Integration Test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/TotpSetupFlowIntegrationTest.kt` | Action: [NEW]
  - FR: FR-002, FR-003 — TOTP setup + confirm + verify
  - Pattern: `@SpringBootTest` + `@Testcontainers` (Redis)
  - Test cases:
    1. Setup → get secret+qrUri → confirm with valid code → MFA enabled
    2. Setup → confirm with wrong code → fail, TOTP not saved
    3. Setup → wait for Redis TTL expiry → confirm → 400 TOTP_NOT_SETUP
    4. Enable MFA method=TOTP without setup → 400 TOTP_NOT_SETUP
    5. Login → MFA with TOTP → verify with authenticator code → tokens

---

## Phase 5: TEST-002 + DOC-001 — Edge Cases + Documentation (~3h)

- [x] **Task 15: Edge Case — Concurrent MFA Verify + Method Mismatch**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/MfaServiceEdgeCaseTest.kt` | Action: [NEW]
  - FR: FR-001, FR-002, FR-015 — MFA idempotency + method validation
  - Pattern: JUnit 5 + Mockito
  - Test cases:
    1. Two threads call verifyMfa() with same mfaToken — first succeeds, second fails (Redis DEL atomicity)
    2. mfaToken claims method=SMS but user has method=TOTP → proper error handling

- [x] **Task 16: Documentation — SecurityProperties Defaults**
  - File: [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Action: [MODIFY]
  - FR: FR-004 (CAPTCHA threshold), FR-001 (MFA TTL)
  - Changes:
    - Add KDoc comments documenting default values for all properties
    - Document CAPTCHA threshold default (from SecurityProperties)
    - Document MFA token TTL default (300s)
    - Document OTP TTL default (300s)
    - Document trusted device TTL (30 days — future, currently no expiry)

---

## Phase 6: Verification

- [x] **Task 17: FR traceability verification**
  - Verify all 17 FRs addressed:
    - FR-001 (OTP): ✅ IMPLEMENTED → test coverage (T9, T15)
    - FR-002 (TOTP): ✅ IMPLEMENTED → test coverage (T14, T15)
    - FR-003 (MFA Settings): ✅ IMPLEMENTED → test coverage (T14)
    - FR-004 (CAPTCHA): ✅ IMPLEMENTED → documentation (T16)
    - FR-005 (Trusted Device): ⚠️ PARTIAL → completion (T4, T5, T6) + test (T9)
    - FR-006 (SSO Login): ⚠️ PARTIAL → Keycloak (T1, T2, T3) + test (T10)
    - FR-007 (JIT Provision): ⚠️ PARTIAL → event publish (T7, T8) + test (T10)
    - FR-008 (SSO Link): ✅ IMPLEMENTED → test coverage (T10)
    - FR-009 (RS256): ✅ FULLY IMPLEMENTED (no tasks needed)
    - FR-010 (JWKS): ✅ IMPLEMENTED → test coverage (T12)
    - FR-011 (Introspect): ✅ IMPLEMENTED → test coverage (T11)
    - FR-012 (Force Logout): ✅ FULLY IMPLEMENTED (no tasks needed)
    - FR-013 (Password Policy): ✅ IMPLEMENTED → test coverage (T13) + docs (T16)
    - FR-014 (Password History): ✅ IMPLEMENTED → test coverage (T13)
    - FR-015 (MFA Idempotency): ✅ IMPLEMENTED → test coverage (T9, T15)
    - FR-016 (Audit Logging): ✅ FULLY IMPLEMENTED (no tasks needed)
    - FR-017 (IdP Timeout): ✅ IMPLEMENTED → test coverage (T10)
  - **Coverage: 17/17 FRs ✓** (3 partial → completion tasks, 14 implemented → test/doc tasks)

---

## Summary

| Phase | Tasks | Files Modified | Files New | Effort |
|-------|-------|---------------|-----------|--------|
| Phase 1: Keycloak SSO | 3 | 3 (SecurityProperties, OAuth2TokenExchanger, application.yml) | 0 | ~2h |
| Phase 2: Trusted Device | 3 | 3 (MfaDtos, MfaService, MfaController) | 0 | ~2h |
| Phase 3: EventPublisher | 2 | 2 (EventPublisher.kt, SsoAdapter) | 0 | ~1h |
| Phase 4: Integration Tests | 6 | 0 | 6 (test classes) | ~12h |
| Phase 5: Edge Cases + Docs | 2 | 1 (SecurityProperties) | 1 (test class) | ~3h |
| Phase 6: Verification | 1 | 0 | 0 | ~0.5h |
| **Total** | **17** | **8** | **7** | **~20.5h** |

### Cross-Feature Coordination

| Shared File | This Feature | Other Feature | Risk |
|------------|-------------|---------------|------|
| `LoginHandler.kt` | No change (FR-005 uses existing `requiresMfa()`) | `anonymous-login-optimization` (session promotion) | 🟢 No conflict |
| `AuthCoreExceptions.kt` | No change (all exceptions exist) | `api-response-i18n-standard` (i18n codes) | 🟢 No conflict |
| `SecurityProperties.kt` | MODIFY (add providers map) | None | 🟢 Isolated change |
