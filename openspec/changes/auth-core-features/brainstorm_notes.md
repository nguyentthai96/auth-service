---
type: brainstorm_notes
change: auth-core-features
date: 2026-08-26
selected_direction: "Testing, Hardening & Legacy Cleanup"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Core Features — Hardening & Completion

## Date
2026-08-26

## Context
Auth-core-features is an EXTEND change for the auth-service's core authentication capabilities: MFA (OTP/TOTP/CAPTCHA/Recovery Codes), SSO/OAuth2 (Google/Microsoft/Keycloak), JWT RS256 + Introspection + JWKS, and Password Policy per domain. The pre_openspec (v5, 2026-08-26) identifies 17 FRs with ~97% implementation complete and 2 remaining gaps. Feature research (2026-08-22) recommends "build from scratch using open source libraries" with a confidence of HIGH.

**Critical discovery during codebase investigation**: Both previously identified gaps are **already resolved** in code, invalidating the pre_openspec gap assessment. The focus must shift entirely to testing, hardening, and architectural cleanup.

## Questions Asked & Answers

### Q1: Are the 2 identified gaps (Keycloak + Trusted Device TTL) actually still open?
**A: NO — both are already resolved.**

**Gap 1 (Keycloak support):** The pre_openspec (Section 8, Issue #1) states Keycloak is missing from `OAuth2TokenExchanger.getTokenEndpoint()`. However, the actual code shows:
- `OAuth2TokenExchanger.kt` is **fully config-driven** — provider endpoints are resolved from `securityProperties.sso.providers[provider]` map, not hardcoded. Any provider (Google, Microsoft, Keycloak, custom) works via configuration.
- `application-security.yml` already has Keycloak configured:
  ```yaml
  keycloak:
    token-endpoint: ${KEYCLOAK_TOKEN_ENDPOINT:http://localhost:8080/realms/master/protocol/openid-connect/token}
    user-info-endpoint: ${KEYCLOAK_USERINFO_ENDPOINT:http://localhost:8080/realms/master/protocol/openid-connect/userinfo}
  ```
- `SsoCallbackIntegrationTest.kt` has TC3: "SSO callback for Keycloak should use config-driven endpoints" — **green and passing**.
- `SsoAdapterTest.kt` includes Keycloak in its test provider config.

**Conclusion:** Keycloak is fully supported. The pre_openspec Issue #1 is STALE.

**Gap 2 (Trusted device TTL enforcement):** The pre_openspec (Section 7, Quality Score deduction) states TTL enforcement is pending. However:
- `User.kt` domain model has `requiresMfa(deviceHash, ttlDays)` method that:
  1. Checks `mfaEnabled` and `mfaMethod`
  2. Compares `deviceHash == trustedDeviceHash`
  3. **Checks TTL**: `trustedDeviceSetAt.plus(ttlDays, DAYS).isBefore(now())` ✅
- `LoginHandler.kt` (CQRS path) calls `user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)` — **full TTL enforcement** ✅
- `UserTest.kt` has **13 test cases** for `requiresMfa()` including TTL edge cases (expired, not expired, null setAt, wrong hash, custom TTL)
- `SecurityProperties.kt` has `trustedDeviceTtlDays: Long = 30` configurable

**However:** `AuthService.kt` (legacy non-CQRS path, line 134-136) still has naive check:
```kotlin
if (trustedHash != null && trustedHash == user.trustedDeviceHash) {
    log.debug("Trusted device matched — skipping MFA for userId={}", user.id)
}
```
This **does NOT check TTL**. This is the legacy login path that exists alongside the CQRS `LoginHandler`.

**Conclusion:** Trusted device TTL is fully enforced on the CQRS path (`LoginHandler`), but the legacy `AuthService.login()` path has a security gap. Needs alignment.

### Q2: What is the relationship between AuthService.login() and LoginHandler?
**A: Dual login paths — potential security inconsistency.**

The codebase has two login paths:
1. **Legacy path**: `AuthController.kt` → `AuthService.login()` — direct service call, simpler, used by `/api/auth/login`
2. **CQRS path**: `CqrsAuthController.kt` → `LoginHandler.handle()` — command/handler pattern, full-featured, used by `/api/v2/auth/login`

Both paths exist simultaneously. The CQRS path has more features:
- ✅ Trusted device TTL enforcement (via `User.requiresMfa()`)
- ✅ Password expiry check (via `passwordPolicyService.isPasswordExpired()`)
- ✅ Session policy enforcement (via `sessionPolicyService.enforcePolicy()`)
- ✅ Token event recording (via `TokenEventRecorder`)
- ✅ Audit logging

The legacy `AuthService.login()` path:
- ⚠️ Has trusted device check WITHOUT TTL enforcement
- ✅ Has password expiry check
- ⚠️ May not have full session policy enforcement

**Decision needed**: Should we deprecate `AuthService.login()` and route everything through `LoginHandler`?

### Q3: What is the test coverage status?
**A: Good coverage but some gaps remain.**

**Existing test files (20 total):**

| Category | Files | Coverage Areas |
|----------|-------|---------------|
| Unit (application) | 13 | MfaService, OtpService, PasswordPolicy, SsoAdapter, MfaRateLimit, DomainLookup, JwtService, AuthServiceRevoke, SessionPromotion, AnonymousRate, AnonymousData, MfaEdgeCase |
| Unit (domain) | 1 | User domain model (requiresMfa, status transitions) |
| Integration | 6 | MFA login flow, SSO callback, Password change, Token introspection, JWKS endpoint, TOTP setup |
| Architecture | 1 | Architecture rules |
| Other integration | 2 | Anonymous session, Session promotion |

**Identified test gaps:**
1. **No EventService/TokenEventRecorder tests** — Event sourcing pipeline untested
2. **No OutboxPoller tests** — Transactional outbox relay untested
3. **No KafkaEventPublisher integration tests** — Event publishing untested
4. **No LoginHandler unit tests** — CQRS login command handler untested (only integration)
5. **No AdminSessionController tests** — Admin force logout untested
6. **No CaptchaVerifier/AltchaCaptchaVerifier tests** — CAPTCHA chain untested
7. **No IdempotencyFilter tests** — Idempotency mechanism untested
8. **No recovery code flow tests** — MFA recovery codes untested
9. **No rate limit filter integration tests** — LoginRateLimitFilter untested
10. **No password expiry on login tests** — Password expiry check during login untested

### Q4: Are there any security concerns beyond the dual login path?
**A: Several minor hardening opportunities.**

1. **AuthService.kt trusted device bypass** — No TTL check (security gap on legacy path)
2. **SecurityProperties.kt KDoc says** "TTL enforcement deferred to future migration" — stale documentation, already implemented in `User.requiresMfa()`
3. **MfaService.kt line 121** sets `user.trustedDeviceHash` but should also set `user.trustedDeviceSetAt = Instant.now()` — need to verify
4. **PasswordPolicyService.kt line 100** clears `trustedDeviceHash` on password change — correct security behavior
5. **Rate limit fail-open strategy** in `MfaRateLimitService` — if Redis is down, MFA rate limiting is skipped. Acceptable trade-off (documented in pre_openspec) but should be monitored

### Q5: What's the architecture decision on CQRS vs legacy?
**A: Migrate fully to CQRS, deprecate legacy AuthService direct methods.**

The CQRS path (`LoginHandler`, `RegisterHandler`, `RefreshTokenHandler`, etc.) is more complete:
- Proper command validation
- Full session policy enforcement
- Token event recording
- Audit logging
- Trusted device TTL enforcement

The legacy `AuthService` methods should:
1. Be marked `@Deprecated` with migration note
2. Delegate to command handlers internally (if keeping backward compatibility)
3. Eventually be removed once `AuthController` is consolidated into `CqrsAuthController`

### Q6: What about the SecurityProperties KDoc stale comments?
**A: Should be updated to reflect current reality.**

`SecurityProperties.kt` line 14-15 says:
> "Trusted device: `mfa.trustedDeviceTtlDays` (default: 30 days) — currently stored as SHA-256 hash on UserEntity, **TTL enforcement deferred to future migration** (no Redis-backed expiry yet)."

This is **STALE** — TTL enforcement is implemented in `User.requiresMfa()` via `trustedDeviceSetAt` column (V10 migration). The KDoc should be updated.

## Approaches Considered

### Approach A: Testing & Hardening Only
Focus exclusively on comprehensive test coverage and security hardening.
- **Pros**: Maximum safety, fills all test gaps, validates existing code
- **Cons**: Doesn't address architectural debt (dual login paths, stale docs)
- **Effort**: 3-4 developer-days

### Approach B: New Feature Exploration (WebAuthn/FIDO2)
Explore adding WebAuthn/passkey support as next evolution of MFA.
- **Pros**: Forward-looking, industry trend
- **Cons**: Out of scope for current EXTEND change, adds complexity, premature
- **Effort**: 8-12 developer-days

### Approach C: Architectural Cleanup Only
Focus on consolidating CQRS paths, deprecating legacy, updating docs.
- **Pros**: Reduces tech debt, single source of truth for login flow
- **Cons**: Risky without comprehensive tests, doesn't add new value
- **Effort**: 2-3 developer-days

### Approach D: Hybrid — Testing + Architectural Cleanup (SELECTED)
Primary: Comprehensive testing. Secondary: Legacy path alignment + doc updates.
- **Pros**: Best risk-value balance, tests validate cleanup, complete deliverable
- **Cons**: Slightly more effort than single-focus approach
- **Effort**: 4-5 developer-days

## Selected Direction

**Approach D: Hybrid — Testing, Hardening & Legacy Cleanup**

**Reasoning:**
1. The codebase is ~98% complete (both "gaps" from pre_openspec are resolved). The highest-value work is **validation through testing**.
2. The dual login path (`AuthService.login()` vs `LoginHandler.handle()`) creates a **real security risk** — the legacy path skips TTL enforcement. This must be fixed.
3. Event sourcing infrastructure (`EventService`, `TokenEventRecorder`, `OutboxPoller`) is new and completely untested — critical for reliability.
4. Stale documentation in `SecurityProperties.kt` could mislead future developers.
5. Tests provide regression safety for all existing 17 FRs.

**Work breakdown:**
1. **Legacy path alignment** — Update `AuthService.kt` to use `User.requiresMfa()` with TTL, or deprecate in favor of CQRS path
2. **Test coverage expansion** — 10 new test files covering untested areas:
   - EventService + TokenEventRecorder (event sourcing)
   - OutboxPoller (transactional outbox)
   - LoginHandler (CQRS login)
   - AdminSessionController (force logout)
   - CaptchaVerifier chain
   - IdempotencyFilter
   - MFA recovery codes
   - LoginRateLimitFilter
   - Password expiry on login
   - WireMock-based SSO provider tests
3. **Documentation updates** — Fix stale KDoc in SecurityProperties.kt
4. **Pre_openspec corrections** — Mark Issue #1 (Keycloak) as RESOLVED, update Quality Score

## Pre-classifications (preliminary)
- Feature type: EXTEND (confirmed — enhancing existing auth-core with testing + hardening)
- Flow type: Non-Financial (confirmed — authentication/identity, no financial transactions)
- Affected modules:
  - `auth/application` — AuthService.kt legacy path fix, all application services under test
  - `auth/application/command` — LoginHandler test coverage
  - `auth/application/event` — EventService, TokenEventRecorder test coverage
  - `auth/adapter/out/event` — OutboxPoller test coverage
  - `auth/adapter/in/web` — AdminSessionController, CaptchaController test coverage
  - `auth/adapter/in/web/filter` — LoginRateLimitFilter, IdempotencyFilter test coverage
  - `auth/domain/model` — User domain model (already well-tested, verify TTL edge cases)
  - `shared/config` — SecurityProperties KDoc updates
  - `shared/filter` — IdempotencyFilter test coverage

## Codebase Investigation Findings

### Finding 1: OAuth2TokenExchanger is Config-Driven (Keycloak RESOLVED)

```
    ┌──────────────────────┐
    │   SecurityProperties │
    │  sso.providers map   │
    │  ┌──────────────┐    │
    │  │ google:      │    │
    │  │  tokenUrl    │    │
    │  │  userInfoUrl │    │
    │  ├──────────────┤    │
    │  │ microsoft:   │    │
    │  │  tokenUrl    │    │
    │  │  userInfoUrl │    │
    │  ├──────────────┤    │
    │  │ keycloak:    │    │  ← Already configured
    │  │  tokenUrl    │    │  ← Environment-driven
    │  │  userInfoUrl │    │
    │  └──────────────┘    │
    └──────────┬───────────┘
               │
    ┌──────────▼───────────┐
    │ OAuth2TokenExchanger │
    │                      │
    │ getTokenEndpoint()   │──→ providers[provider]?.tokenEndpoint
    │ getUserInfoEndpoint()│──→ providers[provider]?.userInfoEndpoint
    │                      │
    │ NO hardcoded URLs    │  ← Config-driven since refactor
    └──────────────────────┘
```

### Finding 2: Trusted Device TTL — Dual Path Inconsistency

```
    ┌─────────────────────────────────────────────────────┐
    │                  Login Request                       │
    └────────────┬────────────────────────┬───────────────┘
                 │                        │
    ┌────────────▼───────────┐  ┌────────▼────────────────┐
    │ AuthController         │  │ CqrsAuthController      │
    │ POST /api/auth/login   │  │ POST /api/v2/auth/login │
    └────────────┬───────────┘  └────────┬────────────────┘
                 │                        │
    ┌────────────▼───────────┐  ┌────────▼────────────────┐
    │ AuthService.login()    │  │ LoginHandler.handle()   │
    │                        │  │                          │
    │ trustedHash ==         │  │ user.requiresMfa(        │
    │   user.trustedDevice   │  │   hash,                  │
    │   Hash                 │  │   securityProperties     │
    │                        │  │   .mfa.trustedDeviceTtl  │
    │ ⚠️ NO TTL CHECK       │  │   Days                   │
    │                        │  │ )                        │
    │ SECURITY GAP           │  │ ✅ TTL ENFORCED         │
    └────────────────────────┘  └──────────────────────────┘
```

**Resolution**: Align `AuthService.login()` to use `User.requiresMfa()` or deprecate legacy path.

### Finding 3: Event Sourcing Pipeline (Complete but Untested)

```
    ┌─────────────┐     ┌──────────────┐     ┌──────────────┐
    │ TokenEvent   │────▶│ EventService │────▶│ EventStore   │
    │ Recorder     │     │              │     │ Port         │
    │              │     │ record()     │     │              │
    │ recordIssu-  │     │ ┌──────────┐ │     │ append()     │
    │ ance()       │     │ │ envelope │ │     └──────┬───────┘
    │ recordRevo-  │     │ │ → store  │ │            │
    │ cation()     │     │ │ → outbox │ │     ┌──────▼───────┐
    └─────────────┘     │ └──────────┘ │     │ EventStore   │
                         └──────────────┘     │ Persistence  │
                                              │ Adapter      │
    ┌─────────────┐     ┌──────────────┐     └──────────────┘
    │ OutboxPoller │────▶│ Kafka Event  │
    │              │     │ Publisher    │
    │ @Scheduled   │     │              │
    │ SELECT FOR   │     │ @Primary     │
    │ UPDATE SKIP  │     │ @Conditional │
    │ LOCKED       │     │ OnProperty   │
    │              │     │              │
    │ batch=50     │     │ retry=3      │
    │ poll=100ms   │     │ backoff=exp  │
    └──────────────┘     └──────────────┘
    
    ⚠️ ZERO TEST COVERAGE for this pipeline
```

### Finding 4: Test Coverage Map

```
    ┌────────────────────────────────────────────────────────┐
    │ Test Coverage Assessment                               │
    │                                                        │
    │ ✅ Well-tested (unit + integration):                   │
    │   • MfaService (10KB + 7KB edge cases)                │
    │   • OtpService (4KB)                                  │
    │   • PasswordPolicyService (7KB)                       │
    │   • SsoAdapter (8KB) + SSO integration (10KB)         │
    │   • JwtService anonymous (6KB)                        │
    │   • MfaRateLimitService (9KB)                         │
    │   • User domain model (10KB)                          │
    │   • MFA login flow integration (13KB)                 │
    │   • Token introspection integration (5KB)             │
    │   • JWKS endpoint integration (5KB)                   │
    │   • Password change integration (9KB)                 │
    │   • TOTP setup flow integration (10KB)                │
    │   • Session promotion (11KB + 11KB)                   │
    │   • Anonymous session (9KB)                           │
    │   • AuthService revoke sessions (4KB)                 │
    │                                                        │
    │ ❌ UNTESTED:                                           │
    │   • EventService + TokenEventRecorder                 │
    │   • OutboxPoller (transactional outbox)                │
    │   • KafkaEventPublisher                               │
    │   • LoginHandler (CQRS command)                       │
    │   • AdminSessionController                            │
    │   • CaptchaVerifier / AltchaCaptchaVerifier           │
    │   • IdempotencyFilter                                 │
    │   • MFA Recovery Codes flow                           │
    │   • LoginRateLimitFilter                              │
    │   • Password expiry on login                          │
    │   • ServiceTokenService + ServiceAuthFilter           │
    └────────────────────────────────────────────────────────┘
```

## Detailed Design Hints for `/wf_openspec`

### Task 1: Legacy Login Path TTL Fix
```kotlin
// AuthService.kt — Replace lines 131-137:
// FROM:
if (user.mfaEnabled && user.mfaMethod != "NONE") {
    val trustedHash = request.trustedDeviceHash
    if (trustedHash != null && trustedHash == user.trustedDeviceHash) {
        log.debug("Trusted device matched — skipping MFA")
    } else {
        return mfaService.initiateMfa(user.id!!, user.mfaMethod)
    }
}

// TO (use domain method with TTL):
val domainUser = userMapper.toDomain(user) // or construct User domain model
if (domainUser.requiresMfa(request.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
    return mfaService.initiateMfa(user.id!!, user.mfaMethod)
}
```

### Task 2: SecurityProperties KDoc Fix
```kotlin
// Update comment at line 14-15:
// FROM: "TTL enforcement deferred to future migration"
// TO: "TTL enforcement implemented in User.requiresMfa() via trustedDeviceSetAt column (V10 migration)"
```

### Task 3: MfaService Trusted Device SetAt Verification
```kotlin
// MfaService.kt line 121 — verify this also sets trustedDeviceSetAt:
user.trustedDeviceHash = deviceHash
user.trustedDeviceSetAt = Instant.now()  // Must be set for TTL to work
```

### Test Strategy Matrix

| Test File | Type | Priority | Key Scenarios |
|-----------|------|----------|--------------|
| LoginHandlerTest.kt | Unit | HIGH | MFA checkpoint, TTL enforcement, password expiry, session policy |
| EventServiceTest.kt | Unit | HIGH | Event envelope creation, store + outbox in transaction |
| TokenEventRecorderTest.kt | Unit | HIGH | Issuance recording, revocation recording |
| OutboxPollerTest.kt | Integration | HIGH | Batch processing, retry logic, Kafka publish |
| AdminSessionControllerTest.kt | Integration | MEDIUM | Force logout, list sessions, session stats |
| CaptchaVerifierTest.kt | Unit | MEDIUM | CAPTCHA chain, noop provider, ALTCHA PoW |
| IdempotencyFilterTest.kt | Unit | MEDIUM | Redis key, TTL 24h, duplicate detection |
| MfaRecoveryCodeTest.kt | Integration | MEDIUM | Generate, verify, single-use, count |
| LoginRateLimitFilterTest.kt | Integration | MEDIUM | IP limit, username limit, device limit |
| PasswordExpiryLoginTest.kt | Integration | LOW | Login with expired password → 403 |

## Open Questions for Design Phase
- [RESOLVED] Q1 (Keycloak support): OAuth2TokenExchanger is fully config-driven. Keycloak configured in application-security.yml. Tests passing.
- [RESOLVED] Q2 (Trusted device TTL): Implemented in User.requiresMfa() domain method. LoginHandler uses it correctly. Only AuthService.kt legacy path needs alignment.
- [OPEN] Q3: Should `AuthService.login()` (legacy path) be deprecated and consolidated into `LoginHandler.handle()` (CQRS path), or should it be fixed to match CQRS behavior? Decision impacts: controller routing, backward compatibility, test scope.
  - **Recommendation**: Fix legacy path to use `User.requiresMfa()` for security, mark `@Deprecated`, plan consolidation in future sprint.
- [OPEN] Q4: Should MFA recovery code flow have its own controller endpoint or be integrated into existing MfaController? Currently `MfaRecoveryCodeEntity` + repository exist but no controller endpoint found.
  - **Recommendation**: Add to `MfaController` — `POST /api/auth/mfa/recovery/generate`, `POST /api/auth/mfa/recovery/verify`, `GET /api/auth/mfa/recovery/count`.
- [OPEN] Q5: Event sourcing tests — should they use embedded Kafka or mock KafkaEventPublisher? 
  - **Recommendation**: Unit tests mock `EventStorePort` + `OutboxPort`. Integration tests use `@EmbeddedKafka` or Testcontainers for OutboxPoller → Kafka round-trip.

## Open Questions for URD Analysis
- None — URD analysis complete via research artifacts + pre_openspec. All FRs mapped and traced.

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| AuthService.kt legacy path exploited (no TTL on trusted device) | LOW | MEDIUM | Priority fix — align with User.requiresMfa() |
| Event sourcing pipeline silent failures (no tests) | MEDIUM | HIGH | Comprehensive unit + integration tests |
| OutboxPoller batch processing edge cases | LOW | MEDIUM | Test with boundary conditions (0, 1, 50, 51 items) |
| Stale documentation misleading developers | LOW | LOW | KDoc updates in SecurityProperties.kt |
| Test infrastructure complexity (embedded Kafka, Redis) | LOW | LOW | Use Testcontainers (already likely in project) |

## Summary Statistics

| Metric | Value |
|--------|-------|
| FRs total | 17 |
| FRs fully implemented | 17 (100%) |
| Pre_openspec gaps identified | 2 |
| Pre_openspec gaps actually remaining | 0 (both resolved) |
| New gap discovered | 1 (AuthService.kt legacy TTL bypass) |
| Test files existing | 20 |
| Test files needed | ~10 new |
| Estimated effort | 4-5 developer-days |
| Risk level | LOW (all code exists, just needs validation) |
