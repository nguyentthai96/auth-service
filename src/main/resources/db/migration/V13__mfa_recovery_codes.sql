-- V13: MFA Recovery Codes table (FR-001)
-- Stores SHA-256 hashed recovery codes for backup MFA authentication.
-- Each code is single-use; 10 codes generated per user.

CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id              BIGINT       PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    code_hash       VARCHAR(64)  NOT NULL,
    used            BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_user_id ON mfa_recovery_codes (user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_user_unused ON mfa_recovery_codes (user_id, used) WHERE used = FALSE;
