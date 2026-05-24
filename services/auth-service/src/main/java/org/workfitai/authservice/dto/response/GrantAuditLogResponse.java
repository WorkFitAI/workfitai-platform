package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.model.GrantAuditLog;

import java.time.Instant;

/**
 * Read-only projection of GrantAuditLog for API responses.
 * Exposes all fields except the internal MongoDB _id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantAuditLogResponse {

    private String id;

    /** Username of the actor who performed the grant/revoke */
    private String grantorUsername;

    /** Username of the user whose roles were changed */
    private String targetUsername;

    /** GRANT or REVOKE */
    private String action;

    /** ROLE or PERMISSION */
    private String resourceType;

    /** Name of the role or permission that was granted/revoked */
    private String resourceName;

    private Instant timestamp;

    public static GrantAuditLogResponse from(GrantAuditLog log) {
        return GrantAuditLogResponse.builder()
                .id(log.getId())
                .grantorUsername(log.getGrantorUsername())
                .targetUsername(log.getTargetUsername())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceName(log.getResourceName())
                .timestamp(log.getTimestamp())
                .build();
    }
}
