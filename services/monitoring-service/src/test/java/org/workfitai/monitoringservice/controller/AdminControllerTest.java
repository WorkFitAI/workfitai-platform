package org.workfitai.monitoringservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.monitoringservice.dto.*;
import org.workfitai.monitoringservice.service.AdminActivityService;
import org.workfitai.monitoringservice.service.AdminDashboardService;
import org.workfitai.monitoringservice.service.AuditSearchService;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AdminActivityService adminActivityService;
    @MockBean
    private AuditSearchService auditSearchService;
    @MockBean
    private AdminDashboardService adminDashboardService;

    private Principal adminPrincipal() {
        return new UsernamePasswordAuthenticationToken("admin1",
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Principal hrmPrincipalWithCompany(String companyId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "manager1")
                .claim("companyId", companyId)
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_HR_MANAGER"));
        return new JwtAuthenticationToken(jwt, authorities, "manager1");
    }

    private Principal hrmPrincipalWithoutCompany() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "manager1")
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_HR_MANAGER"));
        return new JwtAuthenticationToken(jwt, authorities, "manager1");
    }

    @Test
    void getUserActivities_returnsOk() throws Exception {
        when(adminActivityService.getUserActivities(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(UserActivityResponse.builder().total(2).build());

        mockMvc.perform(get("/admin/user-activities").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void getUserActivityByUsername_returnsOk() throws Exception {
        when(adminActivityService.getUserActivities(eq("alice"), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(UserActivityResponse.builder().total(1).build());

        mockMvc.perform(get("/admin/user-activities/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getOnlineUsers_returnsOk() throws Exception {
        when(adminActivityService.getUserActivities(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(UserActivityResponse.builder().total(0).build());

        mockMvc.perform(get("/admin/online-users").param("minutes", "15"))
                .andExpect(status().isOk());
    }

    @Test
    void getActivitySummary_returnsOk() throws Exception {
        when(adminActivityService.getActivitySummary(anyInt()))
                .thenReturn(ActivitySummary.builder().activeUsers(3).build());

        mockMvc.perform(get("/admin/activity-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers").value(3));
    }

    @Test
    void getAuditLogs_admin_callsSearchAdmin() throws Exception {
        when(auditSearchService.searchAdmin(any(), any())).thenReturn(Page.empty(PageRequest.of(0, 50)));

        mockMvc.perform(get("/admin/audit").principal(adminPrincipal()))
                .andExpect(status().isOk());
    }

    @Test
    void getAuditLogs_hrmWithCompanyId_callsSearchHrm() throws Exception {
        when(auditSearchService.searchHrm(eq("company-1"), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 50)));

        mockMvc.perform(get("/admin/audit").principal(hrmPrincipalWithCompany("company-1")))
                .andExpect(status().isOk());
    }

    @Test
    void getAuditLogs_hrmWithoutCompanyId_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/admin/audit").principal(hrmPrincipalWithoutCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getDashboard_returnsOk() throws Exception {
        when(adminDashboardService.getDashboard()).thenReturn(
                new AdminDashboardResponse(null, null, null, List.of(), null, List.of()));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    void getAuditStats_returnsOk() throws Exception {
        when(auditSearchService.getAuditStats(any(), any())).thenReturn(
                new AuditStatsResponse(10, 1, 90.0, 5, java.util.Map.of(), java.util.Map.of(), java.util.Map.of()));

        mockMvc.perform(get("/admin/audit/stats")
                        .param("from", Instant.parse("2024-01-01T00:00:00Z").toString())
                        .param("to", Instant.parse("2024-01-02T00:00:00Z").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(10));
    }
}
