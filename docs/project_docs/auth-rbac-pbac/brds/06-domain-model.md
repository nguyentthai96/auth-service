# BRD-06: Domain Model

## Entity Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ USER_DOMAINS : "belongs to"
    USERS ||--o{ USER_GROUPS : "member of"
    USERS ||--o{ REFRESH_TOKENS : "has"
    
    DOMAINS ||--o{ USER_DOMAINS : "contains"
    DOMAINS ||--o{ GROUPS : "has"
    DOMAINS ||--o{ DOMAIN_ROLES : "defines"
    DOMAINS ||--o{ DOMAIN_RESOURCES : "registers"
    DOMAINS ||--o{ POLICIES : "configures"
    
    GROUPS ||--o{ USER_GROUPS : "contains"
    GROUPS ||--o{ GROUP_ROLES : "assigned"
    GROUPS }o--|| DOMAINS : "scoped to"
    
    DOMAIN_ROLES ||--o{ GROUP_ROLES : "granted to"
    DOMAIN_ROLES ||--o{ ROLE_PERMISSIONS : "has"
    
    DOMAIN_RESOURCES ||--o{ PERMISSIONS : "target"
    ACTIONS ||--o{ PERMISSIONS : "allowed"
    
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : "granted via"
    
    POLICIES ||--o{ POLICY_CONDITIONS : "evaluated by"

    USERS {
        uuid id PK
        string username UK
        string email UK
        string password_hash
        string full_name
        string phone
        string avatar_url
        string status "ACTIVE,LOCKED,SUSPENDED"
        int failed_login_count
        long locked_until_at
        boolean active
        long created_at
        string created_by
        long updated_at
        string updated_by
    }

    DOMAINS {
        uuid id PK
        string code UK "booking, rental, loyalty"
        string name
        string description
        jsonb config "domain-specific settings"
        string status "ACTIVE,SUSPENDED,ARCHIVED"
        boolean active
        long created_at
        long updated_at
    }

    USER_DOMAINS {
        uuid id PK
        uuid user_id FK
        uuid domain_id FK
        boolean is_primary
        long joined_at
        boolean active
    }

    GROUPS {
        uuid id PK
        uuid domain_id FK
        string name
        string description
        boolean active
        long created_at
        long updated_at
    }

    USER_GROUPS {
        uuid id PK
        uuid user_id FK
        uuid group_id FK
        long assigned_at
        boolean active
    }

    DOMAIN_ROLES {
        uuid id PK
        uuid domain_id FK
        string code UK_domain "HOTEL_OWNER, RECEPTIONIST"
        string name
        string description
        int hierarchy_level "0=highest"
        boolean is_default
        boolean active
        long created_at
        long updated_at
    }

    GROUP_ROLES {
        uuid id PK
        uuid group_id FK
        uuid role_id FK
        long assigned_at
        boolean active
    }

    DOMAIN_RESOURCES {
        uuid id PK
        uuid domain_id FK
        string code UK_domain "bookings, rooms, payments"
        string name
        string description
        uuid parent_resource_id FK "nullable - hierarchy"
        boolean active
        long created_at
    }

    ACTIONS {
        uuid id PK
        string code UK "READ, CREATE, UPDATE, DELETE"
        string name
        string description
    }

    PERMISSIONS {
        uuid id PK
        uuid resource_id FK
        uuid action_id FK
    }

    ROLE_PERMISSIONS {
        uuid id PK
        uuid role_id FK
        uuid permission_id FK
        long granted_at
        boolean active
    }

    POLICIES {
        uuid id PK
        uuid domain_id FK
        string name
        string description
        uuid resource_id FK
        uuid action_id FK
        string effect "ALLOW, DENY"
        int priority "lower=higher priority"
        string status "DRAFT, ACTIVE, INACTIVE"
        boolean active
        long created_at
        long updated_at
    }

    POLICY_CONDITIONS {
        uuid id PK
        uuid policy_id FK
        string attribute_path "resource.owner_id"
        string operator "eq, neq, in, gt, lt, between"
        jsonb value "comparison value"
        string value_type "STATIC, USER_ATTR, CONTEXT"
        int condition_order
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        string token_hash UK
        long expires_at
        boolean revoked
        long created_at
    }
```

## Aggregate Roots

| Aggregate | Root Entity | Children |
|:---|:---|:---|
| **User** | `USERS` | USER_DOMAINS, USER_GROUPS, REFRESH_TOKENS |
| **Domain** | `DOMAINS` | DOMAIN_ROLES, DOMAIN_RESOURCES, GROUPS, POLICIES |
| **Group** | `GROUPS` | GROUP_ROLES, USER_GROUPS |
| **Permission** | `PERMISSIONS` | ROLE_PERMISSIONS |
| **Policy** | `POLICIES` | POLICY_CONDITIONS |

## Key Relationships

1. **User ↔ Domain**: Many-to-Many via USER_DOMAINS
2. **User ↔ Group**: Many-to-Many via USER_GROUPS
3. **Group ↔ Role**: Many-to-Many via GROUP_ROLES
4. **Role ↔ Permission**: Many-to-Many via ROLE_PERMISSIONS
5. **Permission = Resource × Action**: Composite uniqueness
6. **Policy → Resource + Action**: Scoped policy evaluation
