-- =====================================================
-- V2: Auth Core Features — MFA, SSO, Password Policy
-- PostgreSQL 17+
-- =====================================================

-- 1. ALTER users — add MFA and password tracking columns
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_method VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN trusted_device_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- 2. User SSO Identities
CREATE TABLE user_identities (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    provider        VARCHAR(50) NOT NULL,
    provider_sub    VARCHAR(255) NOT NULL,
    provider_email  VARCHAR(255),
    provider_name   VARCHAR(200),
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_sub)
);
CREATE INDEX idx_user_identities_user ON user_identities(user_id) WHERE active = TRUE;

-- 3. Password Policies per Domain
CREATE TABLE password_policies (
    id                       BIGINT PRIMARY KEY,
    domain_id                BIGINT NOT NULL UNIQUE REFERENCES domains(id),
    min_length               INT NOT NULL DEFAULT 8,
    max_length               INT NOT NULL DEFAULT 128,
    require_uppercase        BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase        BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit            BOOLEAN NOT NULL DEFAULT TRUE,
    require_special          BOOLEAN NOT NULL DEFAULT FALSE,
    min_character_types      INT NOT NULL DEFAULT 3,
    history_count            INT NOT NULL DEFAULT 5,
    max_age_days             INT NOT NULL DEFAULT 90,
    lockout_threshold        INT NOT NULL DEFAULT 5,
    lockout_duration_minutes INT NOT NULL DEFAULT 15,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Password History
CREATE TABLE password_history (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_history_user ON password_history(user_id);
