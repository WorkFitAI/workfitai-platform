package org.workfitai.monitoringservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.HrmDashboardResponse;
import org.workfitai.monitoringservice.service.AuditSearchService;
import org.workfitai.monitoringservice.service.HrmDashboardService;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HrmController.class)
@AutoConfigureMockMvc(addFilters = false)
class HrmControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuditSearchService auditSearchService;
    @MockBean
    private HrmDashboardService hrmDashboardService;

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
    void getDashboard_withCompanyId_returnsDashboard() throws Exception {
        when(hrmDashboardService.getDashboard("company-1")).thenReturn(
                new HrmDashboardResponse(null, null,
                        new AuditStatsResponse(5, 0, 100.0, 2, java.util.Map.of(), java.util.Map.of(), java.util.Map.of())));

        mockMvc.perform(get("/hrm/dashboard").principal(hrmPrincipalWithCompany("company-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditStats.totalEvents").value(5));
    }

    @Test
    void getDashboard_withoutCompanyId_returnsEmptyDashboard() throws Exception {
        mockMvc.perform(get("/hrm/dashboard").principal(hrmPrincipalWithoutCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStats").doesNotExist());
    }

    @Test
    void getAudit_withCompanyId_returnsHrmScopedResults() throws Exception {
        when(auditSearchService.searchHrm(eq("company-1"), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 50)));

        mockMvc.perform(get("/hrm/audit").principal(hrmPrincipalWithCompany("company-1")))
                .andExpect(status().isOk());
    }

    @Test
    void getAudit_withoutCompanyId_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/hrm/audit").principal(hrmPrincipalWithoutCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
