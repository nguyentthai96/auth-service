# Research Brief: ERP IAM System

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Enterprise IAM — system-admin-service, account-service, auth-service |
| **Ngày tạo** | 2026-08-05 |
| **Input source** | Idea (mô tả tự do từ user + directory reference) |
| **Input content** | Thiết kế và triển khai hệ thống IAM hoàn chỉnh cho ERP enterprise, chia thành 3 microservice: auth-service (xác thực + phân quyền), account-service (profile + preferences), system-admin-service (menu, org, API partner, workflow, audit) |
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
| MFA Service | auth-service | `auth.application` | ✅ Có | `MfaService.kt`, `TotpService.kt`, `OtpService.kt` |
| SSO Adapter | auth-service | `auth.application` | ✅ Có | `SsoAdapter.kt`, `SsoController.kt` |
| Session Management | auth-service | `auth.application` | ✅ Có | `LoginSessionService.kt`, `SessionPolicyService.kt` |
| E2EE Cipher | auth-service | `auth.application.cipher` | ✅ Có | `DecryptionVaultService.kt`, `CipherVersionNegotiator.kt` |
| Anonymous Session | auth-service | `auth.application` | ✅ Có | `AnonymousSessionHandler.kt` |
| Password Policy | auth-service | `auth.application` | ✅ Có | `PasswordPolicyService.kt`, `PasswordHistoryEntity.kt`, `PasswordPolicyEntity.kt` |
| Account Lifecycle | account-service | `lifecycle` | ✅ Có | `AccountLifecycleController.kt`, `AccountLifecycleService.kt`, `DataExportService.kt` |
| Session Management | account-service | `session` | ✅ Có | `SessionController.kt`, `SessionService.kt`, `SessionRedisAdapter.kt`, `ActiveSessionEntity.kt` |
| Profile Event Listener | account-service | `profile` | ⚠️ Partial | `ProfileKafkaListener.kt` (listener only, no CRUD yet) |
| Menu Permission | system-admin-service | `menu` | ✅ Có | `MenuController.kt`, `MenuPermissionService.kt`, `MenuEntities.kt`, `MenuPermissionCacheAdapter.kt` |
| API Partner Management | system-admin-service | `apipartner` | ✅ Có | `ApiPartnerController.kt`, `ApiKeyService.kt`, `ApiPartnerService.kt`, `ApiUsageService.kt` |
| Domain/Tenant Config | system-admin-service | `tenant` | ✅ Có | `DomainConfigController.kt`, `DomainConfigService.kt`, `DomainConfigEntity.kt` |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Evidence |
|---------|---------------|----------|
| **Architecture** | Clean Architecture (Hexagonal): `adapter/in/web`, `adapter/out/persistence`, `application` | auth-service package structure |
| **Entity Base** | `SnowflakePersistentAuditableEntity` (id, createdAt, updatedAt, active) | base-core library |
| **ID Generation** | Snowflake 64-bit Long | base-core `SnowflakeIdGenerator` |
| **Response Format** | `ApiResponse<T>` unified format | base-core `base-web-starter` |
| **Exception Handling** | `GlobalExceptionHandler` + custom exceptions | `GlobalExceptionHandler.kt` |
| **Logging** | AOP-based logging + `HttpLoggingFilter` | base-core `common-log` |
| **DTO Mapping** | Extension functions `toResponse()` | auth-service Kotlin convention |
| **Validation** | Jakarta `@Valid` + Bean Validation | `spring-boot-starter-validation` |
| **Password** | `PasswordEncoder` (Argon2 ready, BouncyCastle) | `build.gradle.kts` test dependency |
| **DB Migration** | Flyway | `build.gradle.kts` dependency |
| **CQRS** | Command/Query handlers (optional flag) | `CqrsAuthController.kt` |
| **Event Sourcing** | `eventsourcing-utils` dependency | `build.gradle.kts` |
| **Caching** | Caffeine L1 + Redis L2 | `RedisConfig.kt` + application.yml |
| **i18n** | Database-backed MessageSource | `DatabaseMessageSource.kt` |

### 4.3 Tech Stack Constraints

| Component | Version/Technology |
|-----------|-------------------|
| Language | Kotlin 2.4.x |
| JDK | 25 |
| Framework | Spring Boot 4.1.0 (Spring Framework 7, Spring Security 7) |
| Build | Gradle 8.x, Convention Plugins (`ntt.spring-app-conventions`) |
| Database | PostgreSQL 17+ |
| ORM | Spring Data JPA + Hibernate |
| Migration | Flyway |
| Security | Spring Security + JJWT (RS256 support) |
| Cache | Caffeine (L1) + Redis (L2) |
| Messaging | Spring Kafka (compileOnly) |
| E2EE | Google Tink + AWS KMS |
| MFA | dev.samstevens.totp + Passay password policy |
| Testing | JUnit 5 + ArchUnit + Mockito-Kotlin + H2 |
| Base Library | base-core platform (base-web-starter, base-data-starter, common-log) |
| Platform BOM | `com.ntt:platform:0.0.1-SNAPSHOT` |

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `SnowflakePersistentAuditableEntity` | Base Class | base-core/base-model | Mọi entity mới phải extend |
| `ApiResponse<T>` | Response Format | base-core/base-web-starter | Unified API response |
| `GlobalExceptionHandler` | Exception Handling | shared/exception | Global exception handler |
| `JwtService` | Service | auth/application | JWT generation/validation |
| `RbacEngine` | Service | rbac/application | RBAC chain evaluation |
| `PolicyEvaluator` | Service | pbac/application | PBAC condition evaluation |
| `SecurityConfig` | Config | shared/config | Spring Security filter chain |
| `MfaService` / `TotpService` | Service | auth/application | MFA verification |
| `SsoAdapter` | Service | auth/application | OAuth2 SSO integration |
| `LoginSessionService` | Service | auth/application | Session management |
| PostgreSQL | Database | All services | Separate DB per service |
| Redis | Cache/State | auth-service | Permission cache, OTP, rate limiting |
| Kafka | Messaging | All services | Inter-service events (compileOnly) |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Spring Security 7 có native MFA support không? → **Có**, dùng `FactorGrantedAuthority`
- [x] Q2: Keycloak 26 integrate với Spring Boot 4.x như thế nào? → `spring-boot-starter-oauth2-resource-server`
- [x] Q3: Có open source nào cho menu permission system không? → **Không** phù hợp, cần custom build
- [x] Q4: Bucket4j + Redis production-ready cho distributed rate limiting? → **Có**
- [x] Q5: State machine hay workflow engine cho approval workflow? → Custom state machine
- [x] Q6: Cerbos/OpenFGA cần thiết khi đã có PolicyEvaluator? → **Không**
- [x] Q7: PostgreSQL recursive CTE hay ltree cho organization hierarchy? → Recursive CTE

### 5.2 Assumptions cần verify
- [x] A1: base-core chưa có TreeEntity base class → **Confirmed**
- [x] A2: Spring Security 7 `FactorGrantedAuthority` available → **Confirmed**
- [x] A3: Bucket4j có Spring Boot starter → **Confirmed**
- [x] A4: account-service và system-admin-service có code → **Updated**: account-service đã có lifecycle + session module; system-admin-service đã có menu + apipartner + tenant module. Không chỉ là stub.

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ sources cho mọi domain (auth, menu, org, API, workflow) | ≥ 10 unique sources |
| Open source options | Đánh giá các projects liên quan | ≥ 5 repos evaluated |
| Gap analysis | Xác định tất cả gaps giữa current và target system | All critical gaps identified |
| Business analysis | Mọi use cases documented với flows | ≥ 30 use cases across 3 modules |
| Technical spec | ERD, API spec, caching, security — agent-ready | 3 ERDs + 80+ API endpoints |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
