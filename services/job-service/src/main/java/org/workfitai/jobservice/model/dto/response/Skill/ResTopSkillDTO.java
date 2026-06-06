package org.workfitai.jobservice.model.dto.response.Skill;

import java.util.UUID;

public record ResTopSkillDTO(
        UUID skillId,
        String skillName,
        long jobCount
) {}
