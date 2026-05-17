package org.workfitai.applicationservice.dto.response;

import java.time.Instant;
import java.util.List;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary of a candidate who applied to company jobs")
public class CandidateSummaryResponse {

    @Schema(description = "Candidate username (JWT sub)")
    private String username;

    @Schema(description = "Full name from user-service (null if unavailable)")
    private String fullName;

    @Schema(description = "Candidate email")
    private String email;

    @Schema(description = "Phone number from user-service")
    private String phoneNumber;

    @Schema(description = "Account status from user-service")
    private String userStatus;

    @Schema(description = "Total applications to this company")
    private long applicationCount;

    @Schema(description = "Status of the most recent application")
    private ApplicationStatus latestStatus;

    @Schema(description = "Submission date of the most recent application")
    private Instant latestApplicationDate;

    @Schema(description = "Distinct job titles this candidate applied to")
    private List<String> appliedJobTitles;
}
