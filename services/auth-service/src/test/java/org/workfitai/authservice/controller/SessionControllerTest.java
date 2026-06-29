package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.SessionService;

@WebMvcTest(SessionController.class)
@Import(SecurityTestConfig.class)
class SessionControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SessionService sessionService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user-id-123");
        testUser.setUsername("alice");
    }

    // ─── GET /sessions ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void getSessions_authenticatedUser_returnsList() throws Exception {
        // Arrange
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(sessionService.getUserSessions(anyString(), any())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/sessions")
                        .requestAttr("sessionId", "current-session-id"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    void getSessions_userNotFound_throws404() throws Exception {
        // Arrange
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/sessions"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /sessions/{sessionId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void deleteSession_existingSession_returns200() throws Exception {
        // Arrange
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        doNothing().when(sessionService).deleteSession(anyString(), anyString(), any());

        // Act & Assert
        mockMvc.perform(delete("/sessions/session-abc")
                        .requestAttr("sessionId", "current-session-id"))
                .andExpect(status().isOk());
    }

    // ─── DELETE /sessions/all ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void deleteAllSessions_returns200() throws Exception {
        // Arrange
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        doNothing().when(sessionService).deleteAllSessionsExceptCurrent(anyString(), any());

        // Act & Assert
        mockMvc.perform(delete("/sessions/all")
                        .requestAttr("sessionId", "current-session-id"))
                .andExpect(status().isOk());
    }
}
