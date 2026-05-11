package org.workfitai.userservice.mapper;

import org.mapstruct.*;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.model.CandidateEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandidateMapper
    extends BaseMapper<CandidateCreateRequest, CandidateEntity, CandidateResponse> {

  @Override
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "userStatus", constant = "ACTIVE")
  CandidateEntity toEntity(CandidateCreateRequest dto);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntityFromUpdateRequest(CandidateUpdateRequest dto, @MappingTarget CandidateEntity entity);
}
