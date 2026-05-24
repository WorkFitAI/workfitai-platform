package org.workfitai.authservice.service;

import java.util.List;
import java.util.Set;

public interface iUserRoleService {
    void grantRoleToUser(String username, String roleName);
    void revokeRoleFromUser(String username, String roleName);
    void grantRolesToUser(String username, List<String> roleNames);
    void revokeRolesFromUser(String username, List<String> roleNames);
    Set<String> getUserRoles(String username);
}