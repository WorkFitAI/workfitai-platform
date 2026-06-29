package org.workfitai.jobservice.controller.Candidate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iReportService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private iReportService reportService;
    @InjectMocks
    private ReportController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReport_delegatesToService_usingCurrentAuthenticatedUser() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "alice").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
        ReqCreateReport req = new ReqCreateReport();
        UUID jobId = UUID.randomUUID();
        req.setJobId(jobId);
        when(reportService.createReport(eq(req), eq(null), eq("alice"))).thenReturn("Report created successfully");

        RestResponse<String> response = controller.createReport(req, null);

        assertThat(response.getData()).isEqualTo("Report created successfully");
    }
}
