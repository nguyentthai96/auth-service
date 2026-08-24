<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A (CQRS)", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: auth-core-features (v5)

> **Type**: EXTEND | **Flow**: Non-Financial | **FRs**: 17 (15 fully implemented, 1 partial — FR-005 TTL hardening, 1 adapter fix — FR-012)
> **Direction**: Approach B — TTL Enforcement + Mapper Gap Fix + Adapter Cleanup + Test Coverage (from brainstorm v5)
> **_Generated**: 2026-08-25 (v5 — delta from v4 2026-08-21)
> **Archive**: `openspec/changes/archive/2026-08-20-auth-core-features/tasks.md` (v3 — 17 tasks, all marked done)

## Changes

[CHANGED] v4→v5: Code re-scan confirms V10 migration APPLIED, entity field EXISTS. Removed Task 1 (V10 migration) and Task 2 (UserEntity column) — both already in codebase.
[CHANGED] Gap 1 restructured: mapper fix (Gap 1a) elevated to CRITICAL — prerequisite for all TTL logic.
[CHANGED] Task count reduced from 12 to 10 (2 tasks no longer needed — DB + entity already done).
[CHANGED] Phase structure simplified: Phase 1 (Mapper) → Phase 2 (Domain+Service) → Phase 3 (Adapter) → Phase 4 (Tests) → Phase 5 (Verify).
[UNCHANGED] Gap 2: `TokenStorePersistenceAdapter.revokeAllForUser()` stale TODO fix.

**Total**: 7 production files modified, 0 new files, 2 test files modified, ~3h effort

---

## Phase 1: Mapper Fix (~25min) — CRITICAL PATH

> **MUST BE FIRST**: Without mapper fix, `User.trustedDeviceSetAt` is always `null` in domain model.
> All TTL logic in Phase 2 depends on this.

- [x] **Task 1: UserEntityMapper — Map `trustedDeviceSetAt` in both directions**
  - File: [`UserEntityMapper.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL (mapper bridge currently BROKEN)
  - Dependencies: None (entity field already exists)
  - Changes:
    - In `toDomain()` extension function (after L28 `trustedDeviceHash = this.trustedDeviceHash,`):
      ```kotlin
      trustedDeviceSetAt = this.trustedDeviceSetAt,
      ```
    - In `toEntity()` extension function (after `trustedDeviceHash = this@toEntity.trustedDeviceHash`):
      ```kotlin
      trustedDeviceSetAt = this@toEntity.trustedDeviceSetAt
      ```
  - Ref: `impact_analysis.md` — 🟢 LOW impact, no breaking changes

- [x] **Task 2: UserPersistenceAdapter — Map `trustedDeviceSetAt`**
  - File: [`UserPersistenceAdapter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Dependencies: Task 1 (UserEntityMapper)
  - Changes:
    - Verify if adapter uses `UserEntityMapper.toDomain()` or manual `User()` construction
    - If manual construction exists, add after `trustedDeviceHash` mapping:
      ```kotlin
      trustedDeviceSetAt = this.trustedDeviceSetAt,
      ```
    - If uses `UserEntityMapper.toDomain()` → verify Task 1 handles it (skip this task)

---

## Phase 2: Domain Model + Service Logic (~1h)

- [x] **Task 3: User Domain Model — Add TTL check in `requiresMfa()`**
  - File: [`User.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Base: Pure Kotlin domain model (no framework imports)
  - Dependencies: Task 1 (mapper must work for domain model to receive data)
  - Changes:
    - `ChronoUnit` already imported (L7) ✅
    - `trustedDeviceSetAt` field already in constructor (L27) ✅
    - Replace `requiresMfa()` method (L73-76):
      ```kotlin
      fun requiresMfa(deviceHash: String?, ttlDays: Long = 30): Boolean {
          if (!mfaEnabled || mfaMethod == "NONE") return false
          if (deviceHash == null || deviceHash != trustedDeviceHash) return true
          // Device hash matches — check TTL
          val setAt = trustedDeviceSetAt ?: return true  // No timestamp → treat as expired
          return setAt.plus(ttlDays, ChronoUnit.DAYS).isBefore(Instant.now())
      }
      ```

- [x] **Task 4: MfaService — Set `trustedDeviceSetAt` on device trust save**
  - File: [`MfaService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Dependencies: `UserEntity.trustedDeviceSetAt` field (already exists ✅)
  - Changes:
    - In `verifyMfa()` trusted device save section (after `user.trustedDeviceHash = deviceHash`), add:
      ```kotlin
      user.trustedDeviceSetAt = Instant.now()
      ```
    - Ensure `import java.time.Instant` is present (likely already imported)
    - Result: both hash and timestamp saved atomically in same `userRepository.save(user)` call

- [x] **Task 5: LoginHandler — Pass `trustedDeviceTtlDays` to `requiresMfa()`**
  - File: [`LoginHandler.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Dependencies: Task 3 (User.requiresMfa signature change)
  - Pattern: `SecurityProperties` injected via constructor (verify)
  - Changes:
    - At requiresMfa call site, change:
      ```kotlin
      // BEFORE:
      if (user.requiresMfa(command.trustedDeviceHash)) {
      // AFTER:
      if (user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
      ```
    - If `securityProperties` is NOT injected in LoginHandler → add constructor parameter:
      ```kotlin
      private val securityProperties: SecurityProperties
      ```

- [x] **Task 6: PasswordPolicyService — Clear trusted device on password change**
  - File: [`PasswordPolicyService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL (security: password change invalidates device trust — D19)
  - Dependencies: `UserEntity` fields (already exist ✅)
  - Changes:
    - In `changePassword()`, after `user.passwordChangedAt = Instant.now()`, add:
      ```kotlin
      // Clear trusted device on password change — security best practice (D19)
      user.trustedDeviceHash = null
      user.trustedDeviceSetAt = null
      ```
    - Result: password change forces MFA re-verification on next login from any device

---

## Phase 3: Adapter Fix (~15min)

- [x] **Task 7: TokenStorePersistenceAdapter — Fix stale TODO**
  - File: [`TokenStorePersistenceAdapter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt) | Action: [MODIFY]
  - FR: FR-012 — Force Logout (hexagonal port consistency)
  - Dependencies: `refreshTokenRepository.revokeAllByUserId()` (already exists in Repositories.kt)
  - Changes:
    - Replace L46-49:
      ```kotlin
      // BEFORE:
      override fun revokeAllForUser(userId: Long): Int {
          // TODO: Add custom query findAllByUserIdAndRevokedFalse for batch revocation
          return 0
      }
      // AFTER:
      override fun revokeAllForUser(userId: Long): Int {
          return refreshTokenRepository.revokeAllByUserId(userId)
      }
      ```
  - Ref: `impact_analysis.md` — 🟢 LOW impact. `RevokeSessionsHandler.kt` is the only caller via `TokenStore` port.

---

## Phase 4: Tests (~1.5h)

- [x] **Task 8: UserTest — TTL expiry scenarios for `requiresMfa()`**
  - File: `src/test/kotlin/com/ntt/authservice/auth/domain/model/UserTest.kt` | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Dependencies: Task 3 (User domain model changes)
  - Pattern: JUnit 5, Kotlin test style, `@Test` annotations
  - Test cases:
    1. `requiresMfa(hash, 30)` with matching hash + `trustedDeviceSetAt = now() - 10 days` → `false` (device valid, skip MFA)
    2. `requiresMfa(hash, 30)` with matching hash + `trustedDeviceSetAt = now() - 31 days` → `true` (device expired, require MFA)
    3. `requiresMfa(hash, 30)` with matching hash + `trustedDeviceSetAt = null` → `true` (no timestamp, treat as expired)
    4. `requiresMfa(hash, 30)` with matching hash + `trustedDeviceSetAt = now()` → `false` (just trusted)
    5. `requiresMfa(hash, 1)` with matching hash + `trustedDeviceSetAt = now() - 2 days` → `true` (TTL=1 day, expired)
    6. `requiresMfa(null, 30)` → `true` (no hash, existing behavior preserved)
    7. `requiresMfa("wrong", 30)` → `true` (wrong hash, existing behavior preserved)
    8. `requiresMfa(hash)` (no ttlDays) → uses default 30 (backward compatibility)
    9. MFA disabled → `requiresMfa(null, 30)` → `false` (existing behavior)

- [x] **Task 9: MfaLoginFlowIntegrationTest — TTL-aware trusted device test**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/MfaLoginFlowIntegrationTest.kt` | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL
  - Dependencies: Tasks 1-6 (all production changes)
  - Pattern: `@SpringBootTest` + `@Testcontainers` (Redis + PostgreSQL) + `@AutoConfigureMockMvc`
  - Test cases:
    1. Login → MFA → verify with trustDevice=true → check DB: `trustedDeviceSetAt` is NOT null and is recent (`Instant.now()`)
    2. Login with trusted device + valid TTL → skip MFA → 200 AuthResponse
    3. Simulate expired trust (manually set `trustedDeviceSetAt` to 31 days ago in DB) → login → MFA required
    4. Password change → verify `trustedDeviceHash = null` AND `trustedDeviceSetAt = null` in DB
    5. `revokeAllForUser()` returns actual revoked count (verify adapter fix for Gap 2)

---

## Phase 5: Verification

- [x] **Task 10: FR traceability verification + build check**
  - Verify all 17 FRs addressed:
    - FR-001 (OTP): ✅ IMPLEMENTED → no changes needed
    - FR-002 (TOTP): ✅ IMPLEMENTED → no changes needed
    - FR-003 (MFA Settings): ✅ IMPLEMENTED → no changes needed
    - FR-004 (CAPTCHA): ✅ IMPLEMENTED → no changes needed
    - FR-005 (Trusted Device): ⚠️ PARTIAL → mapper fix (Tasks 1-2), TTL hardening (Tasks 3-6), tests (Tasks 8-9)
    - FR-006 (SSO Login): ✅ IMPLEMENTED → no changes needed
    - FR-007 (JIT Provision): ✅ IMPLEMENTED → no changes needed
    - FR-008 (SSO Link): ✅ IMPLEMENTED → no changes needed
    - FR-009 (RS256): ✅ IMPLEMENTED → no changes needed
    - FR-010 (JWKS): ✅ IMPLEMENTED → no changes needed
    - FR-011 (Introspect): ✅ IMPLEMENTED → no changes needed
    - FR-012 (Force Logout): ✅ IMPLEMENTED → adapter fix (Task 7)
    - FR-013 (Password Policy): ✅ IMPLEMENTED → clear device trust (Task 6)
    - FR-014 (Password History): ✅ IMPLEMENTED → no changes needed
    - FR-015 (MFA Idempotency): ✅ IMPLEMENTED → no changes needed
    - FR-016 (Audit Logging): ✅ IMPLEMENTED → no changes needed
    - FR-017 (IdP Timeout): ✅ IMPLEMENTED → no changes needed
  - **Coverage: 17/17 FRs ✓** (1 partial → completion tasks, 16 implemented → no changes)
  - Build verification:
    - `./gradlew compileKotlin` — must pass after all changes
    - `./gradlew test` — must pass (verify existing tests don't break from backward-compatible changes)
    - Flyway migration — V10 already applied ✅

---

## Summary

| Phase | Tasks | Files Modified | Files New | Effort |
|-------|-------|---------------|-----------|--------|
| Phase 1: Mapper Fix (CRITICAL) | 2 | 2 (UserEntityMapper, UserPersistenceAdapter) | 0 | ~25min |
| Phase 2: Domain + Service | 4 | 4 (User, MfaService, LoginHandler, PasswordPolicyService) | 0 | ~1h |
| Phase 3: Adapter Fix | 1 | 1 (TokenStorePersistenceAdapter) | 0 | ~15min |
| Phase 4: Tests | 2 | 2 (UserTest, MfaLoginFlowIntegrationTest) | 0 | ~1.5h |
| Phase 5: Verification | 1 | 0 | 0 | ~15min |
| **Total** | **10** | **7** (unique production files) + **2** (test files) | **0** | **~3h** |

### Execution Order (Dependency Graph)

```
Task 1 (UserEntityMapper) ──────────── CRITICAL PATH (must be first)
Task 2 (UserPersistenceAdapter) ────── DEPENDS on Task 1
Task 3 (User domain model) ─────────── DEPENDS on Task 1 (mapper must work)
Task 4 (MfaService) ────────────────── DEPENDS on entity field (already exists)
Task 5 (LoginHandler) ──────────────── DEPENDS on Task 3 (requiresMfa signature)
Task 6 (PasswordPolicyService) ─────── DEPENDS on entity fields (already exist)
Task 7 (TokenStorePersistenceAdapter) ── INDEPENDENT (can run in parallel)
Task 8 (UserTest) ──────────────────── DEPENDS on Task 3
Task 9 (MfaLoginFlowIntegrationTest) ── DEPENDS on Tasks 1-6
Task 10 (Verification) ─────────────── DEPENDS on ALL
```

### Cross-Feature Coordination

| Shared File | This Feature | Other Feature | Risk |
|------------|-------------|---------------|------|
| `User.kt` | MODIFY (TTL logic in requiresMfa) | None active | 🟢 Backward-compatible (default params) |
| `LoginHandler.kt` | MODIFY (pass ttlDays param) | `anonymous-login-optimization` (archived) | 🟢 No conflict — different code paths |
| `PasswordPolicyService.kt` | MODIFY (clear device trust) | None active | 🟢 Isolated change |
| `TokenStorePersistenceAdapter.kt` | MODIFY (fix stale TODO) | None active | 🟢 Isolated change |
| `UserEntityMapper.kt` | MODIFY (add field mapping) | None active | 🟢 Additive change |

### Design Decisions Applied

| Decision | Task | Evidence |
|----------|------|----------|
| D18: DB-only `trusted_device_set_at` (not Redis) | N/A | V10 migration + UserEntity column already applied |
| D19: Clear device trust on pwd change (not force-logout) | Task 6 | PasswordPolicyService clears hash + setAt |
| D20: Fix TokenStorePersistenceAdapter stale TODO | Task 7 | Delegate to existing repo method |
| D21: Fix UserEntityMapper trustedDeviceSetAt mapping | Task 1 | CRITICAL — mapper bridge currently BROKEN |
| D22: `requiresMfa()` receives `ttlDays` as parameter | Tasks 3, 5 | Domain model stays pure, LoginHandler passes config |
| D23: MfaService set `trustedDeviceSetAt = now()` on trust save | Task 4 | Atomic with hash save |
| D24: Keep domain model pure (no framework deps) | Task 3 | Pass ttlDays as Long parameter |
| D25: Keycloak = config-only | N/A | Already resolved — no code change |
| D26: Force-logout does NOT clear trusted device | N/A | Only password change does (Task 6) |
