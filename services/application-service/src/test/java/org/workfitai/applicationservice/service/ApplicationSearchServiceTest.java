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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

@ExtendWith(MockitoExtension.class)
class ApplicationSearchServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock ApplicationMapper applicationMapper;

    @InjectMocks ApplicationSearchService service;

    private final Pageable pageable = PageRequest.of(0, 10);
    private final Application app = Application.builder().id("app-1").status(ApplicationStatus.APPLIED).build();
    private final ApplicationResponse response = new ApplicationResponse();

    @Test
    void search_allFiltersNull_returnsAllActive() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, null, null, null, null, null, pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void search_withCompanyId_scopsToTenant() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, null, null, null, null, "company-1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_withJobIds_filtersJobs() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(List.of("job-1", "job-2"), null, null, null, null, "company-1", pageable);

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void search_emptyJobIds_ignoresJobFilter() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(Collections.emptyList(), null, null, null, null, "company-1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_withStatus_filtersStatus() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, ApplicationStatus.REVIEWING, null, null, null, "c1", pageable);

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void search_withDateRange_filtersCreatedAt() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T00:00:00Z");

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, null, from, to, null, "c1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_withSearchText_addsTextFilter() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, null, null, null, "java developer", "c1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_withBlankSearchText_ignoresTextFilter() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ResultPaginationDTO<ApplicationResponse> result =
                service.search(null, null, null, null, "   ", "c1", pageable);

        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_allFiltersSet_appliesAll() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ResultPaginationDTO<ApplicationResponse> result = service.search(
                List.of("job-1"),
                ApplicationStatus.INTERVIEW,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T00:00:00Z"),
                "developer",
                "company-1",
                pageable);

        assertThat(result.getItems()).hasSize(1);
    }
}
