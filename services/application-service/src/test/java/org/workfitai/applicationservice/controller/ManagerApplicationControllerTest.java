package org.workfitai.applicationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.applicationservice.dto.request.AssignApplicationRequest;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.CandidateProfileResponse;
import org.workfitai.applicationservice.dto.response.CandidateSummaryResponse;
import org.workfitai.applicationservice.dto.response.CompanyJobSummaryResponse;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.dto.response.HRAuditActivityResponse;
import org.workfitai.applicationservice.dto.response.HRUserResponse;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.AssignmentService;
import org.workfitai.applicationservice.service.CompanyApplicationService;
import org.workfitai.applicationservice.service.CompanyCandidateService;
import org.workfitai.applicationservice.service.CompanyHRService;
import org.workfitai.applicationservice.service.ExportService;
import org.workfitai.applicationservice.service.ManagerStatsService;

@ExtendWith(MockitoExtension.class)
class ManagerApplicationControllerTest {

    @Mock ApplicationSecurity applicationSecurity;
    @Mock CompanyApplicationService companyApplicationService;
    @Mock CompanyCandidateService companyCandidateService;
    @Mock AssignmentService assignmentService;
    @Mock ManagerStatsService managerStatsService;
    @Mock ExportService exportService;
    @Mock CompanyHRService companyHRService;

    @InjectMocks ManagerApplicationController controller;

    private JwtAuthenticationToken managerAuth;
    private ApplicationResponse appResponse;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .claim("sub", "manager1").claim("companyId", "company-1").build();
        managerAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("application:manage")), "manager1");

        appResponse = new ApplicationResponse();
    }

    private ResultPaginationDTO<ApplicationResponse> emptyAppPage() {
        return ResultPaginationDTO.<ApplicationResponse>builder()
                .items(List.of()).meta(ResultPaginationDTO.Meta.builder().build()).build();
    }

    // ─── getCompanyApplications ───────────────────────────────────────────────

    @Test
    void getCompanyApplications_noFilters_delegatesToService() {
        when(companyApplicationService.getCompanyApplicationsWithFilters(
                eq("company-1"), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(emptyAppPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getCompanyApplications("company-1", null, null, null, null, 0, 20, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(companyApplicationService).getCompanyApplicationsWithFilters(
                eq("company-1"), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void getCompanyApplications_withFilters_passesFilters() {
        when(companyApplicationService.getCompanyApplicationsWithFilters(
                eq("company-1"), eq(ApplicationStatus.APPLIED), eq("hr1"), eq("Engineer"), isNull(), any()))
                .thenReturn(emptyAppPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getCompanyApplications(
                        "company-1", ApplicationStatus.APPLIED, "hr1", "Engineer", null, 0, 20, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── assignApplication ────────────────────────────────────────────────────

    @Test
    void assignApplication_callsServiceAndReturnsResponse() {
        when(applicationSecurity.getCurrentUsername(managerAuth)).thenReturn("manager1");
        AssignApplicationRequest req = AssignApplicationRequest.builder().assignedTo("hr1").build();
        when(assignmentService.assignApplication("app-1", "hr1", "manager1")).thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.assignApplication("app-1", req, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(appResponse);
        verify(assignmentService).assignApplication("app-1", "hr1", "manager1");
    }

    // ─── unassignApplication ──────────────────────────────────────────────────

    @Test
    void unassignApplication_callsServiceAndReturnsResponse() {
        when(applicationSecurity.getCurrentUsername(managerAuth)).thenReturn("manager1");
        when(assignmentService.unassignApplication("app-1", "manager1")).thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.unassignApplication("app-1", managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(assignmentService).unassignApplication("app-1", "manager1");
    }

    // ─── getManagerStats ──────────────────────────────────────────────────────

    @Test
    void getManagerStats_returnsStats() {
        ManagerStatsResponse stats = ManagerStatsResponse.builder()
                .totalApplications(50L).build();
        when(managerStatsService.getManagerStats("company-1")).thenReturn(stats);

        ResponseEntity<RestResponse<ManagerStatsResponse>> resp =
                controller.getManagerStats("company-1", managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getTotalApplications()).isEqualTo(50L);
    }

    // ─── exportApplications ───────────────────────────────────────────────────

    @Test
    void exportApplications_returnsExportResponse() {
        when(applicationSecurity.getCurrentUsername(managerAuth)).thenReturn("manager1");
        ExportRequest req = ExportRequest.builder().companyId("company-1").format("CSV").build();
        ExportResponse exportResp = ExportResponse.builder().downloadUrl("http://minio/export.csv").build();
        when(exportService.exportApplications(req)).thenReturn(exportResp);

        ResponseEntity<RestResponse<ExportResponse>> resp =
                controller.exportApplications(req, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getDownloadUrl()).contains("export.csv");
    }

    // ─── getCompanyHRUsers ────────────────────────────────────────────────────

    @Test
    void getCompanyHRUsers_returnsHrList() {
        HRUserResponse hr = HRUserResponse.builder().username("hr1").email("hr1@co.com").build();
        when(companyHRService.getCompanyHRUsers("company-1")).thenReturn(List.of(hr));

        ResponseEntity<RestResponse<List<HRUserResponse>>> resp =
                controller.getCompanyHRUsers("company-1", managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).hasSize(1);
        assertThat(resp.getBody().getData().get(0).username()).isEqualTo("hr1");
    }

    // ─── getCompanyHRAuditActivities ──────────────────────────────────────────

    @Test
    void getCompanyHRAuditActivities_returnsPagedActivities() {
        HRAuditActivityResponse activity = HRAuditActivityResponse.builder()
                .performedBy("hr1").action("STATUS_UPDATED").build();
        when(companyHRService.getCompanyHRAuditActivities(
                eq("company-1"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(activity)));

        ResponseEntity<RestResponse<ResultPaginationDTO<HRAuditActivityResponse>>> resp =
                controller.getCompanyHRAuditActivities("company-1", null, null, 0, 20, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getItems()).hasSize(1);
    }

    // ─── getCompanyCandidates ─────────────────────────────────────────────────

    @Test
    void getCompanyCandidates_returnsCandidateList() {
        ResultPaginationDTO<CandidateSummaryResponse> page =
                ResultPaginationDTO.<CandidateSummaryResponse>builder()
                        .items(List.of()).meta(ResultPaginationDTO.Meta.builder().build()).build();
        when(companyCandidateService.getCandidateList(eq("company-1"), isNull(), isNull(), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<CandidateSummaryResponse>>> resp =
                controller.getCompanyCandidates("company-1", null, null, 0, 20, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── getCandidateProfile ──────────────────────────────────────────────────

    @Test
    void getCandidateProfile_returnsProfile() {
        CandidateProfileResponse profile = CandidateProfileResponse.builder()
                .username("user1").build();
        when(companyCandidateService.getCandidateProfile("company-1", "user1")).thenReturn(profile);

        ResponseEntity<RestResponse<CandidateProfileResponse>> resp =
                controller.getCandidateProfile("company-1", "user1", managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getUsername()).isEqualTo("user1");
    }

    // ─── getCompanyJobs ───────────────────────────────────────────────────────

    @Test
    void getCompanyJobs_returnsJobList() {
        ResultPaginationDTO<CompanyJobSummaryResponse> page =
                ResultPaginationDTO.<CompanyJobSummaryResponse>builder()
                        .items(List.of()).meta(ResultPaginationDTO.Meta.builder().build()).build();
        when(companyCandidateService.getJobsWithStats(eq("company-1"), isNull(), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<CompanyJobSummaryResponse>>> resp =
                controller.getCompanyJobs("company-1", null, 0, 20, managerAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
