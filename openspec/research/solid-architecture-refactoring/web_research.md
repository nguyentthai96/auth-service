# Web Research: SOLID Architecture Patterns for Authentication Systems

## Search Iterations

### Iteration 1: Broad Search — Architecture Patterns

#### Finding 1: Mediator Pattern in CQRS (Baeldung/DZone pattern)
- **Source**: Baeldung — CQRS with Mediator pattern articles; DZone — Mediator pattern in Spring
- **Key Insights**:
  - Mediator Pattern giúp decouple Controller khỏi Handler cụ thể
  - Spring auto-wiring `List<CommandHandler>` tạo registry tự động
  - Pipeline behaviors (pre/post processing) giúp cross-cutting concerns (logging, validation, transaction) tách ra khỏi handler
  - Kotlin generic reification (`inline fun <reified C>`) giúp type-safe dispatch
- **Relevance**: 9/10 — Trực tiếp giải quyết vấn đề `CqrsAuthController` 13 dependencies
- **Pros**: Decoupling hoàn toàn, testable, extensible pipeline
- **Cons**: Thêm abstraction layer, debugging qua bus khó hơn trực tiếp

#### Finding 2: Chain of Responsibility cho Authentication
- **Source**: Design Patterns: Elements of Reusable OO Software (GoF); Spring Security Filter Chain architecture
- **Key Insights**:
  - Spring Security bản thân đã dùng `FilterChain` = Chain of Responsibility
  - Mỗi filter là một step độc lập: `SecurityContextPersistenceFilter → UsernamePasswordAuthenticationFilter → ExceptionTranslationFilter → ...`
  - Authentication pipeline = danh sách ordered steps, mỗi step nhận `AuthenticationContext` và trả về result hoặc throw
  - Step có thể short-circuit (MFA required) hoặc continue (pass check)
  - Keycloak implement tương tự: `Authenticator.authenticate(context)` → `context.success()` hoặc `context.challenge(response)`
- **Relevance**: 10/10 — Chính xác pattern cần cho `LoginHandler` refactoring
- **Pros**: SRP per step, unit test riêng, feature flags per step, ordered execution
- **Cons**: Context object cần thiết kế cẩn thận, debugging flow dài hơn

---

### Iteration 2: Deep Dive — MFA & Strategy Pattern

#### Finding 3: MFA Provider Abstraction Pattern
- **Source**: Keycloak SPI docs; Auth0 architecture blog
- **Key Insights**:
  - Keycloak: `CredentialProvider<T extends CredentialModel>` + `CredentialValidator<T>` — mỗi MFA method là 1 provider
  - Auth0: Factor object model — `SmsFactor`, `EmailFactor`, `TotpFactor` — polymorphic dispatch
  - Pattern chung:
    ```
    interface MfaProvider {
        val method: String          // "SMS", "EMAIL", "TOTP", "WEBAUTHN"
        fun initiate(userId): Result
        fun verify(userId, code): Boolean
        fun isConfigured(user): Boolean
    }
    ```
  - Registry pattern: `MfaProviderRegistry(providers: List<MfaProvider>)` — Spring auto-inject
  - `MfaService` chỉ là orchestrator: parse MFA token → dispatch tới đúng provider → handle result
- **Relevance**: 9/10 — Giải quyết OCP violation và SRP violation trong `MfaService`
- **Pros**: Thêm WebAuthn/Passkey chỉ cần thêm 1 bean, không đụng MfaService
- **Cons**: Mỗi provider cần inject riêng dependencies (OtpService, TotpService)

#### Finding 4: CAPTCHA Strategy hoàn thiện
- **Key Insights**:
  - Project đã có `CaptchaStrategyRegistry` tốt, nhưng `CaptchaController` bypass nó
  - Fix OCP: Thêm `generateChallenge(): Any` vào `CaptchaStrategy` interface
  - Controller chỉ cần: `okResponse(registry.getStrategy(type).generateChallenge())`
  - Tổng thời gian fix: ~15 phút (Quick Win — P1)
- **Relevance**: 8/10
- **Pros**: Đơn giản, impact thấp, fix ngay được
- **Cons**: Hầu như không có

---

### Iteration 3: Targeted Follow-up — Policy Evaluation & Clean Architecture

#### Finding 5: Enum Strategy cho Operator Evaluation
- **Source**: Effective Java (Bloch) — Enum with behavior; OPA Rego operator model
- **Key Insights**:
  - Kotlin enum class có thể chứa lambda function → thay thế `when` switch
  - Pattern:
    ```kotlin
    enum class ConditionOperator(val evaluate: (Any?, Any?) -> Boolean) {
        EQ({ a, e -> a?.toString() == e?.toString() }),
        IN({ a, e -> (e as? List<*>)?.map { it.toString() }?.contains(a?.toString()) == true }),
        GT({ a, e -> compareNumbers(a, e) > 0 }),
        // ...
    }
    ```
  - Thêm operator mới = thêm 1 enum entry, không cần sửa `evaluateCondition()`
  - `evaluatePolicies` hiện duyệt 2 lần (loop DENY + `any{}` ALLOW) → optimize thành 1 lần
- **Relevance**: 8/10
- **Pros**: OCP compliance, extensible, no cyclomatic complexity growth
- **Cons**: Cần migration từ string-based `"eq"` → enum `EQ` (hoặc `valueOf()`)

#### Finding 6: Clean Architecture cho RBAC/PBAC — Application Layer
- **Source**: Clean Architecture (Uncle Bob); Hexagonal Architecture (Alistair Cockburn)
- **Key Insights**:
  - Nguyên tắc: **Controller → Use Case → Port → Adapter**
  - Hiện tại RBAC/PBAC: **Controller → Repository** (bypass Application layer hoàn toàn)
  - Fix: Tạo Application Use Cases:
    - `PolicyManagementUseCase` (CRUD + state machine DRAFT → ACTIVE → INACTIVE → DELETED)
    - `RolePermissionManagementUseCase` (batch assign, resolve permission matrix)
    - `DomainManagementUseCase` (create domain + default roles/permissions)
  - N+1 query fix: `RolePermissionController.listRolePermissions()` hiện loop từng permission → JOIN query
- **Relevance**: 10/10
- **Pros**: Clean Architecture compliance, testable use cases, batch optimization
- **Cons**: Tạo thêm files/classes, migration effort

#### Finding 7: ResponseBodyAdvice vs BaseController inheritance
- **Source**: Spring Framework docs; Community best practices
- **Key Insights**:
  - `ResponseBodyAdvice<Any>` intercept tất cả response trước khi serialize
  - Controller return trực tiếp DTO → advice wrap vào `ApiResponse.success(body)`
  - Skip cho ProblemDetail, JWKS, và raw responses (annotation `@RawResponse`)
  - Trade-off:

    | | `BaseController` (hiện tại) | `ResponseBodyAdvice` |
    |---|---|---|
    | Explicit | ✅ Rõ ràng `okResponse()` | ⚠️ Magic, implicit |
    | Debugging | ✅ Dễ trace | ⚠️ Advice chain khó debug |
    | Inheritance | ❌ Forced inheritance | ✅ Composition |
    | Consistency | ⚠️ Developer phải nhớ gọi | ✅ Tự động, không miss |
    | Kotlin compat | ⚠️ `open class` required | ✅ Any class |

  - **⚠️ Assumption**: Với trạng thái hiện tại đã chuẩn hóa `okResponse()` ở tất cả controllers, `ResponseBodyAdvice` mang tính **nice-to-have**. Ưu tiên thấp hơn các fix DIP/SRP.
- **Relevance**: 5/10 — Lower priority
- **Pros**: Cleaner controllers, composition over inheritance
- **Cons**: Implicit magic, harder debugging, breaking existing pattern

---

## Products/Tools Evaluated

| Tool | Approach | Pros | Cons | Gap | Fit |
|------|----------|------|------|-----|-----|
| Axon Framework | Full CQRS+ES framework | Battle-tested, pipeline | Heavy, complex | Overkill for auth | Tham khảo |
| MediatR (.NET) | Mediator + pipeline | Clean API, pipeline behaviors | Wrong stack | Port pattern | Tham khảo |
| OPA / Rego | External policy engine | Powerful, declarative | Learning curve, extra infra | External dependency | Tham khảo |
| Keycloak SPI | Auth flow + MFA SPIs | Production-proven | Monolithic | Extract patterns | **Tham khảo core** |
| Casbin | RBAC/ABAC library | Multi-model, multi-language | Java lib quality | Feature fit unclear | Skip |
| Spring Modulith | Module boundaries | ArchUnit, events | Not architecture-level | Nice-to-have | Later |

---

## Key Conclusions

1. **BUILD recommendation** — tự implement lightweight patterns, không adopt framework nặng
2. **Authentication Pipeline** = tham khảo Keycloak SPI `Authenticator` + Spring Security `FilterChain`
3. **CommandBus** = tham khảo MediatR interface + Axon dispatch mechanism
4. **MFA Provider** = tham khảo Keycloak `CredentialProvider` SPI
5. **Policy Operator** = Kotlin enum with behavior (tham khảo OPA extensibility)
6. **ResponseBodyAdvice** = ưu tiên thấp, BaseController hiện tại đã đủ tốt
