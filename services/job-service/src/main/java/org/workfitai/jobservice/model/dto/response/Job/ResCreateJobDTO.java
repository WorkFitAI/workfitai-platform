package org.workfitai.jobservice.model.dto.response.Job;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

import org.workfitai.jobservice.model.dto.AuditableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@Builder
public class ResCreateJobDTO implements AuditableResponse {
    private UUID postId;

    @JsonIgnore
    @Override
    public String getAuditId() {
        return postId.toString();
    }
}