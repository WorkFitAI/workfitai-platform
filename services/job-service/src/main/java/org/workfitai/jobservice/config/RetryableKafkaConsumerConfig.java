package org.workfitai.jobservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class RetryableKafkaConsumerConfig {

    // Số lần retry trước khi gửi message sang DLT
    @Value("${app.kafka.retry.topic.attempts:3}")
    private int retryAttempts;

    // Delay giữa các lần retry (ms)
    @Value("${app.kafka.retry.topic.delay:1000}")
    private long retryDelay;

    // Topic Dead Letter (DLT) để lưu message thất bại sau retry
    @Value("${app.kafka.topics.job-event-dlt:job-event-dlt}")
    private String dltTopic;

    /**
     * Tạo một KafkaListenerContainerFactory riêng
     * - Hỗ trợ retry + gửi message thất bại sang DLT
     * - Sử dụng manual ack (bạn tự commit offset)
     */
    @Bean(name = "retryableKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> retryKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Cấu hình manual acknowledgment
        // Bạn sẽ tự commit offset bằng cách gọi ack.acknowledge()
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Cấu hình error handler với retry và DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                // Callback khi message thất bại sau retry
                (consumerRecord, ex) -> {
                    log.error("Failed to process job message after {} retries, sending to DLT: {}",
                            retryAttempts, consumerRecord.value(), ex);

                    // Key nếu có, nếu không có thì gán "unknown"
                    String key = consumerRecord.key() != null ? consumerRecord.key().toString() : "unknown";

                    // Gửi message lỗi sang DLT topic
                    kafkaTemplate.send(dltTopic, key, consumerRecord.value());
                },
                // FixedBackOff: delay giữa các lần retry, số lần retry
                new FixedBackOff(retryDelay, retryAttempts)
        );

        // Các exception không retry, ví dụ IllegalArgumentException sẽ ngay lập tức gửi DLT
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        // Gán errorHandler vào factory
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
