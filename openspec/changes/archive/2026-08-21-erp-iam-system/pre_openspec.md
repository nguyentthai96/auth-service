# Pre-OpenSpec: erp-iam-system

> **Type**: NEWBUILD
> **Flow**: Non-Financial
> **Source**: URD (File: `openspec/research/erp-iam-system/business_analysis.md`)
> **Classification Evidence**: Keywords `auth, account, system-admin` → Module: `com.ntt.authservice` → File: `src/main/kotlin/com/ntt/authservice/` (auth, rbac, pbac, shared packages). Research artifacts confirm 3 services: auth-service (existing foundation), account-service (stub), system-admin-service (stub).
> **Archive**: `openspec/changes/archive/erp-iam-system/` (empty — no prior specs archived)
> **Quality Score**: 88/100
> **Brainstorm**: `brainstorm_notes.md` — Selected Direction: "Event-Driven & Redis-Backed Decentralized Authorization"

## 📋 Feature Summary

Hệ thống Identity & Access Management hoàn chỉnh cho ERP enterprise, chia thành 3 microservices: `auth-service` (xác thực, MFA, SSO, JWT, Password Policy, E2EE — đã có foundation mạnh), `account-service` (profile, device, session, preferences, lifecycle — cần build), và `system-admin-service` (menu permission, organization, API partner, approval workflow, audit, config — cần build). Kiến trúc Event-Driven với Redis làm backbone cho decentralized authorization và rate limiting.

| Metric | Giá trị |
|--------|---------|
| Số FR | 21 (URD: 16, Enriched: 5) |
| Issues | 3 (🔴: 0, 🟡: 2, 🟢: 1) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

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

### FR-002: Progressive MFA Login Flow [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ progressive authentication: password verify → partial JWT → MFA verify → full JWT.
- **Validation**: Partial token chỉ cho phép `/verify-2fa`; TTL partial token = 5 phút; trusted device skip MFA.
- **Existing**: `LoginHandler.kt`, `AuthService.kt`, `AuthController.kt`, `CqrsAuthController.kt` — login flow đã có.

### FR-003: SSO Integration [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ OAuth2/OIDC login via external IdP (Google, Microsoft, Keycloak) với auto-provision user nếu domain config cho phép.
- **Validation**: SSO callback verify token; auto-link external account; Keycloak optional downstream.
- **Existing**: `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` — SSO adapter đã có.

### FR-004: Password Policy [URD]
- **Actor**: System Administrator, Domain Admin
- **Action**: Hệ thống phải cho phép cấu hình password rules per domain (min length, uppercase, lowercase, digit, special, max age, history count).
- **Validation**: Không reuse N passwords gần nhất; force change khi expired.
- **Existing**: `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` — đã có.

### FR-005: Quản lý User Profile [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép CRUD thông tin cá nhân (display name, first/last name, DOB, address, timezone, locale, avatar) tách biệt khỏi auth data, trên account-service.
- **Validation**: Đổi email/phone cần verification flow.
- **Existing**: account-service stub — `ProfileKafkaListener.kt` (listener only, no CRUD).

### FR-006: Quản lý Preferences & Settings [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép user lưu cài đặt UI, notification, privacy preferences dưới dạng key-value/category.
- **Validation**: Validate category/key format, merge-update semantics.
- **Existing**: NOT DETECTED — cần build mới trên account-service.

### FR-007: Quản lý Device [URD]
- **Actor**: End User
- **Action**: Hệ thống phải track thiết bị đăng nhập, hỗ trợ trust device (skip 2FA cho 30 days), remote logout single device.
- **Validation**: Trusted device TTL = 30 days; device fingerprint unique per user.
- **Existing**: NOT DETECTED — cần build trên account-service.

### FR-008: Quản lý Session Nâng Cao [URD]
- **Actor**: End User, System Administrator
- **Action**: Hệ thống phải track active sessions, cấu hình max concurrent sessions per user, cho phép terminate specific session.
- **Validation**: Auto-expire session khi inactive; oldest session terminated khi vượt max.
- **Existing**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt`, `LoginSessionEntity.kt` — foundation đã có trên auth-service.

### FR-009: Account Lifecycle (GDPR) [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép deactivate account, request deletion (GDPR), export personal data.
- **Validation**: Deletion request có grace period; export format JSON; immutable audit trail.
- **Existing**: `AccountLifecycleService.kt`, `AccountLifecycleController.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt` — đã có trên auth-service.

### FR-010: Phân Quyền Menu Động [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cung cấp cây menu dynamic (DIRECTORY, MENU, BUTTON, API types) với role-based permission + user override. Frontend nhận menu tree filtered per user.
- **Validation**: User override > role permission; BUTTON type invisible trong navigation; cache Redis TTL 5 phút; circular reference detection; menu code unique per domain.
- **Existing**: NOT DETECTED — cần build trên system-admin-service.

### FR-011: Quản lý Cơ Cấu Tổ Chức [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải quản lý department tree (recursive hierarchy, max 10 levels) và position, phân bổ user vào vị trí.
- **Validation**: Department tree acyclic; position unique per department; transfer user between departments.
- **Existing**: NOT DETECTED — cần build trên system-admin-service.

### FR-012: Quản lý API Partner [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải hỗ trợ partner onboarding, API key lifecycle (generate, rotate, revoke), rate limiting (Bucket4j + Redis), quota tracking.
- **Validation**: API key show-once (Stripe pattern); key format `ntt_pk_`/`ntt_sk_` + SHA-256 hash; IP whitelist; max keys per partner.
- **Existing**: NOT DETECTED — cần build trên system-admin-service.

### FR-013: Dynamic Approval Workflow [URD]
- **Actor**: System Administrator, Approver
- **Action**: Hệ thống phải cho phép define workflow (multi-step, conditional routing), submit entity for approval, approve/reject/delegate, auto-escalation on timeout.
- **Validation**: Workflow version immutable; state machine: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED.
- **Existing**: NOT DETECTED — cần build trên system-admin-service.

### FR-014: Quản lý Cấu Hình Hệ Thống [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu trữ system configs (STRING, JSON, NUMBER types) và feature flags per domain.
- **Validation**: Config versioning; audit trail on changes.
- **Existing**: NOT DETECTED — cần build trên system-admin-service.

### FR-015: Audit Trail Toàn Diện [URD]
- **Actor**: System
- **Action**: Hệ thống phải ghi nhận toàn bộ thao tác admin (create/update/delete) vào immutable audit log với old_value/new_value JSONB.
- **Validation**: Không cho UPDATE/DELETE trên audit_logs; mask sensitive data; search + export support.
- **Existing**: `AuditLogService.kt` — foundation trên auth-service; cần mở rộng cho system-admin-service.

### FR-016: Domain/Tenant Config [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu cấu hình nâng cao per domain (branding, login page config, session policy).
- **Validation**: Tách biệt dữ liệu theo domain; cache config.
- **Existing**: `DomainEntity.kt` (rbac module), `DomainLookupService.kt` — domain foundation đã có.

### FR-017: Idempotency cho Create/Update Operations [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo idempotency cho mọi API create/update bằng idempotency key pattern (header `X-Idempotency-Key`).
- **Validation**: Duplicate request trả kết quả cached; key TTL = 24h trong Redis.
- **Justification**: Enterprise system cần đảm bảo no duplicate side effects khi network retry.

### FR-018: Transaction Logging Toàn Diện [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải log toàn bộ API request/response cho audit và debugging (sensitive fields masked).
- **Validation**: Log format structured JSON; correlation ID tracing; log rotation.
- **Justification**: Compliance requirement — base-core `common-log` có `HttpLoggingFilter` reusable.

### FR-019: Timeout Handling cho External API [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải có timeout handling cho mọi external API call (SSO provider, CAPTCHA) với configurable timeout.
- **Validation**: Default timeout 10s; circuit breaker pattern cho repeated failures.
- **Justification**: `MfaRateLimitService.kt` đã có fail-open pattern — mở rộng cho toàn bộ external calls.

### FR-020: Retry Mechanism cho Failed Calls [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải có retry mechanism cho Kafka event publishing và external API calls với exponential backoff.
- **Validation**: Max 3 retries; dead-letter queue cho Kafka; backoff multiplier = 2.
- **Justification**: Kafka hiện là `compileOnly` — cần runtime retry khi implement inter-service events.

### FR-021: Request Authentication & Encryption [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải xác thực mọi inter-service communication (service-to-service JWT hoặc mTLS).
- **Validation**: Internal endpoints require internal JWT validation; E2EE đã có cho client-facing.
- **Justification**: 3 microservices giao tiếp qua REST/Kafka — cần bảo mật inter-service.

## 3. Non-functional Requirements

- **Performance**: API response time < 500ms (P95); menu tree load (cached) < 100ms; permission check (cached) < 50ms; rate limit check < 10ms.
- **Security**: MFA progressive auth; Argon2id password hashing; API key SHA-256 hash; TOTP secret AES-256 encrypted; audit log immutable (no UPDATE/DELETE constraint).
- **Caching**: Caffeine L1 (30s TTL) + Redis L2 (5-30min TTL); cache hit ratio > 80%.
- **Architecture**: Clean Architecture (Hexagonal) — `adapter/in/web`, `adapter/out/persistence`, `application`. 3 services với separate PostgreSQL DB.
- **Messaging**: Kafka async events cho inter-service communication; Spring ApplicationEvent as fallback.
- **Data**: Snowflake 64-bit Long ID generation; Flyway migrations; separate DB per service.
- **Scalability**: 1000+ concurrent users per service; horizontal scaling support.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. FR-008 (Session) và FR-009 (Account Lifecycle) có overlap nhỏ trên session termination — consolidated: FR-008 handles session management (CRUD, max concurrent), FR-009 handles account-level lifecycle actions (deactivate, delete, export).

## 5. Enriched Domain Requirements

### Enriched FRs (5/5 — max limit reached)

| FR-ID | Tên | Justification |
|-------|-----|---------------|
| FR-017 | Idempotency | Enterprise API cần no duplicate side effects khi retry |
| FR-018 | Transaction Logging | Compliance + debugging (base-core common-log reusable) |
| FR-019 | Timeout Handling | External SSO/CAPTCHA resilience (fail-open pattern exists) |
| FR-020 | Retry Mechanism | Kafka + external API reliability (exponential backoff) |
| FR-021 | Request Auth/Encryption | Inter-service security (3 services communicate) |

### External Integrations

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Keycloak (Optional) | SSO/OIDC delegation | `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` |
| Redis | Cache + state store | Permission cache, OTP state, rate limiting, anonymous sessions |
| Kafka | Async inter-service events | `compileOnly` dependency — cần upgrade to `implementation` |
| PostgreSQL | Persistent storage | Separate DB per service; Flyway migrations V1-V9 |
| CAPTCHA Provider | Bot protection | `CaptchaClient.kt`, `HttpCaptchaGateway.kt` |
| Google Tink + AWS KMS | E2EE | `TinkCipherAlgorithmFactory.kt` |

## 6. Assumptions

- ⚠️ Assumption: account-service và system-admin-service chỉ có stub code — Lý do: research_brief.md confirmed, directory scan confirmed.
- ⚠️ Assumption: base-core chưa có TreeEntity base class — Lý do: research confirmed, cần tạo mới cho menu/department tree structures.
- ⚠️ Assumption: Spring Security 7 MFA API (`FactorGrantedAuthority`) đã stable — Lý do: Spring Boot 4.1.0 docs reference.
- ⚠️ Assumption: API Gateway đã xử lý routing cơ bản — rate limit config từ admin sẽ được Gateway đọc qua Redis sync.
- ⚠️ Assumption: Kafka cần upgrade từ `compileOnly` sang `implementation` dependency — Lý do: `build.gradle.kts` review.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không trừ |
| Đầy đủ (Completeness) | 20/25 | FR-013: workflow condition DSL chưa define cụ thể; FR-015: audit old_value/new_value JSON format chưa rõ |
| Nhất quán (Consistency) | 22/25 | FR-005/FR-009: Profile trên account-service nhưng Lifecycle trên auth-service — inconsistent service boundary |
| Kiểm thử được (Testability) | 21/25 | FR-013: state machine guard conditions chưa đầy đủ; FR-010: cache invalidation timing chưa rõ SLA |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Completeness | -3 | FR-013 | Workflow condition DSL ("JSONB conditions evaluate dynamic routing") chưa define cụ thể condition schema | Define condition DSL tương tự PBAC PolicyCondition — JSON schema với operators |
| 2 | Completeness | -2 | FR-015 | "old_value_json/new_value_json" format chưa standardize | Define JSON diff format: `{field: {old: X, new: Y}}` |
| 3 | Consistency | -3 | FR-005, FR-009 | Profile (account-service) vs Lifecycle (auth-service) — service boundary overlap trên user data | Migrate lifecycle to account-service hoặc define clear inter-service API contract |
| 4 | Testability | -2 | FR-013 | State machine guard conditions ("if configured") vague | Add explicit guard per transition: `canResubmit`, `canCancel`, `isEscalated` |
| 5 | Testability | -2 | FR-010 | Cache invalidation "event-driven" chưa specify timing guarantee | Define max staleness SLA: ≤5s after permission change |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Scope Risk | 🟡 | Scope rất lớn (3 microservices, 47 use cases, 57+ endpoints) — risk scope creep | All | Phân chia 4 phases theo research handoff; strict phase gates |
| 2 | Service Boundary | 🟡 | FR-005 (Profile) trên account-service nhưng FR-009 (Lifecycle) vẫn trên auth-service — inconsistent boundary | FR-005, FR-009 | Define clear contract: lifecycle actions trigger from account-service qua Kafka event tới auth-service |
| 3 | Infrastructure | 🟢 | Kafka hiện là compileOnly — cần migration plan để enable runtime | FR-020 | Phase 4 integration: add `spring-kafka` implementation dependency |

## 9. Open Questions

- OQ-001: Spring Security 7 `@EnableMultiFactorAuthentication` API đã stable chưa? Nếu chưa → fallback to custom MFA flow (hiện tại MfaService.kt đã có).
- OQ-002: Menu cache invalidation khi role permissions thay đổi ở system-admin-service — dùng Redis Pub/Sub hay Kafka? (brainstorm_notes.md: OPEN question)

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
IAM (Identity & Access Management), System Administration, Account Management

### 10.2 Flow Type
Non-Financial (authentication, authorization, configuration management)

### 10.3 Candidate Services
- **auth-service** (PRIMARY — existing code): MFA, SSO, JWT, Password Policy, RBAC, PBAC, Session Management, E2EE, Anonymous Sessions, Account Lifecycle.
  - Evidence: `src/main/kotlin/com/ntt/authservice/auth/` (28 application files), `rbac/` (RbacEngine, entities), `pbac/` (PolicyEvaluator), `shared/` (config, exception, security, i18n, audit, persistence)
- **account-service** (NEWBUILD — stub only): Profile, Device, Session (Redis-backed), Preferences, Lifecycle.
  - Evidence: Research handoff confirms stub status; only `ProfileKafkaListener.kt` exists.
- **system-admin-service** (NEWBUILD — stub only): Menu Permission, Organization, API Partner, Workflow, Audit, Config.
  - Evidence: Research handoff confirms stub status.

### Detection Evidence
- Keyword: `auth`, `mfa`, `sso`, `rbac`, `pbac` → Module: `com.ntt.authservice` → File: `AuthService.kt`, `MfaService.kt`, `SsoAdapter.kt`, `RbacEngine.kt`
- Keyword: `session`, `lifecycle` → Module: `auth.application` → File: `LoginSessionService.kt`, `AccountLifecycleService.kt`
- Keyword: `password`, `policy` → Module: `rbac.adapter.out.persistence` → File: `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`
- Keyword: `menu`, `organization`, `api-partner`, `workflow` → Module: system-admin-service → File: N/A (NEWBUILD)
- Keyword: `device`, `preferences`, `profile` → Module: account-service → File: N/A (NEWBUILD)
- Archive: `openspec/changes/archive/erp-iam-system/` (empty)
- Build: `build.gradle.kts` — base-core platform, Spring Security, JJWT, Tink, totp, Passay, Kafka (compileOnly), Redis, OAuth2

### 10.4 External Integrations
- Keycloak (Optional SSO) — `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`
- Redis (Cache + State) — `RedisConfig.kt`, `StringRedisTemplate` usage in 10+ services
- Kafka (Event Streaming) — `PermissionChangedConsumer.kt`, `SpringEventPublisher.kt`
- PostgreSQL (Persistence) — Flyway V1-V9, separate DB per service
- CAPTCHA Provider — `CaptchaClient.kt`, `HttpCaptchaGateway.kt`
- Google Tink + AWS KMS (E2EE) — `TinkCipherAlgorithmFactory.kt`

### 10.5 Required Modules
- `base-web-starter` (REST API + exception handling via `BaseControllerAdvice`)
- `base-data-starter` (JPA + Flyway)
- `common-log` (AOP logging + `HttpLoggingFilter`)
- `eventsourcing-utils` (CQRS command/query handlers)
- `spring-boot-starter-security`
- `spring-boot-starter-data-redis`
- `spring-kafka` (upgrade from compileOnly to implementation)
- `spring-boot-starter-oauth2-client` + `spring-boot-starter-oauth2-resource-server`
- Bucket4j (NEW — for rate limiting)

---

## 11. Transaction Flow Detail

### Flow 1: Login with MFA (auth-service)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Submit username + password + captcha | auth-service (AuthController → LoginHandler) |
| 2 | System | Validate credentials (Argon2), check MFA status | auth-service (UserEntity, MfaService) |
| 3 | System | MFA enabled → issue partial JWT, store partial session in Redis | auth-service (JwtService, OtpService) |
| 4 | End User | Submit TOTP/OTP code | auth-service (MfaController → MfaService) |
| 5 | System | Verify code → issue full JWT (access + refresh) | auth-service (TokenGenerator) |

### Flow 2: Menu Tree Load (system-admin-service)

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | GET /api/admin/menus/user-tree (JWT) | API Gateway |
| 2 | System | Check Redis cache `user:{userId}:menu` | system-admin-service |
| 3 | System | Cache miss → call auth-service for user roles | auth-service (internal API) |
| 4 | System | Load menu_items + role_menu_permissions + user overrides | system-admin-service (PostgreSQL) |
| 5 | System | Filter tree → cache (TTL 5min) → return | system-admin-service (Redis) |

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
| 1 | End User | Submit entity for approval | system-admin-service (WorkflowService) |
| 2 | System | Lookup workflow_definition by entity_type | system-admin-service |
| 3 | System | Create workflow_instance (PENDING), resolve first approver | system-admin-service |
| 4 | System | Notify approver via Kafka event | Kafka → notification service |
| 5 | Approver | Approve/Reject step | system-admin-service |
| 6 | System | Advance to next step or mark complete | system-admin-service |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-MFA-01..05 | AUTH-F01 | `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `MfaController.kt` | Existing (EXTEND) |
| FR-002 | UC-MFA-02 | AUTH-F02 | `LoginHandler.kt`, `AuthService.kt`, `AuthController.kt`, `CqrsAuthController.kt` | Existing (EXTEND) |
| FR-003 | UC-SSO-01..04 | AUTH-F03 | `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` | Existing (EXTEND) |
| FR-004 | UC-PWD-01..02 | AUTH-F04 | `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt` | Existing |
| FR-005 | UC-PROF-01..05 | ACC-F01 | TBD (account-service: UserProfileEntity, ProfileService, ProfileController) | NEWBUILD |
| FR-006 | UC-PROF-01 | ACC-F02 | TBD (account-service: UserPreferencesEntity, PreferencesService) | NEWBUILD |
| FR-007 | UC-DEV-01..03 | ACC-F03 | TBD (account-service: UserDeviceEntity, DeviceService, DeviceController) | NEWBUILD |
| FR-008 | UC-SES-01..02 | AUTH-F05 | `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt` | Existing (EXTEND) |
| FR-009 | UC-LIFE-01..03 | AUTH-F06 | `AccountLifecycleService.kt`, `AccountLifecycleController.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt` | Existing |
| FR-010 | UC-MENU-01..05 | SYS-F01 | TBD (system-admin-service: MenuItemEntity, MenuPermissionService, MenuController) | NEWBUILD |
| FR-011 | UC-ORG-01..05 | SYS-F02 | TBD (system-admin-service: DepartmentEntity, PositionEntity, OrganizationService) | NEWBUILD |
| FR-012 | UC-API-01..06 | SYS-F03 | TBD (system-admin-service: ApiPartnerEntity, ApiKeyEntity, ApiKeyService) | NEWBUILD |
| FR-013 | UC-WF-01..06 | SYS-F04 | TBD (system-admin-service: WorkflowDefinitionEntity, WorkflowService) | NEWBUILD |
| FR-014 | N/A | SYS-F05 | TBD (system-admin-service: SystemConfigEntity, ConfigService) | NEWBUILD |
| FR-015 | UC-AUDIT-01..02 | SYS-F06 | `AuditLogService.kt` (auth), TBD (system-admin: AuditLogEntity, AuditController) | EXTEND + NEWBUILD |
| FR-016 | N/A | AUTH-F07 | `DomainEntity.kt`, `DomainLookupService.kt` | Existing (EXTEND) |
| FR-017 | N/A | INFRA-F01 | TBD (IdempotencyFilter) | NEWBUILD [ENRICHED] |
| FR-018 | N/A | INFRA-F02 | `HttpLoggingFilter` (base-core common-log) | REUSE [ENRICHED] |
| FR-019 | N/A | INFRA-F03 | `HttpSsoGateway.kt`, `CaptchaClient.kt`, `HttpClientConfig.kt` | EXTEND [ENRICHED] |
| FR-020 | N/A | INFRA-F04 | `SpringEventPublisher.kt`, `PermissionChangedConsumer.kt` | EXTEND [ENRICHED] |
| FR-021 | N/A | INFRA-F05 | TBD (ServiceAuthFilter) | NEWBUILD [ENRICHED] |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations

Đây là feature có quy mô **rất lớn** — 3 microservices, 47 use cases, 57+ API endpoints, 30+ entities. auth-service đã có foundation mạnh: RBAC Engine (`RbacEngine.kt`), PBAC PolicyEvaluator, MFA (TOTP + OTP), SSO Adapter, Session Management, E2EE (Google Tink), Anonymous Sessions, Account Lifecycle, CQRS handlers. Có 20+ controllers, 15+ entities, 9 Flyway migrations.

**Complexity Assessment**: HIGH
- Cross-service communication (REST sync + Kafka async)
- Tree data structures (menu, department — cần TreeEntity base)
- State machine (workflow approval — 6 states, 8 transitions)
- Rate limiting (Bucket4j + Redis — decentralized at Gateway)
- Multi-tier caching (Caffeine L1 + Redis L2 — 6 cache patterns)

### Related Features / Precedents
- `auth-core-features` (archived: `2026-08-20-auth-core-features/`) — MFA, SSO, Password Policy, Session — đã implement Phase 1.
- `anonymous-login-optimization` (archived: `2026-08-20-anonymous-login-optimization/`) — Anonymous session + Redis pattern reusable.
- `api-response-i18n-standard` (archived: `2026-08-22-api-response-i18n-standard/`) — I18n pattern (`DatabaseMessageSource`) reusable.
- `e2ee-performance-compliance` (archived: `2026-08-15-e2ee-performance-compliance/`) — E2EE cipher pattern reusable.
- `architecture-optimization` (archived: `2026-08-05-architecture-optimization/`) — Base architecture patterns.
- `auth-login-admin` (archived: `2026-08-11-auth-login-admin/`) — Login + admin session patterns.

### Integration Notes

1. **Redis as backbone** (from brainstorm): Gateway reads rate limit config directly from Redis (Bucket4j ProxyManager) — không gọi sync tới system-admin-service runtime. system-admin-service chỉ CRUD config → push to Redis.
2. **Kafka topics**: `iam.user.created`, `iam.user.sso_provisioned`, `iam.permission.changed`, `iam.audit.log`. Hiện `compileOnly` — cần `implementation` dependency.
3. **Lean JWT** (from brainstorm): JWT chỉ chứa `userId`, `tenantId`, `roleIds`. Permission resolution at runtime via Redis cache. Tránh JWT bloat.
4. **Service-to-service auth**: Internal endpoints giữa 3 services cần service-level JWT hoặc mTLS — chưa implement.
5. **Event Publisher**: `SpringEventPublisher.kt` hiện dùng Spring ApplicationEvent — Phase 3 migrate to Kafka.

### Suggested Approach

1. **Phase 1 (Foundation P0, 3-4 weeks)**: system-admin-service Menu Permission + Organization + Audit; account-service Profile CRUD.
2. **Phase 2 (Security P1, 3-4 weeks)**: system-admin-service API Partner + Rate Limiting; account-service Device + Session (Redis).
3. **Phase 3 (Advanced P2, 2-3 weeks)**: system-admin-service Approval Workflow + System Config + Feature Flags.
4. **Phase 4 (Integration, 1-2 weeks)**: Kafka runtime migration; E2E tests; inter-service auth; OpenAPI documentation.

Bắt đầu với system-admin-service (Menu, Organization) vì đây là foundation cho frontend navigation và org-based authorization. Song song build account-service Profile module.

### Context from Confluence Images
N/A — Source là research artifacts (file-based), không có Confluence.

### Brainstorm Integration
- **Selected Direction**: "Event-Driven & Redis-Backed Decentralized Authorization" (brainstorm_notes.md)
- **Key Decisions**:
  - Decentralized rate limiting (Bucket4j + Redis at Gateway) over synchronous check to system-admin-service
  - Kafka event-driven inter-service communication over REST coupling
  - Lean JWT (identity + roles only) over permission-embedded JWT
- **Open from brainstorm**: Redis Pub/Sub vs Kafka for menu cache invalidation — recommend Kafka for consistency with other events.
