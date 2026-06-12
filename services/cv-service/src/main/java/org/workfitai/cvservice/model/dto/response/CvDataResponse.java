package org.workfitai.cvservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvDataResponse {
    private String username;
    private String resumeSummary;
    private String resumeExperience;
    private String resumeSkills;
    private String resumeEducation;
}
