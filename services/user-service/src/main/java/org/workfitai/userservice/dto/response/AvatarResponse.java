package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarResponse {
    private String avatarUrl;
    private String publicId;
    private Instant uploadedAt;
    private String message;
}
