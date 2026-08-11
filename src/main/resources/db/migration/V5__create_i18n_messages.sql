-- ============================================================================
-- V5: Create i18n_messages table for dynamic message source
-- Part of: api-response-i18n-standard
-- Purpose: Store runtime-manageable i18n messages for API responses
-- ============================================================================

CREATE TABLE i18n_messages (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(128) NOT NULL,                    -- message key (e.g., 'auth.rate_limited')
    locale        VARCHAR(10)  NOT NULL,                    -- BCP 47 locale (e.g., 'en', 'vi')
    message       TEXT         NOT NULL,                    -- message template with {0}, {1} placeholders
    module        VARCHAR(64)  NOT NULL DEFAULT 'common',   -- service grouping (e.g., 'auth', 'common')
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    BIGINT       NOT NULL,                    -- epoch millis
    updated_at    BIGINT       NOT NULL,                    -- epoch millis
    CONSTRAINT uq_i18n_code_locale UNIQUE (code, locale)
);

CREATE INDEX idx_i18n_messages_code ON i18n_messages (code);
CREATE INDEX idx_i18n_messages_module ON i18n_messages (module);

COMMENT ON TABLE i18n_messages IS 'Dynamic i18n message storage for API response messages';
COMMENT ON COLUMN i18n_messages.code IS 'Message key matching ErrorCodeBase.msgCode (e.g., auth.rate_limited)';
COMMENT ON COLUMN i18n_messages.locale IS 'BCP 47 locale tag (e.g., en, vi)';
COMMENT ON COLUMN i18n_messages.message IS 'MessageFormat template with {0}, {1} placeholders';
COMMENT ON COLUMN i18n_messages.module IS 'Service grouping for bulk operations (e.g., auth, common)';
