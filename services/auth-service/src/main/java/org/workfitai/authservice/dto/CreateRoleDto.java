package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoleDto {
    @NotBlank String name;
    String description;
    Set<String> permissions;
}
