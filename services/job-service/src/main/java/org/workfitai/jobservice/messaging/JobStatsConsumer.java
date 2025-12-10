package org.workfitai.jobservice.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.dto.kafka.JobStatsUpdateEvent;
import org.workfitai.jobservice.service.impl.JobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatsConsumer {

    private final JobService jobService;

    @KafkaListener(topics = "${app.kafka.topics.job-stats-update:job-stats-update}", groupId = "${spring.kafka.consumer.group-id:job-service-group}", containerFactory = "jobStatsKafkaListenerContainerFactory")
    public void handleJobStatsUpdate(
            @Payload JobStatsUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        log.info("Received job stats update event from topic {} -> jobId={}, totalApps={}",
                topic, event.getJobId(), event.getTotalApplications());

        try {
            jobService.updateStats(event.getJobId(), event.getTotalApplications());

            log.info("Updated job stats successfully for jobId: {}", event.getJobId());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error updating stats for jobId {}: {}", event.getJobId(), ex.getMessage(), ex);
        }
    }
}
