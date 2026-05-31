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
 * JWT Token Structure:
 * {
 *   "sub": "candidate_john",      // username
 *   "roles": ["CANDIDATE"],       // mapped to ROLE_* authorities
 *   "perms": ["application:create", ...], // direct authorities
 *   "companyId": "company-uuid"   // OPTIONAL: null for CANDIDATE/ADMIN
 * }
 *
 * SpEL: @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
 */
@Component("applicationSecurity")
@RequiredArgsConstructor
@Slf4j
public class ApplicationSecurity {

    private final ApplicationRepository applicationRepository;

    public String getCurrentUsername(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        throw new IllegalStateException("Expected JWT authentication");
    }

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

    public boolean isOwner(String applicationId, Authentication authentication) {
        String username = getCurrentUsername(authentication);
        return applicationRepository.existsByIdAndUsernameAndDeletedAtIsNull(applicationId, username);
    }

    public void requireOwnership(String applicationId, Authentication authentication) {
        String username = getCurrentUsername(authentication);
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        if (!application.getUsername().equals(username)) {
            log.warn("User {} attempted to access application {} owned by {}",
                    username, applicationId, application.getUsername());
            throw new ForbiddenException("You don't have access to this application");
        }
    }

    public boolean hasPermission(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public boolean isHR(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR"));
    }

    public boolean isCandidate(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE"));
    }

    public boolean canView(String applicationId, Authentication authentication) {
        if (hasPermission(authentication, "application:review") || isAdmin(authentication)) {
            return true;
        }
        return isOwner(applicationId, authentication);
    }

    public boolean canUpdateStatus(String applicationId, Authentication authentication) {
        return hasPermission(authentication, "application:update") || isAdmin(authentication);
    }

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

    public boolean isSameCompany(String companyId, Authentication authentication) {
        String username = getCurrentUsername(authentication);

        if (isAdmin(authentication)) {
            log.debug("Admin user {} granted access to companyId={}", username, companyId);
            return true;
        }

        String userCompanyId = getCurrentCompanyId(authentication);
        if (userCompanyId == null) {
            log.warn("Company validation DENIED: user={} has no companyId in JWT, cannot access companyId={}",
                    username, companyId);
            return false;
        }

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

    public boolean canAssign(String applicationId, Authentication authentication) {
        if (!hasPermission(authentication, "application:assign") && !isAdmin(authentication)) {
            return false;
        }
        return applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .map(app -> isSameCompany(app.getCompanyId(), authentication))
                .orElse(false);
    }

    public boolean canExport(String companyId, Authentication authentication) {
        return (hasPermission(authentication, "application:export") || isAdmin(authentication))
                && isSameCompany(companyId, authentication);
    }
}
