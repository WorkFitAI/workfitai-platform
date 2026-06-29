package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.dto.response.IssuedTokens;
import org.workfitai.authservice.dto.response.MeResponse;
import org.workfitai.authservice.dto.response.Partial2FALoginResponse;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.security.RefreshCookieFactory;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.iAuthService;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean iAuthService authService;
    @MockBean JwtService jwtService;
    @MockBean RefreshCookieFactory refreshCookieFactory;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── healthCheck ──────────────────────────────────────────────────────────

    @Test
    void healthCheck_returns200WithRunningMessage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    // ─── me ───────────────────────────────────────────────────────────────────

    @Test
    void me_whenAnonymous_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    @WithMockUser(username = "alice")
    void me_whenAuthenticated_returnsCurrentUser() throws Exception {
        // Arrange
        when(authService.getCurrentUser("alice"))
                .thenReturn(MeResponse.authenticated("alice", Set.of("CANDIDATE"), null));

        // Act & Assert
        mockMvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(authService).register(any());
        String body = """
                {
                  "email": "test@example.com",
                  "password": "Password@123",
                  "role": "CANDIDATE",
                  "fullName": "Test User",
                  "phoneNumber": "0912345678"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void register_blankEmail_returns400() throws Exception {
        // Arrange
        String body = """
                {
                  "email": "",
                  "password": "Password@123",
                  "role": "CANDIDATE",
                  "fullName": "Test User",
                  "phoneNumber": "0912345678"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─── verify-otp ───────────────────────────────────────────────────────────

    @Test
    void verifyOtp_validRequest_returns200() throws Exception {
        // Arrange
        doNothing().when(authService).verifyOtp(any());
        String body = """
                {"email": "test@example.com", "otp": "123456"}
                """;

        // Act & Assert
        mockMvc.perform(post("/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    void login_noTwoFA_returns200WithAccessToken() throws Exception {
        // Arrange
        IssuedTokens issued = IssuedTokens.of("access-token", "refresh-token", 3600000L,
                "alice", Set.of("CANDIDATE"));
        when(authService.login(any(), any(), any())).thenReturn(issued);
        when(refreshCookieFactory.build(anyString(), any(long.class)))
                .thenReturn(ResponseCookie.from("refreshToken", "refresh-token").build());
        when(jwtService.getRefreshExpMs()).thenReturn(604800000L);

        String body = """
                {"usernameOrEmail": "alice", "password": "Pass@1234"}
                """;

        // Act & Assert
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void login_twoFARequired_returns200WithPartialResponse() throws Exception {
        // Arrange
        Partial2FALoginResponse partial = Partial2FALoginResponse.builder()
                .require2FA(true)
                .method("TOTP")
                .tempToken("temp-token")
                .build();
        when(authService.login(any(), any(), any())).thenReturn(partial);

        String body = """
                {"usernameOrEmail": "alice", "password": "Pass@1234"}
                """;

        // Act & Assert
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── verify-2fa-login ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void verify2FALogin_validCode_returns200WithTokens() throws Exception {
        // Arrange
        IssuedTokens issued = IssuedTokens.of("access-token", "refresh-token", 3600000L,
                "alice", Set.of("CANDIDATE"));
        when(authService.verify2FALogin(any(), any())).thenReturn(issued);
        when(refreshCookieFactory.build(anyString(), any(long.class)))
                .thenReturn(ResponseCookie.from("refreshToken", "refresh-token").build());
        when(jwtService.getRefreshExpMs()).thenReturn(604800000L);

        String body = """
                {"tempToken": "temp-123", "code": "123456", "useBackupCode": false}
                """;

        // Act & Assert
        mockMvc.perform(post("/verify-2fa-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void logout_withPrincipal_returns200AndClearsCookie() throws Exception {
        // Arrange
        doNothing().when(authService).logout(any(), anyString());
        when(refreshCookieFactory.delete())
                .thenReturn(ResponseCookie.from("refreshToken", "").maxAge(0).build());

        // Act & Assert
        mockMvc.perform(post("/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void logout_withoutPrincipal_returns401() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/logout"))
                .andExpect(status().isUnauthorized());
    }

    // ─── refresh ──────────────────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200WithNewTokens() throws Exception {
        // Arrange
        IssuedTokens issued = IssuedTokens.of("new-access", "new-refresh", 3600000L,
                "alice", Set.of("CANDIDATE"));
        when(authService.refresh(anyString(), any())).thenReturn(issued);
        when(refreshCookieFactory.build(anyString(), any(long.class)))
                .thenReturn(ResponseCookie.from("refreshToken", "new-refresh").build());
        when(jwtService.getRefreshExpMs()).thenReturn(604800000L);

        // Act & Assert
        mockMvc.perform(post("/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("RT", "old-refresh")))
                .andExpect(status().isOk());
    }
}
