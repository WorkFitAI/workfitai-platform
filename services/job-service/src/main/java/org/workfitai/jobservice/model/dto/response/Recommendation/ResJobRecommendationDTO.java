package org.workfitai.jobservice.model.dto.response.Recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDTO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResJobRecommendationDTO {
    private List<JobRecommendation> recommendations;
    private int totalResults;
    private String processingTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobRecommendation {
        private ResJobDTO job;
        private double score;
        private int rank;
    }
}
