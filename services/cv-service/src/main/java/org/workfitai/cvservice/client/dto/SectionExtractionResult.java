package org.workfitai.cvservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from recommendation-engine {@code POST /internal/cv/extract-sections}.
 *
 * {@code extracted=false} means Ollama extraction was disabled, unconfigured, or
 * failed — the caller then keeps its existing heuristic-parsed sections rather
 * than overwriting them with blanks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectionExtractionResult {

    @Builder.Default
    private boolean extracted = false;

    @Builder.Default
    private String summary = "";

    @Builder.Default
    private String experience = "";

    @Builder.Default
    private String skills = "";

    @Builder.Default
    private String education = "";

    /** Convenience: the disabled/failed sentinel returned on any non-success path. */
    public static SectionExtractionResult notExtracted() {
        return SectionExtractionResult.builder().extracted(false).build();
    }
}
