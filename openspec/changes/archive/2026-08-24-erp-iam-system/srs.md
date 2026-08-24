# SRS: erp-iam-system

_Generated: 2025-07-15_
_Profile: Non-Financial | N/A (no factory) | EXTEND_
_Previous Version: 2026-08-24 (archived, NEWBUILD) — [CHANGED] reclassified to EXTEND_

---

## 1. System Context

- **Feature Name**: ERP IAM System Enhancement
- **Domain**: IAM (Identity & Access Management), System Administration, Account Management
- **Flow**: Non-Financial
- **Services**: auth-service (EXTEND), account-service (EXTEND), system-admin-service (EXTEND)
- **Source**: URD (`openspec/research/erp-iam-system/business_analysis.md`)

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| End User | Người dùng hệ thống ERP | Login, MFA, profile, menu access, approval requests |
| System Administrator | Quản trị viên hệ thống | Menu config, organization, API partner, workflow, audit |
| Domain Admin | Quản trị viên domain/tenant | Role assignment, password policy, scoped configuration |
| API Partner | Hệ thống bên ngoài | API key authentication, rate-limited access |
| Approver | Người phê duyệt | Approve/reject/delegate workflow steps |
| System | Hệ thống tự động | Scheduled jobs, event processing, cache invalidation |

---

## 3. Functional Requirements

### 3.1 Authentication & Security (auth-service)

#### FR-001: Quản lý Multi-Factor Authentication [URD] [EXTEND]
- **Actor**: End User
- **Precondition**: User đã đăng nhập hoặc đang trong MFA setup flow.
- **Action**: Hệ thống hỗ trợ enable/disable MFA per user với phương thức OTP (SMS, Email), TOTP (app-based), CAPTCHA.
- **Validation Rules**:
  - OTP expires sau 5 phút, tối đa 3 lần sai → lock 30 phút.
  - TOTP window ±1 step (30s).
  - Recovery codes: generate 10 single-use codes on MFA setup, regenerate endpoint.
- **Error Codes**: `AUTH_011` (MFA code invalid), `AUTH_012` (MFA expired), `AUTH_013` (max attempts), `AUTH_019` (rate limited).
- **Existing Code**: `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `MfaController.kt`, `MfaRateLimitService.kt`.
- **Enhancement**: Add recovery codes management (generate, store hashed, verify, regenerate).
- **API Endpoints**:
  - `POST /api/auth/mfa/recovery-codes` — Generate recovery codes
  - `POST /api/auth/mfa/recovery-codes/verify` — Verify recovery code
  - `POST /api/auth/mfa/recovery-codes/regenerate` — Regenerate all codes

#### FR-002: Progressive MFA Login Flow [URD] [EXTEND]
- **Actor**: End User
- **Precondition**: MFA enabled for user.
- **Action**: Progressive auth: password verify → partial JWT (scope: `/verify-2fa` only) → MFA verify → full JWT.
- **Validation Rules**:
  - Partial token TTL = 5 minutes.
  - Trusted device → skip MFA (integrate with account-service device management).
- **Existing Code**: `LoginHandler.kt`, `AuthService.kt`, `SessionPromotionService.kt`.
- **Enhancement**: Integrate trusted device check via REST call to account-service.
- **API Endpoints**:
  - `POST /api/auth/login` — Password verify, returns partial JWT if MFA enabled
  - `POST /api/auth/verify-2fa` — MFA verify, returns full JWT

#### FR-003: SSO/OIDC Integration [URD] [EXTEND]
- **Actor**: End User
- **Precondition**: SSO provider configured for domain.
- **Action**: Support OIDC authorization code flow, token exchange, identity linking/unlinking, auto-provision user profile on first SSO login.
- **Validation Rules**:
  - SSO token validated via provider's JWKS endpoint.
  - Identity conflict detection (email already exists).
  - Cannot unlink last identity.
- **Error Codes**: `AUTH_014` (SSO token invalid), `AUTH_015` (not provisioned), `AUTH_016` (identity conflict).
- **Existing Code**: `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`.
- **Enhancement**: Publish `SsoProvisionedEvent` to Kafka for account-service profile auto-creation.
- **API Endpoints**:
  - `GET /api/auth/sso/authorize/{provider}` — Initiate SSO flow
  - `POST /api/auth/sso/callback` — SSO callback, token exchange
  - `POST /api/auth/sso/link` — Link SSO identity
  - `DELETE /api/auth/sso/unlink/{provider}` — Unlink SSO identity

#### FR-004: Password Policy [URD] [REUSE]
- **Actor**: Domain Admin
- **Action**: Configure password policy per domain (min length, complexity, history, expiry).
- **Existing Code**: `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`.
- **Classification**: REUSE — no changes needed, already fully implemented.
- **Error Codes**: `AUTH_017` (policy violation), `AUTH_018` (password expired).

#### FR-008: Session Management Enhancement [URD] [EXTEND]
- **Actor**: End User, System Administrator
- **Precondition**: User authenticated.
- **Action**: Track active sessions, configurable max concurrent sessions per user, terminate specific session.
- **Validation Rules**:
  - Auto-expire session on inactivity (configurable TTL).
  - Max concurrent sessions from `SessionPolicyService` config.
  - Oldest session terminated when exceeding max.
- **Existing Code**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt`, `LoginSessionEntity.kt`.
- **Enhancement**: Enforce concurrent session limit, provide active session list with device info.
- **API Endpoints**:
  - `GET /api/auth/sessions` — List user's active sessions
  - `DELETE /api/auth/sessions/{sessionId}` — Terminate specific session
  - `GET /api/admin/sessions` — Admin list all sessions (paginated)
  - `DELETE /api/admin/sessions/{userId}` — Admin terminate user sessions

#### FR-009: Account Lifecycle (GDPR) [URD] [EXTEND]
- **Actor**: End User
- **Precondition**: User authenticated.
- **Action**: Deactivate account, request deletion (GDPR), export personal data.
- **Validation Rules**:
  - Deletion request grace period (configurable, default 30 days).
  - Export format: JSON.
  - Immutable audit trail for all lifecycle actions.
- **Existing Code**: `AccountLifecycleService.kt`, `AccountLifecycleController.kt`, `AccountDeletionRequestEntity.kt`, `AccountDataExportEntity.kt`.
- **Enhancement**: Publish `AccountDeactivatedEvent` cross-service event, enhanced data export with auth data aggregation.
- **API Endpoints**:
  - `POST /api/auth/account/deactivate` — Deactivate account
  - `POST /api/auth/account/deletion-request` — Request GDPR deletion
  - `GET /api/auth/account/export` — Export personal data (JSON)

#### FR-021: Inter-service Authentication [ENRICHED] [EXTEND]
- **Actor**: System
- **Action**: Authenticate inter-service communication via service-level JWT.
- **Validation Rules**:
  - Internal endpoints path prefix: `/api/internal/`.
  - Service JWT contains `service_name`, `scope: INTERNAL`.
  - Shared secret for JWT signing (Phase 1).
- **Existing Code**: `ServiceTokenService.kt`.
- **Enhancement**: Add `ServiceAuthFilter.kt`, error codes AUTH_050–AUTH_052, internal endpoint security config.
- **Error Codes**: ⚠️ Originally planned AUTH_050–AUTH_052 but range consumed by Event Sourcing. Currently handled via Spring Security 401/403 without dedicated error codes. If dedicated codes needed, allocate AUTH_060+ range.
- **API Endpoints**:
  - `POST /api/internal/auth/service-token` — Generate service JWT
  - Internal endpoints prefixed with `/api/internal/`

#### FR-019: Timeout Handling [ENRICHED] [EXTEND]
- **Actor**: System
- **Action**: Configurable timeout for all external API calls with circuit breaker.
- **Validation Rules**:
  - Default timeout 10s.
  - Circuit breaker: 5 failures → open for 30s → half-open (1 attempt) → close.
- **Existing Code**: `HttpClientConfig.kt`, `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt`.
- **Enhancement**: Add Resilience4j circuit breaker annotations, configurable timeout properties.

#### FR-020: Retry & Kafka Event Migration [ENRICHED] [EXTEND]
- **Actor**: System
- **Action**: Retry mechanism for Kafka event publishing and external API calls with exponential backoff.
- **Validation Rules**:
  - Max 3 retries, backoff multiplier = 2, initial delay = 1s.
  - Dead-letter queue for Kafka (`.DLT` topic suffix).
- **Existing Code**: `SpringEventPublisher.kt`, `PermissionChangedConsumer.kt`, `EventPublisher.kt` (port).
- **Enhancement**: Create `KafkaEventPublisher.kt` implementing `EventPublisher` port, add retry with backoff, DLQ config.
- **Kafka Topics**:
  - `iam.user.registered` — auth → account-service
  - `iam.user.sso_provisioned` — auth → account-service
  - `iam.permission.changed` — auth → system-admin
  - `acct.lifecycle.deactivated` — account → auth
  - `system.audit.events` — system-admin → all

### 3.2 Account Management (account-service)

#### FR-005: Profile Management [URD] [EXTEND]
- **Actor**: End User
- **Precondition**: User registered.
- **Action**: View/update profile (fullName, phone, avatar, contacts), email/phone verification flow.
- **Validation Rules**:
  - Email uniqueness per domain.
  - Phone format validation (E.164).
  - Avatar max 5MB, formats: jpg/png/webp.
- **Existing Code**: `ProfileService.kt`, `ProfileController.kt`, `UserProfileEntity.kt`, `UserContactEntity.kt`, `ProfileDtos.kt`.
- **Enhancement**: Add verification flow (OTP via auth-service), avatar upload, profile completeness score.
- **Error Codes**: `ACCT_001` (profile not found), `ACCT_002` (duplicate email), `ACCT_003` (invalid format).
- **API Endpoints**:
  - `GET /api/account/profile` — Get current user profile
  - `PUT /api/account/profile` — Update profile
  - `POST /api/account/profile/avatar` — Upload avatar
  - `POST /api/account/profile/verify-email` — Initiate email verification
  - `POST /api/account/profile/verify-phone` — Initiate phone verification

#### FR-006: User Preferences [URD] [EXTEND]
- **Actor**: End User
- **Action**: Manage key-value preferences grouped by category (notification, display, language).
- **Validation Rules**:
  - Merge-update semantics (PATCH behavior).
  - Category-based grouping.
- **Existing Code**: `PreferenceService.kt`, `PreferenceController.kt`, `UserPreferenceEntity.kt`.
- **Enhancement**: Add category-based query, bulk merge-update.
- **Error Codes**: `ACCT_004` (invalid preference key).
- **API Endpoints**:
  - `GET /api/account/preferences` — Get all preferences
  - `GET /api/account/preferences/{category}` — Get by category
  - `PATCH /api/account/preferences` — Merge-update preferences

#### FR-007: Device Management [URD] [EXTEND]
- **Actor**: End User
- **Action**: Track registered devices, trust/untrust, remote logout, integrate with MFA skip.
- **Validation Rules**:
  - Device fingerprint unique per user.
  - Max 5 trusted devices per user.
  - Trusted device TTL = 30 days.
- **Existing Code**: `DeviceService.kt`, `DeviceController.kt`, `UserDeviceEntity.kt`, `DeviceDtos.kt`.
- **Enhancement**: Add trusted device management, TTL enforcement, integration API for auth-service MFA skip.
- **Error Codes**: `ACCT_005` (device not found), `ACCT_006` (max devices), `ACCT_007` (device already trusted).
- **API Endpoints**:
  - `GET /api/account/devices` — List user devices
  - `POST /api/account/devices/trust` — Trust device (with fingerprint)
  - `DELETE /api/account/devices/{deviceId}/trust` — Untrust device
  - `DELETE /api/account/devices/{deviceId}` — Remove device
  - `GET /api/internal/devices/{userId}/trusted` — Internal: check if device is trusted

#### FR-017: Idempotency (account-service) [ENRICHED] [REUSE]
- **Actor**: System
- **Action**: Replicate `IdempotencyFilter.kt` pattern from auth-service.
- **Existing Code**: `IdempotencyFilter.kt` (auth-service) — pattern to replicate.
- **Enhancement**: Copy filter, configure for account-service POST endpoints.

### 3.3 System Administration (system-admin-service)

#### FR-010: Dynamic Menu Permission [URD] [EXTEND]
- **Actor**: System Administrator
- **Action**: Manage dynamic menu tree (DIRECTORY, MENU, BUTTON, API types) with role-based permission + user override.
- **Validation Rules**:
  - User override > role permission.
  - BUTTON type invisible in navigation, carries `permission_code`.
  - Redis cache TTL 5 minutes.
  - Circular reference detection (via `TreeBuilder.detectCycle()`).
  - Menu code unique per domain.
  - Max depth = 10 levels.
- **Existing Code**: `MenuPermissionService.kt`, `TreeEntity.kt`, `TreeBuilder.kt`.
- **Enhancement**: Add button-level permission, role-menu assignment CRUD, user override table, Kafka-driven cache invalidation.
- **Error Codes**: `SYS_001` (menu not found), `SYS_002` (circular reference), `SYS_003` (max depth exceeded).
- **API Endpoints**:
  - `GET /api/admin/menus/tree` — Get full menu tree
  - `GET /api/admin/menus/user-tree` — Get user-filtered menu tree (cached)
  - `POST /api/admin/menus` — Create menu item
  - `PUT /api/admin/menus/{id}` — Update menu item
  - `DELETE /api/admin/menus/{id}` — Delete menu item (cascade children)
  - `POST /api/admin/menus/{id}/permissions` — Assign role permissions
  - `POST /api/admin/menus/{id}/user-override` — Set user override

#### FR-011: Organization Management [URD] [EXTEND]
- **Actor**: System Administrator
- **Action**: Manage department tree (recursive hierarchy, max 10 levels), positions, user assignments.
- **Validation Rules**:
  - Department tree acyclic (validated by `TreeBuilder.detectCycle()`).
  - Position unique per department.
  - User transfer between departments with audit trail.
- **Existing Code**: `OrganizationService.kt`, `PositionService.kt`, `DepartmentEntity.kt`, `PositionEntity.kt`, `UserPositionEntity.kt`, `DepartmentController.kt`, `PositionController.kt`.
- **Enhancement**: Add user transfer API, org chart query (recursive CTE), department head assignment.
- **Error Codes**: `SYS_004` (department not found), `SYS_005` (position not found), `SYS_006` (cyclic hierarchy).
- **API Endpoints**:
  - `GET /api/admin/departments/tree` — Get department tree
  - `POST /api/admin/departments` — Create department
  - `PUT /api/admin/departments/{id}` — Update department
  - `DELETE /api/admin/departments/{id}` — Delete department
  - `GET /api/admin/positions` — List positions (by department)
  - `POST /api/admin/positions` — Create position
  - `POST /api/admin/departments/{id}/transfer-user` — Transfer user between departments
  - `GET /api/admin/org-chart` — Org chart (recursive CTE query)

#### FR-012: API Partner Management [URD] [EXTEND]
- **Actor**: System Administrator
- **Action**: Partner onboarding, API key lifecycle (generate, rotate, revoke), rate limiting (Bucket4j + Redis), quota tracking.
- **Validation Rules**:
  - API key show-once (Stripe pattern): format `ntt_pk_`/`ntt_sk_` + SHA-256 hash.
  - IP whitelist enforcement.
  - Max keys per partner (configurable, default 5).
  - Rate limit config persisted → pushed to Redis (Bucket4j ProxyManager).
- **Existing Code**: `ApiPartnerService.kt`, `ApiUsageController.kt`.
- **Enhancement**: Add Bucket4j rate limiting config CRUD, key lifecycle endpoints, IP whitelist, usage dashboard.
- **Error Codes**: `SYS_007` (partner not found), `SYS_008` (key already revoked), `SYS_009` (rate limit config invalid), `SYS_010` (IP not whitelisted).
- **API Endpoints**:
  - `POST /api/admin/partners` — Onboard partner
  - `POST /api/admin/partners/{id}/keys` — Generate API key (show-once)
  - `POST /api/admin/partners/{id}/keys/{keyId}/rotate` — Rotate key
  - `DELETE /api/admin/partners/{id}/keys/{keyId}` — Revoke key
  - `PUT /api/admin/partners/{id}/rate-limit` — Configure rate limit
  - `PUT /api/admin/partners/{id}/ip-whitelist` — Configure IP whitelist
  - `GET /api/admin/partners/{id}/usage` — Usage dashboard

#### FR-013: Dynamic Approval Workflow [URD] [EXTEND]
- **Actor**: System Administrator, Approver
- **Action**: Define workflow (multi-step, conditional routing), submit for approval, approve/reject/delegate, auto-escalation.
- **Validation Rules**:
  - Workflow definition versioned + immutable.
  - State machine: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED.
  - Guard conditions per transition: `canResubmit`, `canCancel`, `isEscalated`.
  - Condition DSL: PBAC PolicyCondition JSONB pattern (operators: AND, OR, GT, EQ, IN).
  - Auto-escalation: configurable timeout per step, scheduler `WorkflowEscalationScheduler.kt`.
- **Existing Code**: `WorkflowEngine.kt`, `WorkflowService.kt`, `WorkflowController.kt`, `WorkflowEntities.kt`, `WorkflowEscalationScheduler.kt`.
- **Enhancement**: Add conditional routing with DSL, delegation support, explicit guard conditions.
- **Error Codes**: `SYS_011` (workflow not found), `SYS_012` (invalid transition), `SYS_013` (step timeout), `SYS_014` (delegation failed).
- **API Endpoints**:
  - `POST /api/admin/workflows` — Create workflow definition
  - `GET /api/admin/workflows/{id}` — Get workflow definition
  - `POST /api/admin/workflows/submit` — Submit entity for approval
  - `POST /api/admin/workflows/instances/{id}/approve` — Approve step
  - `POST /api/admin/workflows/instances/{id}/reject` — Reject step
  - `POST /api/admin/workflows/instances/{id}/delegate` — Delegate to another approver
  - `GET /api/admin/workflows/instances` — List workflow instances (filterable)

#### FR-014: System Configuration [URD] [EXTEND]
- **Actor**: System Administrator
- **Action**: Manage system configs (STRING, JSON, NUMBER types) and feature flags per domain.
- **Validation Rules**:
  - Config versioning with history tracking.
  - Config value type validation.
  - Feature flags: boolean + percentage rollout + segment targeting.
  - Audit trail on all config changes.
- **Existing Code**: `DomainConfigService.kt`, `FeatureFlagService.kt`, `FeatureFlagEntity.kt`.
- **Enhancement**: Add config versioning, type validation, enhanced feature flag targeting.
- **Error Codes**: `SYS_015` (config not found), `SYS_016` (invalid config type).
- **API Endpoints**:
  - `GET /api/admin/config` — List configs (by domain)
  - `PUT /api/admin/config/{key}` — Update config (versioned)
  - `GET /api/admin/config/{key}/history` — Config change history
  - `GET /api/admin/feature-flags` — List feature flags
  - `PUT /api/admin/feature-flags/{id}` — Update feature flag

#### FR-015: Audit Trail [URD] [EXTEND]
- **Actor**: System
- **Action**: Record all admin operations (create/update/delete) to immutable audit log with old_value/new_value JSONB diff.
- **Validation Rules**:
  - Database-level: NO UPDATE/DELETE on audit_logs table (DB rule/trigger).
  - Sensitive data masking (password, API key → `***`).
  - Search by actor, entity type, date range.
  - Export: CSV/JSON format.
- **Existing Code**: auth-service: `AuditLogService.kt`; system-admin-service: `AuditAspect.kt`, `AuditService.kt`, `AuditLogEntity.kt`, `AuditController.kt`.
- **Enhancement**: Add immutability DB constraints, sensitive data masking, export endpoint.
- **Error Codes**: `SYS_017` (audit log not found), `AUTH_053` (audit export failed).
- **API Endpoints**:
  - `GET /api/admin/audit-logs` — Search audit logs (paginated, filterable)
  - `GET /api/admin/audit-logs/export` — Export audit logs (CSV/JSON)

#### FR-016: Domain/Tenant Configuration [URD] [EXTEND]
- **Actor**: System Administrator
- **Action**: Advanced per-domain config (branding, login page config, session policy).
- **Validation Rules**:
  - Data isolation by domain.
  - Cache config with TTL.
- **Existing Code**: `DomainEntity.kt` (rbac), `DomainLookupService.kt` (auth), `DomainConfigService.kt` (system-admin).
- **Enhancement**: Add branding fields (logo_url, primary_color, login_page_config JSONB).
- **API Endpoints**:
  - `GET /api/admin/domains/{domainCode}/config` — Get domain config
  - `PUT /api/admin/domains/{domainCode}/config` — Update domain config
  - `PUT /api/admin/domains/{domainCode}/branding` — Update branding

#### FR-017: Idempotency (system-admin-service) [ENRICHED] [REUSE]
- **Actor**: System
- **Action**: Replicate `IdempotencyFilter.kt` pattern from auth-service.
- **Enhancement**: Copy filter, configure for system-admin-service POST endpoints.

### 3.4 Infrastructure (Cross-cutting)

#### FR-018: Transaction Logging [ENRICHED] [REUSE]
- **Actor**: System
- **Action**: Log all API request/response with structured JSON, correlation ID tracing.
- **Existing Code**: `HttpLoggingFilter` (base-core common-log) — already available via dependency.
- **Classification**: REUSE — no changes needed.

---

## 4. Non-Functional Requirements

| Category | Requirement | Target |
|----------|------------|--------|
| Performance | API response time (P95) | < 500ms |
| Performance | Menu tree load (cached) | < 100ms |
| Performance | Permission check (cached) | < 50ms |
| Performance | Rate limit check | < 10ms |
| Security | Password hashing | Argon2id |
| Security | API key storage | SHA-256 hash |
| Security | TOTP secret | AES-256 encrypted |
| Security | Audit log | Immutable (no UPDATE/DELETE) |
| Caching | L1 (Caffeine) TTL | 30s |
| Caching | L2 (Redis) TTL | 5-30 min |
| Caching | Cache hit ratio | > 80% |
| Architecture | Pattern | Clean Architecture (Hexagonal) |
| Architecture | DB per service | Separate PostgreSQL |
| Messaging | Event bus | Kafka (async state changes) |
| Messaging | Fallback | Spring ApplicationEvent |
| Data | ID generation | Snowflake 64-bit Long |
| Data | Migrations | Flyway |
| Scalability | Concurrent users per service | 1000+ |

---

## 5. External Dependencies

| System | Protocol | Purpose | Existing Code |
|--------|----------|---------|---------------|
| Redis | Spring Data Redis | Cache, state store, rate limiting | `RedisConfig.kt`, `AbstractTwoTierCache.kt` |
| Kafka | Spring Kafka | Async inter-service events | `PermissionChangedConsumer.kt`, `EventPublisher.kt` |
| PostgreSQL | JPA + Flyway | Persistence (separate DB/service) | `UserPersistenceAdapter.kt` |
| Keycloak (optional) | OAuth2/OIDC | SSO delegation | `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt` |
| CAPTCHA Provider | HTTP REST | Bot protection | `CaptchaClient.kt`, `HttpCaptchaGateway.kt` |
| Google Tink + AWS KMS | Library | E2EE | `TinkCipherAlgorithmFactory.kt` |

---

## 6. Glossary

| Term | Definition |
|------|-----------|
| Lean JWT | JWT containing only userId, tenantId, roleIds — permissions resolved at runtime |
| Partial JWT | JWT issued after password verify but before MFA — limited scope |
| Recovery Code | Single-use backup code for MFA when TOTP device unavailable |
| Show-once | API key displayed only at creation — stored as SHA-256 hash |
| Bucket4j | Token bucket rate limiting library for Java/Kotlin |
| DLT | Dead Letter Topic — Kafka topic for failed events |
| PolicyCondition DSL | JSONB-based condition expression format from PBAC module |
| TreeEntity | Base entity for hierarchical data (menu, department) in system-admin-service |
| AbstractTwoTierCache | Cache pattern: Caffeine L1 (in-process, 30s) + Redis L2 (distributed, 5-30min) |
