package org.workfitai.applicationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Security helper for application-level authorization checks.
 * 
 * Provides methods for:
 * - Extracting username from JWT sub claim
 * - Checking resource ownership
 * - Permission verification via JWT perms claim
 * 
 * Used in:
 * - Controller layer for authorization
 * - Service layer for business rule enforcement
 * - @PreAuthorize expressions (via SpEL)
 * 
 * JWT Token Structure:
 * {
 * "sub": "candidate_john", // username (NOT userId!)
 * "roles": ["CANDIDATE"], // mapped to ROLE_* authorities
 * "perms": ["application:create", "application:read", ...] // direct
 * authorities
 * }
 * 
 * SpEL Usage Example:
 * @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
 * @PreAuthorize("hasAuthority('application:create')")
 */
@Component("applicationSecurity")
@RequiredArgsConstructor
@Slf4j
public class ApplicationSecurity {

    private final ApplicationRepository applicationRepository;

    /**
     * Extracts username from JWT token.
     * 
     * The username is stored in the "sub" (subject) claim of the JWT.
     * This is set by auth-service when creating tokens.
     * 
     * @param authentication Spring Security authentication object
     * @return Username from JWT subject claim
     * @throws IllegalStateException if authentication is not JWT-based
     */
    public String getCurrentUsername(Authentication authentication) {
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
        String username = getCurrentUsername(authentication);

        return applicationRepository.findById(applicationId)
                .map(app -> app.getUsername().equals(username))
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
        String username = getCurrentUsername(authentication);

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        if (!application.getUsername().equals(username)) {
            log.warn("User {} attempted to access application {} owned by {}",
                    username, applicationId, application.getUsername());
            throw new ForbiddenException("You don't have access to this application");
        }
    }

    /**
     * Checks if user has a specific permission.
     * 
     * Permissions come from JWT "perms" claim and are mapped as direct authorities.
     * 
     * @param authentication Current authentication
     * @param permission     Permission to check (e.g., "application:create")
     * @return true if user has the permission
     */
    public boolean hasPermission(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
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
     * Checks if user can view application.
     * 
     * Authorization rules:
     * - Owner can view their own application (application:read permission)
     * - HR can view applications for jobs (application:review permission)
     * - Admin has all permissions
     * 
     * @param applicationId  Application to check
     * @param authentication Current authentication
     * @return true if user can view
     */
    public boolean canView(String applicationId, Authentication authentication) {
        // Admin or HR with review permission can view all
        if (hasPermission(authentication, "application:review") || isAdmin(authentication)) {
            return true;
        }

        // Owner can view their own
        return isOwner(applicationId, authentication);
    }

    /**
     * Checks if user can update application status.
     * 
     * Only users with application:update permission (typically HR/ADMIN) can update
     * status.
     * 
     * @param applicationId  Application to update
     * @param authentication Current authentication
     * @return true if user can update status
     */
    public boolean canUpdateStatus(String applicationId, Authentication authentication) {
        return hasPermission(authentication, "application:update") || isAdmin(authentication);
    }
}
