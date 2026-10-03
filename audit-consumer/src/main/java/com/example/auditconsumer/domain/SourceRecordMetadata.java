package com.example.auditconsumer.domain;

public record SourceRecordMetadata(
        String topic,
        int partition,
        long offset,
        long kafkaTimestamp,
        String key
) {
}
