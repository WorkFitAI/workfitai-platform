package org.workfitai.applicationservice.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full candidate profile enriched from user-service, plus all their applications for this company")
public class CandidateProfileResponse {

    @Schema(description = "Candidate username (JWT sub)")
    private String username;

    @Schema(description = "User ID from user-service")
    private String userId;

    @Schema(description = "Full name from user-service")
    private String fullName;

    @Schema(description = "Candidate email")
    private String email;

    @Schema(description = "Phone number from user-service")
    private String phoneNumber;

    @Schema(description = "Account status from user-service")
    private String userStatus;

    @Schema(description = "All applications this candidate submitted to the company")
    private List<ApplicationResponse> applications;

    @Schema(description = "Total number of applications for this company")
    private long totalApplications;
}
