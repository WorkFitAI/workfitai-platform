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
import org.workfitai.jobservice.service.impl.JobService;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobRegistrationConsumer {

    private final JobService jobService;

    @KafkaListener(
            topics = "${app.kafka.topics.job-registration:job-registration}",
            groupId = "${spring.kafka.consumer.group-id:job-service-group}",
            containerFactory = "retryableKafkaListenerContainerFactory"
    )
    public void handleJobRegistration(
            @Payload JobRegistrationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received job registration event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            if (event == null || event.getJobData() == null) {
                log.error("Invalid job registration event: {}", event);
                acknowledgment.acknowledge();
                return;
            }

            if (!"JOB_REGISTERED".equals(event.getEventType())) {
                log.warn("Unknown event type: {}, skipping", event.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            var jobData = event.getJobData();
            log.info("Processing job registration for jobId: {}", jobData.getJobId());

            jobService.createFromKafkaEvent(jobData);

            log.info("Successfully processed job registration for jobId: {}", jobData.getJobId());
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("Error processing job registration event: {}", ex.getMessage(), ex);
            // Không ack để retry hoặc DLT xử lý
            throw new RuntimeException("Failed to process job registration event", ex);
        }
    }
}