package org.workfitai.authservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePermissionDto {
    @NotBlank
    @Pattern(regexp = "^[A-Z]+(?::[A-Za-z0-9._-]+)+$",
            message = "Format must be DOMAIN:action[:subaction...] (e.g., USER:self:read)")
    @Size(max = 64)
    String name;

    @Size(max = 255)
    String description;
}