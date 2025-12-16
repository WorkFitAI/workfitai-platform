package org.workfitai.authservice.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "two_factor_auth")
public class TwoFactorAuth {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String method; // TOTP or EMAIL

    private String secret; // For TOTP

    private List<String> backupCodes;

    private Boolean enabled;

    private LocalDateTime enabledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
