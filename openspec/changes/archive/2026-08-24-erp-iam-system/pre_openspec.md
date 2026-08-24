# Pre-OpenSpec: erp-iam-system

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (File: `openspec/research/erp-iam-system/business_analysis.md`)
> **Classification Evidence**: Keywords `auth, mfa, sso, rbac, pbac` → Module: `com.ntt.authservice` → File: `AuthService.kt`, `MfaService.kt`, `RbacEngine.kt`. Keywords `menu, organization, workflow, apipartner, audit` → Module: `com.ntt.sysadminservice` → File: `MenuPermissionService.kt`, `OrganizationService.kt`, `WorkflowEngine.kt`, `ApiPartnerService.kt`, `AuditService.kt`. Keywords `profile, device, preference` → Module: `com.ntt.accountservice` → File: `ProfileService.kt`, `DeviceService.kt`, `PreferenceService.kt`. **All 3 services confirmed existing with substantial implementations (NOT stubs).**
> **Archive**: `openspec/changes/archive/2026-08-24-erp-iam-system/pre_openspec.md`
> **Previous Version**: 2026-08-24 (archived, classified as NEWBUILD — [CHANGED] reclassified to EXTEND)
> **Quality Score**: 90/100
> **Brainstorm**: `brainstorm_notes.md` — Selected Direction: "Event-Driven & Redis-Backed Decentralized Authorization"

## 📋 Feature Summary

Hệ thống Identity & Access Management hoàn chỉnh cho ERP enterprise, chia thành 3 microservices: `auth-service` (xác thực, MFA, SSO, JWT, Password Policy, E2EE — foundation mạnh, 29+ application files), `account-service` (profile, device, preferences — 16 Kotlin files, 883 LOC), và `system-admin-service` (menu permission, organization, API partner, approval workflow, audit, config, feature flags — 27 Kotlin files, 1967 LOC). **[CHANGED]** Cả 3 services đều có implementations thực tế (NOT stubs như archived version). Kiến trúc Event-Driven với Redis làm backbone cho decentralized authorization và rate limiting.

| Metric | Giá trị |
|--------|---------|
| Số FR | 21 (URD: 16, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 2, 🟢: 1) |
| Open Questions | 2 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **End User**: Người dùng hệ thống ERP — đăng nhập, MFA, quản lý profile, sử dụng menu, submit approval request.
- **System Administrator**: Quản trị viên — cấu hình menu, tổ chức, API partner, workflow, audit review.
- **Domain Admin**: Quản trị viên domain/tenant — role assignment, password policy, scoped configuration.
- **API Partner**: Hệ thống bên ngoài tích hợp qua API key — rate limited, quota managed.
- **Approver**: Người phê duyệt workflow — approve/reject/delegate steps.
- **System**: Hệ thống tự động — scheduled jobs, event processing, cache invalidation.

## 2. Functional Requirements

### FR-001: Quản lý Multi-Factor Authentication [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ enable/disable MFA per user với các phương thức OTP (SMS, Email), TOTP (app-based), và CAPTCHA khi đăng nhập.
- **Validation**: OTP expires sau 5 phút, tối đa 3 lần sai → lock 30 phút; TOTP window ±1 step (30s); recovery codes single-use.
- **Existing**: `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `MfaController.kt`, `MfaRateLimitService.kt` — foundation đã có.
- **Impact**: [MODIFY] `MfaService.kt` (auth-service) — enhance progressive flow, add recovery codes management.

### FR-002: Progressive MFA Login Flow [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ progressive authentication: password verify → partial JWT → MFA verify → full JWT.
- **Validation**: Partial token chỉ cho phép `/verify-2fa`; TTL partial token = 5 phút; trusted device skip MFA.
- **Existing**: `LoginHandler.kt`, `AuthService.kt`, `AuthController.kt`, `CqrsAuthController.kt` — login flow đã có.
- **Impact**: [MODIFY] `LoginHandler.kt`, `SessionPromotionService.kt` (auth-service) — enhance progressive auth flow, trusted device integration.

### FR-003: Tích Hợp SSO/OAuth2 [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ OAuth2/OIDC login qua external IdP (Keycloak, Google, Microsoft), auto-provision SSO user, link/unlink external accounts.
- **Validation**: SSO token exchange validated; auto-provision chỉ khi domain cho phép; unlink phải giữ ít nhất 1 identity.
- **Existing**: `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`, `SsoProviderClient.kt` — SSO foundation đã có.
- **Impact**: [MODIFY] `SsoAdapter.kt` (auth-service) — enhance provider configuration, account linking, auto-provisioning.

### FR-004: Chính Sách Mật Khẩu [URD]
- **Actor**: Domain Admin
- **Action**: Hệ thống phải hỗ trợ configurable password rules per domain (min length, uppercase, special chars, history check, expiration).
- **Validation**: Password policy configurable per domain; force reset sau N ngày; không cho dùng lại N mật khẩu gần nhất.
- **Existing**: `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` — foundation đã có trên auth-service.
- **Impact**: [REUSE] Existing implementation — minor enhancements for domain-scoped config.

### FR-005: Quản Lý User Profile [URD]
- **Actor**: End User, System Administrator
- **Action**: Hệ thống phải hỗ trợ separate user profile management (CRUD, change email/phone with verification, avatar upload).
- **Validation**: Email/phone change yêu cầu verification; avatar size limit; profile tách biệt khỏi auth data.
- **Existing**: [CHANGED] `ProfileService.kt`, `ProfileController.kt`, `UserProfileEntity.kt`, `UserContactEntity.kt`, `ProfileDtos.kt`, `ProfileKafkaListener.kt` — **account-service có implementation.**
- **Impact**: [MODIFY] `ProfileService.kt` (account-service) — enhance verification flow, avatar upload.

### FR-006: Quản Lý Preferences [URD]
- **Actor**: End User
- **Action**: Hệ thống phải quản lý user preferences (notification settings, UI theme, language, timezone).
- **Validation**: Preference format validation per category; default values per domain.
- **Existing**: [CHANGED] `PreferenceService.kt`, `PreferenceController.kt`, `UserPreferenceEntity.kt` — **account-service có implementation.**
- **Impact**: [MODIFY] `PreferenceService.kt` (account-service) — enhance preference categories, default values.

### FR-007: Quản Lý Thiết Bị [URD]
- **Actor**: End User
- **Action**: Hệ thống phải track và manage user devices (list active, trust device for MFA skip, revoke device). Device fingerprint unique per user.
- **Existing**: [CHANGED] `DeviceService.kt`, `DeviceController.kt`, `UserDeviceEntity.kt`, `DeviceDtos.kt` — **account-service có implementation.**
- **Impact**: [MODIFY] `DeviceService.kt` (account-service) — enhance trusted device management, integrate with auth-service MFA skip.

### FR-008: Quản lý Session Nâng Cao [URD]
- **Actor**: End User, System Administrator
- **Action**: Hệ thống phải track active sessions, cấu hình max concurrent sessions per user, cho phép terminate specific session.
- **Validation**: Auto-expire session khi inactive; oldest session terminated khi vượt max.
- **Existing**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt`, `LoginSessionEntity.kt` — foundation đã có trên auth-service.
- **Impact**: [MODIFY] `LoginSessionService.kt`, `SessionPolicyService.kt` (auth-service) — enhance concurrent session management.

### FR-009: Account Lifecycle (GDPR) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép deactivate account, request deletion (GDPR), export personal data.
- **Validation**: Deletion request có grace period; export format JSON; immutable audit trail.
- **Existing**: `AccountLifecycleService.kt`, `AccountLifecycleController.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt` — đã có trên auth-service.
- **Impact**: [REUSE] Existing implementation — minor enhancements for data export format.

### FR-010: Phân Quyền Menu Động [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cung cấp cây menu dynamic (DIRECTORY, MENU, BUTTON, API types) với role-based permission + user override. Frontend nhận menu tree filtered per user.
- **Validation**: User override > role permission; BUTTON type invisible trong navigation; cache Redis TTL 5 phút; circular reference detection; menu code unique per domain.
- **Existing**: [CHANGED] `MenuPermissionService.kt` — **system-admin-service có implementation.** `TreeEntity.kt` (base class), `TreeBuilder.kt` (utility) confirmed.
- **Impact**: [MODIFY] `MenuPermissionService.kt` (system-admin-service) — enhance button-level permission, add role-menu assignment, user override.

### FR-011: Quản lý Cơ Cấu Tổ Chức [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải quản lý department tree (recursive hierarchy, max 10 levels) và position, phân bổ user vào vị trí.
- **Validation**: Department tree acyclic; position unique per department; transfer user between departments.
- **Existing**: [CHANGED] `OrganizationService.kt`, `PositionService.kt`, `DepartmentEntity.kt`, `PositionEntity.kt`, `UserPositionEntity.kt`, `DepartmentController.kt`, `PositionController.kt` — **system-admin-service có implementation.**
- **Impact**: [MODIFY] `OrganizationService.kt`, `PositionService.kt` (system-admin-service) — enhance user transfer, org chart query.

### FR-012: Quản lý API Partner [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải hỗ trợ partner onboarding, API key lifecycle (generate, rotate, revoke), rate limiting (Bucket4j + Redis), quota tracking.
- **Validation**: API key show-once (Stripe pattern); key format `ntt_pk_`/`ntt_sk_` + SHA-256 hash; IP whitelist; max keys per partner.
- **Existing**: [CHANGED] `ApiPartnerService.kt`, `ApiUsageController.kt` — **system-admin-service có implementation.**
- **Impact**: [MODIFY] `ApiPartnerService.kt` (system-admin-service) — add Bucket4j rate limiting, key lifecycle, IP whitelist.

### FR-013: Dynamic Approval Workflow [URD]
- **Actor**: System Administrator, Approver
- **Action**: Hệ thống phải cho phép define workflow (multi-step, conditional routing), submit entity for approval, approve/reject/delegate, auto-escalation on timeout.
- **Validation**: Workflow version immutable; state machine: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED.
- **Existing**: [CHANGED] `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowEscalationScheduler.kt`, `WorkflowEntities.kt`, `WorkflowController.kt` — **system-admin-service có implementation.**
- **Impact**: [MODIFY] `WorkflowEngine.kt`, `WorkflowService.kt` (system-admin-service) — enhance conditional routing, delegation, escalation logic.

### FR-014: Quản lý Cấu Hình Hệ Thống [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu trữ system configs (STRING, JSON, NUMBER types) và feature flags per domain.
- **Validation**: Config versioning; audit trail on changes.
- **Existing**: [CHANGED] `DomainConfigService.kt`, `FeatureFlagService.kt`, `FeatureFlagEntity.kt` — **system-admin-service có implementation.**
- **Impact**: [MODIFY] `DomainConfigService.kt`, `FeatureFlagService.kt` (system-admin-service) — enhance config types, versioning.

### FR-015: Audit Trail Toàn Diện [URD]
- **Actor**: System
- **Action**: Hệ thống phải ghi nhận toàn bộ thao tác admin (create/update/delete) vào immutable audit log với old_value/new_value JSONB.
- **Validation**: Không cho UPDATE/DELETE trên audit_logs; mask sensitive data; search + export support.
- **Existing**: [CHANGED] auth-service: `AuditLogService.kt`; system-admin-service: `AuditAspect.kt`, `AuditService.kt`, `AuditLogEntity.kt`, `AuditController.kt` — **cả 2 services đều có implementation.**
- **Impact**: [MODIFY] `AuditService.kt`, `AuditAspect.kt` (system-admin-service), `AuditLogService.kt` (auth-service) — enhance immutability constraints, export, sensitive data masking.

### FR-016: Domain/Tenant Config [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu cấu hình nâng cao per domain (branding, login page config, session policy).
- **Validation**: Tách biệt dữ liệu theo domain; cache config.
- **Existing**: `DomainEntity.kt` (rbac module), `DomainLookupService.kt` (auth-service); `DomainConfigService.kt` (system-admin-service) — domain foundation đã có.
- **Impact**: [MODIFY] `DomainLookupService.kt` (auth-service), `DomainConfigService.kt` (system-admin-service) — enhance domain-scoped config.

### FR-017: Idempotency cho Create/Update Operations [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotency cho mọi API create/update bằng idempotency key pattern (header `X-Idempotency-Key`).
- **Validation**: Duplicate request trả kết quả cached; key TTL = 24h trong Redis.
- **Justification**: Enterprise system cần đảm bảo no duplicate side effects khi network retry. `IdempotencyFilter.kt` đã có trên auth-service.
- **Impact**: [REUSE] `IdempotencyFilter.kt` (auth-service) — replicate pattern to account-service, system-admin-service.

### FR-018: Transaction Logging Toàn Diện [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải log toàn bộ API request/response cho audit và debugging (sensitive fields masked).
- **Validation**: Log format structured JSON; correlation ID tracing; log rotation.
- **Justification**: Compliance requirement — base-core `common-log` có `HttpLoggingFilter` reusable.
- **Impact**: [REUSE] `HttpLoggingFilter` (base-core common-log) — already available via base-core dependency.

### FR-019: Timeout Handling cho External API [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải có timeout handling cho mọi external API call (SSO provider, CAPTCHA) với configurable timeout.
- **Validation**: Default timeout 10s; circuit breaker pattern cho repeated failures.
- **Justification**: `MfaRateLimitService.kt` đã có fail-open pattern — mở rộng cho toàn bộ external calls.
- **Impact**: [MODIFY] `HttpClientConfig.kt`, `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt` (auth-service) — add configurable timeout, circuit breaker.

### FR-020: Retry Mechanism cho Failed Calls [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải có retry mechanism cho Kafka event publishing và external API calls với exponential backoff.
- **Validation**: Max 3 retries; dead-letter queue cho Kafka; backoff multiplier = 2.
- **Justification**: Kafka inter-service events cần retry đảm bảo eventual consistency.
- **Impact**: [MODIFY] `SpringEventPublisher.kt`, `PermissionChangedConsumer.kt` (auth-service) — add retry with backoff.

### FR-021: Request Authentication & Encryption [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xác thực mọi inter-service communication (service-to-service JWT hoặc mTLS).
- **Validation**: Internal endpoints require internal JWT validation; E2EE đã có cho client-facing.
- **Justification**: 3 microservices giao tiếp qua REST/Kafka — cần bảo mật inter-service. `ServiceTokenService.kt` đã có trên auth-service.
- **Impact**: [MODIFY] `ServiceTokenService.kt` (auth-service) — enhance for cross-service token validation.

## 3. Non-functional Requirements

- **Performance**: API response time < 500ms (P95); menu tree load (cached) < 100ms; permission check (cached) < 50ms; rate limit check < 10ms.
- **Security**: MFA progressive auth; Argon2id password hashing; API key SHA-256 hash; TOTP secret AES-256 encrypted; audit log immutable (no UPDATE/DELETE constraint).
- **Caching**: Caffeine L1 (30s TTL) + Redis L2 (5-30min TTL); cache hit ratio > 80%. Existing: `AbstractTwoTierCache.kt`, `CaffeinePermissionCache.kt`, `MultiTierPermissionCache.kt`.
- **Architecture**: Clean Architecture (Hexagonal) — `adapter/in/web`, `adapter/out/persistence`, `application`. 3 services với separate PostgreSQL DB.
- **Messaging**: Kafka async events cho inter-service communication; Spring ApplicationEvent as fallback. Existing: `PermissionChangedConsumer.kt`, `SpringEventPublisher.kt`.
- **Data**: Snowflake 64-bit Long ID generation; Flyway migrations; separate DB per service.
- **Scalability**: 1000+ concurrent users per service; horizontal scaling support.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-008 (Session) và FR-009 (Account Lifecycle) có overlap nhỏ trên session termination — consolidated: FR-008 handles session management (CRUD, max concurrent), FR-009 handles account-level lifecycle actions (deactivate, delete, export).

## 5. Enriched Domain Requirements

### Enriched FRs (5/5 — max limit reached)

| FR-ID | Tên | Justification |
|-------|-----|---------------|
| FR-017 | Idempotency | Enterprise API cần no duplicate side effects khi retry. `IdempotencyFilter.kt` đã có. |
| FR-018 | Transaction Logging | Compliance + debugging (base-core common-log reusable) |
| FR-019 | Timeout Handling | External SSO/CAPTCHA resilience (fail-open pattern exists in `MfaRateLimitService.kt`) |
| FR-020 | Retry Mechanism | Kafka + external API reliability (exponential backoff) |
| FR-021 | Request Auth/Encryption | Inter-service security (3 services communicate). `ServiceTokenService.kt` exists. |

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Keycloak (Optional) | SSO/OIDC delegation | `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` |
| Redis | Cache + state store | Permission cache, OTP state, rate limiting, anonymous sessions |
| Kafka | Async inter-service events | `PermissionChangedConsumer.kt`, `SpringEventPublisher.kt` |
| PostgreSQL | Persistent storage | Separate DB per service; Flyway migrations |
| CAPTCHA Provider | Bot protection | `CaptchaClient.kt`, `HttpCaptchaGateway.kt` |
| Google Tink + AWS KMS | E2EE | `TinkCipherAlgorithmFactory.kt` |

## 6. Assumptions

- ⚠️ Assumption: [CHANGED] Tất cả 3 services đã có substantial implementations — Lý do: codebase scan xác nhận auth-service (29+ application files), account-service (16 files, 883 LOC), system-admin-service (27 files, 1967 LOC). **Archived version (2026-08-24) sai khi classify account-service và system-admin-service là stubs.**
- ⚠️ Assumption: `TreeEntity` base class tồn tại tại `system-admin-service/shared/persistence/TreeEntity.kt` — Lý do: confirmed via codebase scan, extends `SnowflakePersistentAuditableEntity`.
- ⚠️ Assumption: Spring Security 7 MFA API (`FactorGrantedAuthority`) đã stable — Lý do: Spring Boot 4.1.0 docs reference.
- ⚠️ Assumption: API Gateway đã xử lý routing cơ bản — rate limit config từ admin sẽ được Gateway đọc qua Redis sync.
- ⚠️ Assumption: Kafka cần đảm bảo runtime dependency — Lý do: `build.gradle.kts` review, `PermissionChangedConsumer.kt` sử dụng `@KafkaListener`.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không trừ |
| Đầy đủ (Completeness) | 22/25 | FR-013: workflow condition DSL chưa define cụ thể |
| Nhất quán (Consistency) | 22/25 | [CHANGED] Service boundary inconsistency giảm so với archive — account-service có ProfileService, DeviceService |
| Kiểm thử được (Testability) | 21/25 | FR-013: state machine guard conditions chưa đầy đủ; FR-010: cache invalidation timing chưa rõ SLA |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Completeness | -3 | FR-013 | Workflow condition DSL ("JSONB conditions evaluate dynamic routing") chưa define cụ thể condition schema | Define condition DSL tương tự PBAC PolicyCondition — JSON schema với operators |
| 2 | Consistency | -3 | FR-005, FR-009 | Profile (account-service) vs Lifecycle (auth-service) — service boundary vẫn cần clarify inter-service contract | Define clear inter-service API contract via Kafka events |
| 3 | Testability | -2 | FR-013 | State machine guard conditions ("if configured") vague | Add explicit guard per transition: `canResubmit`, `canCancel`, `isEscalated` |
| 4 | Testability | -2 | FR-010 | Cache invalidation "event-driven" chưa specify timing guarantee | Define max staleness SLA: ≤5s after permission change |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Scope Risk | 🟡 | Scope rất lớn (3 microservices, 47 use cases, 57+ endpoints) — risk scope creep. [CHANGED] Giảm risk vì code đã có — enhancement only. | All | Phân chia phases theo research handoff; strict phase gates |
| 2 | Service Boundary | 🟡 | FR-005 (Profile) trên account-service nhưng FR-009 (Lifecycle) vẫn trên auth-service — inconsistent boundary | FR-005, FR-009 | Define clear contract: lifecycle actions trigger from account-service qua Kafka event tới auth-service |
| 3 | Infrastructure | 🟢 | Kafka runtime dependency cần verify — `PermissionChangedConsumer.kt` dùng `@KafkaListener` | FR-020 | Verify build.gradle.kts dependency scope |

> [CHANGED] So với archived version: Risk giảm từ NEWBUILD → EXTEND, vì all 3 services đã có substantial code.

## 9. Open Questions

- OQ-001: Spring Security 7 `@EnableMultiFactorAuthentication` API đã stable chưa? Nếu chưa → fallback to custom MFA flow (hiện tại `MfaService.kt` đã có).
- OQ-002: Menu cache invalidation khi role permissions thay đổi ở system-admin-service — dùng Redis Pub/Sub hay Kafka? (brainstorm_notes.md: recommend Kafka for consistency)

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
IAM (Identity & Access Management), System Administration, Account Management

### 10.2 Flow Type
Non-Financial (authentication, authorization, configuration management)

### 10.3 Candidate Services
- **auth-service** (PRIMARY — existing code, EXTEND): MFA, SSO, JWT, Password Policy, RBAC, PBAC, Session Management, E2EE, Anonymous Sessions, Account Lifecycle.
  - Evidence: `src/main/kotlin/com/ntt/authservice/auth/` (29 application files), `rbac/` (RbacEngine, entities), `pbac/` (PolicyEvaluator), `shared/` (config, exception, security, i18n, audit, persistence)
- **account-service** (EXTEND — [CHANGED] substantial code, NOT stub): Profile, Device, Preferences.
  - Evidence: `account-service/src/main/kotlin/com/ntt/accountservice/` — 16 Kotlin files, 883 LOC. Modules: `profile/` (ProfileService, ProfileController, UserProfileEntity, UserContactEntity, ProfileKafkaListener, ProfileDtos), `device/` (DeviceService, DeviceController, UserDeviceEntity, DeviceDtos), `preference/` (PreferenceService, PreferenceController, UserPreferenceEntity), `shared/exception/` (AccountControllerAdvice, AccountErrorCode, AccountExceptions).
- **system-admin-service** (EXTEND — [CHANGED] substantial code, NOT stub): Menu Permission, Organization, API Partner, Workflow, Audit, Config.
  - Evidence: `system-admin-service/src/main/kotlin/com/ntt/sysadminservice/` — 27 Kotlin files, 1967 LOC. Modules: `menu/` (MenuPermissionService), `organization/` (OrganizationService, PositionService, DepartmentEntity, PositionEntity, UserPositionEntity, DepartmentController, PositionController), `apipartner/` (ApiPartnerService, ApiUsageController), `workflow/` (WorkflowEngine, WorkflowService, WorkflowEscalationScheduler, WorkflowEntities, WorkflowController), `audit/` (AuditAspect, AuditService, AuditLogEntity, AuditController), `config/` (DomainConfigService, FeatureFlagService, FeatureFlagEntity), `shared/` (TreeEntity, TreeBuilder, SysAdminControllerAdvice, SysAdminErrorCode, SysAdminExceptions).

### Detection Evidence
- Keyword: `auth`, `mfa`, `sso`, `rbac`, `pbac` → Module: `com.ntt.authservice` → File: `AuthService.kt`, `MfaService.kt`, `SsoAdapter.kt`, `RbacEngine.kt`
- Keyword: `session`, `lifecycle` → Module: `auth.application` → File: `LoginSessionService.kt`, `AccountLifecycleService.kt`
- Keyword: `password`, `policy` → Module: `rbac.adapter.out.persistence` → File: `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`
- Keyword: `menu`, `tree` → Module: `com.ntt.sysadminservice.menu` → File: `MenuPermissionService.kt`
- Keyword: `organization`, `department`, `position` → Module: `com.ntt.sysadminservice.organization` → File: `OrganizationService.kt`, `PositionService.kt`, `DepartmentEntity.kt`
- Keyword: `apipartner`, `api-key` → Module: `com.ntt.sysadminservice.apipartner` → File: `ApiPartnerService.kt`, `ApiUsageController.kt`
- Keyword: `workflow`, `approval` → Module: `com.ntt.sysadminservice.workflow` → File: `WorkflowEngine.kt`, `WorkflowService.kt`
- Keyword: `audit` → Module: `com.ntt.sysadminservice.audit` → File: `AuditService.kt`, `AuditAspect.kt`, `AuditLogEntity.kt`
- Keyword: `profile`, `device`, `preference` → Module: `com.ntt.accountservice` → File: `ProfileService.kt`, `DeviceService.kt`, `PreferenceService.kt`
- Archive: `openspec/changes/archive/2026-08-24-erp-iam-system/` (previous version classified as NEWBUILD — reclassified to EXTEND)
- Build: `build.gradle.kts` — base-core platform, Spring Security, JJWT, Tink, totp, Passay, Kafka, Redis, OAuth2

### 10.4 External Integrations
- Keycloak (Optional SSO) — `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`
- Redis (Cache + State) — `RedisConfig.kt`, multiple usage in OTP, MFA, sessions, cache
- Kafka (Event Streaming) — `PermissionChangedConsumer.kt`, `SpringEventPublisher.kt`
- PostgreSQL (Persistence) — Flyway migrations, separate DB per service
- CAPTCHA Provider — `CaptchaClient.kt`, `HttpCaptchaGateway.kt`
- Google Tink + AWS KMS (E2EE) — `TinkCipherAlgorithmFactory.kt`

### 10.5 Required Modules
- `base-web-starter` (REST API + exception handling via `BaseControllerAdvice`)
- `base-data-starter` (JPA + Flyway)
- `common-log` (AOP logging + `HttpLoggingFilter`)
- `eventsourcing-utils` (CQRS command/query handlers)
- `spring-boot-starter-security`
- `spring-boot-starter-data-redis`
- `spring-kafka` (runtime dependency)
- `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server`
- Bucket4j (NEW — for rate limiting in API partner module)

---

## 11. Transaction Flow Detail

### Flow 1: Login with MFA (auth-service)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Submit username + password + captcha | auth-service (AuthController → LoginHandler) |
| 2 | System | Validate credentials (Argon2), check MFA status | auth-service (UserEntity, MfaService) |
| 3 | System | MFA enabled → issue partial JWT, store partial session in Redis | auth-service (JwtService, OtpService) |
| 4 | End User | Submit TOTP/OTP code | auth-service (MfaController → MfaService) |
| 5 | System | Verify code → promote session → issue full JWT (access + refresh) | auth-service (SessionPromotionService, TokenGenerator) |

### Flow 2: Menu Tree Load (system-admin-service)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | GET /api/admin/menus/user-tree (JWT) | API Gateway |
| 2 | System | Check Redis cache `user:{userId}:menu` | system-admin-service (MenuPermissionService) |
| 3 | System | Cache miss → resolve user roles + permissions | system-admin-service / auth-service (inter-service) |
| 4 | System | Load menu_items + role_menu_permissions + user overrides | system-admin-service (PostgreSQL) |
| 5 | System | Filter tree → cache (TTL 5min) → return | system-admin-service (Redis, TreeBuilder) |

### Flow 3: API Key Rate Limiting (Gateway + Redis)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | API Partner | Send request with `X-API-Key` header | API Gateway |
| 2 | System | Hash key (SHA-256) → lookup in Redis | Gateway (Bucket4j) |
| 3 | System | Check rate limit (token bucket algorithm) | Redis (decentralized) |
| 4 | System | Within limit → forward; exceeded → 429 + Retry-After | Target service |

### Flow 4: Approval Workflow (system-admin-service)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Submit entity for approval | system-admin-service (WorkflowController → WorkflowService) |
| 2 | System | Lookup workflow_definition by entity_type | system-admin-service (WorkflowEngine) |
| 3 | System | Create workflow_instance (PENDING), resolve first approver | system-admin-service |
| 4 | System | Notify approver via Kafka event | Kafka → notification service |
| 5 | Approver | Approve/Reject step | system-admin-service (WorkflowController) |
| 6 | System | Advance to next step or mark complete | system-admin-service (WorkflowEngine) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-MFA-01..05 | AUTH-F01 | `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `MfaController.kt` | Existing (EXTEND) |
| FR-002 | UC-MFA-02 | AUTH-F02 | `LoginHandler.kt`, `AuthService.kt`, `SessionPromotionService.kt` | Existing (EXTEND) |
| FR-003 | UC-SSO-01..04 | AUTH-F03 | `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` | Existing (EXTEND) |
| FR-004 | UC-PWD-01..02 | AUTH-F04 | `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` | Existing (REUSE) |
| FR-005 | UC-PROF-01..05 | ACC-F01 | `ProfileService.kt`, `ProfileController.kt`, `UserProfileEntity.kt`, `UserContactEntity.kt` | [CHANGED] Existing (EXTEND) |
| FR-006 | UC-PROF-01 | ACC-F02 | `PreferenceService.kt`, `PreferenceController.kt`, `UserPreferenceEntity.kt` | [CHANGED] Existing (EXTEND) |
| FR-007 | UC-DEV-01..03 | ACC-F03 | `DeviceService.kt`, `DeviceController.kt`, `UserDeviceEntity.kt` | [CHANGED] Existing (EXTEND) |
| FR-008 | UC-SES-01..02 | AUTH-F05 | `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt` | Existing (EXTEND) |
| FR-009 | UC-LIFE-01..03 | AUTH-F06 | `AccountLifecycleService.kt`, `AccountLifecycleController.kt` | Existing (REUSE) |
| FR-010 | UC-MENU-01..05 | SYS-F01 | `MenuPermissionService.kt`, `TreeEntity.kt`, `TreeBuilder.kt` | [CHANGED] Existing (EXTEND) |
| FR-011 | UC-ORG-01..05 | SYS-F02 | `OrganizationService.kt`, `PositionService.kt`, `DepartmentEntity.kt`, `PositionEntity.kt` | [CHANGED] Existing (EXTEND) |
| FR-012 | UC-API-01..06 | SYS-F03 | `ApiPartnerService.kt`, `ApiUsageController.kt` | [CHANGED] Existing (EXTEND) |
| FR-013 | UC-WF-01..06 | SYS-F04 | `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowEscalationScheduler.kt` | [CHANGED] Existing (EXTEND) |
| FR-014 | N/A | SYS-F05 | `DomainConfigService.kt`, `FeatureFlagService.kt`, `FeatureFlagEntity.kt` | [CHANGED] Existing (EXTEND) |
| FR-015 | UC-AUDIT-01..02 | SYS-F06 | `AuditLogService.kt` (auth), `AuditService.kt`, `AuditAspect.kt` (sys-admin) | Existing (EXTEND) |
| FR-016 | N/A | AUTH-F07 | `DomainEntity.kt`, `DomainLookupService.kt`, `DomainConfigService.kt` | Existing (EXTEND) |
| FR-017 | N/A | INFRA-F01 | `IdempotencyFilter.kt` (auth-service) | Existing REUSE [ENRICHED] |
| FR-018 | N/A | INFRA-F02 | `HttpLoggingFilter` (base-core common-log) | REUSE [ENRICHED] |
| FR-019 | N/A | INFRA-F03 | `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt`, `HttpClientConfig.kt` | EXTEND [ENRICHED] |
| FR-020 | N/A | INFRA-F04 | `SpringEventPublisher.kt`, `PermissionChangedConsumer.kt` | EXTEND [ENRICHED] |
| FR-021 | N/A | INFRA-F05 | `ServiceTokenService.kt` (auth-service) | EXTEND [ENRICHED] |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations

**[CHANGED] So với archived version (2026-08-24):**

1. **Classification reclassified: NEWBUILD → EXTEND**. Codebase scan xác nhận tất cả 3 services có substantial implementations:
   - auth-service: 29+ application files, 20+ controllers, 15+ entities, CQRS handlers, caching, E2EE
   - account-service: 16 Kotlin files, 883 LOC — Profile, Device, Preference modules (NOT stubs)
   - system-admin-service: 27 Kotlin files, 1967 LOC — Menu, Organization, API Partner, Workflow, Audit, Config modules (NOT stubs)

2. **Risk giảm đáng kể**: Vì code đã có, approach chuyển từ "build from scratch" → "enhance existing". Effort estimate giảm ~40-50%.

3. **TreeEntity base class confirmed**: Tại `system-admin-service/shared/persistence/TreeEntity.kt` — extends `SnowflakePersistentAuditableEntity`, có `parentId`, `name`, `code`, `sortOrder`, `treeLevel`, `treePath`.

4. **Error code patterns consistent**: auth-service (`AUTH_001..044`), account-service (`ACCT_001..008`), system-admin-service (`SYS_001..018`). All implement `ErrorCodeBase` from base-core.

**Complexity Assessment**: HIGH (nhưng giảm so với archive vì code đã tồn tại)
- Cross-service communication (REST sync + Kafka async)
- Tree data structures (menu, department — TreeEntity + TreeBuilder đã có)
- State machine (workflow approval — WorkflowEngine đã có)
- Rate limiting (Bucket4j + Redis — ApiPartnerService đã có base)
- Multi-tier caching (Caffeine L1 + Redis L2 — AbstractTwoTierCache đã có)

### Related Features / Precedents
- `auth-core-features` (archived: `2026-08-20-auth-core-features/`) — MFA, SSO, Password Policy, Session.
- `anonymous-login-optimization` (archived: `2026-08-20-anonymous-login-optimization/`) — Anonymous session + Redis pattern.
- `api-response-i18n-standard` (archived: `2026-08-22-api-response-i18n-standard/`) — I18n pattern (`DatabaseMessageSource`).
- `e2ee-performance-compliance` (archived: `2026-08-15-e2ee-performance-compliance/`) — E2EE cipher pattern.
- `architecture-optimization` (archived: `2026-08-05-architecture-optimization/`) — Base architecture patterns.
- `auth-login-admin` (archived: `2026-08-11-auth-login-admin/`) — Login + admin session patterns.

### Integration Notes

1. **Redis as backbone** (from brainstorm): Gateway reads rate limit config directly from Redis (Bucket4j ProxyManager) — không gọi sync tới system-admin-service runtime. system-admin-service chỉ CRUD config → push to Redis.
2. **Kafka topics**: `iam.user.created`, `iam.user.sso_provisioned`, `iam.permission.changed`, `iam.audit.log`. `PermissionChangedConsumer.kt` already listens to `iam.permission.changed`.
3. **Lean JWT** (from brainstorm): JWT chỉ chứa `userId`, `tenantId`, `roleIds`. Permission resolution at runtime via Redis cache.
4. **Service-to-service auth**: `ServiceTokenService.kt` đã có trên auth-service — extend for cross-service validation.
5. **Event Publisher**: `SpringEventPublisher.kt` hiện dùng Spring ApplicationEvent — migrate to Kafka.

### Suggested Approach

[CHANGED] Do all 3 services đã có code, approach chuyển sang enhancement-focused:

1. **Phase 1 (Foundation Enhancement, 2-3 weeks)**: Enhance system-admin-service Menu Permission (button-level, user override) + Organization (transfer user). Enhance account-service Profile (verification flow, avatar).
2. **Phase 2 (Security Enhancement, 2-3 weeks)**: Enhance system-admin-service API Partner (Bucket4j rate limiting). Enhance auth-service MFA (progressive flow, recovery codes).
3. **Phase 3 (Advanced Enhancement, 2-3 weeks)**: Enhance system-admin-service Workflow (conditional routing, delegation, escalation). Enhance Config (versioning).
4. **Phase 4 (Integration & Testing, 1-2 weeks)**: Kafka event migration; E2E tests; inter-service auth; OpenAPI documentation.

### Context from Confluence Images
N/A — Source là research artifacts (file-based), không có Confluence.

### Brainstorm Integration
- **Selected Direction**: "Event-Driven & Redis-Backed Decentralized Authorization" (brainstorm_notes.md)
- **Key Decisions**:
  - Decentralized rate limiting (Bucket4j + Redis at Gateway) over synchronous check
  - Kafka event-driven inter-service communication over REST coupling
  - Lean JWT (identity + roles only) over permission-embedded JWT
- **Open from brainstorm**: Redis Pub/Sub vs Kafka for menu cache invalidation — recommend Kafka for consistency.
