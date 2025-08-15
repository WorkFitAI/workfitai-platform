package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePermissionDto {
    @NotBlank
    String name;
    String description;
}