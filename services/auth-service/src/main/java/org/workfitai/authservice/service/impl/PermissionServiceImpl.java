package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.service.iPermissionService;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements iPermissionService {
    private final PermissionRepository perms;
    private final RoleRepository roles;

    @Override
    public Permission create(Permission p) {
        if (perms.findByName(p.getName()).isPresent()) {
            throw new IllegalArgumentException("Permission exists");
        }
        return perms.save(p);
    }

    @Override
    public List<Permission> listAll() {
        return perms.findAll();
    }

    @Override
    public Permission getByName(String name) {
        return perms.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Permission not found"));
    }

    @Override
    public Permission updateDescription(String name, String description) {
        Permission existing = perms.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Permission not found"));
        existing.setDescription(description);
        return perms.save(existing);
    }

    @Override
    public void deleteByName(String name) {
        // Prevent destructive delete if any role still references this permission
        if (roles.existsByPermissionsContains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission is referenced by one or more roles");
        }
        Permission existing = perms.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Permission not found"));
        perms.delete(existing);
    }
}