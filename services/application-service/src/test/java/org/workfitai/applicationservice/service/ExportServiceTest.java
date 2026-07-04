package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks ExportService service;

    private Application buildApp(String id, String username) {
        return Application.builder()
                .id(id)
                .username(username)
                .email(username + "@test.com")
                .jobId("job-1")
                .status(ApplicationStatus.APPLIED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .jobSnapshot(Application.JobSnapshot.builder()
                        .title("Java Developer")
                        .build())
                .build();
    }

    // ─── exportApplications ───────────────────────────────────────────────────

    @Test
    void exportApplications_csvFormat_returnsValidResponse() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .build();
        Application app = buildApp("app-1", "user1");
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        ExportResponse result = service.exportApplications(req);

        assertThat(result.getFormat()).isEqualTo("csv");
        assertThat(result.getRowCount()).isEqualTo(1);
        assertThat(result.getDownloadUrl()).startsWith("data:text/csv");
        assertThat(result.getFileSize()).isGreaterThan(0);
        assertThat(result.getGeneratedAt()).isNotNull();
    }

    @Test
    void exportApplications_notCsvFormat_throwsBadRequest() {
        ExportRequest req = ExportRequest.builder()
                .format("xlsx")
                .companyId("company-1")
                .build();

        assertThatThrownBy(() -> service.exportApplications(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSV");
    }

    @Test
    void exportApplications_exceedsMaxRows_throwsBadRequest() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .build();

        // Return 10001 applications (over the 10000 limit)
        List<Application> manyApps = new ArrayList<>();
        for (int i = 0; i <= 10000; i++) {
            manyApps.add(buildApp("app-" + i, "user" + i));
        }
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(manyApps);

        assertThatThrownBy(() -> service.exportApplications(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum limit");
    }

    @Test
    void exportApplications_emptyResult_returnsCsvWithHeaderOnly() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .build();
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ExportResponse result = service.exportApplications(req);

        assertThat(result.getRowCount()).isEqualTo(0);
        assertThat(result.getDownloadUrl()).contains("id,username");
    }

    @Test
    void exportApplications_withStatusFilter_appliesFilter() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .status(ApplicationStatus.REVIEWING)
                .assignedTo("hr1")
                .fromDate(Instant.parse("2024-01-01T00:00:00Z"))
                .toDate(Instant.parse("2024-12-31T00:00:00Z"))
                .build();
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ExportResponse result = service.exportApplications(req);

        assertThat(result.getRowCount()).isEqualTo(0);
    }

    @Test
    void exportApplications_customColumns_useCustomColumns() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .columns(List.of("username", "status"))
                .build();
        Application app = buildApp("app-1", "user1");
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        ExportResponse result = service.exportApplications(req);

        assertThat(result.getDownloadUrl()).contains("username,status");
        assertThat(result.getDownloadUrl()).doesNotContain("email");
    }

    @Test
    void exportApplications_csvInjectionPrefixed_escaped() {
        ExportRequest req = ExportRequest.builder()
                .format("csv")
                .companyId("company-1")
                .columns(List.of("username"))
                .build();
        Application app = buildApp("app-1", "=MALICIOUS()");
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        ExportResponse result = service.exportApplications(req);

        assertThat(result.getDownloadUrl()).contains("'=MALICIOUS()");
    }

    // ─── exportAllApplications ────────────────────────────────────────────────

    @Test
    void exportAllApplications_includeDeleted_returnsAll() {
        Application app = buildApp("app-1", "user1");
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        ExportResponse result = service.exportAllApplications(true, null, null, null);

        assertThat(result.getRowCount()).isEqualTo(1);
    }

    @Test
    void exportAllApplications_withDateRange_appliesFilter() {
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        ExportResponse result = service.exportAllApplications(
                false,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T00:00:00Z"),
                null);

        assertThat(result.getRowCount()).isEqualTo(0);
    }

    @Test
    void exportAllApplications_exceedsAdminLimit_throwsBadRequest() {
        List<Application> manyApps = new ArrayList<>();
        for (int i = 0; i <= 50000; i++) {
            manyApps.add(buildApp("app-" + i, "u" + i));
        }
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(manyApps);

        assertThatThrownBy(() -> service.exportAllApplications(true, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum limit");
    }
}
