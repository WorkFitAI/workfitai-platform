package org.workfitai.authservice.dto.kafka;

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
        String sourceService,        // "auth-service"
        String actorUsername,        // who triggered the action (grantor/revoker)
        String actorRole,            // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
        String companyId,            // null for platform-level ADMIN actions
        String entityType,           // "ROLE"
        String entityId,             // role name (e.g., "HR_MANAGER", "HR")
        String action,               // "ROLE_GRANTED" | "ROLE_REVOKED"
        Map<String, Object> before,  // null for grant (no prior state)
        Map<String, Object> after,   // {"targetUsername": "...", "roleName": "..."}
        Instant occurredAt
) {}
