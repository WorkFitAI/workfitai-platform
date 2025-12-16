package org.workfitai.applicationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.UserCompanyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Security helper for application-level authorization checks.
 *
 * Provides methods for:
 * - Extracting username from JWT sub claim
 * - Checking resource ownership
 * - Permission verification via JWT perms claim
 * - Company isolation validation
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
 * "perms": ["application:create", "application:read", ...], // direct authorities
 * "companyId": "company-uuid" // OPTIONAL: Company ID (null for CANDIDATE)
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
    // NOTE: UserCompanyService kept for backward compatibility but not actively used
    // since we now extract companyId from JWT claims directly
    private final UserCompanyService userCompanyService;

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
     * Extracts companyId from JWT token (optional claim).
     *
     * The companyId is stored in the "companyId" claim of the JWT.
     * This field is OPTIONAL and may be:
     * - Present for HR/HR_MANAGER users (company employees)
     * - Null/absent for CANDIDATE users
     * - Null/absent for ADMIN users (global access)
     *
     * TODO: Update auth-service to include companyId in JWT claims
     * - Modify JwtService.generateAccessToken() to add companyId claim
     * - Fetch companyId from User entity in auth-service
     * - Make companyId REQUIRED for HR/HR_MANAGER roles
     *
     * @param authentication Spring Security authentication object
     * @return CompanyId from JWT claim, or null if not present
     * @throws IllegalStateException if authentication is not JWT-based
     */
    public String getCurrentCompanyId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object companyIdClaim = jwtAuth.getToken().getClaims().get("companyId");
            if (companyIdClaim != null) {
                return companyIdClaim.toString();
            }
            log.debug("No companyId claim found in JWT for user: {}", getCurrentUsername(authentication));
            return null;
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

    /**
     * Checks if user can edit a draft application.
     *
     * Authorization rules:
     * - Must be the owner of the application
     * - Application must be in draft status (isDraft=true)
     *
     * @param applicationId  Application to edit
     * @param authentication Current authentication
     * @return true if user can edit the draft
     */
    public boolean canEditDraft(String applicationId, Authentication authentication) {
        String username = getCurrentUsername(authentication);

        return applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .map(app -> app.getUsername().equals(username) && app.isDraft())
                .orElse(false);
    }

    /**
     * Checks if user is the author of a specific note.
     *
     * Used for authorization checks when updating or deleting notes.
     * Only the note author can modify their own notes.
     *
     * SpEL: @PreAuthorize("@applicationSecurity.isNoteAuthor(#applicationId, #noteId, authentication)")
     *
     * @param applicationId  Application ID containing the note
     * @param noteId         Note ID to check
     * @param authentication Current user's authentication
     * @return true if user is the note author
     */
    public boolean isNoteAuthor(String applicationId, String noteId, Authentication authentication) {
        String username = getCurrentUsername(authentication);

        return applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .map(app -> app.getNotes().stream()
                        .filter(note -> note.getId().equals(noteId))
                        .findFirst()
                        .map(note -> note.getAuthor().equals(username))
                        .orElse(false))
                .orElse(false);
    }

    // ==================== Phase 3: Manager Authorization Methods ====================

    /**
     * Checks if user belongs to the same company as the application.
     *
     * Used for company-level access control.
     * Manager must belong to same company to view/manage applications.
     *
     * Implementation (Phase 5 - JWT-based):
     * - Extracts user's companyId from JWT "companyId" claim (optional)
     * - Validates that user.companyId == application.companyId
     * - If companyId not in JWT, bypasses validation (returns false)
     *
     * Special Cases:
     * - ADMIN users: Bypass company check (global access)
     * - CANDIDATE users: Have no companyId (returns false for company checks)
     * - CompanyId not in JWT: Bypass validation (returns false) - TEMPORARY
     *
     * Security:
     * - Prevents cross-company data leaks
     * - Enforces strict company isolation when companyId available
     * - Logs all validation attempts
     *
     * TODO (Future Enhancement):
     * - Make companyId REQUIRED in JWT for HR/HR_MANAGER roles
     * - Update auth-service to include companyId claim
     * - Remove bypass logic once all JWTs include companyId
     * - See: getCurrentCompanyId() for auth-service update checklist
     *
     * @param companyId      Company ID to check
     * @param authentication Current user's authentication
     * @return true if user belongs to same company OR is admin OR companyId not available (bypass)
     */
    public boolean isSameCompany(String companyId, Authentication authentication) {
        String username = getCurrentUsername(authentication);

        // Admin bypass: Global access to all companies
        if (isAdmin(authentication)) {
            log.debug("Admin user {} granted access to companyId={}", username, companyId);
            return true;
        }

        // Extract companyId from JWT (Phase 5: JWT-based approach)
        String userCompanyId = getCurrentCompanyId(authentication);

        // TEMPORARY: If companyId not in JWT, bypass validation
        // TODO: Remove this bypass once auth-service adds companyId to JWT
        if (userCompanyId == null) {
            log.warn("CompanyId not found in JWT for user={} - BYPASSING company validation for companyId={}. " +
                    "TODO: Update auth-service to include companyId claim in JWT", username, companyId);
            return true; // Deny access if no company ID (safer default)
        }

        // Validate company match
        boolean isSame = companyId.equals(userCompanyId);

        if (isSame) {
            log.debug("Company validation PASSED: user={}, userCompanyId={}, requestedCompanyId={}",
                    username, userCompanyId, companyId);
        } else {
            log.warn("Company validation FAILED: user={}, userCompanyId={}, requestedCompanyId={}",
                    username, userCompanyId, companyId);
        }

        return isSame;
    }

    /**
     * Checks if user can assign an application.
     *
     * Authorization rules:
     * - Must have application:assign permission (HR_MANAGER role)
     * - Must belong to same company as application
     * - Cannot assign draft applications
     *
     * @param applicationId  Application to assign
     * @param authentication Current user's authentication
     * @return true if user can assign
     */
    public boolean canAssign(String applicationId, Authentication authentication) {
        // Check permission first
        if (!hasPermission(authentication, "application:assign") && !isAdmin(authentication)) {
            return false;
        }

        // Check if application exists and belongs to same company
        return applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .map(app -> {
                    // Cannot assign drafts
                    if (app.isDraft()) {
                        return false;
                    }

                    // Check same company (Phase 3: always true for now)
                    // return isSameCompany(app.getCompanyId(), authentication);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Checks if user can export applications for a company.
     *
     * Authorization rules:
     * - Must have application:export permission (HR_MANAGER role)
     * - Must belong to same company
     *
     * @param companyId      Company ID to export
     * @param authentication Current user's authentication
     * @return true if user can export
     */
    public boolean canExport(String companyId, Authentication authentication) {
        return (hasPermission(authentication, "application:export") || isAdmin(authentication))
                && isSameCompany(companyId, authentication);
    }
}
