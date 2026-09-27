-- V3__create_inbox_table.sql
-- Idempotent Consumer Inbox Table for Event Deduplication

CREATE TABLE processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
