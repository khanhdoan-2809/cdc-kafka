package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.application.exception.InvalidCdcEventException;
import com.example.auditconsumer.application.exception.UnsupportedCdcEventException;
import com.example.auditconsumer.infrastructure.observability.AuditMetrics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@AllArgsConstructor
public class KafkaErrorConfiguration {

    private final AuditMetrics auditMetrics;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${audit.kafka.retry.interval-ms}") long retryIntervalMs,
            @Value("${audit.kafka.retry.max-retries}") long maxRetries) {

        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(
                                record.topic() + ".dlt",
                                record.partition()
                        )
        );

        /*
         * This is an audit pipeline.
         *
         * If publishing to the DLT itself fails, we must not consider
         * the original Kafka record successfully recovered.
         */
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setLogRecoveryRecord(true);

        var errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, maxRetries)
        );

        errorHandler.addNotRetryableExceptions(
                InvalidCdcEventException.class,
                UnsupportedCdcEventException.class,
                DataIntegrityViolationException.class
        );

        errorHandler.setRetryListeners(new RetryListener() {

            @Override
            public void failedDelivery(
                    ConsumerRecord<?, ?> record,
                    Exception exception,
                    int deliveryAttempt) {

                auditMetrics.retry(record.topic());

                log.warn(
                        "Kafka processing failed topic={} partition={} offset={} attempt={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        exception
                );
            }

            @Override
            public void recovered(
                    ConsumerRecord<?, ?> record,
                    Exception exception) {

                auditMetrics.dlt(record.topic());

                log.error(
                        "Kafka record moved to DLT sourceTopic={} dltTopic={} partition={} offset={}",
                        record.topic(),
                        record.topic() + ".dlt",
                        record.partition(),
                        record.offset(),
                        exception
                );
            }

            @Override
            public void recoveryFailed(
                    ConsumerRecord<?, ?> record,
                    Exception original,
                    Exception failure) {

                log.error(
                        "DLT publishing failed sourceTopic={} partition={} offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        failure
                );
            }
        });

        return errorHandler;
    }
}