---
type: brainstorm_notes
change: auth-core-features
date: 2026-08-19
selected_direction: "Approach B: Gap Completion + Hardening + Testing"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Core Features — Completion & Hardening

## Date
2026-08-19

## Context

This is a **second-pass brainstorm** — the first was conducted on 2026-08-05 when the feature was ~60% implemented. Since then, implementation has progressed to ~90% complete. All 17 FRs (14 URD + 3 Enriched) are either IMPLEMENTED or PARTIAL. The codebase has a solid foundation with full MFA flow (OTP/TOTP), SSO adapter, RS256 JWT signing, password policy with Passay, CAPTCHA integration, rate limiting, and session management.

**What changed since last brainstorm (2026-08-05 → 2026-08-19):**
- `LoginResult` sealed class: ✅ IMPLEMENTED (as designed in D1)
- MFA JWT stateless token: ✅ IMPLEMENTED (as designed in D2)
- RS256 dual-key migration: ✅ IMPLEMENTED (Option C refresh-based, as designed in D3)
- CAPTCHA at service level: ✅ IMPLEMENTED (as designed in D4)
- SSO hybrid approach: ✅ IMPLEMENTED (as designed in D5)
- UserEntity columns: ✅ APPLIED via V2 migration (as designed in D6)
- Passay factory per domain: ✅ IMPLEMENTED (as designed in D7)
- Quality score: 88 → 91 (RS256 migration risk resolved)
- All test classes created: `LoginHandlerTest`, `MfaServiceTest`, `PasswordPolicyServiceTest`, `OtpServiceTest`, `SsoAdapterTest`, `MfaRateLimitServiceTest`

**Source context (refreshed):**
- `pre_openspec.md` (2026-08-19): 17 FRs, quality 91/100, 2 issues (🟡)
- Research artifacts: 7 files complete (business_analysis, technical_spec, comparison, etc.)
- Existing brainstorm (2026-08-05): 10 design decisions, all except Q8-Q10 RESOLVED

**In-pipeline overlap:**
- `anonymous-login-optimization`: Overlaps in `LoginHandler`, `AuthService`, `SessionPromotionService`
- `api-response-i18n-standard`: Overlaps in `AuthCoreExceptions`, `AuthErrorCode`

---

## Questions Asked & Answers

### Q1: What exactly remains unfinished? (Gap audit against codebase)

**→ A: Three concrete gaps + two quality gaps**

**Gap 1: Keycloak SSO Provider Support** (FR-006)
```
File: OAuth2TokenExchanger.kt (lines 43-52)

Current:
  getTokenEndpoint("google")     → ✅ hardcoded URL
  getTokenEndpoint("microsoft")  → ✅ hardcoded URL
  getTokenEndpoint("keycloak")   → ❌ throws "Unsupported SSO provider"

  getUserInfoEndpoint("google")     → ✅
  getUserInfoEndpoint("microsoft")  → ✅
  getUserInfoEndpoint("keycloak")   → ❌ throws
```

**Gap 2: Kafka Event for SSO Provisioning** (FR-007)
```
File: SsoAdapter.kt (line 71)
  // TODO: Emit event via EventPublisher when Kafka is configured
  // kafkaTemplate.send("iam.user.sso_provisioned", ...)

Dependency: spring-kafka is compileOnly in build.gradle.kts
Status: Deferred — infrastructure not ready
```

**Gap 3: Trusted Device Hardening** (FR-005)
```
File: User.kt (lines 71-76)
  fun requiresMfa(deviceHash: String?): Boolean {
      if (!mfaEnabled || mfaMethod == "NONE") return false
      return deviceHash == null || deviceHash != trustedDeviceHash
  }

Current state:
  ✅ Domain model compares deviceHash
  ✅ LoginHandler passes command.trustedDeviceHash
  ⚠️ No endpoint to SET trustedDeviceHash after MFA verify
  ⚠️ No TTL management (30-day expiry per SecurityProperties.mfa.trustedDeviceTtlDays)
  ⚠️ No hash generation logic documented (SHA-256 of what inputs?)
```

**Quality Gap A: Test Coverage for SSO/MFA integration flows**
```
Existing tests:
  ✅ LoginHandlerTest (unit)
  ✅ MfaServiceTest (unit)
  ✅ PasswordPolicyServiceTest (unit)
  ✅ OtpServiceTest (unit)
  ✅ SsoAdapterTest (unit)
  ✅ MfaRateLimitServiceTest (unit)
  ✅ AuthControllerIntegrationTest (integration)
  ✅ MfaRateLimitIntegrationTest (integration)

Missing:
  ❌ SsoController integration test (WireMock for IdP)
  ❌ Full MFA login flow integration test (login → MFA → verify → tokens)
  ❌ Token introspection integration test
  ❌ JWKS endpoint integration test
  ❌ Password change with policy integration test
  ❌ TOTP setup → confirm → MFA verify flow test
```

**Quality Gap B: Edge case handling documentation**
```
Not explicitly tested/handled:
  - Login with expired mfaToken + valid OTP code
  - MFA verify with wrong method in mfaToken claims
  - SSO callback with revoked/expired authorization code
  - Password change when policy doesn't exist for domain (fallback to default)
  - TOTP confirm with expired Redis key (setup timeout)
  - Concurrent MFA verify with same mfaToken
```

### Q2: Should we complete Keycloak support now or defer?

**→ A: Complete now — low effort, high value**

Trade-offs:
| | Complete Now | Defer |
|---|---|---|
| Effort | ~2 hours (config-driven endpoints) | 0 now, same later |
| Risk | LOW — simple URL configuration | LOW |
| Value | FR-006 fully covered, SSO provider list is honest | `getProviders()` returns Keycloak but it doesn't actually work |
| Dependency | None — Keycloak URLs are per-instance configurable | Same |

**Design decision**: Extend `SsoProperties` with per-provider config, NOT hardcode Keycloak URLs:

```kotlin
data class SsoProperties(
    val enabled: Boolean = false,
    val autoProvisionEnabled: Boolean = false,
    val defaultDomainCode: String = "default",
    val timeoutMs: Long = 10_000,
    // NEW
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    data class ProviderConfig(
        val tokenEndpoint: String,
        val userInfoEndpoint: String,
        val clientId: String = "",      // Override env var
        val clientSecret: String = "",  // Override env var
        val enabled: Boolean = true
    )
}
```

This makes ALL providers configurable (not just Keycloak) and eliminates hardcoded Google/Microsoft URLs from `OAuth2TokenExchanger`. Config-driven approach:

```yaml
app:
  security:
    sso:
      providers:
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

**Why this is better than `spring.security.oauth2.client.registration`:**
- Our SPA frontend already handles OAuth2 redirect + code extraction
- We only need token exchange + userinfo fetch on backend
- Spring Security OAuth2 Login auto-redirects browser — incompatible with our API-first SPA model
- Properties-based config is simpler and more explicit

### Q3: What about trusted device — how should the hash be generated?

**→ A: SHA-256(userAgent + timezone + language + screenRes) — provided by frontend**

Trusted device flow analysis:

```
Current Implementation State:
  ┌─────────────────────────────────────────┐
  │ LoginRequest.trustedDeviceHash  ✅ EXIST │ → Client sends hash
  │ User.trustedDeviceHash          ✅ EXIST │ → DB stores hash
  │ User.requiresMfa(deviceHash)    ✅ EXIST │ → Comparison logic
  │                                          │
  │ MISSING:                                 │
  │  1. MfaVerifyRequest.trustDevice ❌      │ → Checkbox "trust this device"
  │  2. MfaService → save hash after verify ❌│ → Persist on confirm
  │  3. TTL expiry check            ❌       │ → 30-day window
  └─────────────────────────────────────────┘
```

**Design for trusted device completion:**

```
Phase 1: MFA Verify → Trust Device
                                              
  ┌────────┐      POST /mfa/verify           ┌────────────┐
  │ Client │ ──── { mfaToken, code,     ───→ │ MfaService │
  │ (SPA)  │      trustDevice: true,          │            │
  │        │      deviceHash: "abc123" }      │ 1. Verify code ✅
  └────────┘                                  │ 2. If trustDevice:
       ▲                                      │    save hash + timestamp
       │          200 OK                      │ 3. Return AuthResponse
       └────────── { tokens... } ─────────── └────────────┘

Phase 2: Subsequent Login → Skip MFA

  ┌────────┐      POST /login                ┌──────────────┐
  │ Client │ ──── { username, password,  ───→ │ LoginHandler │
  │ (SPA)  │      trustedDeviceHash:          │              │
  │        │      "abc123" }                  │ 1. user.requiresMfa("abc123")
  └────────┘                                  │ 2. hash matches + not expired
       ▲                                      │ 3. Skip MFA → direct tokens
       │          200 OK                      │
       └────────── { tokens... } ────────── └──────────────┘
```

**TTL approach**: Store `trustedDeviceSetAt` on UserEntity (or Redis). Check `now - trustedDeviceSetAt < 30 days` in `requiresMfa()`.

**Recommendation**: Use UserEntity field `trusted_device_set_at` (new column, V-next migration) rather than Redis — device trust is a persistent property, not ephemeral state.

But since V2 migration is already applied, we can add this as a V-next migration or piggyback on a future migration. For now, **trusted device can work without TTL** — just compare the hash. Add TTL check in a follow-up.

**Simplified approach for this sprint:**
1. Add `trustDevice: Boolean` and `deviceHash: String?` to `MfaVerifyRequest` DTO
2. In `MfaService.verifyMfa()`: after successful verify, if trustDevice=true, update `user.trustedDeviceHash`
3. `User.requiresMfa()` already handles the comparison
4. TTL: defer to next sprint (add `trusted_device_set_at` column)

### Q4: Should Kafka event publishing be implemented now?

**→ A: No — implement EventPublisher PORT with in-memory fallback**

```
Reasoning:
  - spring-kafka is compileOnly → switching to implementation changes dependency graph
  - No Kafka infrastructure available yet
  - SSO provisioning event is "fire-and-forget" — can be added later with zero impact
  
Approach:
  1. Define port: EventPublisher interface
  2. Provide NoopEventPublisher (logs only)
  3. When Kafka ready: KafkaEventPublisher implementation + switch compileOnly → implementation
  4. SsoAdapter already has TODO comment — replace with port call
```

This follows hexagonal architecture: domain doesn't know about Kafka.

### Q5: What is the optimal testing strategy given existing test infrastructure?

**→ A: Layer-prioritized testing — integration tests first, then edge cases**

```
Testing Pyramid (current state → target):

           ┌─────┐
          /  E2E  \          ← NOT in scope (frontend needed)
         /─────────\
        / Integration \      ← PRIORITY: 6 new integration tests
       /───────────────\
      /   Unit Tests    \    ← EXISTS: 6 tests, add edge cases
     /───────────────────\
```

**Priority-ordered test plan:**

| # | Test Name | Type | Coverage | Effort |
|---|-----------|------|----------|--------|
| T1 | Full MFA login flow (login → MFA → verify → tokens) | Integration | UC-001 end-to-end | 3h |
| T2 | SSO callback with WireMock IdP | Integration | UC-002 + Keycloak | 3h |
| T3 | Token introspection (active + blacklisted) | Integration | UC-003 | 1h |
| T4 | JWKS endpoint + Cache-Control | Integration | FR-010 | 0.5h |
| T5 | Password change with policy + history | Integration | UC-004 | 2h |
| T6 | TOTP setup → confirm → MFA verify | Integration | UC-005 | 2h |
| T7 | Edge cases: expired mfaToken, wrong method, concurrent verify | Unit | Exception flows | 2h |
| T8 | Trusted device skip MFA flow | Unit | FR-005 | 1h |

Total test effort: ~14.5h

### Q6: How to handle TOTP encryption key management?

**→ A: Already resolved — custom approach confirmed**

Findings from codebase:
- `TotpService` handles AES-256 encryption/decryption directly
- AES-256 encryption confirmed (via `TOTP_ENCRYPTION_KEY` env var)
- Previous Q8 (Jasypt vs custom EncryptionService) → resolved with custom approach

### Q8: JWKS key rotation — how?

**→ A: Manual rotation with `kid` support (from Q9 previous brainstorm)**

```
Current: JwtService uses SecurityProperties.jwt.keyId = "auth-service-key-1"
JWKS endpoint returns { keys: [{ kid: "auth-service-key-1", ... }] }

Rotation plan:
  1. Generate new key pair
  2. Update config: keyId = "auth-service-key-2", new privateKeyPath/publicKeyPath
  3. Deploy → JWKS returns new key
  4. Resource servers auto-fetch new key (Cache-Control: max-age=86400)
  5. Old tokens verified until they expire (resource servers cache old key for 24h)

This is sufficient for current needs. Automated rotation can be added when KMS is available.
```

### Q9: SSO auto-provision default domain — resolved?

**→ A: YES — SecurityProperties.sso.defaultDomainCode = "default" (from Q10 previous brainstorm)**

SsoAdapter.kt line 137: `domainRepository.findByCodeAndActiveTrue(securityProperties.sso.defaultDomainCode)`

---

## Approaches Considered

### Approach A: Testing-Only Focus
Complet─────────────────────┤
  │ Phase 5: Documentation + Config Defaults (1h)       │
  │   ├── Document CAPTCHA threshold default            │
  │   ├── Document MFA token TTL defaults               │
  │   └── SecurityProperties Javadoc update             │
  └─────────────────────────────────────────────────────┘

  Total: ~15h (2 dev-days)
```

**Pros:**
- ✅ All 17 FRs fully covered
- ✅ Quality score → 95+ (testing gaps fixed)
- ✅ Keycloak SSO actually works
- ✅ Trusted device flow complete
- ✅ Clean architecture (EventPublisher port)

**Cons:**
- Kafka event still deferred (by design — no infra)
- Slightly more code changes than testing-only

### Approach C: Full Hardening + Security Audit
Everything in B + security audit + performance testing + admin documentation.

**Pros:**
- Maximum quality and security confidence
- Production-ready with audit trail

**Cons:**
- ❌ 4-5 dev-days (excessive for remaining 10%)
- ❌ Security audit scope creep risk
- ❌ Performance testing requires load test infrastructure

---

## Selected Direction

**Approach B: Gap Completion + Hardening + Testing**

**Reasoning:**
1. **Keycloak support** is LOW effort, HIGH impact — fixing the "lie" where `getProviders()` returns Keycloak but the code can't handle it.
2. **Trusted device** is the only PARTIAL FR (FR-005) — completing it closes the last functional gap with minimal risk.
3. **EventPublisher port** is clean architecture best practice — 1 hour of work, makes Kafka integration trivial later.
4. **Integration tests** are the biggest quality gap — they cover the critical paths that unit tests can't.
5. **Deferring Kafka** is correct — no infrastructure, and the port abstraction makes future implementation zero-risk.
6. **Approach C is overkill** — security audit and performance testing have separate concerns and timelines.

**Execution order (dependency-aware):**

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
  (SSO)    (Trusted)  (Event)   (Tests)   (Docs)
   │          │          │         │         │
   └────NO DEPENDENCIES──┘         │         │
                                   │         │
                              DEPENDS ON 1-3  │
                                              │
                                        ANYTIME
```

Phases 1-3 can be parallelized. Phase 4 depends on 1-3 (tests need complete code). Phase 5 is independent.

---

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Non-Financial (authentication identity verification)
- Affected modules:
  - `auth/application` (MfaService MODIFY, SsoAdapter MODIFY)
  - `auth/application/port/out` (EventPublisher NEW — port interface)
  - `auth/adapter/out/sso` (OAuth2TokenExchanger MODIFY)
  - `auth/adapter/out/event` (NoopEventPublisher NEW — adapter)
  - `auth/adapter/in/web/dto` (MfaDtos MODIFY — add trustDevice/deviceHash)
  - `shared/config` (SecurityProperties MODIFY — SsoProperties.providers)
  - `test/` (6 NEW integration test classes)

---

## Codebase Investigation Findings

### OAuth2TokenExchanger — Config Refactoring Plan

```
BEFORE (hardcoded):
  getTokenEndpoint("google")     → "https://oauth2.googleapis.com/token"
  getTokenEndpoint("microsoft")  → "https://login.microsoftonline.com/..."
  getTokenEndpoint("keycloak")   → ❌ throws

AFTER (config-driven):
  getTokenEndpoint("google")     → securityProperties.sso.providers["google"]?.tokenEndpoint ?: throw
  getTokenEndpoint("microsoft")  → securityProperties.sso.providers["microsoft"]?.tokenEndpoint ?: throw
  getTokenEndpoint("keycloak")   → securityProperties.sso.providers["keycloak"]?.tokenEndpoint ?: throw
  getTokenEndpoint("custom-idp") → securityProperties.sso.providers["custom-idp"]?.tokenEndpoint ?: throw

Impact: OAuth2TokenExchanger.kt — ~20 lines changed. No public API change.
Config: application.yml — add providers map (Google/Microsoft URLs move from code to config).
```

### MfaService.verifyMfa() — Trusted Device Extension Point

```
CURRENT (line 67-103):
  fun verifyMfa(mfaToken, code, authResponseBuilder) → AuthResponse

PROPOSED:
  fun verifyMfa(mfaToken, code, trustDevice, deviceHash, authResponseBuilder) → AuthResponse
  
  After successful verify (line 99):
    if (trustDevice && !deviceHash.isNullOrBlank()) {
        val user = userRepository.findById(userId).get()
        user.trustedDeviceHash = deviceHash
        userRepository.save(user)
        log.info("Trusted device set for userId={}", userId)
    }
```

### EventPublisher Port Design

```
Port (hexagonal):
  auth/application/port/out/EventPublisher.kt
  
  interface EventPublisher {
      fun publish(topic: String, key: String, payload: Map<String, Any>)
  }

Adapter (noop):
  auth/adapter/out/event/NoopEventPublisher.kt
  
  @Component
  @ConditionalOnMissingBean(EventPublisher::class)
  class NoopEventPublisher : EventPublisher {
      override fun publish(topic: String, key: String, payload: Map<String, Any>) {
          log.info("Event published (noop): topic={}, key={}, payload={}", topic, key, payload)
      }
  }

Usage in SsoAdapter (replace TODO):
  eventPublisher.publish("iam.user.sso_provisioned", user.id.toString(), 
      mapOf("provider" to provider, "email" to idpUser.email))
```

### SecurityConfig — No Changes Needed

```
Verified: All auth endpoints already configured as permitAll:
  /api/auth/login       ✅
  /api/auth/mfa/verify  ✅
  /api/auth/mfa/resend  ✅
  /api/auth/sso/**      ✅
  /.well-known/**       ✅
  /api/auth/introspect  ✅ (internal, but public for service-to-service)
```

### Cross-Feature Coordination Points

```
LoginHandler.kt — SHARED with anonymous-login-optimization:
  Line 117: user.requiresMfa(command.trustedDeviceHash) — auth-core concern
  Line 143-153: sessionPromotionService.promoteSession() — anonymous concern
  
  These are INDEPENDENT concerns. No merge conflict expected.
  auth-core-features does NOT modify lines 143-153.
  anonymous-login-optimization does NOT modify line 117.

AuthCoreExceptions.kt — SHARED with api-response-i18n-standard:
  Existing exceptions: CaptchaRequiredException, MfaCodeInvalidException, etc.
  
  auth-core-features: may add new exception classes (if needed)
  api-response-i18n-standard: adds i18n message codes to ALL exceptions
  
  Coordination: auth-core-features defines exceptions FIRST,
  api-response-i18n-standard adds i18n codes AFTER.
  Order: auth-core-features → api-response-i18n-standard ✅ (pipeline correct)
```

---

## Architecture Diagram (Updated — Final State)

```
                         ┌──────────────────────────────────────────────┐
                         │              auth-service                    │
                         │                                              │
                         │  ┌──────────────────────────────────────┐   │
                         │  │       Adapter In (Web)                │   │
                         │  │  AuthCtrl  MfaCtrl  SsoCtrl  TokenCtrl│   │
                         │  └────────────────┬─────────────────────┘   │
                         │                   │                          │
                         │  ┌────────────────▼─────────────────────┐   │
                         │  │       Application (Services)          │   │
                         │  │                                  usted" status and gets MFA again.
**Mitigation**: This is expected behavior (conservative security). Document hash algorithm for frontend team. MFA re-challenge is not a UX disaster — it's security working correctly.

### Risk 3: Cross-feature merge conflicts
**Severity**: 🟢 LOW
**Issue**: LoginHandler.kt shared with anonymous-login-optimization.
**Mitigation**: Changes are in different code regions (line 117 vs lines 143-153). Auth-core doesn't modify anonymous session code. Pipeline processes auth-core-features AFTER anonymous-login-optimization.

### Risk 4: Test flakiness with Redis/WireMock
**Severity**: 🟡 MEDIUM
**Issue**: Integration tests need Redis (Testcontainers or embedded) + WireMock for IdP
**Mitigation**: Use `@Testcontainers` with Redis image (already used in existing tests). WireMock via `@WireMockTest` annotation.

---

## Open Questions for Design Phase

- [RESOLVED] Q8: TOTP encryption → custom AES-256-GCM in TotpService ✅
- [RESOLVED] Q9: JWKS rotation → manual with kid support ✅
- [RESOLVED] Q10: SSO default domain → SecurityProperties.sso.defaultDomainCode ✅
- [OPEN] Q11: Trusted device TTL — should `trusted_device_set_at` column be added in V-next migration or deferred?
  → Recommend: Defer to separate migration. Current hash comparison works without TTL (conservative: never expires until hash changes). TTL is a "nice to have" enhancement.
- [OPEN] Q12: Should EventPublisher use Spring ApplicationEventPublisher (in-process) or define custom port?
  → Recommend: Custom port — cleaner hexagonal boundary, future Kafka integration is explicit, not hidden behind Spring event bus.

## Open Questions for URD Analysis

- None — URD analysis is complete in pre_openspec.md.

---

## Design Decisions Summary (Updated from 2026-08-05)

| # | Decision | Rationale | Status | Impact |
|---|----------|-----------|--------|--------|
| D1 | Sealed class `LoginResult` | Type-safe MFA vs Success | ✅ IMPLEMENTED | - |
| D2 | MFA token = JWT stateless (5min TTL) | Reduce Redis dependency | ✅ IMPLEMENTED | - |
| D3 | RS256 via refresh-based migration | Non-breaking, 7-day auto-cleanup | ✅ IMPLEMENTED | - |
| D4 | CAPTCHA at service level | Conditional check on failedLoginCount | ✅ IMPLEMENTED | - |
| D5 | SSO hybrid (OAuth2 Client + manual controller) | SPA-compatible | ✅ IMPLEMENTED | - |
| D6 | UserEntity columns (not separate entity) | Avoid N+1 on login | ✅ IMPLEMENTED | - |
| D7 | Passay factory per domain (ConcurrentHashMap cache) | Performance | ✅ IMPLEMENTED | - |
| D8 | SSO-only: passwordHash = "!SSO_ONLY!" | Keep NOT NULL constraint | ✅ IMPLEMENTED | - |
| D9 | Redis key namespace: `otp:{userId}:{channel}` | Clean TTL cleanup | ✅ IMPLEMENTED | - |
| D10 | Feature config via SecurityProperties | Simple, env-injectable | ✅ IMPLEMENTED | - |
| D11 | **[NEW]** Config-driven SSO providers (SsoProperties.providers map) | Keycloak support + extensibility | PENDING | OAuth2TokenExchanger, SecurityProperties |
| D12 | **[NEW]** Trusted device save on MFA verify success | Complete FR-005 flow | PENDING | MfaService, MfaDtos |
| D13 | **[NEW]** EventPublisher port + NoopEventPublisher adapter | Hexagonal architecture for deferred Kafka | PENDING | SsoAdapter, new port/adapter |
| D14 | **[NEW]** Defer trusted device TTL to separate migration | Minimize V-next migration scope | PENDING | Future migration |
