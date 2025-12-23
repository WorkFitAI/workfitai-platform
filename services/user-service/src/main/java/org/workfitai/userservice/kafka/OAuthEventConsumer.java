package org.workfitai.userservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.OAuthAccountLinkedEvent;
import org.workfitai.userservice.dto.kafka.OAuthAccountUnlinkedEvent;
import org.workfitai.userservice.service.UserService;

/**
 * Kafka consumer for OAuth account linking/unlinking events from auth-service
 * Updates user profile with linked OAuth provider metadata
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthEventConsumer {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Handles OAuth account linked events
     * Updates user profile to add newly linked provider to metadata
     */
    @KafkaListener(topics = "${app.kafka.topics.user-activity-events:user-activity-events}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory", filter = "oauthAccountLinkedFilter")
    public void handleOAuthAccountLinked(OAuthAccountLinkedEvent event, Acknowledgment ack) {
        try {
            log.info("Received OAuth account linked event: userId={}, provider={}, eventId={}",
                    event.getUserId(), event.getProvider(), event.getEventId());

            // Add provider to user's linked providers list
            userService.addOAuthProvider(event.getUsername(), event.getProvider(), event.getProviderEmail());

            log.info("Successfully added OAuth provider {} to user {}",
                    event.getProvider(), event.getUsername());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing OAuth account linked event: {}", event, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process OAuth account linked event", e);
        }
    }

    /**
     * Handles OAuth account unlinked events
     * Updates user profile to remove unlinked provider from metadata
     */
    @KafkaListener(topics = "${app.kafka.topics.user-activity-events:user-activity-events}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory", filter = "oauthAccountUnlinkedFilter")
    public void handleOAuthAccountUnlinked(OAuthAccountUnlinkedEvent event, Acknowledgment ack) {
        try {
            log.info("Received OAuth account unlinked event: userId={}, provider={}, eventId={}",
                    event.getUserId(), event.getProvider(), event.getEventId());

            // Remove provider from user's linked providers list
            userService.removeOAuthProvider(event.getUsername(), event.getProvider());

            log.info("Successfully removed OAuth provider {} from user {}",
                    event.getProvider(), event.getUsername());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing OAuth account unlinked event: {}", event, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process OAuth account unlinked event", e);
        }
    }
}
