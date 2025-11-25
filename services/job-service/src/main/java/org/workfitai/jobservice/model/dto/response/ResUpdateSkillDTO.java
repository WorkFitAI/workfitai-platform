package org.workfitai.jobservice.model.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResUpdateSkillDTO {
    private UUID skillId;
    private String name;
}