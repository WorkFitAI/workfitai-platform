package org.workfitai.applicationservice.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the internal active-pool endpoint.
 *
 * Used by recommendation-engine at startup to rebuild CvReferStore.
 * Each {@link ApplicantSnapshot} contains the username and the CV structured
 * fields extracted at apply time so the engine can rank without extra calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivePoolResponse {
    private String jobId;
    private List<ApplicantSnapshot> applicants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicantSnapshot {
        private String username;

        /**
         * cv-service document ID of the immutable snapshot (may be null for legacy applications).
         */
        private String cvSnapshotId;

        /**
         * Extracted summary section — empty string when cv-service snapshot is unavailable.
         */
        private String resumeSummary;

        /**
         * Extracted experience section — empty string when cv-service snapshot is unavailable.
         */
        private String resumeExperience;

        /**
         * Extracted skills section — empty string when cv-service snapshot is unavailable.
         */
        private String resumeSkills;

        /**
         * Extracted education section — empty string when cv-service snapshot is unavailable.
         */
        private String resumeEducation;
    }
}
