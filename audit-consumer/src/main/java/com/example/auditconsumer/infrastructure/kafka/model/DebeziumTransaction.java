package com.example.auditconsumer.infrastructure.kafka.model;

public record DebeziumTransaction(
        String id,
        Long totalOrder,
        Long dataCollectionOrder
) {
}