# SRS: erp-iam-system

_Generated: 2026-08-24_
_Profile: Non-Financial | N/A (no factory) | NEWBUILD_

---

## 1. System Context

- **Feature Name**: ERP IAM System
- **Domain**: IAM (Identity & Access Management), System Administration, Account Management
- **Flow**: Non-Financial
- **Services**: auth-service (existing, EXTEND), account-service (NEWBUILD + EXTEND), system-admin-service (NEWBUILD + EXTEND)
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

#### FR-001: Quản lý Multi-Factor Authentication [URD]
- **Actor**: End User
- **Precondition**: User đã đăng nhập hoặc đang trong MFA setup flow.
- **Action**: Hệ thống hỗ trợ enable/disable MFA per user với phương thức OTP (SMS, Email), TOTP (app-based), CAPTCHA.
- **Validation Rules**:
  - OTP expires sau 5 phút, tối đa 3 lần sai → lock 30 phút.
  - TOTP window ±1 step (30s).
  - Recovery codes single-use, 10 codes generated on MFA setup.
- **Error Codes**: `AUTH_011` (MFA code invalid), `AUTH_012` (MFA expired), `AUTH_013` (max attempts), `AUTH_019` (rate limited).
- **Existing Code**: `MfaService.kt`, `TotpService.kt`, `OtpService.kt`, `MfaController.kt`, `MfaRateLimitService.kt`.
- **Classification**: EXTEND — add recovery codes UI flow, MFA method management.

#### FR-002: Progressive MFA Login Flow [URD]
- **Actor**: End User
- **Precondition**: MFA enabled for user.
- **Action**: Progressive authentication: password verify → partial JWT (scope: `/verify-2fa` only) → MFA verify → full JWT.
- **Validation Rules**:
  - Partial token TTL = 5 phút.
  - Trusted device skip MFA (30 days TTL).
  - Device fingerprint tracked per login.
- **Error Codes**: `AUTH_001` (invalid credentials), `AUTH_002` (locked), `AUTH_003` (token expired).
- **Existing Code**: `LoginHandler.kt`, `AuthService.kt`, `AuthController.kt`, `CqrsAuthController.kt`.
- **Classification**: EXTEND — enhance trusted device flow.

#### FR-003: SSO Integration [URD]
- **Actor**: End User
- **Precondition**: Domain has SSO provider configured.
- **Action**: OAuth2/OIDC login via external IdP (Google, Microsoft, Keycloak) với auto-provision user nếu domain config cho phép.
- **Validation Rules**:
  - SSO callback verify token.
  - Auto-link external account to existing user (email match).
  - Keycloak optional downstream delegation.
- **Error Codes**: `AUTH_014` (SSO token invalid), `AUTH_015` (not provisioned), `AUTH_016` (identity conflict).
- **Existing Code**: `SsoAdapter.kt`, `SsoController.kt`, `OAuth2TokenExchanger.kt`, `HttpSsoGateway.kt`.
- **Classification**: EXTEND — enhance auto-provision with Kafka event publishing.

#### FR-004: Password Policy [URD]
- **Actor**: System Administrator, Domain Admin
- **Action**: Cấu hình password rules per domain: min length, uppercase, lowercase, digit, special char, max age (days), history count.
- **Validation Rules**:
  - Không reuse N passwords gần nhất (configurable per domain).
  - Force change khi expired (grace login redirect to change-password).
- **Error Codes**: `AUTH_017` (policy violation), `AUTH_018` (expired).
- **Existing Code**: `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`.
- **Classification**: Existing — minor config enhancements only.

### 3.2 Account Management (account-service)

#### FR-005: Quản lý User Profile [URD]
- **Actor**: End User
- **Precondition**: User authenticated.
- **Action**: CRUD thông tin cá nhân: display name, first/last name, DOB, address, timezone, locale, avatar URL.
- **Validation Rules**:
  - Đổi email/phone cần verification flow (OTP to new email/phone).
  - Avatar upload via separate file service (URL reference only).
  - Profile tách biệt khỏi auth data — separate DB.
- **Error Codes**: `ACCT_001` (profile not found), `ACCT_002` (validation failed), `ACCT_003` (verification required).
- **Existing Code**: `ProfileKafkaListener.kt` (listener only — no CRUD).
- **Classification**: NEWBUILD — full CRUD service + controller.

#### FR-006: Quản lý Preferences & Settings [URD]
- **Actor**: End User
- **Action**: Lưu cài đặt UI, notification, privacy preferences dưới dạng key-value grouped by category.
- **Validation Rules**:
  - Category format: alphanumeric + underscore, max 50 chars.
  - Key format: alphanumeric + dot notation, max 100 chars.
  - Merge-update semantics (PATCH = partial update, không overwrite unmentioned keys).
- **Error Codes**: `ACCT_004` (invalid category/key format).
- **Existing Code**: NOT DETECTED.
- **Classification**: NEWBUILD.

#### FR-007: Quản lý Device [URD]
- **Actor**: End User
- **Action**: Track thiết bị đăng nhập, trust device (skip MFA 30 days), remote logout single device.
- **Validation Rules**:
  - Device fingerprint unique per user.
  - Trusted device TTL = 30 days (configurable).
  - Remote logout → terminate session + revoke refresh token.
  - Max trusted devices per user = 5 (configurable).
- **Error Codes**: `ACCT_005` (device not found), `ACCT_006` (max devices reached).
- **Existing Code**: NOT DETECTED.
- **Classification**: NEWBUILD.

#### FR-008: Quản lý Session Nâng Cao [URD]
- **Actor**: End User, System Administrator
- **Action**: Track active sessions, cấu hình max concurrent sessions per user, terminate specific session.
- **Validation Rules**:
  - Auto-expire session khi inactive (configurable TTL).
  - Oldest session terminated khi vượt max concurrent.
  - Admin có thể terminate any user session.
- **Error Codes**: `AUTH_021` (session limit exceeded).
- **Existing Code**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt`, `AdminSessionController.kt`.
- **Classification**: Existing — auth-service foundation complete.

#### FR-009: Account Lifecycle (GDPR) [URD]
- **Actor**: End User
- **Action**: Deactivate account, request deletion (GDPR right to be forgotten), export personal data.
- **Validation Rules**:
  - Deletion request grace period = 30 days (configurable).
  - Export format = JSON.
  - Immutable audit trail for lifecycle actions.
  - Deactivation → terminate all sessions + revoke tokens.
- **Error Codes**: `ACCT_007` (already deactivated), `ACCT_008` (deletion pending).
- **Existing Code**: `AccountLifecycleService.kt`, `AccountLifecycleController.kt` (auth-service).
- **Classification**: EXTEND — enhance GDPR data export, add cross-service event for account deactivation.

### 3.3 System Administration (system-admin-service)

#### FR-010: Phân Quyền Menu Động [URD]
- **Actor**: System Administrator
- **Action**: Dynamic menu tree (types: DIRECTORY, MENU, BUTTON, API) với role-based permission + user override. Frontend nhận filtered menu tree per user.
- **Validation Rules**:
  - User override > role permission (user can be granted/denied specific menu items regardless of role).
  - BUTTON type invisible in navigation (visible only as action within MENU).
  - Cache Redis TTL = 5 phút.
  - Circular reference detection (tree must be acyclic).
  - Menu code unique per domain.
  - Max tree depth = 10 levels.
- **Error Codes**: `SYS_001` (menu not found), `SYS_002` (circular reference), `SYS_003` (duplicate code).
- **Existing Code**: `MenuPermissionService.kt`, `MenuEntities.kt` (4 entities), `MenuPermissionCacheAdapter.kt`, `MenuController.kt` (brainstorm confirmed).
- **Classification**: EXTEND — add circular ref detection, validation enhancements.

#### FR-011: Quản lý Cơ Cấu Tổ Chức [URD]
- **Actor**: System Administrator
- **Action**: Department tree (recursive hierarchy, max 10 levels) + position management. Assign users to positions.
- **Validation Rules**:
  - Department tree acyclic (DFS/BFS cycle detection before save).
  - Position unique per department.
  - Transfer user between departments preserves history.
  - Max depth = 10 levels (configurable).
- **Error Codes**: `SYS_004` (department not found), `SYS_005` (circular hierarchy), `SYS_006` (position duplicate).
- **Existing Code**: NOT DETECTED.
- **Classification**: NEWBUILD.

#### FR-012: Quản lý API Partner [URD]
- **Actor**: System Administrator
- **Action**: Partner onboarding, API key lifecycle (generate, rotate, revoke), rate limiting (Bucket4j + Redis), quota tracking.
- **Validation Rules**:
  - API key show-once (Stripe pattern): `ntt_pk_` (publishable) / `ntt_sk_` (secret) + SHA-256 hash.
  - IP whitelist enforcement (optional per partner).
  - Max keys per partner = 5 (configurable).
  - Rate limit sync to Redis (Bucket4j ProxyManager).
  - Key rotation: new key → old key grace period = 24h.
- **Error Codes**: `SYS_007` (partner not found), `SYS_008` (key revoked), `SYS_009` (rate limit exceeded), `SYS_010` (IP not whitelisted).
- **Existing Code**: `ApiKeyService.kt`, `ApiPartnerEntities.kt` (5 entities), `ApiPartnerController.kt`, `ApiUsageController.kt` (brainstorm confirmed).
- **Classification**: EXTEND — add usage dashboard, IP whitelist enforcement.

#### FR-013: Dynamic Approval Workflow [URD]
- **Actor**: System Administrator, Approver
- **Action**: Define workflow (multi-step, conditional routing via JSONB DSL), submit entity for approval, approve/reject/delegate, auto-escalation on timeout.
- **Validation Rules**:
  - Workflow definition immutable after activation (versioned).
  - State machine: PENDING → IN_PROGRESS → APPROVED | REJECTED | ESCALATED | CANCELLED.
  - Guard conditions per transition: `canResubmit`, `canCancel`, `isEscalated`.
  - Condition DSL reuses PBAC PolicyCondition pattern:
    ```json
    {"operator": "AND", "conditions": [{"field": "amount", "op": "GT", "value": 10000}]}
    ```
  - Auto-escalation timeout configurable per step (default 48h).
  - Delegation: approver can delegate to another user (one level).
- **Error Codes**: `SYS_011` (workflow not found), `SYS_012` (invalid transition), `SYS_013` (already processed), `SYS_014` (escalation timeout).
- **Existing Code**: NOT DETECTED.
- **Classification**: NEWBUILD.

#### FR-014: Quản lý Cấu Hình Hệ Thống [URD]
- **Actor**: System Administrator
- **Action**: System configs (STRING, JSON, NUMBER types) + feature flags per domain.
- **Validation Rules**:
  - Config versioning (audit trail on changes).
  - Feature flags: boolean, percentage rollout, user segment targeting.
  - Config keys unique per domain.
- **Error Codes**: `SYS_015` (config key not found), `SYS_016` (invalid config type).
- **Existing Code**: `DomainConfigService.kt`, entities, history tracking (brainstorm confirmed).
- **Classification**: EXTEND — add feature flags.

#### FR-015: Audit Trail Toàn Diện [URD]
- **Actor**: System
- **Action**: Record all admin CRUD operations into immutable audit log with old_value/new_value JSONB diff.
- **Validation Rules**:
  - No UPDATE/DELETE on audit_logs table (immutable — DB constraint).
  - Sensitive data masking (password, API key, tokens).
  - JSON diff format: `{"field": {"old": X, "new": Y}}`.
  - Search by: action, entity_type, user_id, date range.
  - Export: CSV, JSON formats.
- **Error Codes**: `SYS_017` (audit log query failed).
- **Existing Code**: `AuditLogService.kt` (auth-service — log only, no DB persist).
- **Classification**: EXTEND (auth-service audit) + NEWBUILD (system-admin audit module).

#### FR-016: Domain/Tenant Config [URD]
- **Actor**: System Administrator
- **Action**: Cấu hình nâng cao per domain: branding, login page config, session policy.
- **Validation Rules**:
  - Tách biệt dữ liệu theo domain.
  - Cache config with TTL.
- **Error Codes**: Reuse `AUTH_005` (not found).
- **Existing Code**: `DomainEntity.kt`, `DomainLookupService.kt`.
- **Classification**: EXTEND.

### 3.4 Infrastructure (Cross-service)

#### FR-017: Idempotency cho Create/Update Operations [ENRICHED]
- **Actor**: System
- **Action**: Idempotency cho mọi API create/update via `X-Idempotency-Key` header.
- **Validation Rules**:
  - Duplicate request trả kết quả cached.
  - Key TTL = 24h trong Redis.
  - Key format: UUID v4.
- **Error Codes**: None (transparent — returns cached response).
- **Existing Code**: NOT DETECTED.
- **Classification**: NEWBUILD — IdempotencyFilter + Redis store.

#### FR-018: Transaction Logging Toàn Diện [ENRICHED]
- **Actor**: System
- **Action**: Log toàn bộ API request/response (structured JSON, correlation ID, sensitive field masking).
- **Validation Rules**:
  - Log rotation configured.
  - Correlation ID via MDC (`X-Correlation-Id` header).
  - Mask: password, token, API key, OTP.
- **Existing Code**: `HttpLoggingFilter` (base-core `common-log`).
- **Classification**: REUSE — configure + extend masking rules.

#### FR-019: Timeout Handling cho External API [ENRICHED]
- **Actor**: System
- **Action**: Configurable timeout cho external API calls (SSO, CAPTCHA) + circuit breaker.
- **Validation Rules**:
  - Default timeout = 10s (configurable per client).
  - Circuit breaker: open after 5 consecutive failures, half-open after 30s.
  - Fail-open pattern for non-critical calls (e.g., CAPTCHA degraded mode).
- **Existing Code**: `HttpClientConfig.kt`, `MfaRateLimitService.kt` (fail-open pattern).
- **Classification**: EXTEND.

#### FR-020: Retry Mechanism cho Failed Calls [ENRICHED]
- **Actor**: System
- **Action**: Retry cho Kafka event publishing + external API calls (exponential backoff).
- **Validation Rules**:
  - Max retries = 3.
  - Backoff multiplier = 2 (1s, 2s, 4s).
  - Dead-letter queue cho Kafka (`.DLT` topic suffix).
  - Idempotent consumers (dedup by event ID).
- **Existing Code**: `SpringEventPublisher.kt` (stub), `PermissionChangedConsumer.kt`.
- **Classification**: EXTEND.

#### FR-021: Request Authentication & Encryption [ENRICHED]
- **Actor**: System
- **Action**: Inter-service communication security via service-level JWT.
- **Validation Rules**:
  - Internal endpoints: `/api/internal/**` require service JWT.
  - Service JWT issued at startup from shared secret (Phase 1) or service account (Phase 4).
  - E2EE already handled for client-facing by existing TinkCipherAlgorithmFactory.
- **Existing Code**: `JwtService.kt` (can be extended for service tokens).
- **Classification**: NEWBUILD — ServiceAuthFilter + service JWT generation.

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
| Security | TOTP secret storage | AES-256 encrypted |
| Security | Audit log | Immutable (no UPDATE/DELETE DB constraint) |
| Caching | Strategy | Caffeine L1 (30s TTL) + Redis L2 (5-30min TTL) |
| Caching | Hit ratio target | > 80% |
| Architecture | Pattern | Clean Architecture (Hexagonal) |
| Architecture | Services | 3 with separate PostgreSQL DB per service |
| Messaging | Inter-service | Kafka async events |
| Messaging | Fallback | Spring ApplicationEvent (Phase 1-2) |
| Data | ID generation | Snowflake 64-bit Long |
| Data | Migrations | Flyway |
| Scalability | Concurrent users per service | 1000+ |
| Scalability | Horizontal scaling | Stateless services + Redis shared state |

---

## 5. Data Entities

### auth-service (Existing — EXTEND)
- `UserEntity`, `UserIdentityEntity`, `DomainEntity`, `UserDomainEntity`
- `GroupEntity`, `UserGroupEntity`, `DomainRoleEntity`, `GroupRoleEntity`
- `DomainResourceEntity`, `RolePermissionEntity`, `PermissionEntity`, `ActionEntity`
- `PolicyEntity`, `PolicyConditionEntity`
- `LoginSessionEntity`, `PasswordPolicyEntity`, `PasswordHistoryEntity`
- `AccountDeletionRequestEntity`, `AccountDataExportEntity`
- `CipherKeySessionEntity`, `VaultAccessLogEntity`
- `I18nMessageEntity`
- [NEW] `ServiceTokenEntity` (inter-service JWT tracking)

### account-service (NEWBUILD)
- [NEW] `UserProfileEntity` — display name, first/last name, DOB, address, timezone, locale, avatar
- [NEW] `UserContactEntity` — email, phone with verification status
- [NEW] `UserPreferenceEntity` — category + key-value settings
- [NEW] `UserDeviceEntity` — device fingerprint, trusted status, last used
- [NEW] `ActiveSessionEntity` (Redis-backed) — session tracking
- [EXISTING] `AccountDeletionRequestEntity`, `AccountDataExportEntity` (per brainstorm: lifecycle in account-service)

### system-admin-service (NEWBUILD + EXTEND)
- [EXISTING] `MenuItemEntity` — menu tree node (DIRECTORY, MENU, BUTTON, API types)
- [EXISTING] `MenuPermissionEntity` — role → menu item permission
- [EXISTING] `RoleMenuPermissionEntity` — role-based menu assignment
- [EXISTING] `UserMenuOverrideEntity` — per-user menu override
- [NEW] `DepartmentEntity` — organization tree node (parent_id, level, sort_order)
- [NEW] `PositionEntity` — position within department
- [NEW] `UserPositionEntity` — user ↔ position assignment
- [EXISTING] `ApiPartnerEntity` — partner profile
- [EXISTING] `ApiKeyEntity` — API key (SHA-256 hash, status, rate limit config)
- [EXISTING] `SubscriptionPlanEntity` — partner subscription tier
- [EXISTING] `ApiUsageLogEntity` — API usage tracking
- [EXISTING] `ApiIpWhitelistEntity` — IP whitelist per partner
- [NEW] `WorkflowDefinitionEntity` — workflow template (versioned, JSONB conditions)
- [NEW] `WorkflowStepEntity` — step definition within workflow
- [NEW] `WorkflowInstanceEntity` — active workflow instance (state machine)
- [NEW] `WorkflowActionEntity` — approval/reject/delegate action log
- [EXISTING] `DomainConfigEntity` — system config per domain
- [EXISTING] `DomainConfigHistoryEntity` — config change history
- [NEW] `FeatureFlagEntity` — feature flag per domain
- [NEW] `AuditLogEntity` — immutable audit log (old_value_json, new_value_json)

---

## 6. API Endpoints

### auth-service (Existing + Extend)

| Method | Path | Description | FR |
|--------|------|------------|-----|
| POST | `/api/auth/login` | Authenticate (partial JWT if MFA) | FR-002 |
| POST | `/api/auth/verify-2fa` | Submit OTP/TOTP for full JWT | FR-001 |
| POST | `/api/auth/register` | Register new user | FR-002 |
| POST | `/api/auth/refresh` | Refresh token | FR-002 |
| GET | `/api/auth/introspect` | Token introspection | FR-002 |
| POST | `/api/auth/mfa/setup` | Setup MFA (TOTP/OTP) | FR-001 |
| DELETE | `/api/auth/mfa/disable` | Disable MFA | FR-001 |
| GET | `/api/auth/mfa/recovery-codes` | Generate/view recovery codes | FR-001 |
| GET | `/api/auth/sso/authorize/{provider}` | Initiate SSO flow | FR-003 |
| POST | `/api/auth/sso/callback` | SSO callback handler | FR-003 |
| GET | `/api/auth/sessions` | List user sessions | FR-008 |
| DELETE | `/api/auth/sessions/{id}` | Terminate specific session | FR-008 |
| PUT | `/api/auth/password` | Change password | FR-004 |
| POST | `/api/auth/lifecycle/deactivate` | Deactivate account | FR-009 |
| POST | `/api/auth/lifecycle/delete` | Request account deletion | FR-009 |
| GET | `/api/auth/lifecycle/export` | Export personal data | FR-009 |
| GET | `/api/internal/users/{id}/roles` | [INTERNAL] User roles for system-admin | FR-021 |
| POST | `/api/internal/service-token` | [INTERNAL] Issue service JWT | FR-021 |

### account-service (NEWBUILD)

| Method | Path | Description | FR |
|--------|------|------------|-----|
| GET | `/api/account/profile` | Get user profile | FR-005 |
| PUT | `/api/account/profile` | Update profile | FR-005 |
| POST | `/api/account/profile/verify-email` | Verify new email | FR-005 |
| POST | `/api/account/profile/verify-phone` | Verify new phone | FR-005 |
| GET | `/api/account/preferences` | Get all preferences | FR-006 |
| GET | `/api/account/preferences/{category}` | Get preferences by category | FR-006 |
| PATCH | `/api/account/preferences/{category}` | Update preferences (merge) | FR-006 |
| GET | `/api/account/devices` | List devices | FR-007 |
| POST | `/api/account/devices/{id}/trust` | Trust device | FR-007 |
| DELETE | `/api/account/devices/{id}` | Remove device (remote logout) | FR-007 |
| GET | `/api/account/sessions` | List active sessions | FR-008 |
| DELETE | `/api/account/sessions/{id}` | Terminate session | FR-008 |

### system-admin-service (NEWBUILD + EXTEND)

| Method | Path | Description | FR |
|--------|------|------------|-----|
| GET | `/api/admin/menus/tree` | Full menu tree (admin) | FR-010 |
| GET | `/api/admin/menus/user-tree` | User-filtered menu tree | FR-010 |
| POST | `/api/admin/menus` | Create menu item | FR-010 |
| PUT | `/api/admin/menus/{id}` | Update menu item | FR-010 |
| DELETE | `/api/admin/menus/{id}` | Delete menu item | FR-010 |
| POST | `/api/admin/menus/{id}/permissions` | Set menu permissions | FR-010 |
| GET | `/api/admin/departments/tree` | Department tree | FR-011 |
| POST | `/api/admin/departments` | Create department | FR-011 |
| PUT | `/api/admin/departments/{id}` | Update department | FR-011 |
| DELETE | `/api/admin/departments/{id}` | Delete department | FR-011 |
| GET | `/api/admin/departments/{id}/positions` | List positions | FR-011 |
| POST | `/api/admin/positions` | Create position | FR-011 |
| PUT | `/api/admin/positions/{id}` | Update position | FR-011 |
| POST | `/api/admin/positions/{id}/assign` | Assign user to position | FR-011 |
| GET | `/api/admin/api-partners` | List partners | FR-012 |
| POST | `/api/admin/api-partners` | Register partner | FR-012 |
| PUT | `/api/admin/api-partners/{id}` | Update partner | FR-012 |
| POST | `/api/admin/api-keys` | Generate API key | FR-012 |
| DELETE | `/api/admin/api-keys/{id}` | Revoke API key | FR-012 |
| POST | `/api/admin/api-keys/{id}/rotate` | Rotate API key | FR-012 |
| GET | `/api/admin/api-usage` | API usage dashboard | FR-012 |
| GET | `/api/admin/workflows` | List workflow definitions | FR-013 |
| POST | `/api/admin/workflows` | Create workflow definition | FR-013 |
| GET | `/api/admin/workflows/instances` | List workflow instances | FR-013 |
| POST | `/api/admin/workflows/instances` | Submit for approval | FR-013 |
| POST | `/api/admin/workflows/instances/{id}/approve` | Approve step | FR-013 |
| POST | `/api/admin/workflows/instances/{id}/reject` | Reject step | FR-013 |
| POST | `/api/admin/workflows/instances/{id}/delegate` | Delegate step | FR-013 |
| GET | `/api/admin/configs` | List system configs | FR-014 |
| PUT | `/api/admin/configs/{key}` | Update config | FR-014 |
| GET | `/api/admin/feature-flags` | List feature flags | FR-014 |
| PUT | `/api/admin/feature-flags/{key}` | Toggle feature flag | FR-014 |
| GET | `/api/admin/audit-logs` | Search audit logs | FR-015 |
| GET | `/api/admin/audit-logs/export` | Export audit logs | FR-015 |
| GET | `/api/admin/domains/{id}/config` | Get domain config | FR-016 |
| PUT | `/api/admin/domains/{id}/config` | Update domain config | FR-016 |

---

## 7. Events (Kafka Topics)

| Topic | Producer | Consumer | Payload |
|-------|----------|----------|---------|
| `iam.user.registered` | auth-service | account-service | `{userId, username, domainCode}` |
| `iam.user.sso_provisioned` | auth-service | account-service | `{userId, provider, email, domainCode}` |
| `iam.permission.changed` | system-admin-service | auth-service | `{userId?, domainId?}` |
| `iam.audit.log` | all services | system-admin-service (optional) | `{action, userId, entityType, entityId, details}` |
| `acct.lifecycle.deactivated` | account-service | auth-service | `{userId, reason}` |
| `acct.lifecycle.deleted` | account-service | auth-service | `{userId}` |

---

## 8. Dependencies

| Dependency | Purpose | Status |
|-----------|---------|--------|
| Redis | Cache, state store, rate limiting, sessions, OTP | Active |
| Kafka | Event streaming (inter-service) | compileOnly → implementation (Phase 4) |
| PostgreSQL | Persistent storage (separate DB per service) | Active |
| Keycloak | Optional SSO/OIDC delegation | Active (via SsoAdapter) |
| CAPTCHA Provider | Bot protection | Active (via CaptchaClient) |
| Google Tink + AWS KMS | E2EE | Active |
| Bucket4j | Rate limiting (Gateway + Redis) | NEW dependency |

---

## 9. FR Traceability

| FR-ID | SRS Section | Status |
|-------|------------|--------|
| FR-001 | 3.1 | ✅ Covered |
| FR-002 | 3.1 | ✅ Covered |
| FR-003 | 3.1 | ✅ Covered |
| FR-004 | 3.1 | ✅ Covered |
| FR-005 | 3.2 | ✅ Covered |
| FR-006 | 3.2 | ✅ Covered |
| FR-007 | 3.2 | ✅ Covered |
| FR-008 | 3.2 | ✅ Covered |
| FR-009 | 3.2 | ✅ Covered |
| FR-010 | 3.3 | ✅ Covered |
| FR-011 | 3.3 | ✅ Covered |
| FR-012 | 3.3 | ✅ Covered |
| FR-013 | 3.3 | ✅ Covered |
| FR-014 | 3.3 | ✅ Covered |
| FR-015 | 3.3 | ✅ Covered |
| FR-016 | 3.3 | ✅ Covered |
| FR-017 | 3.4 | ✅ Covered |
| FR-018 | 3.4 | ✅ Covered |
| FR-019 | 3.4 | ✅ Covered |
| FR-020 | 3.4 | ✅ Covered |
| FR-021 | 3.4 | ✅ Covered |

**Total: 21/21 FR covered.**
