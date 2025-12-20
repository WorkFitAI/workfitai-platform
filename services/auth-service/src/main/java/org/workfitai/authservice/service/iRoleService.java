package org.workfitai.authservice.service;

import org.workfitai.authservice.model.Role;

import java.util.List;
import java.util.Set;

public interface iRoleService {
    Role create(Role r);
    List<Role> createBatch(List<Role> roles);
    Role getByName(String roleName);
    Role updateDescription(String roleName, String description);
    void deleteByName(String roleName);
    Role addPermission(String roleName, String permName);
    Role addPermissions(String roleName, List<String> permNames);
    Role removePermission(String roleName, String permName);
    Role removePermissions(String roleName, List<String> permNames);
    Set<String> getPermissions(String roleName);
    List<Role> listAll();
}