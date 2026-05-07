package org.workfitai.jobservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.enums.ExperienceLevel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
  List<Job> findBySkillsIn(List<Skill> skills);

  boolean existsByJobId(UUID jobId);

  Optional<Job> findByIdAndCreatedBy(UUID id, String createdBy);

  @Query("""
      SELECT j FROM Job j
      WHERE j.jobId IN :jobIds
        AND j.status = 'PUBLISHED'
        AND j.isDeleted = false
        AND j.expiresAt > CURRENT_TIMESTAMP
      """)
  List<Job> findActiveJobsByIds(@Param("jobIds") List<UUID> jobIds);

  @Query("""
      SELECT j FROM Job j
      WHERE j.status = 'PUBLISHED'
      AND j.expiresAt <= :now
      AND j.isDeleted = false
      """)
  List<Job> findJobsToClose(@Param("now") Instant now);
}
