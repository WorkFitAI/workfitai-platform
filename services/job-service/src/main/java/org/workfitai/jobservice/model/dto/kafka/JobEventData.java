package org.workfitai.jobservice.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event data cho job - chứa tất cả thông tin cần thiết cho recommendation
 * engine
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEventData {

    private UUID jobId;
    private String title;
    private String description;
    private String shortDescription;
    private String requirements;
    private String responsibilities;
    private String benefits;

    // Location & Employment
    private String location;
    private String employmentType;

    // Experience & Education
    private String experienceLevel;
    private String requiredExperience;
    private String educationLevel;

    // Salary
    private Double salaryMin;
    private Double salaryMax;
    private String currency;

    // Skills
    private List<String> skills;

    // Company
    private CompanyData company;

    // Status & Expiration
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Company data nested trong job event
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyData {
        private String companyId; // Company.companyNo is String, not UUID
        private String companyName;
        private String industry;
        private String companySize;
        private String description;
    }
}
