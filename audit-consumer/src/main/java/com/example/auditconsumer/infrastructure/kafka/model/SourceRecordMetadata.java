package com.example.auditconsumer.infrastructure.kafka.model;

public record SourceRecordMetadata(
        String topic,
        int partition,
        long offset,
        long kafkaTimestamp,
        String key
) {
}