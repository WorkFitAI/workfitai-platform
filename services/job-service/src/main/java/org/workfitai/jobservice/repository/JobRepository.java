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
import org.workfitai.jobservice.model.enums.EmploymentType;

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

  boolean existsByTitleAndCompany_CompanyNo(String title, String companyNo);

  Job findByTitleAndCompany_CompanyNo(String title, String companyNo);

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

  // --- Platform-wide stats queries ---

  @Query("SELECT j.employmentType, COUNT(j) FROM Job j WHERE j.isDeleted = false AND j.status = 'PUBLISHED' GROUP BY j.employmentType")
  List<Object[]> countPublishedByEmploymentType();

  @Query("SELECT j.experienceLevel, COUNT(j) FROM Job j WHERE j.isDeleted = false AND j.status = 'PUBLISHED' GROUP BY j.experienceLevel")
  List<Object[]> countPublishedByExperienceLevel();

  @Query("SELECT j FROM Job j LEFT JOIN FETCH j.company WHERE j.status = 'PUBLISHED' AND j.isDeleted = false ORDER BY j.views DESC LIMIT 10")
  List<Job> findTop10PublishedByViewsDesc();

  @Query("SELECT j.jobCategory.name, COUNT(j) FROM Job j WHERE j.isDeleted = false AND j.status = 'PUBLISHED' AND j.jobCategory IS NOT NULL GROUP BY j.jobCategory.name")
  List<Object[]> countPublishedByJobCategory();

  // --- Company-scoped stats queries (HRM) ---

  @Query("SELECT j.employmentType, COUNT(j) FROM Job j WHERE j.company.companyNo = :companyId AND j.isDeleted = false AND j.status = 'PUBLISHED' GROUP BY j.employmentType")
  List<Object[]> countPublishedByEmploymentTypeForCompany(@Param("companyId") String companyId);

  @Query("SELECT j.jobCategory.name, COUNT(j) FROM Job j WHERE j.company.companyNo = :companyId AND j.isDeleted = false AND j.status = 'PUBLISHED' AND j.jobCategory IS NOT NULL GROUP BY j.jobCategory.name")
  List<Object[]> countPublishedByJobCategoryForCompany(@Param("companyId") String companyId);

  @Query("SELECT j.experienceLevel, COUNT(j) FROM Job j WHERE j.company.companyNo = :companyId AND j.isDeleted = false AND j.status = 'PUBLISHED' GROUP BY j.experienceLevel")
  List<Object[]> countPublishedByExperienceLevelForCompany(@Param("companyId") String companyId);

  @Query("SELECT j FROM Job j LEFT JOIN FETCH j.company WHERE j.company.companyNo = :companyId AND j.status = 'PUBLISHED' AND j.isDeleted = false ORDER BY j.views DESC LIMIT 10")
  List<Job> findTop10PublishedByCompanyAndViewsDesc(@Param("companyId") String companyId);
}
