package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.dto.response.Enable2FAResponse;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.TwoFactorAuthService;

@WebMvcTest(TwoFactorAuthController.class)
@Import(SecurityTestConfig.class)
class TwoFactorAuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean TwoFactorAuthService twoFactorAuthService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── enable-2fa ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void enable2fa_validRequest_returns200() throws Exception {
        // Arrange
        when(twoFactorAuthService.enable2FA(anyString(), any()))
                .thenReturn(Enable2FAResponse.builder()
                        .method("TOTP")
                        .secret("TOTP_SECRET")
                        .build());

        String body = """
                {"method": "TOTP"}
                """;

        // Act & Assert
        mockMvc.perform(post("/enable-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── disable-2fa ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void disable2fa_validRequest_returns200() throws Exception {
        // Arrange
        when(twoFactorAuthService.disable2FA(anyString(), any()))
                .thenReturn(Map.of("message", "2FA disabled"));

        String body = """
                {"code": "123456", "password": "Pass@1234"}
                """;

        // Act & Assert
        mockMvc.perform(post("/disable-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── 2fa/status ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void get2faStatus_returns200WithStatus() throws Exception {
        // Arrange
        when(twoFactorAuthService.get2FAStatus(anyString()))
                .thenReturn(Map.of("enabled", true, "method", "TOTP"));

        // Act & Assert
        mockMvc.perform(get("/2fa/status"))
                .andExpect(status().isOk());
    }
}
