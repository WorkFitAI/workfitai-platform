package org.workfitai.applicationservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.applicationservice.model.AuditLog;

import java.time.Instant;
import java.util.List;

/**
 * Repository for AuditLog entities
 * Supports querying by entity ID, performer, action, and date range
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    /**
     * Find all audit logs for a specific entity
     */
    Page<AuditLog> findByEntityIdOrderByPerformedAtDesc(String entityId, Pageable pageable);

    /**
     * Find all audit logs by a specific user
     */
    Page<AuditLog> findByPerformedByOrderByPerformedAtDesc(String performedBy, Pageable pageable);

    /**
     * Find all audit logs for a specific action
     */
    Page<AuditLog> findByActionOrderByPerformedAtDesc(String action, Pageable pageable);

    /**
     * Find audit logs within a date range
     */
    Page<AuditLog> findByPerformedAtBetweenOrderByPerformedAtDesc(
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    );

    /**
     * Complex query: Filter by entity ID and date range
     */
    Page<AuditLog> findByEntityIdAndPerformedAtBetweenOrderByPerformedAtDesc(
        String entityId,
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    );

    /**
     * Complex query: Filter by performer and date range
     */
    Page<AuditLog> findByPerformedByAndPerformedAtBetweenOrderByPerformedAtDesc(
        String performedBy,
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    );

    /**
     * Complex query: Filter by action and date range
     */
    Page<AuditLog> findByActionAndPerformedAtBetweenOrderByPerformedAtDesc(
        String action,
        Instant fromDate,
        Instant toDate,
        Pageable pageable
    );

    /**
     * Count audit logs by entity ID
     */
    long countByEntityId(String entityId);

    /**
     * Count audit logs by performer
     */
    long countByPerformedBy(String performedBy);

    /**
     * Find audit logs older than a specific date (for cleanup/archival)
     */
    List<AuditLog> findByPerformedAtBefore(Instant date);

    /**
     * Delete audit logs older than a specific date (cleanup after 7 years)
     */
    void deleteByPerformedAtBefore(Instant date);
}
