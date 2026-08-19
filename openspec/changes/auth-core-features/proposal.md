## Why

Auth-service đã implement ~90% core authentication features (MFA, SSO, RS256 JWT, Password Policy). Tuy nhiên, 3 functional gaps còn lại và thiếu integration test coverage khiến feature chưa production-ready:

1. **🟡 GAP — Keycloak SSO**: `OAuth2TokenExchanger.getTokenEndpoint("keycloak")` throws "Unsupported SSO provider" — hardcoded endpoints chỉ có Google + Microsoft. `getProviders()` trả về Keycloak nhưng code không xử lý được.
2. **🟡 GAP — Trusted Device Save**: `User.requiresMfa(deviceHash)` so sánh hash đúng, nhưng không có logic SET `trustedDeviceHash` sau MFA verify thành công. User không bao giờ có thể trust device.
3. **🟡 GAP — SSO Provisioning Event**: `SsoAdapter.handleCallback()` có TODO comment cho Kafka event `iam.user.sso_provisioned`. `EventPublisher` port + `SpringEventPublisher` adapter đã tồn tại nhưng chưa được gọi từ SSO flow.
4. **🔴 GAP — Zero Integration Test Coverage**: 6 unit test classes exist, nhưng không có integration test cho full login→MFA→verify flow, SSO callback, token introspection, JWKS, password change, hay TOTP setup flow.

## Changes

[CHANGED] Scope thu hẹp từ v1 (30 tasks — full build) → v2 (17 tasks — completion + testing). ~90% code đã implemented.

- **GAP-001: Config-Driven SSO Providers** — Refactor `OAuth2TokenExchanger` từ hardcoded `when()` → config lookup via `SecurityProperties.sso.providers` map. Add `ProviderConfig` nested data class. Add Keycloak config in `application.yml` with env var fallback. ~20 lines changed, 3 files.
- **GAP-002: Trusted Device Save on MFA Verify** — Add `trustDevice: Boolean` + `deviceHash: String?` to `MfaVerifyRequest` DTO. In `MfaService.verifyMfa()`: after successful verify, save `user.trustedDeviceHash`. Pass params from `MfaController`. 3 files modified.
- **GAP-003: SSO Provisioning Event** — Add `SsoProvisionedEvent` data class to `EventPublisher.kt`. Replace TODO in `SsoAdapter.handleCallback()` with `eventPublisher.publish(SsoProvisionedEvent(...))`. 2 files modified.
- **TEST-001: Integration Test Suite** — 6 new integration test classes covering: MFA login flow, SSO callback with WireMock, token introspection, JWKS endpoint, password change with policy, TOTP setup flow.
- **TEST-002: Edge Case Tests** — 1 new unit test class for concurrent MFA verify idempotency and MFA token method mismatch.
- **DOC-001: SecurityProperties Documentation** — Add KDoc comments documenting default values for CAPTCHA threshold, MFA token TTL, OTP TTL.

**Total**: 8 files modified, 7 files new (0 production + 7 test), ~20h effort.

## Capabilities

### Fixed Capabilities
- `sso-keycloak-support`: OAuth2TokenExchanger supports Keycloak via config-driven endpoints (GAP-001)
- `trusted-device-persistence`: MfaService saves trustedDeviceHash after MFA verify success (GAP-002)
- `sso-provisioning-event`: SsoAdapter publishes SsoProvisionedEvent via EventPublisher port (GAP-003)

### New Capabilities
- `sso-config-driven-providers`: All SSO provider endpoints configurable via `app.security.sso.providers.*` — extensible to any OIDC provider
- `auth-integration-tests`: 6 integration test classes covering all critical auth flows (TEST-001)
- `auth-edge-case-tests`: Edge case unit tests for MFA idempotency and method mismatch (TEST-002)

### Unchanged Capabilities (REUSE — Fully Implemented)
- `mfa-otp-sms-email` — OtpService + MfaService (FR-001) ✅
- `mfa-totp-authenticator` — TotpService + AES-256-GCM encryption (FR-002) ✅
- `mfa-settings-management` — MfaService.updateSettings() + MfaController (FR-003) ✅
- `captcha-integration` — CaptchaVerifier + AltchaCaptchaVerifier (FR-004) ✅
- `sso-oauth2-login` — SsoAdapter + OAuth2TokenExchanger for Google/Microsoft (FR-006) ✅
- `sso-jit-provisioning` — SsoAdapter.handleCallback() JIT logic (FR-007) ✅
- `sso-identity-linking` — SsoAdapter.linkIdentity/unlinkIdentity (FR-008) ✅
- `jwt-rs256-signing` — JwtService dual-key RS256+HMAC fallback (FR-009) ✅
- `jwks-endpoint` — TokenController.jwks() with Cache-Control (FR-010) ✅
- `token-introspection` — TokenController.introspect() RFC 7662 (FR-011) ✅
- `force-logout` — TokenController.revokeAllSessions() (FR-012) ✅
- `password-policy-passay` — PasswordPolicyService + Passay + cache (FR-013) ✅
- `password-history` — PasswordPolicyService.checkPasswordHistory() (FR-014) ✅
- `mfa-verify-idempotency` — OtpService Redis DEL after success (FR-015) ✅
- `audit-logging` — AuditLogService + AuditAction enum (FR-016) ✅
- `idp-timeout-handling` — SsoProviderTimeoutException (FR-017) ✅

## Impact

### Backend (auth-service)

**MODIFY** (8 existing files):
- `OAuth2TokenExchanger.kt` — replace hardcoded `when()` with `securityProperties.sso.providers[provider]` config lookup (~20 lines)
- `SecurityProperties.kt` — add `providers: Map<String, ProviderConfig>` to `SsoProperties` + add `ProviderConfig` data class (~15 lines)
- `application.yml` — add `app.security.sso.providers.*` section with Google/Microsoft/Keycloak configs (~15 lines)
- `MfaService.kt` — add `trustDevice`/`deviceHash` params to `verifyMfa()`, save hash after success (~10 lines)
- `MfaController.kt` — pass `request.trustDevice` and `request.deviceHash` to `mfaService.verifyMfa()` (~2 lines)
- `MfaDtos.kt` — add `trustDevice: Boolean = false`, `deviceHash: String? = null` to `MfaVerifyRequest` (~2 lines)
- `EventPublisher.kt` — add `SsoProvisionedEvent` data class implementing `DomainEvent` (~8 lines)
- `SsoAdapter.kt` — replace TODO with `eventPublisher.publish(SsoProvisionedEvent(...))` (~3 lines)

**NEW** (7 test files):
- `MfaLoginFlowIntegrationTest.kt` — full MFA login flow (6 test cases)
- `SsoCallbackIntegrationTest.kt` — SSO callback with WireMock IdP (6 test cases)
- `TokenIntrospectionIntegrationTest.kt` — token introspection (4 test cases)
- `JwksEndpointIntegrationTest.kt` — JWKS response + cache headers (3 test cases)
- `PasswordChangeIntegrationTest.kt` — password policy + history (5 test cases)
- `TotpSetupFlowIntegrationTest.kt` — TOTP setup → confirm → verify (5 test cases)
- `MfaServiceEdgeCaseTest.kt` — concurrent verify + method mismatch (2 test cases)

### Database
- **No changes** — V2 migration already applied, no new tables or columns

### External Systems
- **No changes** — same Redis key namespaces, same OAuth2 endpoints (now configurable), same PostgreSQL schema
- **New config**: Keycloak token/userinfo endpoints via env vars `KEYCLOAK_TOKEN_ENDPOINT`, `KEYCLOAK_USERINFO_ENDPOINT`

### Cross-Feature Coordination
- `LoginHandler.kt` — shared with `anonymous-login-optimization` but different code regions (no conflict)
- `AuthCoreExceptions.kt` — shared with `api-response-i18n-standard` but no changes needed (all exceptions exist)
- `SecurityProperties.kt` — isolated change (add providers map), no overlap with other features
