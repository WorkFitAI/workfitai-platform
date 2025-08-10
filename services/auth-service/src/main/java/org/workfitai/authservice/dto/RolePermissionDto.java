package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionDto {
    @NotBlank
    String roleName;
    @NotBlank String permName;
}