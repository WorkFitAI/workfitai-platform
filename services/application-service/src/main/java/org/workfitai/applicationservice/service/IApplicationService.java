package org.workfitai.applicationservice.service;

import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

/**
 * Service interface for Application business operations.
 * 
 * Defines the contract for application management:
 * - Creating new job applications
 * - Retrieving application data (by user, job, or ID)
 * - Updating application status (HR workflow)
 * - Withdrawing applications (candidate action)
 * 
 * Implementation handles:
 * - Cross-service validation (job-service, cv-service)
 * - Business rule enforcement
 * - Data enrichment (job title, company name, etc.)
 */
public interface IApplicationService {

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new job application.
     * 
     * Business Rules:
     * 1. User can only apply once per job (no duplicates)
     * 2. Job must be in PUBLISHED status
     * 3. CV must belong to the applying user
     * 4. Initial status is APPLIED
     * 
     * Cross-Service Calls:
     * - job-service: Validate job exists and is PUBLISHED
     * - cv-service: Validate CV exists and belongs to user
     * 
     * @param request Application data (jobId, cvId, note)
     * @param userId  User ID from JWT token
     * @return Created application with enriched data
     * @throws org.workfitai.applicationservice.exception.ApplicationConflictException if
     *                                                                                 already
     *                                                                                 applied
     * @throws org.workfitai.applicationservice.exception.ForbiddenException           if
     *                                                                                 job
     *                                                                                 not
     *                                                                                 published
     *                                                                                 or
     *                                                                                 CV
     *                                                                                 not
     *                                                                                 owned
     * @throws org.workfitai.applicationservice.exception.NotFoundException            if
     *                                                                                 job
     *                                                                                 or
     *                                                                                 CV
     *                                                                                 not
     *                                                                                 found
     */
    ApplicationResponse createApplication(CreateApplicationRequest request, String userId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Retrieves an application by ID.
     * 
     * Authorization:
     * - Candidate: Can only view own applications
     * - HR: Can view applications for jobs they own
     * - Admin: Can view all applications
     * 
     * @param id Application ID
     * @return Application details
     * @throws org.workfitai.applicationservice.exception.NotFoundException if not
     *                                                                      found
     */
    ApplicationResponse getApplicationById(String id);

    /**
     * Retrieves all applications by the current user (paginated).
     * 
     * Use case: Candidate viewing their application history
     * Default sort: by createdAt descending (newest first)
     * 
     * @param userId   User ID from JWT
     * @param pageable Pagination parameters
     * @return Paginated list of applications
     */
    ResultPaginationDTO<ApplicationResponse> getMyApplications(String userId, Pageable pageable);

    /**
     * Retrieves all applications by user with status filter.
     * 
     * Use case: Candidate filtering by status (e.g., "show only interviews")
     * 
     * @param userId   User ID from JWT
     * @param status   Status filter (optional)
     * @param pageable Pagination parameters
     * @return Paginated list of filtered applications
     */
    ResultPaginationDTO<ApplicationResponse> getMyApplicationsByStatus(
            String userId,
            ApplicationStatus status,
            Pageable pageable);

    /**
     * Retrieves all applications for a specific job (paginated).
     * 
     * Use case: HR viewing applicants for a job posting
     * Authorization: Only job owner or ADMIN
     * 
     * @param jobId    Job ID
     * @param pageable Pagination parameters
     * @return Paginated list of applicants
     */
    ResultPaginationDTO<ApplicationResponse> getApplicationsByJob(String jobId, Pageable pageable);

    /**
     * Retrieves all applications for a job with status filter.
     * 
     * Use case: HR filtering applicants (e.g., "show candidates in INTERVIEW")
     * 
     * @param jobId    Job ID
     * @param status   Status filter
     * @param pageable Pagination parameters
     * @return Paginated list of filtered applicants
     */
    ResultPaginationDTO<ApplicationResponse> getApplicationsByJobAndStatus(
            String jobId,
            ApplicationStatus status,
            Pageable pageable);

    /**
     * Checks if user has already applied to a job.
     * 
     * Use case: Frontend checks before showing "Apply" button
     * 
     * @param userId User ID
     * @param jobId  Job ID
     * @return true if already applied
     */
    boolean hasUserAppliedToJob(String userId, String jobId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates application status (HR action).
     * 
     * Status Flow:
     * APPLIED → REVIEWING → INTERVIEW → OFFER → HIRED
     * ↘ → REJECTED
     * ↘ → REJECTED
     * 
     * Authorization: Only job owner or ADMIN
     * 
     * @param id        Application ID
     * @param newStatus New status
     * @return Updated application
     * @throws org.workfitai.applicationservice.exception.NotFoundException  if not
     *                                                                       found
     * @throws org.workfitai.applicationservice.exception.ForbiddenException if not
     *                                                                       authorized
     * @throws IllegalArgumentException                                      if
     *                                                                       invalid
     *                                                                       status
     *                                                                       transition
     */
    ApplicationResponse updateStatus(String id, ApplicationStatus newStatus);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Withdraws an application (soft delete).
     * 
     * Only the applicant can withdraw their own application.
     * Sets status to a withdrawn state or marks as deleted.
     * 
     * @param id     Application ID
     * @param userId User ID from JWT (for ownership verification)
     * @throws org.workfitai.applicationservice.exception.NotFoundException  if not
     *                                                                       found
     * @throws org.workfitai.applicationservice.exception.ForbiddenException if not
     *                                                                       the
     *                                                                       applicant
     */
    void withdrawApplication(String id, String userId);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 STATISTICS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets application count for a user.
     * 
     * @param userId User ID
     * @return Total applications count
     */
    long countByUser(String userId);

    /**
     * Gets application count for a job.
     * 
     * @param jobId Job ID
     * @return Total applicants count
     */
    long countByJob(String jobId);
}
