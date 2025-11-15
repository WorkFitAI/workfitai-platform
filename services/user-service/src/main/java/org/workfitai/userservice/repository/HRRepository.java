package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.HREntity;

import java.util.Map;
import java.util.UUID;

@Repository
public interface HRRepository extends JpaRepository<HREntity, UUID>, JpaSpecificationExecutor<HREntity> {
  boolean existsByEmail(String email);

  @Query("""
          SELECT h.department AS dept, COUNT(h) AS cnt
          FROM HREntity h
          WHERE h.department IS NOT NULL
          GROUP BY h.department
      """)
  Map<String, Long> countByDepartment();
}
