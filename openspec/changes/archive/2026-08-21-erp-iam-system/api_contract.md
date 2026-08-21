<!-- contract-version: 1.0 -->
<!-- backend-status: drift -->
<!-- frontend-status: pending -->
<!-- generated-by: wf_api_contract -->
<!-- generated-at: 2026-08-20T10:28:00+07:00 -->
<!-- validated-at: 2026-08-20T10:42:00+07:00 -->
<!-- drift-count: 43 (11 HIGH, 17 MEDIUM, 6 LOW, 9 NOT_IMPLEMENTED) -->

# API Contract — ERP IAM System

> Source: `design.md` + `srs.md` + `business_analysis.md` (research)
> Version: 1.0
> Generated: 2026-08-20

---

## 1. Response Wrapper (Project Convention)

All API responses use `ApiResponse<T>` from `base-web-starter`:

```typescript
interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ErrorDetail | null;
  timestamp: string; // ISO 8601
  requestId: string;
}

interface ErrorDetail {
  code: string;
  message: string;
  details?: Record<string, string[]>;
}

interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

---

## 2. Auth Service Endpoints

### 2.1 POST `/api/auth/login`
**FR**: FR-001 — Authentication with optional MFA
**Auth**: Public

```typescript
// Request
interface LoginRequest {
  username: string;             // visibility: public
  password: string;             // visibility: public (E2EE encrypted)
  domainCode: string;           // visibility: public
  captchaToken?: string;        // visibility: public
  deviceFingerprint?: string;   // visibility: public
}

// Response (Success — no MFA)
interface LoginResponse {
  accessToken: string;          // visibility: public
  refreshToken: string;         // visibility: public
  tokenType: string;            // visibility: public — "Bearer"
  expiresIn: number;            // visibility: public — seconds
  user: UserBrief;              // visibility: public
}

// Response (Partial — MFA required)
interface LoginMfaRequiredResponse {
  partialToken: string;         // visibility: public — temporary token for 2FA
  requireMfa: boolean;          // visibility: public — true
  availableMethods: MfaMethod[];// visibility: public — ["TOTP", "SMS", "EMAIL"]
}

type MfaMethod = "TOTP" | "SMS" | "EMAIL";

interface UserBrief {
  id: string;                   // visibility: public — Snowflake ID as string
  username: string;             // visibility: public
  email: string;                // visibility: public
  fullName: string;             // visibility: public
  activeDomainCode: string;     // visibility: public
  roles: string[];              // visibility: public — role codes
}
```

**Errors**:

| Code | HTTP | Description | Frontend Action |
|------|------|-------------|-----------------|
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong username/password | field-error |
| `AUTH_ACCOUNT_LOCKED` | 423 | Account locked (too many failures) | modal |
| `AUTH_ACCOUNT_DISABLED` | 403 | Account deactivated | modal |
| `AUTH_CAPTCHA_REQUIRED` | 428 | CAPTCHA needed (>3 failures) | modal |
| `AUTH_CAPTCHA_INVALID` | 400 | Invalid CAPTCHA token | toast |
| `AUTH_DOMAIN_NOT_FOUND` | 404 | Domain code not found | toast |

---

### 2.2 POST `/api/auth/verify-2fa`
**FR**: FR-001 — MFA Verification
**Auth**: Partial Token

```typescript
// Request
interface Verify2faRequest {
  partialToken: string;         // visibility: public
  code: string;                 // visibility: public — OTP/TOTP code
  method: MfaMethod;            // visibility: public
}

// Response
type Verify2faResponse = LoginResponse; // Same as full login response
```

**Errors**:

| Code | HTTP | Description | Frontend Action |
|------|------|-------------|-----------------|
| `MFA_INVALID_CODE` | 401 | Wrong OTP/TOTP code | field-error |
| `MFA_CODE_EXPIRED` | 410 | OTP expired (>5 min) | toast + retry |
| `MFA_MAX_ATTEMPTS` | 429 | Max 3 attempts exceeded | modal |
| `MFA_TOKEN_INVALID` | 401 | Partial token invalid/expired | redirect (login) |

---

### 2.3 POST `/api/auth/request-otp`
**FR**: FR-001 — Request OTP
**Auth**: Partial Token

```typescript
// Request
interface RequestOtpRequest {
  partialToken: string;         // visibility: public
  channel: "SMS" | "EMAIL";     // visibility: public
}

// Response
interface RequestOtpResponse {
  sent: boolean;                // visibility: public
  channel: string;              // visibility: public
  maskedTarget: string;         // visibility: public — "***@gm***.com" / "***1234"
  expiresInSeconds: number;     // visibility: public — 300
}
```

---

### 2.4 POST `/api/auth/refresh`
**FR**: FR-003 — Token Refresh
**Auth**: Public (Refresh Token)

```typescript
// Request
interface RefreshTokenRequest {
  refreshToken: string;         // visibility: public
}

// Response
type RefreshTokenResponse = LoginResponse;
```

**Errors**:

| Code | HTTP | Description | Frontend Action |
|------|------|-------------|-----------------|
| `AUTH_TOKEN_EXPIRED` | 401 | Refresh token expired | redirect (login) |
| `AUTH_TOKEN_REVOKED` | 401 | Token revoked | redirect (login) |

---

### 2.5 POST `/api/auth/logout`
**FR**: FR-003 — Logout
**Auth**: Bearer

```typescript
// Request — No body needed (JWT from header)

// Response
interface LogoutResponse {
  success: boolean;             // visibility: public
}
```

---

### 2.6 POST `/api/auth/logout-all`
**FR**: FR-003 — Logout All Sessions
**Auth**: Bearer

```typescript
// Request — No body needed

// Response
interface LogoutAllResponse {
  terminatedSessions: number;   // visibility: public
}
```

---

### 2.7 POST `/api/auth/switch-domain`
**FR**: FR-003 — Switch Domain
**Auth**: Bearer

```typescript
// Request
interface SwitchDomainRequest {
  domainCode: string;           // visibility: public
}

// Response
type SwitchDomainResponse = LoginResponse;
```

---

### 2.8 GET `/api/auth/introspect`
**FR**: FR-003 — Token Introspection
**Auth**: Bearer

```typescript
// Response
interface TokenIntrospectionResponse {
  active: boolean;              // visibility: public
  sub: string;                  // visibility: public — user ID
  username: string;             // visibility: public
  domainCode: string;           // visibility: public
  roles: string[];              // visibility: public
  iat: number;                  // visibility: public — issued at
  exp: number;                  // visibility: public — expires at
  jti: string;                  // visibility: internal
}
```

---

### 2.9 POST `/api/auth/forgot-password`
**FR**: FR-004 — Password Reset Initiation
**Auth**: Public

```typescript
// Request
interface ForgotPasswordRequest {
  email: string;                // visibility: public
  domainCode: string;           // visibility: public
}

// Response
interface ForgotPasswordResponse {
  sent: boolean;                // visibility: public
  maskedEmail: string;          // visibility: public
}
```

---

### 2.10 POST `/api/auth/reset-password`
**FR**: FR-004 — Password Reset
**Auth**: Public (Reset Token)

```typescript
// Request
interface ResetPasswordRequest {
  token: string;                // visibility: public
  newPassword: string;          // visibility: public (E2EE)
}

// Response
interface ResetPasswordResponse {
  success: boolean;             // visibility: public
}
```

---

### 2.11 POST `/api/auth/change-password`
**FR**: FR-004 — Change Password (Authenticated)
**Auth**: Bearer

```typescript
// Request
interface ChangePasswordRequest {
  currentPassword: string;      // visibility: public (E2EE)
  newPassword: string;          // visibility: public (E2EE)
}

// Response
interface ChangePasswordResponse {
  success: boolean;             // visibility: public
}
```

**Errors**:

| Code | HTTP | Description | Frontend Action |
|------|------|-------------|-----------------|
| `PWD_WRONG_CURRENT` | 400 | Current password incorrect | field-error |
| `PWD_POLICY_VIOLATION` | 400 | New password violates policy | field-error |
| `PWD_RECENTLY_USED` | 400 | Password recently used | field-error |

---

### 2.12 GET `/api/mfa/status`
**FR**: FR-001 — MFA Status
**Auth**: Bearer

```typescript
// Response
interface MfaStatusResponse {
  enabled: boolean;             // visibility: public
  methods: MfaMethodConfig[];   // visibility: public
  recoveryCodesRemaining: number; // visibility: public
}

interface MfaMethodConfig {
  method: MfaMethod;            // visibility: public
  active: boolean;              // visibility: public
  configuredAt: string;         // visibility: public — ISO 8601
}
```

---

### 2.13 POST `/api/mfa/enable`
**FR**: FR-001 — Enable MFA
**Auth**: Bearer

```typescript
// Request
interface EnableMfaRequest {
  method: MfaMethod;            // visibility: public
  verificationCode?: string;    // visibility: public — verify phone/email first
}

// Response (TOTP)
interface EnableTotpResponse {
  secret: string;               // visibility: public — base32 secret (show once)
  qrCodeUri: string;            // visibility: public — otpauth:// URI
  recoveryCodes: string[];      // visibility: public — backup codes (show once)
}

// Response (SMS/EMAIL)
interface EnableOtpMethodResponse {
  maskedTarget: string;         // visibility: public
  recoveryCodes: string[];      // visibility: public
}
```

---

### 2.14 POST `/api/mfa/disable`
**FR**: FR-001 — Disable MFA
**Auth**: Bearer

```typescript
// Request
interface DisableMfaRequest {
  method: MfaMethod;            // visibility: public
  verificationCode: string;     // visibility: public — current MFA code
}

// Response
interface DisableMfaResponse {
  success: boolean;             // visibility: public
}
```

---

### 2.15 GET `/api/mfa/recovery-codes`
**FR**: FR-001 — Regenerate Recovery Codes
**Auth**: Bearer

```typescript
// Response
interface RecoveryCodesResponse {
  codes: string[];              // visibility: public — 10 new codes (show once)
}
```

---

### 2.16 GET `/api/sso/providers`
**FR**: FR-002 — SSO Provider List
**Auth**: Public

```typescript
// Response
interface SsoProviderListResponse {
  providers: SsoProviderItem[]; // visibility: public
}

interface SsoProviderItem {
  code: string;                 // visibility: public
  name: string;                 // visibility: public
  providerType: string;         // visibility: public — "KEYCLOAK" | "GOOGLE" | "MICROSOFT"
  logoUrl?: string;             // visibility: public
}
```

---

### 2.17 GET `/api/sso/{provider}/authorize`
**FR**: FR-002 — Initiate SSO
**Auth**: Public

```typescript
// Response — HTTP 302 Redirect or:
interface SsoAuthorizeResponse {
  redirectUrl: string;          // visibility: public
}
```

---

### 2.18 POST `/api/sso/{provider}/callback`
**FR**: FR-002 — SSO Callback
**Auth**: Public

```typescript
// Request
interface SsoCallbackRequest {
  code: string;                 // visibility: public — authorization code
  state: string;                // visibility: public — CSRF state
}

// Response
type SsoCallbackResponse = LoginResponse;
```

---

### 2.19 POST `/api/sso/link`
**FR**: FR-002 — Link SSO Account
**Auth**: Bearer

```typescript
// Request
interface LinkSsoRequest {
  providerCode: string;         // visibility: public
  authorizationCode: string;    // visibility: public
}

// Response
interface LinkSsoResponse {
  linked: boolean;              // visibility: public
  providerName: string;         // visibility: public
  externalEmail: string;        // visibility: public
}
```

---

### 2.20 DELETE `/api/sso/link/{providerId}`
**FR**: FR-002 — Unlink SSO
**Auth**: Bearer

```typescript
// Response
interface UnlinkSsoResponse {
  unlinked: boolean;            // visibility: public
}
```

---

### 2.21 POST `/api/permissions/check`
**FR**: FR-010 — Permission Check
**Auth**: Internal (service-to-service)

```typescript
// Request
interface CheckPermissionRequest {
  userId: string;               // visibility: internal
  resource: string;             // visibility: internal
  action: string;               // visibility: internal
  context?: Record<string, any>; // visibility: internal
}

// Response
interface CheckPermissionResponse {
  allowed: boolean;             // visibility: internal
  reason?: string;              // visibility: internal
}
```

---

### 2.22 POST `/api/permissions/check-batch`
**FR**: FR-010 — Batch Permission Check
**Auth**: Internal

```typescript
// Request
interface CheckBatchPermissionRequest {
  userId: string;               // visibility: internal
  checks: PermissionCheckItem[];// visibility: internal
}

interface PermissionCheckItem {
  resource: string;
  action: string;
}

// Response
interface CheckBatchPermissionResponse {
  results: Record<string, boolean>; // visibility: internal — "resource:action" -> allowed
}
```

---

### 2.23 GET `/api/permissions/user/{userId}`
**FR**: FR-010 — User Effective Permissions
**Auth**: Internal

```typescript
// Response
interface UserPermissionsResponse {
  userId: string;               // visibility: internal
  roles: RoleBrief[];           // visibility: internal
  permissions: string[];        // visibility: internal — "resource:action" list
}

interface RoleBrief {
  id: string;
  code: string;
  name: string;
}
```

---

## 3. Account Service Endpoints

### 3.1 GET `/api/account/profile`
**FR**: FR-005 — User Profile
**Auth**: Bearer

```typescript
// Response
interface UserProfileResponse {
  userId: string;               // visibility: public
  displayName: string;          // visibility: public
  firstName: string;            // visibility: public
  lastName: string;             // visibility: public
  dateOfBirth?: string;         // visibility: public — ISO date
  gender?: string;              // visibility: public
  address?: string;             // visibility: public
  city?: string;                // visibility: public
  country?: string;             // visibility: public
  timezone: string;             // visibility: public
  locale: string;               // visibility: public
  avatarUrl?: string;           // visibility: public
  bio?: string;                 // visibility: public
  contacts: UserContact[];      // visibility: public
}

interface UserContact {
  id: string;                   // visibility: public
  contactType: string;          // visibility: public — "EMAIL" | "PHONE" | "TELEGRAM"
  contactValue: string;         // visibility: public
  isPrimary: boolean;           // visibility: public
  isVerified: boolean;          // visibility: public
}
```

---

### 3.2 PUT `/api/account/profile`
**FR**: FR-005 — Update Profile
**Auth**: Bearer

```typescript
// Request
interface UpdateProfileRequest {
  displayName?: string;         // visibility: public
  firstName?: string;           // visibility: public
  lastName?: string;            // visibility: public
  dateOfBirth?: string;         // visibility: public
  gender?: string;              // visibility: public
  address?: string;             // visibility: public
  city?: string;                // visibility: public
  country?: string;             // visibility: public
  timezone?: string;            // visibility: public
  locale?: string;              // visibility: public
  bio?: string;                 // visibility: public
}

// Response
type UpdateProfileResponse = UserProfileResponse;
```

---

### 3.3 POST `/api/account/profile/avatar`
**FR**: FR-005 — Upload Avatar
**Auth**: Bearer

```typescript
// Request: multipart/form-data with "file" field

// Response
interface AvatarUploadResponse {
  avatarUrl: string;            // visibility: public
}
```

---

### 3.4 PUT `/api/account/profile/email`
**FR**: FR-005 — Change Email
**Auth**: Bearer

```typescript
// Request
interface ChangeEmailRequest {
  newEmail: string;             // visibility: public
  verificationCode: string;     // visibility: public — OTP sent to new email
}

// Response
interface ChangeEmailResponse {
  success: boolean;             // visibility: public
  newEmail: string;             // visibility: public
}
```

---

### 3.5 GET `/api/account/preferences`
**FR**: FR-006 — Preferences
**Auth**: Bearer

```typescript
// Response
interface UserPreferencesResponse {
  preferences: PreferenceItem[];// visibility: public
}

interface PreferenceItem {
  key: string;                  // visibility: public
  value: string;                // visibility: public
  category: string;             // visibility: public — "UI" | "NOTIFICATION" | "PRIVACY" | "SECURITY"
}
```

---

### 3.6 PUT `/api/account/preferences`
**FR**: FR-006 — Update Preferences
**Auth**: Bearer

```typescript
// Request
interface UpdatePreferencesRequest {
  preferences: { key: string; value: string }[]; // visibility: public
}

// Response
type UpdatePreferencesResponse = UserPreferencesResponse;
```

---

### 3.7 GET `/api/account/notifications/settings`
**FR**: FR-006 — Notification Settings
**Auth**: Bearer

```typescript
// Response
interface NotificationSettingsResponse {
  settings: NotificationSetting[];
}

interface NotificationSetting {
  channel: string;              // visibility: public — "EMAIL" | "SMS" | "PUSH" | "IN_APP"
  eventType: string;            // visibility: public
  enabled: boolean;             // visibility: public
}
```

---

### 3.8 PUT `/api/account/notifications/settings`
**FR**: FR-006
**Auth**: Bearer

```typescript
// Request
type UpdateNotificationSettingsRequest = NotificationSettingsResponse;

// Response
type UpdateNotificationSettingsResponse = NotificationSettingsResponse;
```

---

### 3.9 GET `/api/account/devices`
**FR**: FR-007 — Device Management
**Auth**: Bearer

```typescript
// Response
interface DeviceListResponse {
  devices: DeviceItem[];
}

interface DeviceItem {
  id: string;                   // visibility: public
  deviceName: string;           // visibility: public
  deviceType: string;           // visibility: public — "WEB" | "MOBILE" | "TABLET"
  os: string;                   // visibility: public
  browser?: string;             // visibility: public
  lastActiveAt: string;         // visibility: public
  trusted: boolean;             // visibility: public
  trustedUntil?: string;        // visibility: public
  isCurrent: boolean;           // visibility: public
}
```

---

### 3.10 POST `/api/account/devices/{id}/trust`
**FR**: FR-007 — Trust Device
**Auth**: Bearer

```typescript
// Response
interface TrustDeviceResponse {
  trusted: boolean;             // visibility: public
  trustedUntil: string;         // visibility: public — ISO 8601
}
```

---

### 3.11 DELETE `/api/account/devices/{id}`
**FR**: FR-007 — Revoke Device
**Auth**: Bearer

```typescript
// Response
interface RevokeDeviceResponse {
  revoked: boolean;             // visibility: public
}
```

---

### 3.12 GET `/api/account/sessions`
**FR**: FR-008 — Session Management
**Auth**: Bearer

```typescript
// Response
interface SessionListResponse {
  sessions: SessionItem[];
}

interface SessionItem {
  id: string;                   // visibility: public
  deviceName: string;           // visibility: public
  ipAddress: string;            // visibility: public
  lastAccessedAt: string;       // visibility: public
  createdAt: string;            // visibility: public
  isCurrent: boolean;           // visibility: public
}
```

---

### 3.13 DELETE `/api/account/sessions/{id}`
**FR**: FR-008 — Terminate Session
**Auth**: Bearer

```typescript
// Response
interface TerminateSessionResponse {
  terminated: boolean;          // visibility: public
}
```

---

### 3.14 DELETE `/api/account/sessions`
**FR**: FR-008 — Terminate All Other Sessions
**Auth**: Bearer

```typescript
// Response
interface TerminateAllSessionsResponse {
  terminatedCount: number;      // visibility: public
}
```

---

### 3.15 GET `/api/account/login-history`
**FR**: FR-005 — Login History
**Auth**: Bearer

```typescript
// Query params: page, size, from, to

// Response
type LoginHistoryResponse = PagedResponse<LoginHistoryItem>;

interface LoginHistoryItem {
  id: string;                   // visibility: public
  ipAddress: string;            // visibility: public
  loginMethod: string;          // visibility: public — "PASSWORD" | "SSO" | "TOKEN_REFRESH"
  status: string;               // visibility: public — "SUCCESS" | "FAILED" | "BLOCKED"
  loginAt: string;              // visibility: public
  country?: string;             // visibility: public
  city?: string;                // visibility: public
  deviceName?: string;          // visibility: public
}
```

---

### 3.16 POST `/api/account/deactivate`
**FR**: FR-009 — Deactivate Account
**Auth**: Bearer

```typescript
// Request
interface DeactivateAccountRequest {
  password: string;             // visibility: public — confirm with password
  reason?: string;              // visibility: public
}

// Response
interface DeactivateAccountResponse {
  deactivated: boolean;         // visibility: public
}
```

---

### 3.17 POST `/api/account/deletion-request`
**FR**: FR-009 — GDPR Delete Request
**Auth**: Bearer

```typescript
// Request
interface DeletionRequestRequest {
  password: string;             // visibility: public
  reason?: string;              // visibility: public
}

// Response
interface DeletionRequestResponse {
  requestId: string;            // visibility: public
  scheduledAt: string;          // visibility: public — deletion date (30 days)
}
```

---

### 3.18 GET `/api/account/export`
**FR**: FR-009 — GDPR Data Export
**Auth**: Bearer

```typescript
// Response: application/json download or:
interface DataExportResponse {
  downloadUrl: string;          // visibility: public — presigned URL
  expiresAt: string;            // visibility: public
}
```

---

### 3.19 Admin User APIs (account-service)

#### GET `/api/admin/users`
**Auth**: Bearer+Admin

```typescript
// Query: page, size, search, status, domainCode, roleId, departmentId

// Response
type AdminUserListResponse = PagedResponse<AdminUserItem>;

interface AdminUserItem {
  id: string;                   // visibility: public
  username: string;             // visibility: public
  email: string;                // visibility: public
  fullName: string;             // visibility: public
  status: string;               // visibility: public
  roles: string[];              // visibility: public
  lastLoginAt?: string;         // visibility: public
  createdAt: string;            // visibility: internal
}
```

#### GET `/api/admin/users/{id}`
**Auth**: Bearer+Admin

```typescript
// Response
interface AdminUserDetailResponse {
  id: string;
  username: string;
  email: string;
  fullName: string;
  status: string;
  roles: RoleBrief[];
  profile: UserProfileResponse;
  mfaEnabled: boolean;
  ssoLinked: string[];          // provider names
  lastLoginAt?: string;
  createdAt: string;            // visibility: internal
  updatedAt: string;            // visibility: internal
}
```

#### PUT `/api/admin/users/{id}/status`
**Auth**: Bearer+Admin

```typescript
// Request
interface UpdateUserStatusRequest {
  status: "ACTIVE" | "INACTIVE" | "LOCKED" | "SUSPENDED";
  reason?: string;
}

// Response
interface UpdateUserStatusResponse {
  userId: string;
  newStatus: string;
}
```

#### POST `/api/admin/users/{id}/reset-password`
**Auth**: Bearer+Admin

```typescript
// Response
interface ForceResetPasswordResponse {
  temporaryPassword: string;    // visibility: public — show once
}
```

---

## 4. System Admin Service Endpoints

### 4.1 Menu Management

#### GET `/api/admin/menus/tree`
**FR**: FR-010 — Full Menu Tree (Admin)
**Auth**: Bearer+Admin

```typescript
// Response
interface MenuTreeResponse {
  menus: MenuTreeNode[];
}

interface MenuTreeNode {
  id: string;                   // visibility: public
  code: string;                 // visibility: public
  name: string;                 // visibility: public
  icon?: string;                // visibility: public
  path?: string;                // visibility: public
  routeName?: string;           // visibility: public
  component?: string;           // visibility: public
  sortOrder: number;            // visibility: public
  menuType: MenuType;           // visibility: public
  isVisible: boolean;           // visibility: public
  isCacheable: boolean;         // visibility: public
  status: string;               // visibility: public
  permissions: MenuPermission[];// visibility: public
  children: MenuTreeNode[];     // visibility: public
}

type MenuType = "DIRECTORY" | "MENU" | "BUTTON" | "API";

interface MenuPermission {
  permissionCode: string;       // visibility: public — "view" | "create" | "edit" | "delete" | "export"
  name: string;                 // visibility: public
}
```

---

#### GET `/api/admin/menus/user-tree`
**FR**: FR-010 — User Accessible Menu
**Auth**: Bearer

```typescript
// Response
interface UserMenuTreeResponse {
  menus: UserMenuNode[];
}

interface UserMenuNode {
  id: string;                   // visibility: public
  code: string;                 // visibility: public
  name: string;                 // visibility: public
  icon?: string;                // visibility: public
  path?: string;                // visibility: public
  routeName?: string;           // visibility: public
  component?: string;           // visibility: public
  menuType: MenuType;           // visibility: public
  permissions: string[];        // visibility: public — granted permission codes
  buttons: ButtonPermission[];  // visibility: public
  children: UserMenuNode[];     // visibility: public
}

interface ButtonPermission {
  code: string;                 // visibility: public — "btn-create"
  name: string;                 // visibility: public — "Tạo mới"
  permission: string;           // visibility: public — "create"
}
```

---

#### POST `/api/admin/menus`
**Auth**: Bearer+Admin

```typescript
// Request
interface CreateMenuRequest {
  parentId?: string;
  code: string;
  name: string;
  icon?: string;
  path?: string;
  routeName?: string;
  component?: string;
  sortOrder: number;
  menuType: MenuType;
  isVisible: boolean;
  isCacheable: boolean;
  permissions?: string[];       // permission codes to create
}

// Response
type CreateMenuResponse = MenuTreeNode;
```

---

#### PUT `/api/admin/menus/{id}`
**Auth**: Bearer+Admin

```typescript
// Request — same fields as Create (partial update)
type UpdateMenuRequest = Partial<CreateMenuRequest>;

// Response
type UpdateMenuResponse = MenuTreeNode;
```

---

#### DELETE `/api/admin/menus/{id}`
**Auth**: Bearer+Admin — Soft delete

```typescript
// Response
interface DeleteMenuResponse {
  deleted: boolean;
}
```

---

#### POST `/api/admin/roles/{roleId}/menus`
**FR**: FR-010 — Assign Menus to Role
**Auth**: Bearer+Admin

```typescript
// Request
interface AssignRoleMenusRequest {
  menuPermissions: RoleMenuPermission[];
}

interface RoleMenuPermission {
  menuId: string;
  permissionCodes: string[];    // ["view", "create", "edit"]
}

// Response
interface AssignRoleMenusResponse {
  assigned: number;
}
```

---

#### GET `/api/admin/roles/{roleId}/menus`
**Auth**: Bearer+Admin

```typescript
// Response
interface RoleMenuPermissionsResponse {
  roleId: string;
  roleName: string;
  menuPermissions: RoleMenuDetail[];
}

interface RoleMenuDetail {
  menuId: string;
  menuCode: string;
  menuName: string;
  grantedPermissions: string[];
}
```

---

#### POST `/api/admin/users/{userId}/menu-overrides`
**Auth**: Bearer+Admin

```typescript
// Request
interface UserMenuOverrideRequest {
  overrides: MenuOverride[];
}

interface MenuOverride {
  menuId: string;
  permissionCode: string;
  isGranted: boolean;
  reason: string;
}

// Response
interface UserMenuOverrideResponse {
  applied: number;
}
```

---

### 4.2 Organization Management

#### GET `/api/admin/departments/tree`
**FR**: FR-011
**Auth**: Bearer

```typescript
// Response
interface DepartmentTreeResponse {
  departments: DepartmentNode[];
}

interface DepartmentNode {
  id: string;
  code: string;
  name: string;
  description?: string;
  managerUserId?: string;
  managerName?: string;
  sortOrder: number;
  level: number;
  status: string;
  userCount: number;
  children: DepartmentNode[];
}
```

---

#### POST `/api/admin/departments`
**Auth**: Bearer+Admin

```typescript
// Request
interface CreateDepartmentRequest {
  parentId?: string;
  code: string;
  name: string;
  description?: string;
  managerUserId?: string;
  sortOrder: number;
}

// Response
type CreateDepartmentResponse = DepartmentNode;
```

---

#### GET `/api/admin/departments/{id}/users`
**Auth**: Bearer

```typescript
// Query: page, size, recursive (include sub-departments)

// Response
type DepartmentUsersResponse = PagedResponse<DepartmentUserItem>;

interface DepartmentUserItem {
  userId: string;
  username: string;
  fullName: string;
  positionName: string;
  isPrimary: boolean;
  departmentName: string;
}
```

---

#### GET `/api/admin/positions`
**Auth**: Bearer

```typescript
// Query: departmentId, page, size

// Response
type PositionListResponse = PagedResponse<PositionItem>;

interface PositionItem {
  id: string;
  code: string;
  name: string;
  description?: string;
  departmentId: string;
  departmentName: string;
  gradeLevel: number;
  status: string;
}
```

---

#### POST `/api/admin/positions`
**Auth**: Bearer+Admin

```typescript
// Request
interface CreatePositionRequest {
  code: string;
  name: string;
  description?: string;
  departmentId: string;
  gradeLevel: number;
}

// Response
type CreatePositionResponse = PositionItem;
```

---

#### POST `/api/admin/user-positions`
**Auth**: Bearer+Admin

```typescript
// Request
interface AssignUserPositionRequest {
  userId: string;
  positionId: string;
  departmentId: string;
  isPrimary: boolean;
  effectiveFrom: string;        // ISO date
  effectiveTo?: string;
}

// Response
interface AssignUserPositionResponse {
  id: string;
  assigned: boolean;
}
```

---

### 4.3 API Partner Management

#### GET `/api/admin/partners`
**FR**: FR-012
**Auth**: Bearer+Admin

```typescript
// Query: page, size, status, search

// Response
type PartnerListResponse = PagedResponse<PartnerItem>;

interface PartnerItem {
  id: string;
  partnerName: string;
  partnerCode: string;
  contactEmail: string;
  status: string;
  subscriptionPlanName: string;
  apiKeyCount: number;
  lastUsedAt?: string;
}
```

---

#### POST `/api/admin/partners`
**Auth**: Bearer+Admin

```typescript
// Request
interface CreatePartnerRequest {
  partnerName: string;
  partnerCode: string;
  contactEmail: string;
  contactPhone?: string;
  description?: string;
  subscriptionPlanId: string;
}

// Response
type CreatePartnerResponse = PartnerItem;
```

---

#### POST `/api/admin/partners/{id}/api-keys`
**FR**: FR-012 — Generate API Key
**Auth**: Bearer+Admin

```typescript
// Request
interface GenerateApiKeyRequest {
  name: string;
  scopes: string[];             // ["read:bookings", "write:bookings"]
  rateLimitPerSecond?: number;
  rateLimitPerMinute?: number;
  rateLimitPerDay?: number;
  quotaMonthly?: number;
  ipWhitelist?: string[];
  expiresAt?: string;           // ISO 8601
}

// Response
interface GenerateApiKeyResponse {
  id: string;
  keyPrefix: string;            // visibility: public — "ntt_pk_"
  fullKey: string;              // visibility: public — SHOW ONCE ONLY
  name: string;
  scopes: string[];
  expiresAt?: string;
  createdAt: string;
}
```

> ⚠️ `fullKey` is displayed **once only**. After this response, only `keyPrefix` + last 4 chars are stored.

---

#### DELETE `/api/admin/api-keys/{id}`
**Auth**: Bearer+Admin

```typescript
// Response
interface RevokeApiKeyResponse {
  revoked: boolean;
}
```

---

#### POST `/api/admin/api-keys/{id}/rotate`
**Auth**: Bearer+Admin

```typescript
// Response — same as GenerateApiKeyResponse (new key issued, old revoked)
type RotateApiKeyResponse = GenerateApiKeyResponse;
```

---

#### GET `/api/admin/partners/{id}/usage`
**Auth**: Bearer+Admin

```typescript
// Query: from, to, granularity ("HOURLY" | "DAILY")

// Response
interface PartnerUsageResponse {
  partnerId: string;
  partnerName: string;
  totalRequests: number;
  successRate: number;          // percentage
  avgResponseTimeMs: number;
  quotaUsed: number;
  quotaLimit: number;
  dataPoints: UsageDataPoint[];
}

interface UsageDataPoint {
  timestamp: string;
  requestCount: number;
  errorCount: number;
  avgResponseTimeMs: number;
}
```

---

#### GET `/api/admin/subscription-plans`
**Auth**: Bearer+Admin

```typescript
// Response
interface SubscriptionPlanListResponse {
  plans: SubscriptionPlanItem[];
}

interface SubscriptionPlanItem {
  id: string;
  code: string;
  name: string;
  description?: string;
  maxRequestsPerDay: number;
  maxRequestsPerMonth: number;
  rateLimitPerSecond: number;
  priceMonthly: number;
  status: string;
}
```

---

### 4.4 Approval Workflow

#### GET `/api/admin/workflows`
**FR**: FR-013
**Auth**: Bearer+Admin

```typescript
// Response
interface WorkflowListResponse {
  workflows: WorkflowDefinitionItem[];
}

interface WorkflowDefinitionItem {
  id: string;
  code: string;
  name: string;
  entityType: string;
  triggerEvent: string;
  version: number;
  status: string;
  stepsCount: number;
}
```

---

#### POST `/api/admin/workflows`
**Auth**: Bearer+Admin

```typescript
// Request
interface CreateWorkflowRequest {
  code: string;
  name: string;
  description?: string;
  entityType: string;
  triggerEvent: string;
  steps: WorkflowStepRequest[];
}

interface WorkflowStepRequest {
  stepOrder: number;
  stepType: "APPROVAL" | "REVIEW" | "NOTIFICATION" | "AUTO_APPROVE";
  name: string;
  approverType: "ROLE" | "DEPARTMENT_HEAD" | "USER" | "POSITION" | "DYNAMIC_RULE";
  approverValue: string;
  condition?: Record<string, any>;  // JSONB conditions
  timeoutHours?: number;
  isParallel: boolean;
}

// Response
type CreateWorkflowResponse = WorkflowDefinitionDetail;

interface WorkflowDefinitionDetail extends WorkflowDefinitionItem {
  description?: string;
  steps: WorkflowStepDetail[];
}

interface WorkflowStepDetail {
  id: string;
  stepOrder: number;
  stepType: string;
  name: string;
  approverType: string;
  approverValue: string;
  timeoutHours?: number;
  isParallel: boolean;
}
```

---

#### POST `/api/workflows/submit`
**FR**: FR-013 — Submit for Approval
**Auth**: Bearer

```typescript
// Request
interface SubmitWorkflowRequest {
  workflowCode: string;
  entityType: string;
  entityId: string;
  metadata?: Record<string, any>;
}

// Response
interface SubmitWorkflowResponse {
  instanceId: string;
  workflowName: string;
  status: "PENDING";
  currentStepName: string;
  assigneeName?: string;
}
```

---

#### POST `/api/workflows/{instanceId}/approve`
**Auth**: Bearer

```typescript
// Request
interface ApproveWorkflowRequest {
  comment?: string;
}

// Response
interface WorkflowActionResponse {
  instanceId: string;
  action: "APPROVED" | "REJECTED" | "DELEGATED";
  nextStepName?: string;
  workflowStatus: string;      // "IN_PROGRESS" | "APPROVED" | "COMPLETED"
}
```

---

#### POST `/api/workflows/{instanceId}/reject`
**Auth**: Bearer

```typescript
// Request
interface RejectWorkflowRequest {
  comment: string;              // required for rejection
}

// Response
type RejectWorkflowResponse = WorkflowActionResponse;
```

---

#### POST `/api/workflows/{instanceId}/delegate`
**Auth**: Bearer

```typescript
// Request
interface DelegateWorkflowRequest {
  delegateToUserId: string;
  comment?: string;
}

// Response
type DelegateWorkflowResponse = WorkflowActionResponse;
```

---

#### GET `/api/workflows/my-pending`
**Auth**: Bearer

```typescript
// Query: page, size

// Response
type MyPendingWorkflowsResponse = PagedResponse<PendingWorkflowItem>;

interface PendingWorkflowItem {
  instanceId: string;
  workflowName: string;
  entityType: string;
  entityId: string;
  requesterName: string;
  currentStepName: string;
  submittedAt: string;
  dueAt?: string;
}
```

---

#### GET `/api/workflows/my-submitted`
**Auth**: Bearer

```typescript
// Query: page, size, status

// Response
type MySubmittedWorkflowsResponse = PagedResponse<SubmittedWorkflowItem>;

interface SubmittedWorkflowItem {
  instanceId: string;
  workflowName: string;
  entityType: string;
  entityId: string;
  status: string;
  currentStepName?: string;
  submittedAt: string;
  completedAt?: string;
}
```

---

#### GET `/api/workflows/{instanceId}/history`
**Auth**: Bearer

```typescript
// Response
interface WorkflowHistoryResponse {
  instanceId: string;
  workflowName: string;
  status: string;
  steps: WorkflowStepHistory[];
}

interface WorkflowStepHistory {
  stepName: string;
  assigneeName: string;
  action: string;
  comment?: string;
  actionedAt?: string;
  delegatedToName?: string;
}
```

---

### 4.5 System Config & Feature Flags

#### GET `/api/admin/configs`
**FR**: FR-014
**Auth**: Bearer+Admin

```typescript
// Query: category

// Response
interface SystemConfigListResponse {
  configs: SystemConfigItem[];
}

interface SystemConfigItem {
  key: string;
  value: string;
  type: "STRING" | "NUMBER" | "BOOLEAN" | "JSON";
  category: string;
  description?: string;
  isPublic: boolean;
}
```

---

#### PUT `/api/admin/configs/{key}`
**Auth**: Bearer+Admin

```typescript
// Request
interface UpdateConfigRequest {
  value: string;
}

// Response
type UpdateConfigResponse = SystemConfigItem;
```

---

#### GET `/api/admin/feature-flags`
**Auth**: Bearer+Admin

```typescript
// Response
interface FeatureFlagListResponse {
  flags: FeatureFlagItem[];
}

interface FeatureFlagItem {
  key: string;
  enabled: boolean;
  rolloutPercentage: number;
  targetRoles?: string[];
  description?: string;
  expiresAt?: string;
}
```

---

#### PUT `/api/admin/feature-flags/{key}`
**Auth**: Bearer+Admin

```typescript
// Request
interface ToggleFeatureFlagRequest {
  enabled: boolean;
  rolloutPercentage?: number;
  targetRoles?: string[];
}

// Response
type ToggleFeatureFlagResponse = FeatureFlagItem;
```

---

### 4.6 Audit Trail

#### GET `/api/admin/audit-logs`
**FR**: FR-015
**Auth**: Bearer+Admin

```typescript
// Query: page, size, userId, actionType, entityType, from, to, module

// Response
type AuditLogListResponse = PagedResponse<AuditLogItem>;

interface AuditLogItem {
  id: string;
  userId: string;
  username: string;
  actionType: string;           // "CREATE" | "UPDATE" | "DELETE" | "LOGIN" | etc.
  entityType: string;
  entityId: string;
  description: string;
  ipAddress: string;
  module: string;
  timestamp: string;
  oldValue?: Record<string, any>;  // visibility: internal — sensitive fields masked
  newValue?: Record<string, any>;  // visibility: internal
}
```

---

#### GET `/api/admin/audit-logs/export`
**Auth**: Bearer+Admin

```typescript
// Query: same filters as list + format ("CSV" | "EXCEL")

// Response: file download or
interface AuditExportResponse {
  downloadUrl: string;
  expiresAt: string;
  recordCount: number;
}
```

---

## 5. Error Codes Reference

| Code | HTTP | Service | Description |
|------|------|---------|-------------|
| `AUTH_INVALID_CREDENTIALS` | 401 | auth | Wrong username/password |
| `AUTH_ACCOUNT_LOCKED` | 423 | auth | Account locked |
| `AUTH_ACCOUNT_DISABLED` | 403 | auth | Account disabled |
| `AUTH_CAPTCHA_REQUIRED` | 428 | auth | CAPTCHA required |
| `AUTH_CAPTCHA_INVALID` | 400 | auth | Invalid CAPTCHA |
| `AUTH_DOMAIN_NOT_FOUND` | 404 | auth | Domain not found |
| `AUTH_TOKEN_EXPIRED` | 401 | auth | Token expired |
| `AUTH_TOKEN_REVOKED` | 401 | auth | Token revoked |
| `MFA_INVALID_CODE` | 401 | auth | Wrong MFA code |
| `MFA_CODE_EXPIRED` | 410 | auth | OTP expired |
| `MFA_MAX_ATTEMPTS` | 429 | auth | Max MFA attempts |
| `MFA_TOKEN_INVALID` | 401 | auth | Partial token invalid |
| `PWD_WRONG_CURRENT` | 400 | auth | Wrong current password |
| `PWD_POLICY_VIOLATION` | 400 | auth | Policy violation |
| `PWD_RECENTLY_USED` | 400 | auth | Recently used password |
| `SSO_PROVIDER_NOT_FOUND` | 404 | auth | SSO provider not found |
| `SSO_CALLBACK_FAILED` | 400 | auth | SSO callback error |
| `MENU_NOT_FOUND` | 404 | system-admin | Menu item not found |
| `MENU_CODE_DUPLICATE` | 409 | system-admin | Duplicate menu code |
| `MENU_MAX_DEPTH` | 400 | system-admin | Max tree depth exceeded |
| `DEPT_NOT_FOUND` | 404 | system-admin | Department not found |
| `DEPT_CODE_DUPLICATE` | 409 | system-admin | Duplicate department code |
| `DEPT_HAS_CHILDREN` | 409 | system-admin | Cannot delete — has children |
| `PARTNER_NOT_FOUND` | 404 | system-admin | Partner not found |
| `API_KEY_EXPIRED` | 401 | system-admin | API key expired |
| `API_KEY_REVOKED` | 401 | system-admin | API key revoked |
| `RATE_LIMIT_EXCEEDED` | 429 | gateway | Rate limit exceeded |
| `QUOTA_EXCEEDED` | 429 | system-admin | Monthly quota exceeded |
| `WF_NOT_FOUND` | 404 | system-admin | Workflow not found |
| `WF_INSTANCE_NOT_FOUND` | 404 | system-admin | Workflow instance not found |
| `WF_NOT_ASSIGNEE` | 403 | system-admin | Not assigned to this step |
| `WF_ALREADY_ACTIONED` | 409 | system-admin | Step already actioned |
| `PROFILE_NOT_FOUND` | 404 | account | Profile not found |
| `DEVICE_NOT_FOUND` | 404 | account | Device not found |
| `SESSION_NOT_FOUND` | 404 | account | Session not found |

---

## 6. State Contracts (Frontend State Shape)

```typescript
// Auth Store
interface AuthState {
  isAuthenticated: boolean;
  user: UserBrief | null;
  accessToken: string | null;
  refreshToken: string | null;
  mfaPending: boolean;
  partialToken: string | null;
  availableMfaMethods: MfaMethod[];
}

// Menu Store
interface MenuState {
  userMenuTree: UserMenuNode[];
  loading: boolean;
  lastFetchedAt: string | null;
}

// Account Store
interface AccountState {
  profile: UserProfileResponse | null;
  preferences: PreferenceItem[];
  devices: DeviceItem[];
  sessions: SessionItem[];
}

// Admin Store (partial — per-page)
interface AdminMenuState {
  fullMenuTree: MenuTreeNode[];
  selectedRoleMenus: RoleMenuDetail[];
}

interface AdminOrgState {
  departmentTree: DepartmentNode[];
  positions: PositionItem[];
}

interface AdminPartnerState {
  partners: PartnerItem[];
  selectedPartnerUsage: PartnerUsageResponse | null;
}

interface AdminWorkflowState {
  pendingApprovals: PendingWorkflowItem[];
  submittedRequests: SubmittedWorkflowItem[];
}
```

---

## 7. Endpoint Summary

| Service | Public | Bearer | Bearer+Admin | Internal | Total |
|---------|:---:|:---:|:---:|:---:|:---:|
| auth-service | 8 | 7 | 0 | 3 | **18** |
| account-service | 0 | 16 | 5 | 0 | **21** |
| system-admin-service | 0 | 8 | 26 | 0 | **34** |
| **Total** | **8** | **31** | **31** | **3** | **73** |
