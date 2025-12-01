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
     */
    boolean existsByUsernameAndJobId(String username, String jobId);

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

    /** Finds all applications using a specific CV. */
    List<Application> findByCvId(String cvId);

    /** Finds recent applications for a user (sorted by createdAt desc). */
    @Query(value = "{ 'username': ?0 }", sort = "{ 'createdAt': -1 }")
    List<Application> findRecentByUsername(String username, int limit);
}
