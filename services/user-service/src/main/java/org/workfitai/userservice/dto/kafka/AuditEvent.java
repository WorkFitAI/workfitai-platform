package org.workfitai.userservice.dto.kafka;

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
        String sourceService,        // "user-service"
        String actorUsername,        // who performed the action (ADMIN username)
        String actorRole,            // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
        String companyId,            // null for platform-level ADMIN actions; set for HR_MANAGER actions
        String entityType,           // "USER"
        String entityId,             // username of the affected user
        String action,               // USER_CREATED | USER_BLOCKED | USER_UNBLOCKED | USER_DELETED | USER_APPROVED | USER_REJECTED
        Map<String, Object> before,  // state before action; null for CREATE
        Map<String, Object> after,   // state after action;  null for DELETE
        Instant occurredAt,
        Boolean success,             // true = succeeded, false = failed, null = not tracked
        String errorMessage,         // populated when success=false; null otherwise
        String actorIp               // client IP; null for async/scheduled contexts
) {}
