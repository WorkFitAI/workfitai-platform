package org.workfitai.applicationservice.dto;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job information retrieved from job-service.
 * Used to create a snapshot in the application document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobInfo {

    private String postId;
    private String title;
    private String shortDescription;
    private String description;
    private String employmentType;
    private String experienceLevel;
    private String educationLevel;
    private String requiredExperience;
    private Double salaryMin;
    private Double salaryMax;
    private String currency;
    private String location;
    private Integer quantity;
    private Integer totalApplications;
    private Instant createdDate;
    private Instant lastModifiedDate;
    private Instant expiresAt;
    private String status;
    private List<String> skillNames;
    private String bannerUrl;
    private String createdBy;

    // Company info
    private String companyId;
    private String companyName;
    private String companyDescription;
    private String companyAddress;
    private String companyWebsiteUrl;
    private String companyLogoUrl;
    private String companySize;

    @Builder.Default
    private Instant fetchedAt = Instant.now();
}
