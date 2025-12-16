package org.workfitai.jobservice.model.dto.response.Report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResReport {
    private UUID reportId;
    private String reportContent;
    private EReportStatus status;
    private UUID jobId;
    private String createdBy;
    private List<String> imageUrls;
    private Instant createdDate;
}
