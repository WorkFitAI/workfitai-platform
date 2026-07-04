package org.workfitai.applicationservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.dto.response.ActivePoolResponse;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.validation.StatusTransitionValidator;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock EventPublisherPort eventPublisher;
    @Mock StatusTransitionValidator statusTransitionValidator;

    @InjectMocks ApplicationServiceImpl service;

    private Application application;
    private ApplicationResponse applicationResponse;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id("app-1")
                .username("user1")
                .jobId("job-1")
                .status(ApplicationStatus.APPLIED)
                .statusHistory(new ArrayList<>(List.of(
                        Application.StatusChange.builder()
                                .previousStatus(null)
                                .newStatus(ApplicationStatus.APPLIED)
                                .changedBy("user1")
                                .changedAt(Instant.now())
                                .build())))
                .notes(new ArrayList<>())
                .build();

        applicationResponse = new ApplicationResponse();
        pageable = PageRequest.of(0, 10);
    }

    // ─── getApplicationById ───────────────────────────────────────────────────

    @Test
    void getApplicationById_found_returnsResponse() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationMapper.toResponse(application)).thenReturn(applicationResponse);

        ApplicationResponse result = service.getApplicationById("app-1");

        assertThat(result).isEqualTo(applicationResponse);
    }

    @Test
    void getApplicationById_notFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplicationById("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── getMyApplications ────────────────────────────────────────────────────

    @Test
    void getMyApplications_returnsPaginatedResult() {
        Page<Application> page = new PageImpl<>(List.of(application), pageable, 1);
        when(applicationRepository.findByUsernameAndDeletedAtIsNull("user1", pageable)).thenReturn(page);
        when(applicationMapper.toResponse(application)).thenReturn(applicationResponse);

        ResultPaginationDTO<ApplicationResponse> result = service.getMyApplications("user1", pageable);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
    }

    // ─── getMyApplicationsByStatus ────────────────────────────────────────────

    @Test
    void getMyApplicationsByStatus_filtersCorrectly() {
        Page<Application> page = new PageImpl<>(List.of(application));
        when(applicationRepository.findByUsernameAndStatusAndDeletedAtIsNull("user1", ApplicationStatus.APPLIED, pageable))
                .thenReturn(page);
        when(applicationMapper.toResponse(application)).thenReturn(applicationResponse);

        ResultPaginationDTO<ApplicationResponse> result = service.getMyApplicationsByStatus("user1", ApplicationStatus.APPLIED, pageable);

        assertThat(result.getItems()).hasSize(1);
    }

    // ─── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_validTransition_savesAndPublishesEvent() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        service.updateStatus("app-1", ApplicationStatus.REVIEWING, "hr1");

        verify(statusTransitionValidator).validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.REVIEWING);
        verify(applicationRepository).save(any());
        verify(eventPublisher).publishStatusChanged(any());
    }

    @Test
    void updateStatus_notFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("bad", ApplicationStatus.REVIEWING, "hr1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateStatus_invalidTransition_propagatesException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        doThrow(new IllegalArgumentException("Invalid transition"))
                .when(statusTransitionValidator).validateTransition(any(), any());

        assertThatThrownBy(() -> service.updateStatus("app-1", ApplicationStatus.HIRED, "hr1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void updateStatus_eventPublishFails_doesNotRollback() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishStatusChanged(any());

        // Fire-and-forget — must not throw
        ApplicationResponse result = service.updateStatus("app-1", ApplicationStatus.REVIEWING, "hr1");

        assertThat(result).isEqualTo(applicationResponse);
    }

    // ─── withdrawApplication ──────────────────────────────────────────────────

    @Test
    void withdrawApplication_validOwnerAndStatus_softDeletes() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);

        service.withdrawApplication("app-1", "user1");

        verify(applicationRepository).save(argThat(app ->
                app.getDeletedAt() != null
                && ApplicationStatus.WITHDRAWN.equals(app.getStatus())
                && "user1".equals(app.getDeletedBy())));
    }

    @Test
    void withdrawApplication_wrongOwner_throwsForbidden() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.withdrawApplication("app-1", "other_user"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void withdrawApplication_nonAppliedStatus_throwsBadRequest() {
        application.setStatus(ApplicationStatus.REVIEWING);
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.withdrawApplication("app-1", "user1"))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── hasUserAppliedToJob ──────────────────────────────────────────────────

    @Test
    void hasUserAppliedToJob_applied_returnsTrue() {
        when(applicationRepository.findByUsernameAndJobIdAndDeletedAtIsNull("user1", "job-1"))
                .thenReturn(Optional.of(application));

        assertThat(service.hasUserAppliedToJob("user1", "job-1")).isTrue();
    }

    @Test
    void hasUserAppliedToJob_notApplied_returnsFalse() {
        when(applicationRepository.findByUsernameAndJobIdAndDeletedAtIsNull("user1", "job-2"))
                .thenReturn(Optional.empty());

        assertThat(service.hasUserAppliedToJob("user1", "job-2")).isFalse();
    }

    // ─── getStatusHistory ─────────────────────────────────────────────────────

    @Test
    void getStatusHistory_found_returnsSortedHistory() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        var history = service.getStatusHistory("app-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    // ─── getPublicNotes ───────────────────────────────────────────────────────

    @Test
    void getPublicNotes_returnsOnlyCandidateVisibleNotes() {
        Application.Note visible = Application.Note.builder()
                .id("n1").author("hr1").content("Good candidate")
                .candidateVisible(true).createdAt(Instant.now()).build();
        Application.Note internal = Application.Note.builder()
                .id("n2").author("hr1").content("Internal note")
                .candidateVisible(false).createdAt(Instant.now()).build();
        application.setNotes(new ArrayList<>(List.of(visible, internal)));

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        var notes = service.getPublicNotes("app-1");

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getId()).isEqualTo("n1");
    }

    // ─── count methods ────────────────────────────────────────────────────────

    @Test
    void countByUser_delegatesToRepository() {
        when(applicationRepository.countByUsernameAndDeletedAtIsNull("user1")).thenReturn(5L);
        assertThat(service.countByUser("user1")).isEqualTo(5L);
    }

    @Test
    void countByJob_delegatesToRepository() {
        when(applicationRepository.countByJobIdAndDeletedAtIsNull("job-1")).thenReturn(12L);
        assertThat(service.countByJob("job-1")).isEqualTo(12L);
    }

    // ─── getApplicationsByJob ─────────────────────────────────────────────────

    @Test
    void getApplicationsByJob_returnsPaginatedResult() {
        Page<Application> page = new PageImpl<>(List.of(application), pageable, 1);
        when(applicationRepository.findByJobIdAndDeletedAtIsNull("job-1", pageable)).thenReturn(page);
        when(applicationMapper.toResponse(application)).thenReturn(applicationResponse);

        ResultPaginationDTO<ApplicationResponse> result = service.getApplicationsByJob("job-1", pageable);

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void getApplicationsByJobAndStatus_returnsPaginatedResult() {
        Page<Application> page = new PageImpl<>(List.of(application), pageable, 1);
        when(applicationRepository.findByJobIdAndStatusAndDeletedAtIsNull("job-1", ApplicationStatus.APPLIED, pageable))
                .thenReturn(page);
        when(applicationMapper.toResponse(application)).thenReturn(applicationResponse);

        ResultPaginationDTO<ApplicationResponse> result =
                service.getApplicationsByJobAndStatus("job-1", ApplicationStatus.APPLIED, pageable);

        assertThat(result.getItems()).hasSize(1);
    }

    // ─── getActivePool ────────────────────────────────────────────────────────

    @Test
    void getActivePool_groupsApplicationsByJobId() {
        Application app2 = Application.builder()
                .id("app-2").username("user2").jobId("job-1")
                .status(ApplicationStatus.REVIEWING).build();
        Application app3 = Application.builder()
                .id("app-3").username("user3").jobId("job-2")
                .status(ApplicationStatus.APPLIED).build();

        when(applicationRepository.findByStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(application, app2, app3));

        List<ActivePoolResponse> result = service.getActivePool();

        assertThat(result).hasSize(2);
        ActivePoolResponse job1Pool = result.stream()
                .filter(r -> "job-1".equals(r.getJobId())).findFirst().orElseThrow();
        assertThat(job1Pool.getApplicants()).hasSize(2);
    }

    @Test
    void getActivePool_emptyRepository_returnsEmptyList() {
        when(applicationRepository.findByStatusInAndDeletedAtIsNull(any()))
                .thenReturn(Collections.emptyList());

        List<ActivePoolResponse> result = service.getActivePool();

        assertThat(result).isEmpty();
    }

    // ─── publishJobStatsDecrementEvent (via withdrawApplication) ──────────────

    @Test
    void withdrawApplication_validUuidJobId_publishesJobStatsEvent() {
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        Application appWithUuid = Application.builder()
                .id("app-2").username("user1").jobId(validUuid)
                .status(ApplicationStatus.APPLIED).statusHistory(new ArrayList<>()).build();

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-2")).thenReturn(Optional.of(appWithUuid));
        when(applicationRepository.save(any())).thenReturn(appWithUuid);

        // Should not throw — event publish is fire-and-forget
        service.withdrawApplication("app-2", "user1");

        verify(eventPublisher).publishJobStatsUpdate(any());
    }

    @Test
    void withdrawApplication_jobStatsEventFails_doesNotRollback() {
        String validUuid = "660e8400-e29b-41d4-a716-446655440001";
        Application appWithUuid = Application.builder()
                .id("app-3").username("user1").jobId(validUuid)
                .status(ApplicationStatus.APPLIED).statusHistory(new ArrayList<>()).build();

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-3")).thenReturn(Optional.of(appWithUuid));
        when(applicationRepository.save(any())).thenReturn(appWithUuid);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishJobStatsUpdate(any());

        // Fire-and-forget — must not throw
        service.withdrawApplication("app-3", "user1");

        verify(applicationRepository).save(any());
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
