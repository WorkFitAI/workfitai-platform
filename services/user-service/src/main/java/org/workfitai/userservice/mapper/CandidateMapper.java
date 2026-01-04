package org.workfitai.userservice.mapper;

import org.mapstruct.*;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.model.CandidateEntity;

@Mapper(componentModel = "spring", uses = {
    CandidateProfileSkillMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandidateMapper
    extends BaseMapper<CandidateCreateRequest, CandidateEntity, CandidateResponse> {

  @Override
  @Mapping(target = "skills", ignore = true)
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "userStatus", constant = "ACTIVE")
  // để xử lý thủ công khi cần nested update
  CandidateEntity toEntity(CandidateCreateRequest dto);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntityFromUpdateRequest(CandidateUpdateRequest dto, @MappingTarget CandidateEntity entity);
}
