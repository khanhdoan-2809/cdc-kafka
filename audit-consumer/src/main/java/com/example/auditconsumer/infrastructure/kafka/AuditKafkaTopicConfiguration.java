package com.example.auditconsumer.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class AuditKafkaTopicConfiguration {

    @Bean
    KafkaAdmin.NewTopics auditDltTopics(
            @Value("${audit.kafka.topics.transport}") String transportTopic,
            @Value("${audit.kafka.topics.job}") String jobTopic,
            @Value("${audit.kafka.topics.container}") String containerTopic,
            @Value("${audit.kafka.dlt.partitions}") int partitions,
            @Value("${audit.kafka.dlt.replication-factor}") int replicationFactor) {

        return new KafkaAdmin.NewTopics(
                dlt(transportTopic, partitions, replicationFactor),
                dlt(jobTopic, partitions, replicationFactor),
                dlt(containerTopic, partitions, replicationFactor)
        );
    }

    private NewTopic dlt(String sourceTopic, int partitions, int replicationFactor) {
        return TopicBuilder.name(sourceTopic + ".dlt")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}