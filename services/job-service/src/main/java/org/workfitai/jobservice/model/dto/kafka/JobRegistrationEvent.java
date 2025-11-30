package org.workfitai.jobservice.model.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobRegistrationEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // "JOB_REGISTERED" hoặc "JOB_UPDATED"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("jobData")
    private JobData jobData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobData {

        @JsonProperty("jobId")
        private UUID jobId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("employmentType")
        private String employmentType;

        @JsonProperty("experienceLevel")
        private String experienceLevel;

        @JsonProperty("salaryMin")
        private BigDecimal salaryMin;

        @JsonProperty("salaryMax")
        private BigDecimal salaryMax;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("location")
        private String location;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("expiresAt")
        private Instant expiresAt;

        @JsonProperty("status")
        private String status;

        @JsonProperty("educationLevel")
        private String educationLevel;

        @JsonProperty("companyNo")
        private String companyNo;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("skills")
        private List<String> skills; // chỉ tên skills
    }
}
