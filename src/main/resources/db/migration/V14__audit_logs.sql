-- V14: Audit logs table with immutability constraints (FR-015)
-- Stores security-sensitive audit events with JSONB details.
-- Database rules prevent UPDATE/DELETE on this table for immutability.

CREATE TABLE IF NOT EXISTS audit_logs (
    id                  BIGINT          PRIMARY KEY,
    user_id             BIGINT,
    action              VARCHAR(50)     NOT NULL,
    entity_type         VARCHAR(100),
    entity_id           VARCHAR(100),
    details             JSONB,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    event_timestamp     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    version             BIGINT          NOT NULL DEFAULT 0
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type_id ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs (event_timestamp);

-- Immutability constraint: prevent UPDATE on audit_logs
CREATE OR REPLACE RULE prevent_audit_update AS
    ON UPDATE TO audit_logs
    DO INSTEAD NOTHING;

-- Immutability constraint: prevent DELETE on audit_logs
CREATE OR REPLACE RULE prevent_audit_delete AS
    ON DELETE TO audit_logs
    DO INSTEAD NOTHING;
