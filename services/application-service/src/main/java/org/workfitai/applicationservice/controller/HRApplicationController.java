package org.workfitai.applicationservice.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.dto.request.BulkUpdateRequest;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.dto.response.CandidateProfileResponse;
import org.workfitai.applicationservice.dto.response.CandidateSummaryResponse;
import org.workfitai.applicationservice.dto.response.CompanyJobSummaryResponse;
import org.workfitai.applicationservice.dto.response.DashboardStatsResponse;
import org.workfitai.applicationservice.dto.response.JobStatsResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.FileStorageException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.ApplicationNoteService;
import org.workfitai.applicationservice.service.ApplicationSearchService;
import org.workfitai.applicationservice.service.ApplicationStatsService;
import org.workfitai.applicationservice.service.BulkOperationService;
import org.workfitai.applicationservice.service.CompanyApplicationService;
import org.workfitai.applicationservice.service.CompanyCandidateService;
import org.workfitai.applicationservice.service.IApplicationService;
import org.workfitai.applicationservice.service.JobStatsService;
import org.workfitai.applicationservice.service.MinioPreSignedUrlService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HR-facing application endpoints.
 * Covers: job-level views, status updates, notes, search, bulk ops, CV download,
 * dashboard stats, and assigned-application queries.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "HR Application Management", description = "HR endpoints for reviewing and managing applications")
@SecurityRequirement(name = "bearerAuth")
public class HRApplicationController {

    private final IApplicationService applicationService;
    private final ApplicationSecurity applicationSecurity;
    private final MinioPreSignedUrlService minioPreSignedUrlService;
    private final ApplicationNoteService applicationNoteService;
    private final ApplicationStatsService applicationStatsService;
    private final ApplicationSearchService applicationSearchService;
    private final BulkOperationService bulkOperationService;
    private final JobStatsService jobStatsService;
    private final CompanyApplicationService companyApplicationService;
    private final CompanyCandidateService companyCandidateService;

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getApplicationsByJob(
            @PathVariable @Parameter(description = "Job ID") String jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        ResultPaginationDTO<ApplicationResponse> result = (status != null)
                ? applicationService.getApplicationsByJobAndStatus(jobId, status, pageable)
                : applicationService.getApplicationsByJob(jobId, pageable);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@applicationSecurity.canUpdateStatus(#id, authentication)")
    public ResponseEntity<RestResponse<ApplicationResponse>> updateStatus(
            @PathVariable String id,
            @RequestParam ApplicationStatus status,
            Authentication authentication) {

        String updatedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info("Updating status for application: id={}, status={}", id, status);
        ApplicationResponse response = applicationService.updateStatus(id, status, updatedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/job/{jobId}/count")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<java.util.Map<String, Long>>> getJobApplicationCount(
            @PathVariable String jobId) {
        return ResponseEntity.ok(RestResponse.success(java.util.Map.of("count", applicationService.countByJob(jobId))));
    }

    /**
     * Download the CV attached to an application.
     *
     * Access rules:
     *   HR / HR_MANAGER / ADMIN — can download any application's CV (has application:review).
     *   CANDIDATE               — can download only their own application's CV (is the owner).
     */
    @GetMapping("/{id}/cv/download")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    public ResponseEntity<org.springframework.core.io.Resource> downloadCv(
            @PathVariable String id,
            Authentication authentication) {

        log.info("Downloading CV for application: id={}, requester={}", id,
                applicationSecurity.getCurrentUsername(authentication));

        ApplicationResponse application = applicationService.getApplicationById(id);
        String objectKey = minioPreSignedUrlService.extractObjectKey(application.getCvFileUrl());

        try {
            byte[] fileData = minioPreSignedUrlService.downloadFile(objectKey);

            // Use stored content type; fall back to application/pdf (enforced at upload)
            org.springframework.http.MediaType mediaType = application.getCvContentType() != null
                    ? org.springframework.http.MediaType.parseMediaType(application.getCvContentType())
                    : org.springframework.http.MediaType.APPLICATION_PDF;

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + application.getCvFileName() + "\"")
                    .contentType(mediaType)
                    .contentLength(fileData.length)
                    .body(new org.springframework.core.io.ByteArrayResource(fileData));
        } catch (Exception e) {
            log.error("Failed to download CV for application {}: {}", id, e.getMessage(), e);
            throw new FileStorageException("Failed to download CV file: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('application:note')")
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
    public ResponseEntity<RestResponse<NoteResponse>> updateNote(
            @PathVariable String id,
            @PathVariable String noteId,
            @Valid @RequestBody UpdateNoteRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        NoteResponse response = applicationNoteService.updateNote(id, noteId, request, username);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize("@applicationSecurity.isNoteAuthor(#id, #noteId, authentication)")
    public ResponseEntity<Void> deleteNote(
            @PathVariable String id,
            @PathVariable String noteId,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        applicationNoteService.deleteNote(id, noteId, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<List<NoteResponse>>> getAllNotes(
            @PathVariable String id) {
        return ResponseEntity.ok(RestResponse.success(applicationNoteService.getAllNotes(id)));
    }

    @GetMapping("/hr/dashboard")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<DashboardStatsResponse>> getDashboardStats(
            @RequestParam(required = false) String hrUsername,
            Authentication authentication) {

        String username = hrUsername != null ? hrUsername
                : applicationSecurity.getCurrentUsername(authentication);
        DashboardStatsResponse response = applicationStatsService.getDashboardStats(username);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> searchApplications(
            @RequestParam(required = false) List<String> jobIds,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) String searchText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Scope search to caller's company — admin passes null to bypass (C2 fix)
        String companyId = applicationSecurity.isAdmin(authentication)
                ? null
                : applicationSecurity.getCurrentCompanyId(authentication);

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        ResultPaginationDTO<ApplicationResponse> result = applicationSearchService.search(
                jobIds, status, fromDate, toDate, searchText, companyId, pageable);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    @PutMapping("/bulk/status")
    @PreAuthorize("hasAuthority('application:update')")
    public ResponseEntity<RestResponse<BulkUpdateResult>> bulkUpdateStatus(
            @Valid @RequestBody BulkUpdateRequest request,
            Authentication authentication) {

        String updatedBy = applicationSecurity.getCurrentUsername(authentication);
        BulkUpdateResult result = bulkOperationService.bulkUpdateStatus(request, updatedBy);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/job/{jobId}/stats")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<JobStatsResponse>> getJobStats(@PathVariable String jobId) {
        return ResponseEntity.ok(RestResponse.success(jobStatsService.getJobStats(jobId)));
    }

    @GetMapping("/assigned/{hrUsername}")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getAssignedApplications(
            @PathVariable String hrUsername,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        if ("my".equals(hrUsername)) {
            hrUsername = applicationSecurity.getCurrentUsername(authentication);
        }
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        ResultPaginationDTO<ApplicationResponse> result = companyApplicationService
                .getAssignedApplicationsWithFilters(hrUsername, status, fromDate, toDate, pageable);
        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/hr/candidates")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<CandidateSummaryResponse>>> getHRCandidates(
            @RequestParam(required = false) @Parameter(description = "Search by username or email") String search,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        String companyId = applicationSecurity.getCurrentCompanyId(authentication);
        if (companyId == null) {
            throw new ForbiddenException("No company associated with this account");
        }
        log.info("HR candidate list: companyId={}, search={}, status={}", companyId, search, status);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(RestResponse.success(
                companyCandidateService.getCandidateList(companyId, search, status, pageable)));
    }

    @GetMapping("/hr/candidates/{username}")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<CandidateProfileResponse>> getHRCandidateProfile(
            @PathVariable @Parameter(description = "Candidate username") String username,
            Authentication authentication) {

        String companyId = applicationSecurity.getCurrentCompanyId(authentication);
        if (companyId == null) {
            throw new ForbiddenException("No company associated with this account");
        }
        log.info("HR candidate profile: companyId={}, username={}", companyId, username);
        return ResponseEntity.ok(RestResponse.success(
                companyCandidateService.getCandidateProfile(companyId, username)));
    }

    @GetMapping("/hr/jobs")
    @PreAuthorize("hasAuthority('application:review')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<CompanyJobSummaryResponse>>> getHRJobs(
            @RequestParam(required = false) @Parameter(description = "Filter by job title") String jobTitle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        String companyId = applicationSecurity.getCurrentCompanyId(authentication);
        if (companyId == null) {
            throw new ForbiddenException("No company associated with this account");
        }
        log.info("HR jobs with stats: companyId={}, jobTitle={}", companyId, jobTitle);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(RestResponse.success(
                companyCandidateService.getJobsWithStats(companyId, jobTitle, pageable)));
    }
}
