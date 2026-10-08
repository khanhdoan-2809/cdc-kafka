package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.infrastructure.observability.AuditMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "audit.kafka.dlt-replay",
        name = "enabled",
        havingValue = "true"
)
public class AuditDltReplayListener {

    private final DltRecordMetadataReader metadataReader;
    private final AuditCdcProcessor processor;
    private final AuditMetrics auditMetrics;

    @KafkaListener(
            id = "audit-dlt-replay",
            groupId = "${audit.kafka.dlt-replay.group-id}",
            topics = {
                    "${audit.kafka.topics.transport}.dlt",
                    "${audit.kafka.topics.job}.dlt",
                    "${audit.kafka.topics.container}.dlt"
            },
            containerFactory = "dltReplayKafkaListenerContainerFactory"
    )
    public void replay(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            throw new IllegalStateException(
                    "DLT record has null payload topic=%s partition=%d offset=%d"
                            .formatted(record.topic(), record.partition(), record.offset())
            );
        }

        var metadata = metadataReader.read(record);

        log.info(
                "Replaying DLT record dltTopic={} dltPartition={} dltOffset={} originalTopic={} originalPartition={} originalOffset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );

        processor.process(record.value(), metadata);
        auditMetrics.dltReplayed(metadata.topic());

        log.info(
                "DLT replay completed originalTopic={} originalPartition={} originalOffset={}",
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );
    }
}