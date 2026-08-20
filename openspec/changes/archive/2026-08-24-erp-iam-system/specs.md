# Specs: erp-iam-system

_Generated: 2026-08-24_
_Profile: Non-Financial | N/A (no factory) | NEWBUILD_

---

## 1. API Specifications

### 1.1 auth-service APIs

#### Authentication

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| POST | `/api/auth/login` | Public | Login (partial JWT if MFA) | FR-002 | `LoginRequestDto` | `AuthResponse` |
| POST | `/api/auth/verify-2fa` | Partial JWT | Submit OTP/TOTP | FR-001 | `{code, type, trustedDeviceHash?}` | `AuthResponse` |
| POST | `/api/auth/register` | Public | Register new user | FR-002 | `RegisterRequestDto` | `AuthResponse` |
| POST | `/api/auth/refresh` | Public | Refresh token | FR-002 | `{refreshToken}` | `AuthResponse` |
| GET | `/api/auth/introspect` | Bearer JWT | Token introspection | FR-002 | — | `{active, userId, roles, exp}` |

#### MFA Management

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| POST | `/api/auth/mfa/setup/totp` | Bearer JWT | Setup TOTP | FR-001 | — | `{secret, qrCodeUrl, recoveryCodes[]}` |
| POST | `/api/auth/mfa/setup/otp` | Bearer JWT | Enable OTP (SMS/Email) | FR-001 | `{method: SMS|EMAIL}` | `{message}` |
| DELETE | `/api/auth/mfa/disable` | Bearer JWT | Disable MFA | FR-001 | `{code}` (verify before disable) | `{message}` |
| GET | `/api/auth/mfa/recovery-codes` | Bearer JWT | View recovery codes | FR-001 | — | `{codes[]}` |
| POST | `/api/auth/mfa/recovery-codes/regenerate` | Bearer JWT | Regenerate codes | FR-001 | `{code}` (verify) | `{codes[]}` |

#### SSO

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/auth/sso/authorize/{provider}` | Public | Initiate SSO redirect | FR-003 | Query: `domainCode` | 302 Redirect |
| POST | `/api/auth/sso/callback` | Public | SSO callback | FR-003 | `{code, state, provider}` | `AuthResponse` |
| GET | `/api/auth/sso/linked-accounts` | Bearer JWT | List linked SSO accounts | FR-003 | — | `[{provider, email, linkedAt}]` |
| DELETE | `/api/auth/sso/linked-accounts/{provider}` | Bearer JWT | Unlink SSO account | FR-003 | — | `{message}` |

#### Password

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| PUT | `/api/auth/password` | Bearer JWT | Change password | FR-004 | `{currentPassword, newPassword}` | `{messhone/confirm` | Bearer JWT | Confirm phone | FR-005 | `{code}` | `{message}` |

#### Preferences

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/account/preferences` | Bearer JWT | Get all preferences | FR-006 | — | `{categories: {category: {key: value}}}` |
| GET | `/api/account/preferences/{category}` | Bearer JWT | Get by category | FR-006 | — | `{key: value}` |
| PATCH | `/api/account/preferences/{category}` | Bearer JWT | Merge update | FR-006 | `{key: value, ...}` | `{key: value}` |
| DELETE | `/api/account/preferences/{category}/{key}` | Bearer JWT | Delete preference | FR-006 | — | `{message}` |

#### Device Management

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/account/devices` | Bearer JWT | List devices | FR-007 | — | `[DeviceResponse]` |
| GET | `/api/account/devices/{id}` | Bearer JWT | Device detail | FR-007 | — | `DeviceResponse` |
| POST | `/api/account/devices/{id}/trust` | Bearer JWT | Trust device (skip MFA 30d) | FR-007 | — | `{trustedUntil}` |
| DELETE | `/api/account/devices/{id}/trust` | Bearer JWT | Untrust device | FR-007 | — | `{message}` |
| DELETE | `/api/account/devices/{id}` | Bearer JWT | Remove device (remote logout) | FR-007 | — | `{message}` |

#### Sessions

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/account/sessions` | Bearer JWT | List active sessions | FR-008 | — | `[SessionResponse]` |
| DELETE | `/api/account/sessions/{id}` | Bearer JWT | Terminate session | FR-008 | — | `{message}` |

### 1.3 system-admin-service APIs

#### Menu Permission

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/menus/tree` | Bearer JWT (Admin) | Full menu tree | FR-010 | Query: `domainId` | `[MenuTreeNode]` |
| GET | `/api/admin/menus/user-tree` | Bearer JWT | User-filtered tree | FR-010 | — | `[MenuTreeNode]` |
| POST | `/api/admin/menus` | Bearer JWT (Admin) | Create menu item | FR-010 | `CreateMenuRequest` | `MenuResponse` |
| PUT | `/api/admin/menus/{id}` | Bearer JWT (Admin) | Update menu item | FR-010 | `UpdateMenuRequest` | `MenuResponse` |
| DELETE | `/api/admin/menus/{id}` | Bearer JWT (Admin) | Delete menu item | FR-010 | — | `{message}` |
| PUT | `/api/admin/menus/{id}/move` | Bearer JWT (Admin) | Move in tree | FR-010 | `{parentId, sortOrder}` | `MenuResponse` |
| POST | `/api/admin/menus/permissions` | Bearer JWT (Admin) | Set role permissions | FR-010 | `{roleId, menuIds[], action}` | `{message}` |
| POST | `/api/admin/menus/user-overrides` | Bearer JWT (Admin) | Set user override | FR-010 | `{userId, menuId, granted}` | `{message}` |

#### Organization

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/departments/tree` | Bearer JWT (Admin) | Department tree | FR-011 | Query: `domainId` | `[DeptTreeNode]` |
| POST | `/api/admin/departments` | Bearer JWT (Admin) | Create department | FR-011 | `CreateDepartmentRequest` | `DepartmentResponse` |
| PUT | `/api/admin/departments/{id}` | Bearer JWT (Admin) | Update department | FR-011 | `UpdateDepartmentRequest` | `DepartmentResponse` |
| DELETE | `/api/admin/departments/{id}` | Bearer JWT (Admin) | Delete department | FR-011 | — | `{message}` |
| PUT | `/api/admin/departments/{id}/move` | Bearer JWT (Admin) | Move in tree | FR-011 | `{parentId, sortOrder}` | `DepartmentResponse` |
| GET | `/api/admin/departments/{id}/positions` | Bearer JWT (Admin) | List positions | FR-011 | — | `[PositionResponse]` |
| POST | `/api/admin/positions` | Bearer JWT (Admin) | Create position | FR-011 | `CreatePositionRequest` | `PositionResponse` |
| PUT | `/api/admin/positions/{id}` | Bearer JWT (Admin) | Update position | FR-011 | `UpdatePositionRequest` | `PositionResponse` |
| DELETE | `/api/admin/positions/{id}` | Bearer JWT (Admin) | Delete position | FR-011 | — | `{message}` |
| POST | `/api/admin/positions/{id}/assign` | Bearer JWT (Admin) | Assign user | FR-011 | `{userId, isPrimary, startDate}` | `{message}` |
| DELETE | `/api/admin/positions/{id}/unassign/{userId}` | Bearer JWT (Admin) | Unassign user | FR-011 | — | `{message}` |

#### API Partner

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/api-partners` | Bearer JWT (Admin) | List partners | FR-012 | Query: `page, size, status` | `Page<PartnerResponse>` |
| GET | `/api/admin/api-partners/{id}` | Bearer JWT (Admin) | Partner detail | FR-012 | — | `PartnerDetailResponse` |
| POST | `/api/admin/api-partners` | Bearer JWT (Admin) | Register partner | FR-012 | `CreatePartnerRequest` | `PartnerResponse` |
| PUT | `/api/admin/api-partners/{id}` | Bearer JWT (Admin) | Update partner | FR-012 | `UpdatePartnerRequest` | `PartnerResponse` |
| POST | `/api/admin/api-keys` | Bearer JWT (Admin) | Generate API key | FR-012 | `{partnerId, type: PK|SK, name}` | `{key (show-once), keyId, createdAt}` |
| DELETE | `/api/admin/api-keys/{id}` | Bearer JWT (Admin) | Revoke key | FR-012 | — | `{message}` |
| POST | `/api/admin/api-keys/{id}/rotate` | Bearer JWT (Admin) | Rotate key | FR-012 | — | `{newKey (show-once), oldKeyGracePeriod}` |
| GET | `/api/admin/api-usage` | Bearer JWT (Admin) | Usage dashboard | FR-012 | Query: `partnerId, from, to` | `UsageSummaryResponse` |
| PUT | `/api/admin/api-partners/{id}/ip-whitelist` | Bearer JWT (Admin) | Set IP whitelist | FR-012 | `{ips[]}` | `{message}` |

#### Approval Workflow

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/workflows` | Bearer JWT (Admin) | List definitions | FR-013 | Query: `entityType, status` | `[WorkflowDefResponse]` |
| POST | `/api/admin/workflows` | Bearer JWT (Admin) | Create definition | FR-013 | `CreateWorkflowRequest` | `WorkflowDefResponse` |
| PUT | `/api/admin/workflows/{id}` | Bearer JWT (Admin) | Update draft definition | FR-013 | `UpdateWorkflowRequest` | `WorkflowDefResponse` |
| POST | `/api/admin/workflows/{id}/activate` | Bearer JWT (Admin) | Activate definition | FR-013 | — | `{message}` |
| POST | `/api/admin/workflows/{id}/deprecate` | Bearer JWT (Admin) | Deprecate definition | FR-013 | — | `{message}` |
| GET | `/api/admin/workflows/instances` | Bearer JWT | My pending approvals | FR-013 | Query: `status, page` | `Page<InstanceResponse>` |
| GET | `/api/admin/workflows/instances/submitted` | Bearer JWT | My submissions | FR-013 | Query: `status, page` | `Page<InstanceResponse>` |
| POST | `/api/admin/workflows/instances` | Bearer JWT | Submit for approval | FR-013 | `{entityType, entityId}` | `InstanceResponse` |
| POST | `/api/admin/workflows/instances/{id}/approve` | Bearer JWT | Approve step | FR-013 | `{comment?}` | `InstanceResponse` |
| POST | `/api/admin/workflows/instances/{id}/reject` | Bearer JWT | Reject step | FR-013 | `{comment}` | `InstanceResponse` |
| POST | `/api/admin/workflows/instances/{id}/delegate` | Bearer JWT | Delegate step | FR-013 | `{delegateToUserId, comment?}` | `InstanceResponse` |
| POST | `/api/admin/workflows/instances/{id}/cancel` | Bearer JWT | Cancel submission | FR-013 | — | `InstanceResponse` |

#### System Config & Feature Flags

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/configs` | Bearer JWT (Admin) | List configs | FR-014 | Query: `domainId, page` | `Page<ConfigResponse>` |
| GET | `/api/admin/configs/{key}` | Bearer JWT (Admin) | Get config | FR-014 | — | `ConfigResponse` |
| PUT | `/api/admin/configs/{key}` | Bearer JWT (Admin) | Update config | FR-014 | `{value, type}` | `ConfigResponse` |
| GET | `/api/admin/configs/{key}/history` | Bearer JWT (Admin) | Config history | FR-014 | — | `[ConfigHistoryResponse]` |
| GET | `/api/admin/feature-flags` | Bearer JWT (Admin) | List flags | FR-014 | Query: `domainId` | `[FeatureFlagResponse]` |
| PUT | `/api/admin/feature-flags/{key}` | Bearer JWT (Admin) | Update flag | FR-014 | `{enabled, rolloutPercentage?, segments?}` | `FeatureFlagResponse` |

#### Audit Log

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/audit-logs` | Bearer JWT (Admin) | Search logs | FR-015 | Query: `action, entityType, userId, from, to, page` | `Page<AuditLogResponse>` |
| GET | `/api/admin/audit-logs/{id}` | Bearer JWT (Admin) | Log detail | FR-015 | — | `AuditLogDetailResponse` |
| GET | `/api/admin/audit-logs/export` | Bearer JWT (Admin) | Export logs | FR-015 | Query: filters + `format=CSV|JSON` | File download |

#### Domain Config

| Method | Path | Auth | Description | FR | Request | Response |
|--------|------|------|------------|-----|---------|----------|
| GET | `/api/admin/domains/{id}/config` | Bearer JWT (Admin) | Get domain config | FR-016 | — | `DomainConfigResponse` |
| PUT | `/api/admin/domains/{id}/config` | Bearer JWT (Admin) | Update domain config | FR-016 | `UpdateDomainConfigRequest` | `DomainConfigResponse` |

---

## 2. Events (Kafka Topics)

| Topic | Schema | Producer | Consumer |
|-------|--------|----------|----------|
| `iam.user.registered` | `{userId: Long, username: String, domainCode: String, timestamp: Instant}` | auth-service | account-service |
| `iam.user.sso_provisioned` | `{userId: Long, provider: String, email: String?, domainCode: String, timestamp: Instant}` | auth-service | account-service |
| `iam.permission.changed` | `{userId: Long?, domainId: Long?, changedBy: Long, timestamp: Instant}` | system-admin | auth-service |
| `iam.audit.log` | `{action: String, userId: Long?, entityType: String, entityId: Long?, domainId: Long?, timestamp: Instant}` | all services | system-admin (optional) |
| `acct.lifecycle.deactivated` | `{userId: Long, reason: String?, deactivatedBy: Long, timestamp: Instant}` | account-service | auth-service |
| `acct.lifecycle.deleted` | `{userId: Long, timestamp: Instant}` | account-service | auth-service |

---

## 3. Redis Pub/Sub Channels

| Channel | Message | Publisher | Subscriber |
|---------|---------|----------|------------|
| `menu_cache_invalidated` | `{domainId: Long, userId: Long?}` | system-admin | system-admin (all instances) |
| `api_key_revoked` | `{keyHash: String, partnerId: Long}` | system-admin | API Gateway |

---

## 4. Error Code Registry

### auth-service (Existing + Extensions)

| Code | Message Key | HTTP | Description |
|------|-----------|------|-------------|
| AUTH_001-044 | (existing) | various | See `AuthErrorCode.kt` |
| AUTH_050 | `auth.service_token_invalid` | 401 | Invalid service JWT |
| AUTH_051 | `auth.service_token_expired` | 401 | Expired service JWT |
| AUTH_052 | `auth.internal_access_denied` | 403 | Internal endpoint access denied |

### account-service (NEW)

| Code | Message Key | HTTP | Description |
|------|-----------|------|-------------|
| ACCT_001 | `acct.profile_not_found` | 404 | User profile not found |
| ACCT_002 | `acct.validation_failed` | 400 | Profile validation failed |
| ACCT_003 | `acct.verification_required` | 428 | Email/phone verification required |
| ACCT_004 | `acct.invalid_preference_format` | 400 | Invalid preference key/category format |
| ACCT_005 | `acct.device_not_found` | 404 | Device not found |
| ACCT_006 | `acct.max_devices_reached` | 429 | Max trusted devices limit |
| ACCT_007 | `acct.already_deactivated` | 409 | Account already deactivated |
| ACCT_008 | `acct.deletion_pending` | 409 | Deletion already requested |

### system-admin-service (NEW)

| Code | Message Key | HTTP | Description |
|------|-----------|------|-------------|
| SYS_001 | `sys.menu_not_found` | 404 | Menu item not found |
| SYS_002 | `sys.circular_reference` | 400 | Circular reference in tree |
| SYS_003 | `sys.duplicate_code` | 409 | Duplicate menu/dept code |
| SYS_004 | `sys.department_not_found` | 404 | Department not found |
| SYS_005 | `sys.circular_hierarchy` | 400 | Circular department hierarchy |
| SYS_006 | `sys.position_duplicate` | 409 | Duplicate position in department |
| SYS_007 | `sys.partner_not_found` | 404 | API partner not found |
| SYS_008 | `sys.key_revoked` | 403 | API key revoked |
| SYS_009 | `sys.rate_limit_exceeded` | 429 | API rate limit exceeded |
| SYS_010 | `sys.ip_not_whitelisted` | 403 | IP not in whitelist |
| SYS_011 | `sys.workflow_not_found` | 404 | Workflow definition not found |
| SYS_012 | `sys.invalid_transition` | 400 | Invalid workflow state transition |
| SYS_013 | `sys.already_processed` | 409 | Workflow step already processed |
| SYS_014 | `sys.escalation_timeout` | 408 | Workflow step timed out |
| SYS_015 | `sys.config_not_found` | 404 | Config key not found |
| SYS_016 | `sys.invalid_config_type` | 400 | Invalid config value type |
| SYS_017 | `sys.audit_query_failed` | 500 | Audit log query failed |
| SYS_018 | `sys.max_tree_depth` | 400 | Maximum tree depth exceeded (10) |

---

## 5. DTO Schemas

### Key Request DTOs (account-service)

```kotlin
data class UpdateProfileRequest(
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: LocalDate?,
    val address: String?,
    val timezone: String?,   // e.g., "Asia/Ho_Chi_Minh"
    val locale: String?      // e.g., "vi"
)

data class DeviceResponse(
    val id: Long,
    val deviceName: String?,
    val deviceType: String?,
    val browser: String?,
    val os: String?,
    val ipAddress: String?,
    val trusted: Boolean,
    val trustedUntil: Instant?,
    val lastUsedAt: Instant?,
    val current: Boolean      // is this the current request device
)
```

### Key Request DTOs (system-admin-service)

```kotlin
data class CreateMenuRequest(
    @NotBlank val code: String,
    @NotBlank val name: String,
    val parentId: Long?,
    val type: MenuType,       // DIRECTORY, MENU, BUTTON, API
    val path: String?,
    val icon: String?,
    val sortOrder: Int = 0,
    val domainId: Long
)

data class CreateDepartmentRequest(
    @NotBlank val code: String,
    @NotBlank val name: String,
    val parentId: Long?,
    val description: String?,
    val sortOrder: Int = 0,
    val managerUserId: Long?,
    val domainId: Long
)

data class CreateWorkflowRequest(
    @NotBlank val entityType: String,
    @NotBlank val name: String,
    val description: String?,
    val steps: List<WorkflowStepRequest>,
    val conditions: Map<String, Any>?,  // JSONB conditions
    val domainId: Long
)

data class WorkflowStepRequest(
    val stepOrder: Int,
    @NotBlank val name: String,
    val approverType: ApproverType,  // ROLE, USER, DEPARTMENT_HEAD
    val approverValue: String,
    val timeoutHours: Int = 48,
    val escalationTo: String?,
    val conditions: Map<String, Any>?
)
```

---

## 6. Idempotency Specification (FR-017)

### Request Header
```
X-Idempotency-Key: <UUID-v4>
```

### Behavior
| Scenario | Response |
|----------|----------|
| First request with key | Process normally, cache response in Redis (TTL 24h) |
| Duplicate request (same key) | Return cached response (same status code + body) |
| Key not provided (GET/DELETE) | Process normally (safe methods) |
| Key not provided (POST/PUT) | Process normally (idempotency optional) |

### Redis Key Format
```
idempotency:{service}:{key}
```

---

## 7. FR Coverage Verification

| FR | Spec Section | Covered |
|----|-------------|---------|
| FR-001 | 1.1 MFA Management | ✅ |
| FR-002 | 1.1 Authentication | ✅ |
| FR-003 | 1.1 SSO | ✅ |
| FR-004 | 1.1 Password | ✅ |
| FR-005 | 1.2 Profile | ✅ |
| FR-006 | 1.2 Preferences | ✅ |
| FR-007 | 1.2 Device Management | ✅ |
| FR-008 | 1.1 Session + 1.2 Sessions | ✅ |
| FR-009 | 1.1 Account Lifecycle | ✅ |
| FR-010 | 1.3 Menu Permission | ✅ |
| FR-011 | 1.3 Organization | ✅ |
| FR-012 | 1.3 API Partner | ✅ |
| FR-013 | 1.3 Approval Workflow | ✅ |
| FR-014 | 1.3 System Config | ✅ |
| FR-015 | 1.3 Audit Log | ✅ |
| FR-016 | 1.3 Domain Config | ✅ |
| FR-017 | 6. Idempotency | ✅ |
| FR-018 | (base-core reuse) | ✅ |
| FR-019 | (HttpClientConfig extend) | ✅ |
| FR-020 | 2. Events | ✅ |
| FR-021 | 1.1 Internal APIs | ✅ |

**Total: 21/21 FR covered.**
