package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.NotificationEvent;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${app.kafka.topics.notification:notification-events}")
  private String notificationTopic;

  public void send(NotificationEvent event) {
    try {
      CompletableFuture<SendResult<String, Object>> future =
          kafkaTemplate.send(notificationTopic, event.getRecipientEmail(), event);
      future.whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error("Failed to send notification event {}", event.getEventId(), throwable);
        } else {
          log.info("Notification event {} sent to topic {} offset {}",
              event.getEventId(),
              result.getRecordMetadata().topic(),
              result.getRecordMetadata().offset());
        }
      });
    } catch (Exception ex) {
      log.error("Error sending notification event {}", event.getEventId(), ex);
    }
  }
}
