package org.workfitai.applicationservice.service;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;

/**
 * Service interface for Draft Application operations.
 * Handles draft creation, updates, and submission workflow.
 *
 * Draft applications allow candidates to save their application
 * progress without triggering the full Saga orchestration.
 */
public interface IDraftApplicationService {

    /**
     * Creates a new draft application.
     *
     * @param jobId Job ID to apply for
     * @param email Candidate's email
     * @param coverLetter Optional cover letter
     * @param cvFile Optional CV file (can be uploaded later)
     * @param username Candidate username (from JWT)
     * @return Created draft application
     * @throws org.workfitai.applicationservice.exception.BadRequestException if user already has a draft for this job
     */
    ApplicationResponse createDraft(
        String jobId,
        String email,
        String coverLetter,
        MultipartFile cvFile,
        String username
    );

    /**
     * Updates an existing draft application.
     *
     * @param id Application ID
     * @param email Updated email (optional)
     * @param coverLetter Updated cover letter (optional)
     * @param cvFile Updated CV file (optional)
     * @param username Candidate username (for ownership verification)
     * @return Updated draft application
     * @throws org.workfitai.applicationservice.exception.NotFoundException if application not found
     * @throws org.workfitai.applicationservice.exception.ForbiddenException if not owner or not a draft
     */
    ApplicationResponse updateDraft(
        String id,
        String email,
        String coverLetter,
        MultipartFile cvFile,
        String username
    );

    /**
     * Submits a draft application, converting it to an active application.
     * Triggers the full Saga orchestration workflow.
     *
     * @param id Application ID
     * @param cvFile CV file (required if not already uploaded)
     * @param username Candidate username (for ownership verification)
     * @return Submitted application
     * @throws org.workfitai.applicationservice.exception.NotFoundException if application not found
     * @throws org.workfitai.applicationservice.exception.ForbiddenException if not owner
     * @throws org.workfitai.applicationservice.exception.BadRequestException if CV not provided and not already uploaded
     */
    ApplicationResponse submitDraft(
        String id,
        MultipartFile cvFile,
        String username
    );

    /**
     * Retrieves all draft applications for a user.
     *
     * @param username Candidate username
     * @param pageable Pagination parameters
     * @return Paginated list of draft applications
     */
    ResultPaginationDTO<ApplicationResponse> getMyDrafts(String username, Pageable pageable);
}
