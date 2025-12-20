package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRolePermissionsRequest {
    @NotEmpty(message = "Permissions list cannot be empty")
    private List<String> permissions;
}
