-- ============================================================================
-- V11: Event Sourcing Tables — event_store, event_outbox, processed_events
-- Part of: user-registration-event (Event Sourcing foundation)
-- Purpose: Append-only event log, transactional outbox, consumer idempotency
-- FR: FR-004, FR-007, FR-008
-- ============================================================================

-- 1. event_store — append-only immutable event log
CREATE TABLE event_store (
    id               BIGINT PRIMARY KEY,
    aggregate_type   VARCHAR(100) NOT NULL,
    aggregate_id     BIGINT NOT NULL,
    event_type       VARCHAR(200) NOT NULL,
    schema_version   INT NOT NULL DEFAULT 1,
    sequence_number  BIGINT NOT NULL,
    payload          JSONB NOT NULL,
    metadata         JSONB NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    updated_by       VARCHAR(255)
);

CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_type, aggregate_id);
CREATE INDEX idx_event_store_type ON event_store(event_type);
CREATE INDEX idx_event_store_created ON event_store(created_at);
CREATE UNIQUE INDEX uq_event_store_agg_seq ON event_store(aggregate_type, aggregate_id, sequence_number);

-- 2. event_outbox — transactional outbox for reliable Kafka publishing
CREATE TABLE event_outbox (
    id               BIGINT PRIMARY KEY,
    aggregate_type   VARCHAR(100) NOT NULL,
    aggregate_id     BIGINT NOT NULL,
    event_type       VARCHAR(200) NOT NULL,
    topic            VARCHAR(200) NOT NULL,
    partition_key    VARCHAR(200) NOT NULL,
    payload          JSONB NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count      INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    updated_by       VARCHAR(255)
);

CREATE INDEX idx_outbox_status_created ON event_outbox(status, created_at);
CREATE INDEX idx_outbox_published ON event_outbox(published_at) WHERE published_at IS NOT NULL;

-- 3. processed_events — consumer-side idempotency
CREATE TABLE processed_events (
    id               BIGINT PRIMARY KEY,
    event_id         UUID NOT NULL,
    event_type       VARCHAR(200) NOT NULL,
    consumer_group   VARCHAR(100) NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    updated_by       VARCHAR(255)
);

CREATE UNIQUE INDEX uq_processed_event_consumer ON processed_events(event_id, consumer_group);
CREATE INDEX idx_processed_at ON processed_events(processed_at);
