package org.workfitai.userservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class RetryableKafkaConsumerConfig {

    @Value("${app.kafka.retry.topic.attempts:3}")
    private int retryAttempts;

    @Value("${app.kafka.retry.topic.delay:1000}")
    private long retryDelay;

    @Value("${app.kafka.topics.user-registration-dlt:user-registration-dlt}")
    private String dltTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Configure manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Configure error handler with retry and DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, ex) -> {
                    log.error("Failed to process message after {} retries, sending to DLT: {}",
                            retryAttempts, consumerRecord.value(), ex);

                    // Send to DLT with proper null handling
                    String key = consumerRecord.key() != null ? consumerRecord.key().toString() : "unknown";
                    kafkaTemplate.send(dltTopic, key, consumerRecord.value());
                },
                new FixedBackOff(retryDelay, retryAttempts));

        // Add exception types that should NOT trigger retry
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}