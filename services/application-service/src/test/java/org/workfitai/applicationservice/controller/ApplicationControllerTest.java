package org.workfitai.applicationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.dto.response.StatusChangeResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.saga.ApplicationSagaOrchestrator;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.IApplicationService;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock IApplicationService applicationService;
    @Mock ApplicationSagaOrchestrator sagaOrchestrator;
    @Mock ApplicationSecurity applicationSecurity;

    @InjectMocks ApplicationController controller;

    private JwtAuthenticationToken candidateAuth;
    private ApplicationResponse applicationResponse;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "candidate1")
                .build();
        candidateAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("application:create"),
                        new SimpleGrantedAuthority("application:read"),
                        new SimpleGrantedAuthority("application:list")),
                "candidate1");

        applicationResponse = new ApplicationResponse();
    }

    // ─── createApplication ────────────────────────────────────────────────────

    @Test
    void createApplication_validRequest_returns201WithBody() {
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[]{1});
        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .jobId("job-1").email("c@example.com").cvPdfFile(cv).build();

        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        when(sagaOrchestrator.createApplication(request, "candidate1")).thenReturn(applicationResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.createApplication(request, candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().getData()).isEqualTo(applicationResponse);
    }

    // ─── getMyApplications ────────────────────────────────────────────────────

    @Test
    void getMyApplications_noStatus_callsGetMyApplications() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        ResultPaginationDTO<ApplicationResponse> page = ResultPaginationDTO.<ApplicationResponse>builder()
                .items(List.of(applicationResponse))
                .meta(ResultPaginationDTO.Meta.builder()
                        .page(0).size(10).totalElements(1).totalPages(1)
                        .first(true).last(true).hasNext(false).hasPrevious(false).build())
                .build();
        when(applicationService.getMyApplications(eq("candidate1"), any())).thenReturn(page);

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getMyApplications(null, 0, 10, candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getItems()).hasSize(1);
    }

    @Test
    void getMyApplications_withStatus_callsGetMyApplicationsByStatus() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        ResultPaginationDTO<ApplicationResponse> empty = ResultPaginationDTO.<ApplicationResponse>builder()
                .items(List.of())
                .meta(ResultPaginationDTO.Meta.builder()
                        .page(0).size(10).totalElements(0).totalPages(0)
                        .first(true).last(true).hasNext(false).hasPrevious(false).build())
                .build();
        when(applicationService.getMyApplicationsByStatus(eq("candidate1"), eq(ApplicationStatus.APPLIED), any()))
                .thenReturn(empty);

        ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> resp =
                controller.getMyApplications(ApplicationStatus.APPLIED, 0, 10, candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationService).getMyApplicationsByStatus(eq("candidate1"), eq(ApplicationStatus.APPLIED), any());
    }

    @Test
    void getMyApplications_sizeClampedToMax() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        when(applicationService.getMyApplications(eq("candidate1"), any()))
                .thenReturn(ResultPaginationDTO.<ApplicationResponse>builder()
                        .items(List.of()).meta(ResultPaginationDTO.Meta.builder().build()).build());

        // size=200 exceeds MAX_PAGE_SIZE=100 — should be clamped
        controller.getMyApplications(null, 0, 200, candidateAuth);

        verify(applicationService).getMyApplications(eq("candidate1"),
                argThat(p -> p.getPageSize() == 100));
    }

    // ─── getApplication ───────────────────────────────────────────────────────

    @Test
    void getApplication_found_returns200() {
        when(applicationService.getApplicationById("app-1")).thenReturn(applicationResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.getApplication("app-1", candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(applicationResponse);
    }

    // ─── withdrawApplication ──────────────────────────────────────────────────

    @Test
    void withdrawApplication_returns204() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        doNothing().when(applicationService).withdrawApplication("app-1", "candidate1");

        ResponseEntity<Void> resp = controller.withdrawApplication("app-1", candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(applicationService).withdrawApplication("app-1", "candidate1");
    }

    // ─── hasApplied ───────────────────────────────────────────────────────────

    @Test
    void hasApplied_returns200WithAppliedTrue() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        when(applicationService.hasUserAppliedToJob("candidate1", "job-1")).thenReturn(true);

        ResponseEntity<RestResponse<Map<String, Boolean>>> resp =
                controller.hasApplied("job-1", candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().get("applied")).isTrue();
    }

    @Test
    void hasApplied_notApplied_returnsFalse() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        when(applicationService.hasUserAppliedToJob("candidate1", "job-2")).thenReturn(false);

        ResponseEntity<RestResponse<Map<String, Boolean>>> resp =
                controller.hasApplied("job-2", candidateAuth);

        assertThat(resp.getBody().getData().get("applied")).isFalse();
    }

    // ─── getMyApplicationCount ────────────────────────────────────────────────

    @Test
    void getMyApplicationCount_returnsCount() {
        when(applicationSecurity.getCurrentUsername(candidateAuth)).thenReturn("candidate1");
        when(applicationService.countByUser("candidate1")).thenReturn(7L);

        ResponseEntity<RestResponse<Map<String, Long>>> resp =
                controller.getMyApplicationCount(candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().get("count")).isEqualTo(7L);
    }

    // ─── getStatusHistory ─────────────────────────────────────────────────────

    @Test
    void getStatusHistory_returnsHistory() {
        StatusChangeResponse change = StatusChangeResponse.builder()
                .newStatus(ApplicationStatus.APPLIED)
                .changedBy("candidate1")
                .changedAt(Instant.now())
                .build();
        when(applicationService.getStatusHistory("app-1")).thenReturn(List.of(change));

        ResponseEntity<RestResponse<List<StatusChangeResponse>>> resp =
                controller.getStatusHistory("app-1", candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).hasSize(1);
        assertThat(resp.getBody().getData().get(0).getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    // ─── getPublicNotes ───────────────────────────────────────────────────────

    @Test
    void getPublicNotes_returnsNotes() {
        NoteResponse note = NoteResponse.builder()
                .id("n1").author("hr1").content("Good luck!")
                .candidateVisible(true).createdAt(Instant.now()).build();
        when(applicationService.getPublicNotes("app-1")).thenReturn(List.of(note));

        ResponseEntity<RestResponse<List<NoteResponse>>> resp =
                controller.getPublicNotes("app-1", candidateAuth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().get(0).getId()).isEqualTo("n1");
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
