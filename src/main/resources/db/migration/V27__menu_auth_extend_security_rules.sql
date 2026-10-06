-- =========================================================
-- V27: Menu-based authorization — extend endpoint_security_rules
-- Adds source_menu_code + auto_generated columns
-- Cleans existing data and re-seeds (no production users)
-- =========================================================

-- 1. Add new columns for menu-rule linking
ALTER TABLE endpoint_security_rules
    ADD COLUMN source_menu_code VARCHAR(100),
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Clean existing seed data (no production usage yet)
DELETE FROM endpoint_security_rules;

-- 3. Index for fast lookup by menu source
CREATE INDEX idx_esr_source_menu ON endpoint_security_rules(source_menu_code)
    WHERE source_menu_code IS NOT NULL;

-- 4. Re-seed manual rules with auto_generated=false
-- Public endpoints (PERMIT_ALL)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, http_method, access_type, sort_order, description, created_by, auto_generated)
VALUES
    ('/auth/login',        'EXACT', 'POST', 'PERMIT_ALL', 10, 'User login',           'SYSTEM', false),
    ('/auth/register',     'EXACT', 'POST', 'PERMIT_ALL', 11, 'User registration',    'SYSTEM', false),
    ('/auth/refresh',      'EXACT', 'POST', 'PERMIT_ALL', 12, 'Token refresh',        'SYSTEM', false),
    ('/auth/introspect',   'EXACT', 'POST', 'PERMIT_ALL', 13, 'Token introspection',  'SYSTEM', false),
    ('/auth/key-exchange', 'EXACT', 'POST', 'PERMIT_ALL', 14, 'E2EE key exchange',    'SYSTEM', false),
    ('/auth/forgot-password', 'EXACT', 'POST', 'PERMIT_ALL', 15, 'Forgot password',   'SYSTEM', false),
    ('/auth/mfa/verify',   'EXACT', 'POST', 'PERMIT_ALL', 20, 'MFA verify OTP',       'SYSTEM', false),
    ('/auth/mfa/resend',   'EXACT', 'POST', 'PERMIT_ALL', 21, 'MFA resend OTP',       'SYSTEM', false),
    ('/auth/mfa/recovery-codes/verify', 'EXACT', 'POST', 'PERMIT_ALL', 22, 'MFA recovery code verify', 'SYSTEM', false),
    ('/auth/sso/callback', 'EXACT', 'POST', 'PERMIT_ALL', 30, 'SSO callback',         'SYSTEM', false),
    ('/auth/sso/providers','EXACT', 'GET',  'PERMIT_ALL', 31, 'SSO provider listing',  'SYSTEM', false),
    ('/captcha/challenge', 'EXACT', 'GET',  'PERMIT_ALL', 40, 'Captcha challenge',     'SYSTEM', false),
    ('/.well-known/jwks.json', 'EXACT', 'GET', 'PERMIT_ALL', 41, 'JWKS endpoint',     'SYSTEM', false),
    ('/auth/anonymous',    'EXACT', 'POST', 'PERMIT_ALL', 50, 'Create anonymous session', 'SYSTEM', false),
    ('/actuator/**',       'ANT',   NULL,   'PERMIT_ALL', 90, 'Actuator endpoints',    'SYSTEM', false);

-- Anonymous session (HAS_ROLE: ANONYMOUS)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by, auto_generated)
VALUES
    ('/auth/anonymous/**', 'ANT', 'HAS_ROLE', ARRAY['ANONYMOUS'], 51, 'Anonymous session operations', 'SYSTEM', false);

-- Internal service-to-service (HAS_ROLE: SERVICE)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by, auto_generated)
VALUES
    ('/internal/**', 'ANT', 'HAS_ROLE', ARRAY['SERVICE'], 60, 'Service-to-service endpoints', 'SYSTEM', false);

-- Authenticated endpoints
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, http_method, access_type, sort_order, description, created_by, auto_generated)
VALUES
    ('/auth/logout',       'EXACT', 'POST', 'AUTHENTICATED', 100, 'User logout',            'SYSTEM', false),
    ('/auth/sessions/**',  'ANT',    NULL,  'AUTHENTICATED', 101, 'Session management',      'SYSTEM', false),
    ('/auth/devices/**',   'ANT',    NULL,  'AUTHENTICATED', 102, 'Device management',       'SYSTEM', false),
    ('/auth/permissions/**','ANT',   NULL,  'AUTHENTICATED', 103, 'Permission checks',       'SYSTEM', false),
    ('/account/**',        'ANT',    NULL,  'AUTHENTICATED', 110, 'Account lifecycle & profile', 'SYSTEM', false),
    ('/auth/mfa/**',       'ANT',    NULL,  'AUTHENTICATED', 120, 'MFA settings (non-public)', 'SYSTEM', false),
    ('/auth/sso/**',       'ANT',    NULL,  'AUTHENTICATED', 130, 'SSO link/unlink',         'SYSTEM', false),
    ('/auth/change-password','EXACT','POST','AUTHENTICATED', 140, 'Change password',          'SYSTEM', false),
    ('/auth/switch-domain','EXACT', 'POST', 'AUTHENTICATED', 141, 'Switch domain',            'SYSTEM', false);

-- Admin endpoints (HAS_ROLE: ADMIN)
INSERT INTO endpoint_security_rules (url_pattern, pattern_type, access_type, required_roles, sort_order, description, created_by, auto_generated)
VALUES
    ('/admin/**', 'ANT', 'HAS_ROLE', ARRAY['ADMIN'], 200, 'Admin-only endpoints', 'SYSTEM', false);
