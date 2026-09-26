package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.application.AuditEventMapper;
import com.example.auditconsumer.infrastructure.kafka.model.SourceRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditCdcListener {

    private final DebeziumEventParser parser;
    private final AuditEventMapper mapper;

    @KafkaListener(
            topics = {
                "${audit.kafka.topics.transport}",
                "${audit.kafka.topics.job}",
                "${audit.kafka.topics.container}"
            }
    )
    public void consume(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            log.debug(
                    "Ignoring tombstone topic={} partition={} offset={} key={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );

            return;
        }

        var debeziumEvent = parser.parse(record.value());
        var metadata = new SourceRecordMetadata(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                record.key()
        );

        var auditEvent = mapper.map(debeziumEvent, metadata);

        if (auditEvent.isEmpty()) {
            log.debug(
                    "Ignoring snapshot topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset()
            );

            return;
        }

        var event = auditEvent.get();

        log.info(
                "Audit event entity={} entityId={} operation={} actor={} changedFields={} topic={} partition={} offset={}",
                event.entityType(),
                event.entityId(),
                event.operation(),
                event.actorId(),
                event.changedFields(),
                event.sourceTopic(),
                event.sourcePartition(),
                event.sourceOffset()
        );

        log.debug("Normalized audit event={}", event);
    }

}
