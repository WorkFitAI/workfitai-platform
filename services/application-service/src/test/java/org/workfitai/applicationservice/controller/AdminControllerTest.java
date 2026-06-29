package org.workfitai.applicationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.service.AdminApplicationService;
import org.workfitai.applicationservice.service.SystemStatsService;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock AdminApplicationService adminApplicationService;
    @Mock SystemStatsService systemStatsService;
    @Mock Authentication authentication;

    @InjectMocks AdminController controller;

    private ApplicationResponse appResponse;

    @BeforeEach
    void setUp() {
        appResponse = new ApplicationResponse();
    }

    // ─── getSystemStats ───────────────────────────────────────────────────────

    @Test
    void getSystemStats_returnsStats() {
        SystemStatsResponse stats = new SystemStatsResponse(
                new SystemStatsResponse.PlatformTotals(100L, 0L, 5L, 20L),
                null, null, null, null, 0.0, null, null, 0L, 0L);
        when(systemStatsService.getSystemStats()).thenReturn(stats);

        ResponseEntity<RestResponse<SystemStatsResponse>> resp = controller.getSystemStats();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().platformTotals().totalApplications()).isEqualTo(100L);
    }

    // ─── getApplications ──────────────────────────────────────────────────────

    @Test
    void getApplications_noFilters_returnsPage() {
        Page<ApplicationResponse> page = new PageImpl<>(List.of(appResponse));
        when(adminApplicationService.getApplications(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<Page<ApplicationResponse>>> resp =
                controller.getApplications(0, 50, null, null, null, null, null, true);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getTotalElements()).isEqualTo(1);
    }

    @Test
    void getApplications_withStatusFilter_passesFilter() {
        Page<ApplicationResponse> page = new PageImpl<>(List.of(appResponse));
        when(adminApplicationService.getApplications(
                eq(ApplicationStatus.APPLIED), isNull(), isNull(), isNull(), isNull(), eq(false), any()))
                .thenReturn(page);

        ResponseEntity<RestResponse<Page<ApplicationResponse>>> resp =
                controller.getApplications(0, 50, ApplicationStatus.APPLIED, null, null, null, null, false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── deleteApplication ────────────────────────────────────────────────────

    @Test
    void deleteApplication_returnsDeletedApplication() {
        when(authentication.getName()).thenReturn("admin1");
        when(adminApplicationService.softDeleteApplication("app-1", "admin1", "Test delete"))
                .thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.deleteApplication("app-1", "Test delete", authentication);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(appResponse);
    }

    @Test
    void deleteApplication_nullAuth_usesAdminFallback() {
        when(adminApplicationService.softDeleteApplication(eq("app-1"), eq("ADMIN"), any()))
                .thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.deleteApplication("app-1", "Admin deleted", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── restoreApplication ───────────────────────────────────────────────────

    @Test
    void restoreApplication_returnsRestoredApplication() {
        when(adminApplicationService.restoreApplication("app-1")).thenReturn(appResponse);

        ResponseEntity<RestResponse<ApplicationResponse>> resp =
                controller.restoreApplication("app-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(appResponse);
    }
}
