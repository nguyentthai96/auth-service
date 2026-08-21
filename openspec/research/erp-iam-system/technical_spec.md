# Đặc tả kỹ thuật: ERP IAM System

> Technical specification chi tiết — thiết kế để agent có thể đọc và dev code trực tiếp.

---

## 1. Tổng quan hệ thống (System Overview)

### 1.1 Kiến trúc tổng thể

```mermaid
graph TB
    subgraph "Client Layer"
        WEB["Web SPA"]
        MOBILE["Mobile App"]
        PARTNER["API Partner"]
    end

    subgraph "Edge Layer"
        GW["API Gateway<br/>Rate Limit + Auth"]
    end

    subgraph "Service Layer"
        subgraph "auth-service"
            A_AUTH["auth module"]
            A_RBAC["rbac module"]
            A_PBAC["pbac module"]
        end
        subgraph "account-service"
            B_PROF["profile module"]
            B_DEV["device module"]
            B_SES["session module"]
            B_PREF["preference module"]
            B_LIFE["lifecycle module"]
        end
        subgraph "system-admin-service"
            C_MENU["menu module"]
            C_ORG["organization module"]
            C_API["api-partner module"]
            C_WF["workflow module"]
            C_AUDIT["audit module"]
            C_CONF["config module"]
        end
    end

    subgraph "Data Layer"
        DB_AUTH["PostgreSQL<br/>auth_db"]
        DB_ACC["PostgreSQL<br/>account_db"]
        DB_SYS["PostgreSQL<br/>system_admin_db"]
        REDIS["Redis Cluster"]
    end

    subgraph "External"
        KC["Keycloak<br/>(optional)"]
        SMS["SMS Provider"]
        EMAIL["Email Provider"]
    end

    WEB --> GW
    MOBILE --> GW
    PARTNER --> GW
    GW --> A_AUTH
    GW --> B_PROF
    GW --> C_MENU

    A_AUTH --> DB_AUTH
    A_AUTH --> REDIS
    B_PROF --> DB_ACC
    B_SES --> REDIS
    C_MENU --> DB_SYS
    C_MENU --> REDIS
```

### 1.2 Technology Stack

| Layer | Technology | Version | Ghi chú |
|-------|-----------|---------|---------|
| Language | Kotlin | 2.4.x | Primary language |
| JDK | OpenJDK | 25 | Virtual threads enabled |
| Framework | Spring Boot | 4.1.0 | Spring Framework 7, Spring Security 7 |
| Database | PostgreSQL | 17+ | Separate DB per service |
| Cache | Redis | 7.x | Permission cache, rate limiting, OTP state |
| Cache L1 | Caffeine | Latest | In-process cache via AbstractTwoTierCache |
| ORM | Spring Data JPA + Hibernate | Latest | Via base-data-starter |
| Migration | Flyway | Latest | Via base-data-starter |
| Security | Spring Security + JJWT | Latest | RS256 + HMAC-SHA256 |
| MFA | dev.samstevens.totp + Passay | 1.7.1 / 1.6.4 | TOTP + password policy |
| Rate Limiting | Bucket4j | Latest | Token bucket + Redis ProxyManager |
| Messaging | Spring Kafka | Runtime | Inter-service events (async) |
| E2EE | Google Tink | 1.15.0 | End-to-end encryption |
| OAuth2 | spring-boot-starter-oauth2-client + resource-server | Latest | SSO integration |
| Build | Gradle | 8.x | Convention plugins |
| Base | com.ntt:platform BOM | 0.0.1-SNAPSHOT | base-web-starter, base-data-starter, base-security-starter, common-log |

### 1.3 Dependencies & Integrations

| Dependency | Type | Purpose | Interface |
|-----------|------|---------|-----------|
| auth-service | Internal | Authentication + Authorization | REST API |
| account-service | Internal | User profile management | REST API |
| system-admin-service | Internal | Menu, Org, API Partner, Workflow | REST API |
| Keycloak | External (Optional) | SSO/OIDC delegation | OAuth2/OIDC (via SsoAdapter) |
| SMS Provider | External | OTP delivery | Adapter pattern (HTTP) |
| Email Provider | External | OTP + notifications | Adapter pattern (SMTP/HTTP) |

---

## 2. Lược đồ dữ liệu (Data Schema)

### 2.1 ERD — Auth Service (Existing + Enhanced)

```mermaid
erDiagram
    users ||--o{ user_domains : "belongs to"
    users ||--o{ user_groups : "member of"
    users ||--o{ mfa_configs : "has"
    users ||--o{ recovery_codes : "has"
    users ||--o{ user_sso_links : "linked"
    users ||--o{ refresh_tokens : "has"
    users ||--o{ password_history : "has"

    domains ||--o{ user_domains : "contains"
    domains ||--o{ domain_roles : "defines"
    domains ||--o{ domain_resources : "owns"
    domains ||--o{ policies : "scoped"
    domains ||--o{ password_policies : "configures"
    domains ||--o{ sso_providers : "integrates"

    groups ||--o{ user_groups : "contains"
    groups ||--o{ group_roles : "assigned"
    domain_roles ||--o{ group_roles : "linked"
    domain_roles ||--o{ role_permissions : "grants"

    domain_resources ||--o{ permissions : "defines"
    actions ||--o{ permissions : "defines"
    permissions ||--o{ role_permissions : "granted via"

    policies ||--o{ policy_conditions : "has"
    sso_providers ||--o{ user_sso_links : "provides"

    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar status
        int failed_login_count
        timestamp locked_until_at
        boolean mfa_enabled
        varchar mfa_method
        int version
    }

    mfa_configs {
        bigint id PK
        bigint user_id FK
        varchar method
        varchar secret_encrypted
        boolean is_active
        timestamp created_at
    }

    otp_tokens {
        bigint id PK
        bigint user_id FK
        varchar code_hash
        varchar channel
        timestamp expires_at
        boolean verified
        int attempts
    }

    recovery_codes {
        bigint id PK
        bigint user_id FK
        varchar code_hash
        timestamp used_at
    }

    sso_providers {
        bigint id PK
        varchar code UK
        varchar name
        varchar provider_type
        varchar client_id
        varchar client_secret_enc
        varchar discovery_url
        varchar scopes
        boolean auto_provision
        varchar status
    }

    user_sso_links {
        bigint id PK
        bigint user_id FK
        bigint provider_id FK
        varchar external_sub
        varchar external_email
        timestamp linked_at
    }

    password_policies {
        bigint id PK
        bigint domain_id FK
        int min_length
        boolean require_uppercase
        boolean require_lowercase
        boolean require_digit
        boolean require_special
        int max_age_days
        int history_count
    }

    password_history {
        bigint id PK
        bigint user_id FK
        varchar password_hash
        timestamp changed_at
    }
```

### 2.2 ERD — Account Service (Existing)

```mermaid
erDiagram
    user_profiles ||--o{ user_contacts : "has"
    user_profiles ||--o{ login_history : "tracks"
    user_profiles ||--o{ user_preferences : "configures"
    user_profiles ||--o{ notification_settings : "configures"
    user_profiles ||--o{ user_devices : "owns"
    user_devices ||--o{ active_sessions : "has"

    user_profiles {
        bigint user_id PK
        varchar display_name
        varchar first_name
        varchar last_name
        date date_of_birth
        varchar gender
        varchar address
        varchar city
        varchar country
        varchar timezone
        varchar locale
        varchar avatar_url
        text bio
        jsonb metadata
    }

    user_contacts {
        bigint id PK
        bigint user_id FK
        varchar contact_type
        varchar contact_value
        boolean is_primary
        boolean is_verified
        timestamp verified_at
    }

    login_history {
        bigint id PK
        bigint user_id FK
        varchar ip_address
        varchar user_agent
        varchar device_fingerprint
        timestamp login_at
        varchar login_method
        varchar status
    }

    user_devices {
        bigint id PK
        bigint user_id FK
        varchar device_id UK
        varchar device_name
        varchar device_type
        varchar os
        varchar browser
        boolean trusted
        timestamp trusted_until
        varchar status
    }

    active_sessions {
        varchar session_id PK
        bigint user_id FK
        bigint device_id FK
        varchar token_jti
        timestamp created_at
        timestamp last_accessed_at
        timestamp expires_at
        varchar status
    }
```

### 2.3 ERD — System Admin Service (Existing)

```mermaid
erDiagram
    menu_items ||--o{ menu_items : "children"
    menu_items ||--o{ menu_permissions : "has"
    menu_permissions ||--o{ role_menu_permissions : "assigned"
    menu_items ||--o{ user_menu_overrides : "overridden"

    departments ||--o{ departments : "children"
    departments ||--o{ positions : "contains"
    positions ||--o{ user_positions : "assigned"

    api_partners ||--o{ api_keys : "owns"
    subscription_plans ||--o{ api_partners : "subscribes"
    api_keys ||--o{ api_usage_logs : "tracks"

    workflow_definitions ||--o{ workflow_steps : "has"
    workflow_definitions ||--o{ workflow_instances : "instantiates"
    workflow_instances ||--o{ workflow_step_instances : "tracks"

    menu_items {
        bigint id PK
        bigint parent_id FK
        bigint domain_id FK
        varchar code UK
        varchar name
        varchar icon
        varchar path
        varchar route_name
        varchar component
        int sort_order
        varchar menu_type
        boolean is_visible
        boolean is_cacheable
        varchar status
        jsonb metadata
    }

    menu_permissions {
        bigint id PK
        bigint menu_id FK
        varchar permission_code
        varchar name
        varchar description
    }

    role_menu_permissions {
        bigint id PK
        bigint role_id FK
        bigint menu_id FK
        varchar permission_code
        boolean is_granted
    }

    departments {
        bigint id PK
        bigint parent_id FK
        bigint domain_id FK
        varchar code UK
        varchar name
        bigint manager_user_id FK
        int sort_order
        varchar status
        int level
    }

    positions {
        bigint id PK
        bigint domain_id FK
        varchar code
        varchar name
        bigint department_id FK
        int grade_level
        varchar status
    }

    api_partners {
        bigint id PK
        bigint domain_id FK
        varchar partner_name
        varchar partner_code UK
        varchar contact_email
        varchar status
        bigint subscription_plan_id FK
    }

    api_keys {
        bigint id PK
        bigint partner_id FK
        varchar key_prefix
        varchar key_hash UK
        varchar name
        jsonb scopes
        int rate_limit_per_second
        int rate_limit_per_day
        int quota_monthly
        jsonb ip_whitelist
        timestamp expires_at
        varchar status
    }

    workflow_definitions {
        bigint id PK
        bigint domain_id FK
        varchar code
        varchar name
        varchar entity_type
        int version
        varchar status
    }

    workflow_steps {
        bigint id PK
        bigint workflow_id FK
        int step_order
        varchar step_type
        varchar approver_type
        varchar approver_value
        jsonb condition
        int timeout_hours
        boolean is_parallel
    }

    workflow_instances {
        bigint id PK
        bigint workflow_definition_id FK
        varchar entity_type
        bigint entity_id
        bigint requester_user_id FK
        int current_step_order
        varchar status
        timestamp submitted_at
        timestamp completed_at
    }

    audit_logs {
        bigint id PK
        bigint domain_id FK
        bigint user_id FK
        varchar action_type
        varchar entity_type
        bigint entity_id
        jsonb old_value
        jsonb new_value
        varchar ip_address
        timestamp timestamp
        varchar module
    }
```

### 2.4 Database Migration Scripts (draft)

```sql
-- Auth Service: V10__mfa_sso_password_policy.sql
CREATE TABLE mfa_configs (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    method VARCHAR(20) NOT NULL,
    secret_encrypted VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE sso_providers (
    id BIGINT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    provider_type VARCHAR(20) NOT NULL,
    client_id VARCHAR(500) NOT NULL,
    client_secret_enc VARCHAR(500),
    discovery_url VARCHAR(500),
    scopes VARCHAR(500),
    auto_provision BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE password_policies (
    id BIGINT PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id),
    min_length INT NOT NULL DEFAULT 8,
    require_uppercase BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit BOOLEAN NOT NULL DEFAULT TRUE,
    require_special BOOLEAN NOT NULL DEFAULT FALSE,
    max_age_days INT NOT NULL DEFAULT 90,
    history_count INT NOT NULL DEFAULT 5,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
```

---

## 3. Luồng dữ liệu (Data Flow)

### 3.1 Data Flow Diagram — Level 0 (Context)

```mermaid
graph LR
    User["👤 User"] -->|"Login/2FA/Profile"| System["⚙️ IAM System"]
    Admin["👤 Admin"] -->|"Config Menu/Org/Partner"| System
    Partner["🤖 API Partner"] -->|"API Key Auth"| System
    System -->|"Read/Write"| DB[("📦 PostgreSQL")]
    System -->|"Cache/State"| Redis[("📦 Redis")]
    KC["🌐 Keycloak"] -->|"SSO Token"| System
    System -->|"Notification"| MQ["📨 Kafka"]
```

### 3.2 Data Transformation Rules

| # | Input | Process | Output | Validation Rules |
|---|-------|---------|--------|-----------------|
| 1 | Login request (username, password) | Validate via LoginHandler + check MFA | Partial/Full JWT token | NOT NULL, password encoder match (Argon2) |
| 2 | MFA verify (partial_token, code) | Validate via TotpService/OtpService, rate limit via MfaRateLimitService | Full JWT token | Code within window, max 3 attempts |
| 3 | API Key header (X-API-Key) | Hash + lookup via ApiPartnerService | Partner context | Key format `ntt_pk_` or `ntt_sk_`, SHA-256 hash match |
| 4 | Menu tree request (userId) | Resolve roles → filter menu via MenuPermissionService + TreeBuilder | Filtered menu tree JSON | User must be authenticated, domain context |

---

## 4. Luồng xử lý (Processing Steps)

### 4.1 Sequence Diagram — Login with MFA

```mermaid
sequenceDiagram
    actor User
    participant Controller as AuthController
    participant LoginHandler as LoginHandler
    participant MFA as MfaRateLimitService
    participant TOTP as TotpService
    participant Promotion as SessionPromotionService
    participant DB as PostgreSQL
    participant Redis as Redis

    User->>Controller: POST /api/auth/login {username, password, captcha}
    Controller->>Controller: Validate @Valid
    Controller->>LoginHandler: handle(LoginCommand)

    LoginHandler->>DB: findByUsername(username)
    DB-->>LoginHandler: UserEntity

    LoginHandler->>LoginHandler: Verify password (Argon2)

    alt MFA Enabled
        LoginHandler->>Redis: Store partial session
        LoginHandler-->>Controller: {partial_token, require_2fa: true, methods: ["TOTP"]}
        Controller-->>User: 200 OK (MFA Required)

        User->>Controller: POST /api/auth/verify-2fa {partial_token, code}
        Controller->>MFA: checkRateLimit(userId)
        MFA->>Redis: Get attempt count
        Controller->>TOTP: verify(secret, code)
        TOTP-->>Controller: VerifyResult(success)
        Controller->>Promotion: promote(partialSession)
        Promotion->>Redis: Clear partial session
        Promotion->>DB: Load roles, permissions
        Promotion-->>Controller: {access_token, refresh_token}
        Controller-->>User: 200 OK (Full Auth)
    else MFA Not Enabled
        LoginHandler->>DB: Load roles, permissions
        LoginHandler-->>Controller: {access_token, refresh_token}
        Controller-->>User: 200 OK (Full Auth)
    end
```

### 4.2 Sequence Diagram — User Menu Tree Load

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant SYS as system-admin-service
    participant MenuSvc as MenuPermissionService
    participant TreeBld as TreeBuilder
    participant AUTH as auth-service
    participant Redis as Redis
    participant DB as PostgreSQL

    User->>GW: GET /api/admin/menus/user-tree (JWT)
    GW->>GW: Validate JWT
    GW->>SYS: Forward request

    SYS->>Redis: GET user:{userId}:menu (via AbstractTwoTierCache)
    alt Cache Hit
        Redis-->>SYS: Cached menu tree
    else Cache Miss
        SYS->>AUTH: GET /api/permissions/user/{userId}/roles
        AUTH-->>SYS: {roles: ["ADMIN", "VIEWER"], permissions: [...]}
        SYS->>DB: Load menu_items + role_menu_permissions
        SYS->>MenuSvc: filterMenuByRoles(menuItems, roles)
        SYS->>DB: Check user_menu_overrides
        SYS->>MenuSvc: applyOverrides(filteredMenu, overrides)
        SYS->>TreeBld: buildTree(flatMenuItems)
        SYS->>Redis: SET user:{userId}:menu (TTL 5min)
    end
    SYS-->>User: {menu_tree with buttons}
```

### 4.3 State Machine — Workflow Instance

```mermaid
stateDiagram-v2
    [*] --> PENDING : Submit
    PENDING --> IN_PROGRESS : First step assigned
    IN_PROGRESS --> IN_PROGRESS : Step approved (more steps)
    IN_PROGRESS --> APPROVED : Last step approved
    IN_PROGRESS --> REJECTED : Step rejected
    REJECTED --> PENDING : Revise & resubmit (if configured)
    REJECTED --> CANCELLED : Terminal reject
    IN_PROGRESS --> ESCALATED : Timeout exceeded (WorkflowEscalationScheduler)
    ESCALATED --> IN_PROGRESS : Escalation step assigned
    PENDING --> CANCELLED : Requester cancels
    APPROVED --> [*]
    CANCELLED --> [*]
```

| Transition | From | To | Trigger | Guard Condition | Side Effect |
|-----------|------|-----|---------|----------------|------------|
| Submit | [*] | PENDING | User action | All required fields filled | Create workflow_instance |
| Assign | PENDING | IN_PROGRESS | System (WorkflowEngine) | First step resolved | Create step_instance, notify approver |
| Approve | IN_PROGRESS | IN_PROGRESS | Approver action | More steps remaining | Advance to next step |
| Approve (last) | IN_PROGRESS | APPROVED | Approver action | Last step | Mark complete, notify requester |
| Reject | IN_PROGRESS | REJECTED | Approver action | - | Notify requester |
| Escalate | IN_PROGRESS | ESCALATED | WorkflowEscalationScheduler | timeout_hours exceeded | Assign to escalation step |
| Cancel | PENDING | CANCELLED | Requester action | - | Close instance |

### 4.4 Screen Flow

| Screen ID | Tên | Mục đích | Data hiển thị | User Actions | Navigation |
|-----------|-----|----------|--------------|-------------|-----------|
| SCR-001 | Menu Management | Admin quản lý menu tree | Tree: menu items with drag-drop | Create, Edit, Delete, Reorder | → SCR-002 |
| SCR-002 | Menu Permission | Assign permissions to menu | Table: permissions per menu item | Add, Remove, Toggle | ← SCR-001 |
| SCR-003 | Role-Menu Assignment | Assign menus to role | Matrix: role × menu × permission | Toggle grants | - |
| SCR-004 | Department Management | Admin quản lý org tree | Tree: departments with managers | Create, Edit, Delete | → SCR-005 |
| SCR-005 | Position Management | CRUD positions | Table: positions per department | Create, Edit, Delete | ← SCR-004 |
| SCR-006 | API Partner Management | Partner + key management | Table: partners with keys | Register, Generate Key, Revoke | → SCR-007 |
| SCR-007 | Usage Dashboard | API usage analytics | Charts: requests/day, response times | Filter by date, export | ← SCR-006 |
| SCR-008 | Workflow Definitions | Admin create workflows | List: definitions with steps | Create, Edit, Activate | → SCR-009 |
| SCR-009 | Approval Queue | Pending approvals | Table: pending items with actions | Approve, Reject, Delegate | - |

---

## 6. API Specification

### 6.1 Auth Service APIs

| # | Method | Path | Description | Auth | Request Body | Response |
|---|--------|------|------------|------|-------------|----------|
| 1 | POST | `/api/auth/login` | Login (existing LoginHandler) | Public | LoginCommand | AuthResponse / MfaChallenge |
| 2 | POST | `/api/auth/verify-2fa` | Verify MFA code | Partial | VerifyMfaRequest | AuthResponse |
| 3 | POST | `/api/auth/request-otp` | Request OTP SMS/Email (OtpService) | Partial | RequestOtpRequest | 200 OK |
| 4 | POST | `/api/auth/forgot-password` | Initiate password reset | Public | ForgotPasswordRequest | 200 OK |
| 5 | POST | `/api/auth/reset-password` | Reset with token | Public | ResetPasswordRequest | 200 OK |
| 6 | POST | `/api/auth/change-password` | Change password | Bearer | ChangePasswordRequest | 200 OK |
| 7 | GET | `/api/auth/introspect` | Token introspection | Bearer | - | IntrospectResponse |
| 8 | GET | `/api/mfa/status` | MFA config status | Bearer | - | MfaStatusResponse |
| 9 | POST | `/api/mfa/enable` | Enable MFA method | Bearer | EnableMfaRequest | MfaSetupResponse |
| 10 | POST | `/api/mfa/disable` | Disable MFA | Bearer | DisableMfaRequest | 200 OK |
| 11 | GET | `/api/mfa/recovery-codes` | Generate recovery codes | Bearer | - | RecoveryCodesResponse |
| 12 | GET | `/api/sso/providers` | List SSO providers | Public | - | List<SsoProvider> |
| 13 | GET | `/api/sso/{provider}/authorize` | Initiate SSO (SsoAdapter) | Public | - | Redirect |
| 14 | POST | `/api/sso/{provider}/callback` | SSO callback | Public | OAuthCallback | AuthResponse |
| 15 | GET | `/api/permissions/user/{userId}` | User effective permissions | Internal | - | PermissionsResponse |

### 6.2 Account Service APIs

| # | Method | Path | Description | Auth |
|---|--------|------|------------|------|
| 1 | GET | `/api/account/profile` | Get profile (ProfileController) | Bearer |
| 2 | PUT | `/api/account/profile` | Update profile (ProfileService) | Bearer |
| 3 | POST | `/api/account/profile/avatar` | Upload avatar | Bearer |
| 4 | GET | `/api/account/devices` | List devices (DeviceController) | Bearer |
| 5 | POST | `/api/account/devices/{id}/trust` | Trust device (DeviceService) | Bearer |
| 6 | DELETE | `/api/account/devices/{id}` | Revoke device | Bearer |
| 7 | GET | `/api/account/sessions` | List sessions (SessionController) | Bearer |
| 8 | DELETE | `/api/account/sessions/{id}` | Terminate session (SessionService) | Bearer |
| 9 | GET | `/api/account/login-history` | Login history | Bearer |
| 10 | GET | `/api/account/preferences` | Get preferences (PreferenceController) | Bearer |
| 11 | PUT | `/api/account/preferences` | Update preferences (PreferenceService) | Bearer |
| 12 | POST | `/api/account/deactivate` | Deactivate account (AccountLifecycleService) | Bearer |
| 13 | POST | `/api/account/deletion-request` | GDPR deletion (AccountLifecycleService) | Bearer |
| 14 | GET | `/api/account/export` | GDPR export (DataExportService) | Bearer |

### 6.3 System Admin Service APIs

| # | Method | Path | Description | Auth |
|---|--------|------|------------|------|
| 1 | GET | `/api/admin/menus/tree` | Full menu tree (MenuPermissionService + TreeBuilder) | Bearer+Admin |
| 2 | GET | `/api/admin/menus/user-tree` | User's menu | Bearer |
| 3 | POST | `/api/admin/menus` | Create menu item | Bearer+Admin |
| 4 | PUT | `/api/admin/menus/{id}` | Update menu item | Bearer+Admin |
| 5 | DELETE | `/api/admin/menus/{id}` | Delete menu item | Bearer+Admin |
| 6 | POST | `/api/admin/roles/{roleId}/menus` | Assign menus to role | Bearer+Admin |
| 7 | GET | `/api/admin/departments/tree` | Department tree (DepartmentController + TreeBuilder) | Bearer |
| 8 | POST | `/api/admin/departments` | Create department (OrganizationService) | Bearer+Admin |
| 9 | PUT | `/api/admin/departments/{id}` | Update department | Bearer+Admin |
| 10 | GET | `/api/admin/positions` | List positions (PositionController) | Bearer |
| 11 | POST | `/api/admin/positions` | Create position (PositionService) | Bearer+Admin |
| 12 | POST | `/api/admin/user-positions` | Assign user position | Bearer+Admin |
| 13 | GET | `/api/admin/partners` | List partners (ApiPartnerService) | Bearer+Admin |
| 14 | POST | `/api/admin/partners` | Register partner | Bearer+Admin |
| 15 | POST | `/api/admin/partners/{id}/api-keys` | Generate API key | Bearer+Admin |
| 16 | POST | `/api/admin/api-keys/{id}/rotate` | Rotate API key | Bearer+Admin |
| 17 | DELETE | `/api/admin/api-keys/{id}` | Revoke API key | Bearer+Admin |
| 18 | GET | `/api/admin/partners/{id}/usage` | Usage dashboard (ApiUsageController) | Bearer+Admin |
| 19 | POST | `/api/admin/workflows` | Create workflow (WorkflowController) | Bearer+Admin |
| 20 | POST | `/api/workflows/submit` | Submit for approval (WorkflowService) | Bearer |
| 21 | POST | `/api/workflows/{id}/approve` | Approve step (WorkflowEngine) | Bearer |
| 22 | POST | `/api/workflows/{id}/reject` | Reject step | Bearer |
| 23 | POST | `/api/workflows/{id}/delegate` | Delegate step | Bearer |
| 24 | GET | `/api/workflows/my-pending` | My pending | Bearer |
| 25 | GET | `/api/admin/audit-logs` | Search audit logs (AuditController) | Bearer+Admin |
| 26 | GET | `/api/admin/audit-logs/export` | Export audit (AuditService) | Bearer+Admin |
| 27 | GET | `/api/admin/configs` | List configs (DomainConfigService) | Bearer+Admin |
| 28 | PUT | `/api/admin/configs/{key}` | Update config | Bearer+Admin |
| 29 | GET | `/api/admin/feature-flags` | List feature flags (FeatureFlagService) | Bearer+Admin |
| 30 | PUT | `/api/admin/feature-flags/{key}` | Update feature flag | Bearer+Admin |

### 6.4 Error Response Format (RFC 7807)

| HTTP Status | Error Type | Khi nào |
|-------------|-----------|---------|
| 400 | Validation Error | Input không hợp lệ |
| 401 | Unauthorized | Chưa đăng nhập hoặc MFA required |
| 403 | Forbidden | Không có quyền |
| 404 | Not Found | Resource không tồn tại |
| 409 | Conflict | Duplicate code, max keys exceeded |
| 422 | Unprocessable Entity | Business rule violation |
| 429 | Too Many Requests | Rate limit exceeded (Bucket4j) |
| 500 | Internal Server Error | Lỗi hệ thống |

---

## 7. Security Considerations

### 7.1 Authentication Flow

MFA progressive auth: Partial JWT (only allows `/verify-2fa`) → Full JWT (all authorized APIs) via SessionPromotionService. Trusted devices (UserDeviceEntity in account-service) skip MFA for configured TTL (30 days default).

### 7.2 Authorization Matrix

| Role | Menu Config | Org Config | API Partner | Workflow Def | Audit View | User Profile |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| SUPER_ADMIN | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (all) |
| DOMAIN_ADMIN | ✅ (own domain) | ✅ (own domain) | ✅ (own domain) | ✅ (own domain) | ✅ (own domain) | ✅ (own domain) |
| USER | ❌ | ❌ | ❌ | Submit only | ❌ | ✅ (own) |
| API_PARTNER | ❌ | ❌ | View own | ❌ | ❌ | ❌ |

### 7.3 Data Protection
- Password: Argon2id hashing (via BouncyCastle)
- TOTP secret: AES-256 encrypted at rest (Google Tink)
- API key: SHA-256 hash stored, raw key show-once
- E2EE: Google Tink for end-to-end encryption (DecryptionVaultService)
- Audit log: Immutable (no UPDATE/DELETE constraints)
- GDPR: Data export + deletion request workflow (DataExportService, AccountLifecycleService)

---

## 8. Performance Requirements

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| API response time (P95) | < 500ms | APM monitoring |
| Menu tree load (cached) | < 100ms | Redis GET latency (AbstractTwoTierCache) |
| Permission check (cached) | < 50ms | Caffeine L1 hit |
| Rate limit check | < 10ms | Bucket4j + Redis |
| Throughput (auth endpoints) | > 200 req/s | Load test |
| Cache hit ratio (permissions) | > 80% | Redis metrics |
| Database query time | < 100ms | Slow query log |

---

## 9. Agent Implementation Notes

> **Section này dành cho AI agent** — chỉ rõ code cần tạo/enhdler) | Follow existing pattern |
| Auth pattern | SecurityConfig filter chain + JwtAuthFilter | Same as existing |
| Base class | SnowflakePersistentAuditableEntity | All entities extend this |
| Tree class | TreeEntity (system-admin-service) | Menu items, departments extend this |
| Tree building | TreeBuilder utility | Use for tree construction |
| Error handling | Service-specific ControllerAdvice + ErrorCode enum | Each service has own handler |
| Caching | AbstractTwoTierCache | Caffeine L1 + Redis L2 |
| DTO mapping | Kotlin extension functions `toResponse()` + `Dtos.kt` | Follow existing convention |
| Idempotency | IdempotencyFilter | For POST endpoints |

### 9.6 Caching Strategy (Redis via AbstractTwoTierCache)

| Cache Key Pattern | TTL | Invalidation Event |
|-------------------|-----|-------------------|
| `user:{id}:permissions` | 5 min | Role/Permission change |
| `user:{id}:menu` | 5 min | Menu permission change |
| `otp:{userId}:{channel}` | 5 min | OTP verified/expired |
| `rate_limit:{apiKeyId}:{window}` | Per window | Auto-expire (Bucket4j) |
| `menu:tree:{domainId}` | 10 min | Menu item change |
| `config:{domainId}:{key}` | 30 min | Config update |

### 9.7 Inter-Service Communication (Kafka)

| Topic | Producer | Consumer | Event |
|-------|----------|----------|-------|
| `auth.user.events` | auth-service | account-service (ProfileKafkaListener), system-admin | user.registered, user.status.changed |
| `auth.permission.events` | auth-service | system-admin | permission.changed, role.changed |
| `system.org.events` | system-admin | auth-service | org.structure.changed |
| `system.audit.events` | all services | system-admin (AuditService) | audit.log.created |

### 9.8 Test Cases (high-level)

| # | Test | Type | Scenario | Expected |
|---|------|------|----------|----------|
| 1 | MFA enable TOTP | Integration | User enables TOTP, gets secret + QR | 200 + secret + recovery codes |
| 2 | Login with MFA | Integration | Password OK + TOTP code | 200 + full JWT |
| 3 | MFA wrong code 3x | Integration | 3 wrong TOTP codes | 401 + rate limit lock (MfaRateLimitService) |
| 4 | Menu tree create | Integration | Admin creates menu item | 201 + item in tree (TreeBuilder) |
| 5 | User menu filtered | Integration | User with limited roles | 200 + filtered menu tree |
| 6 | API key generate | Integration | Generate key for partner | 201 + raw key (show-once) |
| 7 | API rate limit | Integration | Exceed rate limit | 429 + Retry-After (Bucket4j) |
| 8 | Workflow submit | Integration | Submit entity for approval | 201 + instance created (WorkflowEngine) |
| 9 | Workflow approve | Integration | Approve pending step | 200 + next step assigned |
| 10 | Audit immutable | Integration | Try UPDATE on audit_logs | DB constraint error |

---

> **Traceability**: Research Brief → Business Analysis → **Technical Spec** → Implementation
> **Ready for**: `/wf_pre_openspec` hoặc `/wf_openspec` hoặc direct coding
