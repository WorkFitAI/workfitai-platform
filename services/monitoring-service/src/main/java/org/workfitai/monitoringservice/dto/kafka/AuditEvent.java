package org.workfitai.monitoringservice.dto.kafka;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event record consumed from Kafka "audit-events" topic.
 *
 * Published by every business service (auth, user, application, job, cv)
 * when a significant business action occurs. This record is the single
 * shape that monitoring-service indexes into Elasticsearch.
 *
 * Partition key: entityType + ":" + entityId (ensures ordering per entity).
 */
public record AuditEvent(
        String eventId,              // UUID idempotency key
        String sourceService,        // "auth-service", "user-service", etc.
        String actorUsername,        // who triggered the action
        String actorRole,            // ADMIN | HR_MANAGER | HR | CANDIDATE | SYSTEM
        String companyId,            // null for platform-level; non-null for company-scoped HRM events
        String entityType,           // USER | ROLE | APPLICATION | CV | JOB | COMPANY | SESSION | OAUTH
        String entityId,             // primary key of the affected entity
        String action,               // standardized action (USER_BLOCKED, ROLE_GRANTED, AUTH_LOGIN_SUCCESS, etc.)
        Map<String, Object> before,  // state before action; null for CREATE events
        Map<String, Object> after,   // state after action;  null for DELETE events
        Instant occurredAt,          // event timestamp
        Boolean success,             // true = succeeded, false = failed, null = not tracked
        String errorMessage,         // populated when success=false; null otherwise
        String actorIp               // client IP; null for async/scheduled contexts
) {}
