package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.AdminEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<AdminEntity, UUID> {

  Optional<AdminEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
