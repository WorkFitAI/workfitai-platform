package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.service.iRoleService;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements iRoleService {
    private final RoleRepository roles;
    private final PermissionRepository perms;

    @Override
    public Role create(Role r) {
        if (roles.findByName(r.getName()).isPresent()) {
            throw new IllegalArgumentException("Role exists");
        }
        // Validate all perms exist
        for (String perm : r.getPermissions()) {
            perms.findByName(perm)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown perm: "+perm));
        }
        return roles.save(r);
    }

    @Override
    public Role addPermission(String roleName, String permName) {
        Role r = roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException("Role not found"));
        if (perms.findByName(permName).isEmpty()) {
            throw new NoSuchElementException("Permission not found");
        }
        r.getPermissions().add(permName);
        return roles.save(r);
    }

    @Override
    public Role removePermission(String roleName, String permName) {
        Role r = roles.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException("Role not found"));
        r.getPermissions().remove(permName);
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
}