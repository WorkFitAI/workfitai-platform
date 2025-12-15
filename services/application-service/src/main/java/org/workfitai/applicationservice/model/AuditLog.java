package org.workfitai.applicationservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log entity for tracking all changes to applications
 * Retention: 7 years for compliance
 * Immutable: No updates or deletes allowed
 */
@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "entity_performed_at", def = "{'entityId': 1, 'performedAt': -1}"),
    @CompoundIndex(name = "performer_performed_at", def = "{'performedBy': 1, 'performedAt': -1}"),
    @CompoundIndex(name = "action_performed_at", def = "{'action': 1, 'performedAt': -1}")
})
public class AuditLog {

    @Id
    private String id;

    /**
     * Entity type being audited (e.g., "APPLICATION", "NOTE")
     */
    @Indexed
    private String entityType;

    /**
     * ID of the entity being audited
     */
    @Indexed
    private String entityId;

    /**
     * Action performed (CREATED, UPDATED, DELETED, RESTORED, etc.)
     */
    @Indexed
    private String action;

    /**
     * Username of person who performed the action
     */
    @Indexed
    private String performedBy;

    /**
     * Timestamp when action was performed
     */
    @Indexed
    private Instant performedAt;

    /**
     * Previous state of the entity (before change)
     * Null for CREATE actions
     */
    private Map<String, Object> beforeState;

    /**
     * New state of the entity (after change)
     * Null for DELETE actions
     */
    private Map<String, Object> afterState;

    /**
     * Additional metadata about the action
     * e.g., reason, IP address, user agent, etc.
     */
    private Map<String, Object> metadata;

    /**
     * ISO 3166-1 alpha-2 country code for compliance
     */
    private String countryCode;

    /**
     * GDPR compliance flag
     */
    private Boolean containsPII = false;
}
