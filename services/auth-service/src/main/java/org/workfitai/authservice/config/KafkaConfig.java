package org.workfitai.authservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.workfitai.authservice.dto.kafka.UserChangeEvent;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:auth-service-group}")
    private String groupId;

    // ==================== Producer Configuration ====================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ==================== Consumer Configuration ====================

    @Bean
    public ConsumerFactory<String, UserRegistrationEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        // Configure JsonDeserializer to ignore type headers from producer
        // This is necessary because user-service sends events with different package
        // name
        JsonDeserializer<UserRegistrationEvent> jsonDeserializer = new JsonDeserializer<>(UserRegistrationEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeMapperForKey(false);
        jsonDeserializer.addTrustedPackages("*");

        // Wrap with ErrorHandlingDeserializer for resilience
        ErrorHandlingDeserializer<UserRegistrationEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegistrationEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserRegistrationEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        log.info("Kafka consumer container factory configured for UserRegistrationEvent");
        return factory;
    }

    // ==================== UserChangeEvent Consumer Configuration
    // ====================

    @Bean
    public ConsumerFactory<String, UserChangeEvent> userChangeEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        JsonDeserializer<UserChangeEvent> jsonDeserializer = new JsonDeserializer<>(UserChangeEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeMapperForKey(false);
        jsonDeserializer.addTrustedPackages("*");

        ErrorHandlingDeserializer<UserChangeEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserChangeEvent> userChangeEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserChangeEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userChangeEventConsumerFactory());
        factory.setConcurrency(3);
        log.info("Kafka consumer container factory configured for UserChangeEvent");
        return factory;
    }
}