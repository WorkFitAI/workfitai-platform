package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.workfitai.authservice.service.iAuthService;

@WebMvcTest(OtpController.class)
@Import(SecurityTestConfig.class)
class OtpControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean iAuthService authService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    @Test
    void verifyOtp_validRequest_returns200WithMessage() throws Exception {
        // Arrange
        doNothing().when(authService).verifyOtp(any());
        String body = """
                {"email": "alice@example.com", "otp": "123456"}
                """;

        // Act & Assert
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OTP verified")));
    }

    @Test
    void verifyOtp_missingEmail_returns400() throws Exception {
        // Arrange
        String body = """
                {"otp": "123456"}
                """;

        // Act & Assert
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
