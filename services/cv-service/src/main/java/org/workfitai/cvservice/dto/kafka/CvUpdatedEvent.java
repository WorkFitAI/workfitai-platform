package org.workfitai.cvservice.dto.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvUpdatedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("cvId")
    private String cvId;

    @JsonProperty("resumeSummary")
    private String resumeSummary;

    @JsonProperty("resumeExperience")
    private String resumeExperience;

    @JsonProperty("resumeSkills")
    private String resumeSkills;

    @JsonProperty("resumeEducation")
    private String resumeEducation;

    @JsonProperty("updatedAt")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
