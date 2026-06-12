package org.workfitai.cvservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned by POST /internal/cvs/application-snapshot.
 *
 * Contains the cvId of the newly created snapshot CV document plus the
 * structured sections extracted from the uploaded PDF. These fields are
 * forwarded by application-service in the APPLICATION_CREATED Kafka event
 * so that recommendation-engine can rank applicants without any additional
 * HTTP calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvSnapshotResponse {

    /** MongoDB ID of the snapshot CV document in cv-service. */
    private String cvId;

    /** Extracted summary / objective section (free text). */
    private String summary;

    /** Extracted work-experience section (free text, newline-delimited entries). */
    private String experience;

    /** Extracted skills section (free text, newline-delimited entries). */
    private String skills;

    /** Extracted education section (free text, newline-delimited entries). */
    private String education;
}
