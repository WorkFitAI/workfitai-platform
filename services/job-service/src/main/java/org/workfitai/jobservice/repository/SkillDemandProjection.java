package org.workfitai.jobservice.repository;

import java.util.UUID;

/** JPA projection for skill demand aggregation results. */
public interface SkillDemandProjection {
    UUID getSkillId();
    String getSkillName();
    Long getJobCount();
}
