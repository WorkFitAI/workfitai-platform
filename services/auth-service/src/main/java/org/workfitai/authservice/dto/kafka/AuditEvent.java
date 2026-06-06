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
        String actorUsername,        // who triggered the action
        String actorRole,            // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
        String companyId,            // null for platform-level ADMIN actions
        String entityType,           // USER | ROLE | SESSION | OAUTH
        String entityId,             // username or role name affected
        String action,               // AUTH_LOGIN_SUCCESS | AUTH_LOGIN_FAILED | ROLE_GRANTED | ...
        Map<String, Object> before,  // state before action; null for CREATE/simple events
        Map<String, Object> after,   // state after action; null for DELETE/simple events
        Instant occurredAt,
        Boolean success,             // true = succeeded, false = failed, null = not tracked
        String errorMessage,         // populated when success=false; null otherwise
        String actorIp               // client IP; null for async/scheduled contexts
) {}
