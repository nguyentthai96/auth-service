# Delta Spec: auth-core-features

> **Generated**: 2026-08-20 | **Type**: EXTEND | **Flow**: Non-Financial
> **Scope**: 3 production gaps closed + 7 test files + documentation

---

## 1. Behavioral Changes (Production Code)

### 1.1 OAuth2TokenExchanger — Config-Driven Endpoints (GAP-001)

| Aspect | Before | After |
|--------|--------|-------|
| Provider endpoint resolution | Hardcoded `when(provider)` expression for Google + Microsoft only | Config-driven via `securityProperties.sso.providers[provider]` lookup |
| Keycloak support | ❌ Not supported — would throw on unknown provider | ✅ Supported — any OIDC provider configurable via `application.yml` |
| Error on unknown provider | Undefined behavior (no `else` branch) | `SsoTokenInvalidException("Unknown SSO provider: $provider")` |
| Adding new provider | Requires code change | Config-only change in `application.yml` |

### 1.2 MfaService.verifyMfa() — Trusted Device Save (GAP-002)

| Aspect | Before | After |
|--------|--------|-------|
| `verifyMfa()` signature | `verifyMfa(mfaToken, code, authResponseBuilder)` | `verifyMfa(mfaToken, code, authResponseBuilder, trustDevice=false, deviceHash=null)` |
| Trusted device flow | MFA always required (no device trust persistence) | On verify success + `trustDevice=true` + valid `deviceHash`: saves hash to `UserEntity.trustedDeviceHash` |
| Subsequent logins | Always triggers MFA challenge | Skips MFA if `LoginHandler.requiresMfa(deviceHash)` matches stored hash |
| Backward compatibility | N/A | ✅ Default params `trustDevice=false, deviceHash=null` — existing callers unaffected |

### 1.3 SsoAdapter — SSO Provisioning Event (GAP-003)

| Aspect | Before | After |
|--------|--------|-------|
| JIT provisioning event | `// TODO` comment — no event published | `eventPublisher.publish(SsoProvisionedEvent(userId, provider, email, domainCode))` |
| Event type | N/A | `iam.user.sso_provisioned` |
| Downstream impact | No notification of new SSO user | In-process `ApplicationEventPublisher` dispatches event (Kafka adapter deferred) |

### 1.4 SsoAdapter.getProviders() — Config-Driven (design.md Gap B)

| Aspect | Before | After |
|--------|--------|-------|
| Provider list | Hardcoded `listOf(google, microsoft, keycloak)` | Reads from `securityProperties.sso.providers` with `.filter { enabled }` |
| Adding/removing provider | Requires code change | Config-only change |

### 1.5 AuthService.revokeAllSessions() — Complete Implementation (design.md Gap A)

| Aspect | Before | After |
|--------|--------|-------|
| Revocation logic | Hollow — `return 0` with TODO | Calls `refreshTokenRepository.revokeAllByUserId(userId)` for batch revocation |
| Session cleanup | Not performed | Login sessions cleaned (natural expiry via JWT design) |
| Audit trail | Only logged user ID | Logs `revokedTokens=N` count in audit event |
| Return value | Always `0` | Actual count of revoked refresh tokens |

### 1.6 AuthController + CqrsAuthController — domainId Resolution (design.md Gap D)

| Aspect | Before | After |
|--------|--------|-------|
| Password change `domainId` | `val domainId: Long? = null` (placeholder) | `domainLookupService.getPrimaryDomainId(userId)` — resolved from user's active domain |
| Password policy enforcement | Default policy only (domain-specific ignored) | Domain-specific Passay rules applied correctly |

### 1.7 SecurityProperties — KDoc Documentation (DOC-001)

| Aspect | Before | After |
|--------|--------|-------|
| Property documentation | Minimal/no KDoc | Full KDoc on all nested data classes and properties with default values documented |

---

## 2. New Files

| # | File | Type | Purpose |
|---|------|------|---------|
| 1 | `MfaLoginFlowIntegrationTest.kt` | Test | Full MFA flow: login → MFA challenge → verify → tokens |
| 2 | `SsoCallbackIntegrationTest.kt` | Test | SSO OAuth2 callback: Google/Microsoft/Keycloak + JIT provision |
| 3 | `TokenIntrospectionIntegrationTest.kt` | Test | RFC 7662 introspection: active/expired/blacklisted/malformed |
| 4 | `JwksEndpointIntegrationTest.kt` | Test | JWKS endpoint: key format + cache-control + verify signing |
| 5 | `PasswordChangeIntegrationTest.kt` | Test | Password change: policy/history/wrong-old/default-fallback |
| 6 | `TotpSetupFlowIntegrationTest.kt` | Test | TOTP lifecycle: setup → confirm → enable → verify |
| 7 | `MfaServiceEdgeCaseTest.kt` | Test | Concurrent verify idempotency + method mismatch |

---

## 3. No Breaking Changes

All production modifications maintain backward compatibility:
- New method parameters have defaults
- No public API signature changes
- No database schema changes (all columns pre-exist from prior migrations)
- Config additions use `emptyMap()` defaults
