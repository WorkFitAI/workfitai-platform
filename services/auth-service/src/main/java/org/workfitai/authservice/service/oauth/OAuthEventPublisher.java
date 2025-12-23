package org.workfitai.authservice.service.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.dto.kafka.OAuthAccountLinkedEvent;
import org.workfitai.authservice.dto.kafka.OAuthAccountUnlinkedEvent;
import org.workfitai.authservice.dto.kafka.OAuthLoginEvent;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.enums.EventType;
import org.workfitai.authservice.enums.Provider;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing OAuth-related events to Kafka
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-activity-events:user-activity-events}")
    private String userActivityTopic;

    /**
     * Publish OAuth login event
     */
    public void publishLoginEvent(User user, Provider provider, String ipAddress, String userAgent) {
        try {
            OAuthLoginEvent event = OAuthLoginEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(EventType.OAUTH_LOGIN)
                    .timestamp(Instant.now())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .provider(provider)
                    .loginMetadata(OAuthLoginEvent.LoginMetadata.builder()
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .build())
                    .build();

            kafkaTemplate.send(userActivityTopic, user.getId(), event);
            log.info("Published OAuth login event for user {} via {}", user.getUsername(), provider);

        } catch (Exception e) {
            log.error("Failed to publish OAuth login event for user {}", user.getUsername(), e);
        }
    }

    /**
     * Publish account linked event
     */
    public void publishAccountLinkedEvent(User user, OAuthProvider oauthProvider) {
        try {
            OAuthAccountLinkedEvent event = OAuthAccountLinkedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(EventType.OAUTH_ACCOUNT_LINKED)
                    .timestamp(Instant.now())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .provider(oauthProvider.getProvider())
                    .providerEmail(oauthProvider.getEmail())
                    .ipAddress("")
                    .userAgent("")
                    .build();

            kafkaTemplate.send(userActivityTopic, user.getId(), event);
            log.info("Published account linked event for user {} - {}",
                    user.getUsername(), oauthProvider.getProvider());

        } catch (Exception e) {
            log.error("Failed to publish account linked event for user {}", user.getUsername(), e);
        }
    }

    /**
     * Publish account unlinked event
     */
    public void publishAccountUnlinkedEvent(String userId, String username, String email,
            Provider provider) {
        try {
            OAuthAccountUnlinkedEvent event = OAuthAccountUnlinkedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(EventType.OAUTH_ACCOUNT_UNLINKED)
                    .timestamp(Instant.now())
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .provider(provider)
                    .ipAddress("")
                    .userAgent("")
                    .build();

            kafkaTemplate.send(userActivityTopic, userId, event);
            log.info("Published account unlinked event for user {} - {}", username, provider);

        } catch (Exception e) {
            log.error("Failed to publish account unlinked event for user {}", username, e);
        }
    }
}
