package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.HREntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HRRepository extends JpaRepository<HREntity, UUID> {

  Optional<HREntity> findByEmail(String email);

  boolean existsByEmail(String email);

}
