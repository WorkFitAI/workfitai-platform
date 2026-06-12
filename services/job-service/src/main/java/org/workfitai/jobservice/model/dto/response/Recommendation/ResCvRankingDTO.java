package org.workfitai.jobservice.model.dto.response.Recommendation;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResCvRankingDTO {

    private String jobId;
    private String jobOverview;
    private int totalCandidates;
    private int rankedCount;
    private double processingTimeMs;
    private List<RankedApplicant> rankedApplicants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedApplicant {
        private String username;
        private double score;
        private double similarityScore;
        private double crossScore;
        private String label;
        private String explanation;
    }
}
