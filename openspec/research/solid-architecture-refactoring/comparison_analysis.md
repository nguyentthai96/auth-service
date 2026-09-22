# Comparison Analysis: SOLID Architecture Refactoring Approaches

## Comparison Matrix

| Đề xuất | Approach A: Adopt Framework | Approach B: BUILD Lightweight | Approach C: Do Nothing | Recommendation |
|---------|---------------------------|-------------------------------|----------------------|----------------|
| **1. LoginHandler Pipeline** | Adopt Axon Pipeline | Custom `AuthenticationStep` chain | Keep God Class | **B — BUILD** |
| **2. CommandBus** | Axon CommandGateway | Custom `CommandBus` interface | Keep direct injection | **B — BUILD** |
| **3. MFA Strategy** | N/A (no MFA framework) | Custom `MfaProvider` SPI | Keep `when` in MfaService | **B — BUILD** |
| **4. RBAC/PBAC Clean Arch** | N/A | Application Use Cases | Keep layer bypass | **B — BUILD** |
| **5. PolicyEvaluator OCP** | Adopt OPA/Casbin | Enum Strategy Operator | Keep `when` clause | **B — BUILD** |
| **6. ResponseBodyAdvice** | N/A | `@ControllerAdvice` advice | Keep BaseController | **C — DEFER** |

---

## Feature-by-Feature Comparison

### Đề xuất 1: LoginHandler → Authentication Pipeline

| Feature | Axon Pipeline | Custom Pipeline | Current (God Class) | Cần cho project? |
|---------|:------------:|:---------------:|:-------------------:|:----------------:|
| SRP per step | ✅ | ✅ | ❌ | ⭐ Must |
| Unit testable steps | ✅ | ✅ | ⚠️ Hard | ⭐ Must |
| Feature flags per step | ✅ | ✅ | ❌ | Nice-to-have |
| Short-circuit (MFA) | ✅ | ✅ | ✅ (via return) | ⭐ Must |
| Type-safe context | ✅ | ✅ | ⚠️ Raw vars | ⭐ Must |
| Zero framework overhead | ❌ | ✅ | ✅ | ⭐ Must |
| Learning curve | ❌ High | ✅ Low | ✅ None | Important |

### Đề xuất 2: CommandBus / Mediator

| Feature | Axon | Custom CommandBus | Direct Injection | Cần cho project? |
|---------|:----:|:-----------------:|:----------------:|:----------------:|
| Controller decoupling | ✅ | ✅ | ❌ | ⭐ Must |
| Pipeline decorators | ✅ | ✅ | ❌ | Nice-to-have |
| Type-safe dispatch | ✅ | ✅ | ✅ | ⭐ Must |
| Dependency count reduction | ✅ | ✅ | ❌ | ⭐ Must |
| No extra framework | ❌ | ✅ | ✅ | ⭐ Must |
| Auto-handler discovery | ✅ | ✅ (Spring) | N/A | Nice-to-have |

### Đề xuất 3: MFA Strategy Pattern

| Feature | Custom MfaProvider | Current MfaService | Cần cho project? |
|---------|:------------------:|:------------------:|:----------------:|
| Add new method (no code change) | ✅ | ❌ | ⭐ Must |
| Per-provider unit tests | ✅ | ⚠️ | ⭐ Must |
| Isolated dependencies | ✅ | ❌ (10 in 1 class) | ⭐ Must |
| WebAuthn/Passkey ready | ✅ | ❌ (requires when clause edit) | Nice-to-have |

### Đề xuất 5: PolicyEvaluator Operator Strategy

| Feature | Enum Strategy | when clause | OPA/Rego | Cần cho project? |
|---------|:------------:|:-----------:|:--------:|:----------------:|
| Add operator without code change | ✅ | ❌ | ✅ | ⭐ Must |
| No external dependency | ✅ | ✅ | ❌ | ⭐ Must |
| Type safety | ✅ | ⚠️ | ❌ | Nice-to-have |
| Single iteration (perf) | ✅ | ❌ (2 loops) | ✅ | Nice-to-have |

---

## Gap Analysis Summary

| Gap ID | Gap Description | Current Impact | Proposed Solution | Priority |
|--------|----------------|---------------|-------------------|----------|
| GAP-01 | LoginHandler 17 deps, untestable | 🔴 HIGH | Auth Pipeline (5 steps) | P1 |
| GAP-02 | CqrsAuthController 13 deps | 🟡 MEDIUM | CommandBus dispatch | P2 |
| GAP-03 | MfaService OCP + SRP violation | 🟡 MEDIUM | MfaProvider Strategy | P2 |
| GAP-04 | CaptchaController bypass registry | 🟢 LOW | Use existing registry | P1 (Quick Win) |
| GAP-05 | PolicyEvaluator hardcode operators | 🟡 MEDIUM | Enum Strategy | P1 (Quick Win) |
| GAP-06 | RBAC Controllers bypass layers | 🔴 HIGH | Application Use Cases | P1 |
| GAP-07 | PBAC Controller bypass layers | 🔴 HIGH | PolicyManagementUseCase | P1 |
| GAP-08 | N+1 queries in RolePermissionController | 🟡 MEDIUM | Batch query in Use Case | P1 |
| GAP-09 | AccountLockoutService → RedisLockoutAdapter | 🟢 LOW | LockoutPort abstraction | P2 |
| GAP-10 | BaseController inheritance | 🟢 LOW | ResponseBodyAdvice (DEFER) | P3 |

---

## Overall Recommendation

> **BUILD** — Tự implement lightweight patterns. Không adopt framework nặng.

**Lý do**:
1. Auth-service là core system — cần full control, không phụ thuộc framework bên thứ 3
2. Các pattern cần implement đều lightweight (interface + Spring auto-wiring)
3. Axon/OPA quá nặng cho scope hiện tại
4. Team đã quen Spring Boot conventions → custom code dễ maintain hơn learn framework mới

**Gap Coverage**: 10/10 gaps identified, 9/10 có giải pháp rõ ràng, 1 deferred (ResponseBodyAdvice).
