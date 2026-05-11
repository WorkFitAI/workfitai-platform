package org.workfitai.applicationservice.controller;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.workfitai.applicationservice.dto.request.AssignApplicationRequest;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.dto.response.HRAuditActivityResponse;
import org.workfitai.applicationservice.dto.response.HRUserResponse;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.AssignmentService;
import org.workfitai.applicationservice.service.CompanyApplicationService;
import org.workfitai.applicationservice.service.CompanyHRService;
import org.workfitai.applicationservice.service.ExportService;
import org.workfitai.applicationservice.service.ManagerStatsService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Manager-facing application endpoints.
 * Covers: company-level views, HR assignment, manager stats, export, and HR audit.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Manager Application Management", description = "Manager/Admin endpoints for company-level application oversight")
@SecurityRequirement(name = "bearerAuth")
public class ManagerApplicationController {

    private final ApplicationSecurity applicationSecurity;
    private final CompanyApplicationService companyApplicationService;
    private final AssignmentService assignmentService;
    private final ManagerStatsService managerStatsService;
    private final ExportService exportService;
    private final CompanyHRService companyHRService;

    @GetMapping("/company/{companyId}")
    @PreAuthorize("@applicationSecurity.isSameCompany(#companyId, authentication)")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getCompanyApplications(
            @PathVariable @Parameter(description = "Company ID") String companyId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("Fetching company applications: companyId={}, status={}, assignedTo={}", companyId, status, assignedTo);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (assignedTo != null && status != null) {
            result = companyApplicationService.getCompanyApplicationsByAssignedHRAndStatus(companyId, assignedTo, status, pageable);
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
    public ResponseEntity<RestResponse<ApplicationResponse>> unassignApplication(
            @PathVariable String id,
            Authentication authentication) {

        String unassignedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info("Unassigning application: id={}, by={}", id, unassignedBy);
        ApplicationResponse response = assignmentService.unassignApplication(id, unassignedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/manager/stats")
    @PreAuthorize("hasAuthority('application:manage')")
    public ResponseEntity<RestResponse<ManagerStatsResponse>> getManagerStats(
            @RequestParam @Parameter(description = "Company ID", required = true) String companyId,
            Authentication authentication) {

        log.info("Fetching manager stats for company: {}", companyId);
        if (!applicationSecurity.isSameCompany(companyId, authentication)) {
            throw new ForbiddenException("You don't have access to this company's statistics");
        }
        ManagerStatsResponse response = managerStatsService.getManagerStats(companyId);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @PostMapping("/export")
    @PreAuthorize("@applicationSecurity.canExport(#request.companyId, authentication)")
    public ResponseEntity<RestResponse<ExportResponse>> exportApplications(
            @Valid @RequestBody ExportRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info("Exporting applications: companyId={}, format={}, user={}", request.getCompanyId(), request.getFormat(), username);
        ExportResponse response = exportService.exportApplications(request);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/company/{companyId}/hr-users")
    @PreAuthorize("hasAuthority('application:manage')")
    public ResponseEntity<RestResponse<List<HRUserResponse>>> getCompanyHRUsers(
            @PathVariable @Parameter(description = "Company ID") String companyId,
            Authentication authentication) {

        log.info("Fetching HR users for company: {}", companyId);
        if (!applicationSecurity.isSameCompany(companyId, authentication)) {
            throw new ForbiddenException("Access denied. You are not authorized to view HR users for this company.");
        }
        List<HRUserResponse> hrUsers = companyHRService.getCompanyHRUsers(companyId);
        return ResponseEntity.ok(RestResponse.success(hrUsers));
    }

    @GetMapping("/company/{companyId}/hr-activities")
    @PreAuthorize("hasAuthority('application:manage')")
    public ResponseEntity<RestResponse<ResultPaginationDTO<HRAuditActivityResponse>>> getCompanyHRAuditActivities(
            @PathVariable @Parameter(description = "Company ID") String companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("Fetching HR audit activities: companyId={}, fromDate={}, toDate={}", companyId, fromDate, toDate);
        if (!applicationSecurity.isSameCompany(companyId, authentication)) {
            throw new ForbiddenException("Access denied. You are not authorized to view audit activities for this company.");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt"));
        Page<HRAuditActivityResponse> activities = companyHRService.getCompanyHRAuditActivities(
                companyId, fromDate, toDate, pageable);

        ResultPaginationDTO<HRAuditActivityResponse> result = ResultPaginationDTO.<HRAuditActivityResponse>builder()
                .items(activities.getContent())
                .meta(ResultPaginationDTO.Meta.builder()
                        .page(activities.getNumber())
                        .size(activities.getSize())
                        .totalElements(activities.getTotalElements())
                        .totalPages(activities.getTotalPages())
                        .first(activities.isFirst())
                        .last(activities.isLast())
                        .hasNext(activities.hasNext())
                        .hasPrevious(activities.hasPrevious())
                        .build())
                .build();
        return ResponseEntity.ok(RestResponse.success(result));
    }
}
