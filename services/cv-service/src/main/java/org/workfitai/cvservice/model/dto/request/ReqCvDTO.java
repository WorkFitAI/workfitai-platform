package org.workfitai.cvservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.util.Map;

/*
 * This template for general CV
 */

@Getter
@Setter
@Builder
public class ReqCvDTO {

    @NotBlank(message = "Headline cannot be blank")
    @Size(max = 200, message = "Headline max 200 characters")
    private String headline;

    private String summary;

    @NotBlank(message = "PDF URL cannot be blank")
    @Pattern(regexp = CVConst.URL_PATTERN, message = "pdfUrl must be a valid URL")
    private String pdfUrl;

    @NotNull(message = "templateType cannot be null")
    private TemplateType templateType;

    @NotNull(message = "sections cannot be null")
    private Map<String, Object> sections;
}
