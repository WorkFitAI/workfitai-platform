package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.dto.request.BulkUpdateRequest;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.validation.StatusTransitionValidator;

@ExtendWith(MockitoExtension.class)
class BulkOperationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock StatusTransitionValidator statusTransitionValidator;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks BulkOperationService bulkOperationService;

    private Application application;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id("app-1")
                .username("user1")
                .companyId("company-1")
                .jobId("job-1")
                .status(ApplicationStatus.APPLIED)
                .statusHistory(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();
    }

    // ─── bulkUpdateStatus — success ───────────────────────────────────────────

    @Test
    void bulkUpdateStatus_twoApplications_returnsSuccessCount() {
        Application app2 = Application.builder()
                .id("app-2").username("user2").companyId("company-1")
                .jobId("job-1").status(ApplicationStatus.APPLIED)
                .statusHistory(new ArrayList<>()).notes(new ArrayList<>()).build();

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-2")).thenReturn(Optional.of(app2));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1", "app-2"))
                .status(ApplicationStatus.REVIEWING)
                .reason("Batch review")
                .build();

        BulkUpdateResult result = bulkOperationService.bulkUpdateStatus(request, "hr1", "company-1");

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        verify(applicationRepository, times(2)).save(any());
    }

    @Test
    void bulkUpdateStatus_setsStatusHistoryEntry() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1"))
                .status(ApplicationStatus.REVIEWING)
                .reason("Bulk")
                .build();

        bulkOperationService.bulkUpdateStatus(request, "hr1", null);

        verify(applicationRepository).save(argThat(app ->
                app.getStatus() == ApplicationStatus.REVIEWING
                && !app.getStatusHistory().isEmpty()));
    }

    // ─── bulkUpdateStatus — size limit ───────────────────────────────────────

    @Test
    void bulkUpdateStatus_exceedsSizeLimit_throwsBadRequest() {
        List<String> ids = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "app-" + i)
                .toList();

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(ids)
                .status(ApplicationStatus.REVIEWING)
                .build();

        assertThatThrownBy(() -> bulkOperationService.bulkUpdateStatus(request, "hr1", "company-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100");
    }

    // ─── bulkUpdateStatus — not found ────────────────────────────────────────

    @Test
    void bulkUpdateStatus_applicationNotFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("bad")).thenReturn(Optional.empty());

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("bad"))
                .status(ApplicationStatus.REVIEWING)
                .build();

        assertThatThrownBy(() -> bulkOperationService.bulkUpdateStatus(request, "hr1", "company-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── bulkUpdateStatus — company scoping ──────────────────────────────────

    @Test
    void bulkUpdateStatus_wrongCompany_throwsForbidden() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1"))
                .status(ApplicationStatus.REVIEWING)
                .build();

        // callerCompanyId = "other-company" but app.companyId = "company-1"
        assertThatThrownBy(() -> bulkOperationService.bulkUpdateStatus(request, "hr1", "other-company"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong to your company");
    }

    @Test
    void bulkUpdateStatus_nullCallerCompanyId_adminBypassesCompanyCheck() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1"))
                .status(ApplicationStatus.REVIEWING)
                .build();

        // null callerCompanyId = admin — must bypass company check
        BulkUpdateResult result = bulkOperationService.bulkUpdateStatus(request, "admin1", null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    // ─── bulkUpdateStatus — event publishing (fire-and-forget) ───────────────

    @Test
    void bulkUpdateStatus_publishEventFails_doesNotFailOperation() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka down"))
                .when(eventPublisher).publishStatusChanged(any());

        BulkUpdateRequest request = BulkUpdateRequest.builder()
                .applicationIds(List.of("app-1"))
                .status(ApplicationStatus.REVIEWING)
                .build();

        // Fire-and-forget — must not propagate Kafka failure
        BulkUpdateResult result = bulkOperationService.bulkUpdateStatus(request, "hr1", null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
