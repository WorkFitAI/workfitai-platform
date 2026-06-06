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
import org.workfitai.jobservice.model.enums.JobStatus;

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
      LEFT JOIN FETCH j.company
      WHERE j.status = 'PUBLISHED'
      AND j.expiresAt <= :now
      AND j.isDeleted = false
      """)
  List<Job> findJobsToClose(@Param("now") Instant now);

  @Query("SELECT j.status, COUNT(j) FROM Job j WHERE j.isDeleted = false GROUP BY j.status")
  List<Object[]> countByStatusRaw();

  @Query("""
      SELECT COUNT(j) FROM Job j
      WHERE j.status = 'PUBLISHED'
        AND j.isDeleted = false
        AND j.expiresAt > :now
        AND j.expiresAt <= :deadline
      """)
  long countExpiringSoon(@Param("now") Instant now, @Param("deadline") Instant deadline);

  @Query("SELECT COALESCE(SUM(j.views), 0) FROM Job j WHERE j.isDeleted = false")
  long sumAllViews();

  long countByCompanyCompanyNoAndStatusAndIsDeletedFalse(String companyNo, JobStatus status);

  @Query("""
      SELECT COUNT(j) FROM Job j
      WHERE j.company.companyNo = :companyId
        AND j.status = 'PUBLISHED'
        AND j.isDeleted = false
        AND j.expiresAt > :now
        AND j.expiresAt <= :deadline
      """)
  long countExpiringByCompany(@Param("companyId") String companyId,
                               @Param("now") Instant now,
                               @Param("deadline") Instant deadline);
}
