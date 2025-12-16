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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    List<Job> findBySkillsIn(List<Skill> skills);

    boolean existsByJobId(UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.jobId = :jobId")
    Optional<Job> findByIdForUpdate(@Param("jobId") UUID jobId);

    @Query("""
                SELECT DISTINCT j FROM Job j
                JOIN j.skills s
                WHERE j.jobId <> :jobId
                  AND (
                      s.skillId IN :skillIds
                      OR LOWER(j.location) = LOWER(:location)
                      OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR j.experienceLevel = :exp
                  )
            """)
    List<Job> findSimilarJobs(
            @Param("jobId") UUID jobId,
            @Param("skillIds") List<UUID> skillIds,
            @Param("location") String location,
            @Param("keyword") String keyword,
            @Param("exp") ExperienceLevel exp
    );

    @Query("""
                SELECT j FROM Job j
                WHERE j.status = 'PUBLISHED'
                  AND j.expiresAt > CURRENT_TIMESTAMP
                ORDER BY j.views DESC, j.totalApplications DESC
            """)
    Page<Job> findFeaturedJobs(Pageable pageable);

    Optional<Job> findByIdAndCreatedBy(UUID id, String createdBy);
}
