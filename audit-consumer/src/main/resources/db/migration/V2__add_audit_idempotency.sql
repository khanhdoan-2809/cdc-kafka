ALTER TABLE data_audit_log
ADD CONSTRAINT uk_data_audit_log_source_record
UNIQUE (
    source_topic,
    source_partition,
    source_offset
);