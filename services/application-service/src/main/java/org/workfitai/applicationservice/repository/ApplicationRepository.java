package org.workfitai.applicationservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

/**
 * MongoDB repository for Application documents.
 * Uses username (from JWT sub claim) instead of userId.
 */
@Repository
public interface ApplicationRepository extends MongoRepository<Application, String> {

        /** Checks if a user has an active (non-deleted) application for a specific job. */
        boolean existsByUsernameAndJobIdAndDeletedAtIsNull(String username, String jobId);

        /** Finds all applications by a specific user (paginated). */
        Page<Application> findByUsername(String username, Pageable pageable);

        /** Finds all applications by user with specific status. */
        Page<Application> findByUsernameAndStatus(String username, ApplicationStatus status, Pageable pageable);

        /** Counts applications by user. */
        long countByUsername(String username);

        /** Finds all applications for a specific job (paginated). */
        Page<Application> findByJobId(String jobId, Pageable pageable);

        /** Finds applications for a job with specific status. */
        Page<Application> findByJobIdAndStatus(String jobId, ApplicationStatus status, Pageable pageable);

        /** Counts applications for a job (including soft-deleted — used for stats events). */
        long countByJobId(String jobId);

        /** Finds applications by status across all users/jobs. */
        Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

        /** Counts applications by status. */
        long countByStatus(ApplicationStatus status);

        /** Finds specific application by user and job. */
        Optional<Application> findByUsernameAndJobId(String username, String jobId);

        /** Finds recent active applications for a user (sorted by createdAt desc). */
        @Query(value = "{ 'username': ?0, 'deletedAt': null }", sort = "{ 'createdAt': -1 }")
        List<Application> findRecentByUsername(String username);

        // ==================== Soft Delete Queries ====================

        /** Finds application by ID, excluding soft-deleted ones. */
        Optional<Application> findByIdAndDeletedAtIsNull(String id);

        /** Finds all active (non-deleted) applications by username. */
        Page<Application> findByUsernameAndDeletedAtIsNull(String username, Pageable pageable);

        /** Finds active applications by username and status. */
        Page<Application> findByUsernameAndStatusAndDeletedAtIsNull(String username, ApplicationStatus status,
                        Pageable pageable);

        /** Finds active applications for a job. */
        Page<Application> findByJobIdAndDeletedAtIsNull(String jobId, Pageable pageable);

        /** Finds active applications for a job with specific status. */
        Page<Application> findByJobIdAndStatusAndDeletedAtIsNull(String jobId, ApplicationStatus status,
                        Pageable pageable);

        /** Finds specific application by user and job (excluding deleted). */
        Optional<Application> findByUsernameAndJobIdAndDeletedAtIsNull(String username, String jobId);

        /** Counts active applications by user. */
        long countByUsernameAndDeletedAtIsNull(String username);

        /** Counts active applications for a job. */
        long countByJobIdAndDeletedAtIsNull(String jobId);

        /** Checks if a non-deleted application with the given ID belongs to the given username. */
        boolean existsByIdAndUsernameAndDeletedAtIsNull(String id, String username);

        // ==================== Phase 3: Company & Assignment Queries ====================

        /** Finds all active applications for a company. */
        Page<Application> findByCompanyIdAndDeletedAtIsNull(String companyId, Pageable pageable);

        /** Finds active applications for a company with specific status. */
        Page<Application> findByCompanyIdAndStatusAndDeletedAtIsNull(String companyId, ApplicationStatus status,
                        Pageable pageable);

        /** Finds active applications for a company assigned to specific HR. */
        Page<Application> findByCompanyIdAndAssignedToAndDeletedAtIsNull(String companyId, String assignedTo,
                        Pageable pageable);

        /** Finds active applications assigned to specific HR user. */
        Page<Application> findByAssignedToAndDeletedAtIsNull(String assignedTo, Pageable pageable);

        /** Finds active applications assigned to HR with specific status. */
        Page<Application> findByAssignedToAndStatusAndDeletedAtIsNull(String assignedTo,
                        ApplicationStatus status,
                        Pageable pageable);

        /** Counts active applications for a company. */
        long countByCompanyIdAndDeletedAtIsNull(String companyId);

        /** Counts active applications assigned to HR user. */
        long countByAssignedToAndDeletedAtIsNull(String assignedTo);

        /** Counts active applications by status (platform-wide). */
        long countByStatusAndDeletedAtIsNull(ApplicationStatus status);

        /** Counts active applications for company by status. */
        long countByCompanyIdAndStatusAndDeletedAtIsNull(String companyId, ApplicationStatus status);

        /** Finds all applications for company (for export — no pagination). Use with row limit. */
        List<Application> findByCompanyIdAndDeletedAtIsNull(String companyId);

        /** Counts applications stuck in APPLIED/REVIEWING that haven't been updated since cutoff. */
        long countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                String companyId,
                List<ApplicationStatus> statuses,
                Instant cutoff
        );

        /**
         * Finds all applications for a company (including soft-deleted).
         * @deprecated Unbounded — OOM risk on large datasets. Use MongoTemplate with Criteria instead.
         */
        @Deprecated
        List<Application> findByCompanyId(String companyId);

        /** Finds all soft-deleted applications (for admin recovery). */
        Page<Application> findByDeletedAtIsNotNull(Pageable pageable);

        /**
         * Finds all non-deleted applications (for export/reporting).
         * @deprecated Unbounded — OOM risk on large datasets. Use MongoTemplate with Criteria and limit instead.
         */
        @Deprecated
        List<Application> findByDeletedAtIsNull();

        /** Finds all active applications in the given statuses — used for cv-refer initial sync. */
        List<Application> findByStatusInAndDeletedAtIsNull(List<ApplicationStatus> statuses);
}
