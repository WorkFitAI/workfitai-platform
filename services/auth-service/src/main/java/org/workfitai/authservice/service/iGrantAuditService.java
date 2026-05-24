package org.workfitai.authservice.service;

import org.workfitai.authservice.model.GrantAuditLog;

import java.util.List;

public interface iGrantAuditService {

    /** Log a GRANT action for a role or permission */
    void logGrant(String grantorUsername, String targetUsername, String resourceType, String resourceName);

    /** Log a REVOKE action for a role or permission */
    void logRevoke(String grantorUsername, String targetUsername, String resourceType, String resourceName);

    /** Return all audit records, newest first (ADMIN use) */
    List<GrantAuditLog> findAll();

    /** Return all audit records where the given user's roles were changed, newest first */
    List<GrantAuditLog> findByTarget(String targetUsername);

    /** Return all audit records performed by the given actor, newest first */
    List<GrantAuditLog> findByGrantor(String grantorUsername);
}
