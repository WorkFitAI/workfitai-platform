package org.workfitai.jobservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.workfitai.jobservice.model.Skill;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    List<Skill> findByIdIn(List<UUID> id);
}
