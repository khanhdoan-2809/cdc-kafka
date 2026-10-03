package com.example.auditconsumer.infrastructure.kafka;

import com.example.auditconsumer.infrastructure.kafka.model.SourceRecordMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class DltRecordMetadataReader {

    public SourceRecordMetadata read(ConsumerRecord<String, String> record) {
        var topic = readString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        var partition = readInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
        var offset = readLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET);
        var timestamp = readLong(record, KafkaHeaders.DLT_ORIGINAL_TIMESTAMP);

        return new SourceRecordMetadata(
                topic,
                partition,
                offset,
                timestamp,
                record.key()
        );
    }

    private String readString(ConsumerRecord<String, String> record, String name) {
        return new String(
                requiredHeader(record, name).value(),
                StandardCharsets.UTF_8
        );
    }

    private int readInt(ConsumerRecord<String, String> record, String name) {
        var value = requiredHeader(record, name).value();

        if (value.length != Integer.BYTES) {
            throw new IllegalStateException("Invalid integer DLT header: " + name);
        }

        return ByteBuffer.wrap(value).getInt();
    }

    private long readLong(ConsumerRecord<String, String> record, String name) {
        var value = requiredHeader(record, name).value();

        if (value.length != Long.BYTES) {
            throw new IllegalStateException("Invalid long DLT header: " + name);
        }

        return ByteBuffer.wrap(value).getLong();
    }

    private Header requiredHeader(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);

        if (header == null) {
            throw new IllegalStateException("Required DLT header is missing: " + name);
        }

        return header;
    }
}