package com.example.auditconsumer.infrastructure.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@ConditionalOnProperty(
        prefix = "audit.kafka.dlt-replay",
        name = "enabled",
        havingValue = "true"
)
public class DltReplayKafkaConfiguration {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> dltReplayKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(new CommonContainerStoppingErrorHandler());

        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }
}