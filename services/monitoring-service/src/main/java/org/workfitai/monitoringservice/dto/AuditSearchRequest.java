package org.workfitai.monitoringservice.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query parameters for audit log search endpoints.
 * All fields are optional — omitting a field means "no filter on this dimension".
 */
public record AuditSearchRequest(
        String sourceService,   // filter by originating service (e.g., "auth-service")
        String actorUsername,   // filter by who performed the action
        String actorRole,       // filter by actor's role (ADMIN, HR_MANAGER, HR, CANDIDATE, SYSTEM)
        String entityType,      // filter by entity domain (USER, ROLE, APPLICATION, etc.)
        String entityId,        // drill into a specific entity's full history
        String action,          // filter by action type (USER_BLOCKED, ROLE_GRANTED, etc.)
        String companyId,       // ADMIN only: filter across companies; auto-set for HRM endpoints
        Boolean success,        // filter by outcome: true = succeeded only, false = failed only, null = all

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,           // start of time range (inclusive)

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to              // end of time range (inclusive)
) {}
