package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.dto.response.PasswordResetResponse;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.PasswordService;

@WebMvcTest(PasswordController.class)
@Import(SecurityTestConfig.class)
class PasswordControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PasswordService passwordService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── change-password ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void changePassword_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(passwordService).changePassword(anyString(), any());
        String body = """
                {"currentPassword": "OldPass@1", "newPassword": "NewPass@1", "confirmPassword": "NewPass@1"}
                """;

        // Act & Assert
        mockMvc.perform(post("/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─── forgot-password ──────────────────────────────────────────────────────

    @Test
    void forgotPassword_validEmail_returns200WithResponse() throws Exception {
        // Arrange
        when(passwordService.forgotPassword(any()))
                .thenReturn(PasswordResetResponse.builder()
                        .message("OTP sent")
                        .email("alice@example.com")
                        .build());
        String body = """
                {"email": "alice@example.com"}
                """;

        // Act & Assert
        mockMvc.perform(post("/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── verify-reset-otp ────────────────────────────────────────────────────

    @Test
    void verifyResetOtp_validOtp_returns200() throws Exception {
        // Arrange
        when(passwordService.verifyResetOtp(any()))
                .thenReturn(java.util.Map.of("resetToken", "reset-token-abc"));
        String body = """
                {"email": "alice@example.com", "otp": "123456"}
                """;

        // Act & Assert
        mockMvc.perform(post("/verify-reset-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("reset-token-abc"));
    }

    // ─── reset-password ───────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_returns200() throws Exception {
        // Arrange
        doNothing().when(passwordService).resetPassword(any());
        String body = """
                {"token": "reset-token-abc", "otp": "123456", "newPassword": "NewPass@1", "confirmPassword": "NewPass@1"}
                """;

        // Act & Assert
        mockMvc.perform(post("/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─── set-password ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void setPassword_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(passwordService).setPassword(anyString(), any());
        String body = """
                {"newPassword": "NewPass@1"}
                """;

        // Act & Assert
        mockMvc.perform(post("/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
