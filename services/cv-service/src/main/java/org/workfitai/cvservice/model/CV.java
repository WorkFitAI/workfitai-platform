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

    @NotNull(message = "belongTo cannot be null")
    @Indexed
    private String belongTo;

    @NotNull
    private TemplateType templateType;

    private Map<String, Object> sections = new HashMap<>();

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