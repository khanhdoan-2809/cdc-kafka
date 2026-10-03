CREATE TABLE audit_pending_transaction (
    transaction_id VARCHAR(200) PRIMARY KEY,
    expected_event_count BIGINT,
    end_received BOOLEAN NOT NULL DEFAULT FALSE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE audit_pending_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    transaction_id VARCHAR(200) NOT NULL,
    total_order BIGINT NOT NULL,

    source_topic VARCHAR(255) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key TEXT,

    payload JSONB NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uk_audit_pending_event_transaction_order
        UNIQUE (transaction_id, total_order),

    CONSTRAINT uk_audit_pending_event_source
        UNIQUE (source_topic, source_partition, source_offset)
);

CREATE INDEX idx_audit_pending_event_transaction
    ON audit_pending_event (transaction_id, total_order);