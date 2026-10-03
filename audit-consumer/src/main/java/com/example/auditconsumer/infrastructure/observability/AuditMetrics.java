package com.example.auditconsumer.infrastructure.observability;

import com.example.auditconsumer.application.port.AuditSaveResult;
import com.example.auditconsumer.domain.AuditEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AuditMetrics {

    private final MeterRegistry meterRegistry;

    public void processed(AuditEvent event, AuditSaveResult result) {
        meterRegistry.counter(
                "audit.events.processed",
                "entity", event.entityType().name(),
                "operation", event.operation().name(),
                "result", result.name()
        ).increment();

        recordEndToEndLatency(event);
    }

    public void retry(String topic) {
        meterRegistry.counter(
                "audit.kafka.retries",
                "topic", topic
        ).increment();
    }

    public void dlt(String topic) {
        meterRegistry.counter(
                "audit.kafka.dlt",
                "topic", topic
        ).increment();
    }

    private void recordEndToEndLatency(AuditEvent event) {
        if (event.databaseOccurredAt() == null) {
            return;
        }

        var duration = Duration.between(
                event.databaseOccurredAt(),
                Instant.now()
        );

        if (duration.isNegative()) {
            return;
        }

        Timer.builder("audit.cdc.end.to.end")
                .description("Time from source database change until audit persistence")
                .register(meterRegistry)
                .record(duration);
    }

    public void dltReplayed(String topic) {
        meterRegistry.counter(
                "audit.kafka.dlt.replayed",
                "topic", topic
        ).increment();
    }
}