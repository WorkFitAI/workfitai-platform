package org.workfitai.authservice.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.dto.CreateRoleDto;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.service.iPermissionService;
import org.workfitai.authservice.service.iRoleService;
import org.workfitai.authservice.service.iUserRoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final iPermissionService permissionService;
    private final iRoleService roleService;
    private final iUserRoleService userRoleService;

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('perm:read')")
    public List<Permission> listPerms() {
        return permissionService.listAll();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:create')")
    public ResponseEntity<Role> createRole(
            @Valid @RequestBody CreateRoleDto dto) {
        Set<String> perms = (dto.getPermissions() == null) ? Set.of() : dto.getPermissions();
        return ResponseEntity.ok(roleService.create(
                Role.builder()
                        .name(dto.getName())
                        .description(dto.getDescription())
                        .permissions(perms)
                        .build()
        ));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public List<Role> listRoles() {
        return roleService.listAll();
    }

    @PostMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:grant')")
    public ResponseEntity<Void> grantRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.grantRoleToUser(username, role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:revoke')")
    public ResponseEntity<Void> revokeRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.revokeRoleFromUser(username, role);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<Set<String>> listUserRoles(
            @PathVariable String username) {
        Set<String> roles = userRoleService.getUserRoles(username);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/permissions/{name}")
    @PreAuthorize("hasAuthority('perm:read')")
    public ResponseEntity<Permission> getPerm(@PathVariable String name) {
        return ResponseEntity.ok(permissionService.getByName(name));
    }
}
