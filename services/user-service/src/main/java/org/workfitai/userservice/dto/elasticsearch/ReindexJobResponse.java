package org.workfitai.userservice.dto.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for reindex job status
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReindexJobResponse {

    private String jobId;
    private String status; // IN_PROGRESS, COMPLETED, FAILED
    private Long totalUsers;
    private Long processedUsers;
    private Long failedUsers;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String errorMessage;
}
