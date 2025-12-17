package org.workfitai.jobservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobInfoDTO {
    private String companyName;
    private boolean isDeleted;
}
