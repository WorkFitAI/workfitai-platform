package org.workfitai.applicationservice.dto.kafka;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event published to the "audit-events" Kafka topic.
 *
 * Copied per-service (not shared library) per KISS principle.
 * Partition key: entityType + ":" + entityId (ordering per entity guaranteed).
 */
public record AuditEvent(
        String eventId,              // UUID idempotency key
        String sourceService,        // "application-service"
        String actorUsername,        // who performed the action
        String actorRole,            // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
        String companyId,            // null for platform-level ADMIN actions; set for HR/HR_MANAGER actions
        String entityType,           // "APPLICATION"
        String entityId,             // application ID
        String action,               // APP_CREATED | APP_STATUS_CHANGED | APP_WITHDRAWN | APP_ASSIGNED | APP_OVERRIDE
        Map<String, Object> before,  // state before action; null for CREATE
        Map<String, Object> after,   // state after action;  null for DELETE
        Instant occurredAt
) {}
