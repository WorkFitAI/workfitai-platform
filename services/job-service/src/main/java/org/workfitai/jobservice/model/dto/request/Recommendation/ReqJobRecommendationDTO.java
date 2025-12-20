package org.workfitai.jobservice.model.dto.request.Recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqJobRecommendationDTO {
    private String profileText;
    private Integer topK;
    private RecommendationFilters filters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationFilters {
        private List<String> locations;
        private List<String> experienceLevels;
        private List<String> employmentTypes;
        private Integer minSalary;
        private Integer maxSalary;
    }
}
