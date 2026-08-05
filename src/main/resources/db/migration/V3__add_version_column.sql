-- V3: Add version column for optimistic locking on key tables
-- Only critical tables that need concurrent write protection:
-- - users: concurrent login/password change
-- - domains: concurrent config updates

ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE domains ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Index for version-based queries
COMMENT ON COLUMN users.version IS 'Optimistic locking version counter';
COMMENT ON COLUMN domains.version IS 'Optimistic locking version counter';
