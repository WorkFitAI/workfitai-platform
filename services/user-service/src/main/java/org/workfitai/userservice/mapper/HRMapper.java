package org.workfitai.userservice.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.model.HREntity;

@Mapper(componentModel = "spring")
public interface HRMapper
    extends BaseMapper<HRCreateRequest, HREntity, HRResponse> {

  @Override
  HREntity toEntity(HRCreateRequest dto);

  @Override
  HRResponse toResponse(HREntity entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntityFromUpdateRequest(HRUpdateRequest dto, @MappingTarget HREntity entity);
}
