package org.workfitai.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.NotificationOrchestrator;

/**
 * Kafka consumer for notification events.
 * Delegates processing to NotificationOrchestrator which uses Strategy pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationOrchestrator orchestrator;

    @KafkaListener(topics = "${app.kafka.topics.notification:notification-events}", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void handleNotification(@Payload NotificationEvent event) {
        if (event == null) {
            log.warn("Received null notification event");
            return;
        }

        log.info("[Kafka] Received notification: type={}, template={}, to={}, source={}",
                event.getEventType(),
                event.getTemplateType(),
                event.getRecipientEmail(),
                event.getSourceService());

        try {
            boolean processed = orchestrator.process(event);

            if (processed) {
                log.info("Notification processed successfully: {}", event.getEventId());
            } else {
                log.warn("Notification processing failed or skipped: {}", event.getEventId());
            }
        } catch (Exception e) {
            log.error("Error processing notification {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }
}
