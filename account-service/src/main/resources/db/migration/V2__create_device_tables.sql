-- V2: Create device tables (FR-007)
-- Snowflake BIGINT PK, audit columns

CREATE TABLE user_devices (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    fingerprint     VARCHAR(255) NOT NULL,
    device_name     VARCHAR(200),
    device_type     VARCHAR(50),
    browser_name    VARCHAR(100),
    os_name         VARCHAR(100),
    ip_address      VARCHAR(45),
    trusted         BOOLEAN NOT NULL DEFAULT FALSE,
    trusted_until   TIMESTAMP WITH TIME ZONE,
    last_used_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_user_devices UNIQUE (user_id, fingerprint)
);

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_fingerprint ON user_devices(user_id, fingerprint);
