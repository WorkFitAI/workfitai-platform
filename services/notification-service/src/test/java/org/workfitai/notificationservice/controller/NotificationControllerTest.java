package org.workfitai.notificationservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.notificationservice.model.EmailLog;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RealtimeNotificationService;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NotificationPersistenceService persistenceService;
    @MockBean
    private RealtimeNotificationService realtimeService;

    private Principal jwtPrincipal(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", username)
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new JwtAuthenticationToken(jwt, authorities, username);
    }

    @Test
    void getNotifications_returnsOk_whenAuthenticated() throws Exception {
        Page<Notification> page = new PageImpl<>(List.of(Notification.builder().id("n1").build()));
        when(persistenceService.getNotificationsByUserId("alice", PageRequest.of(0, 20))).thenReturn(page);

        mockMvc.perform(get("/").principal(jwtPrincipal("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("n1"));
    }

    @Test
    void getNotifications_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_usesProvidedPageAndSize() throws Exception {
        when(persistenceService.getNotificationsByUserId("alice", PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/").param("page", "2").param("size", "5").principal(jwtPrincipal("alice")))
                .andExpect(status().isOk());
    }

    @Test
    void getUnreadCount_returnsCount() throws Exception {
        when(persistenceService.getUnreadCount("alice")).thenReturn(5L);

        mockMvc.perform(get("/unread-count").principal(jwtPrincipal("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void getUnreadCount_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAsRead_returnsOk_whenOwnedByCurrentUser() throws Exception {
        Notification notification = Notification.builder().id("n1").userId("alice").build();
        when(persistenceService.getNotificationById("n1")).thenReturn(notification);
        when(persistenceService.markAsRead("n1")).thenReturn(notification);

        mockMvc.perform(put("/n1/read").principal(jwtPrincipal("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("n1"));
    }

    @Test
    void markAsRead_returns404_whenNotificationNotFound() throws Exception {
        when(persistenceService.getNotificationById("missing")).thenReturn(null);

        mockMvc.perform(put("/missing/read").principal(jwtPrincipal("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_returns403_whenNotOwnedByCurrentUser() throws Exception {
        Notification notification = Notification.builder().id("n1").userId("bob").build();
        when(persistenceService.getNotificationById("n1")).thenReturn(notification);

        mockMvc.perform(put("/n1/read").principal(jwtPrincipal("alice")))
                .andExpect(status().isForbidden());

        verify(persistenceService, never()).markAsRead("n1");
    }

    @Test
    void markAsRead_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/n1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllAsRead_returnsOk() throws Exception {
        mockMvc.perform(put("/read-all").principal(jwtPrincipal("alice")))
                .andExpect(status().isOk());

        verify(persistenceService).markAllAsRead("alice");
    }

    @Test
    void markAllAsRead_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEmailLogs_returnsLogs_withDefaultLimit() throws Exception {
        when(persistenceService.getLatestEmailLogs(50)).thenReturn(List.of(EmailLog.builder().id("log1").build()));

        mockMvc.perform(get("/email-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log1"));
    }

    @Test
    void getEmailLogs_usesProvidedLimit() throws Exception {
        when(persistenceService.getLatestEmailLogs(10)).thenReturn(List.of());

        mockMvc.perform(get("/email-logs").param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void sendTestNotification_returnsSuccess_whenAuthenticated() throws Exception {
        Notification saved = Notification.builder().id("n1").build();
        when(persistenceService.createNotification(any())).thenReturn(saved);
        when(persistenceService.getUnreadCount("alice")).thenReturn(1L);

        mockMvc.perform(post("/test").principal(jwtPrincipal("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unreadCount").value(1));

        verify(realtimeService).pushUnreadCountUpdate("alice", 1L);
    }

    @Test
    void sendTestNotification_usesCustomTitleAndMessage_whenProvided() throws Exception {
        Notification saved = Notification.builder().id("n1").build();
        when(persistenceService.createNotification(any())).thenReturn(saved);
        when(persistenceService.getUnreadCount("alice")).thenReturn(0L);

        mockMvc.perform(post("/test").principal(jwtPrincipal("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Custom\",\"message\":\"Hello\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sendTestNotification_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendTestNotification_handlesEmailUsername_withoutAppendingTestDomain() throws Exception {
        Notification saved = Notification.builder().id("n1").build();
        when(persistenceService.createNotification(any())).thenReturn(saved);
        when(persistenceService.getUnreadCount("alice@test.com")).thenReturn(0L);

        mockMvc.perform(post("/test").principal(jwtPrincipal("alice@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
