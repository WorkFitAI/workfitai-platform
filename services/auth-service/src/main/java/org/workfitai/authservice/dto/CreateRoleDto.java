package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.constants.Messages;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoleDto {
    @NotBlank(message = Messages.Validation.ROLE_NAME_REQUIRED)
    @Size(max = 64, message = Messages.Validation.ROLE_NAME_SIZE)
    String name;

    @Size(max = 255, message = Messages.Validation.ROLE_DESCRIPTION_SIZE)
    String description;

    Set<@NotBlank(message = Messages.Validation.ROLE_PERMISSIONS_INVALID) String> permissions;
}
