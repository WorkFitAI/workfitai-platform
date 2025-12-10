package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.service.CandidateService;
import org.workfitai.userservice.service.HRService;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationConsumer {

    private final CandidateService candidateService;
    private final HRService hrService;

    @KafkaListener(topics = "${app.kafka.topics.user-registration:user-registration}", groupId = "${spring.kafka.consumer.group-id:user-service-group}", containerFactory = "retryableKafkaListenerContainerFactory")
    public void handleUserRegistration(
            @Payload UserRegistrationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received user registration event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);
        log.debug("Event details: {}", event);

        try {
            // Validate event
            if (event == null || event.getUserData() == null) {
                log.error("Invalid user registration event: {}", event);
                acknowledgment.acknowledge();
                return;
            }

            if (!"USER_REGISTERED".equals(event.getEventType()) &&
                    !"HR_MANAGER_APPROVED".equals(event.getEventType()) &&
                    !"HR_APPROVED".equals(event.getEventType())) {
                log.warn("Unknown event type: {}, skipping", event.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            // Handle approval events - update user status
            if ("HR_MANAGER_APPROVED".equals(event.getEventType()) || "HR_APPROVED".equals(event.getEventType())) {
                handleUserStatusUpdate(event.getUserData());
                acknowledgment.acknowledge();
                return;
            }

            // Process the registration - create CandidateEntity
            var userData = event.getUserData();
            log.info("Processing user registration for email: {}", userData.getEmail());

            if (userData.getRole() == null || userData.getRole().isBlank()) {
                log.error("Missing role for user registration event: {}", userData);
                acknowledgment.acknowledge();
                return;
            }

            EUserRole role = EUserRole.fromJson(userData.getRole());
            switch (role) {
                case CANDIDATE -> candidateService.createFromKafkaEvent(userData);
                case HR, HR_MANAGER -> hrService.createFromKafkaEvent(userData);
                default -> log.warn("Unsupported role {} for user {}", userData.getRole(), userData.getEmail());
            }

            log.info("Successfully processed user registration for email: {}", userData.getEmail());

            // Acknowledge successful processing
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("Error processing user registration event: {}", ex.getMessage(), ex);

            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Failed to process user registration event", ex);
        }
    }

    private void handleUserStatusUpdate(UserRegistrationEvent.UserData userData) {
        log.info("Updating user status for email: {} to status: {}", userData.getEmail(), userData.getStatus());

        try {
            EUserRole role = EUserRole.fromJson(userData.getRole());
            EUserStatus status = EUserStatus.fromDisplayName(userData.getStatus());

            switch (role) {
                case CANDIDATE -> candidateService.updateStatus(userData.getEmail(), status);
                case HR, HR_MANAGER -> hrService.updateStatus(userData.getEmail(), status);
                default -> log.warn("Unsupported role {} for status update", userData.getRole());
            }

            log.info("Successfully updated user status for email: {}", userData.getEmail());

        } catch (Exception ex) {
            log.error("Error updating user status for email: {}", userData.getEmail(), ex);
            throw new RuntimeException("Failed to update user status", ex);
        }
    }
}
