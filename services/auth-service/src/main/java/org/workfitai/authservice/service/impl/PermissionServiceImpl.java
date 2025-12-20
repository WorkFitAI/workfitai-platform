package org.workfitai.authservice.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.service.iPermissionService;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements iPermissionService {
    private final PermissionRepository perms;
    private final RoleRepository roles;

    @Override
    public Permission create(Permission p) {
        if (perms.findByName(p.getName()).isPresent()) {
            throw new IllegalArgumentException(String.format(Messages.Error.PERMISSION_ALREADY_EXISTS, p.getName()));
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
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.PERMISSION_NOT_FOUND));
    }

    @Override
    public Permission updateDescription(String name, String description) {
        Permission existing = perms.findByName(name)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.PERMISSION_NOT_FOUND));
        existing.setDescription(description);
        return perms.save(existing);
    }

    @Override
    public void deleteByName(String name) {
        // Prevent destructive delete if any role still references this permission
        if (roles.existsByPermissionsContains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, Messages.Error.PERMISSION_IN_USE);
        }
        Permission existing = perms.findByName(name)
                .orElseThrow(() -> new NoSuchElementException(Messages.Error.PERMISSION_NOT_FOUND));
        perms.delete(existing);
    }

    @Override
    @Transactional
    public List<Permission> createBatch(List<Permission> permissions) {
        List<Permission> createdPermissions = new ArrayList<>();
        for (Permission p : permissions) {
            createdPermissions.add(create(p));
        }
        return createdPermissions;
    }
}
