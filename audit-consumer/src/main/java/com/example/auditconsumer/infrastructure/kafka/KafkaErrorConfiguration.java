package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.application.exception.InvalidCdcEventException;
import com.example.auditconsumer.application.exception.UnsupportedCdcEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorConfiguration {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${audit.kafka.retry.interval-ms}") long retryIntervalMs,
            @Value("${audit.kafka.retry.max-retries}") long maxRetries) {

        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(record.topic() + ".dlt", record.partition())
        );

        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(
                retryIntervalMs, maxRetries
        ));

        errorHandler.addNotRetryableExceptions(
                InvalidCdcEventException.class,
                UnsupportedCdcEventException.class,
                DataIntegrityViolationException.class
        );

        errorHandler.setRetryListeners(
                (record, exception, deliveryAttempt) ->
                        log.warn(
                                "Kafka processing failed topic={} partition={} offset={} attempt={}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                deliveryAttempt,
                                exception
                        )
        );

        return errorHandler;
    }
}
