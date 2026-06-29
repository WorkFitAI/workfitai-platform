package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.FeatureToggleChangedEvent;
import org.workfitai.userservice.service.FeatureToggleChangePublisher;

import java.time.Instant;

/**
 * Broadcasts platform feature toggle changes to Kafka. Consumed by
 * recommendation-engine to keep its local enforcement cache in sync.
 *
 * Direct produce (no outbox) - this is a low-stakes, eventually-consistent
 * cache-update signal, not a transactional domain event. A broker blip during
 * an admin's toggle update could fail to propagate while the DB write still
 * succeeds; acceptable given low write frequency and a human in the loop who
 * can retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureToggleEventProducer implements FeatureToggleChangePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.feature-toggle:platform-feature-toggle-events}")
    private String featureToggleTopic;

    @Override
    public void publish(String featureKey, boolean enabled) {
        FeatureToggleChangedEvent event = FeatureToggleChangedEvent.builder()
                .featureKey(featureKey)
                .enabled(enabled)
                .updatedAt(Instant.now())
                .build();

        kafkaTemplate.send(featureToggleTopic, featureKey, event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish feature toggle change {}={}", featureKey, enabled, throwable);
                    } else {
                        log.info("Feature toggle change published: {}={} (offset {})",
                                featureKey, enabled, result.getRecordMetadata().offset());
                    }
                });
    }
}
