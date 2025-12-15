package org.workfitai.applicationservice.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Response containing audit log details
 */
public record AuditLogResponse(
    String id,
    String entityType,
    String entityId,
    String action,
    String performedBy,
    Instant performedAt,
    Map<String, Object> beforeState,
    Map<String, Object> afterState,
    Map<String, Object> metadata,
    Boolean containsPII
) {}
