# SRS: SOLID Architecture Refactoring

> **Change**: solid-architecture-refactoring
> **Type**: MAINTENANCE
> **Version**: 1.0
> **Date**: 2026-09-22

---

## 1. Scope

Refactoring nội bộ `auth-service` để tuân thủ SOLID principles. **Không thay đổi API contracts** — tất cả HTTP endpoints, request/response DTOs, và business behavior giữ nguyên.

### In Scope
- Tách `LoginHandler` God Class → Authentication Pipeline (Chain of Responsibility)
- Wire `CommandBus`/`QueryBus` từ eventsourcing-utils cho Controllers
- Chuẩn hóa MFA Strategy Pattern (`MfaProviderRegistry`)
- Tạo Application Layer cho RBAC/PBAC (Use Cases + Port interfaces)
- Fix OCP violation trong `PolicyEvaluator` (`ConditionOperator` enum)
- Thêm `LockoutPort` abstraction cho `AccountLockoutService`
- Logging decorator cho CommandBus/QueryBus

### Out of Scope
- Database schema changes
- API contract changes (endpoints, DTOs)
- New business features
- Performance optimization beyond structural improvements
- UI/Frontend changes

---

## 2. Functional Requirements Traceability

| FR-ID | Title | Phase | Task | Affected Files | Test |
|-------|-------|-------|------|---------------|------|
| FR-001 | Tách LoginHandler → Pipeline | P2b | T23 | `LoginHandler.kt` | T24, T25 |
| FR-002 | AuthenticationStep interface | P2b | T16 | NEW `AuthenticationStep.kt` | T24 |
| FR-003 | AuthenticationContext (immutable) | P2b | T16 | NEW `AuthenticationContext.kt` | T24 |
| FR-004 | SecurityPreCheckStep | P2b | T18 | NEW `SecurityPreCheckStep.kt` | T24 |
| FR-005 | CredentialVerificationStep | P2b | T19 | NEW `CredentialVerificationStep.kt` | T24 |
| FR-006 | PolicyEnforcementStep | P2b | T20 | NEW `PolicyEnforcementStep.kt` | T24 |
| FR-007 | SessionEstablishmentStep | P2b | T21 | NEW `SessionEstablishmentStep.kt` | T24 |
| FR-008 | TokenIssuanceStep | P2b | T22 | NEW `TokenIssuanceStep.kt` | T24 |
| FR-009 | CommandBus (REUSE) | P2a | T12 | NEW `CqrsConfig.kt` | T15 |
| FR-010 | Giảm deps CqrsAuthController | P3 | T34 | `CqrsAuthController.kt` | T35 |
| FR-011 | MfaProvider interface | P2c | T26 | NEW `MfaProvider.kt` | T30 |
| FR-012 | MfaProviderRegistry | P2c | T27 | NEW `MfaProviderRegistry.kt` | T30 |
| FR-013 | CaptchaController OCP | P1a | — | Already fixed via `CaptchaStrategyRegistry` | — |
| FR-014 | RBAC Application Use Cases | P1b | T7, T9 | NEW `RoleManagementUseCase.kt`, `PermissionUseCase.kt` | T11 |
| FR-015 | PBAC Application Use Cases | P1b | T8, T10 | NEW `PolicyManagementUseCase.kt` | T11 |
| FR-016 | ConditionOperator enum | P1a | T1 | NEW `ConditionOperator.kt` | T3 |
| FR-017 | Single-pass policy evaluation | P1a | T2 | `PolicyEvaluator.kt` | T3 |
| FR-018 | Port interfaces RBAC/PBAC | P1b | T4, T5 | NEW `RolePort.kt`, `PermissionPort.kt`, `PolicyPort.kt` | T11 |
| FR-019 | Fix N+1 query RBAC | P1b | T9 | `RolePermissionController.kt` | T11 |
| FR-020 | LockoutPort abstraction | P3 | T31-33 | NEW `LockoutPort.kt`, MOD `AccountLockoutService.kt` | T35 |

**Coverage**: 20/20 FRs mapped (100%)

---

## 3. Non-Functional Requirements

| NFR-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-001 | API contracts KHÔNG thay đổi | Integration tests pass — same endpoints, same DTOs |
| NFR-002 | Performance overhead < 1μs cho CommandBus dispatch | CommandBus is just HashMap lookup + method call |
| NFR-003 | Test coverage ≥ 80% cho new components | `./gradlew test jacocoTestReport` |
| NFR-004 | Không yêu cầu database migration | No Flyway scripts needed |
| NFR-005 | Backward compatibility | All existing integration tests pass unchanged |
| NFR-006 | Logging decorator cho flow tracing | CommandBus logs command name + duration |

---

## 4. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| LoginHandler refactoring breaks integration tests | 🔴 High | Medium | Run tests sau mỗi step extraction, git commit per step |
| CommandBus handler not found at runtime | 🟡 Medium | Low | `commandType()` đã verified, Spring auto-wires `List<CommandHandler>` |
| RbacControllers split misses endpoint | 🟡 Medium | Low | Verify all `@*Mapping` annotations present |
| MFA provider not registered | 🟡 Medium | Low | Spring `List<MfaProvider>` auto-inject, test registry lookup |

---

## 5. Assumptions

| ID | Assumption | Validated |
|----|------------|-----------|
| A-001 | `eventsourcing-utils` provides CommandBus/QueryBus | ✅ Decompiled JAR — confirmed |
| A-002 | `SpringCommandBus` uses `commandType()` for handler mapping | ✅ Decompiled — `associateBy { it.commandType() }` |
| A-003 | `SpringCommandBus` NOT auto-configured (@Component) | ✅ Decompiled — only kotlin.Metadata annotation |
| A-004 | All existing `CommandHandler` impls have `commandType()` override | ✅ Grep verified — all 9 handlers |
| A-005 | `AuthenticationContext` immutable (data class) | ✅ User decision in brainstorm OQ-002 |
