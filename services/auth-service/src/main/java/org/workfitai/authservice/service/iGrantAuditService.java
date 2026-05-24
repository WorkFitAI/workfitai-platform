package org.workfitai.authservice.service;

/**
 * Contract for publishing role/permission grant and revoke audit events.
 *
 * Events are published to the Kafka "audit-events" topic and consumed by
 * monitoring-service which indexes them in Elasticsearch (workfitai-audit-*).
 * The old MongoDB grant_audit_logs collection is no longer used.
 */
public interface iGrantAuditService {

    /** Publish a ROLE_GRANTED audit event for a role or permission grant. */
    void logGrant(String grantorUsername, String targetUsername, String resourceType, String resourceName);

    /** Publish a ROLE_REVOKED audit event for a role or permission revoke. */
    void logRevoke(String grantorUsername, String targetUsername, String resourceType, String resourceName);
}
