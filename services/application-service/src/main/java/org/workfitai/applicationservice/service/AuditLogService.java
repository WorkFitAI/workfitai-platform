package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.model.AuditLog;
import org.workfitai.applicationservice.repository.AuditLogRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Service for managing audit logs
 * Provides async logging to avoid blocking main operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an action asynchronously
     * Non-blocking operation to avoid impacting main flow
     */
    @Async
    public void logAction(
        String entityType,
        String entityId,
        String action,
        String performedBy,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Map<String, Object> metadata
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .performedAt(Instant.now())
                .beforeState(beforeState)
                .afterState(afterState)
                .metadata(metadata)
                .containsPII(true) // Applications contain PII
                .build();

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: {} - {} by {}",
                entityType, action, performedBy);
        } catch (Exception e) {
            // Never fail main operation due to audit logging issues
            log.error("Failed to create audit log: {} - {} by {}",
                entityType, action, performedBy, e);
        }
    }

    /**
     * Query audit logs by entity ID
     */
    public Page<AuditLog> getByEntityId(String entityId, Pageable pageable) {
        return auditLogRepository.findByEntityIdOrderByPerformedAtDesc(entityId, pageable);
    }

    /**
     * Query audit logs by performer
     */
    public Page<AuditLog> getByPerformer(String performedBy, Pageable pageable) {
        return auditLogRepository.findByPerformedByOrderByPerformedAtDesc(performedBy, pageable);
    }

    /**
     * Query audit logs by action
     */
    public Page<AuditLog> getByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByPerformedAtDesc(action, pageable);
    }

    /**
     * Query audit logs within date range
     */
    public Page<AuditLog> getByDateRange(
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    ) {
        return auditLogRepository.findByPerformedAtBetweenOrderByPerformedAtDesc(
            fromDate, toDate, pageable
        );
    }

    /**
     * Complex query with multiple filters
     */
    public Page<AuditLog> queryAuditLogs(
        String entityId,
        String performedBy,
        String action,
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    ) {
        // Apply filters based on what's provided
        if (entityId != null && fromDate != null && toDate != null) {
            return auditLogRepository.findByEntityIdAndPerformedAtBetweenOrderByPerformedAtDesc(
                entityId, fromDate, toDate, pageable
            );
        } else if (performedBy != null && fromDate != null && toDate != null) {
            return auditLogRepository.findByPerformedByAndPerformedAtBetweenOrderByPerformedAtDesc(
                performedBy, fromDate, toDate, pageable
            );
        } else if (action != null && fromDate != null && toDate != null) {
            return auditLogRepository.findByActionAndPerformedAtBetweenOrderByPerformedAtDesc(
                action, fromDate, toDate, pageable
            );
        } else if (entityId != null) {
            return getByEntityId(entityId, pageable);
        } else if (performedBy != null) {
            return getByPerformer(performedBy, pageable);
        } else if (action != null) {
            return getByAction(action, pageable);
        } else if (fromDate != null && toDate != null) {
            return getByDateRange(fromDate, toDate, pageable);
        }

        // No filters - return all (paginated)
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Count audit logs for an entity
     */
    public long countByEntityId(String entityId) {
        return auditLogRepository.countByEntityId(entityId);
    }

    /**
     * Count audit logs by performer
     */
    public long countByPerformer(String performedBy) {
        return auditLogRepository.countByPerformedBy(performedBy);
    }

    /**
     * Cleanup audit logs older than 7 years (compliance retention)
     * Should be run as scheduled job
     */
    public void cleanupOldAuditLogs() {
        Instant sevenYearsAgo = Instant.now().minus(7 * 365, ChronoUnit.DAYS);

        log.info("Cleaning up audit logs older than {}", sevenYearsAgo);
        auditLogRepository.deleteByPerformedAtBefore(sevenYearsAgo);
    }

    /**
     * Archive audit logs older than 1 year
     * Implementation would move logs to cold storage (e.g., S3)
     * Placeholder for future implementation
     */
    public void archiveOldAuditLogs() {
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);

        log.info("Archiving audit logs older than {}", oneYearAgo);

        // TODO: Implement archival to cold storage
        // var oldLogs = auditLogRepository.findByPerformedAtBefore(oneYearAgo);
        // archiveToS3(oldLogs);
        // auditLogRepository.deleteAll(oldLogs);
    }
}
