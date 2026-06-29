package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.ApprovalService;

@WebMvcTest(ApprovalController.class)
@Import(SecurityTestConfig.class)
class ApprovalControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ApprovalService approvalService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── getPendingApprovals ──────────────────────────────────────────────────

    @Test
    void getPendingApprovals_returns200WithList() throws Exception {
        // Arrange
        when(approvalService.getPendingApprovals()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/admin/pending-approvals"))
                .andExpect(status().isOk());
    }

    // ─── approveHRManager ────────────────────────────────────────────────────

    @Test
    void approveHRManager_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(approvalService).approveHRManager(anyString(), anyString());
        String body = """
                {"approvedBy": "admin-user"}
                """;

        // Act & Assert
        mockMvc.perform(post("/admin/approve-hr-manager/user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── approveHR ───────────────────────────────────────────────────────────

    @Test
    void approveHR_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(approvalService).approveHR(anyString(), anyString());
        String body = """
                {"approvedBy": "admin-user"}
                """;

        // Act & Assert
        mockMvc.perform(post("/admin/approve-hr/user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── rejectUser ──────────────────────────────────────────────────────────

    @Test
    void rejectUser_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(approvalService).rejectUser(anyString(), anyString(), any());
        String body = """
                {"approvedBy": "admin-user", "rejectedBy": "admin-user", "reason": "Incomplete profile"}
                """;

        // Act & Assert
        mockMvc.perform(post("/admin/reject/user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
