package org.workfitai.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 * Auto-creates required topics on application startup.
 */
@Configuration
public class KafkaTopicConfig {

    /**
     * Topic for user change events (CREATE, UPDATE, DELETE, BLOCK, UNBLOCK)
     * Used for Elasticsearch synchronization
     */
    @Bean
    public NewTopic userChangeEventsTopic() {
        return TopicBuilder.name("user-change-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic for password change events
     * Used for password reset notifications
     */
    @Bean
    public NewTopic passwordChangeTopic() {
        return TopicBuilder.name("password-change")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
