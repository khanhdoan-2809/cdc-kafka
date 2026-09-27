package com.example.auditconsumer.infrastructure.persistence;

import com.example.auditconsumer.application.port.AuditLogStore;
import com.example.auditconsumer.application.port.AuditSaveResult;
import com.example.auditconsumer.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class JdbcAuditLogStore implements AuditLogStore {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public AuditSaveResult save(AuditEvent event) {
        var affectedRows = jdbcTemplate.update("""
            INSERT INTO data_audit_log (
                entity_type,
                entity_id,

                transport_id,
                job_id,
                container_id,

                operation,
                actor_id,

                before_data,
                after_data,
                changed_fields,

                source_lsn,
                source_transaction_id,

                database_occurred_at,
                cdc_processed_at,

                transaction_id,
                transaction_total_order,
                transaction_data_collection_order,

                source_topic,
                source_partition,
                source_offset,
                source_key
            )
            VALUES (
                ?, ?,
                ?, ?, ?,
                ?, ?,
                CAST(? AS jsonb),
                CAST(? AS jsonb),
                CAST(? AS jsonb),
                ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?
            )
            ON CONFLICT (
                source_topic,
                source_partition,
                source_offset
            )
            DO NOTHING
            """,
                event.entityType().name(),
                event.entityId(),

                event.transportId(),
                event.jobId(),
                event.containerId(),

                event.operation().name(),
                event.actorId(),

                json(event.before()),
                json(event.after()),
                jsonMapper.valueToTree(event.changedFields()).toString(),

                event.sourceLsn(),
                event.sourceTransactionId(),

                event.databaseOccurredAt(),
                event.cdcProcessedAt(),

                event.transactionId(),
                event.transactionTotalOrder(),
                event.transactionDataCollectionOrder(),

                event.sourceTopic(),
                event.sourcePartition(),
                event.sourceOffset(),
                event.sourceKey()
        );

        return affectedRows == 1
                ? AuditSaveResult.INSERTED
                : AuditSaveResult.DUPLICATE;
    }

    private String json(Object value) {
        return value == null ? null : value.toString();
    }
}