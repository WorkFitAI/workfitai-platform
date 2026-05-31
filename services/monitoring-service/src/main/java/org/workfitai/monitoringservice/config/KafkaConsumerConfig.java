package org.workfitai.monitoringservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.workfitai.monitoringservice.dto.kafka.AuditEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the audit-events topic.
 *
 * Group ID: monitoring-audit-consumer
 * Concurrency: 6 (matches partition count for maximum throughput)
 * Error handling: ErrorHandlingDeserializer wraps JsonDeserializer so
 * malformed messages are logged and skipped rather than crashing the consumer.
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private static final String GROUP_ID = "monitoring-audit-consumer";

    @Bean
    public ConsumerFactory<String, AuditEvent> auditEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        // All JsonDeserializer config via props only — mixing setters + props throws IllegalStateException
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        // Ignore __TypeId__ headers from producers (auth-service, application-service emit
        // their own local class name which doesn't exist on this classpath).
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(AuditEvent.class)));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> auditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditEventConsumerFactory());
        factory.setConcurrency(6); // matches audit-events partition count
        log.info("Kafka audit consumer factory configured (concurrency=6, group={})", GROUP_ID);
        return factory;
    }
}
