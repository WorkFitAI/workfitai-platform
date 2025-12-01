package org.workfitai.applicationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.GlobalExceptionHandler;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.IApplicationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApplicationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ApplicationController Tests")
class ApplicationControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private IApplicationService applicationService;

        @MockBean
        private ApplicationSecurity applicationSecurity;

        @MockBean
        private JwtDecoder jwtDecoder;

        private static final String USERNAME = "john.doe";
        private static final String HR_USERNAME = "hr.manager";
        private static final String JOB_ID = UUID.randomUUID().toString();
        private static final String CV_ID = UUID.randomUUID().toString();
        private static final String APP_ID = "app-123";
        private static final String BASE_URL = "/api/v1/applications";

        private CreateApplicationRequest validRequest;
        private ApplicationResponse applicationResponse;

        @BeforeEach
        void setUp() {
                validRequest = CreateApplicationRequest.builder()
                                .jobId(JOB_ID)
                                .cvId(CV_ID)
                                .note("I'm interested in this position")
                                .build();

                applicationResponse = ApplicationResponse.builder()
                                .id(APP_ID)
                                .username(USERNAME)
                                .jobId(JOB_ID)
                                .cvId(CV_ID)
                                .status(ApplicationStatus.APPLIED)
                                .jobTitle("Senior Java Developer")
                                .companyName("TechCorp Inc")
                                .createdAt(Instant.now())
                                .build();

                given(applicationSecurity.getCurrentUsername(any())).willReturn(USERNAME);
        }

        @Nested
        @DisplayName("POST /api/v1/applications")
        class CreateApplicationTests {

                @Test
                @DisplayName("Should create application with 201 status")
                void shouldCreateApplicationSuccessfully() throws Exception {
                        given(applicationService.createApplication(any(), eq(USERNAME)))
                                        .willReturn(applicationResponse);

                        mockMvc.perform(post(BASE_URL)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(validRequest)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.data.id").value(APP_ID))
                                        .andExpect(jsonPath("$.data.status").value("APPLIED"))
                                        .andExpect(jsonPath("$.data.jobTitle").value("Senior Java Developer"));
                }

                @Test
                @DisplayName("Should return 400 for missing required fields")
                void shouldReturn400ForMissingFields() throws Exception {
                        CreateApplicationRequest invalidRequest = CreateApplicationRequest.builder()
                                        .jobId(null)
                                        .cvId(CV_ID)
                                        .build();

                        mockMvc.perform(post(BASE_URL)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(invalidRequest)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.status").value(400));
                }

                @Test
                @DisplayName("Should return 409 for duplicate application")
                void shouldReturn409ForDuplicate() throws Exception {
                        given(applicationService.createApplication(any(), eq(USERNAME)))
                                        .willThrow(new ApplicationConflictException(
                                                        "You have already applied to this job"));

                        mockMvc.perform(post(BASE_URL)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(validRequest)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.status").value(409))
                                        .andExpect(jsonPath("$.message").value("You have already applied to this job"));
                }

                @Test
                @DisplayName("Should return 403 when job is not published")
                void shouldReturn403WhenJobNotPublished() throws Exception {
                        given(applicationService.createApplication(any(), eq(USERNAME)))
                                        .willThrow(new ForbiddenException(
                                                        "Cannot apply to a job that is not published"));

                        mockMvc.perform(post(BASE_URL)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(validRequest)))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.status").value(403));
                }

                @Test
                @DisplayName("Should return 401 without authentication")
                void shouldReturn401WithoutAuth() throws Exception {
                        mockMvc.perform(post(BASE_URL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(validRequest)))
                                        .andExpect(status().isUnauthorized());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/applications/my")
        class GetMyApplicationsTests {

                @Test
                @DisplayName("Should return user's applications with CANDIDATE role")
                void shouldReturnMyApplicationsForCandidate() throws Exception {
                        ResultPaginationDTO<ApplicationResponse> paginatedResult = ResultPaginationDTO.of(
                                        List.of(applicationResponse),
                                        0, 10, 1L, 1);

                        given(applicationService.getMyApplications(eq(USERNAME), any()))
                                        .willReturn(paginatedResult);

                        mockMvc.perform(get(BASE_URL + "/my")
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.items").isArray())
                                        .andExpect(jsonPath("$.data.items[0].id").value(APP_ID))
                                        .andExpect(jsonPath("$.data.meta.totalElements").value(1));
                }

                @Test
                @DisplayName("Should filter by status when provided")
                void shouldFilterByStatus() throws Exception {
                        ResultPaginationDTO<ApplicationResponse> paginatedResult = ResultPaginationDTO.of(
                                        List.of(applicationResponse),
                                        0, 10, 1L, 1);

                        given(applicationService.getMyApplicationsByStatus(eq(USERNAME), eq(ApplicationStatus.APPLIED),
                                        any()))
                                        .willReturn(paginatedResult);

                        mockMvc.perform(get(BASE_URL + "/my")
                                        .param("status", "APPLIED")
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                                        .andExpect(status().isOk());
                }

                @Test
                @DisplayName("Should return 403 without CANDIDATE role")
                void shouldReturn403WithoutCandidateRole() throws Exception {
                        mockMvc.perform(get(BASE_URL + "/my")
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                                        .andExpect(status().isForbidden());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/applications/job/{jobId}")
        class GetApplicationsByJobTests {

                @Test
                @DisplayName("Should return job applications for HR role")
                void shouldReturnJobApplicationsForHR() throws Exception {
                        ResultPaginationDTO<ApplicationResponse> paginatedResult = ResultPaginationDTO.of(
                                        List.of(applicationResponse),
                                        0, 10, 1L, 1);

                        given(applicationService.getApplicationsByJob(eq(JOB_ID), any()))
                                        .willReturn(paginatedResult);

                        mockMvc.perform(get(BASE_URL + "/job/" + JOB_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject(HR_USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.items").isArray());
                }

                @Test
                @DisplayName("Should return 403 for CANDIDATE role")
                void shouldReturn403ForCandidateRole() throws Exception {
                        mockMvc.perform(get(BASE_URL + "/job/" + JOB_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                                        .andExpect(status().isForbidden());
                }
        }

        @Nested
        @DisplayName("PUT /api/v1/applications/{id}/status")
        class UpdateStatusTests {

                @Test
                @DisplayName("Should update status for HR role")
                void shouldUpdateStatusForHR() throws Exception {
                        given(applicationSecurity.canUpdateStatus(eq(APP_ID), any())).willReturn(true);
                        given(applicationSecurity.getCurrentUsername(any())).willReturn(HR_USERNAME);
                        given(applicationService.updateStatus(APP_ID, ApplicationStatus.REVIEWING, HR_USERNAME))
                                        .willReturn(applicationResponse);

                        mockMvc.perform(put(BASE_URL + "/" + APP_ID + "/status")
                                        .param("status", "REVIEWING")
                                        .with(jwt().jwt(jwt -> jwt.subject(HR_USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                                        .andExpect(status().isOk());
                }

                @Test
                @DisplayName("Should return 404 when application not found")
                void shouldReturn404WhenNotFound() throws Exception {
                        given(applicationSecurity.canUpdateStatus(eq(APP_ID), any())).willReturn(true);
                        given(applicationSecurity.getCurrentUsername(any())).willReturn(HR_USERNAME);
                        given(applicationService.updateStatus(APP_ID, ApplicationStatus.REVIEWING, HR_USERNAME))
                                        .willThrow(new NotFoundException("Application not found"));

                        mockMvc.perform(put(BASE_URL + "/" + APP_ID + "/status")
                                        .param("status", "REVIEWING")
                                        .with(jwt().jwt(jwt -> jwt.subject(HR_USERNAME))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("DELETE /api/v1/applications/{id}")
        class WithdrawApplicationTests {

                @Test
                @DisplayName("Should withdraw application for owner")
                void shouldWithdrawForOwner() throws Exception {
                        given(applicationSecurity.isOwner(eq(APP_ID), any())).willReturn(true);
                        willDoNothing().given(applicationService).withdrawApplication(APP_ID, USERNAME);

                        mockMvc.perform(delete(BASE_URL + "/" + APP_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))))
                                        .andExpect(status().isNoContent());
                }

                @Test
                @DisplayName("Should return 403 for non-owner")
                void shouldReturn403ForNonOwner() throws Exception {
                        given(applicationSecurity.isOwner(eq(APP_ID), any())).willReturn(false);

                        mockMvc.perform(delete(BASE_URL + "/" + APP_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject("other-user"))))
                                        .andExpect(status().isForbidden());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/applications/check")
        class CheckApplicationTests {

                @Test
                @DisplayName("Should return true when already applied")
                void shouldReturnTrueWhenApplied() throws Exception {
                        given(applicationService.hasUserAppliedToJob(USERNAME, JOB_ID)).willReturn(true);

                        mockMvc.perform(get(BASE_URL + "/check")
                                        .param("jobId", JOB_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.applied").value(true));
                }

                @Test
                @DisplayName("Should return false when not applied")
                void shouldReturnFalseWhenNotApplied() throws Exception {
                        given(applicationService.hasUserAppliedToJob(USERNAME, JOB_ID)).willReturn(false);

                        mockMvc.perform(get(BASE_URL + "/check")
                                        .param("jobId", JOB_ID)
                                        .with(jwt().jwt(jwt -> jwt.subject(USERNAME))))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.applied").value(false));
                }
        }
}
