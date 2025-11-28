package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterTopicConsumer {

    // Temporarily disabled to debug main consumer
    // @KafkaListener(topics =
    // "${app.kafka.topics.user-registration-dlt:user-registration-dlt}", groupId =
    // "${spring.kafka.consumer.group-id:user-service-group}-dlt", containerFactory
    // = "kafkaListenerContainerFactory")
    public void handleDeadLetter(
            @Payload UserRegistrationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.error("Processing dead letter from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);
        log.error("Dead letter event: {}", event);

        try {
            // Here you could:
            // 1. Store in database for manual review
            // 2. Send alert notifications
            // 3. Log to external monitoring system
            // 4. Attempt manual processing

            if (event != null && event.getUserData() != null) {
                log.error("Failed user registration for email: {}, userId: {}",
                        event.getUserData().getEmail(),
                        event.getUserData().getUserId());

                // TODO: Store failed registration for manual processing
                // deadLetterRepository.save(DeadLetterRecord.from(event));
            }

            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("Error processing dead letter: {}", ex.getMessage(), ex);
            // Still acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }
}