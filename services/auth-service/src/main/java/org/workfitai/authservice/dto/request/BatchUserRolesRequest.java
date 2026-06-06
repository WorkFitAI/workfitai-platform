package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Request body for batch grant/revoke of roles to/from a user. */
@Data
public class BatchUserRolesRequest {

    @NotEmpty(message = "Role list must not be empty")
    private List<String> roles;
}
