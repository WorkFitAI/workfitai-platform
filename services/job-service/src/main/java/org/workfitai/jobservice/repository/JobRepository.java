package org.workfitai.jobservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    List<Job> findBySkillsIn(List<Skill> skills);
}
