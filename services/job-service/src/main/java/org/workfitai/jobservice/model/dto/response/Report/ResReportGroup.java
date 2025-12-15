package org.workfitai.jobservice.model.dto.response.Report;

import lombok.Builder;
import lombok.Data;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ResReportGroup {
    private UUID jobId;
    private EReportStatus status;
    private List<ResReport> reports;
}