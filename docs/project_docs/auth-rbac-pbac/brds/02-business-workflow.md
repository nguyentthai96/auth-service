# BRD-02: Business Workflow

## WF-01: User Authentication Flow

```mermaid
flowchart TD
    START([User]) --> LOGIN{Has Account?}
    LOGIN -->|No| REGISTER[Register + Select Domain]
    LOGIN -->|Yes| CREDS[Enter Credentials]
    REGISTER --> VERIFY[Email Verification]
    VERIFY --> CREDS
    CREDS --> AUTH[Auth Service: Validate]
    AUTH --> LOAD[Load User Roles per Domain]
    LOAD --> JWT[Generate JWT with claims:<br/>userId, domains, roles, permissions]
    JWT --> RETURN[Return JWT + Refresh Token]
    RETURN --> CLIENT([Client stores token])
```

## WF-02: Authorization Check Flow (2-Stage)

```mermaid
flowchart TD
    REQ([API Request + JWT]) --> PARSE[Parse JWT Claims]
    PARSE --> STAGE1{Stage 1: Role Check}
    
    STAGE1 -->|Role has permission| RBAC_OK[RBAC: ALLOWED]
    STAGE1 -->|Role denied| DENY1[403 Forbidden]
    STAGE1 -->|Needs context| STAGE2
    
    RBAC_OK --> POLICY{Stage 2: Policy Check}
    STAGE2 --> POLICY
    
    POLICY -->|No policies| ALLOW([200 OK - Process])
    POLICY -->|Evaluate conditions| EVAL[Policy Engine:<br/>Check JSONB conditions]
    
    EVAL -->|All conditions met| ALLOW
    EVAL -->|Condition failed| DENY2[403 Forbidden]
    
    DENY1 --> ERR([Error Response])
    DENY2 --> ERR
```

## WF-03: Domain Registration Flow

```mermaid
flowchart TD
    ADMIN([Super Admin]) --> CREATE[POST /api/domains]
    CREATE --> VALIDATE{Validate domain code<br/>unique?}
    VALIDATE -->|Exists| ERR[409 Conflict]
    VALIDATE -->|New| SAVE[Save Domain]
    SAVE --> DEFAULTS[Create default resources:<br/>users, settings]
    DEFAULTS --> ROLES[Create default roles:<br/>ADMIN, VIEWER]
    ROLES --> PERMS[Create default permissions:<br/>ADMIN=full, VIEWER=read]
    PERMS --> DONE([Domain Ready])
```

## WF-04: Permission Assignment Flow

```mermaid
flowchart TD
    ADMIN([Domain Admin]) --> SELECT[Select Domain]
    SELECT --> STEP1{What to configure?}
    
    STEP1 -->|Roles| ROLE_CRUD[Create/Edit Role]
    STEP1 -->|Groups| GROUP_CRUD[Create/Edit Group]
    STEP1 -->|Users| USER_ASSIGN[Assign User to Group]
    STEP1 -->|Permissions| PERM_MATRIX[Edit Permission Matrix]
    STEP1 -->|Policies| POLICY_CRUD[Create/Edit Policy]
    
    ROLE_CRUD --> DONE([Configuration Saved])
    GROUP_CRUD --> ASSIGN_ROLES[Assign Roles to Group]
    ASSIGN_ROLES --> DONE
    USER_ASSIGN --> DONE
    PERM_MATRIX --> DONE
    POLICY_CRUD --> DONE
```

## WF-05: Read-Only User Access Flow

```mermaid
flowchart TD
    USER([Read-Only User]) --> REQ[API Request]
    REQ --> CHECK{HTTP Method?}
    
    CHECK -->|GET| RBAC{Has READ permission<br/>on resource?}
    CHECK -->|POST/PUT/DELETE| DENY[403 Forbidden:<br/>Read-only access]
    
    RBAC -->|Yes| POLICY{Policy conditions met?}
    RBAC -->|No| DENY
    
    POLICY -->|Yes| ALLOW[200 OK]
    POLICY -->|No| DENY
    
    POLICY -->|owner-only policy| OWN{resource.owner == user?}
    OWN -->|Yes| ALLOW
    OWN -->|No| DENY
```

## WF-06: Multi-Domain User Flow

```mermaid
flowchart TD
    USER([User]) --> LOGIN[Login]
    LOGIN --> DOMAINS[Load user's domains]
    DOMAINS --> SELECT{Select active domain}
    
    SELECT -->|Booking| LOAD_B[Load Booking roles/perms]
    SELECT -->|Rental| LOAD_R[Load Rental roles/perms]
    SELECT -->|Loyalty| LOAD_L[Load Loyalty roles/perms]
    
    LOAD_B --> JWT[Generate domain-scoped JWT]
    LOAD_R --> JWT
    LOAD_L --> JWT
    
    JWT --> API[Access domain APIs]
    API --> SWITCH{Switch domain?}
    SWITCH -->|Yes| SELECT
    SWITCH -->|No| CONTINUE[Continue using API]
```
