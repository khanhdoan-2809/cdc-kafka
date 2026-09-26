package com.example.auditconsumer.infrastructure.kafka.model;


import tools.jackson.databind.JsonNode;

public record DebeziumEvent(
        JsonNode before,
        JsonNode after,
        DebeziumSource source,
        String operation,
        Long processedAtMs,
        DebeziumTransaction transaction
) {
}