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

    // Structured CV sections. When populated (the /for-me path fills them from the
    // candidate's CV), the recommendation engine runs its multi-field bi-encoder +
    // per-field cross-encoder rerank and the entry-level seniority guardrail (which
    // reads resumeSummary), instead of the weaker single-field encode of profileText.
    private String resumeSummary;
    private String resumeSkills;
    private String resumeExperience;
    private String resumeEducation;

    // Candidate identity. When set, the engine additionally enforces the candidate's
    // personal AI-job-recommendation consent (privacy setting) on top of the admin toggle.
    private String candidateUsername;

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
