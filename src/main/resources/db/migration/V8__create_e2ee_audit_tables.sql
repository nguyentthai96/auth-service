-- E2EE: Encrypted audit log + vault access log tables
-- Audit data encrypted at rest, accessible only via break-glass procedure.

-- Encrypted audit log — stores references to encrypted payloads
CREATE TABLE IF NOT EXISTS audit_log_encrypted (
    id                    BIGSERIAL    PRIMARY KEY,
    trace_id              VARCHAR(36)  NOT NULL,
    user_id               VARCHAR(100),
    action                VARCHAR(100) NOT NULL,
    encrypted_payload_ref VARCHAR(500),
    key_id_used           VARCHAR(36),
    created_at            BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_enc_user
    ON audit_log_encrypted(user_id);

CREATE INDEX IF NOT EXISTS idx_audit_enc_created
    ON audit_log_encrypted(created_at);

CREATE INDEX IF NOT EXISTS idx_audit_enc_trace
    ON audit_log_encrypted(trace_id);

-- Vault access log — 4-eyes break-glass procedure
CREATE TABLE IF NOT EXISTS vault_access_log (
    id                BIGSERIAL    PRIMARY KEY,
    requester_id      VARCHAR(100) NOT NULL,
    approver_id       VARCHAR(100),
    request_type      VARCHAR(50)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    target_audit_id   BIGINT       REFERENCES audit_log_encrypted(id),
    decrypt_count     INT          NOT NULL DEFAULT 0,
    max_decrypts      INT          NOT NULL DEFAULT 10,
    expires_at        BIGINT,
    created_at        BIGINT       NOT NULL,
    approved_at       BIGINT
);

CREATE INDEX IF NOT EXISTS idx_vault_status
    ON vault_access_log(status);

CREATE INDEX IF NOT EXISTS idx_vault_requester
    ON vault_access_log(requester_id);
