package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.kafka.UserChangeEvent;
import org.workfitai.userservice.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing user change events to Kafka.
 * These events are consumed by Elasticsearch indexing service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "user-change-events";

    /**
     * Publish USER_CREATED event
     */
    public void publishUserCreated(UserEntity user) {
        publishEvent(user, "USER_CREATED");
    }

    /**
     * Publish USER_UPDATED event
     */
    public void publishUserUpdated(UserEntity user) {
        publishEvent(user, "USER_UPDATED");
    }

    /**
     * Publish USER_DELETED event
     */
    public void publishUserDeleted(UserEntity user) {
        publishEvent(user, "USER_DELETED");
    }

    /**
     * Publish USER_BLOCKED event
     */
    public void publishUserBlocked(UserEntity user) {
        publishEvent(user, "USER_BLOCKED");
    }

    /**
     * Publish USER_UNBLOCKED event
     */
    public void publishUserUnblocked(UserEntity user) {
        publishEvent(user, "USER_UNBLOCKED");
    }

    /**
     * Generic method to publish events
     */
    private void publishEvent(UserEntity user, String eventType) {
        try {
            UserChangeEvent event = UserChangeEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .userId(user.getUserId())
                    .version(user.getVersion())
                    .data(mapToEventData(user))
                    .build();

            kafkaTemplate.send(TOPIC, user.getUserId().toString(), event);
            log.info("Published {} event for user: {}", eventType, user.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish {} event for user: {}", eventType, user.getUserId(), e);
            // Don't throw exception - event publishing is async and non-critical
            // Failed events will be picked up by reconciliation job
        }
    }

    /**
     * Map UserEntity to event data
     */
    private UserChangeEvent.UserEventData mapToEventData(UserEntity user) {
        // Extract company information based on user type
        String companyNo = null;
        String companyName = null;

        if (user instanceof org.workfitai.userservice.model.HREntity) {
            org.workfitai.userservice.model.HREntity hrEntity = (org.workfitai.userservice.model.HREntity) user;
            companyNo = hrEntity.getCompanyNo();
            // Use company name from company service if available, otherwise use companyNo
            companyName = hrEntity.getCompanyNo(); // TODO: Get actual company name from company service
        }

        return UserChangeEvent.UserEventData.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .userRole(user.getUserRole())
                .userStatus(user.getUserStatus())
                .isBlocked(user.isBlocked())
                .isDeleted(user.isDeleted())
                .companyNo(companyNo)
                .companyName(companyName)
                .createdDate(user.getCreatedDate())
                .lastModifiedDate(user.getLastModifiedDate())
                .version(user.getVersion())
                .build();
    }
}
