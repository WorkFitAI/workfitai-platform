package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.request.BatchUserRolesRequest;
import org.workfitai.authservice.dto.response.ResponseData;
import org.workfitai.authservice.service.iUserRoleService;

import java.util.Set;

/**
 * Role grant/revoke for authorized users.
 *
 * This system follows strict RBAC: permissions are ALWAYS attached to roles,
 * never granted directly to users. To give a user a permission, assign them
 * the appropriate role (or an ADMIN-created custom role with that permission).
 *
 * Access rules (enforced at both controller + service layers):
 *   ADMIN      — can grant/revoke any role to any user
 *   HR_MANAGER — can only grant/revoke the HR role, and only within their company
 *   Others     — no grant rights (rejected at @PreAuthorize)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserGrantController {

    private final iUserRoleService userRoleService;

    // ==================== ROLE GRANT/REVOKE ====================

    @PostMapping("/{username}/roles")
    @PreAuthorize("hasAuthority('role:grant')")
    public ResponseEntity<ResponseData<Void>> grantRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.grantRoleToUser(username, role);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_GRANTED));
    }

    @DeleteMapping("/{username}/roles")
    @PreAuthorize("hasAuthority('role:revoke')")
    public ResponseEntity<ResponseData<Void>> revokeRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.revokeRoleFromUser(username, role);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_REVOKED));
    }

    @PostMapping("/{username}/roles/batch")
    @PreAuthorize("hasAuthority('role:grant')")
    public ResponseEntity<ResponseData<Void>> grantRolesBatch(
            @PathVariable String username,
            @Valid @RequestBody BatchUserRolesRequest request) {
        userRoleService.grantRolesToUser(username, request.getRoles());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLES_GRANTED_BATCH));
    }

    @DeleteMapping("/{username}/roles/batch")
    @PreAuthorize("hasAuthority('role:revoke')")
    public ResponseEntity<ResponseData<Void>> revokeRolesBatch(
            @PathVariable String username,
            @Valid @RequestBody BatchUserRolesRequest request) {
        userRoleService.revokeRolesFromUser(username, request.getRoles());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLES_REVOKED_BATCH));
    }

    @GetMapping("/{username}/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<ResponseData<Set<String>>> listUserRoles(
            @PathVariable String username) {
        Set<String> roles = userRoleService.getUserRoles(username);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.USER_ROLES_FETCHED, roles));
    }
}
