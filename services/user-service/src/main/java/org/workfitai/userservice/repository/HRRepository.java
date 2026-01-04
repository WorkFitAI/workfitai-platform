package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.enums.EUserRole;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HRRepository extends JpaRepository<HREntity, UUID>, JpaSpecificationExecutor<HREntity> {
  boolean existsByEmail(String email);

  Optional<HREntity> findByEmail(String email);

  Optional<HREntity> findByUsername(String username);

  boolean existsByPhoneNumber(String phoneNumber);

  boolean existsByCompanyIdAndUserRole(UUID companyId, EUserRole userRole);

  @Query("""
          SELECT h.department AS dept, COUNT(h) AS cnt
          FROM HREntity h
          WHERE h.department IS NOT NULL
          GROUP BY h.department
      """)
  Map<String, Long> countByDepartment();
}
