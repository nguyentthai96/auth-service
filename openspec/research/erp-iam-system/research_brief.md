# Research Brief: ERP IAM System

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Enterprise IAM — system-admin-service, account-service, auth-service |
| **Ngày tạo** | 2025-07-15 |
| **Input source** | Idea (mô tả tự do từ user + directory reference) |
| **Input content** | Thiết kế và triển khai hệ thống IAM hoàn chỉnh cho ERP enterprise, chia thành 3 microservice: auth-service (xác thực + phân quyền), account-service (profile + preferences), system-admin-service (menu, org, API partner, workflow, audit) |
| **Người yêu cầu** | nguyentthai96 |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
Hệ thống ERP boilerplate có 3 microservice IAM đã implement ở nhiều mức độ khác nhau:
- **auth-service**: Có authentication (login/register, JWT RS256, RBAC/PBAC), MFA (TOTP + OTP), SSO adapter, session management, E2EE (Google Tink), anonymous sessions, password policy, Kafka events. Foundation mạnh.
- **account-service**: Có profile CRUD, device management, session management (Redis-backed), lifecycle (deactivate, GDPR export/deletion), preference management. Kafka listener cho profile events.
- **system-admin-service**: Có menu permission (tree CRUD, caching), API partner management (key generation, usage tracking), organization (department tree, positions), workflow (engine, escalation scheduler), audit (AOP aspect, audit logs), domain/tenant config, feature flags. TreeEntity base class đã có.

Cần consolidate và enhance IAM thành enterprise-grade hoàn chỉnh:
- Enhance MFA flow (progressive auth, trusted devices, recovery codes)
- Strengthen SSO/OAuth2 integration (Keycloak optional IdP)
- Add Bucket4j-based rate limiting cho API partner
- Refine approval workflow engine
- Comprehensive audit trail

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
| Login/Register | auth-service | `auth.application` | ✅ Có | `AuthService.kt`, `LoginHandler.kt`, `RegisterHandler.kt` |
| JWT Generation/Validation (RS256) | auth-service | `auth.application` | ✅ Có | `JwtService.kt`, `TokenGenerator.kt` |
| Security Config (Filter Chain) | auth-service | `shared.security` | ✅ Có | `SecurityConfig.kt`, `JwtAuthFilter.kt` |
| RBAC Engine (User→Group→Role→Permission) | auth-service | `rbac.application` | ✅ Có | `RbacEngine.kt` |
| RBAC Entities | auth-service | `rbac.adapter.out.persistence` | ✅ Có | `RbacEntities.kt`, `UserEntity.kt` |
| Permission Entities | auth-service | `rbac.adapter.out.persistence` | ✅ Có | `PermissionEntities.kt` |
| PBAC Policy Evaluator | auth-service | `pbac.application` | ✅ Có | `PolicyEvaluator.kt` |
| PBAC Policy Entities | auth-service | `pbac.adapter.out.persistence` | ✅ Có | `PolicyEntities.kt` |
| Domain/Role/Group Management | auth-service | `rbac.adapter.in.web` | ✅ Có | `RbacControllers.kt` |
| Role-Permission Controller | auth-service | `rbac.adapter.in.web` | ✅ Có | `RolePermissionController.kt` |
| Exception Handling | auth-service | `shared.exception` | ✅ Có | `GlobalExceptionHandler.kt`, `AuthExceptions.kt`, `AuthCoreExceptions.kt` |
| MFA Service (TOTP + OTP) | auth-service | `auth.application` | ✅ Có | `MfaRateLimitService.kt`, `TotpService.kt`, `OtpService.kt` |
| SSO Adapter | auth-service | `auth.application` | ✅ Có | `SsoAdapter.kt` |
| Session Management | auth-service | `auth.application` | ✅ Có | `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionPromotionService.kt` |
| E2EE Cipher | auth-service | `auth.application.cipher` | ✅ Có | `DecryptionVaultService.kt`, `CipherVersionNegotiator.kt`, `X25519KeyExchangeServiceImpl.kt` |
| Anonymous Session | auth-service | `auth.application.command` | ✅ Có | `AnonymousSessionHandler.kt`, `CreateAnonymousSessionCommand.kt` |
| Password Policy | auth-service | `auth.application` | ✅ Có | `PasswordPolicyService.kt` (via Passay) |
| Two-Tier Cache | auth-service | `shared.cache` | ✅ Có | `AbstractTwoTierCache.kt` |
| Idempotency Filter | auth-service | `shared.filter` | ✅ Có | `IdempotencyFilter.kt` |
| Audit Log Service | auth-service | `shared.audit` | ✅ Có | `AuditLogService.kt` |
| i18n (Database-backed) | auth-service | `shared.i18n` | ✅ Có | `DatabaseMessageSource.kt`, `I18nMessageEntity.kt` |
| Kafka Config | auth-service | `shared.config` | ✅ Có | `KafkaConfig.kt` |
| CQRS Commands/Queries | auth-service | `auth.application.command/query` | ✅ Có | `LoginCommand.kt`, `BuildAuthResponseQuery.kt` |
| Domain Events | auth-service | `auth.application` | ✅ Có | `AuthDomainEvents.kt`, `RateLimitExceededEvent.kt`, `NewDeviceLoginEvent.kt` |
| Profile CRUD | account-service | `profile` | ✅ Có | `ProfileController.kt`, `ProfileService.kt`, `UserProfileEntity.kt`, `UserContactEntity.kt` |
| Profile Kafka Listener | account-service | `profile` | ✅ Có | `ProfileKafkaListener.kt` |
| Device Management | account-service | `device` | ✅ Có | `DeviceController.kt`, `DeviceService.kt`, `UserDeviceEntity.kt` |
| Preference Management | account-service | `preference` | ✅ Có | `PreferenceController.kt`, `PreferenceService.kt`, `UserPreferenceEntity.kt` |
| Account Lifecycle | account-service | `lifecycle` | ✅ Có | `AccountLifecycleController.kt`, `AccountLifecycleService.kt`, `DataExportService.kt` |
| Account Session | account-service | `session` | ✅ Có | `SessionController.kt`, `SessionService.kt`, `SessionRedisAdapter.kt`, `ActiveSessionEntity.kt` |
| Menu Permission | system-admin-service | `menu` | ✅ Có | `MenuPermissionService.kt` |
| API Partner Management | system-admin-service | `apipartner` | ✅ Có | `ApiPartnerService.kt`, `ApiUsageController.kt` |
| Organization (Department + Position) | system-admin-service | `organization` | ✅ Có | `OrganizationService.kt`, `PositionService.kt`, `DepartmentEntity.kt`, `PositionEntity.kt`, `UserPositionEntity.kt`, `DepartmentController.kt`, `PositionController.kt` |
| Workflow Engine | system-admin-service | `workflow` | ✅ Có | `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowEscalationScheduler.kt`, `WorkflowEntities.kt`, `WorkflowController.kt` |
| Audit (AOP + Service) | system-admin-service | `audit` | ✅ Có | `AuditAspect.kt`, `AuditService.kt`, `AuditLogEntity.kt`, `AuditController.kt` |
| Domain/Tenant Config | system-admin-service | `config` | ✅ Có | `DomainConfigService.kt`, `FeatureFlagService.kt`, `FeatureFlagEntity.kt` |
| TreeEntity Base Class | system-admin-service | `shared.persistence` | ✅ Có | `TreeEntity.kt` |
| TreeBuilder Utility | system-admin-service | `shared.util` | ✅ Có | `TreeBuilder.kt` |
| Exception Handling | system-admin-service | `shared.exception` | ✅ Có | `SysAdminControllerAdvice.kt`, `SysAdminExceptions.kt`, `SysAdminErrorCode.kt` |
| Exception Handling | account-service | `shared.exception` | ✅ Có | `AccountControllerAdvice.kt`, `AccountExceptions.kt`, `AccountErrorCode.kt` |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Evidence |
|---------|---------------|----------|
| **Architecture** | Clean Architecture (Hexagonal): `adapter/in/web`, `adapter/out/persistence`, `application`, `domain/model` | All 3 services follow same structure |
| **Entity Base** | `SnowflakePersistentAuditableEntity` (id, createdAt, updatedAt, active) | base-core library |
| **Tree Entity** | `TreeEntity` (parentId, sortOrder, level, children) | `system-admin-service/shared/persistence/TreeEntity.kt` |
| **Tree Builder** | `TreeBuilder` utility for tree construction | `system-admin-service/shared/util/TreeBuilder.kt` |
| **ID Generation** | Snowflake 64-bit Long | base-core `SnowflakeIdGenerator` |
| **Response Format** | `ApiResponse<T>` unified format | base-core `base-web-starter` |
| **Exception Handling** | Service-specific `ControllerAdvice` + error codes | Each service has own `ControllerAdvice` + `ErrorCode` enum |
| **Logging** | AOP-based logging + `HttpLoggingFilter` | base-core `common-log` |
| **DTO Mapping** | Extension functions `toResponse()`, separate `Dtos.kt` files | Kotlin convention across services |
| **Validation** | Jakarta `@Valid` + Bean Validation | `spring-boot-starter-validation` |
| **Password** | `PasswordEncoder` (Argon2 via BouncyCastle) | `build.gradle.kts` test dependency |
| **DB Migration** | Flyway | `build.gradle.kts` dependency |
| **CQRS** | Command/Query handlers with handler pattern | `LoginCommand.kt`/`LoginHandler.kt`, `BuildAuthResponseQuery.kt`/`BuildAuthResponseHandler.kt` |
| **Event Sourcing** | `eventsourcing-utils` dependency | `build.gradle.kts` |
| **Domain Events** | Spring `ApplicationEvent` + Kafka | `AuthDomainEvents.kt`, `NewDeviceLoginEvent.kt` |
| **Caching** | Two-tier: Caffeine L1 + Redis L2 (`AbstractTwoTierCache`) | `RedisConfig.kt`, `AbstractTwoTierCache.kt` |
| **i18n** | Database-backed MessageSource | `DatabaseMessageSource.kt` |
| **Idempotency** | Filter-based idempotency | `IdempotencyFilter.kt` |
| **Versioned Entity** | Optimistic locking | `VersionedAuditableEntity.kt` |

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
| Cache | Caffeine (L1) + Redis (L2) via `AbstractTwoTierCache` |
| Messaging | Spring Kafka (runtime dependency) |
| E2EE | Google Tink + AWS KMS |
| MFA | dev.samstevens.totp + Passay password policy |
| OAuth2 | spring-boot-starter-oauth2-client + oauth2-resource-server |
| Testing | JUnit 5 + ArchUnit + Mockito-Kotlin + H2 |
| Base Library | base-core platform (base-web-starter, base-data-starter, base-security-starter, base-observability-starter, common-log) |
| Platform BOM | `com.ntt:platform:0.0.1-SNAPSHOT` |

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `SnowflakePersistentAuditableEntity` | Base Class | base-core/base-model | Mọi entity mới phải extend |
| `TreeEntity` | Base Class | system-admin-service/shared/persistence | Menu items, departments extend |
| `TreeBuilder` | Utility | system-admin-service/shared/util | Build tree from flat list |
| `ApiResponse<T>` | Response Format | base-core/base-web-starter | Unified API response |
| `GlobalExceptionHandler` | Exception Handling | auth-service shared/exception | Auth service exception handler |
| `SysAdminControllerAdvice` | Exception Handling | system-admin-service shared/exception | System admin exception handler |
| `AccountControllerAdvice` | Exception Handling | account-service shared/exception | Account service exception handler |
| `JwtService` | Service | auth/application | JWT generation/validation (RS256) |
| `RbacEngine` | Service | rbac/application | RBAC chain evaluation |
| `PolicyEvaluator` | Service | pbac/application | PBAC condition evaluation |
| `SecurityConfig` | Config | shared/config | Spring Security filter chain |
| `TotpService` / `OtpService` | Service | auth/application | MFA verification |
| `MfaRateLimitService` | Service | auth/application | MFA brute-force protection |
| `SsoAdapter` | Service | auth/application | OAuth2 SSO integration |
| `LoginSessionService` | Service | auth/application | Session management |
| `SessionPromotionService` | Service | auth/application | Anonymous → authenticated promotion |
| `AbstractTwoTierCache` | Infrastructure | shared/cache | Caffeine L1 + Redis L2 |
| `DatabaseMessageSource` | Infrastructure | shared/i18n | i18n from database |
| PostgreSQL | Database | All services | Separate DB per service |
| Redis | Cache/State | auth-service, account-service | Permission cache, OTP, sessions, rate limiting |
| Kafka | Messaging | All services | Inter-service events (runtime) |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Spring Security 7 có native MFA support không? → **Có**, dùng `FactorGrantedAuthority`
- [x] Q2: Keycloak 26 integrate với Spring Boot 4.x như thế nào? → `spring-boot-starter-oauth2-resource-server`
- [x] Q3: Có open source nào cho menu permission system không? → **Không** phù hợp, cần custom build (đã có trong system-admin-service)
- [x] Q4: Bucket4j + Redis production-ready cho distributed rate limiting? → **Có**
- [x] Q5: State machine hay workflow engine cho approval workflow? → Custom state machine (đã có `WorkflowEngine.kt`)
- [x] Q6: Cerbos/OpenFGA cần thiết khi đã có PolicyEvaluator? → **Không**
- [x] Q7: PostgreSQL recursive CTE hay ltree cho organization hierarchy? → Recursive CTE (đã implement `TreeEntity` + `TreeBuilder`)

### 5.2 Assumptions cần verify
- [x] A1: TreeEntity base class → **Confirmed EXISTS** tại `system-admin-service/shared/persistence/TreeEntity.kt`
- [x] A2: Spring Security 7 `FactorGrantedAuthority` available → **Confirmed**
- [x] A3: Bucket4j có Spring Boot starter → **Confirmed**
- [x] A4: account-service và system-admin-service có substantial code (NOT stubs) → **Confirmed**: account-service có profile, device, preference, lifecycle, session modules; system-admin-service có menu, apipartner, organization, workflow, audit, config modules

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ sources cho mọi domain (auth, menu, org, API, workflow) | ≥ 10 unique sources |
| Open source options | Đánh giá các projects liên quan | ≥ 5 repos evaluated |
| Gap analysis | Xác định tất cả gaps giữa current và target system | All critical gaps identified |
| Business analysis | Mọi use cases documented với flows | ≥ 30 use cases across 3 modules |
| Technical spec | ERD, API spec, caching, security — agent-ready | 3 ERDs + 50+ API endpoints |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
