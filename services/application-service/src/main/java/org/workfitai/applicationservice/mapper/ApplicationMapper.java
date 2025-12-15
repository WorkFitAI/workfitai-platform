package org.workfitai.applicationservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.model.Application;

/**
 * MapStruct mapper for Application entity <-> DTO conversions.
 * 
 * Note: Application entity is now built directly in Saga Orchestrator
 * (not from request DTO) since we need to handle file uploads and job
 * snapshots.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ApplicationMapper {

    @Mapping(target = "jobSnapshot", source = "jobSnapshot")
    ApplicationResponse toResponse(Application application);

    @Mapping(target = "title", source = "title")
    @Mapping(target = "companyName", source = "companyName")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "employmentType", source = "employmentType")
    @Mapping(target = "experienceLevel", source = "experienceLevel")
    @Mapping(target = "snapshotAt", source = "snapshotAt")
    ApplicationResponse.JobSnapshotResponse toJobSnapshotResponse(Application.JobSnapshot snapshot);

    default org.workfitai.applicationservice.dto.response.ResultPaginationDTO<ApplicationResponse> toResultPaginationDTO(
            org.springframework.data.domain.Page<Application> page) {
        return org.workfitai.applicationservice.dto.response.ResultPaginationDTO.<ApplicationResponse>builder()
                .items(page.getContent().stream()
                        .map(this::toResponse)
                        .toList())
                .meta(org.workfitai.applicationservice.dto.response.ResultPaginationDTO.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .first(page.isFirst())
                        .last(page.isLast())
                        .hasNext(page.hasNext())
                        .hasPrevious(page.hasPrevious())
                        .build())
                .build();
    }
}
