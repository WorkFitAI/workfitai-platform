package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.constants.Messages;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionRequest {
    @NotBlank(message = Messages.Validation.PERMISSION_CODE_REQUIRED)
    private String permission;
}
