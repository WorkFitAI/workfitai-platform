package org.workfitai.jobservice.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReport;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    @Mapping(target = "reportId", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "job", ignore = true)
    @Mapping(target = "images", ignore = true)
    Report toEntity(ReqCreateReport dto);

    @Mapping(source = "job.jobId", target = "jobId")
    @Mapping(source = "images", target = "imageUrls")
    ResReport toDto(Report report);
}
