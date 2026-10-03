package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.application.AuditEventMapper;
import com.example.auditconsumer.application.AuditLogService;
import com.example.auditconsumer.infrastructure.kafka.model.SourceRecordMetadata;
import com.example.auditconsumer.infrastructure.observability.AuditMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditCdcProcessor {

    private final DebeziumEventParser parser;
    private final AuditEventMapper mapper;
    private final AuditLogService auditLogService;
    private final AuditMetrics auditMetrics;

    public void process(String payload, SourceRecordMetadata metadata) {
        var debeziumEvent = parser.parse(payload);
        var auditEvent = mapper.map(debeziumEvent, metadata);

        if (auditEvent.isEmpty()) {
            log.debug(
                    "Ignoring snapshot topic={} partition={} offset={}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset()
            );

            return;
        }

        var event = auditEvent.get();
        var result = auditLogService.save(event);

        auditMetrics.processed(event, result);

        switch (result) {
            case INSERTED -> log.info(
                    "Audit persisted entity={} entityId={} operation={} topic={} partition={} offset={}",
                    event.entityType(),
                    event.entityId(),
                    event.operation(),
                    event.sourceTopic(),
                    event.sourcePartition(),
                    event.sourceOffset()
            );

            case DUPLICATE -> log.info(
                    "Audit event already processed topic={} partition={} offset={}",
                    event.sourceTopic(),
                    event.sourcePartition(),
                    event.sourceOffset()
            );
        }
    }
}