package org.workfitai.applicationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvRankingResponse {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("job_overview")
    private String jobOverview;

    @JsonProperty("total_candidates")
    private int totalCandidates;

    @JsonProperty("ranked_count")
    private int rankedCount;

    @JsonProperty("processing_time_ms")
    private double processingTimeMs;

    @JsonProperty("ranked_applicants")
    private List<RankedApplicant> rankedApplicants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedApplicant {
        private String username;
        private double score;

        @JsonProperty("similarity_score")
        private double similarityScore;

        @JsonProperty("cross_score")
        private double crossScore;

        private String label;
        private String explanation;
    }
}
