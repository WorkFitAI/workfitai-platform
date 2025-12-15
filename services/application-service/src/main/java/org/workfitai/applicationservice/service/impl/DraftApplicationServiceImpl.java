package org.workfitai.applicationservice.service.impl;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.FileStoragePort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.saga.ApplicationSagaOrchestrator;
import org.workfitai.applicationservice.service.IDraftApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Draft Application Service.
 * Manages draft application lifecycle: create, update, submit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DraftApplicationServiceImpl implements IDraftApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final FileStoragePort fileStoragePort;
    private final ApplicationSagaOrchestrator sagaOrchestrator;

    @Override
    @Transactional
    public ApplicationResponse createDraft(
            String jobId,
            String email,
            String coverLetter,
            MultipartFile cvFile,
            String username) {

        log.info("Creating draft application for user: {}, job: {}", username, jobId);

        // Check if user already has a draft for this job
        boolean draftExists = applicationRepository.existsByUsernameAndJobIdAndIsDraftAndDeletedAtIsNull(
                username, jobId, true);
        if (draftExists) {
            throw new BadRequestException("You already have a draft application for this job");
        }

        // Check if user already has an active application for this job
        boolean applicationExists = applicationRepository.findByUsernameAndJobIdAndDeletedAtIsNull(username, jobId)
                .isPresent();
        if (applicationExists) {
            throw new BadRequestException("You have already applied to this job");
        }

        // Build draft application
        Application.ApplicationBuilder draftBuilder = Application.builder()
                .username(username)
                .email(email)
                .jobId(jobId)
                .coverLetter(coverLetter)
                .isDraft(true)
                .status(ApplicationStatus.DRAFT);

        // Upload CV if provided (optional for draft)
        if (cvFile != null && !cvFile.isEmpty()) {
            String tempFolder = "draft-" + java.util.UUID.randomUUID().toString();
            FileUploadResult uploadResult = fileStoragePort.uploadFile(cvFile, username, tempFolder);

            draftBuilder
                    .cvFileUrl(uploadResult.getFileUrl())
                    .cvFileName(uploadResult.getFileName())
                    .cvContentType(uploadResult.getContentType())
                    .cvFileSize(uploadResult.getFileSize());
        }

        Application draft = draftBuilder.build();
        Application savedDraft = applicationRepository.save(draft);

        log.info("Draft application created successfully: id={}", savedDraft.getId());
        return applicationMapper.toResponse(savedDraft);
    }

    @Override
    @Transactional
    public ApplicationResponse updateDraft(
            String id,
            String email,
            String coverLetter,
            MultipartFile cvFile,
            String username) {

        log.info("Updating draft application: id={}, user={}", id, username);

        // Find draft and verify ownership
        Application draft = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Draft application not found"));

        // Verify ownership
        if (!draft.getUsername().equals(username)) {
            throw new ForbiddenException("You can only update your own draft applications");
        }

        // Verify it's still a draft
        if (!draft.isDraft()) {
            throw new BadRequestException("Cannot update a submitted application");
        }

        // Update fields if provided
        if (email != null && !email.isBlank()) {
            draft.setEmail(email);
        }

        if (coverLetter != null) {
            draft.setCoverLetter(coverLetter);
        }

        // Update CV if provided
        if (cvFile != null && !cvFile.isEmpty()) {
            // Delete old CV if exists
            if (draft.getCvFileUrl() != null) {
                try {
                    fileStoragePort.deleteFile(draft.getCvFileUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old CV file: {}", draft.getCvFileUrl(), e);
                }
            }

            // Upload new CV
            String tempFolder = "draft-" + draft.getId();
            FileUploadResult uploadResult = fileStoragePort.uploadFile(cvFile, username, tempFolder);

            draft.setCvFileUrl(uploadResult.getFileUrl());
            draft.setCvFileName(uploadResult.getFileName());
            draft.setCvContentType(uploadResult.getContentType());
            draft.setCvFileSize(uploadResult.getFileSize());
        }

        Application updatedDraft = applicationRepository.save(draft);
        log.info("Draft application updated successfully: id={}", updatedDraft.getId());

        return applicationMapper.toResponse(updatedDraft);
    }

    @Override
    @Transactional
    public ApplicationResponse submitDraft(
            String id,
            MultipartFile cvFile,
            String username) {

        log.info("Submitting draft application: id={}, user={}", id, username);

        // Find draft and verify ownership
        Application draft = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Draft application not found"));

        // Verify ownership
        if (!draft.getUsername().equals(username)) {
            throw new ForbiddenException("You can only submit your own draft applications");
        }

        // Verify it's still a draft
        if (!draft.isDraft()) {
            throw new BadRequestException("Application has already been submitted");
        }

        // Verify CV is present (either already uploaded or provided now)
        if (draft.getCvFileUrl() == null && (cvFile == null || cvFile.isEmpty())) {
            throw new BadRequestException("CV file is required to submit application");
        }

        // Upload CV if provided now
        if (cvFile != null && !cvFile.isEmpty()) {
            // Delete old CV if exists
            if (draft.getCvFileUrl() != null) {
                try {
                    fileStoragePort.deleteFile(draft.getCvFileUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old CV file: {}", draft.getCvFileUrl(), e);
                }
            }

            // Upload final CV
            String folder = draft.getId();
            FileUploadResult uploadResult = fileStoragePort.uploadFile(cvFile, username, folder);

            draft.setCvFileUrl(uploadResult.getFileUrl());
            draft.setCvFileName(uploadResult.getFileName());
            draft.setCvContentType(uploadResult.getContentType());
            draft.setCvFileSize(uploadResult.getFileSize());
        }

        // Convert draft to submitted application
        draft.setDraft(false);
        draft.setSubmittedAt(Instant.now());
        draft.setStatus(ApplicationStatus.APPLIED);

        // Add initial status history entry
        Application.StatusChange initialStatusChange = Application.StatusChange.builder()
                .previousStatus(null)
                .newStatus(ApplicationStatus.APPLIED)
                .changedBy(username)
                .changedAt(Instant.now())
                .reason("Application submitted")
                .build();
        draft.getStatusHistory().add(initialStatusChange);

        Application submittedApplication = applicationRepository.save(draft);

        // TODO: Publish Kafka events for submitted application
        // This requires refactoring Saga to support draft submission
        // For now, we just mark as submitted without full Saga workflow
        // The Saga workflow will be triggered after this in a follow-up task

        log.info("Draft application submitted successfully: id={}", submittedApplication.getId());
        return applicationMapper.toResponse(submittedApplication);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO<ApplicationResponse> getMyDrafts(String username, Pageable pageable) {
        log.info("Fetching draft applications for user: {}", username);

        Page<Application> draftsPage = applicationRepository.findByUsernameAndIsDraftAndDeletedAtIsNull(
                username, true, pageable);

        return applicationMapper.toResultPaginationDTO(draftsPage);
    }
}
