package org.workfitai.cvservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "cv")
@Data
@JsonIgnoreProperties(value = {"createdBy", "createdAt", "updatedBy", "updatedAt"}, allowGetters = true)
@NoArgsConstructor
@AllArgsConstructor
public class CV {

    @Id
    private String cvId;

    private String headline;

    private String summary;

    @Pattern(regexp = CVConst.URL_PATTERN, message = "pdfUrl must be a valid URL")
    private String pdfUrl;

    private String objectName; // Thêm để download CV

    @NotNull(message = "belongTo cannot be null")
    @Indexed
    private String belongTo;

    @NotNull
    private TemplateType templateType;

    /**
     * Application snapshot linkage.
     * null  = regular user-owned CV
     * non-null = immutable snapshot created when candidate applied (links to application-service applicationId)
     */
    @Indexed
    private String applicationId;

    private Map<String, Object> sections = new HashMap<>();

    /**
     * Raw extracted PDF text, carried in-memory from the upload strategy to the
     * async Ollama section-enrichment step. {@link Transient} so it is never
     * persisted to Mongo — it only lives on the entity long enough to hand off
     * to {@code CvSectionEnrichmentService}.
     */
    @Transient
    private String rawText;

    /**
     * Timestamp of the last Ollama section-extraction ATTEMPT for this CV.
     *
     * null means the CV has never had an extraction attempt made — NOT that the
     * resulting sections happen to be blank. Set unconditionally whenever the
     * async enrichment step gets a completed response from recommendation-engine's
     * extract-sections call, regardless of whether that response was usable
     * (extracted=true/false) or whether any individual section field ended up
     * blank and was left as heuristic (see CvSectionEnrichmentService.applyOverride's
     * per-field non-blank rule). Used by {@code CvOllamaBackfillRunner} (startup,
     * one-shot per process restart/rebuild) to find CVs still needing an attempt.
     */
    @Indexed
    private Instant ollamaExtractedAt;

    /**
     * The job's postId, persisted only for application-snapshot CVs (applicationId
     * != null). Lets the startup backfill runner re-push corrected sections into
     * recommendation-engine's CvReferStore (keyed by jobId+username) without a
     * cross-service call to application-service to look it up. null for regular
     * (non-snapshot) CVs and for snapshots created before this field existed.
     */
    private String jobId;

    private boolean isExist = true;

    @CreatedDate
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss a", timezone = "GMT+7")
    @PastOrPresent(message = "createdAt must be in the past or present")
    private Instant createdAt;

    @CreatedBy
    @NotBlank(message = "createdBy cannot be blank")
    private String createdBy;

    @LastModifiedDate
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss a", timezone = "GMT+7")
    @PastOrPresent(message = "updatedAt must be in the past or present")
    private Instant updatedAt;

    @LastModifiedBy
    private String updatedBy;
}