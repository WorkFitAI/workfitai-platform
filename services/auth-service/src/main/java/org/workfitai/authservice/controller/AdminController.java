package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.*;
import org.workfitai.authservice.model.*;
import org.workfitai.authservice.service.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final iPermissionService permissionService;
    private final iRoleService roleService;
    private final iUserRoleService userRoleService;

    @PostMapping("/permissions")
    public ResponseEntity<Permission> createPerm(
            @Valid @RequestBody CreatePermissionDto dto) {
        return ResponseEntity.ok(permissionService.create(
                Permission.builder()
                        .name(dto.getName())
                        .description(dto.getDescription())
                        .build()));
    }

    @GetMapping("/permissions")
    public List<Permission> listPerms() {
        return permissionService.listAll();
    }

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(
            @Valid @RequestBody CreateRoleDto dto) {
        return ResponseEntity.ok(roleService.create(
                Role.builder()
                        .name(dto.getName())
                        .description(dto.getDescription())
                        .permissions(dto.getPermissions())
                        .build()));
    }

    @GetMapping("/roles")
    public List<Role> listRoles() {
        return roleService.listAll();
    }

    @PostMapping("/roles/add-perm")
    public Role addPerm(@RequestBody RolePermissionDto dto) {
        return roleService.addPermission(dto.getRoleName(), dto.getPermName());
    }

    @PostMapping("/roles/remove-perm")
    public Role removePerm(@RequestBody RolePermissionDto dto) {
        return roleService.removePermission(dto.getRoleName(), dto.getPermName());
    }

    @PostMapping("/users/{username}/roles")
    public ResponseEntity<Void> grantRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.grantRoleToUser(username, role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{username}/roles")
    public ResponseEntity<Void> revokeRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.revokeRoleFromUser(username, role);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{username}/roles")
    public ResponseEntity<Set<String>> listUserRoles(
            @PathVariable String username) {
        Set<String> roles = userRoleService.getUserRoles(username);
        return ResponseEntity.ok(roles);
    }
}
