package org.workfitai.applicationservice.mapper;

import org.mapstruct.*;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.model.Application;

/**
 * MapStruct mapper for Application entity <-> DTO conversions.
 * 
 * Uses Spring component model for dependency injection.
 * Follows the same pattern as user-service's BaseMapper.
 * 
 * Mapping Strategy:
 * - Request → Entity: For creating new applications
 * - Entity → Response: For returning application data
 * - Partial updates: Using @BeanMapping with null value strategy
 * 
 * Key Considerations:
 * - userId is extracted from JWT, not from request body (security)
 * - status is set by service layer, not mapped from request
 * - createdAt/updatedAt are set by @CreatedDate/@LastModifiedDate
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ApplicationMapper {

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 REQUEST → ENTITY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Converts CreateApplicationRequest to Application entity.
     * 
     * Note: Does NOT map userId - that must be set from JWT token
     * for security reasons (prevent users from applying as others).
     * 
     * Mapped fields:
     * - jobId → jobId
     * - cvId → cvId
     * - note → note
     * 
     * Ignored (set by service/framework):
     * - id (auto-generated)
     * - userId (from JWT)
     * - status (defaults to APPLIED)
     * - createdAt/updatedAt (@CreatedDate/@LastModifiedDate)
     * 
     * @param request The application request DTO
     * @return Application entity (without userId)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true) // Security: must come from JWT
    @Mapping(target = "status", ignore = true) // Set by service layer
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Application toEntity(CreateApplicationRequest request);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 ENTITY → RESPONSE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Converts Application entity to ApplicationResponse DTO.
     * 
     * Direct field mapping since field names match.
     * 
     * Mapped fields:
     * - id → id
     * - userId → userId
     * - jobId → jobId
     * - cvId → cvId
     * - status → status
     * - note → note
     * - createdAt → createdAt
     * - updatedAt → updatedAt
     * 
     * Rich data fields (set by service layer after this mapping):
     * - jobTitle
     * - companyName
     * - cvHeadline
     * 
     * @param application The application entity
     * @return ApplicationResponse DTO
     */
    ApplicationResponse toResponse(Application application);

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 PARTIAL UPDATES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates existing Application entity with non-null values from request.
     * 
     * Used for PATCH-style updates where only changed fields are provided.
     * Null values in source are ignored (not copied to target).
     * 
     * @param request Source with update data
     * @param entity  Target entity to update
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true) // Cannot change owner
    @Mapping(target = "jobId", ignore = true) // Cannot change target job
    @Mapping(target = "cvId", ignore = true) // Cannot change CV after apply
    @Mapping(target = "status", ignore = true) // Status changed via dedicated endpoint
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(CreateApplicationRequest request, @MappingTarget Application entity);
}
