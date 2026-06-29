package org.workfitai.applicationservice.saga;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.CvSnapshotResponse;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.JobStatsUpdateEvent;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.port.outbound.FileStoragePort;
import org.workfitai.applicationservice.port.outbound.JobServicePort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.saga.ApplicationSagaContext.SagaStep;
import org.workfitai.applicationservice.validation.ValidationPipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Saga Orchestrator for application creation workflow.
 * 
 * Orchestration Pattern: Sequential saga with compensation on failure.
 * 
 * Saga Steps:
 * 1. VALIDATE      - Run validation pipeline (duplicate check, file validation, job validation)
 * 2. FETCH_JOB_INFO - Get job details from job-service for snapshot
 * 3. UPLOAD_CV     - Upload CV PDF to MinIO
 * 4. SNAPSHOT_CV   - Create CV snapshot in cv-service (best-effort, non-blocking)
 * 5. SAVE_APPLICATION - Persist application to MongoDB
 * 6. PUBLISH_EVENTS - Fire Kafka events (fire-and-forget)
 * 
 * Compensation:
 * - If step 5 fails after step 3: Delete uploaded file from MinIO
 * - SNAPSHOT_CV failure is silently swallowed (best-effort)
 * - If step 6 fails: Log warning (events are fire-and-forget, don't rollback application)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationSagaOrchestrator {

    // Short random suffix length for temp folder names during CV upload
    private static final int TEMP_ID_SUFFIX_LENGTH = 8;
    // "system" is a sentinel value used by job-service for auto-created jobs with no human HR owner
    private static final String SYSTEM_USER = "system";

    private final ValidationPipeline validationPipeline;
    private final JobServicePort jobServicePort;
    private final FileStoragePort fileStoragePort;
    private final ApplicationRepository applicationRepository;
    private final EventPublisherPort eventPublisher;
    private final ApplicationMapper applicationMapper;
    private final UserServiceClient userServiceClient;
    private final CvServiceClient cvServiceClient;

    /**
     * Execute the full saga for creating an application.
     * Not @Transactional — this is a distributed saga spanning MinIO + MongoDB + Kafka.
     * Atomicity is provided by explicit compensation in {@link #compensate(ApplicationSagaContext)}.
     *
     * @param request  The application request with file
     * @param username The authenticated username
     * @return ApplicationResponse on success
     * @throws RuntimeException on any saga step failure
     */
    @CacheEvict(value = "systemStats", allEntries = true)
    public ApplicationResponse createApplication(CreateApplicationRequest request, String username) {
        log.info("Starting application creation saga for user: {}, job: {}", username, request.getJobId());

        // Initialize saga context — pre-generate applicationId so cv-service snapshot links back correctly
        ApplicationSagaContext context = ApplicationSagaContext.builder()
                .username(username)
                .email(request.getEmail())
                .jobId(request.getJobId())
                .coverLetter(request.getCoverLetter())
                .applicationId(UUID.randomUUID().toString())
                .build();

        try {
            // Step 1: Validate
            executeValidationStep(context, request);

            // Step 2: Fetch job info for snapshot
            executeFetchJobInfoStep(context);

            // Step 3: Upload CV to MinIO
            executeUploadCvStep(context, request);

            // Step 4: Create CV snapshot in cv-service (best-effort — never fails the saga)
            executeSnapshotCvStep(context, request);

            // Step 5: Save application to MongoDB
            executeSaveApplicationStep(context);

            // Step 6: Publish events (fire-and-forget)
            executePublishEventsStep(context);

            context.setCompleted(true);
            log.info("Application creation saga completed successfully: applicationId={}",
                    context.getSavedApplication().getId());

            return applicationMapper.toResponse(context.getSavedApplication());

        } catch (Exception e) {
            log.error("Saga failed at step {}: {}", context.getCurrentStep(), e.getMessage());
            compensate(context);
            throw e;
        }
    }

    private void executeValidationStep(ApplicationSagaContext context, CreateApplicationRequest request) {
        context.setCurrentStep(SagaStep.VALIDATE);
        log.debug("Saga Step 1: VALIDATE");

        validationPipeline.validate(request, context.getUsername());
    }

    private void executeFetchJobInfoStep(ApplicationSagaContext context) {
        context.setCurrentStep(SagaStep.FETCH_JOB_INFO);
        log.debug("Saga Step 2: FETCH_JOB_INFO");

        JobInfo jobInfo = jobServicePort.validateAndGetJob(context.getJobId());
        context.setJobInfo(jobInfo);

        log.debug("Job info fetched: title={}, company={}", jobInfo.getTitle(), jobInfo.getCompanyName());
    }

    private void executeUploadCvStep(ApplicationSagaContext context, CreateApplicationRequest request) {
        context.setCurrentStep(SagaStep.UPLOAD_CV);
        log.debug("Saga Step 3: UPLOAD_CV");

        // Use a temp folder initially, will be renamed after we have applicationId
        String tempFolder = "temp-" + UUID.randomUUID().toString().substring(0, TEMP_ID_SUFFIX_LENGTH);

        FileUploadResult result = fileStoragePort.uploadFile(
                request.getCvPdfFile(),
                context.getUsername(),
                tempFolder);
        context.setFileUploadResult(result);

        log.debug("CV uploaded: url={}", result.getFileUrl());
    }

    /**
     * SNAPSHOT_CV step — calls cv-service to parse the PDF and create an immutable snapshot CV.
     *
     * This step is best-effort: any failure (cv-service down, timeout, parse error) is caught
     * and logged as a warning. The saga continues with {@code context.cvSnapshot == null}.
     * Ranking will still work but CV fields will be empty for this applicant.
     */
    private void executeSnapshotCvStep(ApplicationSagaContext context, CreateApplicationRequest request) {
        context.setCurrentStep(SagaStep.SNAPSHOT_CV);
        log.debug("Saga Step 4: SNAPSHOT_CV");

        try {
            CvSnapshotResponse snapshot = cvServiceClient.createApplicationSnapshot(
                    context.getUsername(),
                    context.getApplicationId(),
                    context.getJobInfo().getTitle(),
                    request.getCvPdfFile()
            );
            context.setCvSnapshot(snapshot);
            log.info("Saga SNAPSHOT_CV: snapshot created — cvId={} applicationId={}",
                    snapshot.getCvId(), context.getApplicationId());
        } catch (Exception e) {
            // Best-effort: swallow error, ranking will work with empty CV data
            log.warn("Saga SNAPSHOT_CV: failed to create CV snapshot (non-critical, saga continues): {}",
                    e.getMessage());
            context.setCvSnapshot(null);
        }
    }

    private void executeSaveApplicationStep(ApplicationSagaContext context) {
        context.setCurrentStep(SagaStep.SAVE_APPLICATION);
        log.debug("Saga Step 5: SAVE_APPLICATION");

        FileUploadResult fileResult = context.getFileUploadResult();
        JobInfo jobInfo = context.getJobInfo();
        CvSnapshotResponse snapshot = context.getCvSnapshot();

        // Build job snapshot
        Application.JobSnapshot jobSnapshot = Application.JobSnapshot.builder()
                .postId(jobInfo.getPostId())
                .title(jobInfo.getTitle())
                .shortDescription(jobInfo.getShortDescription())
                .description(jobInfo.getDescription())
                .employmentType(jobInfo.getEmploymentType())
                .experienceLevel(jobInfo.getExperienceLevel())
                .educationLevel(jobInfo.getEducationLevel())
                .requiredExperience(jobInfo.getRequiredExperience())
                .salaryMin(jobInfo.getSalaryMin())
                .salaryMax(jobInfo.getSalaryMax())
                .currency(jobInfo.getCurrency())
                .location(jobInfo.getLocation())
                .quantity(jobInfo.getQuantity())
                .totalApplications(jobInfo.getTotalApplications())
                .createdDate(jobInfo.getCreatedDate())
                .lastModifiedDate(jobInfo.getLastModifiedDate())
                .expiresAt(jobInfo.getExpiresAt())
                .status(jobInfo.getStatus())
                .skillNames(jobInfo.getSkillNames())
                .bannerUrl(jobInfo.getBannerUrl())
                .createdBy(jobInfo.getCreatedBy())
                .companyNo(jobInfo.getCompanyId())
                .companyName(jobInfo.getCompanyName())
                .companyDescription(jobInfo.getCompanyDescription())
                .companyAddress(jobInfo.getCompanyAddress())
                .companyWebsiteUrl(jobInfo.getCompanyWebsiteUrl())
                .companyLogoUrl(jobInfo.getCompanyLogoUrl())
                .companySize(jobInfo.getCompanySize())
                .snapshotAt(Instant.now())
                .build();

        log.debug("Company ID for application: {}", jobInfo.getCompanyId());

        // Build application entity
        // Only auto-assign if the job has a valid HR creator (H3 fix)
        String createdBy = jobInfo.getCreatedBy();
        boolean validHR = createdBy != null && !createdBy.isBlank() && !"system".equals(createdBy);

        Instant now = Instant.now();
        Application.StatusChange initialStatus = Application.StatusChange.builder()
                .previousStatus(null)
                .newStatus(ApplicationStatus.APPLIED)
                .changedBy(context.getUsername())
                .changedAt(now)
                .reason("Application submitted")
                .build();

        Application application = Application.builder()
                .id(context.getApplicationId())
                .username(context.getUsername())
                .email(context.getEmail())
                .jobId(context.getJobId())
                .companyId(jobInfo.getCompanyId())
                .jobSnapshot(jobSnapshot)
                .cvFileUrl(fileResult.getFileUrl())
                .cvFileName(fileResult.getFileName())
                .cvContentType(fileResult.getContentType())
                .cvFileSize(fileResult.getFileSize())
                // CV snapshot linkage (null when cv-service was unavailable)
                .cvSnapshotId(snapshot != null ? snapshot.getCvId() : null)
                .coverLetter(context.getCoverLetter())
                .status(ApplicationStatus.APPLIED)
                .statusHistory(new ArrayList<>(List.of(initialStatus)))
                .assignedTo(validHR ? createdBy : null)
                .assignedAt(validHR ? now : null)
                .assignedBy(validHR ? "SYSTEM" : null)
                // @CreatedDate/@LastModifiedDate auditing relies on the entity being "new"
                // (i.e. @Id null at save time), but the id is pre-generated above so
                // cv-service can link its snapshot before persistence — set explicitly instead.
                .createdAt(now)
                .updatedAt(now)
                .build();

        Application saved = applicationRepository.save(application);
        context.setSavedApplication(saved);

        log.debug("Application saved: id={}, cvSnapshotId={}", saved.getId(), saved.getCvSnapshotId());
    }

    private void executePublishEventsStep(ApplicationSagaContext context) {
        context.setCurrentStep(SagaStep.PUBLISH_EVENTS);
        log.debug("Saga Step 6: PUBLISH_EVENTS (fire-and-forget)");

        Application app = context.getSavedApplication();
        CvSnapshotResponse snapshot = context.getCvSnapshot();

        try {
            // Publish APPLICATION_CREATED event for notifications + recommendation-engine
            JobInfo jobInfo = context.getJobInfo();
            ApplicationCreatedEvent event = ApplicationCreatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("APPLICATION_CREATED")
                    .timestamp(Instant.now())
                    .data(ApplicationCreatedEvent.ApplicationData.builder()
                            .applicationId(app.getId())
                            .username(app.getUsername())
                            .jobId(app.getJobId())
                            .cvFileUrl(app.getCvFileUrl())
                            .status(app.getStatus())
                            .jobTitle(app.getJobSnapshot().getTitle())
                            .companyName(app.getJobSnapshot().getCompanyName())
                            .appliedAt(Instant.now())
                            .hrUsername(jobInfo.getCreatedBy())
                            .candidateName(app.getUsername()) // Will be enhanced by notification-service
                            // CV snapshot fields — empty strings when snapshot unavailable
                            .cvSnapshotId(snapshot != null ? snapshot.getCvId() : null)
                            .resumeSummary(snapshot != null ? snapshot.getSummary() : "")
                            .resumeExperience(snapshot != null ? snapshot.getExperience() : "")
                            .resumeSkills(snapshot != null ? snapshot.getSkills() : "")
                            .resumeEducation(snapshot != null ? snapshot.getEducation() : "")
                            .build())
                    .build();

            eventPublisher.publishApplicationCreated(event);
            log.debug("Application created event published (cvSnapshotId={})",
                    snapshot != null ? snapshot.getCvId() : "null");

            // Publish JOB_STATS_UPDATE event for job-service — always 1 because this saga
            // creates exactly one application; job-service applies the INCREMENT delta itself.
            JobStatsUpdateEvent statsEvent = JobStatsUpdateEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .jobId(UUID.fromString(app.getJobId()))
                    .totalApplications(1)
                    .timestamp(Instant.now())
                    .operation("INCREMENT")
                    .build();

            eventPublisher.publishJobStatsUpdate(statsEvent);
            log.debug("Job stats update event published: jobId={}, totalApplications=1", app.getJobId());

            // Fetch user details and publish notification events
            String hrUsername = jobInfo.getCreatedBy();

            // Prepare list of usernames to fetch (always include candidate)
            List<String> usernamesToFetch = new ArrayList<>();
            usernamesToFetch.add(app.getUsername()); // Always fetch candidate

            if (hrUsername != null && !hrUsername.isEmpty() && !hrUsername.equals(SYSTEM_USER)) {
                usernamesToFetch.add(hrUsername); // Add HR if valid
            }

            log.info("Fetching user details from user-service for: {}", usernamesToFetch);

            var usersResponse = userServiceClient.getUsersByUsernames(usernamesToFetch);

            if (usersResponse == null || usersResponse.getData() == null) {
                log.warn("Failed to fetch user details from user-service: response is null");
            } else {
                log.debug("Fetched {} users from user-service", usersResponse.getData().size());

                // Extract candidate info (REQUIRED)
                var candidateInfoOpt = usersResponse.getData().stream()
                        .filter(u -> app.getUsername().equals(u.username()))
                        .findFirst();

                if (candidateInfoOpt.isEmpty()) {
                    log.error("Failed to fetch candidate info for username: {}", app.getUsername());
                } else {
                    var candidateInfo = candidateInfoOpt.get();
                    // ALWAYS publish candidate notification
                    eventPublisher.publishCandidateNotification(
                            app.getId(),
                            candidateInfo.email(),
                            candidateInfo.username(), // Pass username for recipientUserId
                            app.getJobSnapshot().getTitle(),
                            app.getJobSnapshot().getCompanyName(),
                            app.getCreatedAt());
                    log.info("Candidate notification published: email={}, username={}",
                            candidateInfo.email(), candidateInfo.username());

                    // Publish HR notification ONLY if HR username is valid
                    if (hrUsername != null && !hrUsername.isEmpty() && !hrUsername.equals(SYSTEM_USER)) {
                        var hrInfoOpt = usersResponse.getData().stream()
                                .filter(u -> hrUsername.equals(u.username()))
                                .findFirst();

                        if (hrInfoOpt.isPresent()) {
                            var hrInfo = hrInfoOpt.get();
                            eventPublisher.publishHrNotification(
                                    app.getId(),
                                    hrInfo.email(),
                                    hrInfo.username(), // Pass username for recipientUserId
                                    candidateInfo.fullName(),
                                    app.getJobSnapshot().getTitle(),
                                    app.getJobSnapshot().getCompanyName(),
                                    app.getCreatedAt());
                            log.info("HR notification published: email={}, username={}",
                                    hrInfo.email(), hrInfo.username());
                        } else {
                            log.warn("Failed to fetch HR info for username: {}", hrUsername);
                        }
                    } else {
                        log.info("Skipping HR notification (no valid HR username)");
                    }
                }
            }

        } catch (Exception e) {
            // Fire-and-forget: log but don't fail the saga
            log.warn("Failed to publish events (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Compensate for saga failures by rolling back completed steps.
     */
    private void compensate(ApplicationSagaContext context) {
        log.info("Running saga compensation from step: {}", context.getCurrentStep());

        // If we uploaded a file, delete it
        if (context.getFileUploadResult() != null) {
            try {
                log.info("Compensation: Deleting uploaded CV file");
                fileStoragePort.deleteFile(context.getFileUploadResult().getFileUrl());
            } catch (Exception e) {
                log.error("Compensation failed - could not delete CV file: {}", e.getMessage());
            }
        }

        // Note: cv-service snapshot is orphaned on failure — acceptable, cleanup can be done via TTL or admin job
        // Note: Kafka events are fire-and-forget, no compensation needed
    }
}
