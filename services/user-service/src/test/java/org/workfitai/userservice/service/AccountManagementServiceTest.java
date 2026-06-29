package org.workfitai.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.userservice.dto.request.DeactivateAccountRequest;
import org.workfitai.userservice.dto.request.DeleteAccountRequest;
import org.workfitai.userservice.dto.response.AccountManagementResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.messaging.NotificationProducer;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock NotificationProducer notificationProducer;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    AccountManagementService service;

    private CandidateEntity user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "deactivationRetentionDays", 30);
        ReflectionTestUtils.setField(service, "deletionGracePeriodDays", 7);

        user = CandidateEntity.builder()
                .userId(UUID.randomUUID())
                .email("u@test.com")
                .username("testuser")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .build();
    }

    // ---- deactivateAccount ----

    @Test
    void deactivateAccount_success_setsFieldsAndNotifies() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        DeactivateAccountRequest req = new DeactivateAccountRequest();
        req.setReason("Taking a break");

        AccountManagementResponse resp = service.deactivateAccount("testuser", req);

        assertThat(resp.getStatus()).isEqualTo("DEACTIVATED");
        assertThat(user.getDeactivatedAt()).isNotNull();
        assertThat(user.getDeletionDate()).isNotNull();
        verify(notificationProducer).send(any());
    }

    @Test
    void deactivateAccount_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateAccount("nobody", new DeactivateAccountRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deactivateAccount_alreadyDeactivated_throws() {
        user.setDeactivatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deactivateAccount("testuser", new DeactivateAccountRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already deactivated");
    }

    @Test
    void deactivateAccount_deletionAlreadyScheduled_throws() {
        user.setDeletionScheduledAt(Instant.now());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deactivateAccount("testuser", new DeactivateAccountRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Deletion is already scheduled");
    }

    // ---- requestAccountDeletion ----

    @Test
    void requestAccountDeletion_success_setsScheduledAtAndNotifies() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        DeleteAccountRequest req = new DeleteAccountRequest();
        req.setReason("Leaving the platform");

        AccountManagementResponse resp = service.requestAccountDeletion("testuser", req);

        assertThat(resp.getStatus()).isEqualTo("DELETION_SCHEDULED");
        assertThat(user.getDeletionScheduledAt()).isNotNull();
        assertThat(user.getDeletionDate()).isNotNull();
        verify(notificationProducer).send(any());
    }

    @Test
    void requestAccountDeletion_alreadyScheduled_throws() {
        user.setDeletionScheduledAt(Instant.now());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.requestAccountDeletion("testuser", new DeleteAccountRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already scheduled");
    }

    // ---- cancelAccountDeletion ----

    @Test
    void cancelAccountDeletion_success_clearsFields() {
        user.setDeletionScheduledAt(Instant.now());
        user.setDeletionDate(Instant.now().plus(7, ChronoUnit.DAYS));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        AccountManagementResponse resp = service.cancelAccountDeletion("testuser");

        assertThat(resp.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getDeletionScheduledAt()).isNull();
        assertThat(user.getDeletionDate()).isNull();
    }

    @Test
    void cancelAccountDeletion_notScheduled_throws() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.cancelAccountDeletion("testuser"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No account deletion scheduled");
    }

    // ---- executeScheduledDeletions ----

    @Test
    void executeScheduledDeletions_pastDeletionDate_softDeletes() {
        user.setDeletionDate(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.save(any())).thenReturn(user);

        service.executeScheduledDeletions();

        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void executeScheduledDeletions_futureDeletionDate_skips() {
        user.setDeletionDate(Instant.now().plus(5, ChronoUnit.DAYS));
        when(userRepository.findAll()).thenReturn(List.of(user));

        service.executeScheduledDeletions();

        verify(userRepository, never()).save(any());
    }
}
