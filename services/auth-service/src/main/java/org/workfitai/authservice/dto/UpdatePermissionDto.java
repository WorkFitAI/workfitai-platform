package org.workfitai.authservice.dto;

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
public class UpdatePermissionDto {
    @Size(max = 255, message = Messages.Validation.PERMISSION_DESCRIPTION_SIZE)
    String description; // name is immutable by design
}
