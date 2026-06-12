package org.workfitai.applicationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror DTO matching cv-service's {@code CvSnapshotResponse}.
 *
 * Populated by {@code CvServiceClient} after the SNAPSHOT_CV saga step.
 * Stored in {@link org.workfitai.applicationservice.saga.ApplicationSagaContext}
 * and then propagated into the APPLICATION_CREATED Kafka event so that
 * recommendation-engine can rank candidates without extra HTTP calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvSnapshotResponse {

    /** MongoDB ID of the snapshot CV document in cv-service. */
    @JsonProperty("cvId")
    private String cvId;

    /** Extracted summary / objective section. */
    @JsonProperty("summary")
    private String summary;

    /** Extracted work-experience section. */
    @JsonProperty("experience")
    private String experience;

    /** Extracted skills section. */
    @JsonProperty("skills")
    private String skills;

    /** Extracted education section. */
    @JsonProperty("education")
    private String education;
}
