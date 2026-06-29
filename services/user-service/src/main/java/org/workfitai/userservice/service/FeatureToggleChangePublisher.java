package org.workfitai.userservice.service;

/**
 * Seam for broadcasting feature-toggle changes to other services. Real
 * implementation (Kafka producer) lands in a later phase; until then, a
 * no-op bean keeps this service buildable independently.
 */
public interface FeatureToggleChangePublisher {
    void publish(String featureKey, boolean enabled);
}
