package org.workfitai.jobservice.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;
import org.workfitai.jobservice.model.Job;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationService {

  private final NotificationProducer notificationProducer;

  public NotificationService(NotificationProducer notificationProducer) {
    this.notificationProducer = notificationProducer;
  }

  @Async
  public void sendExpiredNotificationAsync(Job job, String hrEmail) {
    sendExpiredNotification(job, hrEmail);
  }

  private void sendExpiredNotification(Job job, String hrEmail) {
    try {

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("jobTitle", job.getTitle());
      metadata.put("jobId", job.getJobId().toString());
      metadata.put("companyName", job.getCompany() != null ? job.getCompany().getName() : "");
      metadata.put("status", job.getStatus().toString());

      NotificationEvent event = NotificationEvent.builder()
          .eventId(UUID.randomUUID().toString())
          .eventType("JOB_EXPIRED")
          .timestamp(Instant.now())
          .recipientEmail(hrEmail)
          .recipientUserId(job.getCreatedBy()) // Add userId for WebSocket push
          .recipientRole("HR")
          .subject("Job Expired: " + job.getTitle())
          .content("Your job posting \"" + job.getTitle() + "\" has been successfully expired.")
          .templateType("job-expired")
          .notificationType("job_expired") // Add notification type
          .sendEmail(true)
          .createInAppNotification(true) // Enable in-app notification
          .referenceId(job.getJobId().toString())
          .referenceType("JOB")
          .metadata(metadata)
          .build();

      notificationProducer.send(event);
      log.info("Sent job expired notification for job: {} to {}", job.getJobId(), hrEmail);
    } catch (Exception e) {
      log.error("Failed to send job expired notification for job: {}", job.getJobId(), e);
    }
  }
}