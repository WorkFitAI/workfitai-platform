package org.workfitai.applicationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.applicationservice.dto.request.BulkUpdateRequest;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.dto.response.CandidateProfileResponse;
import org.workfitai.applicationservice.dto.response.CandidateSummaryResponse;
import org.workfitai.applicationservice.dto.response.DashboardStatsResponse;
import org.workfitai.applicationservice.dto.response.JobStatsResponse;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.response.CompanyJobSummaryResponse;
import org.workfitai.applicationservice.dto.response.CvRankingResultResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.ApplicationNoteService;
import org.workfitai.applicationservice.service.ApplicationSearchService;
import org.workfitai.applicationservice.service.ApplicationStatsService;
import org.workfitai.applicationservice.service.BulkOperationService;
import org.workfitai.applicationservice.service.CompanyApplicationService;
import org.workfitai.applicationservice.service.CompanyCandidateService;
import org.workfitai.applicationservice.service.CvRankingService;
import org.workfitai.applicationservice.service.IApplicationService;
import org.workfitai.applicationservice.service.JobStatsService;
import org.workfitai.applicationservice.service.MinioPreSignedUrlService;

@ExtendWith(MockitoExtension.class)
class HRApplicationControllerTest {

    @Mock IApplicationService applicationService;
    @Mock ApplicationSecurity applicationSecurity;
    @Mock MinioPreSignedUrlService minioPreSignedUrlService;
    @Mock ApplicationNoteService applicationNoteService;
    @Mock ApplicationStatsService applicationStatsService;
    @Mock ApplicationSearchService applicationSearchService;
    @Mock BulkOperationService bulkOperationService;
    @Mock JobStatsService jobStatsService;
    @Mock CompanyApplicationService companyApplicationService;
    @Mock CompanyCandidateService companyCandidateService;
    @Mock CvRankingService cvRankingService;

    @InjectMocks HRApplicationController controller;

    private JwtAuthenticationToken hrAuth;
    private JwtAuthenticationToken hrWithCompanyAuth;
    private ApplicationResponse appResponse;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .claim("sub", "hr1").build();
        hrAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("application:review")), "hr1");

        Jwt jwtWithCompany = Jwt.withTokenValue("t2").header("alg", "none")
                .claim("sub", "hr1").claim("companyId", "company-1").build();
        hrWithCompanyAuth = new JwtAuthenticationToken(jwtWithCompany,
                List.of(new SimpleGrantedAuthority("application:review")), "hr1");

        appResponse = new ApplicationResponse();
    }

    private ResultPaginationDTO<ApplicationResponse> emptyPage() {
        return ResultPaginationDTO.<ApplicationResponse>builder()
                .items(List.of())
                .meta(ResultPaginationDTO.Meta.builder().build())
                .build();
    }

    // ─── getApplicationsByJob ─────────────────────────────────────────────────

    @Test
    void getApplicationsByJob_noStatus_returnsPage() {
        when(applicationService.getApplicationsByJob(eq("job-1"), any())).thenReturn(emptyPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getApplicationsByJob("job-1", null, 0, 10);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationService).getApplicationsByJob(eq("job-1"), any());
    }

    @Test
    void getApplicationsByJob_withStatus_delegatesToGetByStatus() {
        when(applicationService.getApplicationsByJobAndStatus(eq("job-1"), eq(ApplicationStatus.APPLIED), any()))
                .thenReturn(emptyPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getApplicationsByJob("job-1", ApplicationStatus.APPLIED, 0, 10);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationService).getApplicationsByJobAndStatus(eq("job-1"), eq(ApplicationStatus.APPLIED), any());
    }

    // ─── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_callsServiceAndReturnsResponse() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        when(applicationService.updateStatus("app-1", ApplicationStatus.REVIEWING, "hr1"))
                .thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.updateStatus("app-1", ApplicationStatus.REVIEWING, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(appResponse);
    }

    // ─── getJobApplicationCount ───────────────────────────────────────────────

    @Test
    void getJobApplicationCount_returnsCount() {
        when(applicationService.countByJob("job-1")).thenReturn(42L);

        ResponseEntity<RestResponse<Map<String, Long>>> resp =
                controller.getJobApplicationCount("job-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().get("count")).isEqualTo(42L);
    }

    // ─── addNote ──────────────────────────────────────────────────────────────

    @Test
    void addNote_returns201WithNote() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        NoteResponse note = NoteResponse.builder().id("n1").author("hr1").content("Good").build();
        CreateNoteRequest req = CreateNoteRequest.builder()
                .content("Good").candidateVisible(false).build();
        when(applicationNoteService.addNote(eq("app-1"), eq(req), eq("hr1"))).thenReturn(note);

        ResponseEntity<RestResponse<NoteResponse>> resp =
                controller.addNote("app-1", req, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().getData().getId()).isEqualTo("n1");
    }

    // ─── getDashboardStats ────────────────────────────────────────────────────

    @Test
    void getDashboardStats_noHrUsername_usesCurrentUser() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        DashboardStatsResponse stats = DashboardStatsResponse.builder().build();
        when(applicationStatsService.getDashboardStats("hr1")).thenReturn(stats);

        ResponseEntity<RestResponse<DashboardStatsResponse>> resp =
                controller.getDashboardStats(null, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getDashboardStats_withHrUsername_usesProvidedUsername() {
        DashboardStatsResponse stats = DashboardStatsResponse.builder().build();
        when(applicationStatsService.getDashboardStats("other_hr")).thenReturn(stats);

        ResponseEntity<RestResponse<DashboardStatsResponse>> resp =
                controller.getDashboardStats("other_hr", hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationStatsService).getDashboardStats("other_hr");
    }

    // ─── searchApplications ───────────────────────────────────────────────────

    @Test
    void searchApplications_nonAdmin_scopesToCompany() {
        when(applicationSecurity.isAdmin(hrWithCompanyAuth)).thenReturn(false);
        when(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).thenReturn("company-1");
        when(applicationSearchService.search(isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("company-1"), any())).thenReturn(emptyPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.searchApplications(null, null, null, null, null, 0, 20, hrWithCompanyAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchApplications_admin_passesNullCompanyId() {
        when(applicationSecurity.isAdmin(hrAuth)).thenReturn(true);
        when(applicationSearchService.search(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), any())).thenReturn(emptyPage());

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.searchApplications(null, null, null, null, null, 0, 20, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationSearchService).search(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), any());
    }

    // ─── bulkUpdateStatus ─────────────────────────────────────────────────────

    @Test
    void bulkUpdateStatus_hrWithCompany_passesCompanyId() {
        when(applicationSecurity.getCurrentUsername(hrWithCompanyAuth)).thenReturn("hr1");
        when(applicationSecurity.isAdmin(hrWithCompanyAuth)).thenReturn(false);
        when(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).thenReturn("company-1");
        BulkUpdateResult result = BulkUpdateResult.builder().successCount(2).failureCount(0).build();
        BulkUpdateRequest req = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1", "app-2"))
                .status(ApplicationStatus.REVIEWING).build();
        when(bulkOperationService.bulkUpdateStatus(eq(req), eq("hr1"), eq("company-1"))).thenReturn(result);

        ResponseEntity<RestResponse<BulkUpdateResult>> resp =
                controller.bulkUpdateStatus(req, hrWithCompanyAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getSuccessCount()).isEqualTo(2);
    }

    @Test
    void bulkUpdateStatus_admin_passesNullCompanyId() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("admin1");
        when(applicationSecurity.isAdmin(hrAuth)).thenReturn(true);
        BulkUpdateResult result = BulkUpdateResult.builder().successCount(1).failureCount(0).build();
        BulkUpdateRequest req = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1"))
                .status(ApplicationStatus.REVIEWING).build();
        when(bulkOperationService.bulkUpdateStatus(eq(req), eq("admin1"), isNull())).thenReturn(result);

        ResponseEntity<RestResponse<BulkUpdateResult>> resp =
                controller.bulkUpdateStatus(req, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── getJobStats ──────────────────────────────────────────────────────────

    @Test
    void getJobStats_returnsStats() {
        JobStatsResponse stats = JobStatsResponse.builder().totalApplications(5L).build();
        when(jobStatsService.getJobStats("job-1")).thenReturn(stats);

        ResponseEntity<RestResponse<JobStatsResponse>> resp = controller.getJobStats("job-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getTotalApplications()).isEqualTo(5L);
    }

    // ─── getHRCandidates ──────────────────────────────────────────────────────

    @Test
    void getHRCandidates_withCompanyId_returnsCandidates() {
        when(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).thenReturn("company-1");
        ResultPaginationDTO<CandidateSummaryResponse> page =
                ResultPaginationDTO.<CandidateSummaryResponse>builder()
                        .items(List.of()).meta(ResultPaginationDTO.Meta.builder().build()).build();
        when(companyCandidateService.getCandidateList(eq("company-1"), isNull(), isNull(), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<CandidateSummaryResponse>>> resp =
                controller.getHRCandidates(null, null, 0, 20, hrWithCompanyAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getHRCandidates_nullCompanyId_throwsForbidden() {
        when(applicationSecurity.getCurrentCompanyId(hrAuth)).thenReturn(null);

        assertThatThrownBy(() -> controller.getHRCandidates(null, null, 0, 20, hrAuth))
                .isInstanceOf(ForbiddenException.class);
    }

    // ─── getHRCandidateProfile ────────────────────────────────────────────────

    @Test
    void getHRCandidateProfile_withCompanyId_returnsCandidateProfile() {
        when(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).thenReturn("company-1");
        CandidateProfileResponse profile = CandidateProfileResponse.builder()
                .username("user1").build();
        when(companyCandidateService.getCandidateProfile("company-1", "user1")).thenReturn(profile);

        ResponseEntity<RestResponse<CandidateProfileResponse>> resp =
                controller.getHRCandidateProfile("user1", hrWithCompanyAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getUsername()).isEqualTo("user1");
    }

    @Test
    void getHRCandidateProfile_nullCompanyId_throwsForbidden() {
        when(applicationSecurity.getCurrentCompanyId(hrAuth)).thenReturn(null);

        assertThatThrownBy(() -> controller.getHRCandidateProfile("user1", hrAuth))
                .isInstanceOf(ForbiddenException.class);
    }

    // ─── downloadCv ───────────────────────────────────────────────────────────

    @Test
    void downloadCv_withContentType_usesStoredMediaType() {
        ApplicationResponse app = new ApplicationResponse();
        app.setCvFileUrl("http://minio:9000/cvs-files/user1/app1/cv.pdf");
        app.setCvContentType("application/pdf");
        app.setCvFileName("cv.pdf");

        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        when(applicationService.getApplicationById("app-1")).thenReturn(app);
        when(minioPreSignedUrlService.extractObjectKey("http://minio:9000/cvs-files/user1/app1/cv.pdf"))
                .thenReturn("user1/app1/cv.pdf");

        ResponseEntity<?> resp = controller.downloadCv("app-1", hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("application/pdf");
    }

    @Test
    void downloadCv_nullContentType_fallsBackToApplicationPdf() {
        ApplicationResponse app = new ApplicationResponse();
        app.setCvFileUrl("http://minio:9000/cvs-files/user1/app1/cv.pdf");
        app.setCvContentType(null);
        app.setCvFileName("cv.pdf");

        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        when(applicationService.getApplicationById("app-1")).thenReturn(app);
        when(minioPreSignedUrlService.extractObjectKey("http://minio:9000/cvs-files/user1/app1/cv.pdf"))
                .thenReturn("user1/app1/cv.pdf");

        ResponseEntity<?> resp = controller.downloadCv("app-1", hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("application/pdf");
    }

    // ─── updateNote / deleteNote / getAllNotes ────────────────────────────────

    @Test
    void updateNote_returnsUpdatedNote() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        NoteResponse updated = NoteResponse.builder().id("n1").content("Updated").build();
        UpdateNoteRequest req = UpdateNoteRequest.builder().content("Updated").build();
        when(applicationNoteService.updateNote("app-1", "n1", req, "hr1")).thenReturn(updated);

        ResponseEntity<RestResponse<NoteResponse>> resp =
                controller.updateNote("app-1", "n1", req, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getContent()).isEqualTo("Updated");
    }

    @Test
    void deleteNote_returnsNoContent() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");

        ResponseEntity<Void> resp = controller.deleteNote("app-1", "n1", hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getAllNotes_returnsNoteList() {
        NoteResponse note = NoteResponse.builder().id("n1").content("Test").build();
        when(applicationNoteService.getAllNotes("app-1")).thenReturn(List.of(note));

        ResponseEntity<RestResponse<List<NoteResponse>>> resp = controller.getAllNotes("app-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).hasSize(1);
    }

    // ─── getMyAssignedApplications / getAssignedApplications ─────────────────

    @Test
    void getMyAssignedApplications_usesCurrentHrUsername() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        ResultPaginationDTO<ApplicationResponse> page = emptyPage();
        when(companyApplicationService.getAssignedApplicationsWithFilters(
                eq("hr1"), isNull(), isNull(), isNull(), any())).thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getMyAssignedApplications(null, null, null, 0, 20, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(companyApplicationService).getAssignedApplicationsWithFilters(
                eq("hr1"), isNull(), isNull(), isNull(), any());
    }

    @Test
    void getAssignedApplications_usesPathVariableUsername() {
        ResultPaginationDTO<ApplicationResponse> page = emptyPage();
        when(companyApplicationService.getAssignedApplicationsWithFilters(
                eq("hr2"), isNull(), isNull(), isNull(), any())).thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getAssignedApplications("hr2", null, null, null, 0, 20, hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── getHRJobs ────────────────────────────────────────────────────────────

    @Test
    void getHRJobs_nullCompanyId_throwsForbidden() {
        when(applicationSecurity.getCurrentCompanyId(hrAuth)).thenReturn(null);

        assertThatThrownBy(() -> controller.getHRJobs(null, 0, 20, hrAuth))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getHRJobs_withCompanyId_returnsJobList() {
        when(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).thenReturn("company-1");
        ResultPaginationDTO<CompanyJobSummaryResponse> page =
                ResultPaginationDTO.<CompanyJobSummaryResponse>builder()
                        .items(List.of())
                        .meta(ResultPaginationDTO.Meta.builder().build())
                        .build();
        when(companyCandidateService.getJobsWithStats(eq("company-1"), isNull(), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<CompanyJobSummaryResponse>>> resp =
                controller.getHRJobs(null, 0, 20, hrWithCompanyAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── rankCvsByJob ─────────────────────────────────────────────────────────

    @Test
    void rankCvsByJob_delegatesAndReturnsResult() {
        when(applicationSecurity.getCurrentUsername(hrAuth)).thenReturn("hr1");
        CvRankingResultResponse rankResult = CvRankingResultResponse.builder()
                .jobId("job-1").rankedCount(2).unrankedCount(1).build();
        when(cvRankingService.rankCvsByJob("job-1", "hr1")).thenReturn(rankResult);

        ResponseEntity<RestResponse<CvRankingResultResponse>> resp =
                controller.rankCvsByJob("job-1", hrAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getRankedCount()).isEqualTo(2);
    }
}
