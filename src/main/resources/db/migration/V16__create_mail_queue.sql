-- Mail queue table — transactional outbox for async email delivery
-- Uses SnowflakePersistentAuditableEntity base columns (id, created_at, updated_at)
CREATE TABLE mail_queue (
    id                  BIGINT PRIMARY KEY,
    recipient           VARCHAR(255) NOT NULL,
    subject             VARCHAR(500),
    template_code       VARCHAR(100) NOT NULL,
    template_data       JSONB NOT NULL DEFAULT '{}',
    body_rendered       TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count         INT NOT NULL DEFAULT 0,
    max_retries         INT NOT NULL DEFAULT 3,
    error_message       TEXT,
    sent_at             TIMESTAMP,
    next_retry_at       TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Performance indexes for scheduled polling
CREATE INDEX idx_mail_queue_status_retry ON mail_queue(status, next_retry_at);
CREATE INDEX idx_mail_queue_created ON mail_queue(created_at);
