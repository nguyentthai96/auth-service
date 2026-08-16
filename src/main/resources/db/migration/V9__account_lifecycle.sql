-- =====================================================
-- FR-009: Account Lifecycle — GDPR compliance tables
-- =====================================================

-- Account deletion requests (GDPR Right to Erasure)
CREATE TABLE account_deletion_requests (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    reason              TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_delete_at TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ,
    processed_by        VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deletion_requests_user ON account_deletion_requests(user_id);
CREATE INDEX idx_deletion_requests_status ON account_deletion_requests(status)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_deletion_requests_scheduled ON account_deletion_requests(scheduled_delete_at)
    WHERE status = 'PENDING';

-- Account data exports (GDPR Right to Data Portability)
CREATE TABLE account_data_exports (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    format          VARCHAR(10) NOT NULL DEFAULT 'JSON',
    file_path       VARCHAR(500),
    file_size_bytes BIGINT,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    download_count  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_data_exports_user ON account_data_exports(user_id);
CREATE INDEX idx_data_exports_status ON account_data_exports(status) WHERE status = 'PENDING';
