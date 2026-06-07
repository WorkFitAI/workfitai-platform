package org.workfitai.jobservice.model.dto.response.Skill;

import lombok.*;

import java.util.UUID;

import org.workfitai.jobservice.model.dto.AuditableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResSkillDTO implements AuditableResponse {
    private UUID skillId;
    private String name;

    @JsonIgnore
    @Override
    public String getAuditId() {
        return skillId.toString();
    }
}
