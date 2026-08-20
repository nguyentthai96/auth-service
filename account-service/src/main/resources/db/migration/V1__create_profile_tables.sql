-- V1: Create profile tables (FR-005)
-- Snowflake BIGINT PK, audit columns

CREATE TABLE user_profiles (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    display_name    VARCHAR(200),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    date_of_birth   DATE,
    address         VARCHAR(500),
    timezone        VARCHAR(50) NOT NULL DEFAULT 'UTC',
    locale          VARCHAR(10) NOT NULL DEFAULT 'en',
    avatar_url      VARCHAR(1000),
    bio             VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);

CREATE TABLE user_contacts (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    contact_type    VARCHAR(30) NOT NULL,
    contact_value   VARCHAR(255) NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_user_contacts UNIQUE (user_id, contact_type, contact_value)
);

CREATE INDEX idx_user_contacts_user_id ON user_contacts(user_id);
