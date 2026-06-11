package org.workfitai.jobservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.dto.kafka.AuditEvent;

/**
 * Publishes AuditEvent records to the "audit-events" Kafka topic.
 *
 * Partition key: entityType + ":" + entityId ensures all events for the same
 * entity land on the same partition (ordered delivery per entity).
 *
 * Fire-and-forget — a Kafka failure is logged but NEVER propagates to the
 * caller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private static final String TOPIC = "audit-events";

  public void publish(AuditEvent event) {
    try {
      String partitionKey = event.entityType() + ":" + event.entityId();
      kafkaTemplate.send(TOPIC, partitionKey, event);
      log.debug("[AUDIT-PUBLISH] {} {} by {}", event.action(), event.entityId(), event.actorUsername());
    } catch (Exception ex) {
      // Non-fatal: audit failure must never block or roll back the business operation
      log.error("[AUDIT-PUBLISH] Kafka send failed for event {}: {}", event.eventId(), ex.getMessage());
    }
  }
}
