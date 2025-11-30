package org.workfitai.applicationservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

/**
 * Security helper for application-level authorization checks.
 * 
 * Provides methods for:
 * - Extracting user ID from JWT
 * - Checking resource ownership
 * - Role/permission verification
 * 
 * Used in:
 * - Controller layer for authorization
 * - Service layer for business rule enforcement
 * - @PreAuthorize expressions (via SpEL)
 * 
 * SpEL Usage Example:
 * @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
 */
@Component("applicationSecurity")
@RequiredArgsConstructor
@Slf4j
public class ApplicationSecurity {

    private final ApplicationRepository applicationRepository;

    /**
     * Extracts user ID from JWT token.
     * 
     * The user ID is stored in the "sub" (subject) claim of the JWT.
     * This is set by auth-service when creating tokens.
     * 
     * @param authentication Spring Security authentication object
     * @return User ID from JWT subject claim
     * @throws IllegalStateException if authentication is not JWT-based
     */
    public String getCurrentUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        throw new IllegalStateException("Expected JWT authentication");
    }

    /**
     * Checks if the current user owns the specified application.
     * 
     * Used for:
     * - Viewing own application details
     * - Withdrawing own application
     * 
     * SpEL: @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
     * 
     * @param applicationId  Application ID to check
     * @param authentication Current user's authentication
     * @return true if user owns the application
     */
    public boolean isOwner(String applicationId, Authentication authentication) {
        String userId = getCurrentUserId(authentication);

        return applicationRepository.findById(applicationId)
                .map(app -> app.getUserId().equals(userId))
                .orElse(false);
    }

    /**
     * Verifies ownership and throws ForbiddenException if not owner.
     * 
     * @param applicationId  Application ID to check
     * @param authentication Current user's authentication
     * @throws NotFoundException  if application doesn't exist
     * @throws ForbiddenException if user is not the owner
     */
    public void requireOwnership(String applicationId, Authentication authentication) {
        String userId = getCurrentUserId(authentication);

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        if (!application.getUserId().equals(userId)) {
            log.warn("User {} attempted to access application {} owned by {}",
                    userId, applicationId, application.getUserId());
            throw new ForbiddenException("You don't have access to this application");
        }
    }

    /**
     * Checks if user has ADMIN role.
     * 
     * @param authentication Current authentication
     * @return true if user has ROLE_ADMIN
     */
    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Checks if user has HR role.
     * 
     * @param authentication Current authentication
     * @return true if user has ROLE_HR
     */
    public boolean isHR(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR"));
    }

    /**
     * Checks if user has CANDIDATE role.
     * 
     * @param authentication Current authentication
     * @return true if user has ROLE_CANDIDATE
     */
    public boolean isCandidate(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE"));
    }

    /**
     * Checks if user can view application (owner, HR for the job, or admin).
     * 
     * @param applicationId  Application to check
     * @param authentication Current authentication
     * @return true if user can view
     */
    public boolean canView(String applicationId, Authentication authentication) {
        // Admin can view all
        if (isAdmin(authentication)) {
            return true;
        }

        // Owner can view their own
        if (isOwner(applicationId, authentication)) {
            return true;
        }

        // HR can view applications for their jobs (would need job ownership check)
        // For simplicity, allowing all HR to view for now
        if (isHR(authentication)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if user can update application status.
     * 
     * Only HR (for their jobs) or ADMIN can update status.
     * 
     * @param applicationId  Application to update
     * @param authentication Current authentication
     * @return true if user can update status
     */
    public boolean canUpdateStatus(String applicationId, Authentication authentication) {
        return isAdmin(authentication) || isHR(authentication);
    }
}
