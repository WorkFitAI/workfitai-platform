package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import lombok.Builder;

/**
 * Response DTO for HR user information.
 * Contains full HR profile returned from user-service.
 */
@Builder
public record HRUserResponse(
        String userId,
        String username,
        String fullName,
        String email,
        String phoneNumber,
        String userRole, // HR or HR_MANAGER
        String userStatus,
        String companyId,
        String companyName,
        String companyNo,
        String department,
        String address,
        String createdBy,
        Instant createdDate) {
}
