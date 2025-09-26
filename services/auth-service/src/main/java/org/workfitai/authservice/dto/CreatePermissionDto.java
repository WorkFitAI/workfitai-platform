package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.constants.Messages;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePermissionDto {
    @NotBlank(message = Messages.Validation.PERMISSION_NAME_REQUIRED)
    @Pattern(regexp = "^[A-Za-z]+(?::[A-Za-z0-9._-]+)+$", message = Messages.Validation.PERMISSION_NAME_INVALID)
    @Size(max = 64, message = Messages.Validation.PERMISSION_NAME_SIZE)
    String name;

    @Size(max = 255, message = Messages.Validation.PERMISSION_DESCRIPTION_SIZE)
    String description;
}
