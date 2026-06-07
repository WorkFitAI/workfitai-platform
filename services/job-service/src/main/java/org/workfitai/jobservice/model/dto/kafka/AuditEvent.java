package org.workfitai.jobservice.model.dto.kafka;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event published to the "audit-events" Kafka topic.
 * Partition key: entityType + ":" + entityId (ordering per entity guaranteed).
 */
public record AuditEvent(
    String eventId, // UUID idempotency key
    String sourceService, // e.g. "job-service", "cv-service"
    String actorUsername,
    String actorRole, // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
    String companyId, // null for platform-level ADMIN actions
    String entityType, // JOB | CV | SKILL | ...
    String entityId,
    String action, // JOB_CREATED | CV_UPLOADED | ...
    Map<String, Object> before,
    Map<String, Object> after,
    Instant occurredAt,
    Boolean success,
    String errorMessage,
    String actorIp) {
}
