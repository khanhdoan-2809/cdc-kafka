package com.example.auditconsumer.application;

import com.example.auditconsumer.domain.AuditEvent;
import com.example.auditconsumer.domain.AuditOperation;
import com.example.auditconsumer.domain.EntityType;
import com.example.auditconsumer.infrastructure.kafka.model.DebeziumEvent;
import com.example.auditconsumer.infrastructure.kafka.model.SourceRecordMetadata;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Component
public class AuditEventMapper {

    private static final Set<String> TECHNICAL_FIELDS = Set.of(
            "created_at",
            "updated_at",
            "created_by",
            "updated_by",
            "deleted_at"
    );

    public Optional<AuditEvent> map(DebeziumEvent event, SourceRecordMetadata metadata) {
        if ("r".equals(event.operation())) {
            return Optional.empty();
        }

        var operation = resolveOperation(event);
        var row = event.after() != null ? event.after() : event.before();

        if (row == null) {
            throw new IllegalArgumentException("CDC event does not contain before or after row");
        }

        var entityType = EntityType.fromTable(event.source().table());
        var entityId = longValue(row, "id");

        var transportId = resolveTransportId(entityType, row);
        var jobId = resolveJobId(entityType, row);
        var containerId = resolveContainerId(entityType, row);

        var actorId = resolveActor(event);
        var changedFields = operation == AuditOperation.UPDATE
                ? findChangedFields(event.before(), event.after())
                : Set.<String>of();

        var transaction = event.transaction();

        return Optional.of(
                new AuditEvent(
                        entityType,
                        entityId,

                        transportId,
                        jobId,
                        containerId,

                        operation,
                        actorId,

                        event.before(),
                        event.after(),

                        changedFields,

                        event.source().lsn(),
                        event.source().transactionId(),

                        toInstant(event.source().occurredAtMs()),
                        toInstant(event.processedAtMs()),

                        transaction == null ? null : transaction.id(),
                        transaction == null ? null : transaction.totalOrder(),
                        transaction == null ? null : transaction.dataCollectionOrder(),

                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        metadata.key()
                )
        );
    }

    private AuditOperation resolveOperation(DebeziumEvent event) {
        return switch (event.operation()) {
            case "c" -> AuditOperation.INSERT;
            case "u" -> isSoftDelete(event) ? AuditOperation.DELETE : AuditOperation.UPDATE;
            case "d" -> AuditOperation.DELETE;
            default -> throw new IllegalArgumentException("Unsupported Debezium operation: " + event.operation());
        };
    }

    private String resolveActor(DebeziumEvent event) {
        return switch (event.operation()) {
            case "c" -> textValue(event.after(), "created_by");
            case "u" -> textValue(event.after(), "updated_by");

            /*
             * A physical DELETE cannot reliably tell us who executed DELETE.
             *
             * OLD.updated_by is only the previous updater, so don't lie and
             * report that person as the deleter.
             */
            case "d" -> null;

            default -> null;
        };
    }

    private boolean isSoftDelete(DebeziumEvent event) {
        if (event.before() == null || event.after() == null) {
            return false;
        }

        var beforeDeletedAt = event.before().get("deleted_at");
        var afterDeletedAt = event.after().get("deleted_at");

        return isNull(beforeDeletedAt) && !isNull(afterDeletedAt);
    }

    private Set<String> findChangedFields(JsonNode before, JsonNode after) {
        if (before == null || after == null) {
            return Set.of();
        }

        var names = new TreeSet<String>();

        names.addAll(before.propertyNames());
        names.addAll(after.propertyNames());

        names.removeAll(TECHNICAL_FIELDS);

        var changed = new TreeSet<String>();

        for (var name : names) {
            if (!Objects.equals(before.get(name), after.get(name))) {
                changed.add(name);
            }
        }

        return Set.copyOf(changed);
    }

    private Long resolveTransportId(EntityType type, JsonNode row) {
        return switch (type) {
            case TRANSPORT -> longValue(row, "id");
            case JOB -> longValue(row, "transport_id");
            case CONTAINER -> null;
        };
    }

    private Long resolveJobId(EntityType type, JsonNode row) {
        return switch (type) {
            case TRANSPORT -> null;
            case JOB -> longValue(row, "id");
            case CONTAINER -> longValue(row, "job_id");
        };
    }

    private Long resolveContainerId(EntityType type, JsonNode row) {
        return type == EntityType.CONTAINER ? longValue(row, "id") : null;
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        var value = node.get(field);

        return value == null || value.isNull() ? null : value.longValue();
    }

    private String textValue(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        var value = node.get(field);

        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }

    private Instant toInstant(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }
}