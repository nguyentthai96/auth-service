-- Login sessions table — tracks active sessions, device info, and login history
CREATE TABLE login_sessions (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    refresh_token_id    BIGINT,
    ip_address          VARCHAR(45) NOT NULL,
    user_agent          VARCHAR(500),
    device_fingerprint  VARCHAR(64),
    device_type         VARCHAR(20),
    browser_name        VARCHAR(50),
    os_name             VARCHAR(50),
    geo_country         VARCHAR(3),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    is_new_device       BOOLEAN NOT NULL DEFAULT FALSE,
    login_at            TIMESTAMP NOT NULL,
    last_activity_at    TIMESTAMP,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_login_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Performance indexes
CREATE INDEX idx_login_sessions_user_active ON login_sessions(user_id, is_active);
CREATE INDEX idx_login_sessions_device ON login_sessions(device_fingerprint);
CREATE INDEX idx_login_sessions_ip ON login_sessions(ip_address);
