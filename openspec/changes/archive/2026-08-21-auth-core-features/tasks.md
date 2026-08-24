<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A (CQRS)", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: auth-core-features (v6)

> **Type**: EXTEND | **Flow**: Non-Financial | **FRs**: 17 (all implemented, 1 legacy path security fix)
> **Direction**: Approach D — Hybrid: Testing, Hardening & Legacy Cleanup (from brainstorm v6)
> **_Generated**: 2026-08-26 (v6 — delta from v5 2026-08-25)
> **Archive**: `openspec/changes/archive/2026-08-21-auth-core-features/tasks.md` (v5 — 10 tasks, all done)

## Changes

[CHANGED] v5→v6: All v5 tasks (mapper fix, TTL logic, adapter fix, tests) — ALL COMPLETED in codebase. v5 tasks are obsolete.
[NEW] Gap 1: AuthService.kt legacy login path — TTL security fix + @Deprecated annotation.
[NEW] Gap 2: SecurityProperties.kt stale KDoc update.
[NEW] Gap 3: Comprehensive test coverage — 11 new test files covering untested subsystems.
[CHANGED] Task count: 10 (v5) → 15 (v6). Focus shifted from production code gaps to testing + hardening.

**Total**: 2 production files modified, 11 new test files, 0 new production files. ~4-5 developer-days effort.

---

## Phase 1: Security Fix (~30min) — CRITICAL

> **MUST BE FIRST**: Legacy login path (`/api/auth/login`) currently bypasses trusted device TTL.
> This is a security vulnerability — expired device hash grants indefinite MFA bypass.

- [x] **Task 1: AuthService.kt — Align legacy login path with TTL enforcement**
  - File: [`AuthService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Action: [MODIFY]
  - FR: FR-005 — Trusted Device TTL (legacy path security gap)
  - Base: `@Service` Spring Bean
  - Dependencies: `UserEntityMapper` (for `toDomain()`), `SecurityProperties` (for `trustedDeviceTtlDays`)
  - Changes:
    1. **Verify constructor injections** — check if `userEntityMapper: UserEntityMapper` and `securityProperties: SecurityProperties` are already injected. If not, add to constructor:
       ```kotlin
       class AuthService(
           ...existing params...,
           private val userEntityMapper: UserEntityMapper,    // ADD if missing
           private val securityProperties: SecurityProperties  // ADD if missing
       )
       ```
    2. **Replace trusted device check** (L131-137):
       ```kotlin
       // BEFORE:
       if (user.mfaEnabled && user.mfaMethod != "NONE") {
           val trustedHash = request.trustedDeviceHash
           if (trustedHash != null && trustedHash == user.trustedDeviceHash) {
               log.debug("Trusted device matched — skipping MFA for userId={}", user.id)
           } else {
               return mfaService.initiateMfa(user.id!!, user.mfaMethod)
           }
       }
       
       // AFTER:
       if (user.mfaEnabled && user.mfaMethod != "NONE") {
           val domainUser = userEntityMapper.toDomain(user)
           if (domainUser.requiresMfa(request.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
               return mfaService.initiateMfa(user.id!!, user.mfaMethod)
           }
       }
       ```
    3. **Add `@Deprecated` annotation** on `login()` method:
       ```kotlin
       @Deprecated(
           message = "Use LoginHandler.handle() via CqrsAuthController (/api/v2/auth/login). " +
                     "Legacy path retained for backward compatibility.",
           replaceWith = ReplaceWith("loginHandler.handle(LoginCommand(...))")
       )
       fun login(request: LoginRequest): LoginResult {
       ```
  - Pattern: Reuse existing `User.requiresMfa(hash, ttlDays)` domain method (D22, D27)

---

## Phase 2: Documentation Fix (~10min)

- [x] **Task 2: SecurityProperties.kt — Update stale KDoc**
  - File: [`SecurityProperties.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Action: [MODIFY]
  - Changes:
    - At L14-15, replace:
      ```kotlin
      // BEFORE:
      // "TTL enforcement deferred to future migration (no Redis-backed expiry yet)."
      
      // AFTER:
      // "TTL enforcement implemented in User.requiresMfa() via trustedDeviceSetAt
      //  column (V10 migration). Default: 30 days. Used by LoginHandler (CQRS)
      //  and AuthService (legacy, @Deprecated)."
      ```

---

## Phase 3: Unit Tests — Application Services (~1.5 days)

- [x] **Task 3: LoginHandlerTest — CQRS login command handler**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/command/LoginHandlerTest.kt` | Action: [NEW]
  - FR: FR-005, FR-004, FR-013 — Login flow with MFA, CAPTCHA, password expiry
  - Pattern: JUnit 5 + MockK, `@ExtendWith(MockKExtension::class)`
  - Dependencies: MockK mocks for `UserPort`, `MfaService`, `SecurityProperties`, `SessionPolicyService`, `PasswordPolicyService`, `TokenGenerator`, `TokenEventRecorder`, `LoginSessionService`, `AuditLogService`, `CaptchaVerifier`
  - Test cases:
    1. Successful login (no MFA, no CAPTCHA) → returns `LoginResult.Success`
    2. MFA enabled + no trusted device → returns `LoginResult.MfaRequired`
    3. MFA enabled + trusted device valid TTL → skip MFA → `LoginResult.Success`
    4. MFA enabled + trusted device expired TTL → `LoginResult.MfaRequired`
    5. MFA enabled + wrong device hash → `LoginResult.MfaRequired`
    6. Account locked → throws `AccountLockedException`
    7. Invalid credentials → throws `InvalidCredentialsException`
    8. CAPTCHA required (threshold exceeded) → throws `CaptchaRequiredException`
    9. CAPTCHA valid → proceeds to login
    10. Password expired → throws `PasswordExpiredException` (AUTH_018)
    11. Session limit exceeded → throws `SessionLimitExceededException`
    12. Token event recorded on success (verify `tokenEventRecorder.recordIssuance()` called)
    13. Audit log recorded on success (verify `auditLogService.logEvent()` called)

- [x] **Task 4: EventServiceTest — Event sourcing service**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/event/EventServiceTest.kt` | Action: [NEW]
  - FR: FR-007, FR-009 — Event recording for token lifecycle and SSO provisioning
  - Pattern: JUnit 5 + MockK
  - Dependencies: MockK mocks for `EventStorePort`, `OutboxPort`
  - Test cases:
    1. `record()` creates `EventEnvelope` with correct aggregate fields
    2. `record()` calls `eventStorePort.append(envelope)`
    3. `record()` calls `outboxPort.insert(outboxEntry)`
    4. Both port calls happen in same invocation
    5. `eventStorePort.append()` failure → propagates exception
    6. `outboxPort.insert()` failure → propagates exception
    7. `EventEnvelope` sequence number auto-incremented for same aggregate

- [x] **Task 5: TokenEventRecorderTest — Token lifecycle events**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorderTest.kt` | Action: [NEW]
  - FR: FR-009, FR-012 — Token issuance and revocation events
  - Pattern: JUnit 5 + MockK
  - Dependencies: MockK mock for `EventService`
  - Test cases:
    1. `recordIssuance()` creates `TokenIssuedEvent` with correct jti, userId
    2. `recordIssuance()` with `IssuanceContext.LOGIN` → correct event type
    3. `recordIssuance()` with `IssuanceContext.REFRESH` → correct event type
    4. `recordIssuance()` with `IssuanceContext.SSO` → correct event type
    5. `recordRevocation()` creates `TokenRevokedEvent` with correct jti
    6. `recordRevocation()` with `RevocationType.LOGOUT` → correct event
    7. `recordRevocation()` with `RevocationType.FORCE_LOGOUT` → correct event
    8. `recordRevocation()` with `RevocationType.TOKEN_REFRESH` → correct event
    9. Both methods delegate to `eventService.record()`

- [x] **Task 6: CaptchaVerifierTest — CAPTCHA verification chain**
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/CaptchaVerifierTest.kt` | Action: [NEW]
  - FR: FR-004 — CAPTCHA integration
  - Pattern: JUnit 5 + MockK
  - Dependencies: MockK mock for `CaptchaGateway`
  - Test cases:
    1. `AltchaCaptchaVerifier.verify(validToken)` → `true`
    2. `AltchaCaptchaVerifier.verify(invalidToken)` → `false`
    3. `verify(null)` → `false`
    4. `verify("")` → `false`
    5. `CaptchaGateway` timeout → returns `false` (graceful)
    6. `CaptchaGateway` error → returns `false` (graceful)

---

## Phase 4: Integration Tests — Adapters & Filters (~2.5 days)

- [x] **Task 7: OutboxPollerTest — Transactional outbox relay**
  - File: `src/test/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPollerTest.kt` | Action: [NEW]
  - FR: FR-007 — Event publishing via transactional outbox
  - Pattern: `@SpringBootTest` + `@Testcontainers` (PostgreSQL) + mock/embedded Kafka
  - Dependencies: `EventOutboxJpaRepository`, `KafkaTemplate` (mock or `@EmbeddedKafka`), `OutboxProperties`
  - Test cases:
    1. Empty outbox → no Kafka publish, no errors
    2. Single outbox entry → publish to Kafka → mark as SENT
    3. Batch of 50 entries → all published in single poll cycle
    4. Batch of 51 entries → first 50 published, 1 remaining
    5. Kafka publish timeout → entry stays PENDING, no retry count increment
    6. Kafka publish confirmed failure → retry count incremented
    7. Entry with `retryCount >= maxRetries` → marked as FAILED
    8. `OutboxProperties` config respected (batchSize, pollIntervalMs, maxRetries)

- [x] **Task 8: AdminSessionControllerTest — Admin force logout**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/AdminSessionControllerTest.kt` | Action: [NEW]
  - FR: FR-012 — Force logout / session revocation
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Testcontainers`
  - Test cases:
    1. `POST /api/admin/sessions/{userId}/revoke-all` with ADMIN role → 200 + `revokedCount`
    2. Same endpoint without ADMIN role → 403
    3. Same endpoint without auth → 401
    4. `GET /api/admin/sessions/{userId}` → list active sessions
    5. After revoke → all refresh tokens revoked, login sessions invalidated

- [x] **Task 9: IdempotencyFilterTest — Idempotency mechanism**
  - File: `src/test/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilterTest.kt` | Action: [NEW]
  - FR: FR-015 — MFA verify idempotency
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + Redis Testcontainer
  - Test cases:
    1. POST with `X-Idempotency-Key` → processes normally, stores result in Redis
    2. Duplicate POST with same key → returns cached response (no re-processing)
    3. POST with different key → independent processing
    4. GET requests → filter skipped (non-mutating)
    5. Redis key TTL = 24h verified
    6. POST without `X-Idempotency-Key` → normal passthrough

- [x] **Task 10: MfaRecoveryCodeFlowTest — MFA recovery codes**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/MfaRecoveryCodeFlowTest.kt` | Action: [NEW]
  - FR: FR-003 — MFA settings (recovery codes extension)
  - Pattern: `@SpringBootTest` + `@Testcontainers`
  - Dependencies: `MfaRecoveryCodeRepository`, user setup fixtures
  - Test cases:
    1. Generate recovery codes → 10 codes returned, SHA-256 hashed in DB
    2. Verify valid recovery code → success, code marked used (single-use)
    3. Verify already-used code → failure
    4. Verify invalid code → failure
    5. Count remaining codes → correct count after usage
    6. Re-generate codes → old codes invalidated, 10 new codes

- [x] **Task 11: LoginRateLimitFilterTest — Rate limiting**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/LoginRateLimitFilterTest.kt` | Action: [NEW]
  - FR: FR-004 — Login security (rate limiting aspect)
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + Redis Testcontainer
  - Test cases:
    1. Under limit → login proceeds normally
    2. Exceed IP rate limit → 429 `AUTH_020`
    3. Exceed username rate limit → 429 `AUTH_020`
    4. Rate limit counter reset after successful login
    5. Redis unavailable → fail-open (login proceeds)

- [x] **Task 12: PasswordExpiryLoginTest — Password expiry on login**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/PasswordExpiryLoginTest.kt` | Action: [NEW]
  - FR: FR-013 — Password policy enforcement during login
  - Pattern: `@SpringBootTest` + `@Testcontainers`
  - Test cases:
    1. Login with expired password → 403 `AUTH_018`
    2. Login with non-expired password → 200 `AuthResponse`
    3. Password expiry determined by domain-scoped policy

- [x] **Task 13: LegacyLoginTtlTest — Legacy path TTL verification**
  - File: `src/test/kotlin/com/ntt/authservice/auth/integration/LegacyLoginTtlTest.kt` | Action: [NEW]
  - FR: FR-005 — Trusted device TTL on legacy path (post-fix validation)
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Testcontainers`
  - Dependencies: Task 1 (AuthService.kt fix must be applied first)
  - Test cases:
    1. `POST /api/auth/login` with trusted hash + valid TTL → skip MFA
    2. `POST /api/auth/login` with trusted hash + expired TTL → MFA required
    3. `POST /api/auth/login` with trusted hash + null `trustedDeviceSetAt` → MFA required
    4. `POST /api/auth/login` without trusted hash + MFA enabled → MFA required
    5. Verify parity with CQRS path (`POST /api/v2/auth/login`) for same inputs

---

## Phase 5: Verification (~30min)

- [x] **Task 14: Build verification**
  - `./gradlew compileKotlin` — must pass after production changes
  - `./gradlew test` — must pass (all existing + new tests)
  - Verify no deprecation warnings break compilation

- [x] **Task 15: FR traceability verification**
  - Verify all 17 FRs addressed:
    - FR-001 (OTP): ✅ IMPLEMENTED → existing tests sufficient
    - FR-002 (TOTP): ✅ IMPLEMENTED → existing tests sufficient
    - FR-003 (MFA Settings): ✅ IMPLEMENTED → Task 10 (recovery codes)
    - FR-004 (CAPTCHA): ✅ IMPLEMENTED → Task 6 (CaptchaVerifier), Task 11 (rate limit)
    - FR-005 (Trusted Device): ✅ IMPLEMENTED → Task 1 (legacy fix), Task 3 (LoginHandler), Task 13 (legacy TTL test)
    - FR-006 (SSO Login): ✅ IMPLEMENTED → existing tests sufficient
    - FR-007 (JIT Provision): ✅ IMPLEMENTED → Tasks 4, 5, 7 (event sourcing)
    - FR-008 (SSO Link): ✅ IMPLEMENTED → existing tests sufficient
    - FR-009 (RS256): ✅ IMPLEMENTED → Task 5 (token events)
    - FR-010 (JWKS): ✅ IMPLEMENTED → existing tests sufficient
    - FR-011 (Introspect): ✅ IMPLEMENTED → existing tests sufficient
    - FR-012 (Force Logout): ✅ IMPLEMENTED → Task 8 (AdminSession)
    - FR-013 (Password Policy): ✅ IMPLEMENTED → Task 12 (password expiry)
    - FR-014 (Password History): ✅ IMPLEMENTED → existing tests sufficient
    - FR-015 (MFA Idempotency): ✅ IMPLEMENTED → Task 9 (IdempotencyFilter)
    - FR-016 (Audit Logging): ✅ IMPLEMENTED → existing tests sufficient
    - FR-017 (IdP Timeout): ✅ IMPLEMENTED → existing tests sufficient
  - **Coverage: 17/17 FRs ✓**

---

## Summary

| Phase | Tasks | Files Modified | Files New | Effort |
|-------|-------|---------------|-----------|--------|
| Phase 1: Security Fix (CRITICAL) | 1 | 1 (AuthService.kt) | 0 | ~30min |
| Phase 2: Documentation | 1 | 1 (SecurityProperties.kt) | 0 | ~10min |
| Phase 3: Unit Tests | 4 | 0 | 4 (LoginHandler, EventService, TokenEventRecorder, CaptchaVerifier) | ~1.5 days |
| Phase 4: Integration Tests | 7 | 0 | 7 (OutboxPoller, AdminSession, Idempotency, RecoveryCode, RateLimit, PwdExpiry, LegacyTtl) | ~2.5 days |
| Phase 5: Verification | 2 | 0 | 0 | ~30min |
| **Total** | **15** | **2** production | **11** test | **~4-5 days** |

### Execution Order (Dependency Graph)

```
Task 1 (AuthService.kt fix) ────────── CRITICAL PATH (security fix first)
Task 2 (SecurityProperties KDoc) ───── INDEPENDENT (can parallel with Task 1)
Task 3 (LoginHandlerTest) ──────────── INDEPENDENT (unit test, mocked)
Task 4 (EventServiceTest) ──────────── INDEPENDENT (unit test, mocked)
Task 5 (TokenEventRecorderTest) ─────── INDEPENDENT (unit test, mocked)
Task 6 (CaptchaVerifierTest) ────────── INDEPENDENT (unit test, mocked)
Task 7 (OutboxPollerTest) ──────────── INDEPENDENT (integration, own infra)
Task 8 (AdminSessionControllerTest) ── INDEPENDENT (integration, own infra)
Task 9 (IdempotencyFilterTest) ──────── INDEPENDENT (integration, Redis)
Task 10 (MfaRecoveryCodeFlowTest) ──── INDEPENDENT (integration, DB)
Task 11 (LoginRateLimitFilterTest) ──── INDEPENDENT (integration, Redis)
Task 12 (PasswordExpiryLoginTest) ──── INDEPENDENT (integration, DB)
Task 13 (LegacyLoginTtlTest) ────────── DEPENDS on Task 1 (AuthService fix applied)
Task 14 (Build verification) ────────── DEPENDS on ALL production tasks (1, 2)
Task 15 (FR traceability) ──────────── DEPENDS on ALL tasks
```

### Cross-Feature Coordination

| Shared File | This Feature | Other Feature | Risk |
|------------|-------------|---------------|------|
| `AuthService.kt` | MODIFY (TTL fix + @Deprecated) | None active | 🟢 Behavioral improvement, same API |
| `SecurityProperties.kt` | MODIFY (KDoc only) | None active | 🟢 Doc-only change |
| Test infrastructure | NEW (11 test files) | Existing test files | 🟢 Additive only, no conflicts |

### Design Decisions Applied

| Decision | Task | Evidence |
|----------|------|----------|
| D27: Fix AuthService.login() TTL + @Deprecated | Task 1 | Legacy path uses User.requiresMfa() |
| D28: Fix SecurityProperties KDoc | Task 2 | "Deferred" → "Implemented" |
| D29: Unit tests mock, integration tests Testcontainers | Tasks 3-13 | Standard Spring Boot test arch |
| D30: OutboxPoller test: embedded Kafka or mock | Task 7 | Full round-trip preferred |
| D32: Keep legacy AuthService with @Deprecated | Task 1 | Backward compat, future consolidation |

### Open Questions Applied

| Question | Status | Resolution |
|----------|--------|------------|
| Q3: Deprecate or fix AuthService.login()? | [RESOLVED] | Fix + @Deprecated (D27, D32) |
| Q4: MFA recovery code endpoints? | ⚠️ OPEN | Entity + repo exist, no controller. Future scope. Task 10 tests existing code. |
| Q5: Event sourcing test strategy? | [RESOLVED] | Unit mock ports (Tasks 4-5). Integration embedded Kafka (Task 7). |
