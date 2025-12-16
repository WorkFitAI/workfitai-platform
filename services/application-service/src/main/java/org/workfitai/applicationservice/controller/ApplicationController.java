package org.workfitai.applicationservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.request.BulkUpdateRequest;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.request.CreateDraftRequest;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.request.SubmitDraftRequest;
import org.workfitai.applicationservice.dto.request.UpdateDraftRequest;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.request.AssignApplicationRequest;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ApiError;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.dto.response.DashboardStatsResponse;
import org.workfitai.applicationservice.dto.response.JobStatsResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.PreSignedUrlResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.dto.response.StatusChangeResponse;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.saga.ApplicationSagaOrchestrator;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.ApplicationNoteService;
import org.workfitai.applicationservice.service.ApplicationSearchService;
import org.workfitai.applicationservice.service.ApplicationStatsService;
import org.workfitai.applicationservice.service.BulkOperationService;
import org.workfitai.applicationservice.service.IApplicationService;
import org.workfitai.applicationservice.service.IDraftApplicationService;
import org.workfitai.applicationservice.service.JobStatsService;
import org.workfitai.applicationservice.service.MinioPreSignedUrlService;
import org.workfitai.applicationservice.service.CompanyApplicationService;
import org.workfitai.applicationservice.service.AssignmentService;
import org.workfitai.applicationservice.service.ManagerStatsService;
import org.workfitai.applicationservice.service.ExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for job application operations.
 * 
 * Uses Saga Orchestrator pattern for application creation:
 * 1. Validate (duplicate check, file validation, job exists)
 * 2. Upload CV to MinIO
 * 3. Save application with job snapshot
 * 4. Publish Kafka events (fire-and-forget)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = Messages.Api.TAG_NAME, description = Messages.Api.TAG_DESCRIPTION)
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final IApplicationService applicationService;
    private final IDraftApplicationService draftApplicationService;
    private final ApplicationSagaOrchestrator sagaOrchestrator;
    private final ApplicationSecurity applicationSecurity;
    private final ApplicationRepository applicationRepository;

    // Phase 2 HR APIs services
    private final MinioPreSignedUrlService minioPreSignedUrlService;
    private final ApplicationNoteService applicationNoteService;
    private final ApplicationStatsService applicationStatsService;
    private final ApplicationSearchService applicationSearchService;
    private final BulkOperationService bulkOperationService;
    private final JobStatsService jobStatsService;

    // Phase 3 HR Manager APIs services
    private final CompanyApplicationService companyApplicationService;
    private final AssignmentService assignmentService;
    private final ManagerStatsService managerStatsService;
    private final ExportService exportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('application:create')")
    @Operation(summary = Messages.Api.CREATE_SUMMARY, description = Messages.Api.CREATE_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = Messages.Api.CREATE_201),
            @ApiResponse(responseCode = "400", description = Messages.Api.CREATE_400, content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = Messages.Api.CREATE_409, content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> createApplication(
            @Valid @ModelAttribute CreateApplicationRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.CREATING_APPLICATION, username, request.getJobId());

        // Use Saga Orchestrator for the multi-step workflow
        ApplicationResponse response = sagaOrchestrator.createApplication(request, username);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RestResponse.created(response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('application:list')")
    @Operation(summary = Messages.Api.GET_MY_SUMMARY, description = Messages.Api.GET_MY_DESCRIPTION)
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getMyApplications(
            @RequestParam(required = false) @Parameter(description = Messages.Api.PARAM_STATUS) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = Messages.Api.PARAM_PAGE) int page,
            @RequestParam(defaultValue = "10") @Parameter(description = Messages.Api.PARAM_SIZE) int size,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = applicationService.getMyApplicationsByStatus(username, status, pageable);
        } else {
            result = applicationService.getMyApplications(username, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    @Operation(summary = Messages.Api.GET_BY_ID_SUMMARY, description = Messages.Api.GET_BY_ID_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = Messages.Api.GET_200),
            @ApiResponse(responseCode = "403", description = Messages.Api.GET_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> getApplication(
            @PathVariable String id,
            Authentication authentication) {

        ApplicationResponse response = applicationService.getApplicationById(id);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = Messages.Api.GET_BY_JOB_SUMMARY, description = Messages.Api.GET_BY_JOB_DESCRIPTION)
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getApplicationsByJob(
            @PathVariable @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId,
            @RequestParam(required = false) @Parameter(description = Messages.Api.PARAM_STATUS) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = Messages.Api.PARAM_PAGE) int page,
            @RequestParam(defaultValue = "10") @Parameter(description = Messages.Api.PARAM_SIZE) int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = applicationService.getApplicationsByJobAndStatus(jobId, status, pageable);
        } else {
            result = applicationService.getApplicationsByJob(jobId, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@applicationSecurity.canUpdateStatus(#id, authentication)")
    @Operation(summary = Messages.Api.UPDATE_STATUS_SUMMARY, description = Messages.Api.UPDATE_STATUS_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = Messages.Api.UPDATE_200),
            @ApiResponse(responseCode = "400", description = Messages.Api.UPDATE_400),
            @ApiResponse(responseCode = "403", description = Messages.Api.GET_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> updateStatus(
            @PathVariable String id,
            @RequestParam ApplicationStatus status,
            Authentication authentication) {

        String updatedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.UPDATING_STATUS, id, status);

        ApplicationResponse response = applicationService.updateStatus(id, status, updatedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
    @Operation(summary = Messages.Api.WITHDRAW_SUMMARY, description = Messages.Api.WITHDRAW_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = Messages.Api.WITHDRAW_204),
            @ApiResponse(responseCode = "403", description = Messages.Api.WITHDRAW_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<Void> withdrawApplication(
            @PathVariable String id,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.WITHDRAWING_APPLICATION, username, id);

        applicationService.withdrawApplication(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check")
    @PreAuthorize("hasAuthority('application:read')")
    @Operation(summary = Messages.Api.CHECK_SUMMARY, description = Messages.Api.CHECK_DESCRIPTION)
    public ResponseEntity<RestResponse<Map<String, Boolean>>> hasApplied(
            @RequestParam @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        boolean hasApplied = applicationService.hasUserAppliedToJob(username, jobId);

        return ResponseEntity.ok(RestResponse.success(Map.of("applied", hasApplied)));
    }

    @GetMapping("/my/count")
    @PreAuthorize("hasAuthority('application:list')")
    @Operation(summary = Messages.Api.COUNT_MY_SUMMARY)
    public ResponseEntity<RestResponse<Map<String, Long>>> getMyApplicationCount(
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        long count = applicationService.countByUser(username);

        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }

    @GetMapping("/job/{jobId}/count")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = Messages.Api.COUNT_JOB_SUMMARY)
    public ResponseEntity<RestResponse<Map<String, Long>>> getJobApplicationCount(
            @PathVariable @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId) {

        long count = applicationService.countByJob(jobId);
        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }

    // ==================== Phase 1: Draft Application Endpoints ====================

    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('application:create')")
    @Operation(summary = "Create draft application", description = "Create a draft application without submitting. CV upload is optional.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - validation failed or draft already exists")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> createDraft(
            @Valid @ModelAttribute CreateDraftRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Creating draft application for user: {}, job: {}", username, request.getJobId());

        ApplicationResponse response = draftApplicationService.createDraft(
                request.getJobId(),
                request.getEmail(),
                request.getCoverLetter(),
                request.getCvPdfFile(),
                username);

        return ResponseEntity.status(HttpStatus.CREATED).body(RestResponse.created(response));
    }

    @PutMapping(value = "/{id}/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@applicationSecurity.canEditDraft(#id, authentication)")
    @Operation(summary = "Update draft application", description = "Update draft application fields. Only draft applications can be updated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft updated successfully"),
            @ApiResponse(responseCode = "400", description = "Not a draft or validation failed"),
            @ApiResponse(responseCode = "403", description = "Not the owner"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> updateDraft(
            @PathVariable String id,
            @Valid @ModelAttribute UpdateDraftRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Updating draft application: id={}, user={}", id, username);

        ApplicationResponse response = draftApplicationService.updateDraft(
                id,
                request.getEmail(),
                request.getCoverLetter(),
                request.getCvPdfFile(),
                username);

        return ResponseEntity.ok(RestResponse.success(response));
    }

    @PostMapping(value = "/{id}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@applicationSecurity.canEditDraft(#id, authentication)")
    @Operation(summary = "Submit draft application", description = "Convert draft to submitted application. CV required if not already uploaded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft submitted successfully"),
            @ApiResponse(responseCode = "400", description = "CV missing or validation failed"),
            @ApiResponse(responseCode = "403", description = "Not the owner"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> submitDraft(
            @PathVariable String id,
            @Valid @ModelAttribute SubmitDraftRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Submitting draft application: id={}, user={}", id, username);

        ApplicationResponse response = draftApplicationService.submitDraft(
                id,
                request.getCvPdfFile(),
                username);

        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('application:list')")
    @Operation(summary = "Get my draft applications", description = "Retrieve all draft applications for the current user")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getMyDrafts(
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "10") @Parameter(description = "Page size") int size,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result = draftApplicationService.getMyDrafts(username, pageable);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    // ==================== Phase 1: Status History & Notes ====================

    @GetMapping("/{id}/history")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    @Operation(summary = "Get status history", description = "Retrieve status change timeline for an application")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status history retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<List<StatusChangeResponse>>> getStatusHistory(
            @PathVariable String id,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        List<StatusChangeResponse> history = applicationService.getStatusHistory(id, username);
        return ResponseEntity.ok(RestResponse.success(history));
    }

    @GetMapping("/{id}/notes/public")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    @Operation(summary = "Get public HR notes", description = "Retrieve HR notes visible to candidate")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notes retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<List<NoteResponse>>> getPublicNotes(
            @PathVariable String id,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        List<NoteResponse> notes = applicationService.getPublicNotes(id, username);
        return ResponseEntity.ok(RestResponse.success(notes));
    }

    // ==================== Phase 2: HR Role APIs ====================

    @GetMapping("/{id}/cv/download")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Download CV file directly",
               description = "Download CV file directly through the application (no pre-signed URL)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CV file downloaded"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<org.springframework.core.io.Resource> downloadCv(
            @PathVariable String id,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Downloading CV for application: id={}, user={}", id, username);

        // Fetch application
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new org.workfitai.applicationservice.exception.NotFoundException("Application not found"));

        // Extract object key from file URL
        String objectKey = minioPreSignedUrlService.extractObjectKey(application.getCvFileUrl());

        // Download file from MinIO and stream to response
        try {
            byte[] fileData = minioPreSignedUrlService.downloadFile(objectKey);

            org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(fileData);

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + application.getCvFileName() + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .contentLength(fileData.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to download CV file: {}", e.getMessage(), e);
            throw new org.workfitai.applicationservice.exception.FileStorageException(
                    "Failed to download CV file: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('application:note')")
    @Operation(summary = "Add note to application", description = "Add a note (internal or public) to an application")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Note added successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<NoteResponse>> addNote(
            @PathVariable String id,
            @Valid @RequestBody CreateNoteRequest request,
            Authentication authentication) {

        String author = applicationSecurity.getCurrentUsername(authentication);
        log.info("Adding note to application: id={}, author={}", id, author);

        NoteResponse response = applicationNoteService.addNote(id, request, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(RestResponse.created(response));
    }

    @PutMapping("/{id}/notes/{noteId}")
    @PreAuthorize("@applicationSecurity.isNoteAuthor(#id, #noteId, authentication)")
    @Operation(summary = "Update note", description = "Update note content or visibility (author only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Note updated successfully"),
            @ApiResponse(responseCode = "403", description = "Not the note author"),
            @ApiResponse(responseCode = "404", description = "Note not found")
    })
    public ResponseEntity<RestResponse<NoteResponse>> updateNote(
            @PathVariable String id,
            @PathVariable String noteId,
            @Valid @RequestBody UpdateNoteRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Updating note: appId={}, noteId={}, user={}", id, noteId, username);

        NoteResponse response = applicationNoteService.updateNote(id, noteId, request, username);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize("@applicationSecurity.isNoteAuthor(#id, #noteId, authentication)")
    @Operation(summary = "Delete note", description = "Delete a note (author only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Note deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not the note author"),
            @ApiResponse(responseCode = "404", description = "Note not found")
    })
    public ResponseEntity<Void> deleteNote(
            @PathVariable String id,
            @PathVariable String noteId,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Deleting note: appId={}, noteId={}, user={}", id, noteId, username);

        applicationNoteService.deleteNote(id, noteId, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Get all notes for application", description = "Retrieve all notes (HR view)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notes retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<List<NoteResponse>>> getAllNotes(
            @PathVariable String id,
            Authentication authentication) {

        log.debug("Fetching all notes for application: id={}", id);
        List<NoteResponse> notes = applicationNoteService.getAllNotes(id);
        return ResponseEntity.ok(RestResponse.success(notes));
    }

    @GetMapping("/hr/dashboard")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Get HR dashboard statistics",
               description = "Retrieve aggregated metrics for HR dashboard including status distribution, recent applications, and trends")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard stats retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<RestResponse<DashboardStatsResponse>> getDashboardStats(
            @RequestParam(required = false) @Parameter(description = "HR username for filtering") String hrUsername,
            Authentication authentication) {

        String username = hrUsername != null ? hrUsername : applicationSecurity.getCurrentUsername(authentication);
        log.info("Fetching dashboard stats for HR: {}", username);

        DashboardStatsResponse response = applicationStatsService.getDashboardStats(username);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Advanced application search",
               description = "Search applications with multiple filters: date range, job IDs, status, text search")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> searchApplications(
            @RequestParam(required = false) @Parameter(description = "Job IDs to filter (comma-separated)") List<String> jobIds,
            @RequestParam(required = false) @Parameter(description = "Application status") ApplicationStatus status,
            @RequestParam(required = false) @Parameter(description = "From date (ISO-8601)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @Parameter(description = "To date (ISO-8601)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) @Parameter(description = "Text search in cover letter") String searchText,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size") int size,
            Authentication authentication) {

        log.info("Searching applications: jobIds={}, status={}, fromDate={}, toDate={}",
                 jobIds, status, fromDate, toDate);

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result = applicationSearchService.search(
                jobIds, status, fromDate, toDate, searchText, pageable
        );

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @PutMapping("/bulk/status")
    @PreAuthorize("hasAuthority('application:update')")
    @Operation(summary = "Bulk update application status",
               description = "Update status of multiple applications (max 100) in a single transaction. All-or-nothing behavior.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk update completed"),
            @ApiResponse(responseCode = "400", description = "Validation failed or partial failure"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<RestResponse<BulkUpdateResult>> bulkUpdateStatus(
            @Valid @RequestBody BulkUpdateRequest request,
            Authentication authentication) {

        String updatedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info("Bulk updating application status: count={}, status={}, user={}",
                 request.getApplicationIds().size(), request.getStatus(), updatedBy);

        BulkUpdateResult result = bulkOperationService.bulkUpdateStatus(request, updatedBy);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/job/{jobId}/stats")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Get job-specific statistics",
               description = "Retrieve application funnel metrics, conversion rates, and time-to-review for a specific job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job stats retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<RestResponse<JobStatsResponse>> getJobStats(
            @PathVariable @Parameter(description = "Job ID") String jobId,
            Authentication authentication) {

        log.info("Fetching job stats: jobId={}", jobId);
        JobStatsResponse response = jobStatsService.getJobStats(jobId);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    // ==================== Phase 3: HR Manager Role APIs ====================

    @GetMapping("/company/{companyId}")
    @PreAuthorize("@applicationSecurity.isSameCompany(#companyId, authentication)")
    @Operation(summary = "Get company applications",
               description = "Retrieve all applications for a company with optional filters (status, assignedTo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied - not same company")
    })
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getCompanyApplications(
            @PathVariable @Parameter(description = "Company ID") String companyId,
            @RequestParam(required = false) @Parameter(description = "Filter by status") ApplicationStatus status,
            @RequestParam(required = false) @Parameter(description = "Filter by assigned HR") String assignedTo,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size") int size,
            Authentication authentication) {

        log.info("Fetching company applications: companyId={}, status={}, assignedTo={}", companyId, status, assignedTo);

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;

        if (assignedTo != null && status != null) {
            // Filter by both assigned HR and status (not directly supported - use company + assignedTo then filter)
            result = companyApplicationService.getCompanyApplicationsByAssignedHR(companyId, assignedTo, pageable);
        } else if (assignedTo != null) {
            result = companyApplicationService.getCompanyApplicationsByAssignedHR(companyId, assignedTo, pageable);
        } else if (status != null) {
            result = companyApplicationService.getCompanyApplicationsByStatus(companyId, status, pageable);
        } else {
            result = companyApplicationService.getCompanyApplications(companyId, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("@applicationSecurity.canAssign(#id, authentication)")
    @Operation(summary = "Assign application to HR user",
               description = "Assign an application to a specific HR user for review. Sends notification to assignee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or already assigned"),
            @ApiResponse(responseCode = "403", description = "Access denied - not authorized to assign"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> assignApplication(
            @PathVariable String id,
            @Valid @RequestBody AssignApplicationRequest request,
            Authentication authentication) {

        String assignedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info("Assigning application: id={}, assignedTo={}, by={}", id, request.getAssignedTo(), assignedBy);

        ApplicationResponse response = assignmentService.assignApplication(id, request.getAssignedTo(), assignedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @DeleteMapping("/{id}/assign")
    @PreAuthorize("@applicationSecurity.canAssign(#id, authentication)")
    @Operation(summary = "Unassign application",
               description = "Remove assignment from an application (return to common pool)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application unassigned successfully"),
            @ApiResponse(responseCode = "400", description = "Application not currently assigned"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> unassignApplication(
            @PathVariable String id,
            Authentication authentication) {

        String unassignedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info("Unassigning application: id={}, by={}", id, unassignedBy);

        ApplicationResponse response = assignmentService.unassignApplication(id, unassignedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/assigned/{hrUsername}")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = "Get applications assigned to HR user",
               description = "Retrieve all applications assigned to a specific HR user with optional status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assigned applications retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getAssignedApplications(
            @PathVariable @Parameter(description = "HR username") String hrUsername,
            @RequestParam(required = false) @Parameter(description = "Filter by status") ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size") int size,
            Authentication authentication) {

        log.info("Fetching assigned applications: hrUsername={}, status={}", hrUsername, status);

        if(hrUsername.equals("my")) {
            hrUsername =  applicationSecurity.getCurrentUsername(authentication);
        }

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = companyApplicationService.getAssignedApplicationsByStatus(hrUsername, status, pageable);
        } else {
            result = companyApplicationService.getAssignedApplications(hrUsername, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/manager/stats")
    @PreAuthorize("hasAuthority('application:manage')")
    @Operation(summary = "Get manager dashboard statistics",
               description = "Retrieve company-wide metrics including status breakdown, team performance, and top jobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager stats retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied - not a manager")
    })
    public ResponseEntity<RestResponse<ManagerStatsResponse>> getManagerStats(
            @RequestParam @Parameter(description = "Company ID", required = true) String companyId,
            Authentication authentication) {

        log.info("Fetching manager stats for company: {}", companyId);

        // Verify manager belongs to same company
        if (!applicationSecurity.isSameCompany(companyId, authentication)) {
            throw new org.workfitai.applicationservice.exception.ForbiddenException(
                    "You don't have access to this company's statistics");
        }

        ManagerStatsResponse response = managerStatsService.getManagerStats(companyId);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @PostMapping("/export")
    @PreAuthorize("@applicationSecurity.canExport(#request.companyId, authentication)")
    @Operation(summary = "Export applications to CSV",
               description = "Export filtered applications to CSV format (Excel support in Phase 4). Max 10,000 rows.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Export generated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or too many rows"),
            @ApiResponse(responseCode = "403", description = "Access denied - not authorized to export")
    })
    public ResponseEntity<RestResponse<ExportResponse>> exportApplications(
            @Valid @RequestBody ExportRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Exporting applications: companyId={}, format={}, user={}", request.getCompanyId(), request.getFormat(), username);

        ExportResponse response = exportService.exportApplications(request);
        return ResponseEntity.ok(RestResponse.success(response));
    }
}
