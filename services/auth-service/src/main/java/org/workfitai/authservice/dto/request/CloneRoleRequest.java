package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request body for cloning an existing role into a new named role. */
@Data
public class CloneRoleRequest {

    @NotBlank(message = "Role name must not be blank")
    @Size(max = 64, message = "Role name must not exceed 64 characters")
    private String name;

    @Size(max = 255, message = "Role description must not exceed 255 characters")
    private String description;
}
