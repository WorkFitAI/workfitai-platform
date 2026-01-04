package org.workfitai.jobservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {
    List<Skill> findByIdIn(List<UUID> id);

    Optional<Skill> findByName(String name);
}
