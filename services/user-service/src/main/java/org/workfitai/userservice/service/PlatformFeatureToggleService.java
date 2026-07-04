package org.workfitai.userservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.response.FeatureToggleResponse;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.PlatformFeatureToggle;
import org.workfitai.userservice.repository.PlatformFeatureToggleRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFeatureToggleService {

    public static final String JOB_RECOMMENDATION = "job-recommendation";
    public static final String CV_REFERRAL = "cv-referral";

    private final PlatformFeatureToggleRepository repository;
    private final FeatureToggleChangePublisher publisher;

    /**
     * Seed the known feature keys on startup, both enabled by default so this
     * ships as a no-op kill switch until an admin actually flips one.
     */
    @PostConstruct
    public void seedDefaults() {
        seedIfMissing(JOB_RECOMMENDATION);
        seedIfMissing(CV_REFERRAL);
    }

    private void seedIfMissing(String featureKey) {
        repository.findByFeatureKey(featureKey).orElseGet(() -> {
            log.info("Seeding platform feature toggle: {} (default enabled)", featureKey);
            return repository.save(PlatformFeatureToggle.builder()
                    .featureKey(featureKey)
                    .enabled(true)
                    .updatedAt(Instant.now())
                    .updatedBy("system")
                    .build());
        });
    }

    public List<FeatureToggleResponse> listAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FeatureToggleResponse updateToggle(String featureKey, boolean enabled, String updatedBy) {
        PlatformFeatureToggle toggle = repository.findByFeatureKey(featureKey)
                .orElseThrow(() -> new NotFoundException("Unknown feature key: " + featureKey));

        toggle.setEnabled(enabled);
        toggle.setUpdatedAt(Instant.now());
        toggle.setUpdatedBy(updatedBy);
        repository.save(toggle);

        log.info("Feature toggle {} set to {} by {}", featureKey, enabled, updatedBy);
        publisher.publish(featureKey, enabled);

        return toResponse(toggle);
    }

    private FeatureToggleResponse toResponse(PlatformFeatureToggle toggle) {
        return FeatureToggleResponse.builder()
                .featureKey(toggle.getFeatureKey())
                .enabled(toggle.isEnabled())
                .updatedAt(toggle.getUpdatedAt())
                .updatedBy(toggle.getUpdatedBy())
                .build();
    }
}
