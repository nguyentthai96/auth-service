# Research Brief: ERP IAM System (3 Modules)

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Enterprise IAM — system-admin-service, account-service, auth-service |
| **Ngày tạo** | 2026-08-05 |
| **Input source** | Idea (mô tả tự do từ user + directory reference) |
| **Input content** | Thiết kế và triển khai hệ thống IAM hoàn chỉnh cho ERP enterprise, chia thành 3 microservice |
| **Người yêu cầu** | nguyentthai96 |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
Hệ thống ERP boilerplate hiện có auth-service với authentication cơ bản (login/register, JWT, RBAC/PBAC) nhưng thiếu nhiều tính năng enterprise-grade: MFA, SSO, menu permission, organization management, API partner management, approval workflow. Account-service và system-admin-service chỉ có stub code.

Cần xây dựng IAM hoàn chỉnh để:
- Đáp ứng yêu cầu bảo mật enterprise (MFA, OAuth2/SSO)
- Cung cấp dynamic menu permission cho frontend
- Quản lý cơ cấu tổ chức (department, position)
- Hỗ trợ API partner integration với rate limiting
- Hỗ trợ approval workflow đa bước

### 2.2 Mục tiêu (Objectives)
- [x] O-01: Hệ thống authentication hoàn chỉnh với MFA, SSO, password policy
- [x] O-02: Dynamic menu permission system (tree + button-level) cho frontend
- [x] O-03: Organization management (department, position, user assignment)
- [x] O-04: API partner management với rate limiting và quota
- [x] O-05: Dynamic approval workflow engine
- [x] O-06: User profile management tách biệt khỏi auth data
- [x] O-07: Audit trail cho mọi thao tác admin

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| User lifecycle management (CRUD, lock/unlock, soft-delete) | Business domain logic (booking, payment, orders) |
| Authentication: username/password, OAuth2 SSO, Keycloak optional | Frontend implementation (chỉ design API contract) |
| MFA: OTP SMS/Email, TOTP app-based, CAPTCHA | Email/SMS provider implementation (dùng adapter pattern) |
| RBAC: Role → Group → User hierarchy | Notification service (separate microservice) |
| PBAC: Dynamic policy conditions (ABAC-style) | Payment/billing cho API partners |
| Menu permission: Dynamic tree + button-level | Mobile-specific auth flows (biometric) |
| API Partner: API key, rate limiting, quota | |
| Organization: Department, position, hierarchy | |
| Approval workflow: Dynamic multi-step, conditional routing | |
| Audit logging: All admin actions | |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `Enterprise IAM architecture microservices`
- `RBAC PBAC policy-based access control`
- `Dynamic menu permission system`
- `Spring Security 7 MFA TOTP`
- `API key management rate limiting`

### 3.2 Secondary Keywords
- `Keycloak OAuth2 SSO integration`
- `Organization hierarchy department management`
- `Approval workflow engine state machine`
- `Cerbos OpenFGA authorization engine`
- `Bucket4j Redis distributed rate limiting`

### 3.3 Domain-Specific Terms
- `RBAC`: Role-Based Access Control — phân quyền dựa trên role
- `PBAC/ABAC`: Policy/Attribute-Based Access Control — phân quyền dựa trên điều kiện động
- `ReBAC`: Relationship-Based Access Control — phân quyền dựa trên quan hệ (Google Zanzibar)
- `MFA/2FA`: Multi-Factor Authentication — xác thực nhiều lớp
- `TOTP`: Time-based One-Time Password — mã OTP dựa trên thời gian (RFC 6238)
- `OIDC`: OpenID Connect — protocol xác thực trên OAuth2
- `DPoP`: Demonstrating Proof-of-Possession — token binding mechanism
- `FactorGrantedAuthority`: Spring Security 7 concept cho progressive authorization

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"enterprise IAM system architecture microservices RBAC PBAC best practices"` | Architecture | High |
| 2 | `"Spring Security 7 multi-factor authentication MFA TOTP OTP"` | MFA | High |
| 3 | `"Keycloak 26 Spring Boot 4 integration microservices"` | SSO | High |
| 4 | `"dynamic menu permission system backend frontend button-level access"` | Menu | High |
| 5 | `"API key management rate limiting Bucket4j Spring Boot Redis"` | API Partner | Medium |
| 6 | `"enterprise approval workflow engine dynamic routing state machine"` | Workflow | Medium |
| 7 | `"organization hierarchy department management PostgreSQL recursive query"` | Organization | Medium |
| 8 | `"Cerbos OpenFGA authorization engine microservices Spring Boot"` | Policy Engine | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Service | Module/Package | Status | Key Files |
|---------|---------|---------------|--------|-----------|
| Login/Register | auth-service | `auth.application` | ✅ Có | `AuthService.kt`, `AuthController.kt` |
| JWT Generation/Validation | auth-service | `auth.application` | ✅ Có | `JwtService.kt` |
| Security Config (Filter Chain) | auth-service | `shared.security` | ✅ Có | `SecurityConfig.kt`, `JwtAuthFilter.kt` |
| RBAC Engine (User→Group→Role→Permission) | auth-service | `rbac.application` | ✅ Có | `RbacEngine.kt` |
| RBAC Entities | auth-service | `rbac.adapter.out.persistence` | ✅ Có | `RbacEntities.kt`, `UserEntity.kt` |
| Permission Entities | auth-service | `rbac.adapter.out.persistence` | ✅ Có | `PermissionEntities.kt` |
| PBAC Policy Evaluator | auth-service | `pbac.application` | ✅ Có | `PolicyEvaluator.kt` |
| PBAC Policy Entities | auth-service | `pbac.adapter.out.persistence` | ✅ Có | `PolicyEntities.kt` |
| Domain/Role/Group Management | auth-service | `rbac.adapter.in.web` | ✅ Có | `RbacControllers.kt` |
| Role-Permission Controller | auth-service | `rbac.adapter.in.web` | ✅ Có | `RolePermissionController.kt` |
| Exception Handling | auth-service | `shared.exception` | ✅ Có | `GlobalExceptionHandler.kt`, `AuthExceptions.kt` |
| Session Management (stub) | account-service | `shared.session` | 🔲 Stub | `DefaultSessionManagement.kt` |
| System Admin (stub) | system-admin-service | `shared.session` | 🔲 Stub | `DefaultSessionManagement.kt` |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Evidence |
|---------|---------------|----------|
| **Architecture** | Clean Architecture (Hexagonal): `adapter/in/web`, `adapter/out/persistence`, `application` | auth-service package structure |
| **Entity Base** | `SnowflakePersistentAuditableEntity` (id, createdAt, updatedAt, active) | base-core library |
| **ID Generation** | Snowflake 64-bit Long | base-core `SnowflakeIdGenerator` |
| **Service Layer** | `AbstractCrudService<E>` từ base-core | base-core library |
| **Controller Layer** | `BaseController` từ base-core | base-core library |
| **Response Format** | `ApiResponse<T>` unified format | base-core `base-web-starter` |
| **Exception Handling** | `BaseControllerAdvice` + custom exceptions | `GlobalExceptionHandler.kt` |
| **Logging** | `@SystemLog` AOP + `HttpLoggingFilter` | base-core `common-log` |
| **DTO Mapping** | Extension functions `toResponse()` | auth-service Kotlin convention |
| **Validation** | Jakarta `@Valid` + Bean Validation | `spring-boot-starter-validation` |
| **Password** | `PasswordEncoder` (Argon2 ready, BouncyCastle) | `build.gradle.kts` test dependency |
| **DB Migration** | Flyway | `build.gradle.kts` dependency |

### 4.3 Tech Stack Constraints

| Component | Version/Technology |
|-----------|-------------------|
| Language | Kotlin 2.4.10 |
| JDK | 25 |
| Framework | Spring Boot 4.1.0 (Spring Framework 7, Spring Security 7) |
| Build | Gradle 8.x, Convention Plugins (`ntt.spring-app-conventions`) |
| Database | PostgreSQL 14+ |
| ORM | Spring Data JPA + Hibernate |
| Migration | Flyway |
| Security | Spring Security + JJWT (HMAC-SHA256) |
| Messaging | Spring Kafka (account-service) |
| Modulith | Spring Modulith (account-service, system-admin-service) |
| Testing | JUnit 5 + Testcontainers + ArchUnit + H2 |
| Base Library | base-core platform (base-web-starter, base-data-starter, common-log) |
| Platform BOM | `com.ntt:platform:0.0.1-SNAPSHOT` |

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `SnowflakePersistentAuditableEntity` | Base Class | base-core/base-model | Mọi entity mới phải extend |
| `AbstractCrudService<E>` | Base Class | base-core | Service layer chuẩn |
| `BaseController` | Base Class | base-core/base-web-starter | Controller layer chuẩn |
| `ApiResponse<T>` | Response Format | base-core/base-web-starter | Unified API response |
| `BaseControllerAdvice` | Exception Handling | base-core | Global exception handler |
| `@SystemLog` | AOP Annotation | common-log | Logging annotation |
| `JwtService` | Service | auth-service | JWT generation/validation |
| `RbacEngine` | Service | auth-service | RBAC chain evaluation |
| `PolicyEvaluator` | Service | auth-service | PBAC condition evaluation |
| `SecurityConfig` | Config | auth-service | Spring Security filter chain |
| PostgreSQL | Database | All services | Separate DB per service |
| Redis | Cache | Planned | Permission cache, rate limiting |
| Kafka | Messaging | account-service, system-admin-service | Inter-service events |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Spring Security 7 có native MFA support không? Cách integrate? → **Có**, dùng `@EnableMultiFactorAuthentication` + `FactorGrantedAuthority`
- [x] Q2: Keycloak 26 integrate với Spring Boot 4.x như thế nào? → Dùng `spring-boot-starter-oauth2-resource-server`, không cần adapter
- [x] Q3: Có open source nào cho menu permission system không? → **Không** có solution phù hợp, cần custom build
- [x] Q4: Bucket4j + Redis có production-ready cho distributed rate limiting không? → **Có**, dùng ProxyManager + Redisson
- [x] Q5: Nên dùng state machine hay workflow engine cho approval workflow? → Custom state machine (đơn giản hơn Camunda/Temporal cho use case này)
- [x] Q6: Cerbos/OpenFGA có cần thiết không khi đã có PolicyEvaluator? → **Không**, PolicyEvaluator hiện tại đủ dùng
- [x] Q7: PostgreSQL recursive CTE hay ltree cho organization hierarchy? → Recursive CTE (đơn giản, đủ performance cho ≤10 levels)

### 5.2 Assumptions cần verify
- [x] A1: base-core chưa có TreeEntity base class → **Confirmed**, cần tạo mới
- [x] A2: Spring Security 7 `FactorGrantedAuthority` available trong Spring Boot 4.1.0 → **Confirmed** qua search
- [x] A3: Bucket4j có Spring Boot starter → **Confirmed**, có integration module
- [x] A4: account-service và system-admin-service chỉ có stub → **Confirmed** qua codebase scan

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ sources cho mọi domain (auth, menu, org, API, workflow) | ≥ 10 unique sources |
| Open source options | Đánh giá các projects liên quan | ≥ 5 repos evaluated |
| Gap analysis | Xác định tất cả gaps giữa current và target system | All critical gaps identified + severity |
| Business analysis | Mọi use cases documented với flows | ≥ 30 use cases across 3 modules |
| Technical spec | ERD, API spec, caching, security — agent-ready | 3 ERDs + 80+ API endpoints |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
