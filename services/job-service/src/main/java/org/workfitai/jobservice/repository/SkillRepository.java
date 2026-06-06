package org.workfitai.jobservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {
    List<Skill> findByIdIn(List<UUID> id);

    Optional<Skill> findByName(String name);

    /** Returns skills ranked by how many published, non-deleted jobs reference them. */
    @Query("""
            SELECT s.skillId AS skillId, s.name AS skillName, COUNT(j.jobId) AS jobCount
            FROM Skill s
            JOIN s.jobs j
            WHERE j.status = :status AND j.isDeleted = false
            GROUP BY s.skillId, s.name
            ORDER BY COUNT(j.jobId) DESC
            """)
    List<SkillDemandProjection> findTopSkillsByPublishedJobCount(
            @Param("status") JobStatus status,
            Pageable pageable
    );
}
