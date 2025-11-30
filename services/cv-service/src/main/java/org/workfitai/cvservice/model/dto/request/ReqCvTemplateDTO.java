package org.workfitai.cvservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.util.HashMap;
import java.util.Map;

@Data
public class ReqCvTemplateDTO {
    @NotBlank
    private String headline;

    @NotBlank
    private String summary;

    @NotNull
    private TemplateType templateType;

    private Map<String, Object> sections = new HashMap<>();
}