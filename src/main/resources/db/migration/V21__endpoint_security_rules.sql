-- =========================================================
-- V21: Dynamic endpoint security rules for DynamicAuthorizationManager
-- Supports: EXACT, ANT, REGEX pattern matching
-- Multi-tenant: domain_scope column
-- Audit: created_by, updated_by columns + AuditLogService integration
-- =========================================================

CREATE TABLE endpoint_security_rules (
    id              BIGSERIAL    PRIMARY KEY,

    -- Pattern Matching
    url_pattern     VARCHAR(500) NOT NULL,
    pattern_type    VARCHAR(10)  NOT NULL DEFAULT 'ANT'
                    CHECK (pattern_type IN ('EXACT', 'ANT', 'REGEX')),
    http_method     VARCHAR(10)  DEFAULT NULL
                    CHECK (http_method IN (NULL, 'GET', 'POST', 'PUT', 'PATCH', 'DELETE')),

    -- Authorization Decision
    access_type     VARCHAR(20)  NOT NULL DEFAULT 'AUTHENTICATED'
                    CHECK (access_type IN ('PERMIT_ALL', 'AUTHENTICATED',
                           'HAS_ROLE', 'HAS_ANY_ROLE', 'HAS_AUTHORITY', 'DENY_ALL')),
    required_roles  TEXT[],          -- ANY match (OR logic)
    required_perms  TEXT[],          -- ALL match (AND logic)

    -- Multi-tenant
    domain_scope    VARCHAR(50)  DEFAULT NULL,   -- NULL = all domains

    -- Ordering & Status
    sort_order      INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(500),

    -- Audit
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

-- Performance indexes
CREATE INDEX idx_esr_active_sort ON endpoint_security_rules(is_active, sort_order)
    WHERE is_active = TRUE;
CREATE INDEX idx_esr_domain ON endpoint_security_rules(domain_scope)
    WHERE domain_scope IS NOT NULL;
CREATE INDEX idx_esr_pattern ON endpoint_security_rules(url_pattern, http_method);

-- =========================================================
-- Seed: Current security rules (matching SecurityConfig.kt)
-- =========================================================

-- Public endpoints (PERMIT_ALL)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, http_method, access_type, sort_order, description, created_by)
VALUES
    ('/auth/login',        'EXACT', 'POST', 'PERMIT_ALL', 10, 'User login',           'SYSTEM'),
    ('/auth/register',     'EXACT', 'POST', 'PERMIT_ALL', 11, 'User registration',    'SYSTEM'),
    ('/auth/refresh',      'EXACT', 'POST', 'PERMIT_ALL', 12, 'Token refresh',        'SYSTEM'),
    ('/auth/introspect',   'EXACT', 'POST', 'PERMIT_ALL', 13, 'Token introspection',  'SYSTEM'),
    ('/auth/key-exchange', 'EXACT', 'POST', 'PERMIT_ALL', 14, 'E2EE key exchange',    'SYSTEM'),
    ('/auth/forgot-password', 'EXACT', 'POST', 'PERMIT_ALL', 15, 'Forgot password',   'SYSTEM'),
    ('/auth/mfa/verify',   'EXACT', 'POST', 'PERMIT_ALL', 20, 'MFA verify OTP',       'SYSTEM'),
    ('/auth/mfa/resend',   'EXACT', 'POST', 'PERMIT_ALL', 21, 'MFA resend OTP',       'SYSTEM'),
    ('/auth/mfa/recovery-codes/verify', 'EXACT', 'POST', 'PERMIT_ALL', 22, 'MFA recovery code verify', 'SYSTEM'),
    ('/auth/sso/callback', 'EXACT', 'POST', 'PERMIT_ALL', 30, 'SSO callback',         'SYSTEM'),
    ('/auth/sso/providers','EXACT', 'GET',  'PERMIT_ALL', 31, 'SSO provider listing',  'SYSTEM'),
    ('/captcha/challenge', 'EXACT', 'GET',  'PERMIT_ALL', 40, 'Captcha challenge',     'SYSTEM'),
    ('/.well-known/jwks.json', 'EXACT', 'GET', 'PERMIT_ALL', 41, 'JWKS endpoint',     'SYSTEM'),
    ('/auth/anonymous',    'EXACT', 'POST', 'PERMIT_ALL', 50, 'Create anonymous session', 'SYSTEM'),
    ('/actuator/**',       'ANT',   NULL,   'PERMIT_ALL', 90, 'Actuator endpoints',    'SYSTEM');

-- Anonymous session (HAS_ROLE: ANONYMOUS)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by)
VALUES
    ('/auth/anonymous/**', 'ANT', 'HAS_ROLE', ARRAY['ANONYMOUS'], 51, 'Anonymous session operations', 'SYSTEM');

-- Internal service-to-service (HAS_ROLE: SERVICE)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by)
VALUES
    ('/internal/**', 'ANT', 'HAS_ROLE', ARRAY['SERVICE'], 60, 'Service-to-service endpoints', 'SYSTEM');

-- Authenticated endpoints
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, http_method, access_type, sort_order, description, created_by)
VALUES
    ('/auth/logout',       'EXACT', 'POST', 'AUTHENTICATED', 100, 'User logout',            'SYSTEM'),
    ('/auth/sessions/**',  'ANT',    NULL,  'AUTHENTICATED', 101, 'Session management',      'SYSTEM'),
    ('/auth/devices/**',   'ANT',    NULL,  'AUTHENTICATED', 102, 'Device management',       'SYSTEM'),
    ('/auth/permissions/**','ANT',   NULL,  'AUTHENTICATED', 103, 'Permission checks',       'SYSTEM'),
    ('/account/**',        'ANT',    NULL,  'AUTHENTICATED', 110, 'Account lifecycle & profile', 'SYSTEM'),
    ('/auth/mfa/**',       'ANT',    NULL,  'AUTHENTICATED', 120, 'MFA settings (non-public)', 'SYSTEM'),
    ('/auth/sso/**',       'ANT',    NULL,  'AUTHENTICATED', 130, 'SSO link/unlink',         'SYSTEM'),
    ('/auth/change-password','EXACT','POST','AUTHENTICATED', 140, 'Change password',          'SYSTEM'),
    ('/auth/switch-domain','EXACT', 'POST', 'AUTHENTICATED', 141, 'Switch domain',            'SYSTEM');

-- Admin endpoints (HAS_ROLE: ADMIN)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by)
VALUES
    ('/admin/**', 'ANT', 'HAS_ROLE', ARRAY['ADMIN'], 200, 'Admin-only endpoints', 'SYSTEM');
