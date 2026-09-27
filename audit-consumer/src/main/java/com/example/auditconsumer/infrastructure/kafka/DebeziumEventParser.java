package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.infrastructure.kafka.model.DebeziumEvent;
import com.example.auditconsumer.infrastructure.kafka.model.DebeziumSource;
import com.example.auditconsumer.infrastructure.kafka.model.DebeziumTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DebeziumEventParser {

    private final JsonMapper jsonMapper;

    public DebeziumEvent parse(String payload) {
        try {
            var root = jsonMapper.readTree(payload);
            var source = root.path("source");
            var transaction = root.get("transaction");

            return new DebeziumEvent(
                    nodeOrNull(root.get("before")),
                    nodeOrNull(root.get("after")),
                    new DebeziumSource(
                            textOrNull(source, "schema"),
                            textOrNull(source, "table"),
                            longOrNull(source, "txId"),
                            longOrNull(source, "lsn"),
                            longOrNull(source, "ts_ms")
                    ),
                    textOrNull(root, "op"),
                    longOrNull(root, "ts_ms"),
                    transaction == null || transaction.isNull()
                            ? null
                            : new DebeziumTransaction(
                            textOrNull(transaction, "id"),
                            longOrNull(transaction, "total_order"),
                            longOrNull(transaction, "data_collection_order")
                    )
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse Debezium event", e);
        }
    }

    private JsonNode nodeOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        var value = node.get(field);

        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        var value = node.get(field);

        return value == null || value.isNull() ? null : value.longValue();
    }
}