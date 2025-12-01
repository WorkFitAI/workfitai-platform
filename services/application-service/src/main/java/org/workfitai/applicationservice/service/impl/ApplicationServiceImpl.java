package org.workfitai.applicationservice.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.messaging.ApplicationEventProducer;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.IApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service implementation using event-driven architecture.
 * Publishes events to Kafka for cross-service communication instead of
 * synchronous REST calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements IApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final ApplicationEventProducer eventProducer;

    @Override
    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, String username) {
        log.info(Messages.Log.CREATING_APPLICATION, username, request.getJobId());

        // Check for duplicate application (only local validation needed)
        if (applicationRepository.existsByUsernameAndJobId(username, request.getJobId())) {
            log.warn(Messages.Log.DUPLICATE_APPLICATION, username, request.getJobId());
            throw new ApplicationConflictException(Messages.Error.APPLICATION_ALREADY_EXISTS);
        }

        // Create application entity
        Application application = applicationMapper.toEntity(request);
        application.setUsername(username);
        application.setStatus(ApplicationStatus.APPLIED);

        // Save to database
        Application savedApplication = applicationRepository.save(application);
        log.info(Messages.Log.APPLICATION_CREATED, savedApplication.getId());

        publishApplicationCreatedEvent(savedApplication);

        return applicationMapper.toResponse(savedApplication);
    }

    @Override
    public ApplicationResponse getApplicationById(String id) {
        log.debug(Messages.Log.FETCHING_APPLICATION, id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        return applicationMapper.toResponse(application);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplications(String username, Pageable pageable) {
        log.debug(Messages.Log.FETCHING_USER_APPLICATIONS, username);

        Page<Application> page = applicationRepository.findByUsername(username, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplicationsByStatus(
            String username,
            ApplicationStatus status,
            Pageable pageable) {

        log.debug("Fetching applications for user {} with status {}", username, status);

        Page<Application> page = applicationRepository.findByUsernameAndStatus(username, status, pageable);
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
    public boolean hasUserAppliedToJob(String username, String jobId) {
        return applicationRepository.existsByUsernameAndJobId(username, jobId);
    }

    @Override
    @Transactional
    public ApplicationResponse updateStatus(String id, ApplicationStatus newStatus, String updatedBy) {
        log.info(Messages.Log.UPDATING_STATUS, id, newStatus);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        ApplicationStatus previousStatus = application.getStatus();
        validateStatusTransition(previousStatus, newStatus);

        application.setStatus(newStatus);
        Application updated = applicationRepository.save(application);

        publishStatusChangedEvent(updated, previousStatus, updatedBy);

        log.info(Messages.Log.STATUS_UPDATED, id, newStatus);
        return applicationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void withdrawApplication(String id, String username) {
        log.info(Messages.Log.WITHDRAWING_APPLICATION, username, id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        if (!application.getUsername().equals(username)) {
            throw new ForbiddenException(Messages.Error.ACCESS_DENIED);
        }

        String jobId = application.getJobId();

        applicationRepository.delete(application);

        publishApplicationWithdrawnEvent(id, username, jobId);

        log.info(Messages.Log.APPLICATION_WITHDRAWN, id);
    }

    @Override
    public long countByUser(String username) {
        return applicationRepository.countByUsername(username);
    }

    @Override
    public long countByJob(String jobId) {
        return applicationRepository.countByJobId(jobId);
    }

    private void publishApplicationCreatedEvent(Application application) {
        eventProducer.publishApplicationCreatedEvent(
                ApplicationCreatedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(Messages.Kafka.EVENT_APPLICATION_CREATED)
                        .timestamp(Instant.now())
                        .data(ApplicationCreatedEvent.ApplicationData.builder()
                                .applicationId(application.getId())
                                .username(application.getUsername())
                                .jobId(application.getJobId())
                                .cvId(application.getCvId())
                                .status(application.getStatus())
                                .appliedAt(Instant.now())
                                .build())
                        .build());
    }

    private void publishStatusChangedEvent(Application application, ApplicationStatus previousStatus,
            String updatedBy) {
        eventProducer.publishStatusChangedEvent(
                ApplicationStatusChangedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(Messages.Kafka.EVENT_STATUS_CHANGED)
                        .timestamp(Instant.now())
                        .data(ApplicationStatusChangedEvent.StatusChangeData.builder()
                                .applicationId(application.getId())
                                .username(application.getUsername())
                                .jobId(application.getJobId())
                                .previousStatus(previousStatus)
                                .newStatus(application.getStatus())
                                .changedBy(updatedBy)
                                .changedAt(Instant.now())
                                .build())
                        .build());
    }

    private void publishApplicationWithdrawnEvent(String applicationId, String username, String jobId) {
        eventProducer.publishApplicationWithdrawnEvent(
                ApplicationWithdrawnEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(Messages.Kafka.EVENT_APPLICATION_WITHDRAWN)
                        .timestamp(Instant.now())
                        .data(ApplicationWithdrawnEvent.WithdrawalData.builder()
                                .applicationId(applicationId)
                                .username(username)
                                .jobId(jobId)
                                .withdrawnAt(Instant.now())
                                .build())
                        .build());
    }

    private ResultPaginationDTO<ApplicationResponse> buildPaginatedResponse(Page<Application> page) {
        var responses = page.getContent().stream()
                .map(applicationMapper::toResponse)
                .toList();

        return ResultPaginationDTO.of(
                responses,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private void validateStatusTransition(ApplicationStatus currentStatus, ApplicationStatus newStatus) {
        log.debug("Status transition: {} → {}", currentStatus, newStatus);
    }
}
