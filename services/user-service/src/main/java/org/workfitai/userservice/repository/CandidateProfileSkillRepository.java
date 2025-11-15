package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.CandidateProfileSkillEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateProfileSkillRepository extends JpaRepository<CandidateProfileSkillEntity, UUID> {
  boolean existsByCandidate_UserIdAndSkillId(UUID candidateId, UUID skillId);

  List<CandidateProfileSkillEntity> findAllByCandidate_UserId(UUID candidateId);
}
