package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.application.exception.InvalidCdcEventException;
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

            if (root == null || !root.isObject()) {
                throw new InvalidCdcEventException("Debezium payload must be a JSON object");
            }

            var source = root.get("source");

            if (source == null || source.isNull()) {
                throw new InvalidCdcEventException("Debezium event does not contain source metadata");
            }

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
        } catch (InvalidCdcEventException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCdcEventException("Cannot parse Debezium event", e);
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