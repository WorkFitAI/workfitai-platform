package org.workfitai.jobservice.model.dto.request.Report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReqCreateReport {
    @NotNull(message = "Content must be not null")
    private String reportContent;

    @NotNull(message = "Job's id must be not null")
    private UUID jobId;
}
