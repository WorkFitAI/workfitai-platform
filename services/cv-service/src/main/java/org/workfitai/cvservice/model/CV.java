package org.workfitai.cvservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.model.enums.TemplateType;
import org.workfitai.cvservice.validation.ValidCvId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Document(collection = "cv")
@Data
@JsonIgnoreProperties(value = {"createdBy", "createdAt", "updatedBy", "updatedAt"}, allowGetters = true)
@NoArgsConstructor
@AllArgsConstructor
public class CV {

    @Id
    @NotNull(message = "cvId cannot be null")
    @ValidCvId
    private String cvId = UUID.randomUUID().toString();

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 200, message = "Title max 200 characters")
    private String headline;

    private String summary;

    @NotBlank(message = "PDF URL cannot be blank")
    @Pattern(regexp = CVConst.URL_PATTERN, message = "pdfUrl must be a valid URL")
    private String pdfUrl;

    @NotNull(message = "belongTo cannot be null")
    @Indexed
    private String belongTo;

    @NotNull
    private TemplateType templateType;

    private Map<String, Object> sections = new HashMap<>();

    private boolean isExist = true;

    @CreatedDate
    @NotNull(message = "createdAt cannot be null")
    @PastOrPresent(message = "createdAt must be in the past or present")
    private Instant createdAt;

    @CreatedBy
    @NotBlank(message = "createdBy cannot be blank")
    private String createdBy;

    @LastModifiedDate
    @PastOrPresent(message = "updatedAt must be in the past or present")
    private Instant updatedAt;

    @LastModifiedBy
    private String updatedBy;
}