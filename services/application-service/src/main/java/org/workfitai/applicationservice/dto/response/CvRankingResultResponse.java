package org.workfitai.applicationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enriched CV ranking response returned to the HR client.
 * Ranked applicants (with AI label/explanation/score) appear first in the list,
 * followed by unranked applicants at the end.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvRankingResultResponse {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("job_overview")
    private String jobOverview;

    @JsonProperty("total_candidates")
    private int totalCandidates;

    @JsonProperty("ranked_count")
    private int rankedCount;

    @JsonProperty("unranked_count")
    private int unrankedCount;

    @JsonProperty("processing_time_ms")
    private double processingTimeMs;

    /** Ranked applicants first (AI order), unranked applicants appended at the end */
    private List<RankedApplicationItem> applications;
}
