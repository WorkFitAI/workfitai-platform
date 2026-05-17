package org.workfitai.applicationservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Job posting summary derived from application snapshots, with applicant statistics")
public class CompanyJobSummaryResponse {

    @Schema(description = "Job ID")
    private String jobId;

    @Schema(description = "Job title")
    private String title;

    @Schema(description = "Short description")
    private String shortDescription;

    @Schema(description = "Job location")
    private String location;

    @Schema(description = "Employment type", example = "FULL_TIME")
    private String employmentType;

    @Schema(description = "Experience level", example = "SENIOR")
    private String experienceLevel;

    @Schema(description = "Minimum salary")
    private Double salaryMin;

    @Schema(description = "Maximum salary")
    private Double salaryMax;

    @Schema(description = "Salary currency", example = "USD")
    private String currency;

    @Schema(description = "Job expiration date")
    private Instant expiresAt;

    @Schema(description = "Job status from snapshot", example = "PUBLISHED")
    private String jobStatus;

    @Schema(description = "Required skills")
    private List<String> skillNames;

    @Schema(description = "Total number of non-deleted applicants")
    private long totalApplicants;

    @Schema(description = "Applicant count broken down by application status", example = "{\"APPLIED\":5,\"INTERVIEW\":2,\"HIRED\":1}")
    private Map<String, Long> statusBreakdown;
}
