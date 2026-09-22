# Business Analysis: SOLID Architecture Refactoring

## 1. Executive Summary

Refactoring kiến trúc `auth-service` để tuân thủ SOLID principles và áp dụng proven design patterns, giải quyết technical debt đã tích lũy trong quá trình phát triển nhanh. Mục tiêu: code maintainable, testable, extensible.

## 2. Stakeholders

| Stakeholder | Interest | Impact |
|------------|---------|--------|
| **Development Team** | Code quality, productivity | Giảm coupling → faster feature dev |
| **QA Team** | Testability | Unit test dễ hơn → coverage tăng |
| **Operations** | Stability | Ít bug do SRP → deployment confidence |
| **Product** | Feature velocity | Thêm MFA method, CAPTCHA type nhanh hơn |

## 3. Use Cases

### UC-001: Authentication Pipeline Refactoring

**Tại sao cần**: `LoginHandler` hiện có 17 dependencies và 10 responsibilities, vi phạm SRP nghiêm trọng. Bất kỳ thay đổi nào ở 1 concern (lockout, captcha, MFA) đều có thể ảnh hưởng toàn bộ login flow. Đã gặp NPE khi test do thiếu 1 dependency.

**Basic Flow**:
1. Controller dispatch `LoginCommand` qua CommandBus
2. CommandBus resolve `LoginHandler`
3. `LoginHandler` delegate cho `AuthenticationPipeline`
4. Pipeline chạy tuần tự các steps:
   - `SecurityPreCheckStep`: RateLimit + Lockout + Captcha
   - `CredentialVerificationStep`: User lookup + Password verify + Hash upgrade
   - `PolicyEnforcementStep`: Password expiry + Session limit
   - `SessionEstablishmentStep`: Session creation + Device fingerprint + Anonymous promotion
   - `TokenIssuanceStep`: JWT generation + Event recording
5. Pipeline return `LoginResult`

**Exception Flows**:
- E1: Step throw `AuthException` → Pipeline catches, records failure event, re-throws
- E2: MFA required → `CredentialVerificationStep` return `MfaRequired` → Pipeline short-circuits
- E3: Account locked → `SecurityPreCheckStep` throw → Pipeline terminates

**Business Rules**:
- BR-001: Pipeline steps execute in strict order
- BR-002: Any step can short-circuit the pipeline
- BR-003: Failure recording happens regardless of which step fails
- BR-004: Each step receives and mutates shared `AuthenticationContext`

---

### UC-002: CommandBus / Mediator Pattern

**Tại sao cần**: `CqrsAuthController` inject 13 dependencies trực tiếp (6 handlers + 7 services). Điều này tạo tight coupling và khiến controller trở thành dependency hub. CQRS pattern bị triệt tiêu khi controller biết cụ thể từng handler.

**Basic Flow**:
1. Controller nhận HTTP request
2. Controller map request → Command DTO
3. Controller dispatch: `commandBus.dispatch(command)` 
4. CommandBus resolve handler từ registry
5. Handler execute và return result
6. Controller wrap result vào `ApiResponse`

**Exception Flows**:
- E1: No handler registered → `HandlerNotFoundException`
- E2: Handler throws → Exception propagates to `@ControllerAdvice`

**Business Rules**:
- BR-005: Controller chỉ phụ thuộc `CommandBus` + `QueryBus` (max 2-3 deps)
- BR-006: Handler auto-registration via Spring `List<CommandHandler>` injection
- BR-007: Pipeline behaviors (logging, metrics) là optional decorators

---

### UC-003: MFA Provider Strategy

**Tại sao cần**: `MfaService` hardcode `when (method) { "SMS" → ... "TOTP" → ... }` tại nhiều hàm. Khi cần tích hợp WebAuthn/Passkey (đã có demand), phải sửa MfaService ở nhiều nơi → vi phạm OCP.

**Basic Flow**:
1. Login flow xác định user requires MFA
2. `MfaProviderRegistry.getProvider(method)` resolve provider
3. Provider `initiate(userId)` → generate challenge
4. User submit code
5. Provider `verify(userId, code)` → validate
6. Success → generate full auth token

**Exception Flows**:
- E1: Provider not found → `UnsupportedMfaMethodException`
- E2: Code invalid → `MfaCodeInvalidException`
- E3: Rate limit exceeded → `MfaMaxAttemptsException`

**Business Rules**:
- BR-008: Mỗi MFA method = 1 `MfaProvider` implementation
- BR-009: Thêm method mới = thêm 1 Spring Bean, không sửa code cũ
- BR-010: `MfaService` chỉ làm orchestrator (parse token → dispatch → handle result)

---

### UC-004: Clean Architecture cho RBAC/PBAC

**Tại sao cần**: `PolicyController`, `RolePermissionController`, `RbacControllers.kt` inject trực tiếp JPA Repositories, bypass Application layer hoàn toàn. Vi phạm DIP nghiêm trọng nhất trong codebase. Business logic (tạo default role, state machine DRAFT→ACTIVE) nằm trong Controller.

**Basic Flow — Policy Management**:
1. Admin request tạo/sửa/xóa policy
2. Controller map request → Use Case command
3. `PolicyManagementUseCase` validate business rules
4. Use Case gọi Repository qua Port interface
5. Return result

**Exception Flows**:
- E1: Policy activation without conditions → Validation error
- E2: Domain not found → `ResourceNotFoundException`

**Business Rules**:
- BR-011: Policy state machine: DRAFT → ACTIVE → INACTIVE → DELETED (soft delete)
- BR-012: Policy chỉ activate được khi có ≥ 1 condition
- BR-013: Domain creation auto-generate default roles + permissions

---

### UC-005: CaptchaController OCP Fix (Quick Win)

**Tại sao cần**: `CaptchaController` hardcode `when (type) { "image" → ...; else → ... }` dù đã có `CaptchaStrategyRegistry` sẵn. Thêm captcha type thứ 3 buộc sửa Controller.

**Basic Flow**:
1. Client request GET /captcha/challenge?type=altcha
2. Controller gọi `registry.getStrategy(type).generateChallenge()`
3. Return challenge

**Business Rules**:
- BR-014: Thêm CAPTCHA type mới = thêm Bean implement `CaptchaStrategy`, không sửa controller

---

### UC-006: PolicyEvaluator Operator Strategy (Quick Win)

**Tại sao cần**: `evaluateCondition()` dùng `when (operator)` với 12 nhánh cứng. Thêm operator mới (regex, geo_within, ip_in_range) buộc sửa function.

**Basic Flow**:
1. Policy evaluation request
2. Cho mỗi condition: resolve operator → `ConditionOperator.valueOf(operator)`
3. Operator execute: `operator.evaluate(actual, expected)`
4. Aggregate results

**Business Rules**:
- BR-015: Thêm operator mới = thêm enum entry, không sửa evaluation logic

---

## 4. Traceability Matrix

| Use Case | Gap ID | Priority | SOLID Principle | Pattern |
|----------|--------|----------|----------------|---------|
| UC-001 | GAP-01 | P1 | SRP | Chain of Responsibility |
| UC-002 | GAP-02 | P2 | DIP, SRP | Mediator / CommandBus |
| UC-003 | GAP-03 | P2 | OCP, SRP | Strategy + Registry |
| UC-004 | GAP-06, GAP-07, GAP-08 | P1 | DIP | Clean Architecture layers |
| UC-005 | GAP-04 | P1 (QW) | OCP | Strategy (existing) |
| UC-006 | GAP-05 | P1 (QW) | OCP | Enum Strategy |

## 5. Non-Functional Requirements

- **Performance**: Không thêm overhead đáng kể (< 1μs per request cho CommandBus dispatch)
- **Backward Compatibility**: API contracts (HTTP endpoints) không thay đổi
- **Test Coverage**: Mỗi refactored component phải có unit test ≥ 80%
- **Zero Downtime**: Refactoring không yêu cầu database migration
