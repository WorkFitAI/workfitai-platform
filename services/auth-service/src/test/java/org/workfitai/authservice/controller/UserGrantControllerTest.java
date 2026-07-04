package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.iUserRoleService;

@WebMvcTest(UserGrantController.class)
@Import(SecurityTestConfig.class)
class UserGrantControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean iUserRoleService userRoleService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── grantRole ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "role:grant")
    void grantRole_withRoleGrant_returns200() throws Exception {
        // Arrange
        doNothing().when(userRoleService).grantRoleToUser(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/users/alice/roles")
                        .param("role", "HR"))
                .andExpect(status().isOk());
    }

    @Test
    void grantRole_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/users/alice/roles")
                        .param("role", "HR"))
                .andExpect(status().isForbidden());
    }

    // ─── revokeRole ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "role:revoke")
    void revokeRole_withRoleRevoke_returns200() throws Exception {
        // Arrange
        doNothing().when(userRoleService).revokeRoleFromUser(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(delete("/users/alice/roles")
                        .param("role", "HR"))
                .andExpect(status().isOk());
    }

    // ─── grantRolesBatch ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "role:grant")
    void grantRolesBatch_returns200() throws Exception {
        // Arrange
        doNothing().when(userRoleService).grantRolesToUser(anyString(), any());
        String body = """
                {"roles": ["HR", "CANDIDATE"]}
                """;

        // Act & Assert
        mockMvc.perform(post("/users/alice/roles/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── listUserRoles ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "role:read")
    void listUserRoles_returns200WithRoleSet() throws Exception {
        // Arrange
        when(userRoleService.getUserRoles("alice")).thenReturn(Set.of("CANDIDATE"));

        // Act & Assert
        mockMvc.perform(get("/users/alice/roles"))
                .andExpect(status().isOk());
    }
}
