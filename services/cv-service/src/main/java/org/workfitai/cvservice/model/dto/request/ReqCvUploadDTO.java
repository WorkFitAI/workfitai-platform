package org.workfitai.cvservice.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.model.enums.TemplateType;

@Data
public class ReqCvUploadDTO {
    @NotNull(message = "templateType cannot be null")
    private TemplateType templateType;

    @NotNull(message = "File cannot be null")
    private MultipartFile file;

    private String pdfUrl;

    private String objectName;
}
