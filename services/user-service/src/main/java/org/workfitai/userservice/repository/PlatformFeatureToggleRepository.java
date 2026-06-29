package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.workfitai.userservice.model.PlatformFeatureToggle;

import java.util.Optional;
import java.util.UUID;

public interface PlatformFeatureToggleRepository extends JpaRepository<PlatformFeatureToggle, UUID> {
    Optional<PlatformFeatureToggle> findByFeatureKey(String featureKey);
}
