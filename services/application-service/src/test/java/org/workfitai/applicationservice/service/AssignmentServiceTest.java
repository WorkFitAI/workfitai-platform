package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationMapper applicationMapper;

    @InjectMocks AssignmentService assignmentService;

    private Application application;
    private ApplicationResponse response;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id("app-1")
                .username("user1")
                .companyId("company-1")
                .status(ApplicationStatus.APPLIED)
                .notes(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .build();

        response = new ApplicationResponse();
    }

    // ─── assignApplication ────────────────────────────────────────────────────

    @Test
    void assignApplication_unassignedApp_setsAssignmentFields() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ApplicationResponse result = assignmentService.assignApplication("app-1", "hr1", "manager1");

        assertThat(result).isEqualTo(response);
        verify(applicationRepository).save(argThat(app ->
                "hr1".equals(app.getAssignedTo())
                && app.getAssignedAt() != null
                && "manager1".equals(app.getAssignedBy())));
    }

    @Test
    void assignApplication_notFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignApplication("bad", "hr1", "manager1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void assignApplication_alreadyAssignedToSameHr_throwsBadRequest() {
        application.setAssignedTo("hr1");
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> assignmentService.assignApplication("app-1", "hr1", "manager1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already assigned");
    }

    // ─── unassignApplication ──────────────────────────────────────────────────

    @Test
    void unassignApplication_assigned_clearsAssignmentFields() {
        application.setAssignedTo("hr1");
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        assignmentService.unassignApplication("app-1", "manager1");

        verify(applicationRepository).save(argThat(app ->
                app.getAssignedTo() == null
                && app.getAssignedAt() == null
                && app.getAssignedBy() == null));
    }

    @Test
    void unassignApplication_notAssigned_throwsBadRequest() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> assignmentService.unassignApplication("app-1", "manager1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not assigned");
    }

    // ─── reassignApplication ──────────────────────────────────────────────────

    @Test
    void reassignApplication_delegatesToAssign() {
        application.setAssignedTo("hr1");
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenReturn(application);
        when(applicationMapper.toResponse(any())).thenReturn(response);

        ApplicationResponse result = assignmentService.reassignApplication("app-1", "hr2", "manager1");

        assertThat(result).isEqualTo(response);
        verify(applicationRepository).save(argThat(app -> "hr2".equals(app.getAssignedTo())));
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
