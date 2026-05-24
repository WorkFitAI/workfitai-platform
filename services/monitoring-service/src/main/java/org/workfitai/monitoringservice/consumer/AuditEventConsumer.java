package org.workfitai.monitoringservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.monitoringservice.dto.kafka.AuditEvent;
import org.workfitai.monitoringservice.service.AuditSearchService;

/**
 * Kafka consumer for the "audit-events" topic.
 *
 * Listens with group "monitoring-audit-consumer" and concurrency 6 (matching partition count).
 * Each consumed event is indexed into Elasticsearch under workfitai-audit-{date}.
 * Errors are caught and logged; the offset still advances so a single bad message
 * doesn't stall the consumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditSearchService auditSearchService;

    @KafkaListener(
            topics = "audit-events",
            groupId = "monitoring-audit-consumer",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void consume(@Payload AuditEvent event) {
        if (event == null) {
            log.warn("[AUDIT-CONSUMER] Received null event, skipping");
            return;
        }
        try {
            auditSearchService.index(event);
            log.debug("[AUDIT-CONSUMER] Indexed event {} ({} {})",
                    event.eventId(), event.action(), event.entityType());
        } catch (Exception e) {
            log.error("[AUDIT-CONSUMER] Failed to index event {}: {}", event.eventId(), e.getMessage(), e);
        }
    }
}
