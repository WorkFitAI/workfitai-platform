package org.workfitai.applicationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single item in the CV ranking result.
 * Combines full application data with optional AI-generated ranking metadata.
 * Unranked applications have ranked=false and null AI fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RankedApplicationItem {

    private ApplicationResponse application;

    /** true = AI ranked this applicant; false = AI did not process or rank this applicant */
    private boolean ranked;

    /** 1-based position in the AI ranking (null if unranked) */
    private Integer rank;

    private Double score;
    private String label;
    private String explanation;
    private Double similarityScore;
    private Double crossScore;
}
