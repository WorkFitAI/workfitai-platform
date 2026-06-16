package org.workfitai.cvservice.dto.kafka;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event published to the "audit-events" Kafka topic.
 *
 * Copied per-service (not shared library) per KISS principle.
 * Partition key: entityType + ":" + entityId (ordering per entity guaranteed).
 */
public record AuditEvent(
        String eventId,
        String sourceService,
        String actorUsername,
        String actorRole,
        String companyId,
        String entityType,
        String entityId,
        String action,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant occurredAt,
        Boolean success,
        String errorMessage,
        String actorIp
) {}
