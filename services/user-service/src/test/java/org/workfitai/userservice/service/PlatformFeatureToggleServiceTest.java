package org.workfitai.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.dto.response.FeatureToggleResponse;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.PlatformFeatureToggle;
import org.workfitai.userservice.repository.PlatformFeatureToggleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformFeatureToggleServiceTest {

    @Mock PlatformFeatureToggleRepository repository;
    @Mock FeatureToggleChangePublisher publisher;
    @InjectMocks PlatformFeatureToggleService service;

    private PlatformFeatureToggle toggle(String key, boolean enabled) {
        return PlatformFeatureToggle.builder()
                .id(UUID.randomUUID())
                .featureKey(key)
                .enabled(enabled)
                .updatedAt(Instant.now())
                .updatedBy("admin")
                .build();
    }

    // ---- seedDefaults ----

    @Test
    void seedDefaults_createsToggle_whenMissing() {
        when(repository.findByFeatureKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaults();

        verify(repository, times(2)).save(any(PlatformFeatureToggle.class));
    }

    @Test
    void seedDefaults_doesNotSave_whenAlreadyExists() {
        PlatformFeatureToggle existing = toggle("job-recommendation", true);
        when(repository.findByFeatureKey("job-recommendation")).thenReturn(Optional.of(existing));
        when(repository.findByFeatureKey("cv-referral")).thenReturn(Optional.of(toggle("cv-referral", true)));

        service.seedDefaults();

        verify(repository, never()).save(any());
    }

    // ---- listAll ----

    @Test
    void listAll_returnsAllToggles() {
        when(repository.findAll()).thenReturn(List.of(
                toggle("job-recommendation", true),
                toggle("cv-referral", false)
        ));

        List<FeatureToggleResponse> result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(r -> "job-recommendation".equals(r.getFeatureKey()) && r.isEnabled());
        assertThat(result).anyMatch(r -> "cv-referral".equals(r.getFeatureKey()) && !r.isEnabled());
    }

    @Test
    void listAll_emptyRepo_returnsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.listAll()).isEmpty();
    }

    // ---- updateToggle ----

    @Test
    void updateToggle_notFound_throwsNotFoundException() {
        when(repository.findByFeatureKey("unknown-feature")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateToggle("unknown-feature", false, "admin"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("unknown-feature");
    }

    @Test
    void updateToggle_success_savesAndPublishes() {
        PlatformFeatureToggle existing = toggle("job-recommendation", true);
        when(repository.findByFeatureKey("job-recommendation")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        FeatureToggleResponse resp = service.updateToggle("job-recommendation", false, "admin");

        assertThat(resp.getFeatureKey()).isEqualTo("job-recommendation");
        verify(repository).save(existing);
        verify(publisher).publish("job-recommendation", false);
    }

    @Test
    void updateToggle_updatesEnabledFlagAndTimestamp() {
        PlatformFeatureToggle existing = toggle("cv-referral", true);
        when(repository.findByFeatureKey("cv-referral")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.updateToggle("cv-referral", false, "superadmin");

        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getUpdatedBy()).isEqualTo("superadmin");
        assertThat(existing.getUpdatedAt()).isNotNull();
    }
}
