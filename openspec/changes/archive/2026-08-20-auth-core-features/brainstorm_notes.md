---
type: brainstorm_notes
change: auth-core-features
date: 2026-08-20
selected_direction: "Approach B: Remaining Gaps Closure + Testing Validation + Production Readiness"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Core Features — Final Gaps & Production Readiness

## Date
2026-08-20

## Context

This is a **third-pass brainstorm** (refreshed after `wf_openspec_apply` failure due to API error). Since the last brainstorm (2026-08-19), significant implementation progress has occurred. The codebase has advanced from ~90% to ~95%+ complete. Most design decisions from the previous brainstorm (D11–D14) have been **already implemented** in code:

**Implementation Progress Since Last Brainstorm (2026-08-19 → 2026-08-20):**

| Design Decision | Status | Evidence |
|----------------|--------|----------|
| D11: Config-driven SSO providers | ✅ IMPLEMENTED | `OAuth2TokenExchanger.kt` — reads from `securityProperties.sso.providers[provider]` (lines 42-46) |
| D12: Trusted device save on MFA verify | ✅ IMPLEMENTED | `MfaService.verifyMfa()` — `trustDevice`/`deviceHash` params, saves to `user.trustedDeviceHash` (lines 65-71, 109-118) |
| D13: EventPublisher port + adapter | ✅ IMPLEMENTED | `EventPublisher.kt` port + `SsoProvisionedEvent` + `SpringEventPublisher` adapter + `SsoAdapter` emits event (line 76) |
| D14: Defer trusted device TTL | ✅ DEFERRED (by design) | No `trusted_device_set_at` column — hash comparison works without TTL |
| MfaDtos update (trustDevice/deviceHash) | ✅ IMPLEMENTED | `MfaDtos.kt:16` — `trustDevice: Boolean = false` |
| MfaController wiring | ✅ IMPLEMENTED | `MfaController.kt:26` — passes `trustDevice` to service |
| Integration tests (6 tests) | ✅ IMPLEMENTED | `integration/` dir: MfaLoginFlow, SsoCallback, TokenIntrospection, JWKS, PasswordChange, TotpSetup |
| Edge case tests | ✅ IMPLEMENTED | `MfaServiceEdgeCaseTest.kt` (183 lines) — concurrent verify idempotency, method mismatch |
| SecurityProperties.SsoProperties.providers | ✅ IMPLEMENTED | `SecurityProperties.kt:133` — `providers: Map<String, ProviderConfig>` with `ProviderConfig` data class |

**Source context:**
- `pre_openspec.md` (2026-08-20): 17 FRs, quality 92/100, 2 issues (🟡)
- Research artifacts: 7 files complete
- Previous brainstorm (2026-08-19): 14 design decisions, 12 IMPLEMENTED, 2 DEFERRED by design
- **Pipeline**: `anonymous-login-optimization` ✅ archived, `api-response-i18n-standard` ✅ archived

---

## Questions Asked & Answers

### Q1: What EXACTLY remains unfinished after the D11-D14 implementations?

**→ A: 5 micro-gaps + 2 quality items — all LOW complexity**

**Code scan results (2026-08-20):**

```
Remaining TODOs in auth-related files:

1. MfaService.kt:45        → "TODO: dispatch actual SMS/Email via notification service"
2. MfaService.kt:205       → "TODO: dispatch actual SMS/Email"
3. SsoAdapter.kt:170       → "TODO: Implement per-provider token exchange" (STALE — already done!)
4. AuthService.kt:288      → "TODO: count revoked tokens" (revokeAllSessions partial)
5. TokenStorePersistenceAdapter.kt:47 → "TODO: Add custom query for batch revocation"
6. CqrsAuthController.kt:197  → "TODO: resolve domainId from user's active domain"
7. CqrsAuthController.kt:206  → "TODO: trigger password reset email"
8. AuthController.kt:67    → "TODO: resolve domainId from user's active domain"
9. AuthController.kt:75    → "TODO: trigger password reset email"
10. AuditLogService.kt:47   → "TODO: persist to audit_log table"
11. PermissionChangedConsumer.kt:31 → "TODO: Parse JSON and invalidate specific user+domain"
```

**Analysis of each TODO — in-scope vs out-of-scope:**

| # | File | TODO | In Scope? | Rationale |
|---|------|------|-----------|-----------|
| 1,2 | MfaService | SMS/Email dispatch | ❌ OUT | Notification service is external dependency (adapter pattern defers this) |
| 3 | SsoAdapter | Per-provider exchange | ⚠️ STALE TODO | Already implemented in OAuth2TokenExchanger! Comment should be removed |
| 4 | AuthService | Count revoked tokens | ✅ IN | `revokeAllSessions()` returns 0 — should count actual revocations |
| 5 | TokenStoreAdapter | Batch revocation query | ✅ IN | Needed for `revokeAllSessions()` to work properly |
| 6,8 | Controllers | Resolve domainId | ⚠️ PARTIAL | Password change needs domainId for policy lookup |
| 7,9 | Controllers | Password reset email | ❌ OUT | Notification service dependency |
| 10 | AuditLogService | Persist to table | ❌ OUT | Separate concern (audit infrastructure) |
| 11 | PermissionConsumer | Parse JSON invalidation | ❌ OUT | Kafka consumer enhancement |

**In-scope gaps summary:**

```
┌─────────────────────────────────────────────────────────────────────┐
│ Gap A: revokeAllSessions() — incomplete implementation              │
│   AuthService.kt:281-289                                            │
│   - Currently: returns 0, doesn't blacklist JTIs or revoke tokens   │
│   - Needed: blacklist all active JTIs + revoke all refresh tokens   │
│   - Depends on: TokenStorePersistenceAdapter batch query            │
│   Impact: FR-012 (Force Logout) — [MODIFY] AdminSessionController  │
│   Effort: ~2h                                                       │
├─────────────────────────────────────────────────────────────────────┤
│ Gap B: SsoAdapter.getProviders() — hardcoded provider list          │
│   SsoAdapter.kt:83-91                                               │
│   - Currently: returns hardcoded [google, microsoft, keycloak]      │
│   - Should: read from securityProperties.sso.providers.keys         │
│   - Inconsistency: OAuth2TokenExchanger is config-driven but        │
│     getProviders() doesn't use the same config                     │
│   Impact: FR-006 — consistency fix                                  │
│   Effort: ~15min                                                    │
├─────────────────────────────────────────────────────────────────────┤
│ Gap C: Stale TODO comment in SsoAdapter.kt:170                     │
│   - exchangeCodeForUser() has a TODO that's already resolved        │
│   - OAuth2TokenExchanger handles all providers config-driven        │
│   Impact: Code cleanliness                                          │
│   Effort: ~5min (remove comment)                                    │
├─────────────────────────────────────────────────────────────────────┤
│ Gap D: domainId resolution in password change controllers           │
│   AuthController.kt:67, CqrsAuthController.kt:197                 │
│   - Currently: TODO comment, domainId not resolved                  │
│   - Needed for: PasswordPolicyService requires domainId             │
│   - Solution: Get from authenticated user's active domain           │
│   Impact: FR-013 (Password Policy) — controller wiring              │
│   Effort: ~30min                                                    │
├─────────────────────────────────────────────────────────────────────┤
│ Gap E: Trusted device TTL (deferred from D14)                      │
│   - No `trusted_device_set_at` column                               │
│   - Current behavior: trust never expires until hash changes        │
│   - This is ACCEPTABLE for now (conservative security)              │
│   - Phase 2 enhancement: Add column + V-next migration              │
│   Impact: FR-005 — enhancement (not a bug)                          │
│   Effort: DEFERRED                                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### Q2: Is `revokeAllSessions()` a blocking issue or can it be deferred?

**→ A: Should be completed now — FR-012 is marked [REUSE] but the implementation is hollow**

```
Current implementation audit:

  revokeAllSessions(userId: Long): Int {
      val user = userRepository.findById(userId).orElseThrow { ... }
      log.info("All sessions revoked for userId={}", userId)
      return 0 // TODO: count revoked tokens
  }

What it SHOULD do:
  1. Find all non-revoked refresh tokens for user → revoke them
  2. Find all active login sessions for user → end them
  3. Blacklist all active JTIs (access tokens) → TokenBlacklistRepository
  4. Return count of revoked sessions/tokens

Dependencies:
  - RefreshTokenRepository.findAllByUserIdAndRevokedFalse() → EXISTS? Need to check
  - LoginSessionRepository.deleteAllByUserId() → EXISTS? Need to check  
  - TokenBlacklistRepository.save() → EXISTS ✅

Design approach:
  1. Query active refresh tokens → set revoked=true
  2. Delete login sessions from DB/Redis
  3. Note: We can't blacklist all access token JTIs easily (we don't store issued JTIs)
     → Accept that existing access tokens expire naturally (max 15min)
     → This is industry-standard behavior for JWT revocation
  4. Return count of revoked refresh tokens
```

**Recommendation**: Complete `revokeAllSessions()` with refresh token revocation + session cleanup. Accept that access tokens expire naturally (15min max). This is the standard approach for stateless JWTs.

### Q3: How should `getProviders()` be fixed to use config?

**→ A: Simple config-driven approach — 5 lines of code**

```kotlin
// BEFORE (hardcoded):
fun getProviders(): List<SsoProviderInfo> {
    if (!securityProperties.sso.enabled) return emptyList()
    return listOf(
        SsoProviderInfo("google", "Google", true),
        SsoProviderInfo("microsoft", "Microsoft", true),
        SsoProviderInfo("keycloak", "Keycloak", true)
    )
}

// AFTER (config-driven):
fun getProviders(): List<SsoProviderInfo> {
    if (!securityProperties.sso.enabled) return emptyList()
    return securityProperties.sso.providers
        .filter { (_, config) -> config.enabled }
        .map { (id, _) -> SsoProviderInfo(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            enabled = true
        )}
}
```

This ensures the provider list always matches what `OAuth2TokenExchanger` can actually handle. No more "Keycloak shows up in list but throws error on callback" scenario.

### Q4: What is the testing gap after the 6 integration tests were added?

**→ A: Test coverage is now GOOD — only minor edge cases remain**

```
Test Inventory (current):

  Unit Tests (11):
    ✅ MfaServiceTest.kt (9888 bytes)
    ✅ MfaServiceEdgeCaseTest.kt (7025 bytes) — concurrent verify, method mismatch
    ✅ OtpServiceTest.kt (4010 bytes)
    ✅ PasswordPolicyServiceTest.kt (7166 bytes)
    ✅ SsoAdapterTest.kt (5932 bytes)
    ✅ MfaRateLimitServiceTest.kt (8722 bytes)
    ✅ AnonymousRateLimitServiceTest.kt (5272 bytes)
    ✅ AnonymousSessionDataServiceTest.kt (6526 bytes)
    ✅ JwtServiceAnonymousTest.kt (5675 bytes)
    ✅ SessionPromotionServiceTest.kt (10694 bytes)
    ✅ LoginHandler test (in command/ dir)

  Integration Tests (8):
    ✅ MfaLoginFlowIntegrationTest.kt (9678 bytes) — full 2-phase flow
    ✅ SsoCallbackIntegrationTest.kt (10177 bytes) — WireMock IdP
    ✅ TokenIntrospectionIntegrationTest.kt (5306 bytes)
    ✅ JwksEndpointIntegrationTest.kt (4559 bytes)
    ✅ PasswordChangeIntegrationTest.kt (8795 bytes)
    ✅ TotpSetupFlowIntegrationTest.kt (9303 bytes)
    ✅ AnonymousSessionIntegrationTest.kt (8550 bytes)
    ✅ SessionPromotionIntegrationTest.kt (10489 bytes)

  Architecture Tests (1):
    ✅ architecture/ directory

  Missing (LOW priority):
    ❌ Trusted device skip MFA integration test
    ❌ revokeAllSessions integration test (once Gap A is fixed)
    ❌ SsoAdapter.getProviders() config-driven test
```

**Testing assessment**: Coverage is SUFFICIENT for `/wf_openspec`. The 3 missing tests correspond to code that still has gaps (Gaps A, B). Tests will be specified in the OpenSpec task list alongside the code fixes.

### Q5: Cross-feature coordination — any conflicts with archived features?

**→ A: No conflicts — pipeline order correct, all overlapping features archived**

```
Coordination Analysis:

  anonymous-login-optimization (ARCHIVED ✅):
    - Overlaps LoginHandler.kt (lines 143-153: session promotion)
    - auth-core-features touches different lines (line 117: requiresMfa)
    - ✅ NO CONFLICT — independent code regions

  api-response-i18n-standard (ARCHIVED ✅):
    - Overlaps AuthCoreExceptions.kt (adds i18n codes)
    - auth-core-features may add new exceptions
    - ✅ Pipeline order correct: auth-core → api-response-i18n already applied
    - New exceptions from auth-core will get i18n in next pass (if needed)

  erp-iam-system (ARCHIVED ✅):
    - Architecture scaffold — no code overlap
    - ✅ NO CONFLICT
```

### Q6: Should the OpenSpec focus on only the remaining gaps, or re-spec the entire feature?

**→ A: GAPS ONLY — per DIFF AWARENESS rule (EXTEND type)**

Since this is an EXTEND feature with ~95% code already implemented:
- Spec ONLY the remaining gaps (A, B, C, D)
- Reference existing code as [REUSE] in traceability matrix
- Testing tasks for gaps + validation of existing integration tests
- Do NOT re-generate specs for already-working code (risk of drift)

---

## Approaches Considered

### Approach A: Gaps-Only (Minimal)
Fix only Gaps A-D. No new tests. No documentation updates.

```
  ┌─────────────────────────────────────────────────────┐
  │ Phase 1: revokeAllSessions() completion (2h)        │
  │ Phase 2: SsoAdapter.getProviders() config-fix (15m) │
  │ Phase 3: Stale TODO cleanup (5m)                    │
  │ Phase 4: domainId resolution in controllers (30m)   │
  └─────────────────────────────────────────────────────┘
  Total: ~3h
```

**Pros:**
- ✅ Fastest delivery
- ✅ Minimal risk

**Cons:**
- ❌ No test coverage for new code
- ❌ Missing documentation updates (SecurityProperties defaults)
- ❌ Quality score stays at 92

### Approach B: Remaining Gaps Closure + Testing Validation + Production Readiness
Fix gaps A-D + add tests for new code + clean up stale comments + document defaults.

```
  ┌─────────────────────────────────────────────────────┐
  │ Phase 1: revokeAllSessions() completion (2h)        │
  │   ├── TokenStorePersistenceAdapter batch query       │
  │   ├── LoginSessionService cleanup                   │
  │   └── AuthService.revokeAllSessions() full impl     │
  ├─────────────────────────────────────────e

---

## Selected Direction

**Approach B: Remaining Gaps Closure + Testing Validation + Production Readiness**

**Reasoning:**
1. **revokeAllSessions() is the only HOLLOW implementation** — FR-012 is marked as "IMPLEMENTED" but returns 0 and doesn't actually revoke anything. This is a correctness issue, not an enhancement.
2. **getProviders() config inconsistency** is a technical debt that should be fixed alongside the config-driven OAuth2TokenExchanger that already works.
3. **Tests for new code** follow engineering best practice — every code change should have a corresponding test.
4. **Documentation** addresses the quality score deductions (-1 Clarity, -2 Completeness) from pre_openspec.
5. **Approach A is too minimal** — leaves hollow implementation that could cause production issues (admin clicks "Force Logout" → nothing happens).
6. **Approach C is overkill** — security audit and performance testing have their own timelines and aren't blockers for feature completion.

**Execution order (dependency-aware):**

```
Phase 1 ─┐
Phase 2 ─┤── NO DEPENDENCIES ── Phase 5 (depends on 1-3)
Phase 3 ─┤                           │
Phase 4 ─┘                      Phase 6 (independent)
```

Phases 1-4 can be parallelized. Phase 5 depends on Phases 1-3 (tests need code). Phase 6 is independent.

---

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Non-Financial (authentication identity verification)
- Affected modules:
  - `auth/application/AuthService.kt` — MODIFY (revokeAllSessions full implementation)
  - `auth/application/SsoAdapter.kt` — MODIFY (getProviders config-driven + remove stale TODO)
  - `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` — MODIFY (batch revocation query)
  - `auth/adapter/in/web/AuthController.kt` — MODIFY (domainId resolution)
  - `auth/adapter/in/web/CqrsAuthController.kt` — MODIFY (domainId resolution)
  - `shared/config/SecurityProperties.kt` — MODIFY (Javadoc defaults documentation)
  - `test/` — 3 NEW test files (revokeAllSessions, trusted device, getProviders)

---

## Codebase Investigation Findings

### OAuth2TokenExchanger — Config-Driven ✅ CONFIRMED

```
File: OAuth2TokenExchanger.kt

Current implementation (VERIFIED):
  private fun getTokenEndpoint(provider: String): String =
      securityProperties.sso.providers[provider]?.tokenEndpoint
          ?: throw SsoTokenInvalidException("Unknown SSO provider: $provider")

  private fun getUserInfoEndpoint(provider: String): String =
      securityProperties.sso.providers[provider]?.userInfoEndpoint
          ?: throw SsoTokenInvalidException("Unknown SSO provider: $provider")

✅ D11 from previous brainstorm FULLY IMPLEMENTED
✅ Keycloak support: works IF config exists in application.yml
✅ Any provider works: just add to providers map
```

### EventPublisher Port — ✅ CONFIRMED

```
Port: auth/application/port/out/EventPublisher.kt
  interface EventPublisher { fun publish(event: DomainEvent) }

Events defined:
  - UserRegisteredEvent
  - PermissionChangedEvent  
  - SsoProvisionedEvent (FR-007) ← NEW since last brainstorm

Adapter: auth/adapter/out/event/SpringEventPublisher.kt
  @Component — logs events only (Phase 1)

Usage in SsoAdapter.kt:76:
  eventPublisher.publish(SsoProvisionedEvent(
      userId = user.id!!, provider = provider,
      email = idpUser.email, domainCode = securityProperties.sso.defaultDomainCode
  ))

✅ D13 from previous brainstorm FULLY IMPLEMENTED
```

### MfaService.verifyMfa() — Trusted Device ✅ CONFIRMED

```
File: MfaService.kt

verifyMfa() signature (VERIFIED):
  fun verifyMfa(
      mfaToken: String,
      code: String,
      trustDevice: Boolean = false,     ← D12 implemented
      deviceHash: String? = null,       ← D12 implemented
      authResponseBuilder: (Long) -> AuthResponse
  ): AuthResponse

Trusted device save (lines 109-118):
  if (trustDevice && !deviceHash.isNullOrBlank()) {
      val user = userRepository.findById(userId).orElseThrow { ... }
      user.trustedDeviceHash = deviceHash
      userRepository.save(user)
      log.info("Trusted device set for userId={}", userId)
      auditLogService.logEvent(userId, AuditAction.TRUSTED_DEVICE_SET, ...)
  }

DTO: MfaDtos.kt:16 → trustDevice: Boolean = false
Controller: MfaController.kt:26 → passes trustDevice to service

✅ D12 from previous brainstorm FULLY IMPLEMENTED
```

### revokeAllSessions() — ❌ INCOMPLETE (Gap A)

```
File: AuthService.kt:281-289

CURRENT (hollow):
  @Transactional
  fun revokeAllSessions(userId: Long): Int {
      val user = userRepository.findById(userId).orElseThrow { ... }
      log.info("All sessions revoked for userId={}", userId)
      return 0 // TODO: count revoked tokens
  }

REQUIRED:
  @Transactional
  fun revokeAllSessions(userId: Long): Int {
      val user = userRepository.findById(userId).orElseThrow { ... }
      
      // 1. Revoke all refresh tokens
      val revokedCount = refreshTokenRepository.revokeAllByUserId(userId)
      
      // 2. End all login sessions
      loginSessionService.endAllSessions(userId)
      
      // 3. Note: Access tokens expire naturally (max 15min)
      // JWT is stateless — no per-token blacklisting for bulk revoke
      // This is industry-standard behavior
      
      auditLogService.logEvent(userId, AuditAction.SESSION_REVOKED, ...)
      log.info("All sessions revoked for userId={}: {} tokens revoked", userId, revokedCount)
      return revokedCount
  }

Repository method needed:
  RefreshTokenRepository: fun revokeAllByUserId(userId: Long): Int
  → @Modifying @Query("UPDATE RefreshTokenEntity SET revoked = true WHERE userId = :userId AND revoked = false")
```

### SsoAdapter.getProviders() — ❌ HARDCODED (Gap B)

```
File: SsoAdapter.kt:83-91

CURRENT (hardcoded):
  fun getProviders(): List<SsoProviderInfo> {
      if (!securityProperties.sso.enabled) return emptyList()
      return listOf(
          SsoProviderInfo("google", "Google", true),
          SsoProviderInfo("microsoft", "Microsoft", true),
          SsoProviderInfo("keycloak", "Keycloak", true)
      )
  }

INCONSISTENCY:
  OAuth2TokenExchanger → config-driven (reads providers map) ✅
  SsoAdapter.getProviders() → hardcoded list ❌
  
  If Keycloak is NOT in providers config → OAuth2TokenExchanger throws on callback
  But getProviders() still returns Keycloak as "available"
  → Client shows Keycloak button → user clicks → 500 error

FIX: Read from securityProperties.sso.providers (see Q3 above)
```

### Cross-Feature State

```
Pipeline State (all prior features archived):
  ✅ anonymous-login-optimization → ARCHIVED
  ✅ api-response-i18n-standard → ARCHIVED
  ✅ erp-iam-system → ARCHIVED
  
  No active feature conflicts.
  LoginHandler.kt: session promotion code (anonymous) in different region from MFA check (auth-core).
  AuthCoreExceptions.kt: i18n codes applied by api-response-i18n (already archived).
```

---

## Architecture Diagram (Final State — Post-Gaps)

```
                        ┌────────────────────────────────────────────────────┐
                        │                auth-service                        │
                        │                                                    │
                        │   ┌────────────────────────────────────────┐      │
                        │   │         Adapter In (Web)                │      │
                        │   │  AuthCtrl  MfaCtrl  SsoCtrl  TokenCtrl │      │
                        │   │  AdminSessionCtrl  CqrsAuthCtrl        │      │
                        │   └──────────────┬─────────────────────────┘      │
         **Issue**: No TTL on trusted device hash. Trust persists indefinitely until hash changes.
**Mitigation**: Acceptable for MVP. Phase 2 enhancement adds `trusted_device_set_at` column.

---

## Open Questions for Design Phase

- [RESOLVED] D11: Config-driven SSO providers → ✅ IMPLEMENTED
- [RESOLVED] D12: Trusted device save on MFA verify → ✅ IMPLEMENTED
- [RESOLVED] D13: EventPublisher port + adapter → ✅ IMPLEMENTED
- [RESOLVED] D14: Defer trusted device TTL → ✅ DEFERRED (by design)
- [RESOLVED] Q11: trusted_device_set_at column → DEFERRED to Phase 2
- [RESOLVED] Q12: EventPublisher approach → Custom port (not Spring events)
- [OPEN] Q13: Should `revokeAllSessions()` also blacklist known access token JTIs, or just revoke refresh tokens?
  → Recommend: Revoke refresh tokens only. Access tokens are stateless (expire in ≤15min). Blacklisting all JTIs requires tracking issued JTIs per user — not currently stored. Industry-standard approach is refresh token revocation + natural access token expiry.
- [OPEN] Q14: Should `getProviders()` include a `displayName` field from config, or derive from provider ID?
  → Recommend: Derive from ID (`id.replaceFirstChar { it.uppercase() }`). Adding `displayName` to ProviderConfig increases config complexity for minimal gain.

## Open Questions for URD Analysis

- None — URD analysis is complete in pre_openspec.md.

---

## Design Decisions Summary (Full History)

| # | Decision | Rationale | Status | Sprint |
|---|----------|-----------|--------|--------|
| D1 | Sealed class `LoginResult` | Type-safe MFA vs Success | ✅ IMPLEMENTED | 2026-08-05 |
| D2 | MFA token = JWT stateless (5min TTL) | Reduce Redis dependency | ✅ IMPLEMENTED | 2026-08-05 |
| D3 | RS256 via refresh-based migration | Non-breaking, 7-day auto-cleanup | ✅ IMPLEMENTED | 2026-08-05 |
| D4 | CAPTCHA at service level | Conditional check on failedLoginCount | ✅ IMPLEMENTED | 2026-08-05 |
| D5 | SSO hybrid (OAuth2 Client + manual controller) | SPA-compatible | ✅ IMPLEMENTED | 2026-08-05 |
| D6 | UserEntity columns (not separate entity) | Avoid N+1 on login | ✅ IMPLEMENTED | 2026-08-05 |
| D7 | Passay factory per domain (ConcurrentHashMap cache) | Performance | ✅ IMPLEMENTED | 2026-08-05 |
| D8 | SSO-only: passwordHash = "!SSO_ONLY!" | Keep NOT NULL constraint | ✅ IMPLEMENTED | 2026-08-05 |
| D9 | Redis key namespace: `otp:{userId}:{channel}` | Clean TTL cleanup | ✅ IMPLEMENTED | 2026-08-05 |
| D10 | Feature config via SecurityProperties | Simple, env-injectable | ✅ IMPLEMENTED | 2026-08-05 |
| D11 | Config-driven SSO providers (SsoProperties.providers map) | Keycloak + extensibility | ✅ IMPLEMENTED | 2026-08-19 |
| D12 | Trusted device save on MFA verify success | Complete FR-005 flow | ✅ IMPLEMENTED | 2026-08-19 |
| D13 | EventPublisher port + SpringEventPublisher adapter | Hexagonal for deferred Kafka | ✅ IMPLEMENTED | 2026-08-19 |
| D14 | Defer trusted device TTL to separate migration | Minimize scope | ✅ DEFERRED | 2026-08-19 |
| D15 | **[NEW]** revokeAllSessions: refresh token revocation only | Access tokens expire naturally (≤15min) | PENDING | 2026-08-20 |
| D16 | **[NEW]** getProviders() reads from config | Consistency with OAuth2TokenExchanger | PENDING | 2026-08-20 |
| D17 | **[NEW]** domainId from authenticated user's active domain | Password policy requires domain context | PENDING | 2026-08-20 |

---

## Estimated Effort

| Phase | Task | Effort | Priority |
|-------|------|--------|----------|
| 1 | revokeAllSessions() full implementation | 2h | HIGH |
| 2 | SsoAdapter.getProviders() config-driven | 15m | MEDIUM |
| 3 | domainId resolution in controllers | 30m | MEDIUM |
| 4 | Stale TODO cleanup | 15m | LOW |
| 5 | Tests (3 new test classes/methods) | 2h | HIGH |
| 6 | Documentation (defaults, Javadoc) | 30m | LOW |
| **Total** | | **~5.5h** | |
