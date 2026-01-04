package org.workfitai.applicationservice.dto.response;

import lombok.Builder;

/**
 * Response DTO for HR user information.
 * Contains basic information about HR and HR_MANAGER users in a company.
 */
@Builder
public record HRUserResponse(
        String userId,
        String username,
        String fullName,
        String email,
        String phoneNumber,
        String userRole, // HR or HR_MANAGER
        String companyId) {
}
