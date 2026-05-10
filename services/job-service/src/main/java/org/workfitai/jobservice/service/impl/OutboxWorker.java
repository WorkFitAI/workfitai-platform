package org.workfitai.jobservice.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.OutboxRepository;
import org.workfitai.jobservice.service.JobEventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxWorker {

  private final OutboxRepository outboxRepository;
  private final NotificationService notificationService;
  private final ObjectMapper objectMapper;
  private final JobEventProducer jobEventProducer;
  private final JobRepository jobRepository;

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void process() {

    List<OutboxExpiredJobEvent> events = outboxRepository.findTop10ByStatusIn(List.of("NEW", "FAILED"));

    for (OutboxExpiredJobEvent event : events) {

      try {
        handleEvent(event);

        event.setStatus("SENT");

      } catch (Exception e) {

        event.setRetryCount(event.getRetryCount() + 1);

        if (event.getRetryCount() >= 5) {
          event.setStatus("DEAD");
        } else {
          event.setStatus("FAILED");
        }

        log.error("Failed event {}", event.getId(), e);
      }

      outboxRepository.save(event);
    }
  }

  private void handleEvent(OutboxExpiredJobEvent event) throws Exception {

    JobExpiredEventDTO dto = objectMapper.readValue(event.getPayload(), JobExpiredEventDTO.class);

    switch (event.getType()) {

      case "JOB_EXPIRED":
        if (dto.getJobId() == null) {
          throw new RuntimeException("Missing jobId in payload");
        }

        Job job = jobRepository.findById(UUID.fromString(dto.getJobId()))
            .orElseThrow(() -> new RuntimeException("Job not found: " + dto.getJobId()));
        jobEventProducer.publishJobExpired(job);
        break;

      case "SEND_MAIL":
        notificationService.sendExpiredNotificationAsync(dto);
        break;
    }
  }
}
