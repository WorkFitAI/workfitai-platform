package org.workfitai.jobservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;

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
}
