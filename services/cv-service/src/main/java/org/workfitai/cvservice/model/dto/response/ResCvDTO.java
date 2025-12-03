package org.workfitai.cvservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
public class ResCvDTO {

    private String cvId;

    private String headline;

    private String summary;

    private String pdfUrl;

    private String belongTo;

    private TemplateType templateType;

    private Map<String, Object> sections;

    private boolean isExist;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss a", timezone = "GMT+7")
    private Instant createdAt;

    private String createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss a", timezone = "GMT+7")

    private Instant updatedAt;

    private String updatedBy;
}