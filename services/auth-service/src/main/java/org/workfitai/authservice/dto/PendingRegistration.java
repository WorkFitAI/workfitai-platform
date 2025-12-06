package org.workfitai.authservice.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.dto.HRProfileRequest;
import org.workfitai.authservice.dto.CompanyRegisterRequest;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {
    private String username;
    private String email;
    private String phoneNumber;
    private String fullName;
    private String passwordHash;
    private UserRole role;
    private HRProfileRequest hrProfile;
    private CompanyRegisterRequest company;
    @Builder.Default
    private Instant createdAt = Instant.now();
}
