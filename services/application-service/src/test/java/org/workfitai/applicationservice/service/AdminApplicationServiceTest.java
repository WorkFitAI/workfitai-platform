package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.request.AdminCreateApplicationRequest;
import org.workfitai.applicationservice.dto.request.AdminOverrideRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class AdminApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks AdminApplicationService service;

    private Application application;
    private ApplicationResponse response;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id("app-1")
                .username("user1")
                .jobId("job-1")
                .status(ApplicationStatus.APPLIED)
                .statusHistory(new ArrayList<>())
                .notes(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        response = new ApplicationResponse();
    }

    // ─── createApplication ────────────────────────────────────────────────────

    @Test
    void createApplication_minimalRequest_savesAndReturnsResponse() {
        // username, email, jobId, status, cvFileUrl, coverLetter, notes, createdAt, reason
        AdminCreateApplicationRequest req = new AdminCreateApplicationRequest(
                "user1", "user1@example.com", "job-1", null, null, null, null, null, "migration");
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(application)).thenReturn(response);

        ApplicationResponse result = service.createApplication(req);

        assertThat(result).isEqualTo(response);
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void createApplication_withNotes_setsNotes() {
        var noteInput = new AdminCreateApplicationRequest.NoteInput("admin", "Test note");
        AdminCreateApplicationRequest req = new AdminCreateApplicationRequest(
                "user1", "user1@example.com", "job-1", ApplicationStatus.REVIEWING,
                null, null, List.of(noteInput), null, "migration");
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationMapper.toResponse(any())).thenReturn(response);

        service.createApplication(req);

        verify(applicationRepository).save(any(Application.class));
    }

    // ─── softDeleteApplication ────────────────────────────────────────────────

    @Test
    void softDeleteApplication_applied_marksDeletedAndWithdrawn() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ApplicationResponse result = service.softDeleteApplication("app-1", "admin1", "test");

        assertThat(result).isEqualTo(response);
        assertThat(application.getDeletedAt()).isNotNull();
        assertThat(application.getDeletedBy()).isEqualTo("admin1");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void softDeleteApplication_notFound_throwsNotFoundException() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDeleteApplication("app-1", "admin", "reason"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("app-1");
    }

    @Test
    void softDeleteApplication_alreadyDeleted_throwsIllegalState() {
        application.setDeletedAt(Instant.now());
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.softDeleteApplication("app-1", "admin", "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already deleted");
    }

    @Test
    void softDeleteApplication_notApplied_throwsIllegalState() {
        application.setStatus(ApplicationStatus.REVIEWING);
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.softDeleteApplication("app-1", "admin", "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPLIED");
    }

    // ─── restoreApplication ───────────────────────────────────────────────────

    @Test
    void restoreApplication_deleted_clearsDeletedAtAndRestoresStatus() {
        application.setDeletedAt(Instant.now());
        application.setDeletedBy("admin");
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.getStatusHistory().add(Application.StatusChange.builder()
                .previousStatus(ApplicationStatus.APPLIED)
                .newStatus(ApplicationStatus.WITHDRAWN)
                .changedBy("admin")
                .changedAt(Instant.now())
                .build());

        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ApplicationResponse result = service.restoreApplication("app-1");

        assertThat(result).isEqualTo(response);
        assertThat(application.getDeletedAt()).isNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void restoreApplication_notDeleted_throwsIllegalState() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.restoreApplication("app-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not deleted");
    }

    @Test
    void restoreApplication_notFound_throwsNotFoundException() {
        when(applicationRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreApplication("x"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── overrideApplication ──────────────────────────────────────────────────

    @Test
    void overrideApplication_updatesAllProvidedFields() {
        // status, assignedTo, cvFileUrl, companyId, updatedAt, deletedBy, deletedAt, customFields, reason
        AdminOverrideRequest req = new AdminOverrideRequest(
                ApplicationStatus.INTERVIEW, "hr1", "http://new-cv.pdf",
                "company-2", null, null, null, null, "fix");
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ApplicationResponse result = service.overrideApplication("app-1", req);

        assertThat(result).isEqualTo(response);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(application.getAssignedTo()).isEqualTo("hr1");
        assertThat(application.getCvFileUrl()).isEqualTo("http://new-cv.pdf");
        assertThat(application.getCompanyId()).isEqualTo("company-2");
    }

    @Test
    void overrideApplication_notFound_throwsNotFoundException() {
        when(applicationRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.overrideApplication("x",
                new AdminOverrideRequest(null, null, null, null, null, null, null, null, "fix")))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── getApplications ──────────────────────────────────────────────────────

    @Test
    void getApplications_noFilters_returnsPagedResults() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(application));
        when(applicationMapper.toResponse(application)).thenReturn(response);

        Page<ApplicationResponse> page = service.getApplications(
                null, null, null, null, null, true, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void getApplications_withFilters_appliesFilters() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of());

        Page<ApplicationResponse> page = service.getApplications(
                ApplicationStatus.APPLIED, "company-1", "user1", "Engineer", "java", false,
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    // ─── permanentlyDeleteApplication ─────────────────────────────────────────

    @Test
    void permanentlyDeleteApplication_deletesRecord() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        service.permanentlyDeleteApplication("app-1", "GDPR request");

        verify(applicationRepository).delete(application);
    }
}
