package org.workfitai.applicationservice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.dto.response.StatusChangeResponse;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.IApplicationService;
import org.workfitai.applicationservice.validation.StatusTransitionValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service implementation.
 * 
 * Note: Application creation is handled by ApplicationSagaOrchestrator.
 * This service handles read operations and status updates.
 * 
 * Uses EventPublisherPort (Hexagonal Architecture) for fire-and-forget event
 * publishing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements IApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final EventPublisherPort eventPublisher;
    private final StatusTransitionValidator statusTransitionValidator;
    private final ApplicationSecurity applicationSecurity;

    @Override
    public ApplicationResponse getApplicationById(String id) {
        log.debug(Messages.Log.FETCHING_APPLICATION, id);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        return applicationMapper.toResponse(application);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplications(String username, Pageable pageable) {
        log.debug(Messages.Log.FETCHING_USER_APPLICATIONS, username);

        Page<Application> page = applicationRepository.findByUsernameAndDeletedAtIsNull(username, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getMyApplicationsByStatus(
            String username,
            ApplicationStatus status,
            Pageable pageable) {

        log.debug("Fetching applications for user {} with status {}", username, status);

        Page<Application> page = applicationRepository.findByUsernameAndStatusAndDeletedAtIsNull(username, status,
                pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getApplicationsByJob(String jobId, Pageable pageable) {
        log.debug("Fetching applications for job: {}", jobId);

        Page<Application> page = applicationRepository.findByJobIdAndDeletedAtIsNull(jobId, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public ResultPaginationDTO<ApplicationResponse> getApplicationsByJobAndStatus(
            String jobId,
            ApplicationStatus status,
            Pageable pageable) {

        log.debug("Fetching applications for job {} with status {}", jobId, status);

        Page<Application> page = applicationRepository.findByJobIdAndStatusAndDeletedAtIsNull(jobId, status, pageable);
        return buildPaginatedResponse(page);
    }

    @Override
    public boolean hasUserAppliedToJob(String username, String jobId) {
        return applicationRepository.findByUsernameAndJobIdAndDeletedAtIsNull(username, jobId).isPresent();
    }

    @Override
    @Transactional
    public ApplicationResponse updateStatus(String id, ApplicationStatus newStatus, String updatedBy) {
        log.info(Messages.Log.UPDATING_STATUS, id, newStatus);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        ApplicationStatus previousStatus = application.getStatus();
        statusTransitionValidator.validateTransition(previousStatus, newStatus);

        application.setStatus(newStatus);

        // Add status change to history
        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(updatedBy)
                .changedAt(Instant.now())
                .build();
        application.getStatusHistory().add(statusChange);

        Application updated = applicationRepository.save(application);

        publishStatusChangedEvent(updated, previousStatus, updatedBy);

        log.info(Messages.Log.STATUS_UPDATED, id, newStatus);
        return applicationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void withdrawApplication(String id, String username) {
        log.info(Messages.Log.WITHDRAWING_APPLICATION, username, id);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        if (!application.getUsername().equals(username)) {
            throw new ForbiddenException(Messages.Error.ACCESS_DENIED);
        }

        String jobId = application.getJobId();
        ApplicationStatus previousStatus = application.getStatus();

        // Soft delete: set timestamps instead of hard delete
        application.setDeletedAt(Instant.now());
        application.setDeletedBy(username);
        application.setStatus(ApplicationStatus.WITHDRAWN);

        // Add status change to history
        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(previousStatus)
                .newStatus(ApplicationStatus.WITHDRAWN)
                .changedBy(username)
                .changedAt(Instant.now())
                .reason("Application withdrawn by candidate")
                .build();
        application.getStatusHistory().add(statusChange);

        applicationRepository.save(application);

        publishApplicationWithdrawnEvent(id, username, jobId);

        log.info(Messages.Log.APPLICATION_WITHDRAWN, id);
    }

    @Override
    public long countByUser(String username) {
        return applicationRepository.countByUsernameAndDeletedAtIsNull(username);
    }

    @Override
    public long countByJob(String jobId) {
        return applicationRepository.countByJobIdAndDeletedAtIsNull(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusChangeResponse> getStatusHistory(String id,
            org.springframework.security.core.Authentication authentication) {
        log.debug("Fetching status history for application: {}", id);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        // Verify user can view this application using ApplicationSecurity helper
        // Allows: owner, HR with application:review, or admin
        String username = applicationSecurity.getCurrentUsername(authentication);
        boolean isOwner = application.getUsername().equals(username);
        boolean hasReviewPermission = applicationSecurity.hasPermission(authentication, "application:review");
        boolean isAdmin = applicationSecurity.isAdmin(authentication);

        if (!isOwner && !hasReviewPermission && !isAdmin) {
            log.warn("User {} attempted to view status history for application {} without proper permissions",
                    username, id);
            throw new ForbiddenException(Messages.Error.ACCESS_DENIED);
        }

        // Return status history sorted by newest first
        return application.getStatusHistory().stream()
                .sorted((a, b) -> b.getChangedAt().compareTo(a.getChangedAt()))
                .map(this::mapToStatusChangeResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteResponse> getPublicNotes(String id,
            org.springframework.security.core.Authentication authentication) {
        log.debug("Fetching public notes for application: {}", id);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(Messages.Error.APPLICATION_NOT_FOUND));

        // Verify user can view this application using ApplicationSecurity helper
        // Allows: owner, HR with application:review, or admin
        String username = applicationSecurity.getCurrentUsername(authentication);
        boolean isOwner = application.getUsername().equals(username);
        boolean hasReviewPermission = applicationSecurity.hasPermission(authentication, "application:review");
        boolean isAdmin = applicationSecurity.isAdmin(authentication);

        if (!isOwner && !hasReviewPermission && !isAdmin) {
            log.warn("User {} attempted to view public notes for application {} without proper permissions",
                    username, id);
            throw new ForbiddenException(Messages.Error.ACCESS_DENIED);
        }

        // Filter only candidate-visible notes
        return application.getNotes().stream()
                .filter(Application.Note::isCandidateVisible)
                .map(this::mapToNoteResponse)
                .collect(Collectors.toList());
    }

    private StatusChangeResponse mapToStatusChangeResponse(Application.StatusChange statusChange) {
        return StatusChangeResponse.builder()
                .previousStatus(statusChange.getPreviousStatus())
                .newStatus(statusChange.getNewStatus())
                .changedBy(statusChange.getChangedBy())
                .changedAt(statusChange.getChangedAt())
                .reason(statusChange.getReason())
                .build();
    }

    private NoteResponse mapToNoteResponse(Application.Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .author(note.getAuthor())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    private void publishStatusChangedEvent(Application application, ApplicationStatus previousStatus,
            String updatedBy) {
        try {
            eventPublisher.publishStatusChanged(
                    ApplicationStatusChangedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType("STATUS_CHANGED")
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
        } catch (Exception e) {
            // Fire-and-forget: log but don't fail the operation
            log.warn("Failed to publish status changed event (non-critical): {}", e.getMessage());
        }
    }

    private void publishApplicationWithdrawnEvent(String applicationId, String username, String jobId) {
        try {
            eventPublisher.publishApplicationWithdrawn(
                    ApplicationWithdrawnEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType("APPLICATION_WITHDRAWN")
                            .timestamp(Instant.now())
                            .data(ApplicationWithdrawnEvent.WithdrawalData.builder()
                                    .applicationId(applicationId)
                                    .username(username)
                                    .jobId(jobId)
                                    .withdrawnAt(Instant.now())
                                    .build())
                            .build());
        } catch (Exception e) {
            // Fire-and-forget: log but don't fail the operation
            log.warn("Failed to publish application withdrawn event (non-critical): {}", e.getMessage());
        }
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

}
