package org.workfitai.userservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.workfitai.userservice.dto.request.CandidateProfileSkillRequest;
import org.workfitai.userservice.dto.response.CandidateProfileSkillResponse;
import org.workfitai.userservice.model.CandidateProfileSkillEntity;

@Mapper(componentModel = "spring")
public interface CandidateProfileSkillMapper
    extends BaseMapper<CandidateProfileSkillRequest, CandidateProfileSkillEntity, CandidateProfileSkillResponse> {

  @Override
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "candidate", ignore = true)
  CandidateProfileSkillEntity toEntity(CandidateProfileSkillRequest dto);
}

