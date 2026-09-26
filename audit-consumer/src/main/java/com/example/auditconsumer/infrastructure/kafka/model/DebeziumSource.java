package com.example.auditconsumer.infrastructure.kafka.model;

public record DebeziumSource(
        String schema,
        String table,
        Long transactionId,
        Long lsn,
        Long occurredAtMs
) {
}