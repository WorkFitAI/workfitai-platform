package org.workfitai.jobservice.model.dto.kafka;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event to update job statistics.
 * Consumed from: job-stats-update topic
 * Producer: application-service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatsUpdateEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("jobId")
    private UUID jobId;

    @JsonProperty("totalApplications")
    private Integer totalApplications;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("operation")
    private String operation; // INCREMENT, DECREMENT, SET
}
