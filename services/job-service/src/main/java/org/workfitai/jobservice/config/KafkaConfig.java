package org.workfitai.jobservice.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.workfitai.jobservice.dto.kafka.CompanySyncEvent;
import org.workfitai.jobservice.model.dto.kafka.JobStatsUpdateEvent;

/**
 * Kafka configuration for job-service.
 * 
 * Configures consumer factories for:
 * - JobStatsUpdateEvent (job-stats-update topic)
 * - CompanySyncEvent (company-sync topic)
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:job-service-group}")
    private String groupId;

    /**
     * Common consumer configuration shared by all consumers
     */
    private Map<String, Object> commonConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    /**
     * Consumer factory for JobStatsUpdateEvent (job-stats-update topic)
     */
    @Bean
    public ConsumerFactory<String, JobStatsUpdateEvent> jobStatsConsumerFactory() {
        Map<String, Object> props = commonConsumerConfigs();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, JobStatsUpdateEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(JobStatsUpdateEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobStatsUpdateEvent> jobStatsKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, JobStatsUpdateEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jobStatsConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Consumer factory for CompanySyncEvent (company-sync topic)
     */
    @Bean
    public ConsumerFactory<String, CompanySyncEvent> companySyncConsumerFactory() {
        Map<String, Object> props = commonConsumerConfigs();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CompanySyncEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(CompanySyncEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CompanySyncEvent> companySyncKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CompanySyncEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(companySyncConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
