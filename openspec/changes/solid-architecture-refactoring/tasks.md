<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- self-contained: true -->
# Tasks: SOLID Architecture Refactoring

> **Change**: solid-architecture-refactoring
> **Type**: MAINTENANCE
> **Phases**: 6 (P1a → P1b → P2a → P2b → P2c → P3)
> **Total Est.**: 28-40h (reduced from 37-52h due to CommandBus reuse)

---

## Phase P1a — Quick Wins (2-4h, 🟢 LOW risk) ✅

- [x] **Task 1: Tạo ConditionOperator enum**
  - File: `src/main/kotlin/com/ntt/authservice/pbac/application/ConditionOperator.kt` | Action: [NEW]
  - FR: FR-016 — Enum class with evaluate lambda + fromString factory

- [x] **Task 2: Refactor PolicyEvaluator dùng ConditionOperator**
  - File: `src/main/kotlin/com/ntt/authservice/pbac/application/PolicyEvaluator.kt` | Action: [MODIFY]
  - FR: FR-016, FR-017 — Single-pass evaluation + ConditionOperator.fromString()

- [x] **Task 3: Unit tests cho ConditionOperator**
  - File: `src/test/kotlin/com/ntt/authservice/pbac/application/ConditionOperatorTest.kt` | Action: [NEW]
  - FR: FR-016 — 18 test cases covering all operators + fromString

---

## Phase P2a — CommandBus Infrastructure (1-2h, 🟢 LOW risk) ✅

- [x] **Task 12: Tạo CqrsConfig bean declaration**
  - File: `src/main/kotlin/com/ntt/authservice/shared/cqrs/CqrsConfig.kt` | Action: [NEW]
  - FR: FR-009 — @Bean for SpringCommandBus + SpringQueryBus with Logging decorators

- [x] **Task 13: Tạo LoggingCommandBus decorator**
  - File: `src/main/kotlin/com/ntt/authservice/shared/cqrs/LoggingCommandBus.kt` | Action: [NEW]
  - FR: FR-009, OQ-005 — Timing + error logging

- [x] **Task 14: Tạo LoggingQueryBus decorator**
  - File: `src/main/kotlin/com/ntt/authservice/shared/cqrs/LoggingQueryBus.kt` | Action: [NEW]

- [x] **Task 15: Unit tests cho LoggingCommandBus**
  - File: `src/test/kotlin/com/ntt/authservice/shared/cqrs/LoggingCommandBusTest.kt` | Action: [NEW]

---

## Phase P2b — Authentication Pipeline (10-14h, 🔴 HIGH risk) ✅

- [x] **Task 16: Tạo Pipeline interfaces + context**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/AuthenticationStep.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/StepOutcome.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/AuthenticationContext.kt` | Action: [NEW]
  - FR: FR-002, FR-003

- [x] **Task 17: Tạo AuthenticationPipeline orchestrator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/AuthenticationPipeline.kt` | Action: [NEW]
  - FR: FR-001

- [x] **Task 18: Tạo SecurityPreCheckStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/SecurityPreCheckStep.kt` | Action: [NEW]
  - FR: FR-004 — User lookup, lockout, captcha, rate limiting

- [x] **Task 19: Tạo CredentialVerificationStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/CredentialVerificationStep.kt` | Action: [NEW]
  - FR: FR-005 — Password verify, hash upgrade, lockout reset

- [x] **Task 20: Tạo PolicyEnforcementStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/PolicyEnforcementStep.kt` | Action: [NEW]
  - FR: FR-006 — Password expiry, MFA check, session policy

- [x] **Task 21: Tạo SessionEstablishmentStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/SessionEstablishmentStep.kt` | Action: [NEW]
  - FR: FR-007 — Login session, device fingerprint, anonymous promotion

- [x] **Task 22: Tạo TokenIssuanceStep**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/pipeline/TokenIssuanceStep.kt` | Action: [NEW]
  - FR: FR-008 — JWT generation with metadata

- [x] **Task 23: Refactor LoginHandler dùng Pipeline**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - FR: FR-001 — 17 deps → 2 deps (Pipeline + EventRecorder)

---

## Phase P2c — MFA Provider Strategy (4-6h, 🟡 MEDIUM risk) ✅

- [x] **Task 26: Tạo MfaProvider interface**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/mfa/MfaProvider.kt` | Action: [NEW]
  - FR: FR-011 — Strategy interface

- [x] **Task 27: Tạo MfaProviderRegistry**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/mfa/MfaProviderRegistry.kt` | Action: [NEW]
  - FR: FR-012 — Auto-register, O(1) lookup

- [x] **Task 28: Extract MFA implementations**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/mfa/SmsMfaProvider.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/mfa/EmailMfaProvider.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/mfa/TotpMfaProvider.kt` | Action: [NEW]
  - FR: FR-011

- [x] **Task 29: Refactor MfaService dùng MfaProviderRegistry**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` | Action: [MODIFY]
  - FR: FR-012 — Replace when with registry.get(method)

---

## Phase P3 — Controller Decoupling via CommandBus (4-6h, 🟡 MEDIUM risk) ✅

- [x] **Task 34: Refactor CqrsAuthController dùng CommandBus + QueryBus**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-010 — 13 deps → 9 deps (handler injections replaced with commandBus.dispatch())

---

## Deferred Tasks (Phase P1b — RBAC/PBAC, Phase P3 LockoutPort)

> These tasks involve RBAC/PBAC module refactoring (Port interfaces + Use Cases).
> They are independent from the core auth pipeline and can be done as a separate change.

- [ ] **Tasks 4-11: RBAC/PBAC Application Layer** (deferred — separate change)
- [ ] **Tasks 24-25: Pipeline unit + integration tests** (deferred — follow-up)
- [ ] **Task 30: MFA provider tests** (deferred — follow-up)
- [ ] **Tasks 31-33: LockoutPort abstraction** (deferred — separate change)
- [ ] **Task 35: Full integration test pass** (deferred — follow-up)
