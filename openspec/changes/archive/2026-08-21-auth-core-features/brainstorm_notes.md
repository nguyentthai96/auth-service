---
type: brainstorm_notes
change: auth-core-features
date: 2026-08-25
selected_direction: "Approach B: Trusted Device TTL Enforcement + Mapper Gap Fix + Adapter Cleanup + Test Coverage"
pre_flow: "Non-Financial"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Core Features — Final Hardening (v5)

## Date
2026-08-25

## Context

This is a **fifth-pass brainstorm** (refreshed after code re-scan on 2026-08-25). The codebase has advanced significantly since the v4 brainstorm (2026-08-21). The `pre_openspec.md` has been updated to v4 (quality 93/100, _Generated 2026-08-25) with refined findings.

**Key progress since last brainstorm (2026-08-21 → 2026-08-25):**

| Previous Gap | Status | Evidence |
|-------------|--------|----------|
| V10 migration for `trusted_device_set_at` | ✅ APPLIED | `V10__trusted_device_ttl.sql` exists in `src/main/resources/db/migration/` |
| `UserEntity.trustedDeviceSetAt` field | ✅ EXISTS | `UserEntity.kt:54-55` — `@Column(name = "trusted_device_set_at")` |
| `User.trustedDeviceSetAt` domain field | ✅ EXISTS | `User.kt:27` — `val trustedDeviceSetAt: Instant? = null` |
| `KafkaEventPublisher.kt` | ✅ IMPLEMENTED | `@Primary`, `@ConditionalOnProperty`, retry 3 attempts exponential backoff |
| `IdempotencyFilter.kt` | ✅ IMPLEMENTED | `shared/filter/` — Redis-backed `X-Idempotency-Key` header, 24h TTL |
| `OAuth2TokenExchanger` config-driven | ✅ IMPLEMENTED | Reads from `securityProperties.sso.providers[provider]` — dynamic, supports any OIDC provider |
| Issue #2 (Kafka deferred) | ✅ RESOLVED | `KafkaEventPublisher.kt` fully implemented |
| Issue #1 (Keycloak in getTokenEndpoint) | ✅ RESOLVED | Config-driven — Keycloak is a **config-only** addition (no code change needed) |

**Remaining issues from `pre_openspec.md` (v4, quality 93/100):**
- 1 issue (🟡): Keycloak provider config not yet added to `application.yml` (config-only, out of code scope)
- Quality deductions: -1 Clarity (CAPTCHA default), -1 Completeness (trusted device TTL logic), -5 Testability (SSO flow IdP mocking)

**Source context:**
- `pre_openspec.md` (2026-08-25): 17 FRs, quality 93/100, 1 issue (🟡), 1 open question
- Research artifacts: 7 files complete
- Previous brainstorm v4 (2026-08-21): 20 design decisions, D18-D20 PENDING
- Previous archive: `2026-08-20-auth-core-features/` (10 artifacts)

---

## Questions Asked & Answers

### Q1: What EXACTLY remains after V10 migration and field additions?

**→ A: 4 items — 2 logic gaps, 1 mapper gap (NEW finding), 1 adapter cleanup**

```
Code scan results (2026-08-25):

Remaining in-scope items:

┌─────────────────────────────────────────────────────────────────────┐
│ Gap 1: Trusted Device TTL LOGIC not enforced (FR-005)              │
│   Current state:                                                     │
│   - User.kt:73-76 → requiresMfa() does simple hash comparison      │
│     STILL NO TTL CHECK despite trustedDeviceSetAt field existing     │
│   - MfaService.kt:112-116 → saves hash but NOT trustedDeviceSetAt  │
│   - SecurityProperties.kt:81 → trustedDeviceTtlDays=30 (STILL      │
│     UNUSED in any logic!)                                            │
│                                                                       │
│   V10 migration ✅ APPLIED, fields ✅ EXIST, but:                    │
│     a) User.requiresMfa() → no TTL check logic                      │
│     b) MfaService.verifyMfa() → doesn't set trustedDeviceSetAt     │
│     c) LoginHandler.kt:120 → doesn't pass ttlDays                  │
│     d) PasswordPolicyService → doesn't clear trusted device         │
│                                                                       │
│   Impact: Security concern — trusted device bypass is indefinite    │
│   Effort: ~2h                                                       │
├─────────────────────────────────────────────────────────────────────┤
│ Gap 2: UserEntityMapper MISSING trustedDeviceSetAt mapping (NEW!)    │
│   File: UserEntityMapper.kt                                          │
│     - toDomain() line 28 → maps trustedDeviceHash ✅                │
│     - toDomain() → DOES NOT map trustedDeviceSetAt ❌               │
│     - toEntity() → DOES NOT map trustedDeviceSetAt ❌               │
│   File: UserPersistenceAdapter.kt:79                                 │
│     - Manual mapping also MISSING trustedDeviceSetAt ❌              │
│                                                                       │
│   Both UserEntity.kt:55 and User.kt:27 have the field,              │
│   but the mapper bridge is BROKEN — data flows through              │
│   but trustedDeviceSetAt is always null in domain model!             │
│                                                                       │
│   Impact: CRITICAL for TTL — even if logic is added, mapper gap     │
│     means trustedDeviceSetAt is never loaded from DB                 │
│   Effort: ~15m                                                       │
├─────────────────────────────────────────────────────────────────────┤
│ Gap 3: TokenStorePersistenceAdapter.revokeAllForUser() — STALE TODO  │
│   File: TokenStorePersistenceAdapter.kt:46-49                        │
│   - Still returns 0 with TODO comment                                │
│   - refreshTokenRepository.revokeAllByUserId() EXISTS and works     │
│   - RevokeSessionsHandler.kt:33 calls tokenStore.revokeAllForUser() │
│     → gets 0 → reports 0 revoked (BUG!)                             │
│                                                                       │
│   NOTE: AuthService.revokeAllSessions():289 bypasses port and       │
│   calls refreshTokenRepository directly (architectural violation)    │
│                                                                       │
│   Impact: RevokeSessionsHandler returns wrong count (always 0)       │
│   Effort: ~15m                                                       │
├─────────────────────────────────────────────────────────────────────┤
│ Gap 4: Missing/incomplete tests                                       │
│   - No test for TTL expiry path in requiresMfa()                     │
│   - MfaLoginFlowIntegrationTest has basic trusted device tests       │
│     but doesn't test the "30-day TTL expires → MFA required" path   │
│   - TokenStorePersistenceAdapter test (if any) would fail            │
│                                                                       │
│   Impact: Testability score deduction                                │
│   Effort: ~1.5h                                                      │
└─────────────────────────────────────────────────────────────────────┘

Out-of-scope items (confirmed):
- Keycloak provider config → config-only (application.yml), no code change
- SMS/Email OTuntrusts devices on admin session revocation

  Implementation:
    PasswordPolicyService.changePassword() → clear trustedDeviceHash + trustedDeviceSetAt
```

### Q5: Any test infrastructure changes needed?

**→ A: No — existing test infrastructure sufficient (reconfirmed from v4)**

```
Test infrastructure audit:
  ✅ @SpringBootTest configured
  ✅ WireMock for SSO IdP mocking 
  ✅ @MockBean for Redis operations
  ✅ TestcontainersConfiguration for PostgreSQL
  ✅ MfaLoginFlowIntegrationTest has basic trusted device tests

What's needed (tests only):
  1. Extend MfaLoginFlowIntegrationTest with TTL expiry scenario
  2. Add User.requiresMfa() unit test for TTL edge cases:
     - hash matches + setAt within TTL → skip MFA
     - hash matches + setAt expired → require MFA
     - hash matches + setAt null → require MFA
     - hash doesn't match → require MFA
     - MFA disabled → skip MFA
  3. Verify TokenStorePersistenceAdapter.revokeAllForUser() after fix

No new infrastructure needed.
```

### Q6: What about RevokeSessionsHandler vs AuthService.revokeAllSessions()?

**→ A: Fix adapter, then refactor AuthService to use port (separate concern)**

```
Current architecture violation:
  AuthService.revokeAllSessions():289 → calls refreshTokenRepository.revokeAllByUserId()
    DIRECTLY bypasses TokenStore port!
  
  RevokeSessionsHandler.kt:33 → calls tokenStore.revokeAllForUser()
    → TokenStorePersistenceAdapter returns 0 (broken)
    → Reports 0 sessions revoked (incorrect)

Fix plan (minimal, non-breaking):
  Step 1: Fix TokenStorePersistenceAdapter.revokeAllForUser()
    → call refreshTokenRepository.revokeAllByUserId(userId)
    → This makes RevokeSessionsHandler work correctly
  
  Step 2 (optional, separate concern):
    → Refactor AuthService to use TokenStore port instead of direct repo
    → This is architectural cleanup, not functional change
    → Tag as [FUTURE] or separate MAINTENANCE change

Decision: Fix Step 1 only (adapter fix) — minimal change, fixes the bug.
```

---

## Approaches Considered

### Approach A: TTL Logic Only (Minimal)
Fix only `User.requiresMfa()` TTL check and `MfaService` timestamp save.

```
  Scope:
  ├── User.requiresMfa() — add TTL check
  ├── MfaService.verifyMfa() — set trustedDeviceSetAt
  └── Done

  Pros:
  ✅ Minimal scope
  ✅ Fixes the security concern

  Cons:
  ❌ MISSES mapper gap → TTL check would see null trustedDeviceSetAt → broken!
  ❌ Leaves TokenStorePersistenceAdapter returning 0 (bug)
  ❌ Doesn't clear trust on password change (security gap)
  ❌ No test coverage for new logic

  Score: 3/10 (incomplete — mapper gap makes it non-functional)
```

### Approach B: TTL Enforcement + Mapper Fix + Adapter Cleanup + Tests
Complete end-to-end fix: mapper, TTL logic, password change clear, adapter fix, tests.

```
  Scope:
  ├── Phase 1: Mapper fix (trustedDeviceSetAt in 3 files) — 15m
  ├── Phase 2: MfaService — set trustedDeviceSetAt on trust save — 15m
  ├── Phase 3: User.requiresMfa() — TTL enforcement logic — 30m
  ├── Phase 4: LoginHandler — pass ttlDays to requiresMfa — 15m
  ├── Phase 5: PasswordPolicyService — clear trusted device on pwd change — 15m
  ├── Phase 6: TokenStorePersistenceAdapter — fix revokeAllForUser — 15m
  ├── Phase 7: Tests — unit + integration for TTL flow — 1.5h
  └── Phase 8: SecurityProperties Javadoc update — 15m

  Pros:
  ✅ End-to-end correct (mapper → logic → test)
  ✅ Addresses ALL quality score deductions
  ✅ Fixes TokenStorePersistenceAdapter bug
  ✅ Security best practice (TTL + clear on pwd change)
  ✅ Testable — verifiable in CI

  Cons:
  ❌ Slightly more scope than Approach A
  
  Score: 9/10 (comprehensive, correct, achievable in ~4h)
```

### Approach C: Full Trusted Device Refactor (Multi-device support)
Introduce a `trusted_devices` table for multi-device trust management.

```
  Scope:
  ├── New trusted_devices table (V11 migration)
  ├── TrustedDeviceEntity + Repository
  ├── TrustedDeviceService (CRUD)
  ├── Multiple devices per user with individual TTLs
  ├── Device name/metadata tracking
  ├── Admin endpoint for device management
  └── Tests for all new code

  Pros:
  ✅ Multi-device support (production-ready)
  ✅ Better UX (manage trusted devices individually)

  Cons:
  ❌ Scope creep — pre_openspec says "trusted device flow hardening"
  ❌ New entity, new endpoints, new service — NEWBUILD scope
  ❌ Effort: ~8-12h vs ~4h for Approach B
  ❌ DIFF AWARENESS violation — EXTEND feature, not NEWBUILD
  ❌ Single-device per user is current design — multi-device is separate feature

  Score: 4/10 (over-engineered for EXTEND scope)
```

---

## Selected Direction

**Approach B: TTL Enforcement + Mapper Fix + Adapter Cleanup + Tests**

**Reasoning:**
1. **Mapper gap is CRITICAL** — Without fixing the mapper, `trustedDeviceSetAt` is never loaded from DB into the domain model. Any TTL logic would silently fail (see `null` and always require MFA). Approach A misses this entirely.
2. **Complete end-to-end correctness** — The fix spans DB → entity → mapper → domain → application → controller → test. Each layer must be correct for the feature to work.
3. **Security best practice** — 30-day TTL config exists but is unused. A trusted device hash without TTL means a stolen fingerprint grants indefinite MFA bypass. This is a security hardening task.
4. **TokenStorePersistenceAdapter is a real bug** — `RevokeSessionsHandler` uses `tokenStore.revokeAllForUser()` which returns 0. The admin thinks no sessions were revoked when they actually could be revoked (via the direct repo call in AuthService). Fixing the adapter makes the CQRS command handler correct.
5. **Approach C is scope creep** — Pre_openspec classifies this as EXTEND. Multi-device trust is a separate feature with its own entity, service, and endpoints.

**Execution order (dependency-aware):**

```
Phase 1 (Mapper fix) ─────────────── CRITICAL PATH (must be first)
Phase 2 (MfaService timestamp) ───── DEPENDS on Phase 1 for persistence
Phase 3 (requiresMfa TTL) ────────── DEPENDS on Phase 1 for data availability
Phase 4 (LoginHandler ttlDays) ───── DEPENDS on Phase 3
Phase 5 (Clear on pwd change) ────── DEPENDS on Phase 1
Phase 6 (Adapter fix) ───────────── INDEPENDENT
Phase 7 (Tests) ──────────────────── DEPENDS on Phases 1-5
Phase 8 (Docs) ───────────────────── INDEPENDENT

Parallelization:
  Group A: Phases 1→2→3→4→5 (sequential, critical path)
  Group B: Phase 6 (independent, can be done in parallel)
  Group C: Phase 8 (independent, can be done in parallel)
  Final: Phase 7 (depends on Group A)
```

---

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Non-Financial (authentication identity verification)
- Affected modules:
  - `auth/domain/model/User.kt` — MODIFY (TTL check in `requiresMfa()`)
  - `auth/application/MfaService.kt` — MODIFY (set `trustedDeviceSetAt` on trust save)
  - `auth/application/PasswordPolicyService.kt` — MODIFY (clear trusted device on password change)
  - `auth/application/command/LoginHandler.kt` — MODIFY (pass `ttlDays` to `requiresMfa()`)
  - `auth/adapter/out/persistence/mapper/UserEntityMapper.kt` — MODIFY (add `trustedDeviceSetAt` to both directions)
  - `auth/adapter/out/persistence/UserPersistenceAdapter.kt` — MODIFY (add `trustedDeviceSetAt` to manual mapping)
  - `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` — MODIFY (fix stale TODO — call `revokeAllByUserId()`)
  - `shared/config/SecurityProperties.kt` — MODIFY (update Javadoc — TTL now enforced)
  - `test/` — MODIFY/NEW (trusted device TTL unit tests, integration test extension)

---

## Codebase Investigation Findings

### User.requiresMfa() — Current Implementation (UNCHANGED since v4)

```kotlin
// File: auth/domain/model/User.kt (lines 72-76)
fun requiresMfa(deviceHash: String?): Boolean {
    if (!mfaEnabled || mfaMethod == "NONE") return false
    return deviceHash == null || deviceHash != trustedDeviceHash
}
```

**Issue**: No TTL check. `trustedDeviceSetAt` field exists on domain model but is not used. `SecurityProperties.mfa.trustedDeviceTtlDays = 30` is configured but completely unused.

**Target implementation:**
```kotlin
fun requiresMfa(deviceHash: String?, ttlDays: Long = 30): Boolean {
    if (!mfaEnabled || mfaMethod == "NONE") return false
    if (deviceHash == null || deviceHash != trustedDeviceHash) return true
    // Device hash matches — check TTL
    val setAt = trustedDeviceSetAt ?: return true  // No timestamp → treat as expired
    return setAt.plus(ttlDays, ChronoUnit.DAYS).isBefore(Instant.now())
}
```

### UserEntityMapper — MISSING trustedDeviceSetAt (NEW FINDING)

```kotlin
// File: auth/adapter/out/persistence/mapper/UserEntityMapper.kt
// toDomain() — line 28 maps trustedDeviceHash BUT NOT trustedDeviceSetAt!
fun UserEntity.toDomain() = User(
    ...
    trustedDeviceHash = this.trustedDeviceHash,
    // MISSING: trustedDeviceSetAt = this.trustedDeviceSetAt,
    passwordChangedAt = this.passwordChangedAt,
    ...
)

// toEntity() — line 49 maps trustedDeviceHash BUT NOT trustedDeviceSetAt!
fun User.toEntity(existing: UserEntity? = null): UserEntity {
    entity.apply {
        ...
        trustedDeviceHash = this@toEntity.trustedDeviceHash
        // MISSING: trustedDeviceSetAt = this@toEntity.trustedDeviceSetAt
        passwordChangedAt = this@toEntity.passwordChangedAt
    }
}
```

**Impact**: Without this fix, `User.trustedDeviceSetAt` is always `null` in the domain model — making TTL checks always treat device as expired.

### UserPersistenceAdapter — ALSO MISSING trustedDeviceSetAt

```kotlin
// File: auth/adapter/out/persistence/UserPersistenceAdapter.kt (lines 70-83)
// Manual mapping also DOES NOT include trustedDeviceSetAt
private fun UserEntity.toDomainModel() = User(
    ...
    trustedDeviceHash = this.trustedDeviceHash,
    // MISSING: trustedDeviceSetAt = this.trustedDeviceSetAt,
    passwordChangedAt = this.passwordChangedAt,
    ...
)
```

### MfaService.verifyMfa() — Missing Timestamp Save

```kotlin
// File: auth/application/MfaService.kt (lines 112-118)
if (trustDevice && !deviceHash.isNullOrBlank()) {
    val user = userRepository.findById(userId).orElseThrow { ... }
    user.trustedDeviceHash = deviceHash
    // MISSING: user.trustedDeviceSetAt = Instant.now()
    userRepository.save(user)
    ...
}
```

**Fix**: Add `user.trustedDeviceSetAt = java.time.Instant.now()` before save.

### TokenStorePersistenceAdapter — Stale TODO (UNCHANGED)

```kotlin
// File: auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt (lines 46-49)
override fun revokeAllForUser(userId: Long): Int {
    // TODO: Add custom query findAllByUserIdAndRevokedFalse for batch revocation
    return 0
}
```

**Fix**: `return refreshTokenRepository.revokeAllByUserId(userId)` — repo method ALREADY EXISTS at `Repositories.kt:81`.

### OAuth2TokenExchanger — Config-Driven (RESOLVED)

```kotlin
// File: auth/adapter/out/sso/OAuth2TokenExchanger.kt (lines 41-43)
private fun getTokenEndpoint(provider: String): String =
    securityProperties.sso.providers[provider]?.tokenEndpoint
        ?: throw SsoTokenInvalidException("Unknown SSO provider: $provider")
```

**Status**: ✅ Config-driven. Keycloak support = add config in `application.yml`. No code change needed.

### SecurityProperties — TTL Config (STILL UNUSED)

```kotlin
// File: shared/config/SecurityProperties.kt (line 81)
val trustedDeviceTtlDays: Long = 30,
// Javadoc says: "TTL enforcement deferred to future migration"
// → NOW is the time! V10 migration applied, field exists.
```

---

## Architecture Diagram — Trusted Device TTL Flow (Updated)

```
    Data Flow for Trusted Device TTL (end-to-end):

    ┌──────────────────────────────────────────────────────────────┐
    │ Persistence Layer                                            │
    │   UserEntity.trustedDeviceSetAt          ← EXISTS in DB ✅   │
    │   UserEntityMapper.toDomain()            ← NOT MAPPED ❌    │
    │   UserPersistenceAdapter.toDomainModel() ← NOT MAPPED ❌    │
    └──────────────────────┬───────────────────────────────────────┘
                           │
    ┌──────────────────────▼───────────────────────────────────────┐
    │ Domain Layer                                                 │
    │   User.trustedDeviceSetAt                ← EXISTS field ✅   │
    │   User.requiresMfa(hash, ttlDays)        ← TTL CHECK ❌     │
    └──────────────────────┬───────────────────────────────────────┘
                           │
    ┌──────────────────────▼───────────────────────────────────────┐
    │ Application Layer                                            │
    │   LoginHandler.handle()                                      │
    │     → user.requiresMfa(hash)             ← NEEDS ttlDays    │
    │   MfaService.verifyMfa()                                     │
    │     → user.trustedDeviceHash = hash      ← EXISTS ✅        │
    │     → user.trustedDeviceSetAt = now()     ← MISSING ❌      │
    │   PasswordPolicyService.changePassword()                     │
    │     → clear trustedDeviceHash            ← MISSING ❌       │
    │     → clear trustedDeviceSetAt           ← MISSING ❌       │
    └──────────────────────────────────────────────────────────────┘
```

```
    Login Flow (with trusted device TTL):

    ┌──────────────────────────────────────────────────────────────┐
    │                    POST /api/auth/login                      │
    │                         │                                    │
    │                    LoginHandler                              │
    │                         │                                    │
    │        user.requiresMfa(deviceHash, ttlDays=30)              │
    │                    ┌────┴─────┐                              │
    │              MFA disabled    MFA enabled                      │
    │                    │          │                               │
    │              issue JWT   ┌───┴────┐                          │
    │                     no hash/   hash matches DB?              │
    │                     mismatch  ┌───┴────┐                     │
    │                          │   NO        YES                   │
    │                     MFA      │         │                     │
    │                    required  MFA   ┌───┴───────┐             │
    │                          required  setAt+TTL   setAt+TTL     │
    │                                    < now       >= now        │
    │                                    (EXPIRED)   (VALID)       │
    │                                      │           │           │
    │                                 MFA required  skip MFA       │
    │                                               issue JWT      │
    └──────────────────────────────────────────────────────────────┘


    Password Change Flow (clear trusted device):

    ┌──────────────────────────────────────────────────────────────┐
    │          POST /api/auth/change-password                      │
    │                         │                                    │
    │              PasswordPolicyService.changePassword()           │
    │                         │                                    │
    │              validate old password ✅                         │
    │              validate new password (Passay) ✅                │
    │              check history ✅                                 │
    │                         │                                    │
    │              update passwordHash                             │
    │              insert password_history                          │
    │              prune old history                                │
    │              update passwordChangedAt                         │
    │              CLEAR trustedDeviceHash = null  ← NEW           │
    │              CLEAR trustedDeviceSetAt = null ← NEW           │
    │                         │                                    │
    │              200 OK                                           │
    └──────────────────────────────────────────────────────────────┘
```

---

## Design Decisions (Full History)

| # | Decision | Status |
|---|----------|--------|
| D1-D10 | Initial auth-core decisions | ✅ ALL IMPLEMENTED |
| D11 | Config-driven SSO providers | ✅ IMPLEMENTED |
| D12 | Trusted device save on MFA verify | ✅ IMPLEMENTED (hash only, no timestamp) |
| D13 | EventPublisher port + adapter | ✅ IMPLEMENTED (Kafka + Spring fallback) |
| D14 | Defer trusted device TTL | ✅ V10 migration applied, fields exist |
| D15 | revokeAllSessions: refresh token revocation | ✅ IMPLEMENTED (in AuthService, not adapter) |
| D16 | getProviders() reads from config | ✅ IMPLEMENTED |
| D17 | domainId from active domain | ✅ IMPLEMENTED |
| D18 | trusted_device_set_at DB column | ✅ V10 APPLIED |
| D19 | Clear trusted device on password change | PENDING (this iteration) |
| D20 | Fix TokenStorePersistenceAdapter.revokeAllForUser() | PENDING (this iteration) |
| D21 | **[NEW]** Fix UserEntityMapper trustedDeviceSetAt mapping | PENDING (CRITICAL — this iteration) |
| D22 | **[NEW]** Pass ttlDays parameter to User.requiresMfa() | PENDING (this iteration) |
| D23 | **[NEW]** MfaService set trustedDeviceSetAt = now() | PENDING (this iteration) |
| D24 | **[NEW]** Keep domain model pure (no framework deps) | DECIDED — pass config as params |
| D25 | **[NEW]** Keycloak support is config-only (resolved) | DECIDED — add to application.yml |
| D26 | **[NEW]** Force-logout does NOT clear trusted device | DECIDED — only password change does |

---

## Open Questions for Design Phase

- [RESOLVED] D18: V10 migration → ✅ APPLIED
- [RESOLVED] D25: Keycloak support → ✅ Config-driven, no code change
- [RESOLVED] D26: Force-logout vs password change clearing device trust → Password change YES, force-logout NO
- [RESOLVED] Q15: ttlDays as parameter vs config injection → Pass as parameter from LoginHandler
- [RESOLVED] Q16: Clear on force-logout? → NO (see D26)
- [OPEN] Q17: Should expired trusted device hash be cleared on next login, or left for eventual overwrite?
  → **Decision (auto-selected)**: Leave for overwrite. When user does MFA verify with trust again, both hash and setAt get fresh values. Clearing on login adds an extra DB write to the critical login path. No security benefit — hash comparison already fails when TTL expired.
- [OPEN] Q18: Should `User.requiresMfa()` return an enum (MFA_REQUIRED/DEVICE_EXPIRED/SKIP_MFA) for better logging?
  → **Decision (auto-selected)**: NO — keep boolean. The caller (LoginHandler) doesn't need to distinguish reasons. Add debug logging inside requiresMfa() if needed, but don't change the return type. KISS principle.

## Open Questions for URD Analysis

- None — URD analysis is complete in pre_openspec.md (v4).

---

## Estimated Effort

| Phase | Task | Effort | Priority | Dependencies |
|-------|------|--------|----------|-------------|
| 1 | UserEntityMapper — add `trustedDeviceSetAt` to toDomain + toEntity | 15m | CRITICAL | None |
| 1b | UserPersistenceAdapter — add `trustedDeviceSetAt` to mapping | 10m | CRITICAL | None |
| 2 | MfaService — set `trustedDeviceSetAt = now()` on trust save | 15m | HIGH | Phase 1 |
| 3 | User.requiresMfa() — add TTL check with `ttlDays` param | 30m | HIGH | Phase 1 |
| 4 | LoginHandler — pass `securityProperties.mfa.trustedDeviceTtlDays` | 15m | HIGH | Phase 3 |
| 5 | PasswordPolicyService — clear `trustedDeviceHash` + `trustedDeviceSetAt` on pwd change | 15m | MEDIUM | Phase 1 |
| 6 | TokenStorePersistenceAdapter — fix `revokeAllForUser()` | 15m | LOW | None |
| 7 | Tests — TTL unit test + integration test extension | 1.5h | HIGH | Phases 1-5 |
| 8 | SecurityProperties Javadoc update (TTL now enforced) | 15m | LOW | None |
| **Total** | | **~4h** | | |
