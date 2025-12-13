package org.workfitai.jobservice.model.dto.response.Job;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class ResCreateJobDTO {
    private UUID postId;
}
