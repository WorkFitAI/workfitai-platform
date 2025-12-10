package org.workfitai.applicationservice.dto.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event for sending application notification emails.
 * 
 * Published to: notification-service
 * Topic: application-notification-events
 * 
 * Use case: When application is created, notify both candidate and HR
 * - Candidate: "Your application for {jobTitle} at {companyName} has been
 * submitted"
 * - HR: "{candidateName} has applied for {jobTitle}"
 * 
 * Event flow:
 * 1. application-service creates application
 * 2. application-service fetches user details from user-service
 * 3. application-service publishes this event to Kafka
 * 4. notification-service consumes event and sends emails
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationNotificationEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // "APPLICATION_SUBMITTED"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("data")
    private NotificationData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationData {

        // Application details
        @JsonProperty("applicationId")
        private String applicationId;

        @JsonProperty("jobId")
        private String jobId;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("appliedAt")
        private Instant appliedAt;

        // Candidate details (to send email to candidate)
        @JsonProperty("candidateUsername")
        private String candidateUsername;

        @JsonProperty("candidateFullName")
        private String candidateFullName;

        @JsonProperty("candidateEmail")
        private String candidateEmail;

        // HR details (to send email to HR)
        @JsonProperty("hrUsername")
        private String hrUsername;

        @JsonProperty("hrFullName")
        private String hrFullName;

        @JsonProperty("hrEmail")
        private String hrEmail;
    }
}
