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
import org.workfitai.applicationservice.model.Application;

/**
 * MongoDB repository for Application documents.
 * 
 * Extends MongoRepository to provide:
 * - Basic CRUD operations (save, findById, findAll, delete, etc.)
 * - Pagination support via Pageable
 * - Automatic query generation from method names
 * 
 * Index Strategy (defined in Application entity):
 * - Compound index on (userId, jobId) for uniqueness check
 * - Single index on userId for user's applications lookup
 * - Single index on jobId for job's applicants lookup
 * 
 * Query Patterns:
 * - existsByUserIdAndJobId: Duplicate check (O(1) with compound index)
 * - findByUserId: User's application history (with pagination)
 * - findByJobId: Job's applicant list (HR use case)
 */
@Repository
public interface ApplicationRepository extends MongoRepository<Application, String> {

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 DUPLICATE CHECK
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks if a user has already applied to a specific job.
     * 
     * Uses compound index on (userId, jobId) for O(1) lookup.
     * Called before creating new application to prevent duplicates.
     * 
     * @param userId User's ID (from JWT sub claim)
     * @param jobId  Job's ID
     * @return true if application already exists
     */
    boolean existsByUserIdAndJobId(String userId, String jobId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 USER'S APPLICATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds all applications by a specific user (paginated).
     * 
     * Use case: Candidate viewing their application history
     * Default sort: by createdAt descending (newest first)
     * 
     * @param userId   User's ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of applications
     */
    Page<Application> findByUserId(String userId, Pageable pageable);

    /**
     * Finds all applications by user with specific status.
     * 
     * Use case: Candidate filtering by status (e.g., "show only INTERVIEW")
     * 
     * @param userId   User's ID
     * @param status   Application status filter
     * @param pageable Pagination parameters
     * @return Page of filtered applications
     */
    Page<Application> findByUserIdAndStatus(String userId, ApplicationStatus status, Pageable pageable);

    /**
     * Counts applications by user.
     * 
     * Use case: Display total application count on user dashboard
     * 
     * @param userId User's ID
     * @return Total number of applications
     */
    long countByUserId(String userId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 JOB'S APPLICANTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds all applications for a specific job (paginated).
     * 
     * Use case: HR viewing applicants for a job posting
     * Requires authorization check: Only job owner or ADMIN can access
     * 
     * @param jobId    Job's ID
     * @param pageable Pagination parameters
     * @return Page of applications
     */
    Page<Application> findByJobId(String jobId, Pageable pageable);

    /**
     * Finds applications for a job with specific status.
     * 
     * Use case: HR filtering applicants (e.g., "show only INTERVIEW candidates")
     * 
     * @param jobId    Job's ID
     * @param status   Status filter
     * @param pageable Pagination parameters
     * @return Page of filtered applications
     */
    Page<Application> findByJobIdAndStatus(String jobId, ApplicationStatus status, Pageable pageable);

    /**
     * Counts applications for a job.
     * 
     * Use case: Display "X applicants" on job listing
     * 
     * @param jobId Job's ID
     * @return Total number of applicants
     */
    long countByJobId(String jobId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 STATUS QUERIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds applications by status across all users/jobs.
     * 
     * Use case: Admin dashboard showing all pending reviews
     * 
     * @param status   Status filter
     * @param pageable Pagination parameters
     * @return Page of applications
     */
    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

    /**
     * Counts applications by status.
     * 
     * Use case: Dashboard metrics (e.g., "50 pending, 20 in interview")
     * 
     * @param status Status to count
     * @return Count
     */
    long countByStatus(ApplicationStatus status);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 LOOKUP QUERIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds specific application by user and job.
     * 
     * Use case: Check application status for a specific job
     * 
     * @param userId User's ID
     * @param jobId  Job's ID
     * @return Application if exists
     */
    Optional<Application> findByUserIdAndJobId(String userId, String jobId);

    /**
     * Finds all applications using a specific CV.
     * 
     * Use case: CV management - show which jobs used this CV
     * 
     * @param cvId CV's ID
     * @return List of applications
     */
    List<Application> findByCvId(String cvId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 AGGREGATE QUERIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Custom query to find recent applications for a user.
     * 
     * MongoDB @Query annotation for complex queries.
     * Returns last N applications sorted by createdAt desc.
     * 
     * @param userId User's ID
     * @param limit  Maximum number of results
     * @return List of recent applications
     */
    @Query(value = "{ 'userId': ?0 }", sort = "{ 'createdAt': -1 }")
    List<Application> findRecentByUserId(String userId, int limit);
}
