package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Audit record for every role/permission grant or revoke action.
 * Stored in MongoDB; never deleted — compliance trail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "grant_audit_logs")
public class GrantAuditLog {

    @Id
    private String id;

    /** Username of the user who performed the action */
    private String grantorUsername;

    /** Username of the user whose roles/permissions were changed */
    private String targetUsername;

    /** GRANT or REVOKE */
    private String action;

    /** ROLE or PERMISSION */
    private String resourceType;

    /** The name of the role or permission that was granted/revoked */
    private String resourceName;

    @CreatedDate
    private Instant timestamp;
}
