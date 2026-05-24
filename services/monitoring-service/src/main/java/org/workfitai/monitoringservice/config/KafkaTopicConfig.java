package org.workfitai.monitoringservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations owned by monitoring-service.
 *
 * monitoring-service is the single authority for the "audit-events" topic.
 * Partition count = 6; partition key = entityType:entityId ensures per-entity ordering.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name("audit-events")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
