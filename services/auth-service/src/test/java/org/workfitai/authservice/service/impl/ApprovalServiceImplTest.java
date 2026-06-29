package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.messaging.CompanyProducer;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock UserRegistrationProducer userRegistrationProducer;
    @Mock NotificationProducer notificationProducer;
    @Mock CompanyProducer companyProducer;
    @InjectMocks ApprovalServiceImpl approvalService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(approvalService, "frontendBaseUrl", "http://localhost:3000");
    }

    private User buildUser(String id, String role, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setUsername("user-" + id);
        u.setEmail("user-" + id + "@example.com");
        u.setRoles(new java.util.HashSet<>(Set.of(role)));
        u.setStatus(status);
        u.setCreatedAt(java.time.Instant.now());
        return u;
    }

    // ─── getPendingApprovals ──────────────────────────────────────────────────

    @Test
    void getPendingApprovals_returnsAllWaitApprovedUsers() {
        // Arrange
        User u1 = buildUser("1", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        when(userRepository.findByStatus(UserStatus.WAIT_APPROVED)).thenReturn(List.of(u1));

        // Act
        List<Object> result = approvalService.getPendingApprovals();

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void getPendingApprovals_emptyList_returnsEmpty() {
        when(userRepository.findByStatus(UserStatus.WAIT_APPROVED)).thenReturn(List.of());
        assertThat(approvalService.getPendingApprovals()).isEmpty();
    }

    // ─── approveHRManager ────────────────────────────────────────────────────

    @Test
    void approveHRManager_validUser_setsStatusActive() {
        // Arrange
        User user = buildUser("1", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.approveHRManager("1", "admin");

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRegistrationProducer).publishUserRegistrationEvent(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void approveHRManager_userNotFound_throws404() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.approveHRManager("unknown", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void approveHRManager_userNotWaitingApproval_throws400() {
        User user = buildUser("1", "HR_MANAGER", UserStatus.ACTIVE);
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.approveHRManager("1", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void approveHRManager_wrongRole_throws400() {
        User user = buildUser("1", "HR", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.approveHRManager("1", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ─── approveHR ───────────────────────────────────────────────────────────

    @Test
    void approveHR_validUser_setsStatusActive() {
        // Arrange
        User user = buildUser("2", "HR", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("2")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.approveHR("2", "admin");

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRegistrationProducer).publishUserRegistrationEvent(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void approveHR_wrongRole_throws400() {
        User user = buildUser("2", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("2")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.approveHR("2", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ─── rejectUser ───────────────────────────────────────────────────────────

    @Test
    void rejectUser_validUser_setsStatusInactive() {
        // Arrange
        User user = buildUser("3", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("3")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.rejectUser("3", "admin", "Invalid info");

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(notificationProducer).send(any());
    }

    @Test
    void rejectUser_userNotWaiting_throws400() {
        User user = buildUser("3", "HR_MANAGER", UserStatus.ACTIVE);
        when(userRepository.findById("3")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.rejectUser("3", "admin", "reason"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- approveHRManager: createCompany branch ----------------------------------

    @Test
    void approveHRManager_withCompanyId_triggersCompanyCreation() {
        // Arrange
        User user = buildUser("1", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        user.setCompanyNo("TAX-001");
        user.setCompanyId("company-uuid-123");
        when(userRepository.findById("1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.approveHRManager("1", "admin");

        // Assert: company creation event sent, notification sent
        verify(userRegistrationProducer).publishUserRegistrationEvent(any());
        verify(companyProducer).sendCompanyCreation(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void approveHRManager_withCompanyIdNull_skipsCompanyCreation() {
        // Arrange: user has companyNo but no companyId — createCompany should be skipped
        User user = buildUser("2", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        user.setCompanyNo("TAX-002");
        user.setCompanyId(null); // no companyId
        when(userRepository.findById("2")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.approveHRManager("2", "admin");

        // Assert: company NOT created (companyId was null)
        verify(companyProducer, org.mockito.Mockito.never()).sendCompanyCreation(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void approveHRManager_withNoCompanyNo_skipsCompanyCreation() {
        // Arrange: user has no companyNo at all
        User user = buildUser("3", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        user.setCompanyNo(null);
        user.setCompanyId(null);
        when(userRepository.findById("3")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        approvalService.approveHRManager("3", "admin");

        // Assert: company NOT created (no companyNo)
        verify(companyProducer, org.mockito.Mockito.never()).sendCompanyCreation(any());
    }

    // --- approveHR: additional edge cases ----------------------------------------

    @Test
    void approveHR_userNotFound_throws404() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.approveHR("unknown", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void approveHR_userNotWaiting_throws400() {
        User user = buildUser("2", "HR", UserStatus.ACTIVE);
        when(userRepository.findById("2")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.approveHR("2", "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- rejectUser: null reason ---------------------------------------------------

    @Test
    void rejectUser_nullReason_sendsNotificationWithDefaultReason() {
        // Arrange
        User user = buildUser("4", "HR_MANAGER", UserStatus.WAIT_APPROVED);
        when(userRepository.findById("4")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act — null reason should not throw, notification sent with "Not specified"
        approvalService.rejectUser("4", "admin", null);

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(notificationProducer).send(any());
    }

    // --- rejectUser: userNotFound -------------------------------------------------

    @Test
    void rejectUser_userNotFound_throws404() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.rejectUser("unknown", "admin", "reason"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
