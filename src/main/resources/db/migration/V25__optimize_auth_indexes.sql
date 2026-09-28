-- V11: Optimized auth indexes — Covering + Partial indexes
-- Change: user-identity-dual-key | FR-004, FR-005, FR-006

-- Covering index for email auth queries (Index-Only Scan)
CREATE INDEX idx_users_email_auth_covering
    ON users(email)
    INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled)
    WHERE active = TRUE;

-- Covering index for username auth queries (Index-Only Scan)
CREATE INDEX idx_users_username_auth_covering
    ON users(username)
    INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled)
    WHERE active = TRUE;

-- Partial unique index for phone (saves 40-60% index size vs full index)
CREATE UNIQUE INDEX idx_users_phone_partial
    ON users(phone)
    WHERE phone IS NOT NULL AND active = TRUE;

-- UUID covering index for API lookups
CREATE INDEX idx_users_uuid_covering
    ON users(uuid)
    INCLUDE (id, username, email, status, active)
    WHERE active = TRUE;
