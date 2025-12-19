package org.workfitai.applicationservice.dto.response;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;

/**
 * Response DTO for HR audit activity.
 * Represents a single audit log entry for HR users' activities.
 */
@Builder
public record HRAuditActivityResponse(
        String id,
        String entityType,
        String entityId,
        String action,
        String performedBy,
        Instant performedAt,
        Map<String, Object> metadata,
        // Optional: Include user details for better readability
        String performerFullName,
        String performerRole) {
}
