package org.workfitai.authservice.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.service.iRoleService;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements iRoleService {
    private final RoleRepository roles;
    private final PermissionRepository perms;

    /** Names of built-in system roles seeded at startup — they are immutable. */
    private static final Set<String> BUILT_IN_ROLES = Arrays.stream(UserRole.values())
            .map(UserRole::getRoleName)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Throws if {@code roleName} matches a built-in system role.
     * Use this guard before any mutation (update, delete, add/remove permissions).
     */
    private void assertNotBuiltIn(String roleName) {
        if (BUILT_IN_ROLES.stream().anyMatch(b -> b.equalsIgnoreCase(roleName))) {
            throw new IllegalArgumentException(
                    String.format(Messages.Error.CANNOT_MODIFY_BUILTIN_ROLE, roleName));
        }
    }

    /**
     * Throws if {@code roleName} matches a built-in system role with a delete-specific message.
     */
    private void assertNotBuiltInForDelete(String roleName) {
        if (BUILT_IN_ROLES.stream().anyMatch(b -> b.equalsIgnoreCase(roleName))) {
            throw new IllegalArgumentException(
                    String.format(Messages.Error.CANNOT_DELETE_BUILTIN_ROLE, roleName));
        }
    }

    @Override
    public Role create(Role r) {
        if (roles.findByName(r.getName()).isPresent()) {
            throw new IllegalArgumentException(String.format(Messages.Error.ROLE_ALREADY_EXISTS, r.getName()));
        }
        Set<String> permissions = r.getPermissions() == null ? new HashSet<>() : new HashSet<>(r.getPermissions());
        r.setPermissions(permissions);
        // Validate all perms exist
        for (String perm : permissions) {
            perms.findByName(perm)
                    .orElseThrow(() -> new IllegalArgumentException(String.format(Messages.Error.UNKNOWN_PERMISSION, perm)));
        }
        return roles.save(r);
    }

    @Override
    public Role addPermission(String roleName, String permName) {
        assertNotBuiltIn(roleName);
        Role r = roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.ROLE_NOT_FOUND));
        if (perms.findByName(permName).isEmpty()) {
            throw new NoSuchElementException(Messages.Error.PERMISSION_NOT_FOUND);
        }
        Set<String> permissions = r.getPermissions();
        if (permissions == null) {
            permissions = new HashSet<>();
            r.setPermissions(permissions);
        }
        if (!permissions.add(permName)) {
            throw new IllegalArgumentException(Messages.Error.PERMISSION_ALREADY_ASSIGNED);
        }
        return roles.save(r);
    }

    @Override
    public Role removePermission(String roleName, String permName) {
        assertNotBuiltIn(roleName);
        Role r = roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.ROLE_NOT_FOUND));
        Set<String> permissions = r.getPermissions();
        if (permissions != null) {
            permissions.remove(permName);
        }
        return roles.save(r);
    }

    @Override
    public Set<String> getPermissions(String roleName) {
        // Fail-open: return empty permissions instead of throwing
        // (and log once so we can spot data issues)
        // You can switch to log.warn if you prefer noisier logs.
        return roles.findByName(roleName)
                .map(Role::getPermissions)
                .orElseGet(java.util.Set::of); // empty when role missing
    }

    @Override
    public List<Role> listAll() {
        return roles.findAll();
    }

    @Override
    @Transactional
    public List<Role> createBatch(List<Role> roleList) {
        List<Role> createdRoles = new ArrayList<>();
        for (Role r : roleList) {
            createdRoles.add(create(r));
        }
        return createdRoles;
    }

    @Override
    public Role getByName(String roleName) {
        return roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.ROLE_NOT_FOUND));
    }

    @Override
    public Role updateDescription(String roleName, String description) {
        assertNotBuiltIn(roleName);
        Role existing = getByName(roleName);
        existing.setDescription(description);
        return roles.save(existing);
    }

    @Override
    public void deleteByName(String roleName) {
        assertNotBuiltInForDelete(roleName);
        Role existing = getByName(roleName);
        roles.delete(existing);
    }

    @Override
    @Transactional
    public Role addPermissions(String roleName, List<String> permNames) {
        assertNotBuiltIn(roleName);
        Role r = getByName(roleName);

        // Validate all permissions exist
        for (String permName : permNames) {
            if (perms.findByName(permName).isEmpty()) {
                throw new NoSuchElementException(String.format(Messages.Error.UNKNOWN_PERMISSION, permName));
            }
        }

        Set<String> permissions = r.getPermissions();
        if (permissions == null) {
            permissions = new HashSet<>();
            r.setPermissions(permissions);
        }

        permissions.addAll(permNames);
        return roles.save(r);
    }

    @Override
    @Transactional
    public Role removePermissions(String roleName, List<String> permNames) {
        assertNotBuiltIn(roleName);
        Role r = getByName(roleName);
        Set<String> permissions = r.getPermissions();
        if (permissions != null) {
            permissions.removeAll(permNames);
        }
        return roles.save(r);
    }

    @Override
    public Role cloneRole(String sourceRoleName, String newRoleName, String description) {
        Role source = getByName(sourceRoleName);
        // Prevent overwriting or shadowing a built-in role name with a clone
        if (BUILT_IN_ROLES.stream().anyMatch(b -> b.equalsIgnoreCase(newRoleName))) {
            throw new IllegalArgumentException(
                    String.format(Messages.Error.CANNOT_MODIFY_BUILTIN_ROLE, newRoleName));
        }
        if (roles.findByName(newRoleName).isPresent()) {
            throw new IllegalArgumentException(String.format(Messages.Error.ROLE_ALREADY_EXISTS, newRoleName));
        }
        // Deep-copy permissions so edits to the clone don't affect the source
        Set<String> clonedPerms = source.getPermissions() == null
                ? new HashSet<>()
                : new HashSet<>(source.getPermissions());
        return roles.save(Role.builder()
                .name(newRoleName)
                .description(description)
                .permissions(clonedPerms)
                .build());
    }
}
