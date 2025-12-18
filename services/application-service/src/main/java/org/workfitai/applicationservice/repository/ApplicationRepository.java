package org.workfitai.applicationservice.repository;

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

    /**
     * Checks if a user has already applied to a specific job.
     * Uses compound index on (username, jobId) for O(1) lookup.
     * NOTE: This checks ALL applications including soft-deleted ones.
     * Use existsByUsernameAndJobIdAndDeletedAtIsNull() to check only active applications.
     */
    boolean existsByUsernameAndJobId(String username, String jobId);

    /**
     * Checks if a user has an active (non-deleted) application for a specific job.
     * This should be used for duplicate validation to allow reapplication after withdrawal.
     * Uses compound index on (username, jobId, deletedAt) for O(1) lookup.
     */
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

    /** Counts applications for a job. */
    long countByJobId(String jobId);

    /** Finds applications by status across all users/jobs. */
    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

    /** Counts applications by status. */
    long countByStatus(ApplicationStatus status);

    /** Finds specific application by user and job. */
    Optional<Application> findByUsernameAndJobId(String username, String jobId);

    /** Finds recent applications for a user (sorted by createdAt desc). */
    @Query(value = "{ 'username': ?0 }", sort = "{ 'createdAt': -1 }")
    List<Application> findRecentByUsername(String username, int limit);

    // ==================== Draft Application Queries ====================

    /**
     * Finds all draft applications for a user (not yet submitted).
     * Excludes soft-deleted drafts.
     */
    Page<Application> findByUsernameAndIsDraftAndDeletedAtIsNull(String username, boolean isDraft, Pageable pageable);

    /**
     * Checks if user has a draft for a specific job.
     * Useful to prevent multiple drafts for same job.
     */
    boolean existsByUsernameAndJobIdAndIsDraftAndDeletedAtIsNull(String username, String jobId, boolean isDraft);

    // ==================== Soft Delete Queries ====================

    /**
     * Finds application by ID, excluding soft-deleted ones.
     * Use this for most read operations to hide deleted applications.
     */
    Optional<Application> findByIdAndDeletedAtIsNull(String id);

    /**
     * Finds all active (non-deleted) applications by username.
     */
    Page<Application> findByUsernameAndDeletedAtIsNull(String username, Pageable pageable);

    /**
     * Finds active applications by username and status.
     */
    Page<Application> findByUsernameAndStatusAndDeletedAtIsNull(String username, ApplicationStatus status,
            Pageable pageable);

    /**
     * Finds active applications for a job.
     */
    Page<Application> findByJobIdAndDeletedAtIsNull(String jobId, Pageable pageable);

    /**
     * Finds active applications for a job with specific status.
     */
    Page<Application> findByJobIdAndStatusAndDeletedAtIsNull(String jobId, ApplicationStatus status, Pageable pageable);

    /**
     * Finds specific application by user and job (excluding deleted).
     */
    Optional<Application> findByUsernameAndJobIdAndDeletedAtIsNull(String username, String jobId);

    /**
     * Counts active applications by user.
     */
    long countByUsernameAndDeletedAtIsNull(String username);

    /**
     * Counts active applications for a job.
     */
    long countByJobIdAndDeletedAtIsNull(String jobId);

    // ==================== Phase 3: Company \u0026 Assignment Queries
    // ====================

    /**
     * Finds all active applications for a company.
     * Supports pagination and sorting.
     */
    Page<Application> findByCompanyIdAndDeletedAtIsNull(String companyId, Pageable pageable);

    /**
     * Finds active applications for a company with specific status.
     */
    Page<Application> findByCompanyIdAndStatusAndDeletedAtIsNull(String companyId, ApplicationStatus status,
            Pageable pageable);

    /**
     * Finds active applications for a company assigned to specific HR.
     */
    Page<Application> findByCompanyIdAndAssignedToAndDeletedAtIsNull(String companyId, String assignedTo,
            Pageable pageable);

    /**
     * Finds active (non-draft, non-deleted) applications assigned to specific HR user.
     * Used for HR personal workload view.
     */
    Page<Application> findByAssignedToAndIsDraftFalseAndDeletedAtIsNull(String assignedTo, Pageable pageable);

    /**
     * Finds active (non-draft, non-deleted) applications assigned to HR with specific status.
     */
    Page<Application> findByAssignedToAndStatusAndIsDraftFalseAndDeletedAtIsNull(String assignedTo, ApplicationStatus status,
            Pageable pageable);

    /**
     * Counts active applications for a company.
     */
    long countByCompanyIdAndDeletedAtIsNull(String companyId);

    /**
     * Counts active applications assigned to HR user.
     */
    long countByAssignedToAndDeletedAtIsNull(String assignedTo);

    /**
     * Counts active applications for company by status.
     */
    long countByCompanyIdAndStatusAndDeletedAtIsNull(String companyId, ApplicationStatus status);

    /**
     * Finds all applications for company (for export).
     * No pagination - use carefully with limits.
     */
    List<Application> findByCompanyIdAndDeletedAtIsNull(String companyId);

    /**
     * Finds all soft-deleted applications (for admin recovery).
     */
    Page<Application> findByDeletedAtIsNotNull(Pageable pageable);

    /**
     * Finds all non-deleted applications (for export/reporting).
     * Use with caution - can return large datasets.
     */
    List<Application> findByDeletedAtIsNull();
}
