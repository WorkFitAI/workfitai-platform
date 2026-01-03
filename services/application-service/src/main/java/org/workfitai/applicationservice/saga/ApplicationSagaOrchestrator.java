package org.workfitai.applicationservice.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.client.UserServiceClient;
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
 * 1. VALIDATE - Run validation pipeline (duplicate check, file validation, job
 * validation)
 * 2. FETCH_JOB_INFO - Get job details from job-service for snapshot
 * 3. UPLOAD_CV - Upload CV PDF to MinIO
 * 4. SAVE_APPLICATION - Persist application to MongoDB
 * 5. PUBLISH_EVENTS - Fire Kafka events (fire-and-forget)
 * 
 * Compensation:
 * - If step 4 fails after step 3: Delete uploaded file from MinIO
 * - If step 5 fails: Log warning (events are fire-and-forget, don't rollback
 * application)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationSagaOrchestrator {

    private final ValidationPipeline validationPipeline;
    private final JobServicePort jobServicePort;
    private final FileStoragePort fileStoragePort;
    private final ApplicationRepository applicationRepository;
    private final EventPublisherPort eventPublisher;
    private final ApplicationMapper applicationMapper;
    private final UserServiceClient userServiceClient;

    /**
     * Execute the full saga for creating an application.
     *
     * @param request  The application request with file
     * @param username The authenticated username
     * @return ApplicationResponse on success
     * @throws RuntimeException on any saga step failure
     */
    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, String username) {
        log.info("Starting application creation saga for user: {}, job: {}", username, request.getJobId());

        // Initialize saga context
        ApplicationSagaContext context = ApplicationSagaContext.builder()
                .username(username)
                .email(request.getEmail())
                .jobId(request.getJobId())
                .coverLetter(request.getCoverLetter())
                .build();

        try {
            // Step 1: Validate
            executeValidationStep(context, request);

            // Step 2: Fetch job info for snapshot
            executeFetchJobInfoStep(context);

            // Step 3: Upload CV to MinIO
            executeUploadCvStep(context, request);

            // Step 4: Save application to MongoDB
            executeSaveApplicationStep(context);

            // Step 5: Publish events (fire-and-forget)
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
        String tempFolder = "temp-" + UUID.randomUUID().toString().substring(0, 8);

        FileUploadResult result = fileStoragePort.uploadFile(
                request.getCvPdfFile(),
                context.getUsername(),
                tempFolder);
        context.setFileUploadResult(result);

        log.debug("CV uploaded: url={}", result.getFileUrl());
    }

    private void executeSaveApplicationStep(ApplicationSagaContext context) {
        context.setCurrentStep(SagaStep.SAVE_APPLICATION);
        log.debug("Saga Step 4: SAVE_APPLICATION");

        FileUploadResult fileResult = context.getFileUploadResult();
        JobInfo jobInfo = context.getJobInfo();

        // Build job snapshot
        Application.JobSnapshot snapshot = Application.JobSnapshot.builder()
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
        Application application = Application.builder()
                .username(context.getUsername())
                .email(context.getEmail())
                .jobId(context.getJobId())
                .companyId(jobInfo.getCompanyId()) // Store companyId for filtering
                .jobSnapshot(snapshot)
                .cvFileUrl(fileResult.getFileUrl())
                .cvFileName(fileResult.getFileName())
                .cvContentType(fileResult.getContentType())
                .cvFileSize(fileResult.getFileSize())
                .coverLetter(context.getCoverLetter())
                .status(ApplicationStatus.APPLIED)
                .assignedTo(jobInfo.getCreatedBy()) // Auto-assign to job creator (HR)
                .assignedAt(Instant.now())
                .assignedBy("SYSTEM") // System auto-assignment
                .build();

        Application saved = applicationRepository.save(application);
        context.setSavedApplication(saved);

        log.debug("Application saved: id={}", saved.getId());
    }

    private void executePublishEventsStep(ApplicationSagaContext context) {
        context.setCurrentStep(SagaStep.PUBLISH_EVENTS);
        log.debug("Saga Step 5: PUBLISH_EVENTS (fire-and-forget)");

        Application app = context.getSavedApplication();

        try {
            // Publish APPLICATION_CREATED event for notifications
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
                            .build())
                    .build();

            eventPublisher.publishApplicationCreated(event);
            log.debug("Application created event published");

            // Publish JOB_STATS_UPDATE event for job-service
            long totalApplications = applicationRepository.countByJobId(app.getJobId());
            JobStatsUpdateEvent statsEvent = JobStatsUpdateEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .jobId(UUID.fromString(app.getJobId()))
                    .totalApplications((int) totalApplications)
                    .timestamp(Instant.now())
                    .operation("INCREMENT")
                    .build();

            eventPublisher.publishJobStatsUpdate(statsEvent);
            log.debug("Job stats update event published: jobId={}, totalApplications={}",
                    app.getJobId(), totalApplications);

            // Publish notification events to notification-events topic
            String candidateEmail = context.getEmail(); // Get from context
            eventPublisher.publishCandidateNotification(
                    app.getId(),
                    candidateEmail,
                    app.getUsername(), // Candidate name (will be enhanced by notification-service)
                    app.getJobSnapshot().getTitle(),
                    app.getJobSnapshot().getCompanyName(),
                    Instant.now());
            log.debug("Candidate notification published: email={}", candidateEmail);

            // Publish HR notification (notification-service will lookup email by username)
            String hrUsername = jobInfo.getCreatedBy();
            if (hrUsername != null && !hrUsername.isEmpty() && !hrUsername.equals("system")) {
                eventPublisher.publishHrNotification(
                        app.getId(),
                        "", // Empty - notification-service will lookup by recipientUserId
                        hrUsername,
                        app.getUsername(),
                        app.getJobSnapshot().getTitle(),
                        app.getJobSnapshot().getCompanyName(),
                        Instant.now());
                log.debug("HR notification published for: username={}", hrUsername);
            } else {
                log.debug("Skipping HR notification: no valid HR username (createdBy={})", hrUsername);
            }

            log.debug("All notification events published successfully");

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

        // Note: If application was saved, MongoDB transaction will rollback
        // Note: Kafka events are fire-and-forget, no compensation needed
    }
}
