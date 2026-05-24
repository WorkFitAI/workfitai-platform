package org.workfitai.authservice.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.iGrantAuditService;
import org.workfitai.authservice.service.iUserRoleService;

/**
 * Manages role assignment to users.
 *
 * Business rules (enforced in service, not only at controller):
 *   1. Self-grant/revoke always rejected.
 *   2. Only ADMIN can grant ADMIN or HR_MANAGER roles.
 *   3. HR_MANAGER can ONLY grant/revoke the HR role, and only within their own company.
 *   4. Permissions always flow through roles — never granted directly to users.
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements iUserRoleService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final iGrantAuditService auditService;

    private static final String RESOURCE_TYPE = "ROLE";
    private static final String ADMIN_ROLE    = "ADMIN";
    private static final String HR_MGR_ROLE   = "HR_MANAGER";
    private static final String HR_ROLE       = "HR";

    /** Set of built-in role names — a user must always retain at least one of these. */
    private static final Set<String> BUILT_IN_ROLES = Arrays.stream(UserRole.values())
            .map(UserRole::getRoleName)
            .collect(Collectors.toUnmodifiableSet());

    /** Returns true when {@code roleName} is a built-in system role (case-insensitive). */
    private static boolean isBuiltIn(String roleName) {
        return BUILT_IN_ROLES.stream().anyMatch(b -> b.equalsIgnoreCase(roleName));
    }

    /**
     * Ensures the revoke operation does not strip the user's last built-in role.
     * A user must always retain at least one built-in role so they can still authenticate.
     */
    private void assertRetainsBuiltInRole(User target, Set<String> rolesToRemove) {
        long remainingBuiltIn = target.getRoles().stream()
                .filter(r -> isBuiltIn(r) && !rolesToRemove.contains(r))
                .count();
        if (remainingBuiltIn < 1) {
            throw new IllegalArgumentException(Messages.Error.CANNOT_REMOVE_LAST_BUILTIN_ROLE);
        }
    }

    // ─── single grant/revoke ───────────────────────────────────────────────

    @Override
    @Transactional
    public void grantRoleToUser(String username, String roleName) {
        Authentication auth = currentAuth();
        assertNotSelf(auth, username);

        User grantor = findUser(auth.getName());
        User target  = findUser(username);

        roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.ROLE_NOT_FOUND));

        assertGrantAllowed(auth, grantor, target, roleName);

        if (!target.getRoles().add(roleName)) {
            throw new IllegalArgumentException(Messages.Error.ROLE_ALREADY_GRANTED);
        }
        // When HR_MANAGER onboards a new HR user, associate them with the company
        if (HR_ROLE.equals(roleName) && isHrManager(auth) && !isAdmin(auth)) {
            if (target.getCompanyNo() == null) {
                target.setCompanyNo(grantor.getCompanyNo());
                target.setCompanyId(grantor.getCompanyId());
            }
        }
        users.save(target);
        auditService.logGrant(auth.getName(), username, RESOURCE_TYPE, roleName);
    }

    @Override
    @Transactional
    public void revokeRoleFromUser(String username, String roleName) {
        Authentication auth = currentAuth();
        assertNotSelf(auth, username);

        User grantor = findUser(auth.getName());
        User target  = findUser(username);

        roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.ROLE_NOT_FOUND));

        assertGrantAllowed(auth, grantor, target, roleName);
        // Protect user's last built-in role — removing it would leave them with no identity
        assertRetainsBuiltInRole(target, Set.of(roleName));

        target.getRoles().remove(roleName);
        users.save(target);
        auditService.logRevoke(auth.getName(), username, RESOURCE_TYPE, roleName);
    }

    // ─── batch grant/revoke ───────────────────────────────────────────────

    @Override
    @Transactional
    public void grantRolesToUser(String username, List<String> roleNames) {
        Authentication auth = currentAuth();
        assertNotSelf(auth, username);

        User grantor = findUser(auth.getName());
        User target  = findUser(username);

        for (String roleName : roleNames) {
            roles.findByName(roleName)
                    .orElseThrow(() -> new NoSuchElementException(
                            String.format(Messages.Error.UNKNOWN_ROLE, roleName)));
            assertGrantAllowed(auth, grantor, target, roleName);
        }
        target.getRoles().addAll(roleNames);
        // Propagate company info for any HR role in the batch
        if (roleNames.contains(HR_ROLE) && isHrManager(auth) && !isAdmin(auth)
                && target.getCompanyNo() == null) {
            target.setCompanyNo(grantor.getCompanyNo());
            target.setCompanyId(grantor.getCompanyId());
        }
        users.save(target);
        roleNames.forEach(r -> auditService.logGrant(auth.getName(), username, RESOURCE_TYPE, r));
    }

    @Override
    @Transactional
    public void revokeRolesFromUser(String username, List<String> roleNames) {
        Authentication auth = currentAuth();
        assertNotSelf(auth, username);

        User grantor = findUser(auth.getName());
        User target  = findUser(username);

        for (String roleName : roleNames) {
            roles.findByName(roleName)
                    .orElseThrow(() -> new NoSuchElementException(
                            String.format(Messages.Error.UNKNOWN_ROLE, roleName)));
            assertGrantAllowed(auth, grantor, target, roleName);
        }
        // Validate the entire batch at once before any removal — fail atomically
        assertRetainsBuiltInRole(target, Set.copyOf(roleNames));
        target.getRoles().removeAll(roleNames);
        users.save(target);
        roleNames.forEach(r -> auditService.logRevoke(auth.getName(), username, RESOURCE_TYPE, r));
    }

    // ─── read ─────────────────────────────────────────────────────────────

    /**
     * Returns roles for the target user.
     *
     * HR_MANAGER scope (enforced here, not only at controller):
     *   - May view roles only for HR or HR_MANAGER users (never ADMIN/CANDIDATE).
     *   - Target must belong to the same company as the HR_MANAGER.
     *   - Exception: HR_MANAGER may always view their own roles.
     */
    @Override
    public Set<String> getUserRoles(String username) {
        Authentication auth = currentAuth();

        User target = users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.USER_NOT_FOUND));

        if (isHrManager(auth) && !isAdmin(auth) && !auth.getName().equals(username)) {
            User viewer = findUser(auth.getName());

            // HR_MANAGER may only inspect HR or HR_MANAGER users
            boolean targetIsHrOrHrm = target.getRoles().stream()
                    .anyMatch(r -> HR_ROLE.equals(r) || HR_MGR_ROLE.equals(r));
            if (!targetIsHrOrHrm) {
                throw new AccessDeniedException(Messages.Error.FORBIDDEN);
            }

            // Target must be in the same company
            if (viewer.getCompanyNo() == null
                    || !viewer.getCompanyNo().equals(target.getCompanyNo())) {
                throw new AccessDeniedException(Messages.Error.CANNOT_GRANT_ROLE_OUTSIDE_COMPANY);
            }
        }

        return target.getRoles();
    }

    // ─── business-rule guards ─────────────────────────────────────────────

    /**
     * Central authorization check for role assignment.
     *
     * ADMIN  → can grant any role to anyone.
     * HR_MANAGER → can only grant/revoke the HR role, and only to users in the same company.
     * Others → no grant rights (blocked at controller via @PreAuthorize).
     */
    private void assertGrantAllowed(Authentication auth, User grantor, User target, String roleName) {
        if (isAdmin(auth)) {
            return; // ADMIN is unrestricted
        }

        if (isHrManager(auth)) {
            // Rule: HR_MANAGER may only manage the HR role
            if (!HR_ROLE.equals(roleName)) {
                throw new AccessDeniedException(Messages.Error.CANNOT_GRANT_ROLE_OUTSIDE_SCOPE);
            }
            // Rule: HR_MANAGER must belong to a company
            if (grantor.getCompanyNo() == null) {
                throw new AccessDeniedException(Messages.Error.HRM_NO_COMPANY);
            }
            // Rule: target must be in the same company (or unaffiliated — will be onboarded)
            if (target.getCompanyNo() != null
                    && !grantor.getCompanyNo().equals(target.getCompanyNo())) {
                throw new AccessDeniedException(Messages.Error.CANNOT_GRANT_ROLE_OUTSIDE_COMPANY);
            }
            return;
        }

        // Any other caller that somehow bypassed @PreAuthorize
        throw new AccessDeniedException(Messages.Error.FORBIDDEN);
    }

    private void assertNotSelf(Authentication auth, String targetUsername) {
        if (auth.getName().equals(targetUsername)) {
            throw new AccessDeniedException(Messages.Error.CANNOT_GRANT_OWN_ROLE);
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_ROLE.equals(a.getAuthority()));
    }

    private boolean isHrManager(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> HR_MGR_ROLE.equals(a.getAuthority()));
    }

    private User findUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.USER_NOT_FOUND));
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
