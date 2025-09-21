package org.workfitai.authservice.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePermissionDto {
    @Size(max = 255)
    String description; // name is immutable by design
}
