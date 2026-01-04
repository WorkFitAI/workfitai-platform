package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.workfitai.authservice.enums.UserStatus;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password; // BCrypt-hashed
    private Instant passwordChangedAt; // For password change tracking

    private String fullName; // User's full name

    private Set<String> roles; // e.g. ["USER"]

    @Builder.Default
    private UserStatus status = UserStatus.PENDING; // User status for registration flow

    @Builder.Default
    private Boolean isBlocked = false; // Admin can block users

    // Company UUID for HR/HR_MANAGER role - internal linking
    private String companyId;

    // Company tax ID (companyNo) for HR_MANAGER role - mã số thuế (primary key in
    // job-service)
    private String companyNo;

    // OAuth-related fields
    private Set<String> oauthProviders; // List of linked OAuth providers: ["GOOGLE", "GITHUB"]

    private LastOAuthLogin lastOAuthLogin;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Last OAuth login metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastOAuthLogin {
        private String provider; // GOOGLE | GITHUB
        private Instant timestamp;
        private String ipAddress;
        private String deviceInfo;
    }
}
