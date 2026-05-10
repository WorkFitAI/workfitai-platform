package org.workfitai.jobservice.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationService {

  private final NotificationProducer notificationProducer;

  public NotificationService(NotificationProducer notificationProducer) {
    this.notificationProducer = notificationProducer;
  }

  @Async
  public void sendExpiredNotificationAsync(JobExpiredEventDTO dto) {
    sendExpiredNotification(dto);
  }

  private void sendExpiredNotification(JobExpiredEventDTO dto) {
    try {

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("jobTitle", dto.getJobTitle());
      metadata.put("jobId", dto.getJobId());
      metadata.put("companyName", dto.getCompanyName());
      metadata.put("status", "CLOSED");
      metadata.put("userName", dto.getCreatedBy());

      NotificationEvent event = NotificationEvent.builder()
          .eventId(UUID.randomUUID().toString())
          .eventType("JOB_EXPIRED")
          .timestamp(Instant.now())
          .recipientEmail(dto.getEmail())
          .recipientUserId(dto.getCreatedBy())
          .recipientRole("HR")
          .subject("Job Expired: " + dto.getJobTitle())
          .content("Your job posting \"" + dto.getJobTitle() + "\" has been successfully closed.")
          .templateType("job-expired")
          .notificationType("job_expired")
          .sendEmail(true)
          .createInAppNotification(true)
          .referenceId(dto.getJobId())
          .referenceType("JOB")
          .metadata(metadata)
          .build();

      notificationProducer.send(event);

    } catch (Exception e) {
      log.error("Failed to send notification", e);
    }
  }
}