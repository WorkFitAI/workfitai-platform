package org.workfitai.monitoringservice.dto.downstream;

import java.util.UUID;

/** Mirrors ResTopSkillDTO from job-service. */
public record TopSkillItem(UUID skillId, String skillName, long jobCount) {}
