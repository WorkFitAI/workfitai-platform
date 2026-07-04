package org.workfitai.jobservice.model.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReport;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMapperTest {

    private final ReportMapper mapper = Mappers.getMapper(ReportMapper.class);

    @Test
    void toEntity_mapsContentAndDefaultsStatusToPending_ignoringJobAndImages() {
        ReqCreateReport dto = new ReqCreateReport();
        dto.setReportContent("Inappropriate content");
        dto.setJobId(UUID.randomUUID());

        Report entity = mapper.toEntity(dto);

        assertThat(entity.getReportContent()).isEqualTo("Inappropriate content");
        assertThat(entity.getStatus()).isEqualTo(EReportStatus.PENDING);
        assertThat(entity.getJob()).isNull();
        assertThat(entity.getReportId()).isNull();
    }

    @Test
    void toDto_mapsJobIdFromNestedJobAndImagesToImageUrls() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setJobId(jobId);
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reportContent("Spam")
                .status(EReportStatus.PENDING)
                .job(job)
                .images(List.of("https://cdn/evidence.png"))
                .build();

        ResReport dto = mapper.toDto(report);

        assertThat(dto.getJobId()).isEqualTo(jobId);
        assertThat(dto.getImageUrls()).containsExactly("https://cdn/evidence.png");
        assertThat(dto.getReportContent()).isEqualTo("Spam");
        assertThat(dto.getStatus()).isEqualTo(EReportStatus.PENDING);
    }
}
