package org.workfitai.cvservice.model.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
public class ResCvDTO {

    private String cvId;

    private String headline;

    private String summary;

    private String pdfUrl;

    private UUID belongTo;

    private TemplateType templateType;

    private Map<String, Object> sections;

    private boolean isExist;

    private Instant createdAt;

    private String createdBy;

    private Instant updatedAt;

    private String updatedBy;
}