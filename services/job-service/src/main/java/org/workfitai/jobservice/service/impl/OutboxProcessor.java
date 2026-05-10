package org.workfitai.jobservice.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;
import org.workfitai.jobservice.model.enums.EventStatus;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.OutboxRepository;
import org.workfitai.jobservice.service.JobEventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OutboxProcessor {

  private final OutboxRepository outboxRepository;
  private final NotificationService notificationService;
  private final ObjectMapper objectMapper;
  private final JobEventProducer jobEventProducer;
  private final JobRepository jobRepository;

  public OutboxProcessor(OutboxRepository outboxRepository, NotificationService notificationService,
      ObjectMapper objectMapper,
      JobEventProducer jobEventProducer, JobRepository jobRepository) {
    this.outboxRepository = outboxRepository;
    this.notificationService = notificationService;
    this.objectMapper = objectMapper;
    this.jobEventProducer = jobEventProducer;
    this.jobRepository = jobRepository;
  }

  @Transactional
  public void process() {

    List<OutboxExpiredJobEvent> events = outboxRepository
        .findTop100ByStatusInOrderByCreatedDateAsc(List.of(EventStatus.NEW, EventStatus.FAILED));

    for (OutboxExpiredJobEvent event : events) {

      try {
        handleEvent(event);

        event.setStatus(EventStatus.SENT);

      } catch (Exception e) {

        event.setRetryCount(event.getRetryCount() + 1);

        if (event.getRetryCount() >= 5) {
          event.setStatus(EventStatus.DEAD);
        } else {
          event.setStatus(EventStatus.FAILED);
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