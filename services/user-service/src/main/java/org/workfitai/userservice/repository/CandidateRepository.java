package org.workfitai.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.CandidateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository
    extends JpaRepository<CandidateEntity, UUID>, JpaSpecificationExecutor<CandidateEntity> {
  boolean existsByEmail(String email);

  Optional<CandidateEntity> findByEmail(String email);

  Optional<CandidateEntity> findByUsername(String username);

  boolean existsByPhoneNumber(String phoneNumber);

  @Query("""
          SELECT c.education, COUNT(c)
          FROM CandidateEntity c
          WHERE c.education IS NOT NULL
          GROUP BY c.education
      """)
  List<Object[]> countByEducation();

  @Query("""
          SELECT
            CASE
              WHEN c.totalExperience < 2 THEN 'Junior'
              WHEN c.totalExperience BETWEEN 2 AND 5 THEN 'Mid-Level'
              ELSE 'Senior'
            END AS experienceLevel,
            COUNT(c) AS count
          FROM CandidateEntity c
          GROUP BY experienceLevel
      """)
  List<Object[]> countByExperienceRange();

}
