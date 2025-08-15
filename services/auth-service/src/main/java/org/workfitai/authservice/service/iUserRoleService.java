package org.workfitai.authservice.service;

import java.util.Set;

public interface iUserRoleService {
    void grantRoleToUser(String username, String roleName);
    void revokeRoleFromUser(String username, String roleName);
    Set<String> getUserRoles(String username);
}