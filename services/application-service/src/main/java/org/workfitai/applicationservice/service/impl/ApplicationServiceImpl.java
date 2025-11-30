package org.workfitai.applicationservice.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.JobServiceClient;
import org.workfitai.applicationservice.client.dto.CvDTO;
import org.workfitai.applicationservice.client.dto.JobDTO;
import org.workfitai.applicationservice.constants.ValidationMessages;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.exception.ServiceUnavailableException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.IApplicationService;

/**
 * Implementation of IApplicationService.
 * 
 * Handles all application business logic including:
 * - Cross-service validation via Feign clients
 * - Business rule enforcement
 * - Data enrichment with job/CV details
 * 
 * Transaction Strategy:
 * - Read operations: No transaction (MongoDB handles it)
 * - Write operations: @Transactional for consistency
 * 
 * Error Handling:
 * - Feign exceptions → NotFoundException or ServiceUnavailableException
 * - Business rule violations → ApplicationConflictException or
 * ForbiddenException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements IApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final JobServiceClient jobServiceClient;
    private final CvServiceClient cvServiceClient;

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new job application with full validation.
     * 
     * Validation Flow:
     * 1. Check for duplicate application
     * 2. Validate job exists and is PUBLISHED
     * 3. Validate CV exists and belongs to user
     * 4. Create application with APPLIED status
     * 5. Enrich response with job/CV details
     */
    @Override
    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, String userId) {
        log.info("Creating application for user {} to job {}", userId, request.getJobId());

        // Step 1: Check for duplicate application
        // Business rule: One application per user per job
        if (applicationRepository.existsByUserIdAndJobId(userId, request.getJobId())) {
            log.warn("Duplicate application attempt: user {} already applied to job {}",
                    userId, request.getJobId());
            throw new ApplicationConflictException(ValidationMessages.APPLICATION_ALREADY_EXISTS);
        }

        // Step 2: Validate job exists and is PUBLISHED
        JobDTO job = fetchAndValidateJob(request.getJobId());

        // Step 3: Validate CV exists and belongs to user
        CvDTO cv = fetchAndValidateCv(request.getCvId(), userId);

        // Step 4: Create application entity
        Application application = applicationMapper.toEntity(request);
        application.setUserId(userId); // Set from JWT, not request body (security)
        application.setStatus(ApplicationStatus.APPLIED); // Initial status

        // Step 5: Save to database
        Application savedApplication = applicationRepository.save(application);
        log.info("Application created successfully: id={}", savedApplication.getId());

        // Step 6: Build enriched response
        return buildEnrichedResponse(savedApplication, job, cv);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public ApplicationResponse getApplicationById(String id) {
        log.debug("Fetching application by id: {}", id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ValidationMessages.APPLICATION_NOT_FOUND));

        // Fetch enrichment data (best effort - don't fail if external service is down)
        return enrichApplicationResponse(application);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplications(String userId, Pageable pageable) {
        log.debug("Fetching applications for user: {}", userId);

        Page<Application> page = applicationRepository.findByUserId(userId, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplicationsByStatus(
            String userId,
            ApplicationStatus status,
            Pageable pageable) {

        log.debug("Fetching applications for user {} with status {}", userId, status);

        Page<Application> page = applicationRepository.findByUserIdAndStatus(userId, status, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getApplicationsByJob(String jobId, Pageable pageable) {
        log.debug("Fetching applications for job: {}", jobId);

        Page<Application> page = applicationRepository.findByJobId(jobId, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getApplicationsByJobAndStatus(
            String jobId,
            ApplicationStatus status,
            Pageable pageable) {

        log.debug("Fetching applications for job {} with status {}", jobId, status);

        Page<Application> page = applicationRepository.findByJobIdAndStatus(jobId, status, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public boolean hasUserAppliedToJob(String userId, String jobId) {
        return applicationRepository.existsByUserIdAndJobId(userId, jobId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ApplicationResponse updateStatus(String id, ApplicationStatus newStatus) {
        log.info("Updating application {} status to {}", id, newStatus);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ValidationMessages.APPLICATION_NOT_FOUND));

        // Validate status transition (optional: add state machine logic)
        validateStatusTransition(application.getStatus(), newStatus);

        application.setStatus(newStatus);
        Application updated = applicationRepository.save(application);

        log.info("Application {} status updated to {}", id, newStatus);
        return enrichApplicationResponse(updated);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void withdrawApplication(String id, String userId) {
        log.info("User {} withdrawing application {}", userId, id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ValidationMessages.APPLICATION_NOT_FOUND));

        // Verify ownership
        if (!application.getUserId().equals(userId)) {
            throw new ForbiddenException(ValidationMessages.ACCESS_DENIED);
        }

        // Option 1: Hard delete
        applicationRepository.delete(application);

        // Option 2: Soft delete (if you add a 'withdrawn' status)
        // application.setStatus(ApplicationStatus.WITHDRAWN);
        // applicationRepository.save(application);

        log.info("Application {} withdrawn successfully", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 STATISTICS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public long countByUser(String userId) {
        return applicationRepository.countByUserId(userId);
    }

    @Override
    public long countByJob(String jobId) {
        return applicationRepository.countByJobId(jobId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fetches job from job-service and validates it's accepting applications.
     * 
     * @param jobId Job ID to validate
     * @return JobDTO if valid
     * @throws NotFoundException           if job doesn't exist
     * @throws ForbiddenException          if job is not PUBLISHED
     * @throws ServiceUnavailableException if job-service is down
     */
    private JobDTO fetchAndValidateJob(String jobId) {
        try {
            RestResponse<JobDTO> response = jobServiceClient.getJobById(jobId);
            JobDTO job = response.getData();

            if (job == null) {
                throw new NotFoundException(ValidationMessages.JOB_NOT_FOUND);
            }

            if (!job.isAcceptingApplications()) {
                log.warn("Job {} is not accepting applications (status: {})",
                        jobId, job.getStatus());
                throw new ForbiddenException(ValidationMessages.JOB_NOT_PUBLISHED);
            }

            return job;

        } catch (FeignException.NotFound e) {
            throw new NotFoundException(ValidationMessages.JOB_NOT_FOUND, e);
        } catch (FeignException e) {
            log.error("Error calling job-service for job {}: {}", jobId, e.getMessage());
            throw new ServiceUnavailableException(ValidationMessages.SERVICE_UNAVAILABLE, e);
        }
    }

    /**
     * Fetches CV from cv-service and validates ownership.
     * 
     * @param cvId   CV ID to validate
     * @param userId User ID to check ownership against
     * @return CvDTO if valid
     * @throws NotFoundException           if CV doesn't exist
     * @throws ForbiddenException          if CV doesn't belong to user
     * @throws ServiceUnavailableException if cv-service is down
     */
    private CvDTO fetchAndValidateCv(String cvId, String userId) {
        try {
            RestResponse<CvDTO> response = cvServiceClient.getCvById(cvId);
            CvDTO cv = response.getData();

            if (cv == null) {
                throw new NotFoundException(ValidationMessages.CV_NOT_FOUND);
            }

            if (!cv.belongsToUser(userId)) {
                log.warn("CV {} does not belong to user {}", cvId, userId);
                throw new ForbiddenException(ValidationMessages.CV_NOT_OWNED_BY_USER);
            }

            return cv;

        } catch (FeignException.NotFound e) {
            throw new NotFoundException(ValidationMessages.CV_NOT_FOUND, e);
        } catch (FeignException e) {
            log.error("Error calling cv-service for cv {}: {}", cvId, e.getMessage());
            throw new ServiceUnavailableException(ValidationMessages.SERVICE_UNAVAILABLE, e);
        }
    }

    /**
     * Builds enriched application response with job and CV details.
     * 
     * @param application Saved application entity
     * @param job         Job data from job-service
     * @param cv          CV data from cv-service
     * @return Enriched ApplicationResponse
     */
    private ApplicationResponse buildEnrichedResponse(Application application, JobDTO job, CvDTO cv) {
        ApplicationResponse response = applicationMapper.toResponse(application);

        // Enrich with job details
        response.setJobTitle(job.getTitle());
        response.setCompanyName(job.getCompanyName());

        // Enrich with CV details
        response.setCvHeadline(cv.getHeadline());

        return response;
    }

    /**
     * Enriches application response by fetching external data.
     * Uses best-effort approach - doesn't fail if external service is down.
     * 
     * @param application Application entity
     * @return Enriched response (may have null enrichment fields if services are
     *         down)
     */
    private ApplicationResponse enrichApplicationResponse(Application application) {
        ApplicationResponse response = applicationMapper.toResponse(application);

        // Best effort enrichment - don't fail the request
        try {
            RestResponse<JobDTO> jobResponse = jobServiceClient.getJobById(application.getJobId());
            if (jobResponse.getData() != null) {
                response.setJobTitle(jobResponse.getData().getTitle());
                response.setCompanyName(jobResponse.getData().getCompanyName());
            }
        } catch (Exception e) {
            log.warn("Failed to enrich job data for application {}: {}",
                    application.getId(), e.getMessage());
        }

        try {
            RestResponse<CvDTO> cvResponse = cvServiceClient.getCvById(application.getCvId());
            if (cvResponse.getData() != null) {
                response.setCvHeadline(cvResponse.getData().getHeadline());
            }
        } catch (Exception e) {
            log.warn("Failed to enrich CV data for application {}: {}",
                    application.getId(), e.getMessage());
        }

        return response;
    }

    /**
     * Builds paginated response from Page of applications.
     * 
     * @param page Page of Application entities
     * @return ResultPaginationDTO with enriched responses
     */
    private ResultPaginationDTO<ApplicationResponse> buildPaginatedResponse(Page<Application> page) {
        var responses = page.getContent().stream()
                .map(this::enrichApplicationResponse)
                .toList();

        return ResultPaginationDTO.of(
                responses,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Validates if status transition is allowed.
     * 
     * Valid transitions:
     * APPLIED → REVIEWING, REJECTED
     * REVIEWING → INTERVIEW, REJECTED
     * INTERVIEW → OFFER, REJECTED
     * OFFER → HIRED, REJECTED
     * 
     * @param currentStatus Current status
     * @param newStatus     Target status
     * @throws IllegalArgumentException if transition is invalid
     */
    private void validateStatusTransition(ApplicationStatus currentStatus, ApplicationStatus newStatus) {
        // Allow any transition for now - can implement state machine later
        // Example validation:
        // if (currentStatus == ApplicationStatus.HIRED || currentStatus ==
        // ApplicationStatus.REJECTED) {
        // throw new IllegalArgumentException("Cannot change status from terminal state:
        // " + currentStatus);
        // }

        log.debug("Status transition: {} → {}", currentStatus, newStatus);
    }
}
