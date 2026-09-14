-- =====================================================
-- V23: Seed Default Highest Privilege Sysadmin Account
-- PostgreSQL 17+
-- =====================================================

-- 1. Ensure Default Domain exists
INSERT INTO domains (id, code, name, description, active, config, status, version, created_at, updated_at)
VALUES (842319544233984, 'default', 'Default Domain', 'Default system domain', true, '{}', 'ACTIVE', 0, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET active = true, status = 'ACTIVE';

-- 2. Ensure Password Policy exists for Default Domain
INSERT INTO password_policies (id, domain_id, min_length, max_length, require_uppercase, require_lowercase, require_digit, require_special, min_character_types, history_count, max_age_days, lockout_threshold, lockout_duration_minutes, created_at, updated_at)
VALUES (842319544233985, 842319544233984, 8, 128, true, true, true, false, 3, 5, 365, 10, 15, NOW(), NOW())
ON CONFLICT (domain_id) DO NOTHING;

-- 3. Create Highest Privilege Roles (ADMIN, SUPER_ADMIN, SYSADMIN)
INSERT INTO domain_roles (id, domain_id, code, name, description, hierarchy_level, is_default, active, created_at, updated_at)
VALUES 
    (300000000000001, 842319544233984, 'ADMIN', 'System Administrator', 'Highest administration authority', 0, false, true, NOW(), NOW()),
    (300000000000002, 842319544233984, 'SUPER_ADMIN', 'Super Administrator', 'Full platform administrator access', 0, false, true, NOW(), NOW()),
    (300000000000003, 842319544233984, 'SYSADMIN', 'SysAdmin', 'System operator role', 0, false, true, NOW(), NOW())
ON CONFLICT (domain_id, code) DO UPDATE SET active = true;

-- 4. Create System Administrators Group
INSERT INTO groups (id, domain_id, name, description, active, created_at, updated_at)
VALUES (400000000000001, 842319544233984, 'System Administrators', 'Superuser and SysAdmin group', true, NOW(), NOW())
ON CONFLICT (domain_id, name) DO UPDATE SET active = true;

-- 5. Assign Roles to Group
INSERT INTO group_roles (id, group_id, role_id, active, created_at, updated_at)
VALUES 
    (600000000000001, 400000000000001, 300000000000001, true, NOW(), NOW()),
    (600000000000002, 400000000000001, 300000000000002, true, NOW(), NOW()),
    (600000000000003, 400000000000001, 300000000000003, true, NOW(), NOW())
ON CONFLICT (group_id, role_id) DO UPDATE SET active = true;

-- 6. Create Resources, Actions & Permissions
INSERT INTO domain_resources (id, domain_id, code, name, description, active, created_at, updated_at)
VALUES 
    (700000000000001, 842319544233984, '*', 'All Resources', 'Wildcard permission for all system modules', true, NOW(), NOW()),
    (700000000000002, 842319544233984, 'users', 'Users Management', 'User management resource', true, NOW(), NOW()),
    (700000000000003, 842319544233984, 'roles', 'Roles Management', 'Role management resource', true, NOW(), NOW()),
    (700000000000004, 842319544233984, 'system', 'System Management', 'System operations resource', true, NOW(), NOW())
ON CONFLICT (domain_id, code) DO UPDATE SET active = true;

-- Standard Actions 1..8 exist from V1 (READ, CREATE, UPDATE, DELETE, EXPORT, IMPORT, APPROVE, MANAGE)
INSERT INTO permissions (id, resource_id, action_id)
VALUES 
    (800000000000001, 700000000000001, 1),
    (800000000000002, 700000000000001, 2),
    (800000000000003, 700000000000001, 3),
    (800000000000004, 700000000000001, 4),
    (800000000000005, 700000000000001, 5),
    (800000000000006, 700000000000001, 6),
    (800000000000007, 700000000000001, 7),
    (800000000000008, 700000000000001, 8)
ON CONFLICT (resource_id, action_id) DO NOTHING;

-- Grant all permissions to ADMIN role (id: 300000000000001)
INSERT INTO role_permissions (id, role_id, permission_id, active, created_at, updated_at)
VALUES 
    (900000000000001, 300000000000001, 800000000000001, true, NOW(), NOW()),
    (900000000000002, 300000000000001, 800000000000002, true, NOW(), NOW()),
    (900000000000003, 300000000000001, 800000000000003, true, NOW(), NOW()),
    (900000000000004, 300000000000001, 800000000000004, true, NOW(), NOW()),
    (900000000000005, 300000000000001, 800000000000005, true, NOW(), NOW()),
    (900000000000006, 300000000000001, 800000000000006, true, NOW(), NOW()),
    (900000000000007, 300000000000001, 800000000000007, true, NOW(), NOW()),
    (900000000000008, 300000000000001, 800000000000008, true, NOW(), NOW())
ON CONFLICT (role_id, permission_id) DO UPDATE SET active = true;

-- 7. Create Default Sysadmin User
-- Password is SysAdmin@123 (bcrypt hash: {bcrypt}$2b$12$8vFKT91UVe7te7a4McnzR.89ALODj4SjG5zdgO00eXqyI1LWOYVAW)
INSERT INTO users (
    id, username, email, password_hash, full_name, status, failed_login_count, active, mfa_enabled, password_changed_at, created_at, updated_at, version
)
VALUES (
    100000000000000, 
    'sysadmin', 
    'sysadmin@system.local', 
    '{bcrypt}$2b$12$8vFKT91UVe7te7a4McnzR.89ALODj4SjG5zdgO00eXqyI1LWOYVAW', 
    'System Administrator', 
    'ACTIVE', 
    0, 
    true, 
    false, 
    NOW(), 
    NOW(), 
    NOW(), 
    0
)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = '{bcrypt}$2b$12$8vFKT91UVe7te7a4McnzR.89ALODj4SjG5zdgO00eXqyI1LWOYVAW',
    status = 'ACTIVE',
    active = true,
    failed_login_count = 0,
    password_changed_at = NOW(),
    updated_at = NOW();

-- 8. Bind Sysadmin to Default Domain
INSERT INTO user_domains (id, user_id, domain_id, is_primary, active, created_at, updated_at)
VALUES (200000000000000, 100000000000000, 842319544233984, true, true, NOW(), NOW())
ON CONFLICT (user_id, domain_id) DO UPDATE SET active = true, is_primary = true;

-- 9. Bind Sysadmin to System Administrators Group
INSERT INTO user_groups (id, user_id, group_id, active, created_at, updated_at)
VALUES (500000000000001, 100000000000000, 400000000000001, true, NOW(), NOW())
ON CONFLICT (user_id, group_id) DO UPDATE SET active = true;


-- Username	sysadmin (hoặc email: sysadmin@system.local)
-- Mật khẩu	SysAdmin@123