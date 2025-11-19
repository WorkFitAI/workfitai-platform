package org.workfitai.userservice.services;

import org.workfitai.userservice.dto.request.CandidateProfileSkillRequest;
import org.workfitai.userservice.dto.request.CandidateProfileSkillUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateProfileSkillResponse;

import java.util.List;
import java.util.UUID;

public interface CandidateProfileSkillService {
  CandidateProfileSkillResponse create(UUID candidateId, CandidateProfileSkillRequest dto);

  CandidateProfileSkillResponse update(UUID id, CandidateProfileSkillUpdateRequest dto);

  void delete(UUID id);

  CandidateProfileSkillResponse getById(UUID id);

  List<CandidateProfileSkillResponse> getAllByCandidate(UUID candidateId);
}
