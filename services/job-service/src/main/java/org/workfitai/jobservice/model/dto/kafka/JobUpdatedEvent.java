package org.workfitai.jobservice.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event khi update job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobUpdatedEvent {

    private String eventType; // "JOB_UPDATED"
    private Instant timestamp;
    private UUID jobId;
    private JobEventData data;

    // Optional: track changed fields
    private Map<String, Object> changes;
}
