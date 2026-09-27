CREATE TABLE data_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,

    transport_id BIGINT,
    job_id BIGINT,
    container_id BIGINT,

    operation VARCHAR(20) NOT NULL,

    actor_id VARCHAR(100),

    before_data JSONB,
    after_data JSONB,

    changed_fields JSONB NOT NULL DEFAULT '[]'::jsonb,

    source_lsn BIGINT,
    source_transaction_id BIGINT,

    database_occurred_at TIMESTAMPTZ,
    cdc_processed_at TIMESTAMPTZ,

    transaction_id VARCHAR(200),
    transaction_total_order BIGINT,
    transaction_data_collection_order BIGINT,

    source_topic VARCHAR(255) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key TEXT,

    consumed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT ck_data_audit_log_operation
        CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);


CREATE INDEX idx_data_audit_log_entity
    ON data_audit_log(entity_type, entity_id, database_occurred_at DESC);


CREATE INDEX idx_data_audit_log_transport
    ON data_audit_log(transport_id, database_occurred_at DESC);


CREATE INDEX idx_data_audit_log_job
    ON data_audit_log(job_id, database_occurred_at DESC);


CREATE INDEX idx_data_audit_log_container
    ON data_audit_log(container_id, database_occurred_at DESC);


CREATE INDEX idx_data_audit_log_actor
    ON data_audit_log(actor_id, database_occurred_at DESC);