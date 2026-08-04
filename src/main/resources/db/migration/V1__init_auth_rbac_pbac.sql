-- =====================================================
-- Auth RBAC+PBAC Schema — Snowflake ID + TIMESTAMPTZ
-- PostgreSQL 17+
-- =====================================================

-- =====================================================
-- 1. Core Tables
-- =====================================================

CREATE TABLE users (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until_at TIMESTAMPTZ,
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status) WHERE active = TRUE;

-- =====================================================
-- 2. Domain Management
-- =====================================================

CREATE TABLE domains (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    config          JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_domains_code ON domains(code) WHERE active = TRUE;

-- =====================================================
-- 3. User ↔ Domain Membership
-- =====================================================

CREATE TABLE user_domains (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    domain_id       BIGINT NOT NULL REFERENCES domains(id),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(user_id, domain_id)
);

CREATE INDEX idx_user_domains_user ON user_domains(user_id) WHERE active = TRUE;
CREATE INDEX idx_user_domains_domain ON user_domains(domain_id) WHERE active = TRUE;

-- =====================================================
-- 4. Groups
-- =====================================================

CREATE TABLE groups (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL REFERENCES domains(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(domain_id, name)
);

CREATE INDEX idx_groups_domain ON groups(domain_id) WHERE active = TRUE;

-- =====================================================
-- 5. User ↔ Group Membership
-- =====================================================

CREATE TABLE user_groups (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    group_id        BIGINT NOT NULL REFERENCES groups(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(user_id, group_id)
);

-- =====================================================
-- 6. Domain Roles
-- =====================================================

CREATE TABLE domain_roles (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL REFERENCES domains(id),
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    hierarchy_level INT NOT NULL DEFAULT 99,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(domain_id, code)
);

CREATE INDEX idx_domain_roles_domain ON domain_roles(domain_id) WHERE active = TRUE;

-- =====================================================
-- 7. Group ↔ Role Assignment
-- =====================================================

CREATE TABLE group_roles (
    id              BIGINT PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id),
    role_id         BIGINT NOT NULL REFERENCES domain_roles(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(group_id, role_id)
);

-- =====================================================
-- 8. Actions (Lookup Table — BaseEntity)
-- =====================================================

CREATE TABLE actions (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(255)
);

-- Seed standard CRUD actions
INSERT INTO actions (id, code, name, description) VALUES
    (1, 'READ',    'Read',    'View/read access'),
    (2, 'CREATE',  'Create',  'Create new resources'),
    (3, 'UPDATE',  'Update',  'Modify existing resources'),
    (4, 'DELETE',  'Delete',  'Remove resources'),
    (5, 'EXPORT',  'Export',  'Export/download data'),
    (6, 'IMPORT',  'Import',  'Import/upload data'),
    (7, 'APPROVE', 'Approve', 'Approve pending items'),
    (8, 'MANAGE',  'Manage',  'Full management access');

-- =====================================================
-- 9. Domain Resources
-- =====================================================

CREATE TABLE domain_resources (
    id                  BIGINT PRIMARY KEY,
    domain_id           BIGINT NOT NULL REFERENCES domains(id),
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    parent_resource_id  BIGINT REFERENCES domain_resources(id),
    -- base-core PersistentAuditableEntity fields
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    UNIQUE(domain_id, code)
);

-- =====================================================
-- 10. Permissions (Join Table — BaseEntity)
-- =====================================================

CREATE TABLE permissions (
    id              BIGINT PRIMARY KEY,
    resource_id     BIGINT NOT NULL REFERENCES domain_resources(id),
    action_id       BIGINT NOT NULL REFERENCES actions(id),
    UNIQUE(resource_id, action_id)
);

-- =====================================================
-- 11. Role ↔ Permission Grant
-- =====================================================

CREATE TABLE role_permissions (
    id              BIGINT PRIMARY KEY,
    role_id         BIGINT NOT NULL REFERENCES domain_roles(id),
    permission_id   BIGINT NOT NULL REFERENCES permissions(id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    UNIQUE(role_id, permission_id)
);

-- =====================================================
-- 12. PBAC Policies
-- =====================================================

CREATE TABLE policies (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL REFERENCES domains(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    resource_id     BIGINT REFERENCES domain_resources(id),
    action_id       BIGINT REFERENCES actions(id),
    effect          VARCHAR(10) NOT NULL DEFAULT 'ALLOW' CHECK (effect IN ('ALLOW', 'DENY')),
    priority        INT NOT NULL DEFAULT 100,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED')),
    -- base-core PersistentAuditableEntity fields
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_policies_domain_status ON policies(domain_id, status) WHERE active = TRUE;

-- =====================================================
-- 13. Policy Conditions (BaseEntity)
-- =====================================================

CREATE TABLE policy_conditions (
    id              BIGINT PRIMARY KEY,
    policy_id       BIGINT NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    attribute_path  VARCHAR(200) NOT NULL,
    operator        VARCHAR(20) NOT NULL,
    value           JSONB NOT NULL,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STATIC',
    condition_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_policy_conditions_policy ON policy_conditions(policy_id);

-- =====================================================
-- 14. Token Management (BaseEntity)
-- =====================================================

CREATE TABLE refresh_tokens (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash) WHERE revoked = FALSE;

CREATE TABLE token_blacklist (
    id              BIGINT PRIMARY KEY,
    token_jti       VARCHAR(255) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason          VARCHAR(100)
);

CREATE INDEX idx_token_blacklist_jti ON token_blacklist(token_jti);

-- =====================================================
-- 15. Audit Log
-- =====================================================

CREATE TABLE audit_log (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT NOT NULL,
    changes         JSONB,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
