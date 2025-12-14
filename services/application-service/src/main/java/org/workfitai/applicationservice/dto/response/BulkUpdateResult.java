package org.workfitai.applicationservice.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for bulk update operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateResult {

    /**
     * Number of successfully updated applications.
     */
    private int successCount;

    /**
     * Number of failed updates.
     */
    private int failureCount;

    /**
     * Detailed results for each application.
     */
    private List<ApplicationUpdateResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationUpdateResult {
        private String applicationId;
        private boolean success;
        private String errorMessage;
    }
}
