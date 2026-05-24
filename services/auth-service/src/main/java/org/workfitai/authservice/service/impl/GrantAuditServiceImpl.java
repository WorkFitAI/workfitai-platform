package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.model.GrantAuditLog;
import org.workfitai.authservice.repository.GrantAuditLogRepository;
import org.workfitai.authservice.service.iGrantAuditService;

import java.util.List;

/**
 * Persists audit records for every role/permission grant or revoke.
 * Intentionally NOT @Transactional — audit failure must never roll back the grant/revoke itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantAuditServiceImpl implements iGrantAuditService {

    private final GrantAuditLogRepository auditRepo;

    private static final String ACTION_GRANT = "GRANT";
    private static final String ACTION_REVOKE = "REVOKE";

    @Override
    public void logGrant(String grantorUsername, String targetUsername,
                         String resourceType, String resourceName) {
        save(grantorUsername, targetUsername, ACTION_GRANT, resourceType, resourceName);
    }

    @Override
    public void logRevoke(String grantorUsername, String targetUsername,
                          String resourceType, String resourceName) {
        save(grantorUsername, targetUsername, ACTION_REVOKE, resourceType, resourceName);
    }

    // ─── read ──────────────────────────────────────────────────────────────

    @Override
    public List<GrantAuditLog> findAll() {
        return auditRepo.findAllByOrderByTimestampDesc();
    }

    @Override
    public List<GrantAuditLog> findByTarget(String targetUsername) {
        return auditRepo.findByTargetUsernameOrderByTimestampDesc(targetUsername);
    }

    @Override
    public List<GrantAuditLog> findByGrantor(String grantorUsername) {
        return auditRepo.findByGrantorUsernameOrderByTimestampDesc(grantorUsername);
    }

    // ─── write ─────────────────────────────────────────────────────────────

    private void save(String grantor, String target, String action,
                      String resourceType, String resourceName) {
        try {
            auditRepo.save(GrantAuditLog.builder()
                    .grantorUsername(grantor)
                    .targetUsername(target)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceName(resourceName)
                    .build());
        } catch (Exception ex) {
            // Audit failure is non-fatal — log and continue
            log.error("[AUDIT] Failed to persist audit log [{} {} {} -> {}]: {}",
                    action, resourceType, resourceName, target, ex.getMessage());
        }
    }
}
