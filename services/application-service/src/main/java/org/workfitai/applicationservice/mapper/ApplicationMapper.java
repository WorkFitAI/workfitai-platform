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

        @Mapping(target = "postId", source = "postId")
        @Mapping(target = "title", source = "title")
        @Mapping(target = "shortDescription", source = "shortDescription")
        @Mapping(target = "description", source = "description")
        @Mapping(target = "employmentType", source = "employmentType")
        @Mapping(target = "experienceLevel", source = "experienceLevel")
        @Mapping(target = "educationLevel", source = "educationLevel")
        @Mapping(target = "requiredExperience", source = "requiredExperience")
        @Mapping(target = "salaryMin", source = "salaryMin")
        @Mapping(target = "salaryMax", source = "salaryMax")
        @Mapping(target = "currency", source = "currency")
        @Mapping(target = "location", source = "location")
        @Mapping(target = "quantity", source = "quantity")
        @Mapping(target = "totalApplications", source = "totalApplications")
        @Mapping(target = "createdDate", source = "createdDate")
        @Mapping(target = "lastModifiedDate", source = "lastModifiedDate")
        @Mapping(target = "expiresAt", source = "expiresAt")
        @Mapping(target = "status", source = "status")
        @Mapping(target = "skillNames", source = "skillNames")
        @Mapping(target = "bannerUrl", source = "bannerUrl")
        @Mapping(target = "createdBy", source = "createdBy")
        @Mapping(target = "companyNo", source = "companyNo")
        @Mapping(target = "companyName", source = "companyName")
        @Mapping(target = "companyDescription", source = "companyDescription")
        @Mapping(target = "companyAddress", source = "companyAddress")
        @Mapping(target = "companyWebsiteUrl", source = "companyWebsiteUrl")
        @Mapping(target = "companyLogoUrl", source = "companyLogoUrl")
        @Mapping(target = "companySize", source = "companySize")
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
