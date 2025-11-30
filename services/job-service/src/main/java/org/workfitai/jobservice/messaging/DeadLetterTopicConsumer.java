package org.workfitai.jobservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.dto.kafka.JobRegistrationEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterTopicConsumer {
    @KafkaListener(
            topics = "${app.kafka.topics.job-event-dlt:job-event-dlt}",
            groupId = "${spring.kafka.consumer.group-id:job-service-group}-dlt",
            containerFactory = "retryableKafkaListenerContainerFactory"
    )
    public void handleDeadLetter(
            @Payload JobRegistrationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.error("Processing dead letter from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);
        log.error("Dead letter event: {}", event);

        try {
            if (event != null && event.getJobData() != null) {
                log.error("Failed job processing for jobId: {}, title: {}",
                        event.getJobData().getJobId(),
                        event.getJobData().getTitle());

                // TODO: lưu vào DB để review hoặc reprocess
                // deadLetterRepository.save(DeadLetterRecord.from(event));
            }

            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("Error processing dead letter: {}", ex.getMessage(), ex);
            acknowledgment.acknowledge(); // tránh infinite loop
        }
    }
}
