package org.workfitai.jobservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReqUpdateSkillDTO {
    @NotNull(message = "id must not null")
    private UUID skillId;

    @NotBlank(message = "skill must not blank")
    private String name;
}
