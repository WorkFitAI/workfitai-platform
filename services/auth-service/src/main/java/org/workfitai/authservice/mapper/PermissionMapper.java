package org.workfitai.authservice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.workfitai.authservice.dto.response.CreatePermissionDto;
import org.workfitai.authservice.dto.response.PermissionResponse;
import org.workfitai.authservice.model.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    @Mapping(target = "id", ignore = true)
    Permission toEntity(CreatePermissionDto dto);

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}
