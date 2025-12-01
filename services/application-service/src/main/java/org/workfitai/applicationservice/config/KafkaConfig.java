package org.workfitai.applicationservice.config;

import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka configuration for application-service.
 * 
 * NOTE: Kafka is temporarily disabled. Events are logged but not sent.
 * TODO: Re-enable when other services are ready to consume events.
 * 
 * Events that will be published when enabled:
 * - APPLICATION_CREATED: When a new application is submitted
 * - APPLICATION_WITHDRAWN: When a candidate withdraws their application
 * - STATUS_CHANGED: When HR/Admin updates application status
 */
@Configuration
@Slf4j
public class KafkaConfig {

    /*
     * TODO: Re-enable when Kafka consumers are ready
     * 
     * @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
     * private String bootstrapServers;
     * 
     * @Bean
     * public ProducerFactory<String, Object> producerFactory() {
     * Map<String, Object> props = new HashMap<>();
     * props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
     * props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
     * StringSerializer.class);
     * props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
     * JsonSerializer.class);
     * props.put(ProducerConfig.ACKS_CONFIG, "all");
     * props.put(ProducerConfig.RETRIES_CONFIG, 3);
     * props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
     * props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
     * props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
     * props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
     * log.info("Kafka producer configured with bootstrap servers: {}",
     * bootstrapServers);
     * return new DefaultKafkaProducerFactory<>(props);
     * }
     * 
     * @Bean
     * public KafkaTemplate<String, Object> kafkaTemplate() {
     * return new KafkaTemplate<>(producerFactory());
     * }
     */
}
