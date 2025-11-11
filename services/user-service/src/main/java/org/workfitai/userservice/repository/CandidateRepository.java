package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.CandidateEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {

  Optional<CandidateEntity> findByEmail(String email);

  boolean existsByEmail(String email);

}
