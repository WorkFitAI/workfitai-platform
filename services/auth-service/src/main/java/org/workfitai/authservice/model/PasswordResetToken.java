package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private String id;

    @Indexed
    private String email;

    @Indexed(unique = true)
    private String token;

    private String otp;

    // MongoDB TTL index - document will be auto-deleted when expiresAt is reached
    @Indexed
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private Boolean used;

    private LocalDateTime usedAt;

    private Integer attempts;
}
