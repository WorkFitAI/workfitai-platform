package org.workfitai.jobservice.config.kafka.consumer;

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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:job-service-group}")
    private String groupId;

    /**
     * ConsumerFactory: tạo Kafka consumer với các config cần thiết
     * Key: String
     * Value: Object (sẽ deserialize từ JSON sang JobRegistrationEvent)
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Kafka server để kết nối
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Group ID của consumer
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializer key/value
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); // Sẽ đổi sang JsonDeserializer khi cần

        // Các config liên quan đến JSON deserialization
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // Cho phép deserialize tất cả các package (dev only)
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Không dùng header để xác định class
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                "org.workfitai.jobservice.dto.kafka.JobRegistrationEvent"); // Luôn deserialize JSON thành class này

        // Nếu chưa có offset, đọc từ đầu topic
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Tắt auto commit để tự commit thủ công
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * KafkaListenerContainerFactory: tạo container cho listener
     * Cho phép set các property như AckMode
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // Gán consumer factory
        factory.setConsumerFactory(consumerFactory());

        // Cấu hình commit offset thủ công và ngay lập tức
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }

}