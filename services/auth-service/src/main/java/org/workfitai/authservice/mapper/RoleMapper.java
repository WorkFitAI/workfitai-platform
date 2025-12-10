package org.workfitai.authservice.mapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.workfitai.authservice.dto.response.CreateRoleDto;
import org.workfitai.authservice.dto.response.RoleResponse;
import org.workfitai.authservice.model.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "permissions", source = "permissions", qualifiedByName = "sanitizePermissions")
    Role toEntity(CreateRoleDto dto);

    @Mapping(target = "permissions", source = "permissions", qualifiedByName = "sanitizePermissions")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    @Named("sanitizePermissions")
    default Set<String> sanitizePermissions(Set<String> permissions) {
        return permissions == null ? new HashSet<>() : new HashSet<>(permissions);
    }
}
