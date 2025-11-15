package org.workfitai.userservice.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.model.AdminEntity;

@Mapper(componentModel = "spring")
public interface AdminMapper
    extends BaseMapper<AdminCreateRequest, AdminEntity, AdminResponse> {

  @Override
  AdminEntity toEntity(AdminCreateRequest dto);

  @Override
  AdminResponse toResponse(AdminEntity entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntityFromUpdateRequest(AdminUpdateRequest dto, @MappingTarget AdminEntity entity);
}
