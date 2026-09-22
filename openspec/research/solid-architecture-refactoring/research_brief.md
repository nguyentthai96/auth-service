# Research Brief: SOLID Architecture Refactoring

## 1. Feature Overview

**Feature**: Refactoring kiến trúc `auth-service` để tuân thủ nguyên lý SOLID và áp dụng Design Patterns phù hợp.

**Input Mode**: FILE — từ `solid_architecture_analysis.md` (phân tích kiến trúc SOLID đã thực hiện).

**Scope**: 6 đề xuất cải tiến chính:
1. **Authentication Pipeline** (Chain of Responsibility) — tách `LoginHandler` God Class
2. **CommandBus / Mediator Pattern** — giảm dependencies cho `CqrsAuthController`
3. **Strategy Pattern chuẩn hóa** — CaptchaController & MfaService
4. **Clean Architecture cho RBAC/PBAC** — tách Controllers khỏi Repositories
5. **Specification Pattern cho PolicyEvaluator** — thay `when` hardcode bằng Enum Strategy
6. **ResponseBodyAdvice** — loại bỏ inheritance `BaseController`

## 2. Research Keywords

| # | Keyword | Scope |
|---|---------|-------|
| 1 | `Chain of Responsibility authentication pipeline` | Đề xuất 1 |
| 2 | `CQRS CommandBus Mediator pattern Spring Boot Kotlin` | Đề xuất 2 |
| 3 | `Strategy Pattern registry Spring auto-inject` | Đề xuất 3 |
| 4 | `Clean Architecture Hexagonal Ports Adapters RBAC` | Đề xuất 4 |
| 5 | `Policy evaluation Specification Pattern enum operator` | Đề xuất 5 |
| 6 | `Spring ResponseBodyAdvice global response wrapping` | Đề xuất 6 |
| 7 | `God Class refactoring Pipeline Pattern` | Cross-cutting |
| 8 | `ABAC PBAC condition evaluator open source` | Đề xuất 5 |
| 9 | `MFA provider abstraction multi-factor strategy` | Đề xuất 3 |
| 10 | `Spring Boot constructor injection dependency limit` | Đề xuất 1 |

## 3. Research Questions

1. Các open source project nào đã implement CommandBus pattern cho Spring Boot/Kotlin?
2. Authentication Pipeline (multi-step) được implement thế nào trong các hệ thống production?
3. Có framework nào hỗ trợ ABAC/PBAC policy evaluation với extensible operator set?
4. Best practice cho việc tách God Class mà không break test suite?
5. ResponseBodyAdvice có trade-off gì so với BaseController inheritance?
6. MFA Provider abstraction pattern trong các hệ thống auth lớn (Keycloak, Auth0)?

## 4. Current System Analysis

### 4.1 Related Features

| Feature | Module | File(s) | Relevance | Issue |
|---------|--------|---------|-----------|-------|
| Login flow | `auth/application/command` | `LoginHandler.kt` (254 lines, 17 deps) | ⭐ Core | God Class, SRP violation |
| MFA flow | `auth/application` | `MfaService.kt` (368 lines, 10 deps) | ⭐ Core | SRP + OCP violation |
| CAPTCHA | `auth/application` | `CaptchaStrategyRegistry.kt`, `CaptchaController.kt` | Medium | OCP violation ở Controller |
| Policy eval | `pbac/application` | `PolicyEvaluator.kt` (176 lines) | Medium | OCP violation, duplicate iteration |
| RBAC admin | `rbac/adapter/in/web` | `RbacControllers.kt` (13KB), `RolePermissionController.kt`, `PolicyController.kt` | High | DIP violation, layer bypassing |
| Response model | `shared/web` | `BaseController.kt` | Medium | Inheritance > Composition |
| CQRS dispatch | `auth/adapter/in/web` | `CqrsAuthController.kt` (278 lines, 13 deps) | Medium | Pseudo-CQRS, no CommandBus |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Quality |
|---------|---------------|---------|
| **Architecture** | Clean Architecture (Hexagonal) + CQRS | ⚠️ Bất đối xứng: `auth` module tốt, `rbac/pbac` bypass layers |
| **Data Access** | Spring Data JPA + Repositories | ✅ Auth module dùng Ports; ❌ RBAC/PBAC gọi trực tiếp |
| **API Style** | REST + ApiResponse envelope | ✅ Đã chuẩn hóa xong |
| **Error Handling** | `@ControllerAdvice` + ProblemDetail (RFC 7807) | ✅ Tốt |
| **CQRS** | Command/Query Handlers + `CommandHandler<C,R>` interface | ⚠️ Controller inject trực tiếp handler, không qua Bus |
| **Strategy** | `CaptchaStrategy` + `CaptchaStrategyRegistry` | ⚠️ Registry tốt, nhưng Controller bypass nó |
| **DI** | Constructor injection (Lombok) | ⚠️ Quá nhiều deps ở LoginHandler (17) |
| **Testing** | JUnit 5 + MockitoBean + SpringBootTest | ✅ Có test suite |
| **Audit** | `AuditLogService` cross-cutting | ✅ Tốt |
| **Event** | Domain Events + `LoginEventRecorder` | ✅ Tốt |

### 4.3 Tech Stack Constraints

| Component | Version | Constraint |
|-----------|---------|-----------|
| **Kotlin** | 2.x | Phải dùng data class, sealed class |
| **Spring Boot** | 4.x | `@MockitoBean` (không `@MockBean`) |
| **Spring Data JPA** | Latest | Repository interfaces |
| **PostgreSQL** | Latest | Flyway migrations |
| **Redis** | Latest | Cache + rate limit |
| **Jackson** | Latest | JSON serialization |
| **base-core** | 0.0.1-SNAPSHOT | `ApiResponse`, `PageResponse`, shared models |
| **eventsourcing-utils** | 0.0.1-SNAPSHOT | `CommandHandler<C,R>` interface |
| **Gradle** | Latest | Kotlin DSL |

### 4.4 Integration Points

| Integration | Affected Modules | Impact |
|------------|-----------------|--------|
| `LoginHandler` refactor | `CqrsAuthController`, tests, `MfaController` | Tất cả callers cần update |
| CommandBus | `CqrsAuthController`, tất cả Handlers | Controller API không đổi, internal wiring thay đổi |
| RBAC Application layer mới | `RbacControllers.kt`, `RolePermissionController.kt`, `InternalUserController.kt` | Controllers simplified |
| PBAC Application layer mới | `PolicyController.kt`, `PolicyEvaluator.kt` | Controller → UseCase → Repository |
| MFA Strategy | `MfaService.kt`, `MfaController.kt`, `CqrsAuthController.kt` | MfaService → MfaProviderRegistry |
| Captcha fix | `CaptchaController.kt` | Đơn giản, dùng Registry đã có |
