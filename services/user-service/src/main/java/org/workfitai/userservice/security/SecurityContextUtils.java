package org.workfitai.userservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility for reading JWT claims from the current security context.
 *
 * Note: the "companyId" JWT claim stores the company tax-number (companyNo),
 * not the internal UUID — this matches the convention set in auth-service JwtService.
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {}

    /**
     * Returns the companyNo for the current caller, extracted from the "companyId" JWT claim.
     * Returns null when the caller is unauthenticated, or has no company association
     * (e.g. ADMIN, CANDIDATE).
     */
    public static String currentCallerCompanyNo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt)) {
            return null;
        }
        return ((Jwt) auth.getPrincipal()).getClaimAsString("companyId");
    }

    /**
     * Returns true if the current caller has the given role.
     * Provide the role WITHOUT the "ROLE_" prefix (e.g. "HR_MANAGER").
     */
    public static boolean callerHasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
