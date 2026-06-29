package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class CompanyApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks CompanyApplicationService service;

    private final Pageable pageable = PageRequest.of(0, 10);
    private final Application app = Application.builder().id("app-1").status(ApplicationStatus.APPLIED).build();
    private final ApplicationResponse response = new ApplicationResponse();

    // ─── getCompanyApplications ───────────────────────────────────────────────

    @Test
    void getCompanyApplications_returnsPagedResult() {
        Page<Application> page = new PageImpl<>(List.of(app), pageable, 1);
        when(applicationRepository.findByCompanyIdAndDeletedAtIsNull("c1", pageable)).thenReturn(page);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result = service.getCompanyApplications("c1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
    }

    // ─── getCompanyApplicationsByStatus ──────────────────────────────────────

    @Test
    void getCompanyApplicationsByStatus_returnsFilteredPage() {
        Page<Application> page = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(applicationRepository.findByCompanyIdAndStatusAndDeletedAtIsNull("c1", ApplicationStatus.REVIEWING, pageable))
                .thenReturn(page);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getCompanyApplicationsByStatus("c1", ApplicationStatus.REVIEWING, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    // ─── getCompanyApplicationsByAssignedHR ──────────────────────────────────

    @Test
    void getCompanyApplicationsByAssignedHR_returnsAssignedApplications() {
        Page<Application> page = new PageImpl<>(List.of(app), pageable, 1);
        when(applicationRepository.findByCompanyIdAndAssignedToAndDeletedAtIsNull("c1", "hr1", pageable))
                .thenReturn(page);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getCompanyApplicationsByAssignedHR("c1", "hr1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
    }

    // ─── getCompanyApplicationsByAssignedHRAndStatus (uses MongoTemplate) ────

    @Test
    void getCompanyApplicationsByAssignedHRAndStatus_mapsResults() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getCompanyApplicationsByAssignedHRAndStatus("c1", "hr1", ApplicationStatus.APPLIED, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
    }

    // ─── getCompanyApplicationsWithFilters ───────────────────────────────────

    @Test
    void getCompanyApplicationsWithFilters_noFilters_returnsAll() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app, app));
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getCompanyApplicationsWithFilters("c1", null, null, null, null, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(2);
    }

    @Test
    void getCompanyApplicationsWithFilters_withAllFilters_appliesCriteria() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.getCompanyApplicationsWithFilters(
                        "c1", ApplicationStatus.INTERVIEW, "hr1", "Engineer", "java", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    // ─── getAssignedApplications ──────────────────────────────────────────────

    @Test
    void getAssignedApplications_returnsAssignedToHR() {
        Page<Application> page = new PageImpl<>(List.of(app), pageable, 1);
        when(applicationRepository.findByAssignedToAndDeletedAtIsNull("hr1", pageable)).thenReturn(page);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result = service.getAssignedApplications("hr1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
    }

    // ─── getAssignedApplicationsByStatus ─────────────────────────────────────

    @Test
    void getAssignedApplicationsByStatus_returnsFiltered() {
        Page<Application> page = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(applicationRepository.findByAssignedToAndStatusAndDeletedAtIsNull("hr1", ApplicationStatus.APPLIED, pageable))
                .thenReturn(page);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getAssignedApplicationsByStatus("hr1", ApplicationStatus.APPLIED, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    // ─── getAssignedApplicationsWithFilters ──────────────────────────────────

    @Test
    void getAssignedApplicationsWithFilters_withDateRange_appliesCriteria() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T00:00:00Z");

        ResultPaginationDTO<ApplicationResponse> result =
                service.getAssignedApplicationsWithFilters("hr1", ApplicationStatus.REVIEWING, from, to, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAssignedApplicationsWithFilters_noFilters_returnsAll() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.getAssignedApplicationsWithFilters("hr1", null, null, null, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }
}
