package org.workfitai.authservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.dto.response.CreateRoleDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchCreateRolesRequest {
    @NotEmpty(message = "Roles list cannot be empty")
    @Valid
    private List<CreateRoleDto> roles;
}
