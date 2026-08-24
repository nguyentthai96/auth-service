# API Specs: erp-iam-system

_Generated: 2025-07-15_
_Profile: Non-Financial | N/A | EXTEND_

---

## 1. auth-service API Endpoints

### 1.1 MFA Enhancement (FR-001, FR-002)

#### POST /api/auth/mfa/recovery-codes
- **Description**: Generate MFA recovery codes (10 single-use codes)
- **Auth**: Bearer JWT (full)
- **Request**: None (uses authenticated user)
- **Response** (200):
```json
{
  "codes": ["ABC12345DEFG6789", "..."],
  "generatedAt": "2025-07-15T10:00:00Z",
  "count": 10,
  "message": "Store these codes securely. Each code can only be used once."
}
```
- **Error**: `AUTH_011` (MFA not enabled), `AUTH_019` (rate limited)

#### POST /api/auth/mfa/recovery-codes/verify
- **Description**: Verify recovery code (used during MFA verification as alternative to TOTP/OTP)
- **Auth**: Bearer JWT (partial — scope `/verify-2fa`)
- **Request**:
```json
{
  "code": "ABC12345DEFG6789"
}
```
- **Response** (200): Same as `/verify-2fa` — full JWT issued
- **Error**: `AUTH_011` (invalid code), `AUTH_012` (session expired), `AUTH_013` (max attempts)

#### POST /api/auth/mfa/recovery-codes/regenerate
- **Description**: Regenerate all recovery codes (invalidates existing)
- **Auth**: Bearer JWT (full)
- **Request**: None
- **Response** (200): Same as generate endpoint

### 1.2 Session Management (FR-008)

#### GET /api/auth/sessions
- **Description**: List user's active sessions
- **Auth**: Bearer JWT
- **Response** (200):
```json
{
  "sessions": [
    {
      "sessionId": "123456789",
      "deviceInfo": "Chrome/120 on Windows",
      "ipAddress": "192.168.1.1",
      "loginAt": "2025-07-15T08:00:00Z",
      "lastActiveAt": "2025-07-15T10:30:00Z",
      "current": true
    }
  ],
  "maxConcurrent": 5,
  "totalActive": 2
}
```

#### DELETE /api/auth/sessions/{sessionId}
- **Description**: Terminate specific session
- **Auth**: Bearer JWT
- **Response** (200): `{"message": "Session terminated"}`
- **Error**: `AUTH_005` (session not found)

#### GET /api/admin/sessions
- **Description**: Admin list all sessions (paginated)
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Query**: `?page=0&size=20&userId={userId}`
- **Response** (200): Paginated session list

#### DELETE /api/admin/sessions/{userId}
- **Description**: Admin terminate all user sessions
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Response** (200): `{"message": "All sessions terminated", "count": 3}`

### 1.3 Account Lifecycle GDPR (FR-009)

#### POST /api/auth/account/deactivate
- **Description**: Deactivate user account
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "reason": "User requested deactivation",
  "password": "currentPassword123"
}
```
- **Response** (200): `{"message": "Account deactivated", "reactivateDeadline": "2025-08-14T10:00:00Z"}`
- **Side Effect**: Publishes `AccountDeactivatedEvent` to Kafka

#### POST /api/auth/account/deletion-request
- **Description**: Request GDPR data deletion
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "confirmationPhrase": "DELETE MY DATA",
  "password": "currentPassword123"
}
```
- **Response** (200):
```json
{
  "requestId": "123456789",
  "gracePeriod": "30 days",
  "scheduledDeletion": "2025-08-14T10:00:00Z"
}
```

#### GET /api/auth/account/export
- **Description**: Export personal data (JSON)
- **Auth**: Bearer JWT
- **Response** (200): JSON file download containing auth data, profile, sessions, audit

### 1.4 Inter-service Auth (FR-021)

#### POST /api/internal/auth/service-token
- **Description**: Generate service JWT for inter-service communication
- **Auth**: Shared secret (Authorization: Basic)
- **Request**:
```json
{
  "serviceName": "system-admin-service",
  "scope": "INTERNAL"
}
```
- **Response** (200):
```json
{
  "token": "eyJ...",
  "expiresIn": 3600
}
```
- **Error**: `AUTH_050` (invalid service credentials), `AUTH_052` (service not registered)

### 1.5 Domain Config (FR-016)

#### PUT /api/admin/domains/{domainCode}/branding
- **Description**: Update domain branding
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "logoUrl": "https://cdn.example.com/logo.png",
  "primaryColor": "#1A73E8",
  "loginPageConfig": {
    "backgroundImage": "https://cdn.example.com/bg.jpg",
    "welcomeMessage": "Welcome to ERP System",
    "showSocialLogin": true
  }
}
```
- **Response** (200): Updated domain config

---

## 2. account-service API Endpoints

### 2.1 Profile (FR-005)

#### GET /api/account/profile
- **Description**: Get current user profile
- **Auth**: Bearer JWT
- **Response** (200):
```json
{
  "userId": 123456789,
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "emailVerified": true,
  "phone": "+84901234567",
  "phoneVerified": false,
  "avatarUrl": "https://cdn.example.com/avatars/123.jpg",
  "completenessScore": 85,
  "missingFields": ["phone_verified"],
  "contacts": [
    {"type": "WORK_EMAIL", "value": "a@company.com", "verified": true}
  ]
}
```

#### PUT /api/account/profile
- **Description**: Update profile
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "fullName": "Nguyen Van A Updated",
  "phone": "+84901234567"
}
```
- **Response** (200): Updated profile
- **Error**: `ACCT_002` (duplicate email), `ACCT_003` (invalid format)

#### POST /api/account/profile/avatar
- **Description**: Upload avatar
- **Auth**: Bearer JWT
- **Content-Type**: multipart/form-data
- **Request**: File field `avatar` (max 5MB, jpg/png/webp)
- **Response** (200): `{"avatarUrl": "https://cdn.example.com/avatars/123.jpg"}`

#### POST /api/account/profile/verify-email
- **Description**: Initiate email verification
- **Auth**: Bearer JWT
- **Request**: `{"email": "a@example.com"}`
- **Response** (200): `{"message": "Verification code sent to email"}`

### 2.2 Preferences (FR-006)

#### GET /api/account/preferences
- **Description**: Get all preferences
- **Auth**: Bearer JWT
- **Response** (200):
```json
{
  "preferences": {
    "notification": {"email": true, "sms": false, "push": true},
    "display": {"language": "vi", "theme": "light", "timezone": "Asia/Ho_Chi_Minh"},
    "privacy": {"profileVisible": true, "activityTracking": false}
  }
}
```

#### PATCH /api/account/preferences
- **Description**: Merge-update preferences
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "notification": {"sms": true},
  "display": {"theme": "dark"}
}
```
- **Response** (200): Full merged preferences

### 2.3 Device Management (FR-007)

#### GET /api/account/devices
- **Description**: List user devices
- **Auth**: Bearer JWT
- **Response** (200):
```json
{
  "devices": [
    {
      "deviceId": 123456789,
      "fingerprint": "abc123...",
      "name": "Chrome/120 on Windows",
      "lastActive": "2025-07-15T10:00:00Z",
      "trusted": true,
      "trustedExpiry": "2025-08-14T10:00:00Z"
    }
  ],
  "maxTrusted": 5,
  "trustedCount": 2
}
```

#### POST /api/account/devices/trust
- **Description**: Trust device
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "fingerprint": "abc123def456...",
  "name": "My Work Laptop"
}
```
- **Response** (200): Updated device with trust info
- **Error**: `ACCT_006` (max trusted devices), `ACCT_007` (already trusted)

#### DELETE /api/account/devices/{deviceId}/trust
- **Description**: Untrust device
- **Auth**: Bearer JWT
- **Response** (200): `{"message": "Device untrusted"}`

#### GET /api/internal/devices/{userId}/trusted
- **Description**: Internal: check if device is trusted (called by auth-service for MFA skip)
- **Auth**: Service JWT
- **Query**: `?fingerprint={hash}`
- **Response** (200):
```json
{
  "trusted": true,
  "trustedExpiry": "2025-08-14T10:00:00Z"
}
```

---

## 3. system-admin-service API Endpoints

### 3.1 Menu Permission (FR-010)

#### GET /api/admin/menus/user-tree
- **Description**: Get user-filtered menu tree (cached in Redis, TTL 5min)
- **Auth**: Bearer JWT
- **Response** (200):
```json
{
  "tree": [
    {
      "id": 1,
      "code": "dashboard",
      "name": "Dashboard",
      "type": "MENU",
      "icon": "dashboard",
      "path": "/dashboard",
      "children": [],
      "permissions": ["VIEW_DASHBOARD"]
    }
  ],
  "cachedAt": "2025-07-15T10:00:00Z"
}
```

#### POST /api/admin/menus
- **Description**: Create menu item
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "code": "user-management",
  "name": "User Management",
  "type": "MENU",
  "parentId": 1,
  "icon": "people",
  "path": "/admin/users",
  "sortOrder": 10,
  "domainId": 1
}
```
- **Response** (201): Created menu item
- **Error**: `SYS_002` (circular reference), `SYS_003` (max depth exceeded)

#### POST /api/admin/menus/{id}/permissions
- **Description**: Assign role permissions to menu item
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "roleId": 123,
  "action": "GRANT"
}
```
- **Response** (200): Updated permissions

#### POST /api/admin/menus/{id}/user-override
- **Description**: Set user-level permission override
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "userId": 456,
  "action": "DENY"
}
```
- **Response** (200): Updated override

### 3.2 Organization (FR-011)

#### GET /api/admin/departments/tree
- **Description**: Get department hierarchy tree
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Response** (200):
```json
{
  "tree": [
    {
      "id": 1,
      "code": "HQ",
      "name": "Headquarters",
      "headUserId": 100,
      "headName": "Director A",
      "userCount": 25,
      "children": [
        {
          "id": 2,
          "code": "IT",
          "name": "IT Department",
          "userCount": 10,
          "children": []
        }
      ]
    }
  ]
}
```

#### POST /api/admin/departments/{id}/transfer-user
- **Description**: Transfer user between departments
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "userId": 456,
  "toPositionId": 789
}
```
- **Response** (200): Transfer confirmation with audit trail reference

#### GET /api/admin/org-chart
- **Description**: Org chart view (recursive CTE query)
- **Auth**: Bearer JWT
- **Response** (200): Hierarchical org structure with user counts

### 3.3 API Partner (FR-012)

#### POST /api/admin/partners
- **Description**: Onboard new API partner
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "name": "Partner Corp",
  "contactEmail": "api@partner.com",
  "rateLimitPerMinute": 100,
  "burstLimit": 20,
  "ipWhitelist": ["10.0.0.0/8", "192.168.1.0/24"]
}
```
- **Response** (201): Partner with generated API key (show-once)
```json
{
  "partnerId": 123,
  "name": "Partner Corp",
  "apiKey": "ntt_pk_abc123def456...",
  "keyId": 456,
  "message": "Save this API key. It will not be shown again."
}
```

#### POST /api/admin/partners/{id}/keys/{keyId}/rotate
- **Description**: Rotate API key
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Response** (200): New API key (show-once), old key revoked

#### PUT /api/admin/partners/{id}/rate-limit
- **Description**: Update rate limit config
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "requestsPerMinute": 200,
  "burstLimit": 40
}
```
- **Response** (200): Updated config
- **Side Effect**: Pushes config to Redis for Gateway Bucket4j

#### GET /api/admin/partners/{id}/usage
- **Description**: Usage dashboard
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Query**: `?period=7d`
- **Response** (200):
```json
{
  "partnerId": 123,
  "period": "7d",
  "totalRequests": 15000,
  "successRate": 99.2,
  "avgResponseTime": 120,
  "topEndpoints": [
    {"path": "/api/data/users", "count": 5000},
    {"path": "/api/data/orders", "count": 3000}
  ],
  "rateLimitHits": 15,
  "quotaUsage": "75%"
}
```

### 3.4 Approval Workflow (FR-013)

#### POST /api/admin/workflows
- **Description**: Create workflow definition
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "name": "Purchase Approval",
  "entityType": "PURCHASE_ORDER",
  "steps": [
    {
      "stepOrder": 1,
      "approverType": "ROLE",
      "approverValue": "MANAGER",
      "timeoutHours": 48,
      "conditions": {
        "operator": "AND",
        "conditions": [
          {"field": "amount", "op": "GT", "value": 10000}
        ]
      }
    }
  ]
}
```
- **Response** (201): Created workflow definition (versioned, immutable)

#### POST /api/admin/workflows/submit
- **Description**: Submit entity for approval
- **Auth**: Bearer JWT
- **Request**:
```json
{
  "workflowDefinitionId": 1,
  "entityType": "PURCHASE_ORDER",
  "entityId": 456,
  "entityData": {"amount": 50000, "department": "FINANCE"}
}
```
- **Response** (201): Workflow instance with first step details

#### POST /api/admin/workflows/instances/{id}/approve
- **Description**: Approve current step
- **Auth**: Bearer JWT (must be assigned approver)
- **Request**:
```json
{
  "comment": "Approved",
  "attachments": []
}
```
- **Response** (200): Updated instance (advanced to next step or APPROVED)

#### POST /api/admin/workflows/instances/{id}/delegate
- **Description**: Delegate approval to another user
- **Auth**: Bearer JWT (must be assigned approver)
- **Request**:
```json
{
  "delegateTo": 789,
  "reason": "Out of office"
}
```
- **Response** (200): Updated step with new approver

### 3.5 System Config (FR-014)

#### PUT /api/admin/config/{key}
- **Description**: Update config (versioned)
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "value": "{\"maxRetries\": 5}",
  "type": "JSON",
  "domainId": 1
}
```
- **Response** (200): Updated config with version number
- **Side Effect**: Creates config_history record

#### GET /api/admin/config/{key}/history
- **Description**: Config change history
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Response** (200): List of config changes with old/new values, timestamps, actors

#### PUT /api/admin/feature-flags/{id}
- **Description**: Update feature flag
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Request**:
```json
{
  "enabled": true,
  "rolloutPercentage": 50,
  "targetSegments": ["BETA_USERS"],
  "domainId": 1
}
```
- **Response** (200): Updated feature flag

### 3.6 Audit Trail (FR-015)

#### GET /api/admin/audit-logs
- **Description**: Search audit logs
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Query**: `?actorId=123&entityType=USER&action=UPDATE&from=2025-07-01&to=2025-07-15&page=0&size=20`
- **Response** (200):
```json
{
  "logs": [
    {
      "id": 123456,
      "actorId": 100,
      "actorName": "Admin A",
      "action": "UPDATE",
      "entityType": "USER",
      "entityId": 456,
      "changes": {
        "fullName": {"old": "Nguyen A", "new": "Nguyen A Updated"},
        "password": {"old": "***", "new": "***"}
      },
      "ipAddress": "192.168.1.1",
      "timestamp": "2025-07-15T10:00:00Z"
    }
  ],
  "page": 0,
  "totalPages": 5,
  "totalElements": 95
}
```

#### GET /api/admin/audit-logs/export
- **Description**: Export audit logs
- **Auth**: Bearer JWT + ROLE_ADMIN
- **Query**: `?format=JSON&from=2025-07-01&to=2025-07-15`
- **Response** (200): File download (CSV or JSON)
- **Error**: `AUTH_053` (export failed)

---

## 4. Common Response Patterns

### 4.1 Success Response
```json
{
  "data": { ... },
  "message": "Operation successful",
  "timestamp": "2025-07-15T10:00:00Z"
}
```

### 4.2 Error Response (RFC 7807)
```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Password doesn't meet policy requirements",
  "instance": "/api/auth/change-password",
  "errorCode": "AUTH_017",
  "timestamp": "2025-07-15T10:00:00Z"
}
```

### 4.3 Paginated Response
```json
{
  "data": [...],
  "page": 0,
  "size": 20,
  "totalPages": 5,
  "totalElements": 95,
  "hasNext": true
}
```

---

## 5. Headers

| Header | Usage | Required |
|--------|-------|:---:|
| `Authorization` | `Bearer {JWT}` — user/admin auth | ✅ |
| `X-API-Key` | API partner authentication | For partner APIs |
| `X-Idempotency-Key` | Prevent duplicate create/update | For POST/PUT |
| `X-Correlation-Id` | Request tracing | Auto-generated |
| `X-Domain-Code` | Multi-tenant domain identification | Optional |
| `Accept-Language` | I18n response language | Optional (default: vi) |
