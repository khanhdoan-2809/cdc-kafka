package com.example.auditconsumer.domain;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Set;

public record AuditEvent(
        EntityType entityType,
        Long entityId,

        Long transportId,
        Long jobId,
        Long containerId,

        AuditOperation operation,
        String actorId,

        JsonNode before,
        JsonNode after,

        Set<String> changedFields,

        Long sourceLsn,
        Long sourceTransactionId,

        Instant databaseOccurredAt,
        Instant cdcProcessedAt,

        String transactionId,
        Long transactionTotalOrder,
        Long transactionDataCollectionOrder,

        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String sourceKey
) {
}