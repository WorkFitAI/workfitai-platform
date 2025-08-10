package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.service.iPermissionService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements iPermissionService {
    private final PermissionRepository perms;

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
}