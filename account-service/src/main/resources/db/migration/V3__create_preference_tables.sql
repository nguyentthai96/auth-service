-- V3: Create preference tables (FR-006)
-- Snowflake BIGINT PK, audit columns

CREATE TABLE user_preferences (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    category        VARCHAR(50) NOT NULL,
    pref_key        VARCHAR(100) NOT NULL,
    pref_value      VARCHAR(2000) NOT NULL,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_user_preferences UNIQUE (user_id, category, pref_key)
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
CREATE INDEX idx_user_preferences_category ON user_preferences(user_id, category);
