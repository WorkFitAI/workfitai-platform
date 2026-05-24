package org.workfitai.monitoringservice.dto;

import java.time.Instant;
import java.util.Map;

/**
 * API response shape for a single audit event.
 * Returned by GET /api/admin/audit and GET /api/hrm/audit.
 */
public record AuditEventResponse(
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
        Instant occurredAt
) {}
