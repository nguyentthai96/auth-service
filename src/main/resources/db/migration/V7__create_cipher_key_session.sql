-- E2EE: Cipher key session table
-- Stores negotiated ECDH key sessions between client devices and server.
-- Keys are encrypted at rest (BYTEA) — plaintext never stored.
CREATE TABLE IF NOT EXISTS cipher_key_session (
    key_id               VARCHAR(36)  PRIMARY KEY,
    user_id              VARCHAR(100),
    device_id            VARCHAR(200) NOT NULL,
    platform             VARCHAR(20)  NOT NULL,
    algorithm_id         VARCHAR(50)  NOT NULL,
    client_to_server_key BYTEA        NOT NULL,
    server_to_client_key BYTEA        NOT NULL,
    key_version          INT          NOT NULL DEFAULT 1,
    app_version          VARCHAR(50)  NOT NULL,
    created_at           BIGINT       NOT NULL,
    expires_at           BIGINT       NOT NULL,
    is_active            BOOLEAN      DEFAULT TRUE
);

-- Lookup by user + device for idempotent key exchange
CREATE INDEX IF NOT EXISTS idx_key_session_user_device
    ON cipher_key_session(user_id, device_id);

-- Active sessions partial index for fast active-only queries
CREATE INDEX IF NOT EXISTS idx_key_session_active
    ON cipher_key_session(is_active) WHERE is_active = TRUE;

-- Expiry cleanup index
CREATE INDEX IF NOT EXISTS idx_key_session_expires
    ON cipher_key_session(expires_at);
